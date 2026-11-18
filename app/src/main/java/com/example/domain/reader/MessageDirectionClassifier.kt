package com.example.domain.reader

import com.example.domain.engine.LocalSelfLearningEngine
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.TransactionType

enum class MessageDirection {
    INCOMING,
    OUTGOING,
    INTERNAL_TRANSFER,
    BALANCE_ONLY,
    UNKNOWN
}

data class DirectionAssessment(
    val direction: MessageDirection,
    val operationKind: MessageOperationKind,
    val confidence: Float,
    val reasonAr: String,
    val reasonEn: String
)

/**
 * Weighted bilingual (Arabic / Egyptian dialect / English) direction classifier that resolves
 * whether a financial alert represents INCOMING funds ("استقبال"), OUTGOING spend/transfer ("إرسال"),
 * INTERNAL_TRANSFER, or BALANCE_ONLY, combining:
 * 1) Weighted directional phrase evidence (handles tricky phrases like "تم تحويل ... إلى حسابك")
 * 2) Balance-delta consistency check when prior account balance is known
 * 3) Self-learned merchant rule directional prior
 */
object MessageDirectionClassifier {

    private data class WeightedCue(
        val regex: Regex,
        val weight: Float,
        val suggestedKind: MessageOperationKind,
        val labelAr: String,
        val labelEn: String
    )

    private val incomingCues = listOf(
        WeightedCue(Regex("""\b(salary|payroll)\b|(راتب|مرتب)""", RegexOption.IGNORE_CASE), 4.6f, MessageOperationKind.SALARY, "إشعار راتب وارد", "Salary credit"),
        WeightedCue(Regex("""(استرداد|مرتجع|رد مبلغ|\brefund\b|\breversed\b|\breversal\b)""", RegexOption.IGNORE_CASE), 4.5f, MessageOperationKind.REFUND, "استرداد مبلغ لحسابك", "Refund credit"),
        WeightedCue(Regex("""(تم إيداع|تم ايداع|إيداع|ايداع|\bdeposited\b|cash deposit|cash in)""", RegexOption.IGNORE_CASE), 4.5f, MessageOperationKind.DEPOSIT, "عملية إيداع", "Deposit cue"),
        WeightedCue(Regex("""(تم استلام|استلمت|تحويل وارد|وصلك|وصلتك|أرسل لك|أضاف لحسابك|إضافة لحسابك|تمت إضافة|\breceived\b|sent you|incoming transfer)""", RegexOption.IGNORE_CASE), 4.0f, MessageOperationKind.INCOMING_TRANSFER, "صيغة استلام / تحويل وارد", "Incoming transfer cue"),
        WeightedCue(Regex("""(إلى حسابك|الى حسابك|لحسابك|في حسابك|بمحفظتك|إلى محفظتك|الى محفظتك|to your account|to your wallet|credited to|credited with)""", RegexOption.IGNORE_CASE), 4.3f, MessageOperationKind.INCOMING_TRANSFER, "إيداع/تحويل متجه إلى حسابك", "Credited to your account")
    )

    private val outgoingCues = listOf(
        WeightedCue(Regex("""(رسوم تحويل|خصم رسوم|مصاريف إدارية|transfer fee|service fee|fee charged)""", RegexOption.IGNORE_CASE), 3.8f, MessageOperationKind.TRANSFER_FEE, "رسوم أو عمولة مخصومة", "Fee charged"),
        WeightedCue(Regex("""(دفع فاتورة|سداد فاتورة|فاتورة|شحن رصيد|bill payment|utility payment|paid bill)""", RegexOption.IGNORE_CASE), 3.8f, MessageOperationKind.BILL_PAYMENT, "سداد فاتورة / شحن", "Bill payment"),
        WeightedCue(Regex("""(تم سحب|سحب نقدي|سحب مبلغ|صراف آلي|\batm\b|\bwithdrawn\b|\bwithdrawal\b|cash out)""", RegexOption.IGNORE_CASE), 3.9f, MessageOperationKind.WITHDRAWAL, "سحب نقدي صادر", "Cash withdrawal"),
        WeightedCue(Regex("""(عملية شراء|شراء بمبلغ|تم الشراء|نقطة بيع|بطاقة.*خصم|تم خصم.*بطاق|purchase of|\bspent\b|\bpos\b|card ending|was charged|bought)""", RegexOption.IGNORE_CASE), 4.0f, MessageOperationKind.CARD_PURCHASE, "شراء أو خصم بطاقة", "Card purchase / debit"),
        WeightedCue(Regex("""(قمت بتحويل|تحويل صادر|حولت|أرسلت|تم خصم|خصم مبلغ|من حسابك|من محفظتك|debited with|debited from|deducted|you sent|outgoing transfer)""", RegexOption.IGNORE_CASE), 3.8f, MessageOperationKind.OUTGOING_TRANSFER, "صيغة إرسال / خصم من حسابك", "Debit / outgoing cue"),
        // Neutral "تم تحويل" or "transferred to ..." - strong outgoing ONLY when not directed to your account
        WeightedCue(Regex("""(إلى رقم|الى رقم|لرقم|لحساب اخر|لحساب آخر|to account(?!\s+ending\s+yours)|to number|transferred to (?!your))""", RegexOption.IGNORE_CASE), 3.4f, MessageOperationKind.OUTGOING_TRANSFER, "تحويل موجه لطرف خارجي", "Sent to external recipient"),
        WeightedCue(Regex("""(تم تحويل|\btransferred\b|\bsent\b)""", RegexOption.IGNORE_CASE), 1.6f, MessageOperationKind.OUTGOING_TRANSFER, "لفظ تحويل", "Transfer keyword")
    )

    private val internalCues = listOf(
        WeightedCue(Regex("""(بين حساباتك|حسابك الآخر|تحويل داخلي|own account|between your accounts|internal transfer)""", RegexOption.IGNORE_CASE), 5.0f, MessageOperationKind.INTERNAL_TRANSFER, "تحويل بين حساباتك الخاصة", "Internal account transfer")
    )

    private val balanceOnlyCue = Regex("""(استعلام عن الرصيد|الرصيد الحالي|رصيدك المتاح|رصيدك هو|balance inquiry|available balance|current balance)""", RegexOption.IGNORE_CASE)

    fun classify(
        rawBody: String,
        balanceAfterPiasters: Long? = null,
        knownAccountBalancePiasters: Long? = null,
        hasTransactionAmount: Boolean = false,
        learnedPreferredType: TransactionType? = null
    ): DirectionAssessment {
        val norm = LocalSelfLearningEngine.normalizeRich(rawBody)
        val combined = "$rawBody $norm"

        // 1. Check explicit internal transfer
        for (cue in internalCues) {
            if (cue.regex.containsMatchIn(combined)) {
                return DirectionAssessment(
                    direction = MessageDirection.INTERNAL_TRANSFER,
                    operationKind = MessageOperationKind.INTERNAL_TRANSFER,
                    confidence = 0.94f,
                    reasonAr = cue.labelAr,
                    reasonEn = cue.labelEn
                )
            }
        }

        // 2. Tally directional cue scores
        var inScore = 0f
        var bestInCue: WeightedCue? = null
        var bestInWeight = 0f
        for (cue in incomingCues) {
            if (cue.regex.containsMatchIn(combined)) {
                inScore += cue.weight
                if (cue.weight > bestInWeight) {
                    bestInWeight = cue.weight
                    bestInCue = cue
                }
            }
        }

        var outScore = 0f
        var bestOutCue: WeightedCue? = null
        var bestOutWeight = 0f
        for (cue in outgoingCues) {
            if (cue.regex.containsMatchIn(combined)) {
                outScore += cue.weight
                if (cue.weight > bestOutWeight) {
                    bestOutWeight = cue.weight
                    bestOutCue = cue
                }
            }
        }

        // 3. Pure balance inquiry check (only when no stronger incoming/outgoing action matched)
        if (inScore == 0f && outScore == 0f && balanceOnlyCue.containsMatchIn(combined)) {
            return DirectionAssessment(
                direction = MessageDirection.BALANCE_ONLY,
                operationKind = MessageOperationKind.BALANCE_INQUIRY,
                confidence = 0.90f,
                reasonAr = "استعلام رصيد بدون خصم أو إضافة",
                reasonEn = "Balance inquiry only"
            )
        }

        // 4. Balance-delta consistency verification if prior balance is known
        var deltaNoteAr: String? = null
        var deltaNoteEn: String? = null
        if (balanceAfterPiasters != null && knownAccountBalancePiasters != null && knownAccountBalancePiasters >= 0L) {
            when {
                balanceAfterPiasters > knownAccountBalancePiasters -> {
                    inScore += 4.0f
                    deltaNoteAr = "زيادة في الرصيد مقارنة بالحساب"
                    deltaNoteEn = "Balance increased vs tracked account"
                }
                balanceAfterPiasters < knownAccountBalancePiasters -> {
                    outScore += 4.0f
                    deltaNoteAr = "انخفاض في الرصيد مقارنة بالحساب"
                    deltaNoteEn = "Balance decreased vs tracked account"
                }
            }
        }

        // 5. Self-learned MerchantRule prior when textual evidence is absent or near-tied
        if (learnedPreferredType != null) {
            when (learnedPreferredType) {
                TransactionType.INCOME -> inScore += 1.5f
                TransactionType.EXPENSE -> outScore += 1.5f
                else -> {}
            }
        }

        if (inScore == 0f && outScore == 0f) {
            return DirectionAssessment(
                direction = MessageDirection.UNKNOWN,
                operationKind = MessageOperationKind.CARD_PURCHASE,
                confidence = if (hasTransactionAmount) 0.45f else 0.25f,
                reasonAr = "لم يرد مؤشر اتجاه صريح",
                reasonEn = "No explicit direction marker"
            )
        }

        return if (inScore > outScore) {
            val margin = (inScore - outScore).coerceAtLeast(0.5f)
            val conf = (0.68f + (margin / 10f)).coerceIn(0.68f, 0.98f)
            DirectionAssessment(
                direction = MessageDirection.INCOMING,
                operationKind = bestInCue?.suggestedKind ?: MessageOperationKind.INCOMING_TRANSFER,
                confidence = conf,
                reasonAr = deltaNoteAr ?: bestInCue?.labelAr ?: "أموال واردة لحسابك",
                reasonEn = deltaNoteEn ?: bestInCue?.labelEn ?: "Incoming funds"
            )
        } else {
            val margin = (outScore - inScore).coerceAtLeast(0.5f)
            val conf = (0.68f + (margin / 10f)).coerceIn(0.68f, 0.98f)
            DirectionAssessment(
                direction = MessageDirection.OUTGOING,
                operationKind = bestOutCue?.suggestedKind ?: MessageOperationKind.OUTGOING_TRANSFER,
                confidence = conf,
                reasonAr = deltaNoteAr ?: bestOutCue?.labelAr ?: "أموال صادرة / خصم",
                reasonEn = deltaNoteEn ?: bestOutCue?.labelEn ?: "Outgoing payment"
            )
        }
    }
}

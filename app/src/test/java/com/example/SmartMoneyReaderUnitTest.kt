package com.example

import com.example.data.mock.MockSmsRepository
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.TransactionType
import com.example.domain.reader.FinancialMessageParser
import com.example.domain.reader.ParseOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmartMoneyReaderUnitTest {

    private val parser = FinancialMessageParser()

    @Test
    fun `parses arabic incoming transfer with eastern digits from Vodafone Cash`() {
        val msg = "فودافون كاش: تم استلام مبلغ ١,٥٠٠.٥٠ ج.م تحويل وارد من احمد علي رقم المرجع VF998877 الرصيد المتاح ٢,٣٠٠.٥٠ ج.م"
        val outcome = parser.parse(msg, sender = "VodafoneCash")
        assertTrue(outcome is ParseOutcome.Financial)
        val parsed = (outcome as ParseOutcome.Financial).parsed
        assertEquals("VODAFONE_CASH", parsed.sourceCode)
        assertEquals(MessageOperationKind.INCOMING_TRANSFER, parsed.operationKind)
        assertEquals(TransactionType.INCOME, parsed.mappedType)
        assertEquals(150_050L, parsed.amountPiasters)
        assertEquals("VF998877", parsed.referenceNumber)
        assertEquals(230_050L, parsed.balanceAfterPiasters)
        assertEquals("EGP", parsed.currency)
    }

    @Test
    fun `parses arabic outgoing transfer with separate fee`() {
        val msg = "إنستاباي: تم تحويل مبلغ 2000 جنيه إلى محمد حسن برسوم 5 جنيه رقم العملية IPN445511"
        val outcome = parser.parse(msg, sender = "InstaPay")
        assertTrue(outcome is ParseOutcome.Financial)
        val parsed = (outcome as ParseOutcome.Financial).parsed
        assertEquals("INSTAPAY", parsed.sourceCode)
        assertEquals(MessageOperationKind.OUTGOING_TRANSFER, parsed.operationKind)
        assertEquals(TransactionType.EXPENSE, parsed.mappedType)
        assertEquals(200_000L, parsed.amountPiasters)
        assertEquals(500L, parsed.feePiasters)
        assertNotNull(parsed.feeDedupFingerprint)
        assertEquals("IPN445511", parsed.referenceNumber)
    }

    @Test
    fun `parses deposit transaction`() {
        val msg = "البنك الأهلي المصري: تم إيداع مبلغ 5000 ج.م في حسابك الرصيد الحالي 12500 ج.م"
        val outcome = parser.parse(msg, sender = "NBE")
        assertTrue(outcome is ParseOutcome.Financial)
        val parsed = (outcome as ParseOutcome.Financial).parsed
        assertEquals("NBE", parsed.sourceCode)
        assertEquals(MessageOperationKind.DEPOSIT, parsed.operationKind)
        assertEquals(TransactionType.INCOME, parsed.mappedType)
        assertEquals(500_000L, parsed.amountPiasters)
        assertEquals(1_250_000L, parsed.balanceAfterPiasters)
    }

    @Test
    fun `parses ATM withdrawal transaction`() {
        val msg = "بنك مصر: تم سحب نقدي بمبلغ 800 جنيه من صراف آلي رمسيس Ref: BM77441"
        val outcome = parser.parse(msg, sender = "BanqueMisr")
        assertTrue(outcome is ParseOutcome.Financial)
        val parsed = (outcome as ParseOutcome.Financial).parsed
        assertEquals("BANQUE_MISR", parsed.sourceCode)
        assertEquals(MessageOperationKind.WITHDRAWAL, parsed.operationKind)
        assertEquals(TransactionType.EXPENSE, parsed.mappedType)
        assertEquals(80_000L, parsed.amountPiasters)
        assertEquals("BM77441", parsed.referenceNumber)
    }

    @Test
    fun `parses english CIB card purchase`() {
        val msg = "CIB: Purchase of 450.75 EGP at Carrefour with card ending 4321. Available balance 9549.25 EGP Ref: TXN8821"
        val outcome = parser.parse(msg, sender = "CIB")
        assertTrue(outcome is ParseOutcome.Financial)
        val parsed = (outcome as ParseOutcome.Financial).parsed
        assertEquals("CIB", parsed.sourceCode)
        assertEquals(MessageOperationKind.CARD_PURCHASE, parsed.operationKind)
        assertEquals(TransactionType.EXPENSE, parsed.mappedType)
        assertEquals(45_075L, parsed.amountPiasters)
        assertEquals("TXN8821", parsed.referenceNumber)
        assertEquals(954_925L, parsed.balanceAfterPiasters)
    }

    @Test
    fun `parses standalone transfer fee`() {
        val msg = "تم خصم رسوم تحويل بمبلغ 10.00 ج.م على حسابك في فودافون كاش"
        val outcome = parser.parse(msg, sender = "VodafoneCash")
        assertTrue(outcome is ParseOutcome.Financial)
        val parsed = (outcome as ParseOutcome.Financial).parsed
        assertEquals(MessageOperationKind.TRANSFER_FEE, parsed.operationKind)
        assertEquals(TransactionType.EXPENSE, parsed.mappedType)
        assertEquals(1_000L, parsed.amountPiasters)
    }

    @Test
    fun `balance inquiry is recognized separately and not treated as transaction`() {
        val msg = "فودافون كاش: استعلام عن الرصيد، رصيدك المتاح حاليا 3450.00 ج.م"
        val outcome = parser.parse(msg, sender = "VodafoneCash")
        assertTrue(outcome is ParseOutcome.BalanceInquiryOnly)
        val bal = outcome as ParseOutcome.BalanceInquiryOnly
        assertEquals(345_000L, bal.balancePiasters)
    }

    @Test
    fun `OTP and verification codes are strictly ignored`() {
        val arOtp = parser.parse("كود التحقق OTP الخاص بك هو 849201 لا تشارك هذا الرمز مع أي شخص", sender = "CIB")
        val enOtp = parser.parse("Your one-time password verification code is 492811. Do not share it.", sender = "NBE")
        assertTrue(arOtp is ParseOutcome.IgnoredOtpOrSecurity)
        assertTrue(enOtp is ParseOutcome.IgnoredOtpOrSecurity)
    }

    @Test
    fun `marketing and non-financial messages are ignored`() {
        val promo = parser.parse("عرض خاص من اورنج كاش: اكسب كاش باك يصل إلى 50% عند الاشتراك الآن!", sender = "OrangeCash")
        val personal = parser.parse("السلام عليكم، نتقابل الساعة 6 في المهندسين إن شاء الله", sender = "Friend")
        assertTrue(promo is ParseOutcome.IgnoredNonFinancial)
        assertTrue(personal is ParseOutcome.IgnoredNonFinancial)
    }

    @Test
    fun `incomplete financial message routes to IncompleteNeedsReview`() {
        val incomplete = parser.parse("البنك الأهلي: تم تنفيذ عملية شراء بالبطاقة بنجاح", sender = "NBE")
        assertTrue(incomplete is ParseOutcome.IncompleteNeedsReview)
        val partial = (incomplete as ParseOutcome.IncompleteNeedsReview).partial
        assertEquals(0L, partial.amountPiasters)
        assertTrue(partial.isIncomplete)
    }

    @Test
    fun `duplicate identical messages generate identical dedup fingerprint`() {
        val msg1 = "InstaPay: Received 750 EGP from Omar Ref: IPN550011"
        val msg2 = "InstaPay: Received 750 EGP from Omar Ref: IPN550011"
        val p1 = (parser.parse(msg1, "InstaPay") as ParseOutcome.Financial).parsed
        val p2 = (parser.parse(msg2, "InstaPay") as ParseOutcome.Financial).parsed
        assertEquals(p1.dedupFingerprint, p2.dedupFingerprint)
    }

    @Test
    fun `strategy pattern selects specific bank wallet and instapay strategies and extracts dates`() {
        val instapay = (parser.parse(
            "إنستاباي: تم تحويل مبلغ 500 جنيه بتاريخ 12/11/2026 14:25 رقم العملية IPN1234",
            "InstaPay"
        ) as ParseOutcome.Financial).parsed
        assertEquals("strategy_instapay", instapay.matchedStrategyId)

        val cal = Calendar.getInstance().apply { timeInMillis = instapay.transactionTimestampMillis }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(12, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(25, cal.get(Calendar.MINUTE))

        val wallet = (parser.parse(
            "Vodafone Cash: Received 300 EGP Ref: VF1122",
            "VodafoneCash"
        ) as ParseOutcome.Financial).parsed
        assertEquals("strategy_egyptian_wallets", wallet.matchedStrategyId)

        val bank = (parser.parse(
            "CIB: Purchase of 200 EGP at Store Ref: CIB11",
            "CIB"
        ) as ParseOutcome.Financial).parsed
        assertEquals("strategy_egyptian_banks", bank.matchedStrategyId)

        val generic = (parser.parse(
            "AlRajhi: تم خصم مبلغ 120 جنيه عملية شراء",
            "AlRajhi"
        ) as ParseOutcome.Financial).parsed
        assertEquals("strategy_generic_bilingual", generic.matchedStrategyId)
    }

    @Test
    fun `mock data layer integrates with FinancialMessageParser across Arabic and English samples`() {
        val mockRepo = MockSmsRepository(parser)
        val previews = mockRepo.fetchAndParseSampleMessages()
        assertEquals(8, previews.size)

        val financial = previews.mapNotNull { (it.outcome as? ParseOutcome.Financial)?.parsed }
        val otpIgnored = previews.count { it.outcome is ParseOutcome.IgnoredOtpOrSecurity }
        val balanceOnly = previews.count { it.outcome is ParseOutcome.BalanceInquiryOnly }

        assertEquals(6, financial.size)
        assertEquals(1, otpIgnored)
        assertEquals(1, balanceOnly)
        assertTrue(financial.any { it.sourceCode == "CIB" && it.amountPiasters == 68_050L })
        assertTrue(financial.any { it.sourceCode == "NBE" && it.amountPiasters == 1_500_000L })
        assertTrue(financial.any { it.sourceCode == "VODAFONE_CASH" && it.amountPiasters == 125_000L })
        assertTrue(financial.any { it.sourceCode == "INSTAPAY" && it.feePiasters == 500L })
    }
}

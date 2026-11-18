package com.example.data.remote

import android.content.Context
import androidx.room.withTransaction
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AiChatMessageEntity
import com.example.data.local.entity.AiChatSessionEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class CloudSyncSummary(
    val accountsCount: Int,
    val categoriesCount: Int,
    val transactionsCount: Int,
    val budgetsCount: Int,
    val goalsCount: Int,
    val merchantRulesCount: Int,
    val syncedAtMillis: Long = System.currentTimeMillis(),
    val aiChatsCount: Int = 0,
    val installmentsCount: Int = 0,
    val recurringRulesCount: Int = 0,
    val debtsCount: Int = 0,
    val bnplProgramsCount: Int = 0,
    val financialSourcesCount: Int = 0,
    val certificatesCount: Int = 0
)

class CloudSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDocRef(uid: String) = db.collection("users").document(uid)

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val ref = userDocRef(uid)
        emitAll(
            ref.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, ref.path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudTransactions(): Flow<List<CloudTransaction>> = flow {
        val uid = requireUserId()
        val colRef = userDocRef(uid).collection("transactions")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudAccounts(): Flow<List<CloudAccount>> = flow {
        val uid = requireUserId()
        val colRef = userDocRef(uid).collection("accounts")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertUserProfile(
        displayName: String,
        email: String?,
        currencyCode: String,
        themePreset: String?,
        lastSyncedAtMillis: Long? = null
    ): Result<UserProfile> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val ref = userDocRef(uid)
        return try {
            val existingSnap = ref.get().await()
            val existingProfile = existingSnap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val normalizedCurrency = if (currencyCode in SUPPORTED_CURRENCIES) currencyCode else "EGP"
            val payload = UserProfile(
                id = uid,
                userId = uid,
                displayName = displayName.trim().ifBlank { "ELBAGORRY User" }.take(100),
                email = email?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                currencyCode = normalizedCurrency,
                themePreset = themePreset?.take(50),
                lastSyncedAtMillis = lastSyncedAtMillis ?: existingProfile?.lastSyncedAtMillis,
                createdAt = existingProfile?.createdAt,
                updatedAt = null
            )
            ref.set(payload).await()
            val saved = ref.get().await().toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: payload
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                OperationType.WRITE,
                ref.path
            )
            Result.failure(e)
        }
    }

    suspend fun getUserProfileOnce(): Result<UserProfile?> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val ref = userDocRef(uid)
        return try {
            val snap = ref.get().await()
            val profile = snap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ref.path)
            Result.failure(e)
        }
    }

    suspend fun upsertAccount(account: CloudAccount): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = account.id.ifBlank { "acc_${account.localId}" }
        val ref = userDocRef(uid).collection("accounts").document(docId)
        return try {
            val existingSnap = ref.get().await()
            val existingCreatedAt = if (existingSnap.exists()) existingSnap.getTimestamp("createdAt") else null
            val payload = account.copy(
                id = docId,
                createdAt = existingCreatedAt,
                updatedAt = null
            )
            ref.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun getAccountById(docId: String): Result<CloudAccount?> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val ref = userDocRef(uid).collection("accounts").document(docId)
        return try {
            val snap = ref.get().await()
            Result.success(
                snap.toObject(
                    CloudAccount::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ref.path)
            Result.failure(e)
        }
    }

    suspend fun upsertTransaction(tx: CloudTransaction): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docId = tx.id.ifBlank { "tx_${tx.localId}" }
        val ref = userDocRef(uid).collection("transactions").document(docId)
        return try {
            val existingSnap = ref.get().await()
            val existingCreatedAt = if (existingSnap.exists()) existingSnap.getTimestamp("createdAt") else null
            val payload = tx.copy(
                id = docId,
                createdAt = existingCreatedAt,
                updatedAt = null
            )
            ref.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTransactionDoc(docId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val ref = userDocRef(uid).collection("transactions").document(docId)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun pushRoomToCloud(
        localDb: AppDatabase,
        displayName: String,
        email: String?,
        currencyCode: String,
        themePreset: String?
    ): Result<CloudSyncSummary> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val now = System.currentTimeMillis()
            upsertUserProfile(
                displayName = displayName,
                email = email,
                currencyCode = currencyCode,
                themePreset = themePreset,
                lastSyncedAtMillis = now
            ).getOrThrow()

            val userRef = userDocRef(uid)
            val accounts = localDb.accountDao().getAllOnce()
            val categories = localDb.categoryDao().getAllOnce()
            val transactions = localDb.transactionDao().getAllOnce()
            val budgets = localDb.budgetDao().getAllOnce()
            val goals = localDb.savingsGoalDao().getAllOnce()
            val merchantRules = localDb.merchantRuleDao().getAllOnce()
            val installments = localDb.installmentDao().getAllOnce()
            val recurringRules = localDb.recurringRuleDao().getAllOnce()
            val debts = localDb.debtDao().getAllOnce()
            val bnplPrograms = localDb.bnplProgramDao().getAllOnce()
            val financialSources = localDb.financialSourceDao().getAllOnce()
            val certificates = localDb.savingsCertificateDao().getAllOnce()

            syncCollectionUpserts(
                colPath = "accounts",
                items = accounts.map { "acc_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "categories",
                items = categories.map { "cat_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "transactions",
                items = transactions.map { "tx_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "budgets",
                items = budgets.map { "bud_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "goals",
                items = goals.map { "goal_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "merchant_rules",
                items = merchantRules.map { "rule_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "installments",
                items = installments.map { "inst_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "recurring_rules",
                items = recurringRules.map { "rec_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "debts",
                items = debts.map { "debt_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "bnpl_programs",
                items = bnplPrograms.map { "bnpl_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "financial_sources",
                items = financialSources.map { "src_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )
            syncCollectionUpserts(
                colPath = "savings_certificates",
                items = certificates.map { "cert_${it.id}" to it.toCloudModel() },
                withCreatedAt = { model, ts -> model.copy(createdAt = ts, updatedAt = null) },
                userRef = userRef
            )

            val aiChatSessions = localDb.aiChatDao().getAllSessionsOnce()
            aiChatSessions.forEach { session ->
                val msgs = localDb.aiChatDao().getMessagesForSessionOnce(session.id)
                syncAiChatSessionInternal(userRef, session, msgs)
                localDb.aiChatDao().markSessionSynced(session.id, true)
            }

            Result.success(
                CloudSyncSummary(
                    accountsCount = accounts.size,
                    categoriesCount = categories.size,
                    transactionsCount = transactions.size,
                    budgetsCount = budgets.size,
                    goalsCount = goals.size,
                    merchantRulesCount = merchantRules.size,
                    syncedAtMillis = now,
                    aiChatsCount = aiChatSessions.size,
                    installmentsCount = installments.size,
                    recurringRulesCount = recurringRules.size,
                    debtsCount = debts.size,
                    bnplProgramsCount = bnplPrograms.size,
                    financialSourcesCount = financialSources.size,
                    certificatesCount = certificates.size
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun syncAiChatSession(
        session: AiChatSessionEntity,
        messages: List<AiChatMessageEntity>
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val userRef = userDocRef(uid)
        return try {
            syncAiChatSessionInternal(userRef, session, messages)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/ai_chats/${session.id}")
            Result.failure(e)
        }
    }

    private suspend fun syncAiChatSessionInternal(
        userRef: com.google.firebase.firestore.DocumentReference,
        session: AiChatSessionEntity,
        messages: List<AiChatMessageEntity>
    ) {
        val chatId = session.id.trim()
        if (chatId.isEmpty()) return
        val sessionRef = userRef.collection("ai_chats").document(chatId)
        val existingSessionSnap = sessionRef.get().await()
        val existingSessionCreatedAt = if (existingSessionSnap.exists()) {
            existingSessionSnap.getTimestamp("createdAt")
        } else null

        val messagesCol = sessionRef.collection("messages")
        val existingMsgCreatedAt = if (existingSessionSnap.exists()) {
            messagesCol.get().await().documents.associate { it.id to it.getTimestamp("createdAt") }
        } else emptyMap()

        val validMsgs = messages.filter { it.id.isNotBlank() }
        val normalizedSession = session.copy(
            messageCount = validMsgs.size.coerceAtLeast(session.messageCount)
        ).toCloudModel(existingSessionCreatedAt)

        // Commit session doc + first chunk atomically so existsAfter() parent guard succeeds
        val allOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Any>>()
        allOps += sessionRef to normalizedSession
        validMsgs.forEach { msg ->
            val msgRef = messagesCol.document(msg.id)
            allOps += msgRef to msg.toCloudModel(existingMsgCreatedAt[msg.id])
        }

        allOps.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, payload) ->
                batch.set(ref, payload)
            }
            batch.commit().await()
        }
    }

    suspend fun deleteAiChatSessionDoc(sessionId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val chatRef = userDocRef(uid).collection("ai_chats").document(sessionId)
        return try {
            val msgsSnap = chatRef.collection("messages").get().await()
            val refsToDelete = msgsSnap.documents.map { it.reference } + chatRef
            refsToDelete.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, chatRef.path)
            Result.failure(e)
        }
    }

    suspend fun pullAllAiChats(): Result<Pair<List<AiChatSessionEntity>, Map<String, List<AiChatMessageEntity>>>> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val userRef = userDocRef(uid)
        return try {
            val result = pullAllAiChatsInternal(userRef)
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$uid/ai_chats")
            Result.failure(e)
        }
    }

    private suspend fun pullAllAiChatsInternal(
        userRef: com.google.firebase.firestore.DocumentReference
    ): Pair<List<AiChatSessionEntity>, Map<String, List<AiChatMessageEntity>>> {
        val sessionsSnap = userRef.collection("ai_chats").get().await()
        val sessions = sessionsSnap.toObjects(
            CloudAiChatSession::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).map { it.toEntity() }

        val messagesMap = LinkedHashMap<String, List<AiChatMessageEntity>>(sessions.size)
        for (session in sessions) {
            val msgSnap = userRef.collection("ai_chats")
                .document(session.id)
                .collection("messages")
                .get()
                .await()
            messagesMap[session.id] = msgSnap.toObjects(
                CloudAiChatMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.toEntity() }.sortedBy { it.timestampMillis }
        }
        return sessions to messagesMap
    }

    suspend fun pullCloudToRoom(localDb: AppDatabase): Result<CloudSyncSummary> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val userRef = userDocRef(uid)
        return try {
            val accSnap = userRef.collection("accounts").get().await()
            val catSnap = userRef.collection("categories").get().await()
            val txSnap = userRef.collection("transactions").get().await()
            val budSnap = userRef.collection("budgets").get().await()
            val goalSnap = userRef.collection("goals").get().await()
            val ruleSnap = userRef.collection("merchant_rules").get().await()
            val instSnap = userRef.collection("installments").get().await()
            val recSnap = userRef.collection("recurring_rules").get().await()
            val debtSnap = userRef.collection("debts").get().await()
            val bnplSnap = userRef.collection("bnpl_programs").get().await()
            val srcSnap = userRef.collection("financial_sources").get().await()
            val certSnap = userRef.collection("savings_certificates").get().await()
            val (aiSessions, aiMessagesBySession) = pullAllAiChatsInternal(userRef)

            val accounts = accSnap.toObjects(CloudAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).map { it.toEntity() }
            val categories = catSnap.toObjects(CloudCategory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).map { it.toEntity() }
            val validAccountIds = accounts.map { it.id }.toSet()
            val validCategoryIds = categories.map { it.id }.toSet()
            val transactions = txSnap.toObjects(CloudTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .map { it.toEntity() }
                .filter { it.accountId in validAccountIds }
                .map { tx ->
                    tx.copy(
                        targetAccountId = tx.targetAccountId?.takeIf { it in validAccountIds },
                        categoryId = tx.categoryId?.takeIf { it in validCategoryIds }
                    )
                }
            val budgets = budSnap.toObjects(CloudBudget::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .map { it.toEntity() }
                .map { b -> b.copy(categoryId = b.categoryId?.takeIf { it in validCategoryIds }) }
            val goals = goalSnap.toObjects(CloudSavingsGoal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).map { it.toEntity() }
            val rules = ruleSnap.toObjects(CloudMerchantRule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .map { it.toEntity() }
                .map { r ->
                    r.copy(
                        categoryId = r.categoryId?.takeIf { it in validCategoryIds },
                        accountId = r.accountId?.takeIf { it in validAccountIds }
                    )
                }
            val installments = instSnap.toObjects(CloudInstallment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .map { it.toEntity() }
                .map { i -> i.copy(accountId = i.accountId?.takeIf { it in validAccountIds }) }
            val recurringRules = recSnap.toObjects(CloudRecurringRule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .map { it.toEntity() }
                .filter { it.accountId in validAccountIds }
                .map { rr -> rr.copy(categoryId = rr.categoryId?.takeIf { it in validCategoryIds }) }
            val debts = debtSnap.toObjects(CloudDebt::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).map { it.toEntity() }
            val bnplPrograms = bnplSnap.toObjects(CloudBnplProgram::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).map { it.toEntity() }
            val financialSources = srcSnap.toObjects(CloudFinancialSource::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .map { it.toEntity() }
                .map { s -> s.copy(defaultAccountId = s.defaultAccountId?.takeIf { it in validAccountIds }) }
            val certificates = certSnap.toObjects(CloudSavingsCertificate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .map { it.toEntity() }
                .map { c -> c.copy(linkedAccountId = c.linkedAccountId?.takeIf { it in validAccountIds }) }

            if (accounts.isEmpty() && categories.isEmpty() && transactions.isEmpty() && aiSessions.isEmpty() &&
                installments.isEmpty() && recurringRules.isEmpty() && debts.isEmpty() && certificates.isEmpty()
            ) {
                return Result.success(CloudSyncSummary(0, 0, 0, 0, 0, 0, aiChatsCount = 0))
            }

            localDb.withTransaction {
                if (accounts.isNotEmpty()) localDb.accountDao().insertAll(accounts)
                if (categories.isNotEmpty()) localDb.categoryDao().insertAll(categories)
                if (goals.isNotEmpty()) localDb.savingsGoalDao().insertAll(goals)
                if (bnplPrograms.isNotEmpty()) localDb.bnplProgramDao().insertAll(bnplPrograms)
                if (financialSources.isNotEmpty()) localDb.financialSourceDao().insertAll(financialSources)
                if (debts.isNotEmpty()) localDb.debtDao().insertAll(debts)
                if (installments.isNotEmpty()) localDb.installmentDao().insertAll(installments)
                if (recurringRules.isNotEmpty()) localDb.recurringRuleDao().insertAll(recurringRules)
                if (budgets.isNotEmpty()) localDb.budgetDao().insertAll(budgets)
                if (certificates.isNotEmpty()) localDb.savingsCertificateDao().insertAll(certificates)
                if (transactions.isNotEmpty()) localDb.transactionDao().insertAll(transactions)
                if (rules.isNotEmpty()) localDb.merchantRuleDao().insertAll(rules)
                if (aiSessions.isNotEmpty()) {
                    localDb.aiChatDao().insertSessions(aiSessions.map { it.copy(syncedToCloud = true) })
                    aiMessagesBySession.values.flatten().let { allMsgs ->
                        if (allMsgs.isNotEmpty()) localDb.aiChatDao().insertMessages(allMsgs)
                    }
                }
            }

            Result.success(
                CloudSyncSummary(
                    accountsCount = accounts.size,
                    categoriesCount = categories.size,
                    transactionsCount = transactions.size,
                    budgetsCount = budgets.size,
                    goalsCount = goals.size,
                    merchantRulesCount = rules.size,
                    aiChatsCount = aiSessions.size,
                    installmentsCount = installments.size,
                    recurringRulesCount = recurringRules.size,
                    debtsCount = debts.size,
                    bnplProgramsCount = bnplPrograms.size,
                    financialSourcesCount = financialSources.size,
                    certificatesCount = certificates.size
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, userRef.path)
            Result.failure(e)
        }
    }

    private suspend fun <T : Any> syncCollectionUpserts(
        colPath: String,
        items: List<Pair<String, T>>,
        withCreatedAt: (T, com.google.firebase.Timestamp?) -> T,
        userRef: com.google.firebase.firestore.DocumentReference
    ) {
        val colRef = userRef.collection(colPath)
        val existingSnap = colRef.get().await()
        val existingCreatedAtMap = existingSnap.documents.associate { doc ->
            doc.id to doc.getTimestamp("createdAt")
        }
        val targetDocIds = items.map { it.first }.toSet()

        // Batch writes in chunks of 400 (well under Firestore's 500-op batch limit)
        val allOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, T?>>()
        items.forEach { (docId, model) ->
            val createdAt = existingCreatedAtMap[docId]
            allOps += colRef.document(docId) to withCreatedAt(model, createdAt)
        }
        // Delete stale cloud items removed locally
        existingCreatedAtMap.keys
            .filter { it !in targetDocIds }
            .forEach { staleId ->
                allOps += colRef.document(staleId) to null
            }

        if (allOps.isEmpty()) return

        allOps.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (docRef, payload) ->
                if (payload == null) {
                    batch.delete(docRef)
                } else {
                    batch.set(docRef, payload)
                }
            }
            batch.commit().await()
        }
    }

    companion object {
        private val SUPPORTED_CURRENCIES = setOf("EGP", "USD", "SAR", "EUR", "AED", "GBP", "KWD")
    }
}

package com.example.data.remote

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.AppDatabase
import com.example.domain.model.AccountType
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertUserProfile_authenticatedOwner_createsAndUpdatesProfile() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore)

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertUserProfile(
                displayName = "Alice Elbagorry",
                email = ALICE_EMAIL,
                currencyCode = "EGP",
                themePreset = "EMERALD_OBSIDIAN"
            )
        }
        assertTrue(created.isSuccess)
        assertEquals(uid, created.getOrThrow().userId)

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertUserProfile(
                displayName = "Alice Updated",
                email = ALICE_EMAIL,
                currencyCode = "USD",
                themePreset = "ROYAL_SAPPHIRE"
            )
        }
        assertTrue(updated.isSuccess)
        assertEquals("USD", updated.getOrThrow().currencyCode)
    }

    @Test
    fun upsertAccountAndObserveTransactions_authenticatedOwner_succeedsAndStreamsRealtime() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore)
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val accDocId = "acc_$suffix"
        val txDocId = "tx_$suffix"

        val accRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertAccount(
                CloudAccount(
                    id = accDocId,
                    localId = 101L,
                    name = "Cloud Wallet",
                    accountType = AccountType.E_WALLET.name,
                    openingBalancePiasters = 50_000L,
                    currentBalancePiasters = 75_000L,
                    currency = "EGP",
                    colorHex = "#10B981",
                    iconName = "wallet",
                    isArchived = false
                )
            )
        }
        assertTrue(accRes.isSuccess)

        val txRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertTransaction(
                CloudTransaction(
                    id = txDocId,
                    localId = 202L,
                    amountPiasters = 12_500L,
                    transactionType = TransactionType.EXPENSE.name,
                    accountLocalId = 101L,
                    description = "Cloud Synced Coffee",
                    notes = "Espresso",
                    source = TransactionSource.MANUAL_ENTRY.name,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }
        assertTrue(txRes.isSuccess)

        val streamed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCloudTransactions().first { list -> list.any { it.id == txDocId } }
        }
        assertTrue(streamed.any { it.id == txDocId && it.amountPiasters == 12_500L })
    }

    @Test
    fun pushAndPullRoomToCloud_roundTripsLocalDatabaseSafely() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            AppDatabase.seedDefaults(localDb)
            val repo = CloudSyncRepository(firestore)

            val pushRes = withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.pushRoomToCloud(
                    localDb = localDb,
                    displayName = "Alice",
                    email = ALICE_EMAIL,
                    currencyCode = "EGP",
                    themePreset = "EMERALD_OBSIDIAN"
                )
            }
            assertTrue(pushRes.isSuccess)
            val pushed = pushRes.getOrThrow()
            assertTrue(pushed.accountsCount >= 3)
            assertTrue(pushed.categoriesCount >= 6)

            // Clear local and pull back from Firestore
            localDb.transactionDao().deleteAll()
            localDb.accountDao().deleteAll()
            localDb.categoryDao().deleteAll()

            val pullRes = withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.pullCloudToRoom(localDb)
            }
            assertTrue(pullRes.isSuccess)
            assertTrue(localDb.accountDao().getAllOnce().size >= 3)
            assertTrue(localDb.categoryDao().getAllOnce().size >= 6)
        } finally {
            localDb.close()
        }
    }

    @Test
    fun getAccountById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore)
        val docId = "acc_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertAccount(
                CloudAccount(
                    id = docId,
                    localId = 555L,
                    name = "Alice Private Vault",
                    accountType = AccountType.BANK.name,
                    openingBalancePiasters = 100_000L,
                    currentBalancePiasters = 100_000L
                )
            )
        }
        assertTrue(created.isSuccess)

        // Switch to Bob and verify cross-user direct Firestore read fails with PERMISSION_DENIED
        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance(
                    com.google.firebase.FirebaseApp.getInstance(),
                    databaseId
                ).collection("users").document(aliceUid).collection("accounts").document(docId)
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED for cross-user account access")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    @Test
    fun observeTransactions_unauthenticatedUser_failsFast() = runBlocking {
        auth.signOut()
        val repo = CloudSyncRepository(firestore)

        // 1. Repository fail-fast guard rejects unauthenticated Flow
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeCloudTransactions().first()
            }
            fail("Expected IllegalStateException when unauthenticated")
        } catch (_: IllegalStateException) {
            // Expected fail-fast check
        }

        // 2. Direct Firestore SDK query without auth is rejected by security rules with PERMISSION_DENIED
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document("some_uid").collection("transactions").get().await()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    @Test
    fun syncAiChatSession_ownerRoundTripsAndCrossUserIsDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = CloudSyncRepository(firestore)
            val chatId = "chat_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val now = System.currentTimeMillis()
            val session = com.example.data.local.entity.AiChatSessionEntity(
                id = chatId,
                title = "مصاريف اليوم",
                lastMessagePreview = "تم التسجيل محليًا بدون إنترنت",
                messageCount = 2,
                createdAtMillis = now,
                updatedAtMillis = now + 500L
            )
            val msgs = listOf(
                com.example.data.local.entity.AiChatMessageEntity(
                    id = "msg_u_$chatId",
                    sessionId = chatId,
                    isUser = true,
                    text = "صرفت 50 اكل و 20 مواصلات",
                    engineSource = "LOCAL_OFFLINE",
                    timestampMillis = now + 100L
                ),
                com.example.data.local.entity.AiChatMessageEntity(
                    id = "msg_a_$chatId",
                    sessionId = chatId,
                    isUser = false,
                    text = "تم التنفيذ محليًا بدون إنترنت",
                    engineSource = "LOCAL_OFFLINE",
                    timestampMillis = now + 500L
                )
            )
            localDb.aiChatDao().insertSession(session)
            localDb.aiChatDao().insertMessages(msgs)

            val syncRes = withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.syncAiChatSession(session, msgs)
            }
            assertTrue("Expected syncAiChatSession success but got ${syncRes.exceptionOrNull()}", syncRes.isSuccess)

            val pulled = withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.pullAllAiChats()
            }
            assertTrue(pulled.isSuccess)
            val (sessions, msgMap) = pulled.getOrThrow()
            assertTrue(sessions.any { it.id == chatId && it.title == "مصاريف اليوم" })
            assertEquals(2, msgMap[chatId]?.size)

            // Cross-user read by Bob must be denied
            signInTestUser(BOB_EMAIL)
            try {
                withTimeout(DEFAULT_TIMEOUT_MS) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance(
                        com.google.firebase.FirebaseApp.getInstance(),
                        databaseId
                    ).collection("users").document(aliceUid)
                        .collection("ai_chats").document(chatId)
                        .collection("messages").get().await()
                }
                fail("Expected PERMISSION_DENIED for cross-user AI chat messages access")
            } catch (e: Exception) {
                val fsError = e.unwrapFirestoreException()
                assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
            }
        } finally {
            localDb.close()
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_money@test.com"
        const val BOB_EMAIL = "bob_money@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

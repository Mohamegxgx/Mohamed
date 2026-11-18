package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupAndExportManager
import com.example.data.backup.BackupEncryptionHelper
import com.example.data.local.AppDatabase
import com.example.data.local.ReceiptStorageManager
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.FinancialSourceEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.SavingsCertificateEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.toCloudModel
import com.example.data.remote.toEntity
import com.example.data.repository.MoneyRepository
import com.example.domain.engine.OfflineReceiptAnalyzer
import com.example.domain.model.AccountType
import com.example.domain.model.AppLanguage
import com.example.domain.model.BalanceDiscrepancy
import com.example.domain.model.MessageChannel
import com.example.domain.model.PayoutCycle
import com.example.domain.model.SavingsAssetKind
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import com.example.domain.reader.BankStatementImporter
import com.example.reader.IncomingRawMessage
import com.example.reader.SmartReaderProcessor
import com.example.ui.screens.AccountsAndGoalsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartMoneyReaderScreen
import com.example.ui.theme.ElbagorryMoneyTheme
import com.example.ui.viewmodel.MainUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoreGapsFeaturesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: MoneyRepository
    private lateinit var prefsRepo: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking { AppDatabase.seedDefaults(db) }
        repo = MoneyRepository(db)
        prefsRepo = UserPreferencesRepository(context)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `room migration 5 to 6 creates savings_certificates table and preserves existing data`() {
        val dbName = "migration_5_6_test_${System.nanoTime()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS accounts (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "name TEXT NOT NULL, type TEXT NOT NULL, " +
                                "openingBalancePiasters INTEGER NOT NULL, " +
                                "currentBalancePiasters INTEGER NOT NULL, " +
                                "currency TEXT NOT NULL, colorHex TEXT NOT NULL, " +
                                "iconName TEXT NOT NULL, isArchived INTEGER NOT NULL, " +
                                "createdAt INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        try {
            val sqliteDb = helper.writableDatabase
            sqliteDb.execSQL(
                "INSERT INTO accounts (name, type, openingBalancePiasters, currentBalancePiasters, currency, colorHex, iconName, isArchived, createdAt) " +
                    "VALUES ('NBE Legacy', 'BANK', 500000, 500000, 'EGP', '#0EA5E9', 'bank', 0, 1000)"
            )
            AppDatabase.MIGRATION_5_6.migrate(sqliteDb)

            sqliteDb.query("SELECT COUNT(*) FROM accounts WHERE name = 'NBE Legacy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }

            sqliteDb.execSQL(
                "INSERT INTO savings_certificates (title, assetKind, institutionName, principalPiasters, annualYieldPercent, payoutCycle, linkedAccountId, nextPayoutMillis, autoCreditInterest, grams, karat, pricePerGramPiasters, notes, isActive, createdAt) " +
                    "VALUES ('الشهادة البلاتينية', 'BANK_CERTIFICATE', 'NBE', 10000000, 27.0, 'MONTHLY', 1, 2000, 1, 0.0, 21, 0, '', 1, 1000)"
            )
            sqliteDb.query("SELECT title, principalPiasters FROM savings_certificates").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("الشهادة البلاتينية", cursor.getString(0))
                assertEquals(10_000_000L, cursor.getLong(1))
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `certificates idempotently credit monthly interest and gold prices update per karat`() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(
                name = "حساب الأهلي",
                type = AccountType.BANK,
                openingBalancePiasters = 1_000_000L,
                currentBalancePiasters = 1_000_000L
            )
        )
        val now = 1_750_000_000_000L
        val dueCertId = repo.saveCertificate(
            SavingsCertificateEntity(
                title = "الشهادة البلاتينية 27%",
                assetKind = SavingsAssetKind.BANK_CERTIFICATE,
                institutionName = "البنك الأهلي المصري",
                principalPiasters = 10_000_000L, // 100,000 EGP -> 2,250 EGP/mo = 225,000 piasters
                annualYieldPercent = 27.0,
                payoutCycle = PayoutCycle.MONTHLY,
                linkedAccountId = accId,
                nextPayoutMillis = now - 60_000L,
                autoCreditInterest = true
            )
        )
        assertTrue(dueCertId > 0L)

        val creditedCount = repo.executeDueCertificates(now)
        assertEquals(1, creditedCount)
        assertEquals(1_225_000L, db.accountDao().getById(accId)!!.currentBalancePiasters)

        // Running again immediately must remain strictly idempotent
        val secondPass = repo.executeDueCertificates(now)
        assertEquals(0, secondPass)
        assertEquals(1_225_000L, db.accountDao().getById(accId)!!.currentBalancePiasters)

        // Gold karat bulk update
        val bar1Id = repo.saveCertificate(
            SavingsCertificateEntity(
                title = "سبيكة ذهب 10 جم",
                assetKind = SavingsAssetKind.GOLD_RESERVE,
                grams = 10.0,
                karat = 21,
                pricePerGramPiasters = 380_000L
            )
        )
        val bar2Id = repo.saveCertificate(
            SavingsCertificateEntity(
                title = "سبيكة ذهب 24 عيار 24",
                assetKind = SavingsAssetKind.GOLD_RESERVE,
                grams = 5.0,
                karat = 24,
                pricePerGramPiasters = 440_000L
            )
        )
        repo.updateGoldPriceForKarat(21, 415_000L)
        assertEquals(415_000L, db.savingsCertificateDao().getById(bar1Id)!!.pricePerGramPiasters)
        assertEquals(440_000L, db.savingsCertificateDao().getById(bar2Id)!!.pricePerGramPiasters)
    }

    @Test
    fun `encrypted aes256 backup roundtrips schema v5 including certificates and rejects wrong password`() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(
                name = "المحفظة الرئيسية",
                type = AccountType.CASH,
                openingBalancePiasters = 250_000L,
                currentBalancePiasters = 250_000L
            )
        )
        db.transactionDao().insert(
            TransactionEntity(
                amountPiasters = 12_500L,
                type = TransactionType.EXPENSE,
                accountId = accId,
                description = "قهوة الصباح"
            )
        )
        repo.saveCertificate(
            SavingsCertificateEntity(
                title = "شهادة ادخار ثلاثية",
                assetKind = SavingsAssetKind.BANK_CERTIFICATE,
                principalPiasters = 5_000_000L,
                annualYieldPercent = 22.0,
                linkedAccountId = accId
            )
        )

        val backupManager = BackupAndExportManager(db)
        val encrypted = backupManager.createEncryptedJsonBackup("StrongPass!2026")
        assertTrue(backupManager.isEncryptedBackup(encrypted))
        assertTrue(BackupEncryptionHelper.isEncryptedBackup(encrypted))
        assertFalse(encrypted.contains("قهوة الصباح"))
        assertFalse(encrypted.contains("شهادة ادخار ثلاثية"))

        // Wrong password must fail cleanly
        val wrongRestore = backupManager.restoreAuto(encrypted, "WrongPass")
        assertTrue(wrongRestore.isFailure)

        // Wipe and restore with valid passphrase
        db.savingsCertificateDao().deleteAll()
        db.transactionDao().deleteAll()
        db.accountDao().deleteAll()
        assertEquals(0, db.savingsCertificateDao().getAllOnce().size)

        val okRestore = backupManager.restoreAuto(encrypted, "StrongPass!2026")
        assertTrue(okRestore.isSuccess)
        assertEquals(1, okRestore.getOrThrow())
        assertEquals(1, db.savingsCertificateDao().getAllOnce().size)
        assertEquals("شهادة ادخار ثلاثية", db.savingsCertificateDao().getAllOnce().first().title)
    }

    @Test
    fun `offline receipt analyzer decodes ZATCA TLV QR, ETA links, multi-line items, and bitmap fallback`() {
        fun encodeTlv(tag: Int, value: String): ByteArray {
            val valBytes = value.toByteArray(Charsets.UTF_8)
            return byteArrayOf(tag.toByte(), valBytes.size.toByte()) + valBytes
        }
        val tlvBytes = ByteArrayOutputStream().apply {
            write(encodeTlv(1, "كارفور مصر"))
            write(encodeTlv(2, "300012345678"))
            write(encodeTlv(3, "2026-11-17T10:15:00Z"))
            write(encodeTlv(4, "450.50"))
            write(encodeTlv(5, "58.76"))
        }.toByteArray()
        val qrB64 = Base64.getEncoder().encodeToString(tlvBytes)

        val qrResult = OfflineReceiptAnalyzer.analyzeOffline(
            textInput = qrB64,
            hasBitmap = false,
            accounts = emptyList(),
            categories = emptyList(),
            merchantRules = emptyList(),
            lang = AppLanguage.AR
        )
        assertEquals(45_050L, qrResult.amountPiasters)
        assertEquals("كارفور مصر", qrResult.description)
        assertTrue(qrResult.notes.contains("300012345678"))

        val etaResult = OfflineReceiptAnalyzer.analyzeOffline(
            textInput = "https://invoicing.eta.gov.eg/receipts/view?uuid=abc&total=275.25",
            hasBitmap = false,
            accounts = emptyList(),
            categories = emptyList(),
            merchantRules = emptyList(),
            lang = AppLanguage.AR
        )
        assertEquals(27_525L, etaResult.amountPiasters)

        val multiLineResult = OfflineReceiptAnalyzer.analyzeOffline(
            textInput = """
                شاي العروسة 45 ج.م
                لبن جهينة 55.50 ج.م
                الإجمالي 100.50 ج.م
            """.trimIndent(),
            hasBitmap = false,
            accounts = emptyList(),
            categories = emptyList(),
            merchantRules = emptyList(),
            lang = AppLanguage.AR
        )
        assertEquals(2, multiLineResult.items.size)
        assertEquals(10_050L, multiLineResult.amountPiasters)

        val bitmapFallback = OfflineReceiptAnalyzer.analyzeOffline(
            textInput = "",
            hasBitmap = true,
            accounts = emptyList(),
            categories = emptyList(),
            merchantRules = emptyList(),
            lang = AppLanguage.EN
        )
        assertTrue(bitmapFallback.description.contains("Receipt"))
    }

    @Test
    fun `csv bank statement importer maps columns, creates pending transactions, and skips duplicates`() = runBlocking {
        prefsRepo.setSmartReaderEnabled(true)
        db.accountDao().insert(
            AccountEntity(
                name = "CIB Bank",
                type = AccountType.BANK,
                openingBalancePiasters = 2_000_000L,
                currentBalancePiasters = 2_000_000L
            )
        )
        val csvContent = """
            التاريخ,البيان,خصم,إيداع,الرصيد
            2026-11-10,طلب مطعم برجر كنج,350.00,,19650.00
            2026-11-11,راتب إضافي من الشركة,,5000.00,24650.00
        """.trimIndent()

        val preview = BankStatementImporter.inspectStatement(csvContent)
        assertNotNull(preview)
        assertEquals(2, preview!!.totalRowCount)
        assertNotNull(preview.suggestedMapping.debitColumnIndex)
        assertNotNull(preview.suggestedMapping.creditColumnIndex)
        assertNotNull(preview.suggestedMapping.balanceColumnIndex)

        val processor = SmartReaderProcessor(db, prefsRepo)
        val firstBatch = processor.ingestBankStatement(csvContent, "CIB Bank")
        assertEquals(2, firstBatch.totalScanned)
        assertEquals(2, firstBatch.pendingCreated)

        val duplicateBatch = processor.ingestBankStatement(csvContent, "CIB Bank")
        assertEquals(2, duplicateBatch.duplicatesSkipped)
        assertEquals(0, duplicateBatch.pendingCreated)
    }

    @Test
    fun `smart reader detects balance discrepancy and repository reconciles account balance`() = runBlocking {
        prefsRepo.setSmartReaderEnabled(true)
        val accId = db.accountDao().insert(
            AccountEntity(
                name = "HSBC Egypt",
                type = AccountType.BANK,
                openingBalancePiasters = 500_000L,
                currentBalancePiasters = 500_000L
            )
        )
        db.financialSourceDao().insert(
            FinancialSourceEntity(
                code = "HSBC_TEST",
                displayNameAr = "بنك HSBC",
                displayNameEn = "HSBC",
                senderKeywords = "HSBC",
                packageNames = "",
                defaultAccountId = accId,
                isEnabled = true
            )
        )
        val processor = SmartReaderProcessor(db, prefsRepo)
        val result = processor.ingestMessages(
            messages = listOf(
                IncomingRawMessage(
                    sender = "HSBC",
                    body = "تم خصم مبلغ 200.00 EGP من بطاقتك لدى Amazon الرصيد المتاح 4200.00 EGP",
                    timestampMillis = System.currentTimeMillis()
                )
            ),
            channel = MessageChannel.CLIPBOARD_PASTE
        )
        // Expected app balance after 200 EGP deduction = 4800 EGP (480,000), bank balance = 4200 EGP (420,000) -> diff = -60,000
        val disc = result.latestBalanceDiscrepancy
        assertNotNull(disc)
        assertEquals(accId, disc!!.accountId)
        assertEquals(420_000L, disc.bankBalancePiasters)
        assertEquals(-60_000L, disc.diffPiasters)

        val adjustTxId = repo.reconcileAccountBalance(accId, disc.bankBalancePiasters, disc.sourceName)
        assertNotNull(adjustTxId)
        assertEquals(420_000L, db.accountDao().getById(accId)!!.currentBalancePiasters)
    }

    @Test
    fun `receipt storage manager persists bitmap to internal filesDir and deletes owned files`() = runBlocking {
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val persistedUri = ReceiptStorageManager.persistReceiptBitmap(context, bmp)
        assertNotNull(persistedUri)
        assertTrue(persistedUri!!.contains("/receipts/"))
        val filePath = android.net.Uri.parse(persistedUri).path!!
        val storedFile = File(filePath)
        assertTrue(storedFile.exists())
        assertTrue(storedFile.length() > 0L)

        ReceiptStorageManager.deleteReceiptIfOwned(context, persistedUri)
        assertFalse(storedFile.exists())
    }

    @Test
    fun `cloud sync models roundtrip savings certificate entity and preferences default auto sync is enabled`() {
        assertTrue(UserPreferences().autoCloudSyncEnabled)
        val entity = SavingsCertificateEntity(
            id = 7L,
            title = "سبيكة BTC",
            assetKind = SavingsAssetKind.GOLD_RESERVE,
            grams = 10.0,
            karat = 24,
            pricePerGramPiasters = 450_000L,
            isActive = true
        )
        val cloud = entity.toCloudModel()
        val roundTripped = cloud.toEntity()
        assertEquals("سبيكة BTC", roundTripped.title)
        assertEquals(SavingsAssetKind.GOLD_RESERVE, roundTripped.assetKind)
        assertEquals(10.0, roundTripped.grams, 0.001)
        assertEquals(24, roundTripped.karat)
        assertEquals(450_000L, roundTripped.pricePerGramPiasters)
    }

    @Test
    fun `ui certificates tab displays summary and triggers credit interest callback`() {
        var creditedCertId: Long? = null
        val cert = SavingsCertificateEntity(
            id = 1L,
            title = "الشهادة البلاتينية",
            assetKind = SavingsAssetKind.BANK_CERTIFICATE,
            institutionName = "NBE",
            principalPiasters = 1_000_000L,
            annualYieldPercent = 27.0,
            linkedAccountId = 1L
        )
        val state = MainUiState(
            preferences = UserPreferences(language = AppLanguage.AR),
            accounts = listOf(
                AccountEntity(
                    id = 1L,
                    name = "CIB Main",
                    type = AccountType.BANK,
                    openingBalancePiasters = 500_000L,
                    currentBalancePiasters = 500_000L
                )
            ),
            savingsCertificates = listOf(cert)
        )
        composeTestRule.setContent {
            ElbagorryMoneyTheme {
                AccountsAndGoalsScreen(
                    state = state,
                    onCreateAccount = { _, _, _, _, _ -> },
                    onTransfer = { _, _, _, _, _ -> },
                    onCreateGoal = { _, _, _, _, _ -> },
                    onContributeGoal = { _, _, _ -> },
                    onToggleGoalPause = {},
                    onDeleteGoal = {},
                    onCreditCertificateInterest = { creditedCertId = it }
                )
            }
        }
        composeTestRule.onNodeWithTag("accounts_tab_2").performClick()
        composeTestRule.onNodeWithTag("certificates_summary_card").assertIsDisplayed()
        composeTestRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasTestTag("credit_interest_btn_1"))
        composeTestRule.onNodeWithTag("credit_interest_btn_1").performClick()
        assertEquals(1L, creditedCertId)
    }

    @Test
    fun `ui smart reader shows csv import button and balance discrepancy reconciliation card`() {
        var reconciledAccId: Long? = null
        val state = MainUiState(
            preferences = UserPreferences(language = AppLanguage.AR),
            balanceDiscrepancies = listOf(
                BalanceDiscrepancy(
                    accountId = 1L,
                    accountName = "CIB Main",
                    appBalancePiasters = 500_000L,
                    bankBalancePiasters = 450_000L,
                    diffPiasters = -50_000L,
                    sourceName = "CIB"
                )
            )
        )
        composeTestRule.setContent {
            ElbagorryMoneyTheme {
                SmartMoneyReaderScreen(
                    state = state,
                    onSetReaderEnabled = {},
                    onSetIgnoreOtp = {},
                    onSetIgnoreBalance = {},
                    onAnalyzePasted = { _, _, _ -> },
                    onImportFile = {},
                    onApprovePending = { _, _, _, _, _, _, _ -> },
                    onRejectPending = {},
                    onIgnoreSource = {},
                    onToggleSource = {},
                    onAddCustomSource = { _, _, _, _ -> },
                    onClearImportData = {},
                    onBack = {},
                    onReconcileBalance = { disc -> reconciledAccId = disc.accountId }
                )
            }
        }
        composeTestRule.onNodeWithTag("import_csv_statement_btn").assertIsDisplayed()
        composeTestRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasTestTag("reconcile_balance_btn_1"))
        composeTestRule.onNodeWithTag("balance_discrepancy_card_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reconcile_balance_btn_1").performClick()
        assertEquals(1L, reconciledAccId)
    }

    @Test
    fun `ui reports screen opens encrypted aes256 backup password dialog`() {
        val state = MainUiState(preferences = UserPreferences(language = AppLanguage.AR))
        composeTestRule.setContent {
            ElbagorryMoneyTheme {
                ReportsScreen(
                    state = state,
                    onExportCsv = {},
                    onExportPdf = {},
                    onShareJsonBackup = {},
                    onSaveBackupToUri = {},
                    onRestoreBackupFromUri = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasTestTag("save_encrypted_backup_btn"))
        composeTestRule.onNodeWithTag("save_encrypted_backup_btn").performClick()
        composeTestRule.onNodeWithTag("encrypt_backup_password_input").assertIsDisplayed()
    }

    @Test
    fun `ui settings screen exposes and toggles auto cloud sync switch`() {
        var autoSyncToggled: Boolean? = null
        val state = MainUiState(
            preferences = UserPreferences(language = AppLanguage.AR, autoCloudSyncEnabled = true)
        )
        composeTestRule.setContent {
            ElbagorryMoneyTheme {
                SettingsScreen(
                    state = state,
                    onSetThemeMode = {},
                    onSetLanguage = {},
                    onSetBiometricLock = {},
                    onToggleNotificationSetting = { _, _ -> },
                    onBack = {},
                    isGuestMode = false,
                    onSetAutoCloudSyncEnabled = { autoSyncToggled = it }
                )
            }
        }
        composeTestRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasTestTag("auto_cloud_sync_switch"))
        composeTestRule.onNodeWithTag("auto_cloud_sync_switch").assertIsDisplayed().performClick()
        assertEquals(false, autoSyncToggled)
    }
}

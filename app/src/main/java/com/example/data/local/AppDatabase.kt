package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.AiChatDao
import com.example.data.local.dao.BnplProgramDao
import com.example.data.local.dao.BudgetDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.DebtDao
import com.example.data.local.dao.FinancialSourceDao
import com.example.data.local.dao.ImportHistoryDao
import com.example.data.local.dao.ImportedMessageDao
import com.example.data.local.dao.InstallmentDao
import com.example.data.local.dao.InstallmentPaymentDao
import com.example.data.local.dao.MerchantRuleDao
import com.example.data.local.dao.NotificationDao
import com.example.data.local.dao.PendingTransactionDao
import com.example.data.local.dao.RecurringRuleDao
import com.example.data.local.dao.SavingsCertificateDao
import com.example.data.local.dao.SavingsGoalDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AiChatMessageEntity
import com.example.data.local.entity.AiChatSessionEntity
import com.example.data.local.entity.BnplProgramEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.DebtEntity
import com.example.data.local.entity.FinancialSourceEntity
import com.example.data.local.entity.ImportHistoryEntity
import com.example.data.local.entity.ImportedMessageEntity
import com.example.data.local.entity.InstallmentEntity
import com.example.data.local.entity.InstallmentPaymentEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.RecurringRuleEntity
import com.example.data.local.entity.SavingsCertificateEntity
import com.example.data.local.entity.SavingsGoalEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.AccountType
import com.example.domain.model.DebtDirection
import com.example.domain.model.DebtStatus
import com.example.domain.model.GoalPreset
import com.example.domain.model.GoalStatus
import com.example.domain.model.ImportedMessageStatus
import com.example.domain.model.IncomeSourceType
import com.example.domain.model.InstallmentFrequency
import com.example.domain.model.InstallmentStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageOperationKind
import com.example.domain.model.NotificationType
import com.example.domain.model.PayoutCycle
import com.example.domain.model.PendingStatus
import com.example.domain.model.RecurringFrequency
import com.example.domain.model.SavingsAssetKind
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoomConverters {
    @TypeConverter fun fromAccountType(value: AccountType): String = value.name
    @TypeConverter fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter fun fromTransactionType(value: TransactionType): String = value.name
    @TypeConverter fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter fun fromTransactionSource(value: TransactionSource): String = value.name
    @TypeConverter fun toTransactionSource(value: String): TransactionSource = TransactionSource.valueOf(value)

    @TypeConverter fun fromMessageChannel(value: MessageChannel): String = value.name
    @TypeConverter fun toMessageChannel(value: String): MessageChannel = MessageChannel.valueOf(value)

    @TypeConverter fun fromMessageOperationKind(value: MessageOperationKind): String = value.name
    @TypeConverter fun toMessageOperationKind(value: String): MessageOperationKind = MessageOperationKind.valueOf(value)

    @TypeConverter fun fromPendingStatus(value: PendingStatus): String = value.name
    @TypeConverter fun toPendingStatus(value: String): PendingStatus = PendingStatus.valueOf(value)

    @TypeConverter fun fromImportedMessageStatus(value: ImportedMessageStatus): String = value.name
    @TypeConverter fun toImportedMessageStatus(value: String): ImportedMessageStatus = ImportedMessageStatus.valueOf(value)

    @TypeConverter fun fromGoalPreset(value: GoalPreset): String = value.name
    @TypeConverter fun toGoalPreset(value: String): GoalPreset = GoalPreset.valueOf(value)

    @TypeConverter fun fromGoalStatus(value: GoalStatus): String = value.name
    @TypeConverter fun toGoalStatus(value: String): GoalStatus = GoalStatus.valueOf(value)

    @TypeConverter fun fromInstallmentFrequency(value: InstallmentFrequency): String = value.name
    @TypeConverter fun toInstallmentFrequency(value: String): InstallmentFrequency = InstallmentFrequency.valueOf(value)

    @TypeConverter fun fromInstallmentStatus(value: InstallmentStatus): String = value.name
    @TypeConverter fun toInstallmentStatus(value: String): InstallmentStatus = InstallmentStatus.valueOf(value)

    @TypeConverter fun fromRecurringFrequency(value: RecurringFrequency): String = value.name
    @TypeConverter fun toRecurringFrequency(value: String): RecurringFrequency = RecurringFrequency.valueOf(value)

    @TypeConverter fun fromIncomeSourceType(value: IncomeSourceType): String = value.name
    @TypeConverter fun toIncomeSourceType(value: String): IncomeSourceType = IncomeSourceType.valueOf(value)

    @TypeConverter fun fromDebtDirection(value: DebtDirection): String = value.name
    @TypeConverter fun toDebtDirection(value: String): DebtDirection = DebtDirection.valueOf(value)

    @TypeConverter fun fromDebtStatus(value: DebtStatus): String = value.name
    @TypeConverter fun toDebtStatus(value: String): DebtStatus = DebtStatus.valueOf(value)

    @TypeConverter fun fromNotificationType(value: NotificationType): String = value.name
    @TypeConverter fun toNotificationType(value: String): NotificationType = NotificationType.valueOf(value)

    @TypeConverter fun fromSavingsAssetKind(value: SavingsAssetKind): String = value.name
    @TypeConverter fun toSavingsAssetKind(value: String): SavingsAssetKind =
        runCatching { SavingsAssetKind.valueOf(value) }.getOrDefault(SavingsAssetKind.BANK_CERTIFICATE)

    @TypeConverter fun fromPayoutCycle(value: PayoutCycle): String = value.name
    @TypeConverter fun toPayoutCycle(value: String): PayoutCycle =
        runCatching { PayoutCycle.valueOf(value) }.getOrDefault(PayoutCycle.MONTHLY)
}

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        InstallmentEntity::class,
        InstallmentPaymentEntity::class,
        RecurringRuleEntity::class,
        DebtEntity::class,
        NotificationEntity::class,
        FinancialSourceEntity::class,
        ImportedMessageEntity::class,
        PendingTransactionEntity::class,
        ImportHistoryEntity::class,
        MerchantRuleEntity::class,
        BnplProgramEntity::class,
        AiChatSessionEntity::class,
        AiChatMessageEntity::class,
        SavingsCertificateEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun installmentPaymentDao(): InstallmentPaymentDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun debtDao(): DebtDao
    abstract fun notificationDao(): NotificationDao
    abstract fun financialSourceDao(): FinancialSourceDao
    abstract fun importedMessageDao(): ImportedMessageDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun importHistoryDao(): ImportHistoryDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun bnplProgramDao(): BnplProgramDao
    abstract fun aiChatDao(): AiChatDao
    abstract fun savingsCertificateDao(): SavingsCertificateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL_ENTRY'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN referenceNumber TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN sourceIdentifier TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN dedupFingerprint TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_dedupFingerprint ON transactions(dedupFingerprint)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS financial_sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        code TEXT NOT NULL,
                        displayNameAr TEXT NOT NULL,
                        displayNameEn TEXT NOT NULL,
                        senderKeywords TEXT NOT NULL,
                        packageNames TEXT NOT NULL,
                        defaultAccountId INTEGER,
                        isEnabled INTEGER NOT NULL,
                        isIgnored INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS imported_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceId INTEGER,
                        sourceName TEXT NOT NULL,
                        channel TEXT NOT NULL,
                        senderLabel TEXT NOT NULL,
                        fingerprintHash TEXT NOT NULL,
                        status TEXT NOT NULL,
                        confidenceScore REAL NOT NULL,
                        messageTimestampMillis INTEGER NOT NULL,
                        importedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_imported_messages_fingerprintHash ON imported_messages(fingerprintHash)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        importedMessageId INTEGER,
                        sourceId INTEGER,
                        sourceName TEXT NOT NULL,
                        channel TEXT NOT NULL,
                        operationKind TEXT NOT NULL,
                        mappedType TEXT NOT NULL,
                        amountPiasters INTEGER NOT NULL,
                        feePiasters INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        referenceNumber TEXT,
                        counterpartyName TEXT,
                        balanceAfterPiasters INTEGER,
                        transactionTimestampMillis INTEGER NOT NULL,
                        suggestedAccountId INTEGER,
                        targetAccountId INTEGER,
                        suggestedCategoryId INTEGER,
                        confidenceScore REAL NOT NULL,
                        dedupFingerprint TEXT NOT NULL,
                        extractedSummary TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(importedMessageId) REFERENCES imported_messages(id) ON DELETE SET NULL,
                        FOREIGN KEY(suggestedAccountId) REFERENCES accounts(id) ON DELETE SET NULL,
                        FOREIGN KEY(suggestedCategoryId) REFERENCES categories(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_transactions_importedMessageId ON pending_transactions(importedMessageId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_transactions_suggestedAccountId ON pending_transactions(suggestedAccountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_transactions_suggestedCategoryId ON pending_transactions(suggestedCategoryId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_transactions_dedupFingerprint ON pending_transactions(dedupFingerprint)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS import_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        channel TEXT NOT NULL,
                        totalScanned INTEGER NOT NULL,
                        financialDetected INTEGER NOT NULL,
                        pendingCreated INTEGER NOT NULL,
                        duplicatesSkipped INTEGER NOT NULL,
                        ignoredFiltered INTEGER NOT NULL,
                        timestampMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS merchant_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        keyword TEXT NOT NULL,
                        normalizedKeyword TEXT NOT NULL,
                        categoryId INTEGER,
                        accountId INTEGER,
                        preferredType TEXT NOT NULL DEFAULT 'EXPENSE',
                        hitCount INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchant_rules_normalizedKeyword ON merchant_rules(normalizedKeyword)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bnpl_programs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        providerCode TEXT NOT NULL,
                        nameAr TEXT NOT NULL,
                        nameEn TEXT NOT NULL,
                        creditLimitPiasters INTEGER NOT NULL DEFAULT 0,
                        manualUsedPiasters INTEGER NOT NULL DEFAULT 0,
                        defaultAnnualInterestPercent REAL NOT NULL DEFAULT 0.0,
                        colorHex TEXT NOT NULL DEFAULT '#38BDF8',
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        notes TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bnpl_programs_providerCode ON bnpl_programs(providerCode)")
                db.execSQL("ALTER TABLE installments ADD COLUMN bnplProgramId INTEGER")
                db.execSQL("ALTER TABLE installments ADD COLUMN principalAmountPiasters INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_installments_bnplProgramId ON installments(bnplProgramId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_chat_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        lastMessagePreview TEXT NOT NULL DEFAULT '',
                        messageCount INTEGER NOT NULL DEFAULT 0,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL,
                        syncedToCloud INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_chat_sessions_updatedAtMillis ON ai_chat_sessions(updatedAtMillis)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_chat_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        isUser INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        isError INTEGER NOT NULL DEFAULT 0,
                        isUndone INTEGER NOT NULL DEFAULT 0,
                        engineSource TEXT NOT NULL DEFAULT 'CLOUD_GEMINI',
                        executedActionsJson TEXT NOT NULL DEFAULT '[]',
                        timestampMillis INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES ai_chat_sessions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_chat_messages_sessionId ON ai_chat_messages(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_chat_messages_timestampMillis ON ai_chat_messages(timestampMillis)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS savings_certificates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        assetKind TEXT NOT NULL DEFAULT 'BANK_CERTIFICATE',
                        institutionName TEXT NOT NULL DEFAULT '',
                        principalPiasters INTEGER NOT NULL DEFAULT 0,
                        annualYieldPercent REAL NOT NULL DEFAULT 0.0,
                        payoutCycle TEXT NOT NULL DEFAULT 'MONTHLY',
                        linkedAccountId INTEGER,
                        nextPayoutMillis INTEGER NOT NULL,
                        maturityMillis INTEGER,
                        autoCreditInterest INTEGER NOT NULL DEFAULT 1,
                        grams REAL NOT NULL DEFAULT 0.0,
                        karat INTEGER NOT NULL DEFAULT 21,
                        pricePerGramPiasters INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(linkedAccountId) REFERENCES accounts(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_certificates_linkedAccountId ON savings_certificates(linkedAccountId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_certificates_nextPayoutMillis ON savings_certificates(nextPayoutMillis)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                "elbagorry_money.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.let { seedDefaults(it) }
                        }
                    }
                })
                .build()
        }

        suspend fun seedDefaults(database: AppDatabase) {
            val accountDao = database.accountDao()
            if (accountDao.getAllOnce().isEmpty()) {
                accountDao.insertAll(
                    listOf(
                        AccountEntity(
                            name = "النقدية (كاش)",
                            type = AccountType.CASH,
                            openingBalancePiasters = 0L,
                            currentBalancePiasters = 0L,
                            currency = "EGP",
                            colorHex = "#10B981",
                            iconName = "payments"
                        ),
                        AccountEntity(
                            name = "الحساب البنكي",
                            type = AccountType.BANK,
                            openingBalancePiasters = 0L,
                            currentBalancePiasters = 0L,
                            currency = "EGP",
                            colorHex = "#38BDF8",
                            iconName = "account_balance"
                        ),
                        AccountEntity(
                            name = "المحفظة الإلكترونية",
                            type = AccountType.E_WALLET,
                            openingBalancePiasters = 0L,
                            currentBalancePiasters = 0L,
                            currency = "EGP",
                            colorHex = "#F59E0B",
                            iconName = "smartphone"
                        )
                    )
                )
            }

            val categoryDao = database.categoryDao()
            val existingCategories = categoryDao.getAllOnce()
            if (existingCategories.isEmpty()) {
                categoryDao.insertAll(defaultCategories())
            } else if (existingCategories.none { it.nameEn.equals("Entertainment", ignoreCase = true) }) {
                categoryDao.insert(
                    CategoryEntity(
                        nameAr = "ترفيه",
                        nameEn = "Entertainment",
                        type = TransactionType.EXPENSE,
                        iconName = "movie",
                        colorHex = "#A855F7",
                        isDefault = true
                    )
                )
            }

            val sourceDao = database.financialSourceDao()
            if (sourceDao.getAllOnce().isEmpty()) {
                val allAccounts = accountDao.getAllOnce()
                val bankAccId = allAccounts.firstOrNull { it.type == AccountType.BANK }?.id
                val walletAccId = allAccounts.firstOrNull { it.type == AccountType.E_WALLET }?.id
                sourceDao.insertAll(defaultFinancialSources(bankAccId, walletAccId))
            }

            val bnplDao = database.bnplProgramDao()
            if (bnplDao.getAllOnce().isEmpty()) {
                bnplDao.insertAllIgnore(defaultBnplPrograms())
            }
        }

        fun defaultBnplPrograms(): List<BnplProgramEntity> = listOf(
            BnplProgramEntity(
                providerCode = "VALU",
                nameAr = "فاليو (valU)",
                nameEn = "valU",
                creditLimitPiasters = 0L,
                manualUsedPiasters = 0L,
                defaultAnnualInterestPercent = 24.0,
                colorHex = "#6366F1",
                isEnabled = true
            ),
            BnplProgramEntity(
                providerCode = "KLIVR",
                nameAr = "كليفر (Klivr)",
                nameEn = "Klivr",
                creditLimitPiasters = 0L,
                manualUsedPiasters = 0L,
                defaultAnnualInterestPercent = 22.0,
                colorHex = "#06B6D4",
                isEnabled = true
            ),
            BnplProgramEntity(
                providerCode = "AMAN",
                nameAr = "أمان (Aman)",
                nameEn = "Aman",
                creditLimitPiasters = 0L,
                manualUsedPiasters = 0L,
                defaultAnnualInterestPercent = 25.0,
                colorHex = "#10B981",
                isEnabled = true
            ),
            BnplProgramEntity(
                providerCode = "SOUHOOLA",
                nameAr = "سهولة (Souhoola)",
                nameEn = "Souhoola",
                creditLimitPiasters = 0L,
                manualUsedPiasters = 0L,
                defaultAnnualInterestPercent = 24.0,
                colorHex = "#F59E0B",
                isEnabled = true
            ),
            BnplProgramEntity(
                providerCode = "FORSA",
                nameAr = "فرصة (Forsa)",
                nameEn = "Forsa",
                creditLimitPiasters = 0L,
                manualUsedPiasters = 0L,
                defaultAnnualInterestPercent = 23.0,
                colorHex = "#EC4899",
                isEnabled = true
            ),
            BnplProgramEntity(
                providerCode = "HALAN",
                nameAr = "حالاً (Halan)",
                nameEn = "Halan",
                creditLimitPiasters = 0L,
                manualUsedPiasters = 0L,
                defaultAnnualInterestPercent = 26.0,
                colorHex = "#8B5CF6",
                isEnabled = true
            ),
            BnplProgramEntity(
                providerCode = "BANK_CARD",
                nameAr = "تقسيط البطاقة البنكية",
                nameEn = "Bank Card Installments",
                creditLimitPiasters = 0L,
                manualUsedPiasters = 0L,
                defaultAnnualInterestPercent = 18.0,
                colorHex = "#3B82F6",
                isEnabled = true
            )
        )

        fun defaultCategories(): List<CategoryEntity> = listOf(
            CategoryEntity(nameAr = "طعام ومشروبات", nameEn = "Food & Drinks", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#F59E0B", isDefault = true),
            CategoryEntity(nameAr = "مواصلات", nameEn = "Transport", type = TransactionType.EXPENSE, iconName = "directions_car", colorHex = "#38BDF8", isDefault = true),
            CategoryEntity(nameAr = "فواتير وخدمات", nameEn = "Bills & Utilities", type = TransactionType.EXPENSE, iconName = "receipt_long", colorHex = "#EF4444", isDefault = true),
            CategoryEntity(nameAr = "سكن وإيجار", nameEn = "Housing & Rent", type = TransactionType.EXPENSE, iconName = "home", colorHex = "#A855F7", isDefault = true),
            CategoryEntity(nameAr = "تسوق", nameEn = "Shopping", type = TransactionType.EXPENSE, iconName = "shopping_bag", colorHex = "#EC4899", isDefault = true),
            CategoryEntity(nameAr = "ترفيه", nameEn = "Entertainment", type = TransactionType.EXPENSE, iconName = "movie", colorHex = "#A855F7", isDefault = true),
            CategoryEntity(nameAr = "صحة وعلاج", nameEn = "Healthcare", type = TransactionType.EXPENSE, iconName = "medical_services", colorHex = "#14B8A6", isDefault = true),
            CategoryEntity(nameAr = "تعليم", nameEn = "Education", type = TransactionType.EXPENSE, iconName = "school", colorHex = "#6366F1", isDefault = true),
            CategoryEntity(nameAr = "اشتراكات", nameEn = "Subscriptions", type = TransactionType.EXPENSE, iconName = "subscriptions", colorHex = "#F97316", isDefault = true),
            CategoryEntity(nameAr = "أقساط", nameEn = "Installments", type = TransactionType.EXPENSE, iconName = "event_repeat", colorHex = "#FB7185", isDefault = true),
            CategoryEntity(nameAr = "رسوم تحويل", nameEn = "Transfer Fees", type = TransactionType.EXPENSE, iconName = "percent", colorHex = "#F43F5E", isDefault = true),
            CategoryEntity(nameAr = "مصروفات أخرى", nameEn = "Other Expenses", type = TransactionType.EXPENSE, iconName = "more_horiz", colorHex = "#94A3B8", isDefault = true),

            CategoryEntity(nameAr = "راتب شهري", nameEn = "Salary", type = TransactionType.INCOME, iconName = "payments", colorHex = "#10B981", isDefault = true),
            CategoryEntity(nameAr = "عمل حر", nameEn = "Freelance", type = TransactionType.INCOME, iconName = "laptop_mac", colorHex = "#34D399", isDefault = true),
            CategoryEntity(nameAr = "مكافآت", nameEn = "Bonuses", type = TransactionType.INCOME, iconName = "workspace_premium", colorHex = "#FBBF24", isDefault = true),
            CategoryEntity(nameAr = "استثمارات", nameEn = "Investments", type = TransactionType.INCOME, iconName = "trending_up", colorHex = "#38BDF8", isDefault = true),
            CategoryEntity(nameAr = "استرداد ومرتجعات", nameEn = "Refunds", type = TransactionType.INCOME, iconName = "replay", colorHex = "#2DD4BF", isDefault = true),
            CategoryEntity(nameAr = "دخل إضافي", nameEn = "Extra Income", type = TransactionType.INCOME, iconName = "add_card", colorHex = "#22C55E", isDefault = true)
        )

        fun defaultFinancialSources(
            bankAccountId: Long? = null,
            walletAccountId: Long? = null
        ): List<FinancialSourceEntity> = listOf(
            FinancialSourceEntity(
                code = "VODAFONE_CASH",
                displayNameAr = "فودافون كاش",
                displayNameEn = "Vodafone Cash",
                senderKeywords = "vodafone,vf-cash,vfcash,فودافون كاش,فودافون",
                packageNames = "com.emeint.android.myservices",
                defaultAccountId = walletAccountId
            ),
            FinancialSourceEntity(
                code = "ORANGE_CASH",
                displayNameAr = "أورنج كاش",
                displayNameEn = "Orange Cash",
                senderKeywords = "orange cash,orangecash,أورنج كاش,اورنج كاش",
                packageNames = "com.orange.myorange.egypt",
                defaultAccountId = walletAccIdOrFallback(walletAccountId)
            ),
            FinancialSourceEntity(
                code = "ETISALAT_CASH",
                displayNameAr = "اتصالات كاش",
                displayNameEn = "Etisalat Cash",
                senderKeywords = "etisalat cash,e& cash,اتصالات كاش",
                packageNames = "com.etisalat",
                defaultAccountId = walletAccountId
            ),
            FinancialSourceEntity(
                code = "INSTAPAY",
                displayNameAr = "إنستاباي (InstaPay)",
                displayNameEn = "InstaPay",
                senderKeywords = "instapay,ipn,إنستاباي,انستاباي",
                packageNames = "com.egyptianbanks.instapay",
                defaultAccountId = bankAccountId
            ),
            FinancialSourceEntity(
                code = "NBE",
                displayNameAr = "البنك الأهلي المصري",
                displayNameEn = "NBE",
                senderKeywords = "nbe,alahly,الأهلي,البنك الأهلي",
                packageNames = "com.alahlybank.nbe",
                defaultAccountId = bankAccountId
            ),
            FinancialSourceEntity(
                code = "BANQUE_MISR",
                displayNameAr = "بنك مصر",
                displayNameEn = "Banque Misr",
                senderKeywords = "banque misr,bm,بنك مصر",
                packageNames = "com.banquemisr.bmwallet",
                defaultAccountId = bankAccountId
            ),
            FinancialSourceEntity(
                code = "CIB",
                displayNameAr = "البنك التجاري الدولي CIB",
                displayNameEn = "CIB",
                senderKeywords = "cib,التجاري الدولي",
                packageNames = "com.cibeg.mobilebanking",
                defaultAccountId = bankAccountId
            ),
            FinancialSourceEntity(
                code = "QNB",
                displayNameAr = "بنك QNB",
                displayNameEn = "QNB",
                senderKeywords = "qnb,الأهلي المتحد",
                packageNames = "com.qnb.egypt",
                defaultAccountId = bankAccountId
            )
        )

        private fun walletAccIdOrFallback(id: Long?): Long? = id
    }
}

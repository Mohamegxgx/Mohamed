# ELBAGORRY Money — Native Android Financial Management Application

تطبيق **ELBAGORRY Money** هو تطبيق إدارة مالية شخصية أصلي (Native Android) مبني بلغة **Kotlin** وواجهة **Jetpack Compose (Material 3)** ومعمارية **MVVM + Clean Layers + Repository Pattern** مع قاعدة بيانات محلية **Room (SQLite)**، ودعم كامل للغة العربية (RTL) والعملة الافتراضية **EGP**، مع جاهزية كاملة للتبديل إلى الإنجليزية.

---

## المزايا والأنظمة المنفذة

1. **لوحة التحكم المالية (Dashboard)**:
   - عرض 10 مؤشرات مالية رئيسية فورية (إجمالي الدخل، إجمالي المصروفات، صافي الرصيد، نسبة المصروفات للدخل، معدل الادخار، إجمالي الالتزامات، المبالغ المستحقة، الميزانية المتبقية، الأموال المتاحة، التدفق النقدي).
   - 5 رسوم بيانية مخصصة بـ Compose Canvas (الدخل مقابل المصروفات، المنحنى المالي، صافي التدفق النقدي، توزيع الفئات Donut، وتقدم أهداف الادخار).
   - 9 فلاتر زمنية شاملة (اليوم، أمس، الأسبوع، الشهر الحالي، الشهر السابق، السنة الحالية، السنة السابقة، كل الوقت، فترة مخصصة).
   - زر إخفاء/إظهار المبالغ بنقرة واحدة لحماية الخصوصية.

2. **محرك الحسابات المالية الدقيق (`FinancialEngine`)**:
   - تخزين جميع المبالغ بوحدة القرش (`Long` piasters) وتحويل النسب عبر `BigDecimal` لمنع أخطاء الفاصلة العائمة.
   - فصل كامل بين الدخل والمصروفات والتحويلات ومساهمات الادخار وسداد الديون لمنع الاحتساب المكرر.
   - تحديث ذري (`Room @Transaction`) للأرصدة عند إضافة أو تعديل أو حذف أي معاملة.

3. **نظام المعاملات والحسابات**:
   - أنواع المعاملات: دخل، مصروف، تحويل بين الحسابات، مساهمة ادخار، سداد دين.
   - البحث والفلترة والترتيب، التكرار، التعديل، الحذف، وإرفاق إيصالات عبر Android Photo Picker بدون صلاحيات تخزين واسعة.
   - حسابات افتراضية (النقدية، الحساب البنكي، المحفظة الإلكترونية) وإمكانية إنشاء حسابات مخصصة والتحويل بينها.

4. **الميزانيات، أهداف الادخار، الأقساط، الدخل والمصروفات المتكررة، والديون**:
   - ميزانيات شهرية عامة أو حسب الفئة، تنبيه 80% وتنبيه تجاوز 100%، ونسخ ميزانيات الشهر السابق.
   - أهداف ادخار بقوالب جاهزة وحلقات تقدم ومساهمات غير مكررة.
   - جدول أقساط يولد مصروفًا واحدًا مرتبطًا عند السداد، مع متابعة الأقساط المتأخرة والإيقاف والأرشفة.
   - قواعد دخل ومصروفات متكررة محمية من التكرار عبر `recurringCycleKey` فريد في قاعدة البيانات (`UNIQUE INDEX`).
   - إدارة الديون (لك / عليك) وسدادها بشكل منفصل.

5. **التحليلات المحلية، التقويم المالي، إدارة الفئات، الإشعارات، التقارير، والأمان**:
   - مركز تحليلات محلي بدون إنترنت أو ذكاء اصطناعي خارجي مع توقع الرصيد بنهاية الشهر ورؤى ذكية.
   - شاشة مخصصة لإدارة الفئات (`CategoriesScreen`) تدعم إنشاء فئات دخل ومصروف مخصصة وحماية الفئات الافتراضية.
   - تقويم مالي يعرض مواعيد الرواتب والأقساط والفواتير.
   - WorkManager وإشعارات محلية مع تحكم كامل في كل نوع إشعار وصلاحية `POST_NOTIFICATIONS`.
   - تصدير **CSV** و **PDF** ومشاركة عبر Android Sharesheet، ونسخ احتياطي واستعادة **JSON (v2)** مع التحقق من سلامة البيانات.
   - قفل التطبيق بالبصمة / قفل الجهاز (`BiometricPrompt`) وتهيئة مفتاح `AndroidKeyStore`.

6. **ELBAGORRY Smart Money Reader (المرحلة الثانية — قارئ المعاملات الذكي)**:
   - **محرك تحليل محلي 100% بدون إنترنت (`FinancialMessageParser` + `ParsingRulesRegistry`)**: يدعم العربية (بما في ذلك الأرقام الشرقية `١٢٣` وعملة `ج.م`) والإنجليزية، مع قواعد قابلة للتوسعة للبنوك والمحافظ المصرية (Vodafone Cash، Orange Cash، Etisalat Cash، InstaPay، البنك الأهلي المصري NBE، بنك مصر، CIB، QNB، ومصادر مخصصة).
   - **قنوات استيراد متوافقة مع سياسات Google Play** (بدون طلب صلاحية `READ_SMS` / `RECEIVE_SMS` المقيدة):
     1. مشاركة النص مباشرًة إلى التطبيق (`Intent.ACTION_SEND`).
     2. استيراد ملفات النصوص وCSV عبر Storage Access Framework (`OpenDocument`).
     3. لصق وتحليل الرسائل مباشرًة داخل التطبيق.
     4. خدمة قارئ الإشعارات الاختيارية (`FinancialNotificationListenerService` عبر `BIND_NOTIFICATION_LISTENER_SERVICE`).
   - **الأمان والخصوصية ومنع التكرار (`DuplicateDetector`)**:
     - تجاهل صارم لرسائل OTP ورموز التحقق والرسائل الترويجية والشخصية دون تخزين نصوصها أبدًا.
     - فصل رسائل الاستعلام عن الرصيد (`BalanceInquiryOnly`) وعدم تحويلها إلى معاملات مالية.
     - فصل رسوم التحويل (`TRANSFER_FEE`) كعمليات مستقلة عند اقترانها بالتحويلات.
     - توجيه جميع العمليات المستخرجة إلى **قائمة المراجعة المعلقة (Pending Review)** لاعتمادها أو تعديلها أو رفضها أو تجاهل مصدرها قبل التأثير على أرصدة الحسابات.
     - بصمة SHA-256 وفهرس فريد (`dedupFingerprint`) في Room v2 لمنع تكرار المعاملات أو تضاعف الأرصدة عند إعادة الاستيراد.

---

## المعمارية وهيكل المشروع

- `domain/model/` — النماذج والأنواع المالية ومصادر الاستيراد (`FinancialModels.kt`)
- `domain/engine/` — محرك الحسابات، التحليلات، تنسيق العملة، وحل النطاقات الزمنية (`FinancialEngine.kt`, `AnalyticsEngine.kt`, `MoneyFormatter.kt`, `DateRangeResolver.kt`)
- `domain/reader/` — محلل الرسائل المالية المحلي، سجل القواعد، وكاشف التكرار (`FinancialMessageParser.kt`, `ParsingRulesRegistry.kt`, `DuplicateDetector.kt`, `SmartReaderModels.kt`)
- `reader/` — منسق الاستيراد الذكي وقارئ الإشعارات الاختياري (`SmartReaderProcessor.kt`, `FinancialNotificationListenerService.kt`)
- `data/local/` — قاعدة بيانات Room الإصدار 2 مع ترحيل `MIGRATION_1_2` و14 كيانًا (`AppDatabase.kt`, `Entities.kt`, `Daos.kt`)
- `data/repository/` — مستودع البيانات الموحد والعمليات الذرية (`MoneyRepository.kt`)
- `data/preferences/` — تفضيلات المستخدم وقارئ المعاملات بـ Jetpack DataStore (`UserPreferencesRepository.kt`)
- `data/backup/` — النسخ الاحتياطي JSON (الإصدار 2) وتصدير CSV/PDF (`BackupAndExportManager.kt`)
- `notifications/` — قناة الإشعارات وWorkManager ومستقبل إعادة التشغيل (`NotificationHelper.kt`, `FinancialCheckWorker.kt`, `BootCompletedReceiver.kt`)
- `security/` — حماية Biometric وAndroid Keystore (`AppSecurityHelper.kt`)
- `ui/` — التصميم الداكن الفاخر (Cairo + Roboto Mono)، المكونات، الرسوم البيانية، شاشات Dashboard/Transactions/Accounts/Planning/Insights/Categories/SmartMoneyReader/Calendar/Reports/Notifications/Settings، و`MainViewModel`.

---

## تعليمات البناء والاختبار (Build & Release Instructions)

1. **تشغيل الاختبارات المحلية (Unit & Robolectric)**:
   ```bash
   gradle :app:testDebugUnitTest
   ```
   تشمل الاختبارات:
   - `FinancialEngineUnitTest`: التحقق من دقة الحسابات المالية بالقرش و`BigDecimal`.
   - `SmartMoneyReaderUnitTest`: التحقق من تحليل التحويل الوارد والصادر، الإيداع، السحب، الشراء، الرسوم، استعلام الرصيد، تجاهل OTP والترويج، العربية والإنجليزية، الرسائل غير المكتملة، وبصمة منع التكرار.
   - `ExampleRobolectricTest`: اختبارات التكامل مع Room و`SmartReaderProcessor` والنسخ الاحتياطي وCompose UI.

2. **بناء نسخة Debug APK**:
   ```bash
   gradle :app:assembleDebug
   ```

3. **بناء نسخة Release APK / AAB للنشر على Google Play**:
   - في `app/build.gradle.kts` يمكنك ضبط `applicationId` للنشر النهائي (مثل `com.elbagorry.money`) وإعداد مفاتيح التوقيع (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) كمتغيرات بيئة، ثم تنفيذ:
   ```bash
   gradle :app:bundleRelease
   ```
   أو لبناء APK:
   ```bash
   gradle :app:assembleRelease
   ```

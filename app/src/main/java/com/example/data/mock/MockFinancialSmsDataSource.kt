package com.example.data.mock

import com.example.domain.reader.FinancialMessageParser
import com.example.domain.reader.ParseOutcome
import com.example.reader.IncomingRawMessage

data class MockSmsRecord(
    val id: String,
    val sender: String,
    val body: String,
    val timestampMillis: Long
)

data class MockParsedSmsPreview(
    val record: MockSmsRecord,
    val outcome: ParseOutcome
)

/**
 * In-memory mock SMS data layer providing realistic Arabic & English
 * Egyptian bank and e-wallet alert samples for local parser evaluation
 * and opt-in user simulation.
 */
object MockFinancialSmsDataSource {

    fun sampleMessages(nowMillis: Long = System.currentTimeMillis()): List<MockSmsRecord> = listOf(
        MockSmsRecord(
            id = "mock_cib_purchase",
            sender = "CIB",
            body = "CIB: Purchase of 680.50 EGP at Spinneys Egypt with card ending 4412 on 12/11/2026 14:25. Available balance 14319.50 EGP Ref: CIB991201",
            timestampMillis = nowMillis - 3_600_000L
        ),
        MockSmsRecord(
            id = "mock_nbe_salary",
            sender = "NBE",
            body = "البنك الأهلي المصري: تم إيداع مبلغ 15,000.00 ج.م راتب شهري في حسابك بتاريخ 12/11/2026 الرصيد الحالي 28,500.00 ج.م Ref: NBE202611",
            timestampMillis = nowMillis - 7_200_000L
        ),
        MockSmsRecord(
            id = "mock_vf_incoming",
            sender = "VodafoneCash",
            body = "فودافون كاش: تم استلام مبلغ ١,٢٥٠.٠٠ ج.م تحويل وارد من كريم محمود رقم المرجع VF884422 الرصيد المتاح ٢,٧٥٠.٠٠ ج.م",
            timestampMillis = nowMillis - 10_800_000L
        ),
        MockSmsRecord(
            id = "mock_instapay_outgoing",
            sender = "InstaPay",
            body = "إنستاباي: تم تحويل مبلغ 2,400 جنيه إلى سارة أحمد برسوم 5 جنيه بتاريخ 12/11/2026 رقم العملية IPN773311",
            timestampMillis = nowMillis - 14_400_000L
        ),
        MockSmsRecord(
            id = "mock_bm_withdrawal",
            sender = "BanqueMisr",
            body = "بنك مصر: تم سحب نقدي بمبلغ 1,000.00 ج.م من ماكينة صراف آلي رمسيس بتاريخ 12/11/2026 10:15 Ref: BM663300",
            timestampMillis = nowMillis - 18_000_000L
        ),
        MockSmsRecord(
            id = "mock_qnb_refund",
            sender = "QNB",
            body = "QNB: Refund of 320.00 EGP credited to your account from Amazon EG on 12/11/2026 Ref: QNB554411",
            timestampMillis = nowMillis - 21_600_000L
        ),
        MockSmsRecord(
            id = "mock_otp_ignored",
            sender = "CIB",
            body = "كود التحقق OTP الخاص بك هو 739104. لا تشارك هذا الرمز مع أي شخص.",
            timestampMillis = nowMillis - 25_200_000L
        ),
        MockSmsRecord(
            id = "mock_balance_inquiry",
            sender = "VodafoneCash",
            body = "فودافون كاش: استعلام عن الرصيد، رصيدك المتاح حاليا 2,750.00 ج.م",
            timestampMillis = nowMillis - 28_800_000L
        )
    )

    fun toIncomingMessages(records: List<MockSmsRecord>): List<IncomingRawMessage> =
        records.map { IncomingRawMessage(sender = it.sender, body = it.body, timestampMillis = it.timestampMillis) }
}

/**
 * Mock data-layer repository integrating MockFinancialSmsDataSource with FinancialMessageParser.
 */
class MockSmsRepository(
    private val parser: FinancialMessageParser = FinancialMessageParser(),
    private val dataSource: MockFinancialSmsDataSource = MockFinancialSmsDataSource
) {
    fun fetchAndParseSampleMessages(nowMillis: Long = System.currentTimeMillis()): List<MockParsedSmsPreview> {
        return dataSource.sampleMessages(nowMillis).map { record ->
            MockParsedSmsPreview(
                record = record,
                outcome = parser.parse(
                    rawBody = record.body,
                    sender = record.sender,
                    messageTimestampMillis = record.timestampMillis
                )
            )
        }
    }

    fun getRawSampleBatch(nowMillis: Long = System.currentTimeMillis()): List<IncomingRawMessage> =
        dataSource.toIncomingMessages(dataSource.sampleMessages(nowMillis))
}

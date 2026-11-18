package com.example.data.ai

import com.example.data.local.entity.AiChatMessageEntity
import com.example.domain.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject

object AiChatJsonMapper {

    fun encodeExecutedActions(actions: List<AiExecutedActionRecord>): String {
        if (actions.isEmpty()) return "[]"
        val arr = JSONArray()
        actions.forEach { rec ->
            arr.put(
                JSONObject().apply {
                    put("transactionId", rec.transactionId)
                    rec.feeTransactionId?.let { put("feeTransactionId", it) }
                    put("type", rec.type.name)
                    put("amountPiasters", rec.amountPiasters)
                    put("feePiasters", rec.feePiasters)
                    put("description", rec.description)
                    rec.categoryNameAr?.let { put("categoryNameAr", it) }
                    rec.categoryNameEn?.let { put("categoryNameEn", it) }
                    rec.categoryColorHex?.let { put("categoryColorHex", it) }
                    put("accountName", rec.accountName)
                    put("createdNewCategory", rec.createdNewCategory)
                }
            )
        }
        return arr.toString()
    }

    fun decodeExecutedActions(json: String): List<AiExecutedActionRecord> {
        if (json.isBlank() || json == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            val list = ArrayList<AiExecutedActionRecord>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val txId = obj.optLong("transactionId", -1L)
                if (txId <= 0L) continue
                val rawType = obj.optString("type", TransactionType.EXPENSE.name)
                val type = runCatching { TransactionType.valueOf(rawType) }
                    .getOrDefault(TransactionType.EXPENSE)
                list.add(
                    AiExecutedActionRecord(
                        transactionId = txId,
                        feeTransactionId = if (obj.has("feeTransactionId") && !obj.isNull("feeTransactionId")) {
                            obj.optLong("feeTransactionId")
                        } else null,
                        type = type,
                        amountPiasters = obj.optLong("amountPiasters", 0L),
                        feePiasters = obj.optLong("feePiasters", 0L),
                        description = obj.optString("description", ""),
                        categoryNameAr = obj.optString("categoryNameAr").takeIf { it.isNotBlank() },
                        categoryNameEn = obj.optString("categoryNameEn").takeIf { it.isNotBlank() },
                        categoryColorHex = obj.optString("categoryColorHex").takeIf { it.isNotBlank() },
                        accountName = obj.optString("accountName", ""),
                        createdNewCategory = obj.optBoolean("createdNewCategory", false)
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun toDomain(entity: AiChatMessageEntity): AiChatMessage = AiChatMessage(
        id = entity.id,
        isUser = entity.isUser,
        text = entity.text,
        isError = entity.isError,
        executedActions = decodeExecutedActions(entity.executedActionsJson),
        isUndone = entity.isUndone,
        engineSource = entity.engineSource,
        timestampMillis = entity.timestampMillis
    )

    fun toEntity(msg: AiChatMessage, sessionId: String): AiChatMessageEntity = AiChatMessageEntity(
        id = msg.id,
        sessionId = sessionId,
        isUser = msg.isUser,
        text = msg.text,
        isError = msg.isError,
        isUndone = msg.isUndone,
        engineSource = msg.engineSource,
        executedActionsJson = encodeExecutedActions(msg.executedActions),
        timestampMillis = msg.timestampMillis
    )
}

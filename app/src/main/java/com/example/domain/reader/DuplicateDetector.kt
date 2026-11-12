package com.example.domain.reader

import com.example.data.local.dao.ImportedMessageDao
import com.example.data.local.dao.PendingTransactionDao
import com.example.data.local.dao.TransactionDao

class DuplicateDetector(
    private val transactionDao: TransactionDao,
    private val pendingDao: PendingTransactionDao,
    private val importedMessageDao: ImportedMessageDao
) {
    suspend fun isDuplicate(
        fingerprint: String,
        referenceNumber: String?,
        sourceName: String
    ): Boolean {
        if (transactionDao.countByFingerprint(fingerprint) > 0) return true
        if (pendingDao.countByFingerprint(fingerprint) > 0) return true
        if (!referenceNumber.isNullOrBlank() &&
            transactionDao.countByReferenceAndSource(referenceNumber, sourceName) > 0
        ) {
            return true
        }
        return false
    }

    suspend fun isMessageHashSeen(messageHash: String): Boolean {
        return importedMessageDao.countByHash(messageHash) > 0
    }
}

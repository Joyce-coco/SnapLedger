package com.snapledger.app.data.db

import androidx.room.*
import com.snapledger.app.data.model.Ledger
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledgers ORDER BY createdAt DESC")
    fun getAllLedgers(): Flow<List<Ledger>>

    @Insert
    suspend fun insert(ledger: Ledger): Long

    @Update
    suspend fun update(ledger: Ledger)

    @Delete
    suspend fun delete(ledger: Ledger)
}

package com.snapledger.app.data.db

import androidx.room.*
import com.snapledger.app.data.model.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE ledgerId = :ledgerId LIMIT 1")
    fun getBudgetByLedger(ledgerId: Long): Flow<Budget?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: Budget)
}

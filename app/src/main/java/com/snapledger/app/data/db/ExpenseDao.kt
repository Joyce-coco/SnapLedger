package com.snapledger.app.data.db

import androidx.room.*
import com.snapledger.app.data.model.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getExpensesBetween(startTime: Long, endTime: Long): Flow<List<Expense>>

    @Insert
    suspend fun insert(expense: Expense): Long

    @Insert
    suspend fun insertAll(expenses: List<Expense>)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT categoryName, SUM(amount) as total FROM expenses WHERE timestamp BETWEEN :startTime AND :endTime GROUP BY categoryName")
    fun getCategorySummary(startTime: Long, endTime: Long): Flow<List<CategorySummary>>

    // 按账本查询
    @Query("SELECT * FROM expenses WHERE ledgerId = :ledgerId ORDER BY timestamp DESC")
    fun getExpensesByLedger(ledgerId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE ledgerId = :ledgerId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getExpensesByLedgerBetween(ledgerId: Long, startTime: Long, endTime: Long): Flow<List<Expense>>

    @Query("SELECT categoryName, SUM(amount) as total FROM expenses WHERE ledgerId = :ledgerId AND timestamp BETWEEN :startTime AND :endTime AND type = 0 GROUP BY categoryName")
    fun getCategorySummaryByLedger(ledgerId: Long, startTime: Long, endTime: Long): Flow<List<CategorySummary>>

    @Query("SELECT SUM(amount) FROM expenses WHERE ledgerId = :ledgerId AND type = 0 AND timestamp BETWEEN :startTime AND :endTime")
    fun getTotalExpenseByLedger(ledgerId: Long, startTime: Long, endTime: Long): Flow<Double?>
}

data class CategorySummary(
    val categoryName: String,
    val total: Double
)

package com.snapledger.app.data.repository

import com.snapledger.app.data.db.*
import com.snapledger.app.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val ledgerDao: LedgerDao,
    private val budgetDao: BudgetDao
) {
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allLedgers: Flow<List<Ledger>> = ledgerDao.getAllLedgers()

    // Expense
    fun getExpensesByLedger(ledgerId: Long) = expenseDao.getExpensesByLedger(ledgerId)
    fun getExpensesByLedgerBetween(ledgerId: Long, start: Long, end: Long) = expenseDao.getExpensesByLedgerBetween(ledgerId, start, end)
    fun getExpensesBetween(start: Long, end: Long) = expenseDao.getExpensesBetween(start, end)
    fun getCategorySummary(start: Long, end: Long) = expenseDao.getCategorySummary(start, end)
    fun getCategorySummaryByLedger(ledgerId: Long, start: Long, end: Long) = expenseDao.getCategorySummaryByLedger(ledgerId, start, end)
    fun getTotalExpenseByLedger(ledgerId: Long, start: Long, end: Long) = expenseDao.getTotalExpenseByLedger(ledgerId, start, end)
    suspend fun insertExpense(expense: Expense) = expenseDao.insert(expense)
    suspend fun insertAllExpenses(expenses: List<Expense>) = expenseDao.insertAll(expenses)
    suspend fun updateExpense(expense: Expense) = expenseDao.update(expense)
    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    // Category
    suspend fun insertCategory(category: Category) = categoryDao.insert(category)
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    suspend fun initDefaultCategories() {
        if (categoryDao.getCount() == 0) {
            categoryDao.insertAll(DefaultCategories.getAll())
        }
    }

    // Ledger
    suspend fun insertLedger(ledger: Ledger) = ledgerDao.insert(ledger)
    suspend fun updateLedger(ledger: Ledger) = ledgerDao.update(ledger)
    suspend fun deleteLedger(ledger: Ledger) = ledgerDao.delete(ledger)

    // Budget
    fun getBudgetByLedger(ledgerId: Long) = budgetDao.getBudgetByLedger(ledgerId)
    suspend fun upsertBudget(budget: Budget) = budgetDao.upsert(budget)
}

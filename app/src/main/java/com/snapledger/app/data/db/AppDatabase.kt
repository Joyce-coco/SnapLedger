package com.snapledger.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.snapledger.app.data.model.Budget
import com.snapledger.app.data.model.Category
import com.snapledger.app.data.model.Expense
import com.snapledger.app.data.model.Ledger

@Database(
    entities = [Expense::class, Category::class, Ledger::class, Budget::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 账本表
                db.execSQL("CREATE TABLE IF NOT EXISTS ledgers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("INSERT INTO ledgers (id, name, createdAt) VALUES (1, '默认账本', ${System.currentTimeMillis()})")
                // Expense 新增字段
                db.execSQL("ALTER TABLE expenses ADD COLUMN ledgerId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE expenses ADD COLUMN type INTEGER NOT NULL DEFAULT 0")
                // 预算表
                db.execSQL("CREATE TABLE IF NOT EXISTS budgets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, monthlyBudget REAL NOT NULL, ledgerId INTEGER NOT NULL DEFAULT 1)")
            }
        }
    }
}

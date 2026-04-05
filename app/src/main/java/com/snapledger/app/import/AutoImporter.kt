package com.snapledger.app.`import`

import android.content.Context
import com.snapledger.app.R
import com.snapledger.app.data.model.Expense
import com.snapledger.app.data.model.Ledger
import com.snapledger.app.data.repository.ExpenseRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object AutoImporter {

    private const val PREF_NAME = "auto_import"
    private const val KEY_MARCH_2026 = "march_2026_imported"

    suspend fun importIfNeeded(context: Context, repository: ExpenseRepository): Long? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MARCH_2026, false)) return null

        // 创建或查找"三月花了多少钱"账本
        val ledgers = repository.getAllLedgersOnce()
        var ledger = ledgers.find { it.name == "三月花了多少钱" }
        val ledgerId: Long
        if (ledger != null) {
            ledgerId = ledger.id
        } else {
            ledgerId = repository.insertLedger(Ledger(name = "三月花了多少钱"))
        }

        // 读取内嵌 CSV
        val expenses = parseCsvResource(context, ledgerId)
        if (expenses.isNotEmpty()) {
            repository.insertAllExpenses(expenses)
            prefs.edit().putBoolean(KEY_MARCH_2026, true).apply()
        }

        return ledgerId
    }

    private fun parseCsvResource(context: Context, ledgerId: Long): List<Expense> {
        val expenses = mutableListOf<Expense>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        try {
            val input = context.resources.openRawResource(R.raw.march_2026)
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            val lines = reader.readLines()
            reader.close()

            // 跳过 BOM 和表头
            // CSV格式: 日期,金额,分类,二级分类,备注,收支
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue

                val cols = line.split(",")
                if (cols.size < 6) continue

                val dateStr = cols[0].trim()
                val amount = cols[1].trim().toDoubleOrNull() ?: continue
                if (amount <= 0) continue

                val categoryName = cols[2].trim()
                val subCategory = cols[3].trim()
                val note = cols[4].trim()
                val typeStr = cols[5].trim()

                val timestamp = try {
                    dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                val type = if (typeStr.contains("收入")) 1 else 0

                expenses.add(
                    Expense(
                        amount = amount,
                        categoryId = 0,
                        categoryName = categoryName,
                        subCategory = subCategory,
                        note = note,
                        timestamp = timestamp,
                        ledgerId = ledgerId,
                        type = type
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return expenses
    }
}

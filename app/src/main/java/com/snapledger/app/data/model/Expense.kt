package com.snapledger.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val categoryId: Long,
    val categoryName: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String? = null,
    val ledgerId: Long = 1,
    val type: Int = 0  // 0=支出, 1=收入
)

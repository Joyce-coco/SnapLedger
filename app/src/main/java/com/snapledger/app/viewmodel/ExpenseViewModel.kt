package com.snapledger.app.viewmodel

import android.net.Uri
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapledger.app.data.model.Category
import com.snapledger.app.data.model.Expense
import com.snapledger.app.data.model.Ledger
import com.snapledger.app.data.repository.ExpenseRepository
import com.snapledger.app.ocr.OcrProcessor
import com.snapledger.app.ocr.ReceiptParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OcrState(
    val isProcessing: Boolean = false,
    val amount: Double? = null,
    val category: String? = null,
    val rawText: String = "",
    val error: String? = null,
    val isIncome: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    // 多账本
    val ledgers = repository.allLedgers.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _currentLedgerId = MutableStateFlow(1L)
    val currentLedgerId = _currentLedgerId.asStateFlow()

    val currentLedgerName: StateFlow<String> = combine(ledgers, _currentLedgerId) { list, id ->
        list.find { it.id == id }?.name ?: "默认账本"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "默认账本")

    val expenses: StateFlow<List<Expense>> = _currentLedgerId.flatMapLatest { id ->
        repository.getExpensesByLedger(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories = repository.allCategories.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _ocrState = MutableStateFlow(OcrState())
    val ocrState = _ocrState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initDefaultCategories()
        }
    }

    fun switchLedger(ledgerId: Long) {
        _currentLedgerId.value = ledgerId
    }

    fun createLedger(name: String) {
        viewModelScope.launch {
            val id = repository.insertLedger(Ledger(name = name))
            _currentLedgerId.value = id
        }
    }

    fun deleteLedger(ledger: Ledger) {
        viewModelScope.launch { repository.deleteLedger(ledger) }
    }

    fun addExpense(
        amount: Double,
        categoryId: Long,
        categoryName: String,
        note: String,
        imagePath: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        type: Int = 0
    ) {
        viewModelScope.launch {
            repository.insertExpense(
                Expense(
                    amount = amount,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    note = note,
                    imagePath = imagePath,
                    timestamp = timestamp,
                    ledgerId = _currentLedgerId.value,
                    type = type
                )
            )
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { repository.updateExpense(expense) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    fun processReceipt(context: Context, uri: Uri) {
        viewModelScope.launch {
            _ocrState.value = OcrState(isProcessing = true)
            try {
                val text = OcrProcessor.recognizeFromUri(context, uri)
                val result = ReceiptParser.parse(text)
                _ocrState.value = OcrState(
                    amount = result.amount,
                    category = result.category,
                    rawText = result.rawText,
                    isIncome = result.isIncome
                )
            } catch (e: Exception) {
                _ocrState.value = OcrState(error = "识别失败: ${e.message}")
            }
        }
    }

    fun clearOcrState() {
        _ocrState.value = OcrState()
    }

    // 批量导入
    fun importExpenses(expenses: List<Expense>) {
        viewModelScope.launch {
            repository.insertAllExpenses(expenses)
        }
    }

    // 分类管理
    fun addCategory(name: String, icon: String, color: Long) {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name, icon = icon, color = color))
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { repository.updateCategory(category) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }
}

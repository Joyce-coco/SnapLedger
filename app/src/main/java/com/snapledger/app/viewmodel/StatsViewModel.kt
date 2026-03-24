package com.snapledger.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapledger.app.data.db.CategorySummary
import com.snapledger.app.data.model.Budget
import com.snapledger.app.data.model.Expense
import com.snapledger.app.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

enum class TimePeriod(val label: String) {
    WEEK("本周"), MONTH("本月")
}

data class BudgetStatus(
    val spent: Double = 0.0,
    val budget: Double = 0.0,
    val remaining: Double = 0.0,
    val isOverBudget: Boolean = false,
    val hasBudget: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _period = MutableStateFlow(TimePeriod.MONTH)
    val period = _period.asStateFlow()

    // 当前账本ID（默认1）
    private val _currentLedgerId = MutableStateFlow(1L)

    private val timeRange: Flow<Pair<Long, Long>> = _period.map { p ->
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        when (p) {
            TimePeriod.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
        }
        cal.timeInMillis to end
    }

    val expenses: StateFlow<List<Expense>> = timeRange.flatMapLatest { (start, end) ->
        repository.getExpensesBetween(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorySummary: StateFlow<List<CategorySummary>> = timeRange.flatMapLatest { (start, end) ->
        repository.getCategorySummary(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalAmount: StateFlow<Double> = expenses.map { list ->
        list.filter { it.type == 0 }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 预算
    val budget: StateFlow<Budget?> = _currentLedgerId.flatMapLatest { id ->
        repository.getBudgetByLedger(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val budgetStatus: StateFlow<BudgetStatus> = combine(totalAmount, budget) { spent, b ->
        if (b != null) {
            BudgetStatus(
                spent = spent,
                budget = b.monthlyBudget,
                remaining = b.monthlyBudget - spent,
                isOverBudget = spent > b.monthlyBudget,
                hasBudget = true
            )
        } else {
            BudgetStatus(spent = spent)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetStatus())

    fun setPeriod(p: TimePeriod) { _period.value = p }

    fun setLedgerId(id: Long) { _currentLedgerId.value = id }

    fun saveBudget(amount: Double) {
        viewModelScope.launch {
            val existing = budget.value
            repository.upsertBudget(
                Budget(
                    id = existing?.id ?: 0,
                    monthlyBudget = amount,
                    ledgerId = _currentLedgerId.value
                )
            )
        }
    }
}

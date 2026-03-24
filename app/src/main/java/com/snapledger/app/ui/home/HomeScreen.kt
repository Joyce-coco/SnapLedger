package com.snapledger.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapledger.app.data.model.Category
import com.snapledger.app.data.model.Expense
import com.snapledger.app.ui.util.categoryIcon
import com.snapledger.app.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ExpenseViewModel,
    onAddClick: () -> Unit,
    onCameraClick: () -> Unit,
    onStatsClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onImportClick: () -> Unit = {}
) {
    val expenses by viewModel.expenses.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val ledgers by viewModel.ledgers.collectAsState()
    val currentLedgerName by viewModel.currentLedgerName.collectAsState()
    val currentLedgerId by viewModel.currentLedgerId.collectAsState()

    var showLedgerMenu by remember { mutableStateOf(false) }
    var showNewLedgerDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayFormat = remember { SimpleDateFormat("MM月dd日 EEEE", Locale.CHINESE) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val todayFormat = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINESE) }
    val todayStr = remember { todayFormat.format(Date()) }

    val grouped = remember(expenses) {
        expenses.groupBy { dateFormat.format(Date(it.timestamp)) }
    }

    val monthTotal = remember(expenses) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val monthStart = cal.timeInMillis
        expenses.filter { it.timestamp >= monthStart && it.type == 0 }.sumOf { it.amount }
    }

    val monthIncome = remember(expenses) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val monthStart = cal.timeInMillis
        expenses.filter { it.timestamp >= monthStart && it.type == 1 }.sumOf { it.amount }
    }

    // 新建账本对话框
    if (showNewLedgerDialog) {
        var ledgerName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewLedgerDialog = false },
            title = { Text("新建账本") },
            text = {
                OutlinedTextField(
                    value = ledgerName,
                    onValueChange = { ledgerName = it },
                    label = { Text("账本名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (ledgerName.isNotBlank()) {
                            viewModel.createLedger(ledgerName.trim())
                            showNewLedgerDialog = false
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewLedgerDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                TextButton(onClick = { showLedgerMenu = true }) {
                                    Text(
                                        currentLedgerName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                DropdownMenu(
                                    expanded = showLedgerMenu,
                                    onDismissRequest = { showLedgerMenu = false }
                                ) {
                                    ledgers.forEach { ledger ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(ledger.name)
                                                    if (ledger.id == currentLedgerId) {
                                                        Spacer(Modifier.width(8.dp))
                                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            },
                                            onClick = {
                                                viewModel.switchLedger(ledger.id)
                                                showLedgerMenu = false
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("新建账本")
                                            }
                                        },
                                        onClick = {
                                            showLedgerMenu = false
                                            showNewLedgerDialog = true
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            todayStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onImportClick) {
                        Icon(Icons.Default.FileUpload, "导入")
                    }
                    IconButton(onClick = onCategoryClick) {
                        Icon(Icons.Default.Category, "分类管理")
                    }
                    IconButton(onClick = onStatsClick) {
                        Icon(Icons.Default.BarChart, "统计")
                    }
                }
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    onClick = onCameraClick,
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.CameraAlt, "拍照记账")
                }
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, "手动记账")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 月度总览卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("本月支出", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "¥ %.2f".format(monthTotal),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (monthIncome > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("本月收入", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "¥ %.2f".format(monthIncome),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }

            if (expenses.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("暂无记录", color = MaterialTheme.colorScheme.outline)
                        Text("点击 + 手动记账 或 📷 拍照记账", color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (dateStr, dayExpenses) ->
                        item {
                            val date = dateFormat.parse(dateStr)
                            val dayTotal = dayExpenses.filter { it.type == 0 }.sumOf { it.amount }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    date?.let { displayFormat.format(it) } ?: dateStr,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "¥ %.2f".format(dayTotal),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(dayExpenses, key = { it.id }) { expense ->
                            ExpenseItem(
                                expense = expense,
                                categories = categories,
                                timeFormat = timeFormat,
                                onDelete = { viewModel.deleteExpense(expense) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExpenseItem(
    expense: Expense,
    categories: List<Category>,
    timeFormat: SimpleDateFormat,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val category = categories.find { it.id == expense.categoryId }
    val iconColor = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primaryContainer
    val isIncome = expense.type == 1

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("删除这笔 ¥%.2f 的${expense.categoryName}记录?".format(expense.amount)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = { showDeleteDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(category?.icon ?: ""),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = iconColor
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(expense.categoryName, fontWeight = FontWeight.Medium)
                if (expense.note.isNotBlank()) {
                    Text(expense.note, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (isIncome) "+¥%.2f".format(expense.amount) else "-¥%.2f".format(expense.amount),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isIncome) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                )
                Text(
                    timeFormat.format(Date(expense.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

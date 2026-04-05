package com.snapledger.app.ui.add

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapledger.app.data.model.Category
import com.snapledger.app.ui.util.categoryIcon
import com.snapledger.app.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit,
    prefilledAmount: String? = null,
    prefilledCategory: String? = null,
    prefilledIsIncome: Boolean = false,
    editExpenseId: Long = 0,
    editNote: String = "",
    editTimestamp: Long = 0
) {
    val categories by viewModel.categories.collectAsState()
    val isEditMode = editExpenseId > 0

    var amount by remember { mutableStateOf(prefilledAmount ?: "") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var note by remember { mutableStateOf(if (isEditMode) editNote else "") }
    var subCategory by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(prefilledIsIncome) }

    // 时间选择状态
    val calendar = remember {
        Calendar.getInstance().also {
            if (editTimestamp > 0) it.timeInMillis = editTimestamp
        }
    }
    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var selectedHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }

    val dateDisplayFormat = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINESE) }
    val timeDisplayFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val selectedTimestamp by remember(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute) {
        derivedStateOf {
            Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    val dateStr by remember(selectedYear, selectedMonth, selectedDay) {
        derivedStateOf {
            Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay)
            }.let { dateDisplayFormat.format(it.time) }
        }
    }

    val timeStr by remember(selectedHour, selectedMinute) {
        derivedStateOf {
            "%02d:%02d".format(selectedHour, selectedMinute)
        }
    }

    val context = LocalContext.current

    // 自动选中 OCR 识别的分类
    LaunchedEffect(categories, prefilledCategory) {
        if (prefilledCategory != null && selectedCategory == null) {
            selectedCategory = categories.find { it.name == prefilledCategory }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "编辑记录" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // 收入/支出切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isIncome,
                    onClick = { isIncome = false },
                    label = { Text("支出") },
                    leadingIcon = if (!isIncome) {{ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }} else null
                )
                FilterChip(
                    selected = isIncome,
                    onClick = { isIncome = true },
                    label = { Text("收入") },
                    leadingIcon = if (isIncome) {{ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }} else null
                )
            }

            Spacer(Modifier.height(12.dp))

            // 金额输入
            OutlinedTextField(
                value = amount,
                onValueChange = { newVal ->
                    if (newVal.isEmpty() || newVal.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                        amount = newVal
                    }
                },
                label = { Text("金额") },
                prefix = { Text("¥ ", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // 时间选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    selectedYear = y
                                    selectedMonth = m
                                    selectedDay = d
                                },
                                selectedYear, selectedMonth, selectedDay
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(dateStr, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                OutlinedCard(
                    modifier = Modifier
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    selectedHour = h
                                    selectedMinute = m
                                },
                                selectedHour, selectedMinute, true
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(timeStr, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 分类选择
            Text("选择分类", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory?.id == cat.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedCategory = cat }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(cat.color)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = categoryIcon(cat.icon),
                                contentDescription = cat.name,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(cat.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 二级分类
            OutlinedTextField(
                value = subCategory,
                onValueChange = { subCategory = it },
                label = { Text("二级分类（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            // 备注
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            Spacer(Modifier.weight(1f))

            // 保存按钮
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull()
                    if (amountValue != null && amountValue > 0 && selectedCategory != null) {
                        if (isEditMode) {
                            viewModel.updateExpense(
                                com.snapledger.app.data.model.Expense(
                                    id = editExpenseId,
                                    amount = amountValue,
                                    categoryId = selectedCategory!!.id,
                                    categoryName = selectedCategory!!.name,
                                    subCategory = subCategory,
                                    note = note,
                                    timestamp = selectedTimestamp,
                                    ledgerId = viewModel.currentLedgerId.value,
                                    type = if (isIncome) 1 else 0
                                )
                            )
                        } else {
                            viewModel.addExpense(
                                amount = amountValue,
                                categoryId = selectedCategory!!.id,
                                categoryName = selectedCategory!!.name,
                                note = note,
                                subCategory = subCategory,
                                timestamp = selectedTimestamp,
                                type = if (isIncome) 1 else 0
                            )
                        }
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = amount.toDoubleOrNull() != null &&
                        (amount.toDoubleOrNull() ?: 0.0) > 0 &&
                        selectedCategory != null
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isEditMode) "更新" else "保存", fontSize = 18.sp)
            }
        }
    }
}

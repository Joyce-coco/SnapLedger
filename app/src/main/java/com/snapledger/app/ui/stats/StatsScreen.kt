package com.snapledger.app.ui.stats

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.snapledger.app.viewmodel.StatsViewModel
import com.snapledger.app.viewmodel.TimePeriod
import java.text.SimpleDateFormat
import java.util.*

private val chartColors = listOf(
    Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
    Color(0xFFA78BFA), Color(0xFF60A5FA), Color(0xFFF472B6),
    Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFF818CF8),
    Color(0xFF9CA3AF),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val period by viewModel.period.collectAsState()
    val categorySummary by viewModel.categorySummary.collectAsState()
    val totalAmount by viewModel.totalAmount.collectAsState()
    val budgetStatus by viewModel.budgetStatus.collectAsState()
    val customStart by viewModel.customStart.collectAsState()
    val customEnd by viewModel.customEnd.collectAsState()

    var showBudgetDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayFormat = remember { SimpleDateFormat("M月d日", Locale.CHINESE) }

    // 预算设置对话框
    if (showBudgetDialog) {
        var budgetInput by remember {
            mutableStateOf(
                if (budgetStatus.hasBudget) "%.0f".format(budgetStatus.budget) else ""
            )
        }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("设置月度预算") },
            text = {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { newVal ->
                        if (newVal.isEmpty() || newVal.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                            budgetInput = newVal
                        }
                    },
                    label = { Text("每月预算金额") },
                    prefix = { Text("¥ ", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = budgetInput.toDoubleOrNull()
                        if (value != null && value > 0) {
                            viewModel.saveBudget(value)
                            showBudgetDialog = false
                        }
                    },
                    enabled = budgetInput.toDoubleOrNull()?.let { it > 0 } == true
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消费统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showBudgetDialog = true }) {
                        Icon(Icons.Default.Savings, "设置预算")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 时间切换：本周 / 本月 / 自定义
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TimePeriod.entries.forEach { p ->
                        FilterChip(
                            selected = period == p,
                            onClick = {
                                if (p == TimePeriod.CUSTOM) {
                                    // 选"自定义"时弹出开始日期选择
                                    val cal = Calendar.getInstance()
                                    DatePickerDialog(
                                        context,
                                        { _, y1, m1, d1 ->
                                            val startCal = Calendar.getInstance()
                                            startCal.set(y1, m1, d1, 0, 0, 0)
                                            val startMs = startCal.timeInMillis

                                            // 再弹出结束日期
                                            DatePickerDialog(
                                                context,
                                                { _, y2, m2, d2 ->
                                                    val endCal = Calendar.getInstance()
                                                    endCal.set(y2, m2, d2)
                                                    viewModel.setCustomRange(startMs, endCal.timeInMillis)
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).apply {
                                                datePicker.minDate = startMs
                                                datePicker.maxDate = System.currentTimeMillis()
                                            }.show()
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).apply {
                                        // 最早一年前
                                        val oneYearAgo = Calendar.getInstance()
                                        oneYearAgo.add(Calendar.YEAR, -1)
                                        datePicker.minDate = oneYearAgo.timeInMillis
                                        datePicker.maxDate = System.currentTimeMillis()
                                    }.show()
                                } else {
                                    viewModel.setPeriod(p)
                                }
                            },
                            label = { Text(p.label) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // 自定义时间范围显示
                if (period == TimePeriod.CUSTOM && customStart > 0) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DateRange, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${displayFormat.format(Date(customStart))} — ${displayFormat.format(Date(customEnd))}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // 总金额卡片
            item {
                val periodLabel = when (period) {
                    TimePeriod.WEEK -> "本周"
                    TimePeriod.MONTH -> "本月"
                    TimePeriod.CUSTOM -> "所选时段"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${periodLabel}支出", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "¥ %.2f".format(totalAmount),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 预算进度（仅本月模式显示）
            if (period == TimePeriod.MONTH) {
                item {
                    if (budgetStatus.hasBudget) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("月度预算", style = MaterialTheme.typography.titleSmall)
                                    TextButton(onClick = { showBudgetDialog = true }) {
                                        Text("修改", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                val progress = if (budgetStatus.budget > 0)
                                    (budgetStatus.spent / budgetStatus.budget).toFloat().coerceIn(0f, 1.5f) else 0f
                                val progressClamped = progress.coerceAtMost(1f)
                                val progressColor = when {
                                    progress > 1f -> MaterialTheme.colorScheme.error
                                    progress > 0.8f -> Color(0xFFF59E0B)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                LinearProgressIndicator(
                                    progress = { progressClamped },
                                    modifier = Modifier.fillMaxWidth().height(12.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    color = progressColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("已花 ¥%.0f".format(budgetStatus.spent), style = MaterialTheme.typography.bodyMedium)
                                    Text("预算 ¥%.0f".format(budgetStatus.budget), style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(Modifier.height(4.dp))
                                if (budgetStatus.isOverBudget) {
                                    Text("已超支 ¥%.0f".format(-budgetStatus.remaining),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold)
                                } else {
                                    Text("剩余 ¥%.0f".format(budgetStatus.remaining),
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showBudgetDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AddCircleOutline, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("设置月度预算", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // ===== 扇形图 =====
            item {
                Text("分类占比", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            if (categorySummary.isNotEmpty()) {
                item {
                    val sortedSummary = categorySummary.sortedByDescending { it.total }
                    val slices = sortedSummary.mapIndexed { index, s ->
                        PieSlice(
                            label = s.categoryName,
                            value = s.total,
                            color = chartColors[index % chartColors.size]
                        )
                    }

                    DonutChart(
                        slices = slices,
                        centerText = "¥%.0f".format(totalAmount),
                        centerSubText = "${categorySummary.size}个分类",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    // 图例
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column {
                            sortedSummary.chunked(3).forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    row.forEach { s ->
                                        val idx = sortedSummary.indexOf(s)
                                        val color = chartColors[idx % chartColors.size]
                                        val pct = if (totalAmount > 0) s.total / totalAmount * 100 else 0.0
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "${s.categoryName} %.0f%%".format(pct),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }

            // ===== 分类条形明细 =====
            item {
                Text("分类明细", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            if (categorySummary.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无数据", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                items(categorySummary.sortedByDescending { it.total }) { summary ->
                    val index = categorySummary.indexOf(summary)
                    val color = chartColors[index % chartColors.size]
                    val percent = if (totalAmount > 0) summary.total / totalAmount else 0.0

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(12.dp).clip(CircleShape).background(color)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            summary.categoryName,
                            modifier = Modifier.width(56.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { percent.toFloat() },
                            modifier = Modifier.weight(1f).height(16.dp).clip(MaterialTheme.shapes.small),
                            color = color,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "¥%.0f".format(summary.total),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(64.dp)
                        )
                        Text(
                            "%.0f%%".format(percent * 100),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

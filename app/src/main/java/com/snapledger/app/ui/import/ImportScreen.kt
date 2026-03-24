package com.snapledger.app.ui.`import`

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snapledger.app.`import`.ImportHelper
import com.snapledger.app.`import`.ImportResult
import com.snapledger.app.data.model.Expense
import com.snapledger.app.viewmodel.ExpenseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLedgerId by viewModel.currentLedgerId.collectAsState()

    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var savedCount by remember { mutableIntStateOf(0) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            isLoading = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    ImportHelper.importFromUri(context, uri, currentLedgerId)
                }
                importResult = result
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入账单") },
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
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("支持导入 Excel (.xlsx/.xls) 和 CSV 文件", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "表头需包含：金额（必填）、分类、备注、日期、收支",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 选择文件按钮
            Button(
                onClick = {
                    filePicker.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "text/csv",
                        "text/comma-separated-values",
                        "*/*"
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && !isSaving
            ) {
                Icon(Icons.Default.FileOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("选择文件")
            }

            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("正在解析文件...")
                        }
                    }
                }

                savedCount > 0 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981))
                            Spacer(Modifier.width(12.dp))
                            Text("成功导入 $savedCount 条记录", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                importResult != null -> {
                    val result = importResult!!
                    // 统计信息
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip("总行数", "${result.totalRows}")
                        StatChip("可导入", "${result.expenses.size}", Color(0xFF10B981))
                        if (result.errors.isNotEmpty()) {
                            StatChip("错误", "${result.errors.size}", MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 导入按钮
                    if (result.expenses.isNotEmpty()) {
                        Button(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    viewModel.importExpenses(result.expenses)
                                    savedCount = result.expenses.size
                                    isSaving = false
                                    importResult = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isSaving) "导入中..." else "确认导入 ${result.expenses.size} 条")
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // 预览列表
                    Text("预览", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(result.expenses.take(50)) { expense ->
                            ImportPreviewItem(expense)
                        }
                        if (result.expenses.size > 50) {
                            item {
                                Text(
                                    "... 还有 ${result.expenses.size - 50} 条",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 错误列表
                        if (result.errors.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                Text("解析错误", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                            }
                            items(result.errors) { error ->
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("选择 Excel 或 CSV 文件导入", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color = MaterialTheme.colorScheme.primary) {
    Card {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ImportPreviewItem(expense: Expense) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.categoryName, fontWeight = FontWeight.Medium)
                if (expense.note.isNotBlank()) {
                    Text(
                        expense.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (expense.type == 1) "+¥%.2f".format(expense.amount) else "-¥%.2f".format(expense.amount),
                    fontWeight = FontWeight.SemiBold,
                    color = if (expense.type == 1) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                )
                Text(
                    dateFormat.format(Date(expense.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

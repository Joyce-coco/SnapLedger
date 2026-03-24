package com.snapledger.app.ui.camera

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.core.content.FileProvider
import com.snapledger.app.data.model.Category
import com.snapledger.app.ocr.ParsedTransaction
import com.snapledger.app.ui.util.categoryIcon
import com.snapledger.app.viewmodel.ExpenseViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: ExpenseViewModel,
    onNavigateToAdd: (amount: String, category: String, isIncome: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val ocrState by viewModel.ocrState.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var savedCount by remember { mutableIntStateOf(0) }

    fun createTempUri(): Uri {
        val file = File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            viewModel.processReceipt(context, photoUri!!)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.processReceipt(context, uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempUri()
            photoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能识别") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearOcrState()
                        onBack()
                    }) {
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                ocrState.isProcessing -> {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("AI 识别中...", style = MaterialTheme.typography.titleMedium)
                    Text("正在分析图片中的交易记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                }

                savedCount > 0 -> {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.CheckCircle, null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF10B981))
                    Spacer(Modifier.height(16.dp))
                    Text("成功导入 $savedCount 条记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            savedCount = 0
                            viewModel.clearOcrState()
                        }) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("继续识别")
                        }
                        Button(onClick = {
                            viewModel.clearOcrState()
                            onBack()
                        }) {
                            Text("返回首页")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }

                ocrState.isAiMode && ocrState.aiTransactions.isNotEmpty() -> {
                    // AI 多条交易结果
                    AiResultsView(
                        transactions = ocrState.aiTransactions,
                        categories = categories,
                        onSave = { selected ->
                            viewModel.saveAiTransactions(selected)
                            savedCount = selected.count { it.selected }
                        },
                        onRetry = { viewModel.clearOcrState() }
                    )
                }

                ocrState.error != null -> {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("识别失败", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error)
                            Text(ocrState.error!!)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.clearOcrState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("重新识别")
                    }
                }

                ocrState.amount != null -> {
                    // 本地单条识别结果（AI降级）
                    Spacer(Modifier.height(16.dp))
                    OcrResultEditor(
                        initialAmount = ocrState.amount,
                        initialCategory = ocrState.category,
                        initialIsIncome = ocrState.isIncome,
                        rawText = ocrState.rawText,
                        categories = categories,
                        onSave = { amount, categoryId, categoryName, note, isIncome ->
                            viewModel.addExpense(
                                amount = amount,
                                categoryId = categoryId,
                                categoryName = categoryName,
                                note = note,
                                type = if (isIncome) 1 else 0
                            )
                            viewModel.clearOcrState()
                            onBack()
                        },
                        onContinueEdit = { amount, category, isIncome ->
                            onNavigateToAdd(amount, category, isIncome)
                            viewModel.clearOcrState()
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.clearOcrState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("重新拍照")
                    }
                }

                else -> {
                    // 初始状态
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("AI 智能账单识别", style = MaterialTheme.typography.titleMedium)
                    Text("拍摄或选择账单截图，自动提取多条交易",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(40.dp))

                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("拍照", fontSize = 18.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("从相册选择", fontSize = 18.sp)
                    }

                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ===== AI 多条识别结果视图 =====

@Composable
private fun AiResultsView(
    transactions: List<ParsedTransaction>,
    categories: List<Category>,
    onSave: (List<ParsedTransaction>) -> Unit,
    onRetry: () -> Unit
) {
    // 用可变列表跟踪选中状态
    val items = remember(transactions) {
        transactions.map { it.copy() }.toMutableStateList()
    }
    val selectedCount = items.count { it.selected }
    val totalAmount = items.filter { it.selected && it.type == "支出" }.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxSize()) {
        // 统计栏
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AI 识别到 ${items.size} 条交易",
                        fontWeight = FontWeight.Bold)
                    Text("已选 $selectedCount 条，支出合计 ¥%.2f".format(totalAmount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
                // 全选/取消全选
                TextButton(onClick = {
                    val allSelected = items.all { it.selected }
                    items.forEachIndexed { i, _ -> items[i] = items[i].copy(selected = !allSelected) }
                }) {
                    Text(if (items.all { it.selected }) "取消全选" else "全选")
                }
            }
        }

        // 交易列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(items) { index, item ->
                AiTransactionItem(
                    transaction = item,
                    categories = categories,
                    onToggle = {
                        items[index] = items[index].copy(selected = !items[index].selected)
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 操作按钮
        Button(
            onClick = { onSave(items) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = selectedCount > 0
        ) {
            Icon(Icons.Default.SaveAlt, null)
            Spacer(Modifier.width(8.dp))
            Text("导入 $selectedCount 条记录", fontSize = 16.sp)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("重新识别")
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AiTransactionItem(
    transaction: ParsedTransaction,
    categories: List<Category>,
    onToggle: () -> Unit
) {
    val category = categories.find { it.name == transaction.category }
    val iconColor = category?.let { Color(it.color) } ?: Color(0xFF9CA3AF)
    val isIncome = transaction.type == "收入"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (transaction.selected)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选中标记
            Checkbox(
                checked = transaction.selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(8.dp))

            // 分类图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(category?.icon ?: "MoreHoriz"),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconColor
                )
            }

            Spacer(Modifier.width(10.dp))

            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.note.ifBlank { transaction.category },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    Text(
                        transaction.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = iconColor
                    )
                    if (transaction.time.isNotBlank()) {
                        Text(
                            " · ${transaction.time}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 金额
            Text(
                if (isIncome) "+¥%.2f".format(transaction.amount) else "-¥%.2f".format(transaction.amount),
                fontWeight = FontWeight.SemiBold,
                color = if (isIncome) Color(0xFF10B981) else MaterialTheme.colorScheme.error
            )
        }
    }
}

// ===== 本地单条识别编辑器（降级模式）=====

@Composable
private fun OcrResultEditor(
    initialAmount: Double?,
    initialCategory: String?,
    initialIsIncome: Boolean,
    rawText: String,
    categories: List<Category>,
    onSave: (amount: Double, categoryId: Long, categoryName: String, note: String, isIncome: Boolean) -> Unit,
    onContinueEdit: (amount: String, category: String, isIncome: Boolean) -> Unit
) {
    var editedAmount by remember { mutableStateOf(initialAmount?.let { "%.2f".format(it) } ?: "") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var note by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(initialIsIncome) }
    var showRaw by remember { mutableStateOf(false) }

    LaunchedEffect(categories, initialCategory) {
        if (initialCategory != null && selectedCategory == null) {
            selectedCategory = categories.find { it.name == initialCategory }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("识别结果（可修改）", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !isIncome, onClick = { isIncome = false }, label = { Text("支出") })
                FilterChip(selected = isIncome, onClick = { isIncome = true }, label = { Text("收入") })
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = editedAmount,
                onValueChange = { newVal ->
                    if (newVal.isEmpty() || newVal.matches(Regex("""^\d*\.?\d{0,2}$"""))) editedAmount = newVal
                },
                label = { Text("金额") },
                prefix = { Text("¥ ", fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Text("分类", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory?.id == cat.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedCategory = cat }
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(cat.color)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(categoryIcon(cat.icon), null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Text(cat.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { showRaw = !showRaw }) {
        Text(if (showRaw) "隐藏原文" else "查看识别原文")
    }
    if (showRaw && rawText.isNotBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(rawText, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }

    Spacer(Modifier.height(16.dp))

    val amountValue = editedAmount.toDoubleOrNull()
    val canSave = amountValue != null && amountValue > 0 && selectedCategory != null

    Button(
        onClick = { if (canSave) onSave(amountValue!!, selectedCategory!!.id, selectedCategory!!.name, note, isIncome) },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = canSave
    ) {
        Icon(Icons.Default.Check, null)
        Spacer(Modifier.width(8.dp))
        Text("直接保存", fontSize = 16.sp)
    }

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = { onContinueEdit(editedAmount, selectedCategory?.name ?: "", isIncome) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("继续编辑") }
}

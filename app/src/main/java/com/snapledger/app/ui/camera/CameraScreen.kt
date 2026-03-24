package com.snapledger.app.ui.camera

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
                title = { Text("拍照识别") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (ocrState.isProcessing) {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("正在识别...", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))

            } else if (ocrState.amount != null || ocrState.error != null) {
                // ===== 识别结果 - 可编辑纠错 =====
                if (ocrState.error != null) {
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
                } else {
                    // 可编辑的识别结果
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
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.clearOcrState() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("重新拍照")
                }

            } else {
                // ===== 初始状态 =====
                Spacer(Modifier.weight(1f))

                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Text("拍摄小票或从相册选择", style = MaterialTheme.typography.titleMedium)
                Text("支持小票、微信/支付宝账单截图",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("拍照", fontSize = 18.sp)
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
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

    // 自动匹配分类
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

            // 收入/支出
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isIncome,
                    onClick = { isIncome = false },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = isIncome,
                    onClick = { isIncome = true },
                    label = { Text("收入") }
                )
            }

            Spacer(Modifier.height(8.dp))

            // 金额 - 可编辑
            OutlinedTextField(
                value = editedAmount,
                onValueChange = { newVal ->
                    if (newVal.isEmpty() || newVal.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                        editedAmount = newVal
                    }
                },
                label = { Text("金额") },
                prefix = { Text("¥ ", fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            // 分类 - 横向滚动选择
            Text("分类", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory?.id == cat.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedCategory = cat }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(cat.color)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = categoryIcon(cat.icon),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(cat.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 备注
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // 识别原文折叠
    TextButton(onClick = { showRaw = !showRaw }) {
        Text(if (showRaw) "隐藏原文" else "查看识别原文")
    }
    if (showRaw && rawText.isNotBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(rawText, modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
    }

    Spacer(Modifier.height(16.dp))

    // 操作按钮
    val amountValue = editedAmount.toDoubleOrNull()
    val canSave = amountValue != null && amountValue > 0 && selectedCategory != null

    Button(
        onClick = {
            if (canSave) {
                onSave(amountValue!!, selectedCategory!!.id, selectedCategory!!.name, note, isIncome)
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        enabled = canSave
    ) {
        Icon(Icons.Default.Check, null)
        Spacer(Modifier.width(8.dp))
        Text("直接保存", fontSize = 16.sp)
    }

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = {
            onContinueEdit(
                editedAmount,
                selectedCategory?.name ?: "",
                isIncome
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("继续编辑")
    }
}

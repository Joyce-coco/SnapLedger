package com.snapledger.app.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snapledger.app.update.UpdateChecker
import com.snapledger.app.update.UpdateInfo
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() },
        title = { Text("发现新版本 ${updateInfo.versionName}") },
        text = {
            Column {
                if (updateInfo.changelog.isNotBlank()) {
                    Text("更新内容：", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(updateInfo.changelog, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }

                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "下载中... $progress%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isDownloading = true
                    scope.launch {
                        val file = UpdateChecker.downloadApk(context, updateInfo.downloadUrl) {
                            progress = it
                        }
                        if (file != null) {
                            UpdateChecker.installApk(context, file)
                        }
                        isDownloading = false
                    }
                },
                enabled = !isDownloading
            ) {
                Text(if (isDownloading) "下载中" else "立即更新")
            }
        },
        dismissButton = {
            if (!updateInfo.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text("稍后再说")
                }
            }
        }
    )
}

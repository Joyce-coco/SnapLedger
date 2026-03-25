package com.snapledger.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.snapledger.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ParsedTransaction(
    val amount: Double = 0.0,
    val category: String = "其他",
    val note: String = "",
    val time: String = "",
    val type: String = "支出",
    var selected: Boolean = true
)

object AiTransactionParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private const val API_URL = "http://model.mify.ai.srv/anthropic/v1/messages"
    private const val MODEL = "ppio/pa/claude-opus-4-6"

    private const val SYSTEM_PROMPT = """你是一个账单识别助手。用户会给你一张手机账单截图，请直接从图片中提取所有交易记录。

返回JSON数组，每条记录包含：
- amount: 金额（数字，不带符号和减号）
- category: 分类，必须是以下之一：餐饮、交通、购物、娱乐、居住、医疗、教育、通讯、日用、其他
- note: 备注（商家名或商品简述，不超过20字，去掉订单编号）
- time: 时间（格式 MM-dd HH:mm）
- type: "支出" 或 "收入"

规则：
1. 金额前有减号"-"的是支出，无减号的正数是收入
2. 分类映射：餐饮美食→餐饮，交通出行→交通，日用百货→购物，文化休闲→娱乐，酒店旅游→居住，家居家装→日用，美容美发→日用，服饰装扮→购物，教育培训→教育，运动户外→娱乐，宠物→日用
3. 投资理财、不计收支、余额宝收益→跳过不提取
4. 金额为0→跳过
5. 交易关闭/退款中→跳过

只返回JSON数组，不要其他文字。"""

    var lastError: String? = null
        private set

    /** 直接发送图片给 Claude Vision API 识别 */
    suspend fun parseImage(context: Context, imageUri: Uri): List<ParsedTransaction> = withContext(Dispatchers.IO) {
        lastError = null
        try {
            val base64Image = imageToBase64(context, imageUri)
            if (base64Image == null) {
                lastError = "无法读取图片"
                return@withContext emptyList()
            }

            val requestBody = buildVisionRequest(base64Image)
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", BuildConfig.CLAUDE_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                lastError = "API错误 ${response.code}: ${body.take(300)}"
                return@withContext emptyList()
            }

            val result = extractTransactions(body)
            if (result.isEmpty()) {
                lastError = "AI未能识别交易，响应: ${body.take(500)}"
            }
            result
        } catch (e: Exception) {
            lastError = "请求失败: ${e.javaClass.simpleName} - ${e.message}"
            emptyList()
        }
    }

    /** OCR文本解析（降级方案） */
    suspend fun parseText(ocrText: String): List<ParsedTransaction> = withContext(Dispatchers.IO) {
        lastError = null
        try {
            val requestBody = buildTextRequest(ocrText)
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", BuildConfig.CLAUDE_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                lastError = "API错误 ${response.code}"
                return@withContext emptyList()
            }
            extractTransactions(body)
        } catch (e: Exception) {
            lastError = "${e.message}"
            emptyList()
        }
    }

    private fun imageToBase64(context: Context, uri: Uri): String? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // 如果图片太大，缩放到合理尺寸（减少 API 传输量）
            val maxDim = 1600
            val bitmap = if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)
                val newW = (originalBitmap.width * scale).toInt()
                val newH = (originalBitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            return null
        }
    }

    /** 构建 Vision API 请求（图片直接识别） */
    private fun buildVisionRequest(base64Image: String): String {
        val imageContent = JsonObject().apply {
            addProperty("type", "image")
            add("source", JsonObject().apply {
                addProperty("type", "base64")
                addProperty("media_type", "image/jpeg")
                addProperty("data", base64Image)
            })
        }

        val textContent = JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", "请从这张账单截图中提取所有交易记录。")
        }

        val contentArray = JsonArray().apply {
            add(imageContent)
            add(textContent)
        }

        val userMessage = JsonObject().apply {
            addProperty("role", "user")
            add("content", contentArray)
        }

        val messages = JsonArray().apply { add(userMessage) }

        val requestJson = JsonObject().apply {
            addProperty("model", MODEL)
            addProperty("max_tokens", 4096)
            addProperty("system", SYSTEM_PROMPT)
            add("messages", messages)
        }

        return gson.toJson(requestJson)
    }

    /** 构建文本请求（降级方案） */
    private fun buildTextRequest(ocrText: String): String {
        val userMessage = JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", "请从以下OCR文字中提取所有交易记录：\n\n$ocrText")
        }
        val messages = JsonArray().apply { add(userMessage) }
        val requestJson = JsonObject().apply {
            addProperty("model", MODEL)
            addProperty("max_tokens", 4096)
            addProperty("system", SYSTEM_PROMPT)
            add("messages", messages)
        }
        return gson.toJson(requestJson)
    }

    private fun extractTransactions(responseBody: String): List<ParsedTransaction> {
        try {
            val responseMap = gson.fromJson(responseBody, Map::class.java)
            val content = responseMap["content"] as? List<*> ?: run {
                lastError = "响应缺少content: ${responseBody.take(300)}"
                return emptyList()
            }
            val firstBlock = content.firstOrNull() as? Map<*, *> ?: return emptyList()
            val text = firstBlock["text"] as? String ?: return emptyList()

            val jsonStr = extractJsonArray(text)
            val listType = object : TypeToken<List<ParsedTransaction>>() {}.type
            val transactions: List<ParsedTransaction> = gson.fromJson(jsonStr, listType)
            return transactions.filter { it.amount > 0 }
        } catch (e: Exception) {
            lastError = "解析失败: ${e.message}"
            return emptyList()
        }
    }

    private fun extractJsonArray(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) return trimmed
        val codeBlockRegex = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?```""")
        val match = codeBlockRegex.find(trimmed)
        if (match != null) return match.groupValues[1].trim()
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return "[]"
    }
}

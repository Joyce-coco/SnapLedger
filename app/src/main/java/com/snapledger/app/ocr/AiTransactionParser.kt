package com.snapledger.app.ocr

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

    // 使用代理 API 地址
    private const val API_URL = "http://model.mify.ai.srv/anthropic/v1/messages"
    private const val MODEL = "ppio/pa/claude-opus-4-6"

    private const val SYSTEM_PROMPT = """你是一个账单识别助手。用户会给你一段从手机截图OCR识别出的文字，可能包含多条交易记录。

请从中提取所有交易记录，返回JSON数组。每条记录包含：
- amount: 金额（数字，不带符号）
- category: 分类，必须是以下之一：餐饮、交通、购物、娱乐、居住、医疗、教育、通讯、日用、其他
- note: 备注（商家名或商品简述，不超过20字）
- time: 时间（格式 MM-dd HH:mm，如果有的话）
- type: "支出" 或 "收入"

规则：
1. 金额前有减号"-"的是支出，无减号的是收入
2. 支付宝分类映射：餐饮美食→餐饮，交通出行→交通，日用百货→购物，文化休闲→娱乐，酒店旅游→居住，家居家装→日用，美容美发→日用，服饰装扮→购物，教育培训→教育，运动户外→娱乐，宠物→日用
3. 投资理财、不计收支、余额宝收益等跳过不提取
4. 金额为0的跳过
5. note要简洁有意义，去掉订单号

只返回JSON数组，不要其他文字。如果无法识别任何交易，返回空数组 []"""

    // 最近一次的错误信息，用于调试
    var lastError: String? = null
        private set

    suspend fun parse(ocrText: String): List<ParsedTransaction> = withContext(Dispatchers.IO) {
        lastError = null
        try {
            val requestBody = buildRequestJson(ocrText)
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
                lastError = "API错误 ${response.code}: $body"
                return@withContext emptyList()
            }

            val result = extractTransactions(body)
            if (result.isEmpty()) {
                lastError = "AI返回为空，原始响应: ${body.take(500)}"
            }
            result
        } catch (e: Exception) {
            lastError = "请求失败: ${e.javaClass.simpleName} - ${e.message}"
            emptyList()
        }
    }

    /** 用 Gson 构建安全的 JSON 请求体 */
    private fun buildRequestJson(ocrText: String): String {
        val userMessage = JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", "请从以下OCR文字中提取所有交易记录：\n\n$ocrText")
        }

        val messages = JsonArray().apply {
            add(userMessage)
        }

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
                lastError = "响应缺少content字段: ${responseBody.take(300)}"
                return emptyList()
            }
            val firstBlock = content.firstOrNull() as? Map<*, *> ?: return emptyList()
            val text = firstBlock["text"] as? String ?: return emptyList()

            val jsonStr = extractJsonArray(text)
            val listType = object : TypeToken<List<ParsedTransaction>>() {}.type
            val transactions: List<ParsedTransaction> = gson.fromJson(jsonStr, listType)
            return transactions.filter { it.amount > 0 }
        } catch (e: Exception) {
            lastError = "解析响应失败: ${e.message}"
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

package com.snapledger.app.ocr

import com.google.gson.Gson
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
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private const val API_URL = "https://api.anthropic.com/v1/messages"

    private val SYSTEM_PROMPT = """
你是一个账单识别助手。用户会给你一段从手机截图OCR识别出的文字，可能包含多条交易记录。

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

只返回JSON数组，不要其他文字。如果无法识别任何交易，返回空数组 []
""".trimIndent()

    suspend fun parse(ocrText: String): List<ParsedTransaction> = withContext(Dispatchers.IO) {
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
            if (!response.isSuccessful) {
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            extractTransactions(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildRequestJson(ocrText: String): String {
        val escapedText = ocrText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val escapedSystem = SYSTEM_PROMPT.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """
        {
            "model": "claude-haiku-4-5-20251001",
            "max_tokens": 2048,
            "system": "$escapedSystem",
            "messages": [
                {"role": "user", "content": "请从以下OCR文字中提取所有交易记录：\n\n$escapedText"}
            ]
        }
        """.trimIndent()
    }

    private fun extractTransactions(responseBody: String): List<ParsedTransaction> {
        try {
            // 从 Claude API 响应中提取 content text
            val responseMap = gson.fromJson(responseBody, Map::class.java)
            val content = responseMap["content"] as? List<*> ?: return emptyList()
            val firstBlock = content.firstOrNull() as? Map<*, *> ?: return emptyList()
            val text = firstBlock["text"] as? String ?: return emptyList()

            // 提取 JSON 数组（可能被包在 markdown code block 里）
            val jsonStr = extractJsonArray(text)

            val listType = object : TypeToken<List<ParsedTransaction>>() {}.type
            val transactions: List<ParsedTransaction> = gson.fromJson(jsonStr, listType)
            return transactions.filter { it.amount > 0 }
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun extractJsonArray(text: String): String {
        // 尝试找到 JSON 数组
        val trimmed = text.trim()

        // 如果直接就是 JSON 数组
        if (trimmed.startsWith("[")) return trimmed

        // 从 markdown code block 中提取
        val codeBlockRegex = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?```""")
        val match = codeBlockRegex.find(trimmed)
        if (match != null) return match.groupValues[1].trim()

        // 找第一个 [ 到最后一个 ]
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)

        return "[]"
    }
}

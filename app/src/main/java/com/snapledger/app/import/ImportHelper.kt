package com.snapledger.app.`import`

import android.content.Context
import android.net.Uri
import com.snapledger.app.data.model.Expense
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

data class ImportResult(
    val expenses: List<Expense>,
    val errors: List<String>,
    val totalRows: Int
)

object ImportHelper {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),
        SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()),
    )

    // 支付宝分类 → App分类 映射
    private val alipayCategory = mapOf(
        "餐饮美食" to "餐饮",
        "交通出行" to "交通",
        "日用百货" to "购物",
        "文化休闲" to "娱乐",
        "运动户外" to "娱乐",
        "酒店旅游" to "居住",
        "美容美发" to "日用",
        "家居家装" to "日用",
        "服饰装扮" to "购物",
        "教育培训" to "教育",
        "医疗健康" to "医疗",
        "数码电器" to "购物",
        "宠物" to "日用",
        "充值缴费" to "日用",
        "转账红包" to "其他",
        "商业服务" to "其他",
        "投资理财" to "其他",
        "信用借还" to "其他",
        "公益" to "其他",
    )

    // 关键词 → 分类（用于从商品说明智能推断）
    private val keywordCategory = listOf(
        listOf("外卖", "美团", "饿了么", "肯德基", "麦当劳", "奶茶", "咖啡", "火锅", "串串", "小吃", "餐厅", "饭店") to "餐饮",
        listOf("打车", "滴滴", "快车", "代驾", "地铁", "公交", "火车", "高铁", "机票", "12306", "加油") to "交通",
        listOf("淘宝", "京东", "拼多多", "抖音电商", "超市") to "购物",
        listOf("电影", "KTV", "游戏", "App Store", "Apple Music", "视频", "会员", "足道", "SPA", "按摩") to "娱乐",
        listOf("房租", "水电", "物业", "燃气", "宽带", "水费", "电费") to "居住",
        listOf("医院", "药店", "门诊", "体检") to "医疗",
        listOf("课程", "培训", "考试", "PMP", "软考", "教材", "网课") to "教育",
        listOf("话费", "流量", "手机", "充值") to "通讯",
        listOf("洗发", "护肤", "化妆", "面膜", "护发", "洗衣", "收纳") to "日用",
    )

    fun importFromUri(context: Context, uri: Uri, ledgerId: Long): ImportResult {
        val fileName = getFileName(context, uri)
        return when {
            fileName.endsWith(".xlsx", true) -> importXlsx(context, uri, ledgerId)
            fileName.endsWith(".csv", true) -> importCsv(context, uri, ledgerId)
            else -> ImportResult(emptyList(), listOf("不支持的文件格式: $fileName\n请使用 .xlsx 或 .csv"), 0)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) {
                name = cursor.getString(nameIdx)
            }
        }
        return name
    }

    // ===== CSV 解析（自动检测支付宝/通用格式）=====

    private fun importCsv(context: Context, uri: Uri, ledgerId: Long): ImportResult {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val raw = input.readBytes()

                // 尝试多种编码读取
                val text = tryDecode(raw)
                val lines = text.split("\n").map { it.trimEnd('\r') }.filter { it.isNotBlank() }

                if (lines.isEmpty()) return ImportResult(emptyList(), listOf("空文件"), 0)

                // 检测是否为支付宝格式
                val isAlipay = lines.any { it.contains("支付宝") || it.startsWith("交易时间,交易分类,") }
                return if (isAlipay) {
                    importAlipay(lines, ledgerId)
                } else {
                    importGenericCsv(lines, ledgerId)
                }
            }
        } catch (e: Exception) {
            return ImportResult(emptyList(), listOf("读取CSV失败: ${e.message}"), 0)
        }
        return ImportResult(emptyList(), listOf("无法读取文件"), 0)
    }

    private fun tryDecode(raw: ByteArray): String {
        // 优先尝试 GB18030（支付宝用的编码），再试 UTF-8
        for (charset in listOf("GB18030", "GBK", "UTF-8")) {
            try {
                val text = String(raw, charset(charset))
                // 简单检测：如果解码后包含常见中文字符说明编码正确
                if (text.contains("交易") || text.contains("金额") || text.contains("日期") || text.length > 100) {
                    return text
                }
            } catch (_: Exception) {}
        }
        return String(raw, Charsets.UTF_8)
    }

    // ===== 支付宝 CSV 专用解析 =====

    private fun importAlipay(lines: List<String>, ledgerId: Long): ImportResult {
        val expenses = mutableListOf<Expense>()
        val errors = mutableListOf<String>()

        // 找到数据表头行
        var headerIdx = -1
        for (i in lines.indices) {
            if (lines[i].trimStart().startsWith("交易时间,")) {
                headerIdx = i
                break
            }
        }
        if (headerIdx < 0) {
            return ImportResult(emptyList(), listOf("未找到支付宝表头行"), 0)
        }

        // 解析表头列索引
        val header = parseCsvLine(lines[headerIdx])
        val colTime = header.indexOfFirst { it.trim() == "交易时间" }
        val colCategory = header.indexOfFirst { it.trim() == "交易分类" }
        val colCounterpart = header.indexOfFirst { it.trim() == "交易对方" }
        val colDesc = header.indexOfFirst { it.trim() == "商品说明" }
        val colDirection = header.indexOfFirst { it.trim() == "收/支" }
        val colAmount = header.indexOfFirst { it.trim() == "金额" }
        val colStatus = header.indexOfFirst { it.trim() == "交易状态" }

        if (colAmount < 0) {
            return ImportResult(emptyList(), listOf("未找到金额列"), 0)
        }

        val totalRows = lines.size - headerIdx - 1
        for (i in (headerIdx + 1) until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("-")) continue

            try {
                val cols = parseCsvLine(line)

                // 读取收/支方向，跳过"不计收支"
                val direction = cols.getOrNull(colDirection)?.trim() ?: ""
                if (direction == "不计收支") continue

                // 跳过交易关闭/退款
                val status = cols.getOrNull(colStatus)?.trim() ?: ""
                if (status == "交易关闭") continue

                // 金额
                val amountStr = cols.getOrNull(colAmount)?.trim() ?: continue
                val amount = amountStr.replace(",", "").toDoubleOrNull()
                if (amount == null || amount == 0.0) continue

                // 交易时间
                val timeStr = cols.getOrNull(colTime)?.trim() ?: ""
                val timestamp = parseDate(timeStr) ?: System.currentTimeMillis()

                // 收/支类型
                val type = if (direction == "收入") 1 else 0

                // 分类：先用支付宝分类映射，再用关键词智能匹配
                val alipayType = cols.getOrNull(colCategory)?.trim() ?: ""
                val desc = cols.getOrNull(colDesc)?.trim() ?: ""
                val counterpart = cols.getOrNull(colCounterpart)?.trim() ?: ""
                val categoryName = smartCategory(alipayType, desc, counterpart)

                // 备注：智能总结商品说明+交易对方
                val note = smartNote(counterpart, desc, alipayType)

                expenses.add(
                    Expense(
                        amount = amount,
                        categoryId = 0,
                        categoryName = categoryName,
                        note = note,
                        timestamp = timestamp,
                        ledgerId = ledgerId,
                        type = type
                    )
                )
            } catch (e: Exception) {
                errors.add("第${i + 1}行：${e.message}")
            }
        }

        return ImportResult(expenses, errors, totalRows)
    }

    /** 智能分类：支付宝分类 → 关键词匹配 → 默认 */
    private fun smartCategory(alipayType: String, desc: String, counterpart: String): String {
        // 1. 先用支付宝自带分类映射
        val mapped = alipayCategory[alipayType]
        if (mapped != null && mapped != "其他") return mapped

        // 2. 用商品说明+对方名关键词二次匹配
        val combined = "$desc $counterpart".lowercase()
        for ((keywords, category) in keywordCategory) {
            if (keywords.any { combined.contains(it) }) {
                return category
            }
        }

        // 3. 映射到"其他"或原始分类
        return mapped ?: "其他"
    }

    /** 智能生成备注：提取有意义的信息 */
    private fun smartNote(counterpart: String, desc: String, alipayType: String): String {
        val parts = mutableListOf<String>()

        // 交易对方（清理无意义内容）
        val cleanCounterpart = counterpart
            .replace(Regex("\\*+"), "")
            .replace("店", "")
            .trim()
        if (cleanCounterpart.isNotBlank() && cleanCounterpart != "/") {
            parts.add(cleanCounterpart)
        }

        // 商品说明（提取核心信息，去掉订单号等）
        var cleanDesc = desc
            .replace(Regex("订单编号\\d+"), "")
            .replace(Regex("商户单号\\S+"), "")
            .replace(Regex("-\\d{26}\\S*"), "")
            .replace(Regex("App-\\S+"), "")
            .replace(Regex("[【】]"), "")
            .replace("抖音电商-", "")
            .trim()

        // 如果太长就截断
        if (cleanDesc.length > 30) {
            cleanDesc = cleanDesc.substring(0, 30)
        }

        if (cleanDesc.isNotBlank()) {
            // 避免和对方名重复
            if (!cleanDesc.contains(cleanCounterpart.take(4))) {
                parts.add(cleanDesc)
            } else {
                parts.clear()
                parts.add(cleanDesc)
            }
        }

        return parts.joinToString(" · ").ifBlank { alipayType }
    }

    // ===== 通用 CSV 解析（原有逻辑）=====

    private fun importGenericCsv(lines: List<String>, ledgerId: Long): ImportResult {
        val expenses = mutableListOf<Expense>()
        val errors = mutableListOf<String>()
        var totalRows = 0

        val header = parseCsvLine(lines[0])
        val colMap = parseCsvHeader(header)

        if (colMap.amountCol < 0) {
            return ImportResult(emptyList(), listOf("未找到金额列（需要：金额/amount/money）"), 0)
        }

        for (i in 1 until lines.size) {
            totalRows++
            try {
                val cols = parseCsvLine(lines[i])
                val amountStr = cols.getOrNull(colMap.amountCol) ?: continue
                val amount = amountStr.replace(",", "").replace("¥", "").replace("￥", "").trim().toDoubleOrNull()
                if (amount == null || amount == 0.0) {
                    errors.add("第${i + 1}行：金额无效")
                    continue
                }

                val categoryName = cols.getOrNull(colMap.categoryCol)?.trim() ?: "其他"
                val subCategory = cols.getOrNull(colMap.subCategoryCol)?.trim() ?: ""
                val note = cols.getOrNull(colMap.noteCol)?.trim() ?: ""
                val dateStr = cols.getOrNull(colMap.dateCol)?.trim() ?: ""
                val typeStr = cols.getOrNull(colMap.typeCol)?.trim() ?: ""

                val timestamp = parseDate(dateStr) ?: System.currentTimeMillis()
                val type = parseType(typeStr, amount)
                val absAmount = kotlin.math.abs(amount)

                expenses.add(
                    Expense(
                        amount = absAmount,
                        categoryId = 0,
                        categoryName = categoryName.ifBlank { "其他" },
                        subCategory = subCategory,
                        note = note,
                        timestamp = timestamp,
                        ledgerId = ledgerId,
                        type = type
                    )
                )
            } catch (e: Exception) {
                errors.add("第${i + 1}行：${e.message}")
            }
        }

        return ImportResult(expenses, errors, totalRows)
    }

    // ===== XLSX 解析（纯 Java ZipInputStream + XmlPullParser）=====

    private fun importXlsx(context: Context, uri: Uri, ledgerId: Long): ImportResult {
        val expenses = mutableListOf<Expense>()
        val errors = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val sharedStrings = mutableListOf<String>()
                var sheetData: String? = null

                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "xl/sharedStrings.xml" -> {
                                sharedStrings.addAll(parseSharedStrings(zip.bufferedReader().readText()))
                            }
                            entry.name == "xl/worksheets/sheet1.xml" -> {
                                sheetData = zip.bufferedReader().readText()
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                if (sheetData == null) {
                    return ImportResult(emptyList(), listOf("无法读取工作表数据"), 0)
                }

                val rows = parseSheet(sheetData!!, sharedStrings)
                if (rows.isEmpty()) {
                    return ImportResult(emptyList(), listOf("空文件"), 0)
                }

                val header = rows[0]
                val colMap = parseCsvHeader(header)

                if (colMap.amountCol < 0) {
                    return ImportResult(emptyList(), listOf("未找到金额列（需要：金额/amount/money）"), 0)
                }

                val totalRows = rows.size - 1
                for (i in 1 until rows.size) {
                    val cols = rows[i]
                    try {
                        val amountStr = cols.getOrNull(colMap.amountCol) ?: continue
                        val amount = amountStr.replace(",", "").replace("¥", "").replace("￥", "").trim().toDoubleOrNull()
                        if (amount == null || amount == 0.0) {
                            errors.add("第${i + 1}行：金额无效")
                            continue
                        }

                        val categoryName = cols.getOrNull(colMap.categoryCol)?.trim() ?: "其他"
                        val subCategory = cols.getOrNull(colMap.subCategoryCol)?.trim() ?: ""
                        val note = cols.getOrNull(colMap.noteCol)?.trim() ?: ""
                        val dateStr = cols.getOrNull(colMap.dateCol)?.trim() ?: ""
                        val typeStr = cols.getOrNull(colMap.typeCol)?.trim() ?: ""

                        val timestamp = parseDate(dateStr) ?: System.currentTimeMillis()
                        val type = parseType(typeStr, amount)
                        val absAmount = kotlin.math.abs(amount)

                        expenses.add(
                            Expense(
                                amount = absAmount,
                                categoryId = 0,
                                categoryName = categoryName.ifBlank { "其他" },
                                subCategory = subCategory,
                                note = note,
                                timestamp = timestamp,
                                ledgerId = ledgerId,
                                type = type
                            )
                        )
                    } catch (e: Exception) {
                        errors.add("第${i + 1}行：${e.message}")
                    }
                }

                return ImportResult(expenses, errors, totalRows)
            }
        } catch (e: Exception) {
            errors.add("读取Excel失败: ${e.message}")
        }

        return ImportResult(expenses, errors, 0)
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val strings = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())

            var inT = false
            val sb = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "t") {
                            inT = true
                            sb.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) sb.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") {
                            inT = false
                        } else if (parser.name == "si") {
                            strings.add(sb.toString())
                            sb.clear()
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return strings
    }

    private fun parseSheet(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())

            var currentRow = mutableListOf<String>()
            var cellType = ""
            var cellValue = ""
            var inV = false
            var currentColIndex = -1

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> {
                                currentRow = mutableListOf()
                                currentColIndex = -1
                            }
                            "c" -> {
                                cellType = parser.getAttributeValue(null, "t") ?: ""
                                val ref = parser.getAttributeValue(null, "r") ?: ""
                                val colStr = ref.replace(Regex("[0-9]"), "")
                                val targetCol = colLetterToIndex(colStr)
                                while (currentRow.size < targetCol) {
                                    currentRow.add("")
                                }
                                currentColIndex = targetCol
                                cellValue = ""
                            }
                            "v" -> inV = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV) cellValue = parser.text
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "v" -> inV = false
                            "c" -> {
                                val value = when (cellType) {
                                    "s" -> {
                                        val idx = cellValue.toIntOrNull() ?: -1
                                        if (idx in sharedStrings.indices) sharedStrings[idx] else cellValue
                                    }
                                    else -> cellValue
                                }
                                if (currentColIndex >= 0) {
                                    while (currentRow.size <= currentColIndex) currentRow.add("")
                                    currentRow[currentColIndex] = value
                                }
                            }
                            "row" -> {
                                if (currentRow.isNotEmpty()) rows.add(currentRow)
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return rows
    }

    private fun colLetterToIndex(col: String): Int {
        var result = 0
        for (ch in col.uppercase()) {
            result = result * 26 + (ch - 'A' + 1)
        }
        return result - 1
    }

    // ===== 公共工具 =====

    private data class ColumnMap(
        val amountCol: Int = -1,
        val categoryCol: Int = -1,
        val subCategoryCol: Int = -1,
        val noteCol: Int = -1,
        val dateCol: Int = -1,
        val typeCol: Int = -1
    )

    private fun parseCsvHeader(headers: List<String>): ColumnMap {
        var amount = -1; var category = -1; var subCategory = -1; var note = -1; var date = -1; var type = -1
        headers.forEachIndexed { i, h ->
            val cell = h.trim().lowercase()
            when {
                cell in listOf("金额", "amount", "money", "数额") -> amount = i
                cell in listOf("分类", "category", "类别", "类型", "一级分类") -> category = i
                cell in listOf("二级分类", "subcategory", "子分类", "标签") -> subCategory = i
                cell in listOf("备注", "note", "说明", "描述", "摘要") -> note = i
                cell in listOf("日期", "date", "时间", "time", "交易时间") -> date = i
                cell in listOf("收支", "type", "收支类型", "方向") -> type = i
            }
        }
        return ColumnMap(amount, category, subCategory, note, date, type)
    }

    private fun parseDate(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        for (fmt in dateFormats) {
            try {
                return fmt.parse(dateStr)?.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseType(typeStr: String, amount: Double): Int {
        return when {
            typeStr.contains("收入") || typeStr.lowercase().contains("income") -> 1
            typeStr.contains("支出") || typeStr.lowercase().contains("expense") -> 0
            amount < 0 -> 0
            else -> 0
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}

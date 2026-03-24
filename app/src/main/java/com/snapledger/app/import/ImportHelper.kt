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

    // ===== XLSX 解析（纯 Java ZipInputStream + XmlPullParser）=====

    private fun importXlsx(context: Context, uri: Uri, ledgerId: Long): ImportResult {
        val expenses = mutableListOf<Expense>()
        val errors = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val sharedStrings = mutableListOf<String>()
                var sheetData: String? = null

                // xlsx 是 zip 包，解压读取 shared strings 和 sheet1
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

                // 第一行为表头
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
                                // 解析列索引 (A1 → 0, B1 → 1, etc.)
                                val colStr = ref.replace(Regex("[0-9]"), "")
                                val targetCol = colLetterToIndex(colStr)
                                // 填充跳过的空列
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

    // ===== CSV 解析 =====

    private fun importCsv(context: Context, uri: Uri, ledgerId: Long): ImportResult {
        val expenses = mutableListOf<Expense>()
        val errors = mutableListOf<String>()
        var totalRows = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val reader = BufferedReader(InputStreamReader(input, "UTF-8"))
                val lines = reader.readLines()
                if (lines.isEmpty()) return ImportResult(emptyList(), listOf("空文件"), 0)

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
            }
        } catch (e: Exception) {
            errors.add("读取CSV失败: ${e.message}")
        }

        return ImportResult(expenses, errors, totalRows)
    }

    // ===== 公共工具 =====

    private data class ColumnMap(
        val amountCol: Int = -1,
        val categoryCol: Int = -1,
        val noteCol: Int = -1,
        val dateCol: Int = -1,
        val typeCol: Int = -1
    )

    private fun parseCsvHeader(headers: List<String>): ColumnMap {
        var amount = -1; var category = -1; var note = -1; var date = -1; var type = -1
        headers.forEachIndexed { i, h ->
            val cell = h.trim().lowercase()
            when {
                cell in listOf("金额", "amount", "money", "数额") -> amount = i
                cell in listOf("分类", "category", "类别", "类型") -> category = i
                cell in listOf("备注", "note", "说明", "描述", "摘要") -> note = i
                cell in listOf("日期", "date", "时间", "time", "交易时间") -> date = i
                cell in listOf("收支", "type", "收支类型", "方向") -> type = i
            }
        }
        return ColumnMap(amount, category, note, date, type)
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

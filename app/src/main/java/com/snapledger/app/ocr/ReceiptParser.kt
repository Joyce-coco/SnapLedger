package com.snapledger.app.ocr

/**
 * 从 OCR 识别出的文本中解析金额、分类和收入/支出类型
 * 支持：普通小票、微信账单、支付宝账单
 */
object ReceiptParser {

    // ========== 通用金额匹配 ==========
    private val amountPatterns = listOf(
        Regex("""(?:总[计价额]|合[计价]|应[付收]|实[付收]|金额|总额|小计)\s*[:：]?\s*[¥￥]?\s*(\d+\.?\d{0,2})"""),
        Regex("""[¥￥]\s*(\d+\.?\d{0,2})"""),
        Regex("""(\d+\.?\d{0,2})\s*元"""),
        Regex("""(?:^|\s)(\d+\.\d{2})(?:\s|$)""", RegexOption.MULTILINE),
    )

    // ========== 微信账单匹配 ==========
    private val wechatAmountPatterns = listOf(
        // 微信支付凭证: "支付金额 ¥XX.XX"
        Regex("""支付金额\s*[¥￥]?\s*(\d+\.?\d{0,2})"""),
        // 微信转账: "转账金额"
        Regex("""转账金额\s*[¥￥]?\s*(\d+\.?\d{0,2})"""),
        // 微信红包
        Regex("""(?:红包金额|发出红包)\s*[¥￥]?\s*(\d+\.?\d{0,2})"""),
        // 微信账单截图常见: "-¥XX.XX" 或 "+¥XX.XX"
        Regex("""[+-]\s*[¥￥]\s*(\d+\.?\d{0,2})"""),
        // 微信零钱明细
        Regex("""(?:零钱|收款|付款)\s*[¥￥]?\s*(\d+\.?\d{0,2})"""),
    )

    // ========== 支付宝账单匹配 ==========
    private val alipayAmountPatterns = listOf(
        // 支付宝交易详情
        Regex("""(?:付款金额|收款金额|交易金额)\s*[¥￥]?\s*(\d+\.?\d{0,2})"""),
        // 支付宝账单: "支出 -XX.XX" / "收入 +XX.XX"
        Regex("""支出\s*-?\s*(\d+\.?\d{0,2})"""),
        Regex("""收入\s*\+?\s*(\d+\.?\d{0,2})"""),
        // 支付宝转账
        Regex("""(?:转账|代付)\s*[¥￥]?\s*(\d+\.?\d{0,2})"""),
    )

    // ========== 收入关键词 ==========
    private val incomeKeywords = listOf(
        "收入", "收款", "转入", "退款", "红包", "奖励", "到账",
        "已收款", "收钱", "工资", "报销", "返现"
    )

    // ========== 支出关键词 ==========
    private val expenseKeywords = listOf(
        "支出", "付款", "消费", "转出", "支付", "扣款",
        "已支付", "付钱", "购买", "充值", "缴费"
    )

    // ========== 平台识别关键词 ==========
    private val wechatKeywords = listOf("微信", "WeChat", "微信支付", "零钱", "微信红包")
    private val alipayKeywords = listOf("支付宝", "Alipay", "花呗", "余额宝", "蚂蚁")

    // ========== 分类关键词映射 ==========
    private val categoryKeywords = mapOf(
        "餐饮" to listOf("餐厅", "饭店", "食堂", "外卖", "美团", "饿了么", "肯德基", "麦当劳", "星巴克", "奶茶", "咖啡", "火锅", "烧烤", "面包", "蛋糕", "早餐", "午餐", "晚餐", "夜宵", "小吃"),
        "交通" to listOf("打车", "滴滴", "出租", "地铁", "公交", "加油", "停车", "高速", "高铁", "火车", "机票", "航空", "顺风车", "共享单车"),
        "购物" to listOf("超市", "商场", "淘宝", "京东", "拼多多", "服装", "鞋", "百货", "天猫", "唯品会", "苏宁", "网购"),
        "娱乐" to listOf("电影", "KTV", "游戏", "健身", "运动", "门票", "景区", "旅游", "酒店", "民宿"),
        "居住" to listOf("房租", "水费", "电费", "燃气", "物业", "宽带", "暖气", "房贷", "装修"),
        "医疗" to listOf("药房", "医院", "诊所", "药店", "门诊", "体检", "挂号"),
        "教育" to listOf("书店", "培训", "课程", "学费", "教材", "考试"),
        "通讯" to listOf("话费", "流量", "充值", "移动", "联通", "电信"),
        "日用" to listOf("便利店", "日用品", "洗衣", "理发", "快递"),
    )

    data class ParseResult(
        val amount: Double?,
        val category: String?,
        val rawText: String,
        val isIncome: Boolean = false
    )

    fun parse(ocrText: String): ParseResult {
        val platform = detectPlatform(ocrText)
        val amount = extractAmount(ocrText, platform)
        val category = guessCategory(ocrText)
        val isIncome = detectIsIncome(ocrText)
        return ParseResult(amount, category, ocrText, isIncome)
    }

    private enum class Platform { WECHAT, ALIPAY, GENERAL }

    private fun detectPlatform(text: String): Platform {
        return when {
            wechatKeywords.any { text.contains(it) } -> Platform.WECHAT
            alipayKeywords.any { text.contains(it) } -> Platform.ALIPAY
            else -> Platform.GENERAL
        }
    }

    private fun extractAmount(text: String, platform: Platform): Double? {
        // 优先用平台专属模式
        val platformPatterns = when (platform) {
            Platform.WECHAT -> wechatAmountPatterns
            Platform.ALIPAY -> alipayAmountPatterns
            Platform.GENERAL -> emptyList()
        }

        // 先尝试平台专属模式
        for (pattern in platformPatterns) {
            val matches = pattern.findAll(text).toList()
            if (matches.isNotEmpty()) {
                return matches.mapNotNull { it.groupValues[1].toDoubleOrNull() }
                    .filter { it > 0 }
                    .maxOrNull()
            }
        }

        // 再用通用模式
        for (pattern in amountPatterns) {
            val matches = pattern.findAll(text).toList()
            if (matches.isNotEmpty()) {
                return matches.mapNotNull { it.groupValues[1].toDoubleOrNull() }
                    .filter { it > 0 }
                    .maxOrNull()
            }
        }
        return null
    }

    private fun detectIsIncome(text: String): Boolean {
        val incomeCount = incomeKeywords.count { text.contains(it) }
        val expenseCount = expenseKeywords.count { text.contains(it) }

        // 检查微信/支付宝特殊标记
        if (Regex("""\+\s*[¥￥]\s*\d""").containsMatchIn(text)) return true
        if (Regex("""-\s*[¥￥]\s*\d""").containsMatchIn(text)) return false

        return incomeCount > expenseCount
    }

    private fun guessCategory(text: String): String? {
        val lowerText = text.lowercase()
        var bestCategory: String? = null
        var bestCount = 0

        for ((category, keywords) in categoryKeywords) {
            val count = keywords.count { lowerText.contains(it) }
            if (count > bestCount) {
                bestCount = count
                bestCategory = category
            }
        }
        return bestCategory
    }
}

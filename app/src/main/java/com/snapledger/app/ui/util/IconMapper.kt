package com.snapledger.app.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

fun categoryIcon(iconName: String): ImageVector {
    return when (iconName) {
        "Restaurant" -> Icons.Default.Restaurant
        "DirectionsCar" -> Icons.Default.DirectionsCar
        "ShoppingBag" -> Icons.Default.ShoppingBag
        "SportsEsports" -> Icons.Default.SportsEsports
        "Home" -> Icons.Default.Home
        "LocalHospital" -> Icons.Default.LocalHospital
        "School" -> Icons.Default.School
        "PhoneAndroid" -> Icons.Default.PhoneAndroid
        "Storefront" -> Icons.Default.Storefront
        "MoreHoriz" -> Icons.Default.MoreHoriz
        "AttachMoney" -> Icons.Default.AttachMoney
        "Favorite" -> Icons.Default.Favorite
        "Star" -> Icons.Default.Star
        "ShoppingCart" -> Icons.Default.ShoppingCart
        "Flight" -> Icons.Default.Flight
        "Pets" -> Icons.Default.Pets
        "Checkroom" -> Icons.Default.Checkroom
        "FlightTakeoff" -> Icons.Default.Flight
        "Group" -> Icons.Default.Group
        "MenuBook" -> Icons.Default.MenuBook
        "Apartment" -> Icons.Default.Apartment
        "Work" -> Icons.Default.Work
        else -> Icons.Default.Category
    }
}

// 可供用户新建分类时选择的图标列表
val availableIcons = listOf(
    "Restaurant" to "餐饮",
    "DirectionsCar" to "交通",
    "ShoppingBag" to "购物",
    "SportsEsports" to "娱乐",
    "Home" to "居住",
    "LocalHospital" to "医疗",
    "School" to "教育",
    "PhoneAndroid" to "通讯",
    "Storefront" to "日用",
    "AttachMoney" to "金融",
    "Favorite" to "生活",
    "Star" to "收藏",
    "ShoppingCart" to "购物车",
    "Flight" to "旅行",
    "Pets" to "宠物",
    "Checkroom" to "服饰",
    "MoreHoriz" to "其他",
    "FlightTakeoff" to "旅游",
    "Group" to "社交",
    "MenuBook" to "学习",
    "Apartment" to "房租",
    "Work" to "副业",
)

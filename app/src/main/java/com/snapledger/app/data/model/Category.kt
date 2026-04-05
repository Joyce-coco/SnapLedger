package com.snapledger.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,       // Material icon name
    val color: Long,        // ARGB color value
    val isDefault: Boolean = false
)

object DefaultCategories {
    fun getAll(): List<Category> = listOf(
        Category(name = "餐饮", icon = "Restaurant", color = 0xFFFF6B6B, isDefault = true),
        Category(name = "交通", icon = "DirectionsCar", color = 0xFF4ECDC4, isDefault = true),
        Category(name = "购物", icon = "ShoppingBag", color = 0xFFFFE66D, isDefault = true),
        Category(name = "娱乐", icon = "SportsEsports", color = 0xFFA78BFA, isDefault = true),
        Category(name = "居住", icon = "Home", color = 0xFF60A5FA, isDefault = true),
        Category(name = "医疗", icon = "LocalHospital", color = 0xFFF472B6, isDefault = true),
        Category(name = "教育", icon = "School", color = 0xFF34D399, isDefault = true),
        Category(name = "通讯", icon = "PhoneAndroid", color = 0xFFFBBF24, isDefault = true),
        Category(name = "日用", icon = "Storefront", color = 0xFF818CF8, isDefault = true),
        Category(name = "其他", icon = "MoreHoriz", color = 0xFF9CA3AF, isDefault = true),
        Category(name = "宠物", icon = "Pets", color = 0xFFFFB347, isDefault = true),
        Category(name = "恋爱", icon = "Favorite", color = 0xFFFF69B4, isDefault = true),
        Category(name = "旅游", icon = "FlightTakeoff", color = 0xFF00CED1, isDefault = true),
        Category(name = "社交", icon = "Group", color = 0xFFDDA0DD, isDefault = true),
        Category(name = "学习", icon = "MenuBook", color = 0xFF87CEEB, isDefault = true),
        Category(name = "房租", icon = "Apartment", color = 0xFFCD853F, isDefault = true),
        Category(name = "副业", icon = "Work", color = 0xFF20B2AA, isDefault = true),
    )
}

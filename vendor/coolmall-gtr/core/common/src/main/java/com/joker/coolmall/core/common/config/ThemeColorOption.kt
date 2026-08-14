package com.joker.coolmall.core.common.config

/**
 * 主题颜色选项
 *
 * 仅保存颜色值与存储键，UI 层自行决定展示名称
 *
 * @property storageValue 存储到本地的字符串
 * @property colorHex 颜色值（0xAARRGGBB）
 * @author Joker.X
 */
enum class ThemeColorOption(
    val storageValue: String,
    val colorHex: Long
) {
    /** GTR primary — only brand default (see brand-tokens.json). */
    DEFAULT("default", 0xFFC8102E),
    PURPLE("purple", 0xFF1E2430),
    GREEN("green", 0xFF0B6E4F),
    ORANGE("orange", 0xFFB45309),
    RED("red", 0xFFC8102E);

    companion object {
        /**
         * 将存储值转换为枚举
         *
         * @param value 存储的字符串
         * @return 匹配的主题颜色，默认返回 DEFAULT
         */
        fun fromStorage(value: String?): ThemeColorOption {
            return entries.firstOrNull { it.storageValue == value } ?: DEFAULT
        }
    }
}

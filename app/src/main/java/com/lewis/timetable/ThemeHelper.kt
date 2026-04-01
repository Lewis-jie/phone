package com.lewis.timetable

import android.content.Context
import android.content.SharedPreferences

object ThemeHelper {

    private const val PREFS_NAME  = "app_prefs"
    private const val KEY_THEME   = "theme"
    private const val KEY_CUSTOM  = "custom_color"

    const val THEME_BLUE   = "blue"
    const val THEME_GREEN  = "green"
    const val THEME_PURPLE = "purple"
    const val THEME_RED    = "red"
    const val THEME_TEAL   = "teal"
    const val THEME_BROWN  = "brown"
    const val THEME_CUSTOM = "custom"

    // 预设主题色（与 CategoryFragment 里的色块一一对应）
    private val PRESET_COLORS = mapOf(
        THEME_BLUE   to 0xFF5B8DB8.toInt(),
        THEME_GREEN  to 0xFF7B9E87.toInt(),
        THEME_PURPLE to 0xFF9B8EA8.toInt(),
        THEME_RED    to 0xFFB87878.toInt(),
        THEME_TEAL   to 0xFF8BA5A0.toInt(),
        THEME_BROWN  to 0xFFAA9070.toInt(),
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 唯一对外读色接口。
     * 预设主题直接返回 map 里的颜色，自定义返回存储的颜色，读不到则返回蓝色。
     * 所有需要主题色的地方统一调用此方法，不再读 ?attr/colorPrimary。
     */
    fun getPrimaryColor(context: Context): Int {
        val p = prefs(context)
        val theme = p.getString(KEY_THEME, THEME_BLUE)
        return if (theme == THEME_CUSTOM) {
            p.getInt(KEY_CUSTOM, PRESET_COLORS.getValue(THEME_BLUE))
        } else {
            PRESET_COLORS[theme] ?: PRESET_COLORS.getValue(THEME_BLUE)
        }
    }

    /**
     * 选择预设主题时调用（CategoryFragment 色块点击）。
     */
    fun savePresetTheme(context: Context, themeKey: String) {
        prefs(context).edit().putString(KEY_THEME, themeKey).apply()
    }

    /**
     * 选择自定义颜色时调用（颜色对话框确定）。
     */
    fun saveCustomColor(context: Context, color: Int) {
        prefs(context).edit()
            .putString(KEY_THEME, THEME_CUSTOM)
            .putInt(KEY_CUSTOM, color)
            .apply()
    }

    /**
     * 在 Activity.onCreate 的 setContentView 之前调用，
     * 仅用于给系统组件（如 CheckBox 勾选色）提供基础主题结构，
     * 不依赖它的 colorPrimary 值，颜色全部由 getPrimaryColor() 提供。
     */
    fun applyTheme(context: Context) {
        val theme = prefs(context).getString(KEY_THEME, THEME_BLUE)
        (context as? android.app.Activity)?.setTheme(
            // 自定义颜色复用蓝色主题结构即可，代码层面颜色已覆盖
            if (theme == THEME_CUSTOM) R.style.Theme_MyApp_Blue
            else getThemeRes(theme)
        )
    }

    fun getThemeRes(theme: String?): Int = when (theme) {
        THEME_GREEN  -> R.style.Theme_MyApp_Green
        THEME_PURPLE -> R.style.Theme_MyApp_Purple
        THEME_RED    -> R.style.Theme_MyApp_Red
        THEME_TEAL   -> R.style.Theme_MyApp_Teal
        THEME_BROWN  -> R.style.Theme_MyApp_Brown
        else         -> R.style.Theme_MyApp_Blue
    }
}
package com.lewis.timetable

object TagColorManager {

    // 标签专用色池：莫兰迪 + 自然色系，与六个预设主题色不重复
    // 预设主题色：#5B8DB8 #7B9E87 #9B8EA8 #B87878 #8BA5A0 #AA9070
    private val COLOR_POOL = listOf(
        0xFFE07B7B.toInt(),  // 玫瑰红
        0xFFD4956A.toInt(),  // 赭橙
        0xFFD4B96A.toInt(),  // 暖黄
        0xFF9DBF7A.toInt(),  // 草绿
        0xFF6AAF9D.toInt(),  // 薄荷绿
        0xFF6A9FBF.toInt(),  // 天青蓝
        0xFF7A8FBF.toInt(),  // 矢车菊蓝
        0xFF9A7ABF.toInt(),  // 薰衣草紫
        0xFFBF7AA0.toInt(),  // 藕粉
        0xFF8FA07A.toInt(),  // 橄榄绿
        0xFFA07A8F.toInt(),  // 灰玫瑰
        0xFF7AABBF.toInt(),  // 雾霾蓝
        0xFFBFAA7A.toInt(),  // 沙金
        0xFF7ABF9A.toInt(),  // 碧玉绿
        0xFFBF8F7A.toInt(),  // 陶土橙
        0xFF8ABFBF.toInt(),  // 冰蓝
        0xFFB07AB0.toInt(),  // 丁香紫
        0xFF7AB08A.toInt(),  // 苔绿
        0xFFB0907A.toInt(),  // 焦糖棕
        0xFF7A90B0.toInt(),  // 灰蓝
    )

    // 无标签任务专用色：低调的蓝灰
    val NO_TAG_COLOR = 0xFFB0BEC5.toInt()

    /**
     * 根据已用颜色列表，为新标签分配一个尽量不重复的颜色。
     * 优先选 COLOR_POOL 中未被使用的颜色；全部用完后轮回。
     */
    fun assignColor(usedColors: List<Int>): Int =
        COLOR_POOL.firstOrNull { it !in usedColors }
            ?: COLOR_POOL[usedColors.size % COLOR_POOL.size]

}
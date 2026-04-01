package com.lewis.timetable

/**
 * 课程颜色池（独立于标签颜色）。
 * 10 种颜色在色相环上均匀分布，饱和度适中，便于辨识且不刺眼。
 */
object CourseColorManager {

    val COLORS = intArrayOf(
        0xFF4A90D9.toInt(),  // 天蓝   hue≈210
        0xFF57B894.toInt(),  // 翠绿   hue≈160
        0xFFE8855A.toInt(),  // 橙色   hue≈20
        0xFF9B72C8.toInt(),  // 紫色   hue≈270
        0xFFD94F6B.toInt(),  // 玫红   hue≈345
        0xFF3FBFBF.toInt(),  // 青色   hue≈180
        0xFFD4A843.toInt(),  // 金黄   hue≈40
        0xFF6B8FD4.toInt(),  // 蓝紫   hue≈225
        0xFF82C05A.toInt(),  // 草绿   hue≈100
        0xFFD47B9B.toInt(),  // 粉红   hue≈330
    )

    /** 根据课程名返回固定颜色（哈希，同名课程颜色一致） */
    fun colorFor(courseName: String): Int {
        val idx = Math.abs(courseName.hashCode()) % COLORS.size
        return COLORS[idx]
    }

    /** 为一批解析后的课程按课程名分配颜色 */
    fun assignColors(courseNames: Collection<String>): Map<String, Int> =
        courseNames.distinct().associateWith { colorFor(it) }
}

# Project Guide

## 说明范围

本文档基于 `git ls-files` 整理，覆盖当前仓库中全部受版本控制的文件路径，并额外补充当前工作区可见但未纳入版本控制的关键本地文件/目录。

同时，本文档已吸收当前工作区中尚未提交的改动，描述以提交前的实际代码状态为准，而不是只看 `HEAD`。

## 项目概览

这是一个单模块 Android 应用，核心能力由三条主线组成：

- 任务管理：待办、重复待办、标签、星标、历史记录。
- 课程管理：课表、课程条目、时间表节次、HTML 导入。
- 综合日程：日视图、周视图、月视图，把待办和课程统一展示。

当前技术组合：

- Kotlin + Android Views/XML
- Fragment + Jetpack Navigation
- AndroidViewModel + LiveData
- Room + KSP
- SharedPreferences
- MPAndroidChart

## 关键架构决定

1. **单模块结构**
   所有业务代码集中在 `app` 模块，没有再拆 feature module。优点是迭代快、依赖简单；代价是边界主要靠类名和文件分工而不是模块隔离。

2. **继续采用 Fragment + XML，而不是 Compose**
   页面由 `MainActivity` 承载，主要交互放在多个 Fragment 中，通过 `nav_graph.xml` 导航。布局、动画、主题资源仍全部走传统 View/XML 体系。

3. **数据层统一收口到一个 Room 数据库**
   `AppDatabase` 同时管理任务、标签、课表、课程、时间表五类核心数据；DAO 分为 `TaskDao`、`TagDao`、`CourseDao`、`TimetableDao`，业务层再通过 Repository 暴露。

4. **ViewModel 手动装配 Repository，不引入 DI 框架**
   `TaskViewModel` 和 `CourseViewModel` 都直接通过 `AppDatabase.getDatabase()` 拿 DAO 再创建 Repository。项目避免了 Hilt/Koin 的复杂度，但依赖注入关系是手写的。

5. **重复任务采用“根任务 + 衍生实例”模型**
   `Task.parentTaskId` 和 `RepeatTaskHelper` 共同实现重复任务。仓库层会把未来实例按需补齐到“今天结束”为止，而不是在数据库里只保存一条 RRULE 规则。

6. **综合日程视图在 UI 层做多源聚合**
   `ScheduleFragment` 同时观察任务、重复任务、标签色、当前课表、课程节次，再在页面层合并计算日/周/月内容。这让日程渲染集中，但也使该 Fragment 成为最复杂的聚合点。

7. **轻量级应用状态放在 SharedPreferences**
   当前主题色、激活课表 ID 等短小配置不入库，而是由 `ThemeHelper`、`CourseViewModel` 读写 `app_prefs`。

8. **备份恢复直接做 JSON 快照**
   `CategoryFragment` 会把 Room 中的任务、标签、课表、时间表以及 `activeScheduleId` 一次性导出为 JSON；恢复时使用 `clearAllTables()` 再批量写回。

9. **课表导入采用“解析器注册表”扩展点**
   `CourseImportParser` 定义统一接口，`CourseImportRegistry` 注册学校专用解析器，目前已接入江西师范大学和暨南大学两个 HTML 解析器。

10. **数据库既保留迁移，也允许兜底性破坏式迁移**
    `AppDatabase` 明确实现了 `6 -> 11` 的迁移，但仍启用了 `fallbackToDestructiveMigration()`。这意味着版本跳跃或迁移缺失时，数据库可能被重建。

11. **提醒能力已留出文件位，但当前未真正接通**
    `BootReceiver`、`Reminder*` 系列文件目前基本为空文件，说明提醒架构已预留命名与位置，但尚未形成可用链路。

12. **启动性能和问题定位优先级较高**
    `MyApplication` 在 Debug 下开启 `StrictMode`，并安装全局 `CrashHandler`；`MainActivity` 与 `ScheduleFragment` 通过启动遮罩和 `reportFullyDrawn()` 控制首屏完成时机。

## 当前待提交改动

截至当前工作区，待提交改动主要集中在“任务完成反馈”和“日程页上下文增强”两条线：

- `app/src/main/java/com/lewis/timetable/TaskRepository.kt`
  新增重复任务实例回填逻辑，并用 `Mutex` 避免并发生成重复实例。
- `app/src/main/java/com/lewis/timetable/TaskViewModel.kt`
  在初始化和任务保存后同步“今日结束前”的重复实例；新增重复任务完成撤销能力。
- `app/src/main/java/com/lewis/timetable/TaskListFragment.kt`
  任务完成后显示 `Snackbar` 反馈；普通任务支持撤销完成，重复任务支持撤销并回收刚生成的下一实例。
- `app/src/main/java/com/lewis/timetable/ScheduleFragment.kt`
  为日/周/月三种日程视图新增“今天”快捷返回、上下文提示文案，以及周视图中对今天列的强化高亮。
- `app/src/main/res/layout/view_day_schedule.xml`
  新增日视图“今天”按钮和上下文提示区域。
- `app/src/main/res/layout/view_week_schedule.xml`
  新增周视图“今天”按钮和上下文提示区域。
- `app/src/main/res/layout/view_month_schedule.xml`
  新增月视图“今天”按钮和上下文提示区域。
- `app/src/main/res/values/strings.xml`
  新增“今天”“撤销”、日/周/月上下文提示，以及任务完成反馈相关文案。

## 本地但未纳管的关键项

这些项出现在当前工作区，但不在版本控制内：

| 路径 | 作用 |
| --- | --- |
| `local.properties` | Android SDK 本地路径配置。 |
| `.gradle/` | Gradle 本地缓存和中间文件。 |
| `.idea/` | Android Studio 工程级配置。 |
| `.kotlin/` | Kotlin/Gradle 本地状态。 |
| `build/` | 根目录构建产物。 |

## 文件路径与作用

### 根目录与协作文件

| 路径 | 作用 |
| --- | --- |
| `.gitattributes` | Git 文本属性配置。 |
| `.github/PULL_REQUEST_TEMPLATE.md` | Pull Request 模板。 |
| `.gitignore` | 根目录忽略规则。 |
| `CHANGELOG.md` | 最近功能与修复记录。 |
| `LICENSE` | Apache 2.0 许可证。 |
| `README.md` | 项目简介、功能、运行方式。 |
| `build.gradle.kts` | 根项目 Gradle 配置；声明 Android 插件和 KSP 插件但不直接应用。 |
| `dependencies.txt` | 依赖记录占位文件，目前为空。 |
| `gradle.properties` | Gradle 构建参数。 |
| `gradle/gradle-daemon-jvm.properties` | Gradle Daemon JVM 相关参数。 |
| `gradle/libs.versions.toml` | 版本目录，集中维护插件和依赖版本。 |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle Wrapper 二进制。 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle Wrapper 版本与分发地址配置。 |
| `gradlew` | Unix/Linux 下的 Gradle Wrapper 启动脚本。 |
| `gradlew.bat` | Windows 下的 Gradle Wrapper 启动脚本。 |
| `modules.txt` | 模块记录占位文件，目前为空。 |
| `settings.gradle.kts` | 仓库级设置；配置仓库源、项目名和 `:app` 模块。 |
| `docs/project-guide.md` | 本文档，记录路径职责与关键架构决定。 |

### app 模块配置与产物

| 路径 | 作用 |
| --- | --- |
| `app/.gitignore` | app 模块忽略规则。 |
| `app/build.gradle.kts` | app 模块构建脚本；声明 SDK 版本、构建类型以及 Room/Lifecycle/Navigation 等依赖。 |
| `app/proguard-rules.pro` | Release 混淆和收缩规则。 |
| `app/release/baselineProfiles/0/app-release.dm` | 已提交的 release baseline profile 相关产物。 |
| `app/release/baselineProfiles/1/app-release.dm` | 已提交的 release baseline profile 相关产物。 |
| `app/release/output-metadata.json` | release 输出元数据。 |

### 测试文件

| 路径 | 作用 |
| --- | --- |
| `app/src/androidTest/java/com/lewis/timetable/ExampleInstrumentedTest.kt` | Android 仪器测试示例。 |
| `app/src/test/java/com/lewis/timetable/ExampleUnitTest.kt` | JVM 单元测试示例。 |

### Android 入口与清单

| 路径 | 作用 |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | 应用清单；声明 `MyApplication`、`MainActivity` 和存储权限。 |
| `app/src/main/java/com/lewis/timetable/MyApplication.kt` | 应用入口；Debug 开启 `StrictMode`，注册全局崩溃处理器。 |
| `app/src/main/java/com/lewis/timetable/MainActivity.kt` | 唯一 Activity；承载 `NavHostFragment`、底部导航、系统栏 inset 和启动遮罩。 |
| `app/src/main/java/com/lewis/timetable/CrashHandler.kt` | 未捕获异常处理器，把崩溃堆栈写入 Downloads。 |
| `app/src/main/java/com/lewis/timetable/ThemeHelper.kt` | 主题色读写与 Activity 主题应用。 |

### 任务领域数据层

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/com/lewis/timetable/AppDatabase.kt` | Room 数据库定义、表注册、迁移和单例获取。 |
| `app/src/main/java/com/lewis/timetable/Task.kt` | 任务实体；包含重复规则、提醒时间、父任务 ID 等字段。 |
| `app/src/main/java/com/lewis/timetable/Tag.kt` | 标签实体。 |
| `app/src/main/java/com/lewis/timetable/TaskTag.kt` | 任务与标签的关联实体。 |
| `app/src/main/java/com/lewis/timetable/TaskWithTags.kt` | Room 关联查询模型，表示“任务 + 标签集合”。 |
| `app/src/main/java/com/lewis/timetable/TaskTagSummary.kt` | 任务标签名聚合摘要。 |
| `app/src/main/java/com/lewis/timetable/TaskTagColorSummary.kt` | 任务标签颜色聚合摘要。 |
| `app/src/main/java/com/lewis/timetable/TaskDao.kt` | 任务表 DAO；提供列表、按日期/周筛选、重复实例查询等。 |
| `app/src/main/java/com/lewis/timetable/TagDao.kt` | 标签及任务标签关联 DAO；负责标签查询、颜色维护、摘要查询。 |
| `app/src/main/java/com/lewis/timetable/TaskRepository.kt` | 任务仓库；封装任务 CRUD、标签绑定、重复实例回填，以及重复实例生成的并发保护。 |
| `app/src/main/java/com/lewis/timetable/TaskViewModel.kt` | 任务域 ViewModel；供 UI 订阅任务、标签、星标、重复任务状态，并负责今日范围内的重复实例同步与完成撤销。 |
| `app/src/main/java/com/lewis/timetable/RepeatTaskHelper.kt` | 重复规则计算工具；计算下一次开始时间和区间内发生次数。 |
| `app/src/main/java/com/lewis/timetable/TagColorManager.kt` | 标签颜色分配器；给标签和无标签任务提供稳定颜色。 |

### 课表与时间表数据层

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/com/lewis/timetable/CourseSchedule.kt` | 课表实体；包含学期开始时间、总周数、绑定时间表 ID。 |
| `app/src/main/java/com/lewis/timetable/CourseLesson.kt` | 课程条目实体；提供节次时间推导、周次位图和节次辅助方法。 |
| `app/src/main/java/com/lewis/timetable/Timetable.kt` | 时间表实体；描述一套节次模板。 |
| `app/src/main/java/com/lewis/timetable/TimetablePeriod.kt` | 时间表中的单个节次定义。 |
| `app/src/main/java/com/lewis/timetable/CourseDao.kt` | 课表和课程 DAO。 |
| `app/src/main/java/com/lewis/timetable/TimetableDao.kt` | 时间表和节次 DAO。 |
| `app/src/main/java/com/lewis/timetable/CourseRepository.kt` | 课表仓库；封装课表、课程条目的增删改查。 |
| `app/src/main/java/com/lewis/timetable/TimetableRepository.kt` | 时间表仓库；封装时间表和节次的维护。 |
| `app/src/main/java/com/lewis/timetable/CourseViewModel.kt` | 课表域 ViewModel；维护当前激活课表和绑定时间表。 |
| `app/src/main/java/com/lewis/timetable/CourseColorManager.kt` | 课程颜色分配器，同名课程保持稳定颜色。 |

### 课表导入链路

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/com/lewis/timetable/CourseImportModels.kt` | 导入结果 DTO、冲突模型和统一解析器接口。 |
| `app/src/main/java/com/lewis/timetable/CourseImportRegistry.kt` | 已注册课表 HTML 解析器列表。 |
| `app/src/main/java/com/lewis/timetable/JxnuHtmlParser.kt` | 江西师范大学课表 HTML 解析器。 |
| `app/src/main/java/com/lewis/timetable/JnuHtmlParser.kt` | 暨南大学课表 HTML 解析器。 |

### 主要页面与业务 Fragment

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/com/lewis/timetable/TaskListFragment.kt` | 待办首页；展示分组任务列表、标签筛选、删除和完成逻辑，并在完成任务后给出 Snackbar 反馈与撤销。 |
| `app/src/main/java/com/lewis/timetable/AddTaskFragment.kt` | 新建/编辑任务页；处理时间、重复、标签和提醒输入。 |
| `app/src/main/java/com/lewis/timetable/HistoryFragment.kt` | 历史待办页；按月份筛选已完成的旧任务并支持恢复/删除。 |
| `app/src/main/java/com/lewis/timetable/ScheduleFragment.kt` | 综合日程页；聚合任务、重复任务、课程和时间表，渲染日/周/月视图，并提供“今天”快捷返回与上下文提示。 |
| `app/src/main/java/com/lewis/timetable/CategoryFragment.kt` | 分类/设置页；展示统计图、主题设置、数据备份恢复和课表入口。 |
| `app/src/main/java/com/lewis/timetable/CourseFragment.kt` | 课表周视图页面；显示当前课表的一周课程栅格。 |
| `app/src/main/java/com/lewis/timetable/ImportCourseFragment.kt` | 课表导入页；支持空白课表、新建/覆盖导入、冲突选择。 |
| `app/src/main/java/com/lewis/timetable/CourseSettingsFragment.kt` | 课表设置页；配置开学周一、总周数、跳转课程总览和时间表编辑。 |
| `app/src/main/java/com/lewis/timetable/CourseOverviewFragment.kt` | 课程总览页；按课程维度查看并跳转到编辑。 |
| `app/src/main/java/com/lewis/timetable/CourseLessonEditFragment.kt` | 课程编辑页；按多时段、多周次规则编辑一门课。 |
| `app/src/main/java/com/lewis/timetable/TimetableEditorFragment.kt` | 时间表编辑页；维护节次起止时间和统一/独立时长。 |

### 列表适配器与 UI 组件

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/com/lewis/timetable/TaskAdapter.kt` | 待办列表适配器；支持日期头、任务行、标签色和星标状态。 |
| `app/src/main/java/com/lewis/timetable/HistoryAdapter.kt` | 历史待办适配器。 |
| `app/src/main/java/com/lewis/timetable/MonthDayAdapter.kt` | 月视图网格适配器；为每个日期格子渲染任务摘要状态。 |
| `app/src/main/java/com/lewis/timetable/AccessibleTouchFrameLayout.kt` | 对 `performClick()` 做补足的可点击 FrameLayout。 |
| `app/src/main/java/com/lewis/timetable/GestureGridView.kt` | 支持外挂手势识别器的 GridView。 |
| `app/src/main/java/com/lewis/timetable/SwipeFrameLayout.kt` | 在不拦截子 View 点击的前提下识别左右/下滑手势。 |
| `app/src/main/java/com/lewis/timetable/RadialClockFaceView.kt` | 自绘径向时钟盘，供时间选择器使用。 |
| `app/src/main/java/com/lewis/timetable/TimePickerDialog.kt` | 底部时间选择对话框；支持时钟盘和键盘两种输入模式。 |
| `app/src/main/java/com/lewis/timetable/ImeInsetsHelper.kt` | 软键盘 inset 辅助工具，确保输入框可见。 |

### 预留/占位文件

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/com/lewis/timetable/BootReceiver.kt` | 预留启动广播接收器，当前为空。 |
| `app/src/main/java/com/lewis/timetable/ReminderAlarmActivity.kt` | 预留提醒相关 Activity，当前为空。 |
| `app/src/main/java/com/lewis/timetable/ReminderNotifier.kt` | 预留提醒通知构建器，当前为空。 |
| `app/src/main/java/com/lewis/timetable/ReminderReceiver.kt` | 预留提醒广播接收器，当前为空。 |
| `app/src/main/java/com/lewis/timetable/ReminderScheduler.kt` | 预留提醒调度器，当前为空。 |
| `app/src/main/java/com/lewis/timetable/ReminderSettingsHelper.kt` | 预留提醒设置工具，当前为空。 |
| `app/src/main/java/com/lewis/timetable/ReminderWorker.kt` | 预留提醒后台任务，当前为空。 |

### 动画资源

| 路径 | 作用 |
| --- | --- |
| `app/src/main/res/anim/fade_stay.xml` | 过渡动画中的淡入淡出保持效果。 |
| `app/src/main/res/anim/slide_down.xml` | 页面/面板下滑退出动画。 |
| `app/src/main/res/anim/slide_up.xml` | 页面/面板上滑进入动画。 |

### Drawable 资源

| 路径 | 作用 |
| --- | --- |
| `app/src/main/res/drawable/bg_schedule_task.xml` | 日程任务卡片背景。 |
| `app/src/main/res/drawable/bg_tab_selected.xml` | 选中标签/按钮背景。 |
| `app/src/main/res/drawable/bg_tab_unselected.xml` | 未选中标签/按钮背景。 |
| `app/src/main/res/drawable/bg_task_item_normal.xml` | 待办卡片默认背景。 |
| `app/src/main/res/drawable/bg_task_item_pressed.xml` | 待办卡片按压态背景。 |
| `app/src/main/res/drawable/bg_time_picker_floating.xml` | 时间选择器浮层背景。 |
| `app/src/main/res/drawable/bg_time_picker_input.xml` | 时间输入框背景。 |
| `app/src/main/res/drawable/bg_time_picker_row.xml` | 时间选择器行容器背景。 |
| `app/src/main/res/drawable/ic_launcher_background.xml` | 启动图标背景层。 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 启动图标前景层。 |
| `app/src/main/res/drawable/ic_star_empty.xml` | 空星标图标。 |
| `app/src/main/res/drawable/ic_star_filled.xml` | 实心星标图标。 |
| `app/src/main/res/drawable/ic_time_clock.xml` | 时间选择器时钟模式图标。 |
| `app/src/main/res/drawable/ic_time_keyboard.xml` | 时间选择器键盘模式图标。 |
| `app/src/main/res/drawable/selector_task_item.xml` | 待办卡片状态选择器。 |

### 布局资源

| 路径 | 作用 |
| --- | --- |
| `app/src/main/res/layout/activity_main.xml` | 主 Activity 布局，包含导航宿主和底部栏。 |
| `app/src/main/res/layout/dialog_time_picker_clock.xml` | 时间选择对话框布局。 |
| `app/src/main/res/layout/fragment_add_task.xml` | 添加/编辑任务页面布局。 |
| `app/src/main/res/layout/fragment_category.xml` | 分类/设置页面布局。 |
| `app/src/main/res/layout/fragment_course.xml` | 课表主页面布局。 |
| `app/src/main/res/layout/fragment_course_lesson_edit.xml` | 课程编辑页面布局。 |
| `app/src/main/res/layout/fragment_course_overview.xml` | 课程总览页面布局。 |
| `app/src/main/res/layout/fragment_course_settings.xml` | 课表设置页面布局。 |
| `app/src/main/res/layout/fragment_history.xml` | 历史待办页面布局。 |
| `app/src/main/res/layout/fragment_import_course.xml` | 课表导入页面布局。 |
| `app/src/main/res/layout/fragment_schedule.xml` | 综合日程页面容器布局。 |
| `app/src/main/res/layout/fragment_task_list.xml` | 待办列表页面布局。 |
| `app/src/main/res/layout/fragment_timetable_editor.xml` | 时间表编辑页面布局。 |
| `app/src/main/res/layout/item_course_overview.xml` | 课程总览列表项布局。 |
| `app/src/main/res/layout/item_history_task.xml` | 历史任务列表项布局。 |
| `app/src/main/res/layout/item_schedule_task.xml` | 日程时间线中的任务卡片布局。 |
| `app/src/main/res/layout/item_task.xml` | 待办列表项布局。 |
| `app/src/main/res/layout/item_task_date_header.xml` | 待办按日期分组的标题布局。 |
| `app/src/main/res/layout/item_timeline_hour.xml` | 日视图时间线中的小时区块布局。 |
| `app/src/main/res/layout/view_day_schedule.xml` | 日程页的日视图布局，包含标题栏、“今天”按钮和日上下文提示区。 |
| `app/src/main/res/layout/view_month_schedule.xml` | 日程页的月视图布局，包含标题栏、“今天”按钮和月上下文提示区。 |
| `app/src/main/res/layout/view_week_schedule.xml` | 日程页的周视图布局，包含标题栏、“今天”按钮和周上下文提示区。 |

### 启动图标资源

| 路径 | 作用 |
| --- | --- |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | 自适应启动图标定义。 |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | 自适应圆形启动图标定义。 |
| `app/src/main/res/mipmap-hdpi/ic_launcher.webp` | hdpi 启动图标。 |
| `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp` | hdpi 圆形启动图标。 |
| `app/src/main/res/mipmap-mdpi/ic_launcher.webp` | mdpi 启动图标。 |
| `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp` | mdpi 圆形启动图标。 |
| `app/src/main/res/mipmap-xhdpi/ic_launcher.webp` | xhdpi 启动图标。 |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp` | xhdpi 圆形启动图标。 |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp` | xxhdpi 启动图标。 |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp` | xxhdpi 圆形启动图标。 |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` | xxxhdpi 启动图标。 |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp` | xxxhdpi 圆形启动图标。 |

### 导航与主题资源

| 路径 | 作用 |
| --- | --- |
| `app/src/main/res/navigation/nav_graph.xml` | Fragment 导航图和页面跳转关系。 |
| `app/src/main/res/values-night/themes.xml` | 夜间主题资源。 |
| `app/src/main/res/values/colors.xml` | 通用颜色资源。 |
| `app/src/main/res/values/launch_colors.xml` | 启动页相关颜色资源。 |
| `app/src/main/res/values/strings.xml` | 应用字符串资源，包括通用操作文案、日程上下文提示和任务完成反馈。 |
| `app/src/main/res/values/themes.xml` | 默认主题与多套预设主题定义。 |

### 备份与数据提取规则

| 路径 | 作用 |
| --- | --- |
| `app/src/main/res/xml/backup_rules.xml` | Android 自动备份规则。 |
| `app/src/main/res/xml/data_extraction_rules.xml` | Android 数据提取规则。 |

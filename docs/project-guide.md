# Project Guide

## 文档目的

这份文档面向维护者，重点说明当前项目的架构分层、关键链路、核心文件和提醒系统的实现边界。

如果只想快速了解产品功能，请先看 [README.md](../README.md)。  
如果想看按时间整理的变更记录，请看 [CHANGELOG.md](../CHANGELOG.md)。

## 项目概览

这是一个单模块 Android 应用，核心能力由四条主线组成：

- 待办管理：普通待办、重复待办、标签、星标、历史归档
- 综合日程：日、周、月视图，把课程与待办统一展示
- 课表管理：课表、课程、时间表节次、HTML 导入
- 本地提醒：待办提醒、课程提醒、开机恢复、补发与通知投递

当前技术组合：

- Kotlin
- Android Views + XML
- Fragment + Jetpack Navigation
- AndroidViewModel + LiveData
- Room
- SharedPreferences
- WorkManager
- MPAndroidChart

## 目录速查

```text
MyApp/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/java/com/lewis/timetable/
│   └── src/main/res/
├── docs/
│   └── project-guide.md
├── CHANGELOG.md
└── README.md
```

## 架构分层

### 入口层
- [`MyApplication.kt`](../app/src/main/java/com/lewis/timetable/MyApplication.kt)
  负责应用级初始化，会创建通知渠道，并在应用恢复后补调度提醒。
- [`MainActivity.kt`](../app/src/main/java/com/lewis/timetable/MainActivity.kt)
  唯一 Activity，承载 `NavHostFragment`、底部导航、首屏 ready 控制和权限请求。

### 数据层
- [`AppDatabase.kt`](../app/src/main/java/com/lewis/timetable/AppDatabase.kt)
  集中管理待办、标签、课表、课程和时间表相关表。
- [`TaskDao.kt`](../app/src/main/java/com/lewis/timetable/TaskDao.kt)
  提供待办、重复实例、历史记录和提醒恢复所需查询。
- [`CourseDao.kt`](../app/src/main/java/com/lewis/timetable/CourseDao.kt)
  管理课表与课程。
- [`TimetableDao.kt`](../app/src/main/java/com/lewis/timetable/TimetableDao.kt)
  管理时间表模板和节次。

### 仓储与业务层
- [`TaskRepository.kt`](../app/src/main/java/com/lewis/timetable/TaskRepository.kt)
  封装待办 CRUD、重复实例回填和待办提醒调度。
- [`CourseRepository.kt`](../app/src/main/java/com/lewis/timetable/CourseRepository.kt)
  封装课表和课程读写。
- [`RepeatTaskHelper.kt`](../app/src/main/java/com/lewis/timetable/RepeatTaskHelper.kt)
  负责重复规则发生时间计算。

### ViewModel 层
- [`TaskViewModel.kt`](../app/src/main/java/com/lewis/timetable/TaskViewModel.kt)
  管理待办、标签和重复任务保存逻辑。
- [`CourseViewModel.kt`](../app/src/main/java/com/lewis/timetable/CourseViewModel.kt)
  管理当前激活课表、课程列表、课表设置和课程提醒重建。

### UI 层
- [`TaskListFragment.kt`](../app/src/main/java/com/lewis/timetable/TaskListFragment.kt)
  待办首页。
- [`AddTaskFragment.kt`](../app/src/main/java/com/lewis/timetable/AddTaskFragment.kt)
  待办编辑页，负责提醒时间校验。
- [`ScheduleFragment.kt`](../app/src/main/java/com/lewis/timetable/ScheduleFragment.kt)
  日、周、月综合日程页，是课程与待办的主要聚合点。
- [`CourseFragment.kt`](../app/src/main/java/com/lewis/timetable/CourseFragment.kt)
  课表周视图。
- [`CourseSettingsFragment.kt`](../app/src/main/java/com/lewis/timetable/CourseSettingsFragment.kt)
  课表设置页，包含时间表设置、学期设置和独立“提醒时间”卡片。

## 关键业务决策

### 1. 单模块结构
所有业务都在 `app` 模块内完成，没有拆成 feature module。优点是迭代快，缺点是领域边界主要靠命名约定维持。

### 2. Fragment + XML 继续保留
当前 UI 仍基于传统 View 系统。布局、动画、主题和 Navigation 都围绕 XML 组织，没有迁移到 Compose。

### 3. Room 统一承载核心数据
待办、标签、课表、课程和时间表都在 `AppDatabase` 中。数据库迁移必须与备份恢复格式一起考虑。

### 4. 重复待办采用“根任务 + 派生实例”
从 2026-04-14 起，根任务新增 `skippedDates`，用于记录“仅删除此次”的跳过日期，避免后续同步时重新回补。

### 5. 课程提醒只跟随当前激活课表
课程提醒调度读取 `app_prefs.active_schedule_id`，只为当前激活课表注册提醒，避免多套课表同时发通知。

## 提醒链路

当前提醒系统分成“待办提醒”和“课程提醒”两条链路，但两者都遵循同样的策略：注册未来提醒、恢复开机后的提醒、在应用恢复可用时补发最近错过的提醒。

### 待办提醒
- [`ReminderScheduler.kt`](../app/src/main/java/com/lewis/timetable/ReminderScheduler.kt)
  负责待办提醒的注册、取消、批量恢复和 catch-up 补发。
- [`ReminderReceiver.kt`](../app/src/main/java/com/lewis/timetable/ReminderReceiver.kt)
  接收待办提醒广播。
- [`ReminderNotificationDispatcher.kt`](../app/src/main/java/com/lewis/timetable/ReminderNotificationDispatcher.kt)
  负责待办通知投递，当前文案为：
  - 标题：`待办「名称」`
  - 内容：`将于 XX 时间后开始`
- [`ReminderDeliveryStore.kt`](../app/src/main/java/com/lewis/timetable/ReminderDeliveryStore.kt)
  防止待办提醒重复补发。

### 课程提醒
- [`CourseReminderScheduler.kt`](../app/src/main/java/com/lewis/timetable/CourseReminderScheduler.kt)
  负责课程提醒注册、取消、激活课表扫描和 catch-up 补发。
- [`CourseReminderReceiver.kt`](../app/src/main/java/com/lewis/timetable/CourseReminderReceiver.kt)
  接收课程提醒广播。
- [`CourseReminderNotificationDispatcher.kt`](../app/src/main/java/com/lewis/timetable/CourseReminderNotificationDispatcher.kt)
  负责课程通知投递，当前标题格式为：`课程名 即将开始`
- [`CourseReminderRegistry.kt`](../app/src/main/java/com/lewis/timetable/CourseReminderRegistry.kt)
  记录已经注册的课程提醒 key，用于清理旧提醒。
- [`CourseReminderDeliveryStore.kt`](../app/src/main/java/com/lewis/timetable/CourseReminderDeliveryStore.kt)
  防止课程提醒重复补发。

### 恢复入口
- [`BootReceiver.kt`](../app/src/main/java/com/lewis/timetable/BootReceiver.kt)
  开机后恢复待办提醒和课程提醒。
- [`MyApplication.kt`](../app/src/main/java/com/lewis/timetable/MyApplication.kt)
  应用恢复后主动重建提醒。
- [`ReminderWorker.kt`](../app/src/main/java/com/lewis/timetable/ReminderWorker.kt)
  作为兜底入口，重新调度两类提醒。

## 课程提醒设置

课程提醒设置位于 [`fragment_course_settings.xml`](../app/src/main/res/layout/fragment_course_settings.xml) 和 [`CourseSettingsFragment.kt`](../app/src/main/java/com/lewis/timetable/CourseSettingsFragment.kt)：

- “时间表”与“提醒时间”分属两个独立卡片
- 提醒时间卡片包含：
  - 开关：是否启用课程提醒
  - 时间选择：上课前 `5/10/15/30/60` 分钟

提醒设置保存在 [`CourseSchedule.kt`](../app/src/main/java/com/lewis/timetable/CourseSchedule.kt)：

- `reminderEnabled`
- `reminderMinutesBefore`

数据库迁移位于 [`AppDatabase.kt`](../app/src/main/java/com/lewis/timetable/AppDatabase.kt) 的 `MIGRATION_12_13`。

## 备份与恢复

入口位于 [`CategoryFragment.kt`](../app/src/main/java/com/lewis/timetable/CategoryFragment.kt)。

当前备份覆盖：
- 待办
- 重复任务
- 标签与颜色
- 任务标签关联
- 课表
- 课程
- 时间表
- 时间表节次
- 当前激活课表 ID

从 2026-04-14 起，课表备份新增：
- `reminderEnabled`
- `reminderMinutesBefore`

## 系统限制

在 Android 12+，提醒稳定性主要受这些条件影响：

- 通知权限
- 精确闹钟权限
- 电池优化 / 后台冻结
- 厂商 ROM 的自启动与最近任务清理策略

在 MIUI 等 ROM 上，如果用户从最近任务划掉应用，系统仍可能把它视为 `force-stop`。当前代码已经补了恢复与补发，但无法绕过 ROM 自身的后台限制。

## 构建与验证

常用命令：

```bash
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat assembleDebug
./gradlew.bat :app:lintDebug
./gradlew.bat testDebugUnitTest
```

本次提醒功能的最小验证命令仍是：

```bash
./gradlew.bat :app:compileDebugKotlin
```

## 维护建议

- 改提醒相关代码时，优先保证调度日志可读
- 改数据库结构时，同时检查 Room 迁移和备份恢复格式
- 改课表切换逻辑时，确认课程提醒是否跟着当前激活课表一起重建
- 改通知文案或通知 ID 时，确认补发去重逻辑仍然成立

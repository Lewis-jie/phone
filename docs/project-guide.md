# Project Guide

## 文档目的

这份文档面向维护者，重点说明当前项目的架构分层、核心链路、关键文件和需要注意的系统限制。

如果你只是想快速了解产品功能，请先看 [README.md](../README.md)。  
如果你想看按时间整理的变更记录，请看 [CHANGELOG.md](../CHANGELOG.md)。

## 项目概览

这是一个单模块 Android 应用，核心能力由四条主线组成：

- 待办管理：普通待办、重复待办、标签、星标、历史归档
- 综合日程：日视图、周视图、月视图，把课程与待办统一展示
- 课表管理：课表、课程、时间表节次、HTML 导入
- 本地提醒：通知渠道、精确闹钟、开机恢复和最近错过提醒补发

当前技术组合：

- Kotlin
- Android Views + XML
- Fragment + Jetpack Navigation
- AndroidViewModel + LiveData
- Room
- SharedPreferences
- MPAndroidChart

## 目录速查

```text
MyApp/
├─ app/
│  ├─ build.gradle.kts
│  ├─ src/main/java/com/lewis/timetable/
│  └─ src/main/res/
├─ docs/
│  └─ project-guide.md
├─ CHANGELOG.md
└─ README.md
```

## 架构分层

### 入口层

- [`MyApplication.kt`](../app/src/main/java/com/lewis/timetable/MyApplication.kt)
  负责应用级初始化，当前会创建提醒通知渠道，并在 Debug 下启用 `StrictMode`。
- [`MainActivity.kt`](../app/src/main/java/com/lewis/timetable/MainActivity.kt)
  是唯一 Activity，承载 `NavHostFragment`、底部导航、首屏 ready 控制和权限请求。

### 数据层

- [`AppDatabase.kt`](../app/src/main/java/com/lewis/timetable/AppDatabase.kt)
  集中管理待办、标签、课表、课程和时间表相关表。
- [`TaskDao.kt`](../app/src/main/java/com/lewis/timetable/TaskDao.kt)
  提供待办、重复实例、历史记录以及提醒恢复需要的同步查询。
- [`TagDao.kt`](../app/src/main/java/com/lewis/timetable/TagDao.kt)
  负责标签、颜色聚合和任务标签关联。
- [`CourseDao.kt`](../app/src/main/java/com/lewis/timetable/CourseDao.kt)
  管理课表与课程。
- [`TimetableDao.kt`](../app/src/main/java/com/lewis/timetable/TimetableDao.kt)
  管理时间表模板和节次。

### 仓储与业务层

- [`TaskRepository.kt`](../app/src/main/java/com/lewis/timetable/TaskRepository.kt)
  封装待办 CRUD、标签绑定、重复实例回填和提醒调度。
- [`CourseRepository.kt`](../app/src/main/java/com/lewis/timetable/CourseRepository.kt)
  封装课表与课程读写。
- [`TimetableRepository.kt`](../app/src/main/java/com/lewis/timetable/TimetableRepository.kt)
  负责时间表和节次维护。
- [`RepeatTaskHelper.kt`](../app/src/main/java/com/lewis/timetable/RepeatTaskHelper.kt)
  计算重复规则发生时间。

### ViewModel 层

- [`TaskViewModel.kt`](../app/src/main/java/com/lewis/timetable/TaskViewModel.kt)
  负责待办、标签、重复任务实例同步和保存逻辑。
- [`CourseViewModel.kt`](../app/src/main/java/com/lewis/timetable/CourseViewModel.kt)
  负责当前激活课表、课程列表和课表设置。

### UI 层

- [`TaskListFragment.kt`](../app/src/main/java/com/lewis/timetable/TaskListFragment.kt)
  待办首页，处理列表、筛选、完成和删除。
- [`AddTaskFragment.kt`](../app/src/main/java/com/lewis/timetable/AddTaskFragment.kt)
  待办编辑页，处理时间、提醒、重复规则、标签和保存校验。
- [`HistoryFragment.kt`](../app/src/main/java/com/lewis/timetable/HistoryFragment.kt)
  历史待办页面。
- [`ScheduleFragment.kt`](../app/src/main/java/com/lewis/timetable/ScheduleFragment.kt)
  日、周、月综合日程页，是课程与待办的主要聚合点。
- [`CourseFragment.kt`](../app/src/main/java/com/lewis/timetable/CourseFragment.kt)
  课表周视图。
- [`CourseSettingsFragment.kt`](../app/src/main/java/com/lewis/timetable/CourseSettingsFragment.kt)
  课表设置页。
- [`CourseOverviewFragment.kt`](../app/src/main/java/com/lewis/timetable/CourseOverviewFragment.kt)
  课程总览页。
- [`CourseLessonEditFragment.kt`](../app/src/main/java/com/lewis/timetable/CourseLessonEditFragment.kt)
  单门课程编辑页。
- [`TimetableEditorFragment.kt`](../app/src/main/java/com/lewis/timetable/TimetableEditorFragment.kt)
  时间表节次编辑页。

## 关键业务决定

### 1. 单模块结构

所有业务都在 `app` 模块内完成，没有拆成 feature module。优点是迭代成本低，缺点是页面和领域边界更多依赖命名约定。

### 2. 继续使用 Fragment + XML

当前 UI 栈仍然是传统 View 系统。布局、动画、主题和 Navigation 都围绕 XML 组织，没有迁移到 Compose。

### 3. Room 统一承载核心数据

待办、标签、课表、课程和时间表都走同一个 `AppDatabase`。这样查询聚合简单，但数据库迁移需要格外谨慎。

### 4. 重复任务采用“根任务 + 派生实例”
从 2026-04-14 起，根任务新增 `skippedDates`，用于记录“仅删除此次”的跳过日期。`TaskViewModel`、`TaskRepository` 和 `RepeatTaskHelper` 都会读取这个字段，避免被手动删除的单次循环实例在后续同步时再次回补。

重复规则保存在根任务上，展示和完成操作主要发生在派生实例上。`TaskRepository` 和 `TaskViewModel` 会把实例补齐到需要的时间范围。

### 5. 日程页在 UI 层聚合多源数据

`ScheduleFragment` 同时观察待办、重复实例、标签色、当前课表、课程和时间表节次，再在页面层做日、周、月视图计算。它是当前最复杂的聚合点。

## 提醒链路

当前提醒逻辑已经是可用状态，不再是占位文件。

### 调度入口

- [`AddTaskFragment.kt`](../app/src/main/java/com/lewis/timetable/AddTaskFragment.kt)
  保存提醒前会检查提醒时间是否合法，并在 Android 12+ 校验精确闹钟权限。
- [`TaskRepository.kt`](../app/src/main/java/com/lewis/timetable/TaskRepository.kt)
  新建、更新、删除待办时会调用提醒调度或取消。

### 调度实现

- [`ReminderScheduler.kt`](../app/src/main/java/com/lewis/timetable/ReminderScheduler.kt)
  负责注册、取消和批量恢复提醒。
- 有精确闹钟权限时优先走 `AlarmManager.setAlarmClock(...)`。
- 无精确闹钟权限时退回 `setAndAllowWhileIdle(...)`。

### 广播与通知

- [`ReminderReceiver.kt`](../app/src/main/java/com/lewis/timetable/ReminderReceiver.kt)
  接收 `AlarmManager` 广播并记录投递延迟。
- [`ReminderNotificationDispatcher.kt`](../app/src/main/java/com/lewis/timetable/ReminderNotificationDispatcher.kt)
  检查通知总开关、渠道状态，安全发出通知并记录日志。
- [`ReminderSettingsHelper.kt`](../app/src/main/java/com/lewis/timetable/ReminderSettingsHelper.kt)
  创建通知渠道。
- [`ReminderDeliveryStore.kt`](../app/src/main/java/com/lewis/timetable/ReminderDeliveryStore.kt)
  记录已投递提醒，避免补发和重复通知。

### 恢复与补发

- [`BootReceiver.kt`](../app/src/main/java/com/lewis/timetable/BootReceiver.kt)
  开机后重新读取任务并恢复未来提醒。
- `ReminderScheduler.scheduleAll(...)`
  除了恢复未来提醒，也会对最近错过且尚未投递的提醒做 catch-up 补发。

### 系统限制

在 Android 12+，提醒至少受这几类系统条件影响：

- 通知权限
- 精确闹钟权限
- 电池优化 / 后台冻结
- 厂商 ROM 的自启动与最近任务清理策略

在 MIUI 等 ROM 上，如果用户主动从最近任务里划掉应用，系统仍可能阻断后续提醒。当前代码已经尽量做了恢复和补发，但不能越过 ROM 的后台限制。

## 页面导航与动画

- Navigation 定义在 [`nav_graph.xml`](../app/src/main/res/navigation/nav_graph.xml)
- 编辑页相关切换统一使用：
  - 进入：`slide_up`
  - 退出：`fade_stay`
  - 返回进入：`fade_stay`
  - 返回退出：`slide_down`

这样做的目标是：

- 新页面带着自己的背景整体上滑
- 旧页面在下层保持可见
- 动画过程中不会先露出容器背景，也不会出现两页一起漂移的重叠感

相关动画资源：

- [`slide_up.xml`](../app/src/main/res/anim/slide_up.xml)
- [`slide_down.xml`](../app/src/main/res/anim/slide_down.xml)
- [`fade_stay.xml`](../app/src/main/res/anim/fade_stay.xml)

## 备份与恢复

- 入口在 [`CategoryFragment.kt`](../app/src/main/java/com/lewis/timetable/CategoryFragment.kt)
- 当前备份覆盖：
  - 待办
  - 重复任务
  - 标签与颜色
  - 任务标签关联
  - 课表
  - 课程
  - 时间表
  - 时间表节次
  - 当前激活课表 ID

恢复时会先清空表再批量写回，因此备份文件兼容性和数据库版本迁移要一起考虑。

## 关键文件速查

| 路径 | 作用 |
| --- | --- |
| [`app/src/main/java/com/lewis/timetable/MainActivity.kt`](../app/src/main/java/com/lewis/timetable/MainActivity.kt) | 唯一 Activity，管理导航和权限 |
| [`app/src/main/java/com/lewis/timetable/TaskViewModel.kt`](../app/src/main/java/com/lewis/timetable/TaskViewModel.kt) | 待办主 ViewModel |
| [`app/src/main/java/com/lewis/timetable/TaskRepository.kt`](../app/src/main/java/com/lewis/timetable/TaskRepository.kt) | 待办仓储与提醒调度入口 |
| [`app/src/main/java/com/lewis/timetable/ScheduleFragment.kt`](../app/src/main/java/com/lewis/timetable/ScheduleFragment.kt) | 综合日程聚合页 |
| [`app/src/main/java/com/lewis/timetable/ReminderScheduler.kt`](../app/src/main/java/com/lewis/timetable/ReminderScheduler.kt) | 提醒调度核心 |
| [`app/src/main/java/com/lewis/timetable/ReminderReceiver.kt`](../app/src/main/java/com/lewis/timetable/ReminderReceiver.kt) | 提醒广播接收器 |
| [`app/src/main/res/navigation/nav_graph.xml`](../app/src/main/res/navigation/nav_graph.xml) | 页面跳转关系 |
| [`app/src/main/res/values/themes.xml`](../app/src/main/res/values/themes.xml) | 主题定义 |
| [`app/build.gradle.kts`](../app/build.gradle.kts) | app 模块构建配置 |

## 构建与验证

常用命令：

```bash
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat assembleDebug
./gradlew.bat :app:lintDebug
./gradlew.bat testDebugUnitTest
```

当前开发中，最常用的快速验证仍然是：

```bash
./gradlew.bat :app:compileDebugKotlin
```

## 维护建议

- 改提醒相关代码时，优先保持 `ReminderScheduler` 日志可读，否则真机定位成本很高
- 改日程页时，先确认 `ScheduleFragment` 的多源数据 ready 条件，避免出现首屏中间态
- 改导航动画时，不要只改某一个 action，编辑页系列需要整体统一
- 改数据库结构时，先检查 `AppDatabase` 迁移和备份恢复是否都要同步调整

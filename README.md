# TimeTable

`TimeTable` 是一个面向学习与日常安排场景的 Android 应用，把待办、重复任务、课程和综合日程放到同一套时间管理体验里。

默认首页为“日程”周视图。

## 功能概览

### 待办与重复任务
- 支持创建、编辑、删除、完成、恢复和历史归档
- 支持开始时间、结束时间、提醒时间和备注
- 支持按天、周、月、年、工作日和自定义周几重复
- 支持标签、星标和最近标签复用
- 完成任务后提供 `Snackbar` 撤销反馈

### 日程视图
- 日视图查看单日时间线
- 周视图统一展示课程与待办
- 月视图查看整月分布并展开指定日期详情
- 支持左右滑动切换日期、周和月份
- 支持“今天”快捷返回和上下文提示

### 课表管理
- 支持导入学校教务系统导出的 HTML 课表
- 支持多课表管理和当前激活课表切换
- 支持自定义学期开始日期、总周数和时间表节次
- 支持课程总览、课程编辑和时间表编辑
- 支持按课表设置课程提醒：开启提醒并选择“上课前 XX 分钟”

### 提醒与通知
- 待办支持本地提醒通知
- 待办通知样式为：`待办「待办名称」` + `将于 XX 时间后开始`
- 课程支持独立本地提醒通知
- 课程通知样式为：`课程名 即将开始`
- 开机后会恢复未来提醒
- 应用恢复可用后会补发最近错过的提醒
- Android 13+ 支持运行时通知权限申请
- Android 12+ 支持精确闹钟权限引导

### 数据与主题
- 支持完整备份与恢复
- 备份覆盖待办、重复任务、标签、课表、课程、时间表和当前激活课表
- 支持多套主题和自定义颜色
- 分类页提供完成统计图表

## 最近更新

### 2026-04-14
- 修复循环待办“仅删除此次”后的跳过日期回填问题
- 备份与恢复新增 `skippedDates`，避免恢复后丢失单次跳过记录
- 新增课程提醒调度、开机恢复、补发与通知投递链路
- 课表设置页新增独立“提醒时间”卡片，可开启课程提醒并选择上课前 5/10/15/30/60 分钟
- 更新待办提醒通知文案为“待办「名称」+ 将于 XX 时间后开始”

完整变更见 [CHANGELOG.md](./CHANGELOG.md)。

## 技术栈
- Kotlin
- Android Views + XML
- Fragment + Jetpack Navigation
- AndroidViewModel + LiveData
- Room
- WorkManager
- KSP
- MPAndroidChart
- Gradle Kotlin DSL

## 运行环境
- Android Studio Stable
- JDK 11
- `compileSdk 36`
- `targetSdk 36`
- `minSdk 24`

## 构建与运行

### Android Studio
直接使用 Android Studio 打开项目根目录，等待 Gradle Sync 完成后运行 `app` 模块即可。

### 命令行
```bash
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat assembleDebug
```

如需执行检查：

```bash
./gradlew.bat :app:lintDebug
./gradlew.bat testDebugUnitTest
```

## 提醒功能说明

提醒依赖系统通知能力，不同 ROM 的后台策略差异较大。为了让提醒更稳定，建议同时确认这些系统设置：

- 允许通知权限
- 开启“闹钟和提醒”或精确闹钟权限
- 关闭电池优化，或将应用设为“不受限制”
- 在有自启动管理的 ROM 上允许自启动
- 不要从最近任务中手动清理应用

在 MIUI 等 ROM 上，如果用户主动从最近任务里划掉应用，系统仍可能把它当成 `force-stop`。当前实现已经尽量恢复和补发，但不能绕过 ROM 本身的后台限制。

## 项目结构

```text
MyApp/
├── app/
│   ├── src/main/java/com/lewis/timetable/   # 业务代码
│   ├── src/main/res/                        # 布局、动画、主题和图形资源
│   ├── src/test/                            # 单元测试
│   └── build.gradle.kts                     # app 模块配置
├── docs/                                    # 项目说明与维护文档
├── CHANGELOG.md
├── README.md
└── settings.gradle.kts
```

## 核心页面
- `MainActivity`：应用入口、导航宿主和底部导航
- `TaskListFragment`：待办列表、筛选、完成和删除
- `AddTaskFragment`：待办新建与编辑
- `HistoryFragment`：历史待办查看、恢复和删除
- `ScheduleFragment`：日、周、月日程页
- `CourseFragment`：课表周视图
- `CourseSettingsFragment`：课表设置与课程提醒设置
- `CategoryFragment`：统计、主题和备份恢复

## 文档
- [README.md](./README.md)：项目概览、功能和运行方式
- [CHANGELOG.md](./CHANGELOG.md)：版本变更记录
- [docs/project-guide.md](./docs/project-guide.md)：维护视角的架构、关键文件和提醒链路说明

## 开源许可
本项目使用 [Apache-2.0](./LICENSE)。

# TimeTable

`TimeTable` 是一个面向学习与日常安排场景的 Android 应用，把待办、重复任务、课表和多视图日程放到同一套时间管理体验里。

默认首页为“日程”，默认进入周视图。

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

### 提醒与通知

- 待办支持本地提醒通知
- 开机后会重新恢复未来提醒
- 最近错过的提醒会在应用恢复可用后补发
- Android 13+ 支持运行时通知权限申请
- Android 12+ 支持精确闹钟权限引导

### 数据与主题

- 支持完整备份与恢复
- 备份覆盖待办、重复任务、标签、课表、课程、时间表和当前激活课表
- 支持多套主题和自定义颜色
- 分类页提供完成统计图表

## 最近更新

### 2026-04-14

- 修复循环待办跨天补实例后，完成旧实例会重复生成下一条的问题
- 调整循环待办“仅删除此次”：删除某一天会记录为跳过日期，并直接推进到下一次有效实例
- 备份与恢复新增循环待办跳过日期字段，避免恢复后丢失单次删除记录

### 2026-04-04

- 完成待办后新增 `Snackbar` 撤销反馈
- 重复任务完成后自动生成下一次实例，并支持撤销回收
- 日程页新增“今天”快捷返回和上下文提示
- 补齐提醒链路，包括通知渠道、精确闹钟、广播接收和通知派发
- 统一待办编辑页和课表编辑页的切换动画，避免切换过程背景露白和叠页感
- 重写项目文档，补充 GitHub 可读说明

### 2026-04-02

- 修复周视图、月视图和历史待办页的多处崩溃与渲染问题
- 启动阶段加入纯白遮罩，等待首屏 ready 后再显示主界面
- 完整备份恢复覆盖待办、重复任务、标签、课表、时间表和当前激活课表
- 清理大量 `lint` 问题、未使用资源和硬编码文案

完整变更见 [CHANGELOG.md](./CHANGELOG.md)。

## 技术栈

- Kotlin
- Android Views + XML
- Fragment + Jetpack Navigation
- AndroidViewModel + LiveData
- Room
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

直接用 Android Studio 打开项目根目录，等待 Gradle Sync 完成后运行 `app` 模块即可。

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

## 项目结构

```text
MyApp/
├─ app/
│  ├─ src/main/java/com/lewis/timetable/   # 业务代码
│  ├─ src/main/res/                        # 布局、动画、主题和图形资源
│  ├─ src/test/                            # 单元测试
│  └─ build.gradle.kts                     # app 模块配置
├─ docs/                                   # 项目说明与维护文档
├─ gradle/
├─ CHANGELOG.md
├─ README.md
└─ settings.gradle.kts
```

## 核心页面

- `MainActivity`：应用入口、导航宿主和底部导航
- `TaskListFragment`：待办列表、筛选、完成和删除
- `AddTaskFragment`：待办新建与编辑
- `HistoryFragment`：历史待办查看、恢复和删除
- `ScheduleFragment`：日、周、月日程页
- `CourseFragment`：课表周视图
- `CourseSettingsFragment`：课表设置
- `CategoryFragment`：统计、主题和备份恢复

## 文档

- [README.md](./README.md)：项目概览、功能和运行方式
- [CHANGELOG.md](./CHANGELOG.md)：版本变更记录
- [docs/project-guide.md](./docs/project-guide.md)：维护者视角的架构、关键文件和提醒链路说明

## 开源许可

本项目使用 [Apache-2.0](./LICENSE)。

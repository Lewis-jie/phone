# TimeTable

一个面向学习和日常安排场景的 Android 时间管理应用，整合了待办、重复待办、课表和多视图日程。

## 项目简介

`TimeTable` 以“任务 + 课表 + 日程”三条主线组织功能：

- 待办管理：支持创建、编辑、删除、完成、星标、标签筛选
- 重复待办：支持按天、周、月、年、工作日和自定义周几生成
- 日程视图：支持日视图、周视图、月视图统一查看课程与待办
- 课表管理：支持导入 HTML 课表、课程编辑、时间表节次配置
- 数据管理：支持完整备份与恢复，包括待办、重复待办、标签、课表和时间表
- 主题系统：支持多套预设主题和自定义颜色

当前默认启动页为“日程”，默认进入周视图。

## 主要功能

### 1. 待办

- 新建和编辑待办
- 起止时间、提醒时间、重复规则
- 标签绑定与最近标签复用
- 星标任务筛选
- 历史待办查看、恢复和删除

### 2. 日程

- 日视图：查看单日任务时间线
- 周视图：合并展示课程和待办
- 月视图：查看整月分布并展开指定日期详情
- 左右滑动切换日期 / 周 / 月

### 3. 课表

- 导入学校教务系统导出的 HTML 课表
- 多课表管理
- 自定义学期开始时间与总周数
- 自定义时间表节次和每节时长
- 课程总览与课程编辑

### 4. 设置与数据

- 主题颜色切换
- 完成统计
- 完整数据备份与恢复

## 最近更新

### 2026-04-04

- 待办完成后新增 `Snackbar` 反馈，支持直接撤销
- 重复任务完成后会自动生成下一次，并支持撤销回收刚生成的实例
- 日 / 周 / 月日程页新增“今天”快捷返回和上下文提示文案
- 新增项目说明文档，补充文件路径、职责和关键架构说明

### 2026-04-02

- 修复周视图、月视图和历史待办页的多处崩溃与渲染问题
- 历史待办页新增更稳定的页面背景和卡片样式
- 周视图首屏渲染链路收口，减少课程后补刷的中间态
- 启动阶段加入纯白遮罩，等待周视图首屏准备完成后再进入主界面
- 备份恢复覆盖待办、重复待办、标签、课表、时间表和当前激活课表
- 清理大量 `lint` 问题、未使用资源和硬编码文案

完整变更见 [CHANGELOG.md](D:/Users/Lewis/Desktop/AndroidStudioProjects/MyApp/CHANGELOG.md)。

## 技术栈

- Kotlin
- Android Views + Fragment
- Jetpack Navigation
- ViewModel + LiveData
- Room
- KSP
- MPAndroidChart
- Gradle Kotlin DSL

## 运行环境

- Android Studio Stable
- JDK 11
- compileSdk 36
- targetSdk 36
- minSdk 24

## 构建与运行

### Android Studio

直接用 Android Studio 打开项目根目录：

`D:\Users\Lewis\Desktop\AndroidStudioProjects\MyApp`

等待 Gradle Sync 完成后运行 `app` 模块即可。

### 命令行

```bash
gradlew.bat :app:compileDebugKotlin
gradlew.bat assembleDebug
```

如需执行检查：

```bash
gradlew.bat :app:lintDebug
gradlew.bat testDebugUnitTest
```

## 项目结构

```text
MyApp/
├─ app/
│  ├─ src/main/java/com/lewis/timetable/   # 业务代码
│  ├─ src/main/res/                        # 布局、动画、主题和图形资源
│  ├─ src/test/                            # 单元测试
│  └─ build.gradle.kts                     # app 模块配置
├─ docs/                                   # 设计和规格文档
├─ gradle/
├─ CHANGELOG.md
├─ README.md
└─ settings.gradle.kts
```

## 项目文档

- [README.md](D:/Users/Lewis/Desktop/AndroidStudioProjects/MyApp/README.md)：项目概览、功能和运行方式
- [docs/project-guide.md](D:/Users/Lewis/Desktop/AndroidStudioProjects/MyApp/docs/project-guide.md)：完整文件路径说明、当前待提交改动和关键架构决定

## 核心页面

- `MainActivity`：应用入口与底部导航
- `TaskListFragment`：待办列表与筛选
- `AddTaskFragment`：待办编辑
- `HistoryFragment`：历史待办
- `ScheduleFragment`：日 / 周 / 月日程
- `CourseFragment`：课表查看
- `CourseSettingsFragment`：课表设置
- `CategoryFragment`：统计、主题和备份恢复

## 数据与备份

当前备份会包含：

- 待办
- 重复待办
- 星标状态
- 标签与标签颜色
- 待办标签关联
- 课表
- 课程
- 时间表
- 时间表节次
- 当前激活课表 ID

## 开源许可

本项目使用 [Apache-2.0](D:/Users/Lewis/Desktop/AndroidStudioProjects/MyApp/LICENSE)。

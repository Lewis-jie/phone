# TimeTable

## Quick Overview

Android 时间管理应用，整合待办、课程表和日程视图。

### 2026-04-01 更新

- 默认启动进入日程页周视图
- 日 / 周 / 月视图左右切换动画只作用于主体内容区
- 日视图顶部日期栏固定，不随内容上下滚动
- 月视图进入当天详情后，左右切换仅动画下半部分详情
- 周视图时间轴更紧凑，结束时间改为左下角显示
- 周视图首屏改为先渲染稳定骨架，再异步填充课程和任务
- 课程节次未加载完成前不先画课程块，避免首屏二次跳动
- 周视图仅在数据真正变化时刷新覆盖层，不再整块重建
- 修复周视图底部时间轴 `22:00` 显示被遮挡的问题
- Gradle 固定使用本地 JDK 21，并开启缓存配置以加快构建

### Build

- Android Studio Stable
- JDK 21
- Android SDK 36
- minSdk 24

```bash
gradlew.bat :app:compileDebugKotlin
gradlew.bat assembleDebug
```

完整更新日志见 `CHANGELOG.md`。

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)
![targetSdk](https://img.shields.io/badge/targetSdk-36-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-Apache--2.0-orange)

一个面向日常学习场景的 Android 时间管理应用，整合了待办事项、课程表和日程视图，方便统一安排学习任务与上课时间。

## 项目简介

`TimeTable` 是一个基于原生 Android 构建的个人效率应用，当前聚焦三个核心场景：

- 待办任务管理
- 课程表导入与维护
- 按天 / 周 / 月查看综合日程

应用首页默认进入日程页，底部提供 `任务`、`日程`、`分类` 三个主入口。其中“分类”页同时承载主题切换、数据备份和统计信息等设置能力。

## 功能特性

- 任务新增、编辑、删除、完成勾选、置顶标星
- 按标签筛选任务，支持最近标签快捷复用
- 支持重复任务规则：每天、每周、每月、每年、工作日、自定义周几
- 支持提醒时间设置，适合需要提前通知的学习任务
- 日程页支持日视图、周视图、月视图切换
- 周视图可将课程与任务合并展示，方便查看时间冲突
- 支持创建空白课表，也支持导入教务系统导出的 HTML 课表
- 已接入 `JxnuHtmlParser`、`JnuHtmlParser` 两种课表解析器
- 课程支持总览、时间段配置、课程编辑
- 分类页提供完成统计图、主题色切换、任务数据导入导出

## 界面预览

将真实截图放到 `docs/images/` 目录后，可直接替换下面的占位文件名：

| 任务页 | 日程页 | 课表页 |
| --- | --- | --- |
| ![任务页](docs/images/tasks.png) | ![日程页](docs/images/schedule.png) | ![课表页](docs/images/course.png) |

如果你准备录制操作演示，也可以增加：

```md
![Demo](docs/images/demo.gif)
```

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
- Android SDK 36
- 最低支持 Android 7.0（API 24）

## 安装与运行

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd MyApp
```

### 2. 使用 Android Studio 打开

直接用 Android Studio 打开项目根目录：

`D:\Users\Lewis\Desktop\AndroidStudioProjects\MyApp`

首次打开后等待 Gradle Sync 完成。

### 3. 运行应用

选择模拟器或真机后，运行 `app` 模块即可。

### 4. 命令行构建

```bash
gradlew.bat assembleDebug
```

如需执行测试：

```bash
gradlew.bat test
gradlew.bat connectedAndroidTest
```

## 使用说明

### 任务管理

在“任务”页可以：

- 新增待办事项
- 编辑任务标题、描述、日期、时间、提醒和重复规则
- 为任务添加标签并按标签筛选
- 勾选完成、删除任务、拖拽调整同分组内顺序

### 日程查看

在“日程”页可以：

- 查看某一天的任务时间线
- 以周视图同时查看课程和任务安排
- 在月视图中浏览整月任务分布，并展开查看指定日期详情

### 课表管理

在“分类”页进入课表功能后，可以：

- 创建空白课表
- 导入教务系统导出的 HTML 课表
- 配置学期周数与课表名称
- 查看课程总览并编辑单门课程
- 自定义时间表节次

## 课表导入支持

当前项目已实现以下课表 HTML 解析器：

- `JxnuHtmlParser`
- `JnuHtmlParser`

如果你后续接入更多学校教务系统，可以继续在 [CourseImportRegistry.kt](D:/Users/Lewis/Desktop/AndroidStudioProjects/MyApp/app/src/main/java/com/lewis/timetable/CourseImportRegistry.kt) 注册新的解析器。

## 项目结构

```text
MyApp/
├─ app/
│  ├─ src/main/java/com/lewis/timetable/   # 业务代码
│  ├─ src/main/res/                        # 布局、动画、图标与主题资源
│  ├─ src/test/                            # 单元测试
│  └─ build.gradle.kts                     # app 模块配置
├─ gradle/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradlew
├─ gradlew.bat
└─ README.md
```

核心模块大致如下：

- `MainActivity`：应用入口与底部导航
- `TaskListFragment`：任务列表与筛选
- `AddTaskFragment`：任务编辑表单
- `ScheduleFragment`：日 / 周 / 月综合日程
- `CourseFragment` 及相关页面：课表导入、设置、总览、课程编辑
- `CategoryFragment`：统计、主题、备份导入导出

## 数据与权限说明

- 当前项目使用 Room 进行本地数据存储
- 应用包含存储读取相关权限，主要用于备份导出和文件导入
- 备份文件会导出到系统 `Downloads/TimeTable/` 目录
- `local.properties` 为本地环境文件，不应提交到公开仓库

## 开发与协作

欢迎提交 Issue 和 Pull Request 来改进这个项目。

建议流程：

1. Fork 本仓库并创建功能分支
2. 完成修改后自行验证主要功能
3. 如涉及 UI，请附上截图或 GIF
4. 提交 Pull Request，并说明改动原因和测试方式

PR 模板见 [.github/PULL_REQUEST_TEMPLATE.md](D:/Users/Lewis/Desktop/AndroidStudioProjects/MyApp/.github/PULL_REQUEST_TEMPLATE.md)。

## 测试状态

项目当前包含基础示例测试文件：

- [ExampleUnitTest.kt](D:/Users/Lewis/Desktop/AndroidStudioProjects/MyApp/app/src/test/java/com/lewis/timetable/ExampleUnitTest.kt)
- [ExampleInstrumentedTest.kt](D:/Users/Lewis/Desktop/AndroidStudioProjects/MyApp/app/src/androidTest/java/com/lewis/timetable/ExampleInstrumentedTest.kt)

如果后续准备公开仓库，建议补充：

- `README` 截图或 GIF 演示
- `.github/PULL_REQUEST_TEMPLATE.md`
- 更完整的单元测试与 UI 测试

## License

本项目使用 [Apache-2.0](D:/Users/Lewis/Desktop/AndroidStudioProjects/MyApp/LICENSE) 许可证。

## 后续可优化方向

- 补充通知调度与 WorkManager 相关实现说明
- 为 README 添加真实界面截图
- 增加 CI 构建与测试流程
- 完善数据导入导出的兼容性说明

## 作者

- GitHub: `Lewis-jie`

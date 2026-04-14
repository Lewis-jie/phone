# Changelog

## 2026-04-14

### 循环待办
- 修复跨天补实例后，已完成旧实例可能重复生成下一条的问题
- 新增循环待办 `skippedDates` 持久化，删除“仅删除此次”后不会再被回补
- 调整单次删除逻辑：删除较新的循环实例时，会推进系列到下一次有效日期

### 提醒与通知
- 新增课程提醒调度链路，支持按课表设置“上课前 XX 分钟”提醒
- `BootReceiver`、`MyApplication` 和 `ReminderWorker` 会在开机或应用恢复时重建课程提醒
- 新增课程提醒广播、通知分发、已调度注册表和补发去重存储
- 待办通知文案调整为 `待办「名称」` + `将于 XX 时间后开始`
- 课程通知文案调整为 `课程名 即将开始`

### 课表设置
- 课表设置页新增独立“提醒时间”卡片
- 提供课程提醒开关
- 提供上课前 `5/10/15/30/60` 分钟选择
- 导入覆盖当前课表时保留原有提醒设置

### 备份与恢复
- 备份 JSON 新增 `skippedDates`
- 课表备份与恢复新增 `reminderEnabled` 和 `reminderMinutesBefore`

## 2026-04-04

### 提醒与通知
- 在 `MyApplication` 启动时初始化通知渠道
- `MainActivity` 在 Android 13+ 主动申请 `POST_NOTIFICATIONS`
- `TaskRepository` 保存、更新和删除待办时同步调度提醒
- `ReminderScheduler` 优先使用精确闹钟注册提醒
- `BootReceiver` 在开机后恢复未来待办提醒
- 新增 `ReminderNotificationDispatcher` 统一处理通知分发和日志
- 新增最近错过提醒的 catch-up 补发逻辑

### 编辑页切换动画
- 统一待办编辑页、课表设置页、课程总览页、课程编辑页、时间表编辑页和导入页的切换方式
- 进入改为新页面整体上滑，返回改为当前页面整体下滑
- 使用 `fade_stay` 保持底层页面可见，避免露白和叠页漂移

### 待办与日程体验
- 待办完成后新增 `Snackbar` 反馈
- 普通任务支持直接撤销完成状态
- 重复任务完成后自动生成下一次实例，并支持撤销时回收
- 日、周、月日程视图新增“今天”快捷返回和上下文提示

### 文档
- 重写 `README.md`
- 更新 `CHANGELOG.md`
- 重写 `docs/project-guide.md`

## 2026-04-02

### 启动与首屏
- 默认进入日程页周视图
- 启动阶段增加纯白遮罩，首屏真正 ready 后再显示主界面
- `MainActivity` 在首屏 ready 后再调用 `reportFullyDrawn()`

### 日程页
- 修复周视图和月视图中的多处空白、闪退和主题色不一致问题
- 周视图在数据未齐时仅显示骨架，不再先画待办再补课程
- 优化周视图骨架与 overlay 的刷新逻辑，减少无效重绘

### 历史待办
- 新增历史待办页面与适配器
- 优化历史待办页背景层级，修复透明区域露出错误背景的问题

### 任务与课表
- 完整备份 / 恢复覆盖待办、重复待办、标签、课表、课程、时间表和当前激活课表
- 修正当前课表读取逻辑，继续使用 `SharedPreferences(app_prefs.active_schedule_id)`

### 工程与质量
- 清理多处旧写法和主要 `lint` 问题
- 保持 `./gradlew.bat :app:compileDebugKotlin` 和 `./gradlew.bat :app:lintDebug` 可通过

# Reminder and Schedule Reliability Design

## Scope

Fix the reviewed notification compatibility, active-schedule consistency, course rendering, reminder scaling, local-calendar timing, and repository portability issues. Course and classroom details remain fully visible on the lock screen.

## Decisions

- Guard all notification-channel APIs behind Android 8.0 / API 26 checks while retaining `minSdk = 24`.
- Use one activity-scoped `CourseViewModel` for every course-related Fragment so the selected schedule has one in-process source of truth.
- Render course lessons only when the active schedule, lesson snapshot, and timetable snapshot identify the same data set.
- Treat an empty but matching timetable snapshot as loaded and fall back to default lesson periods.
- A lesson is active only when its week is inside `1..totalWeeks` and its bitmap contains that week.
- Serialize course-reminder rebuilds with one process-wide coroutine mutex.
- Register only reminders whose trigger is within the next 28 local calendar days. The existing 12-hour Worker, app startup, boot receiver, and time-change receiver replenish the window.
- Build occurrence times with `Calendar` date arithmetic so daylight-saving transitions preserve local class times.
- Continue scheduling other occurrences when one AlarmManager call fails and keep the registry aligned with alarms that may still exist.
- Keep lock-screen visibility public as explicitly requested.
- Remove machine-specific Gradle JDK paths and ignore local APK/device dump artifacts.

## Verification

- Unit tests cover the 28-day boundary, 40-week validation, daylight-saving time, semester boundaries, and snapshot matching.
- `testDebugUnitTest`, `compileDebugKotlin`, and `lintDebug` must complete successfully.
- `git diff --check` must report no whitespace errors.

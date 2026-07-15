# Reminder and Schedule Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development for every behavior change.

**Goal:** Make course selection and course reminders correct across supported Android versions, large schedules, page switches, and local time changes.

**Architecture:** Course-related Fragments share one activity-scoped ViewModel and consume identity-bearing snapshots. Reminder occurrences are produced by a pure calendar-based planner, while the Android scheduler serializes rebuilds and registers only the next 28 days.

**Tech Stack:** Kotlin, AndroidX Lifecycle, Room LiveData, AlarmManager, WorkManager, JUnit 4.

## Global Constraints

- Keep `minSdk = 24`.
- Keep course and classroom notification content fully visible on the lock screen.
- Do not add dependencies or change unrelated UI behavior.
- Do not delete existing local APK or dump files; ignore them only.

---

### Task 1: Notification compatibility and repository configuration

**Files:**
- Modify: `app/src/main/java/com/lewis/timetable/ReminderSettingsHelper.kt`
- Modify: `app/src/main/java/com/lewis/timetable/CourseReminderNotificationDispatcher.kt`
- Modify: `app/src/main/java/com/lewis/timetable/ReminderNotificationDispatcher.kt`
- Modify: `gradle.properties`
- Modify: `.gitignore`

- [x] Run `lintDebug` and record the existing API-26 failures.
- [x] Return immediately from channel creation below API 26 and conditionally query channels in both dispatchers.
- [x] Remove the two machine-specific JDK path properties.
- [x] Add exact ignore rules for `base.apk`, `_adb_dump`, `_apk_dump`, `_apk_inspect`, and `_apk_manual`.
- [x] Re-run Lint and confirm the notification API errors are gone.

### Task 2: Shared active-schedule state and coherent rendering

**Files:**
- Modify: all Fragments that declare a `CourseViewModel`
- Modify: `app/src/main/java/com/lewis/timetable/CourseFragment.kt`
- Modify: `app/src/main/java/com/lewis/timetable/ScheduleFragment.kt`
- Modify: `app/src/main/java/com/lewis/timetable/CourseLesson.kt`
- Test: `app/src/test/java/com/lewis/timetable/ActiveScheduleLessonsSnapshotTest.kt`
- Test: `app/src/test/java/com/lewis/timetable/ActiveTimetablePeriodSnapshotTest.kt`
- Create: `app/src/test/java/com/lewis/timetable/CourseLessonWeekTest.kt`

- [x] Add failing tests showing that weeks before the semester and after `totalWeeks` are inactive.
- [x] Add a failing snapshot test for an empty but matching timetable.
- [x] Change every course-related Fragment to `activityViewModels()`.
- [x] Make `CourseFragment` consume snapshots plus `activeSchedule`, exposing lists only when identities match.
- [x] Make an empty matching timetable ready and use default period times.
- [x] Replace fixed-day overlay calculations with local `Calendar` day boundaries.
- [x] Run the focused snapshot and week tests.

### Task 3: Rolling reminder planning and serialized scheduling

**Files:**
- Create: `app/src/main/java/com/lewis/timetable/CourseReminderOccurrencePlanner.kt`
- Modify: `app/src/main/java/com/lewis/timetable/CourseReminderScheduler.kt`
- Test: `app/src/test/java/com/lewis/timetable/CourseReminderOccurrencePlannerTest.kt`

**Interfaces:**
- Produce: `CourseReminderOccurrencePlanner.build(schedule, lessons, periods, now, timeZone): List<CourseReminderOccurrence>`.
- Produce: occurrences only through the next 28 local calendar days and never beyond 40 teaching weeks.

- [x] Add failing tests for the 28-day inclusive boundary, exclusion beyond the boundary, the 40-week cap, and preservation of local class time across a DST transition.
- [x] Implement the pure occurrence planner with `Calendar.add()` for weeks and days.
- [x] Wrap `scheduleAll()` in a process-wide coroutine `Mutex`.
- [x] Cancel stale registry entries, schedule desired occurrences independently, continue after individual failures, and persist a registry that still permits later cancellation.
- [x] Avoid rescheduling already-delivered future occurrences after a manual clock rollback.
- [x] Run the planner tests and existing notification tests.

### Task 4: Integrated verification

- [x] Run `gradlew.bat testDebugUnitTest --rerun-tasks`.
- [x] Run `gradlew.bat :app:compileDebugKotlin`.
- [x] Run `gradlew.bat lintDebug` and require zero errors.
- [x] Run `git diff --check` and inspect `git diff --stat` for unrelated changes.
- [x] Perform a final code review of the combined diff.

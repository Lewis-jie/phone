package com.lewis.timetable

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class CourseViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: CourseRepository
    private val timetableRepo: TimetableRepository
    val allSchedules: LiveData<List<CourseSchedule>>
    val allTimetables: LiveData<List<Timetable>>

    private val _selectedId = MutableLiveData<Int>()

    val activeLessons: LiveData<List<CourseLesson>> = _selectedId.switchMap { id ->
        if (id <= 0) MutableLiveData(emptyList())
        else repo.getLessonsForSchedule(id)
    }

    val activeTimetablePeriods: LiveData<List<TimetablePeriod>> = _selectedId.switchMap { id ->
        if (id <= 0) return@switchMap MutableLiveData(emptyList())
        repo.getScheduleByIdLive(id).switchMap { schedule ->
            val timetableId = schedule?.timetableId ?: 0
            if (timetableId <= 0) MutableLiveData(emptyList())
            else timetableRepo.getPeriodsForTimetable(timetableId)
        }
    }

    val activeSchedule: LiveData<CourseSchedule?> = _selectedId.switchMap { id ->
        if (id <= 0) MutableLiveData(null)
        else repo.getScheduleByIdLive(id)
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repo = CourseRepository(db.courseDao())
        timetableRepo = TimetableRepository(db.timetableDao())
        allSchedules = repo.allSchedules
        allTimetables = timetableRepo.allTimetables
        val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        _selectedId.value = prefs.getInt("active_schedule_id", 0)
    }

    fun selectSchedule(id: Int) {
        _selectedId.value = id
        getApplication<Application>()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putInt("active_schedule_id", id).apply()
    }

    fun getSelectedId(): Int = _selectedId.value ?: 0

    fun createSchedule(
        name: String,
        semesterStart: Long,
        totalWeeks: Int,
        lessons: List<CourseLesson>,
        onDone: (scheduleId: Int) -> Unit = {}
    ) = viewModelScope.launch {
        val scheduleId = repo.insertSchedule(
            CourseSchedule(name = name, semesterStart = semesterStart, totalWeeks = totalWeeks)
        ).toInt()
        val withId = lessons.map { it.copy(scheduleId = scheduleId) }
        repo.insertLessons(withId)
        selectSchedule(scheduleId)
        onDone(scheduleId)
    }

    fun overwriteSchedule(scheduleId: Int, lessons: List<CourseLesson>) = viewModelScope.launch {
        val withId = lessons.map { it.copy(scheduleId = scheduleId) }
        repo.replaceLessons(scheduleId, withId)
        selectSchedule(scheduleId)
    }

    fun updateScheduleSettings(scheduleId: Int, semesterStart: Long, totalWeeks: Int) =
        viewModelScope.launch {
            val schedule = repo.getScheduleById(scheduleId) ?: return@launch
            repo.updateSchedule(schedule.copy(semesterStart = semesterStart, totalWeeks = totalWeeks))
        }

    fun deleteSchedule(scheduleId: Int) = viewModelScope.launch {
        repo.deleteSchedule(scheduleId)
        if (getSelectedId() == scheduleId) {
            val remaining = repo.getAllSchedulesSync()
            selectSchedule(remaining.firstOrNull()?.id ?: 0)
        }
    }

    suspend fun getScheduleById(id: Int) = repo.getScheduleById(id)
    suspend fun getAllSchedulesSync() = repo.getAllSchedulesSync()
    suspend fun getLessonsSync(scheduleId: Int) = repo.getLessonsForScheduleSync(scheduleId)
    suspend fun getLessonsByCourseName(scheduleId: Int, courseName: String) =
        repo.getLessonsByCourseName(scheduleId, courseName)

    suspend fun getLessonById(id: Int) = repo.getLessonById(id)

    fun saveCourseEdit(
        scheduleId: Int,
        originalCourseName: String,
        newLessons: List<CourseLesson>,
        onDone: () -> Unit = {}
    ) = viewModelScope.launch {
        repo.replaceCourseSlots(scheduleId, originalCourseName, newLessons)
        onDone()
    }

    fun linkTimetableToSchedule(scheduleId: Int, timetableId: Int) = viewModelScope.launch {
        val schedule = repo.getScheduleById(scheduleId) ?: return@launch
        repo.updateSchedule(schedule.copy(timetableId = timetableId))
    }

    suspend fun getTimetableById(id: Int) = timetableRepo.getTimetableById(id)
    suspend fun getPeriodsForTimetableSync(id: Int) = timetableRepo.getPeriodsForTimetableSync(id)

    fun saveTimetable(
        timetableId: Int,
        name: String,
        sameDuration: Boolean,
        durationMinutes: Int,
        periods: List<TimetablePeriod>,
        onDone: (newId: Int) -> Unit = {}
    ) = viewModelScope.launch {
        if (timetableId <= 0) {
            val newId = timetableRepo.insertTimetable(
                Timetable(name = name, sameDuration = sameDuration, durationMinutes = durationMinutes)
            ).toInt()
            val withId = periods.map { it.copy(timetableId = newId) }
            timetableRepo.replacePeriods(newId, withId)
            onDone(newId)
        } else {
            val existing = timetableRepo.getTimetableById(timetableId) ?: return@launch
            timetableRepo.updateTimetable(
                existing.copy(
                    name = name,
                    sameDuration = sameDuration,
                    durationMinutes = durationMinutes
                )
            )
            val withId = periods.map { it.copy(timetableId = timetableId) }
            timetableRepo.replacePeriods(timetableId, withId)
            onDone(timetableId)
        }
    }

    fun deleteTimetable(id: Int) = viewModelScope.launch {
        timetableRepo.deleteTimetable(id)
        val schedules = repo.getAllSchedulesSync()
        schedules.filter { it.timetableId == id }.forEach { schedule ->
            repo.updateSchedule(schedule.copy(timetableId = 0))
        }
    }
}

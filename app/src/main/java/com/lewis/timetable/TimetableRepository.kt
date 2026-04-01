package com.lewis.timetable

import androidx.lifecycle.LiveData

class TimetableRepository(private val dao: TimetableDao) {

    val allTimetables: LiveData<List<Timetable>> = dao.getAllTimetables()

    suspend fun getTimetableById(id: Int) = dao.getTimetableById(id)

    suspend fun insertTimetable(timetable: Timetable): Long = dao.insertTimetable(timetable)

    suspend fun updateTimetable(timetable: Timetable) = dao.updateTimetable(timetable)

    suspend fun deleteTimetable(id: Int) {
        dao.deletePeriodsForTimetable(id)
        dao.deleteTimetableById(id)
    }

    fun getPeriodsForTimetable(timetableId: Int): LiveData<List<TimetablePeriod>> =
        dao.getPeriodsForTimetable(timetableId)

    suspend fun getPeriodsForTimetableSync(timetableId: Int) =
        dao.getPeriodsForTimetableSync(timetableId)

    suspend fun replacePeriods(timetableId: Int, periods: List<TimetablePeriod>) {
        dao.deletePeriodsForTimetable(timetableId)
        dao.insertPeriods(periods)
    }
}

package com.lewis.timetable

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TimetableDao {

    @Query("SELECT * FROM timetables ORDER BY createdAt DESC")
    fun getAllTimetables(): LiveData<List<Timetable>>

    @Query("SELECT * FROM timetables WHERE id = :id")
    suspend fun getTimetableById(id: Int): Timetable?

    @Insert
    suspend fun insertTimetable(timetable: Timetable): Long

    @Update
    suspend fun updateTimetable(timetable: Timetable)

    @Query("DELETE FROM timetables WHERE id = :id")
    suspend fun deleteTimetableById(id: Int)

    @Query("SELECT * FROM timetable_periods WHERE timetableId = :timetableId ORDER BY periodNumber ASC")
    fun getPeriodsForTimetable(timetableId: Int): LiveData<List<TimetablePeriod>>

    @Query("SELECT * FROM timetable_periods WHERE timetableId = :timetableId ORDER BY periodNumber ASC")
    suspend fun getPeriodsForTimetableSync(timetableId: Int): List<TimetablePeriod>

    @Insert
    suspend fun insertPeriods(periods: List<TimetablePeriod>)

    @Query("DELETE FROM timetable_periods WHERE timetableId = :timetableId")
    suspend fun deletePeriodsForTimetable(timetableId: Int)
}

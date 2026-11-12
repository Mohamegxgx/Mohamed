package com.example.domain.engine

import com.example.domain.model.TimeFilter
import com.example.domain.model.TimeRange
import java.util.Calendar

object DateRangeResolver {

    fun resolve(
        filter: TimeFilter,
        nowMillis: Long = System.currentTimeMillis(),
        customRange: TimeRange? = null
    ): TimeRange {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (filter) {
            TimeFilter.TODAY -> {
                val start = startOfDay(cal)
                val end = endOfDay(cal)
                TimeRange(start, end)
            }
            TimeFilter.YESTERDAY -> {
                val yesterday = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
                TimeRange(startOfDay(yesterday), endOfDay(yesterday))
            }
            TimeFilter.THIS_WEEK -> {
                val weekStart = (cal.clone() as Calendar).apply {
                    firstDayOfWeek = Calendar.SATURDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
                    // If today is before Saturday in default locale week, step back 7 days
                    if (timeInMillis > nowMillis) add(Calendar.DAY_OF_YEAR, -7)
                }
                val weekEnd = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
                TimeRange(startOfDay(weekStart), endOfDay(weekEnd))
            }
            TimeFilter.CURRENT_MONTH -> {
                val start = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                val end = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                }
                TimeRange(startOfDay(start), endOfDay(end))
            }
            TimeFilter.PREVIOUS_MONTH -> {
                val prev = (cal.clone() as Calendar).apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val end = (prev.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                }
                TimeRange(startOfDay(prev), endOfDay(end))
            }
            TimeFilter.CURRENT_YEAR -> {
                val start = (cal.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val end = (cal.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.DECEMBER)
                    set(Calendar.DAY_OF_MONTH, 31)
                }
                TimeRange(startOfDay(start), endOfDay(end))
            }
            TimeFilter.PREVIOUS_YEAR -> {
                val prevYear = (cal.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
                val start = (prevYear.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val end = (prevYear.clone() as Calendar).apply {
                    set(Calendar.MONTH, Calendar.DECEMBER)
                    set(Calendar.DAY_OF_MONTH, 31)
                }
                TimeRange(startOfDay(start), endOfDay(end))
            }
            TimeFilter.ALL_TIME -> TimeRange(0L, Long.MAX_VALUE / 2)
            TimeFilter.CUSTOM -> customRange ?: resolve(TimeFilter.CURRENT_MONTH, nowMillis)
        }
    }

    fun currentYearMonth(nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return String.format(java.util.Locale.US, "%04d-%02d", year, month)
    }

    fun previousYearMonth(yearMonth: String): String {
        val parts = yearMonth.split("-")
        if (parts.size != 2) return yearMonth
        var year = parts[0].toIntOrNull() ?: return yearMonth
        var month = parts[1].toIntOrNull() ?: return yearMonth
        month -= 1
        if (month <= 0) {
            month = 12
            year -= 1
        }
        return String.format(java.util.Locale.US, "%04d-%02d", year, month)
    }

    fun monthRange(yearMonth: String): TimeRange {
        val parts = yearMonth.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val end = (start.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        return TimeRange(startOfDay(start), endOfDay(end))
    }

    fun startOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return startOfDay(cal)
    }

    private fun startOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }
}

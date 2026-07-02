package com.thesmallmarket.arrumacomigo.data.local

import androidx.room.TypeConverter
import com.thesmallmarket.arrumacomigo.data.entity.Priority
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import com.thesmallmarket.arrumacomigo.data.entity.RoomType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Conversores Room para os tipos java.time e enums. Datas/horas viram strings ISO (ordenáveis). */
class Converters {

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter
    fun fromRoomType(value: RoomType): String = value.name

    @TypeConverter
    fun toRoomType(value: String): RoomType = RoomType.fromName(value)

    @TypeConverter
    fun fromRecurrence(value: Recurrence): String = value.name

    @TypeConverter
    fun toRecurrence(value: String): Recurrence = Recurrence.fromName(value)

    @TypeConverter
    fun fromPriority(value: Priority): String = value.name

    @TypeConverter
    fun toPriority(value: String): Priority = Priority.fromName(value)
}

package io.github.mbsantos97.camerasaovivo.core.time

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formatos brasileiros no fuso do dispositivo (sem java.time, que exige API 26). */
object Formats {
    val ptBR: Locale = Locale.forLanguageTag("pt-BR")

    private fun local(pattern: String): ThreadLocal<SimpleDateFormat> =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat(pattern, ptBR)
        }

    private val hm = local("HH:mm")
    private val hms = local("HH:mm:ss")
    private val dayMonth = local("dd/MM")
    private val fullDate = local("EEEE, d 'de' MMMM")
    private val dateTime = local("dd/MM/yyyy HH:mm:ss")
    private val weekdayShort = local("EEE")
    private val fileStamp = local("yyyyMMdd_HHmmss")

    fun hourMinute(millis: Long): String = hm.get()!!.format(Date(millis))

    fun hourMinuteSecond(millis: Long): String = hms.get()!!.format(Date(millis))

    fun dayMonth(millis: Long): String = dayMonth.get()!!.format(Date(millis))

    fun fullDate(millis: Long): String = fullDate.get()!!.format(Date(millis)).replaceFirstChar { it.uppercase(ptBR) }

    fun dateTime(millis: Long): String = dateTime.get()!!.format(Date(millis))

    fun weekdayShort(millis: Long): String = weekdayShort.get()!!.format(Date(millis)).trimEnd('.').replaceFirstChar { it.uppercase(ptBR) }

    fun fileStamp(millis: Long): String = fileStamp.get()!!.format(Date(millis))

    fun parseFileStamp(text: String): Long? =
        runCatching {
            fileStamp.get()!!.apply { isLenient = false }.parse(text)?.time
        }.getOrNull()

    /** "1,2 GB", "350 MB". */
    fun bytes(value: Long): String {
        val gb = value / 1_073_741_824.0
        if (gb >= 1) return String.format(ptBR, "%.1f GB", gb)
        val mb = value / 1_048_576.0
        return String.format(ptBR, "%.0f MB", mb)
    }

    /** Segundos desde a meia-noite → "hh:mm:ss". */
    fun secondsOfDay(sec: Int): String = String.format(Locale.ROOT, "%02d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
}

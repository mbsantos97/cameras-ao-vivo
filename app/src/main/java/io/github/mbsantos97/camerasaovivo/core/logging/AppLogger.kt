package io.github.mbsantos97.camerasaovivo.core.logging

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Mascara segredos antes de qualquer linha ir para o log (memória, arquivo ou logcat). */
object LogMasker {
    private val secrets = java.util.concurrent.CopyOnWriteArraySet<String>()
    private val patterns =
        listOf(
            Regex("""(\w+://)([^:/@\s]+):([^@\s]+)@""") to "$1$2:***@",
            Regex("""(?i)(<(?:\w+:)?Password[^>]*>)[^<]*(<)""") to "$1***$2",
            Regex("""(?i)(:rtsp-pwd=)\S+""") to "$1***",
            Regex("""(?i)\b(password|senha|pwd|pass)(["']?\s*[=:]\s*["']?)[^\s"',&]+""") to "$1$2***",
            Regex("""(?i)(<(?:\w+:)?SerialNumber>)[^<]*(<)""") to "$1***$2",
            Regex("""(?i)(<(?:\w+:)?HwAddress>)[^<]*(<)""") to "$1***$2",
            Regex("""(?i)(Authorization:\s*\w+\s+)\S.*""") to "$1***",
        )

    /** Registra um segredo conhecido (ex.: senha decifrada) para mascarar onde aparecer. */
    fun register(secret: String) {
        if (secret.length >= 3) secrets += secret
    }

    fun mask(text: String): String {
        var out = text
        for ((re, rep) in patterns) out = re.replace(out, rep)
        for (s in secrets) if (out.contains(s)) out = out.replace(s, "***")
        return out
    }
}

/**
 * Log do app: buffer em memória (últimas 1000 linhas) + arquivo rotativo (2 × 512 KB).
 * Escrita em arquivo numa thread própria (nunca na main thread).
 */
object AppLogger {
    private const val MAX_LINES = 1000
    private const val MAX_FILE_BYTES = 512 * 1024L
    private val buffer = ArrayDeque<String>(MAX_LINES)
    private var dir: File? = null
    private val io: ExecutorService by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "aoVivo-log").apply { isDaemon = true } } }
    private val stamp =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("dd/MM HH:mm:ss.SSS", Locale.ROOT)
        }
    var logcat: Boolean = true

    fun init(logDir: File) {
        dir = logDir
        io.execute { logDir.mkdirs() }
    }

    fun d(
        tag: String,
        msg: String,
    ) = write("D", tag, msg, null)

    fun i(
        tag: String,
        msg: String,
    ) = write("I", tag, msg, null)

    fun w(
        tag: String,
        msg: String,
        t: Throwable? = null,
    ) = write("W", tag, msg, t)

    fun e(
        tag: String,
        msg: String,
        t: Throwable? = null,
    ) = write("E", tag, msg, t)

    private fun write(
        level: String,
        tag: String,
        msg: String,
        t: Throwable?,
    ) {
        val safe = LogMasker.mask(if (t != null) "$msg — ${t.javaClass.simpleName}: ${t.message}" else msg)
        val line = "${stamp.get()!!.format(Date())} $level/$tag: $safe"
        synchronized(buffer) {
            if (buffer.size >= MAX_LINES) buffer.removeFirst()
            buffer.addLast(line)
        }
        if (logcat) {
            runCatching {
                when (level) {
                    "E" -> Log.e("AoVivo/$tag", safe)
                    "W" -> Log.w("AoVivo/$tag", safe)
                    "I" -> Log.i("AoVivo/$tag", safe)
                    else -> Log.d("AoVivo/$tag", safe)
                }
            }
        }
        val d = dir ?: return
        io.execute {
            runCatching {
                val f = File(d, "app.log")
                if (f.length() > MAX_FILE_BYTES) {
                    val old = File(d, "app.1.log")
                    old.delete()
                    f.renameTo(old)
                }
                File(d, "app.log").appendText(line + "\n")
            }
        }
    }

    /** Últimas linhas (já mascaradas). */
    fun lines(): List<String> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        dir?.listFiles()?.forEach { it.delete() }
    }
}

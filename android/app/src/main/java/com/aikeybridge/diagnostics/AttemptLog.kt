package com.aikeybridge.diagnostics

import android.content.Context
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Một lượt chuyển hướng: từ lúc bắt được cửa sổ trợ lý gốc tới khi xong/thất bại. */
data class Attempt(
    val at: Long,
    val flow: String,
    val ok: Boolean,
    val durationMs: Long,
    val detail: String,
    val micFailed: Boolean = false,
)

data class DayBucket(
    val label: String,
    val total: Int,
    val failed: Int,
    val today: Boolean,
)

data class AttemptSummary(
    val total: Int,
    val ok: Int,
    val failed: Int,
    val micFailed: Int,
    val avgMs: Long?,
    val today: Int,
    val failedToday: Int,
    val last: Attempt?,
    val days: List<DayBucket>,
) {
    val rate: Int? get() = if (total == 0) null else ok * 100 / total
}

object AttemptStats {

    private val weekdayLabels = mapOf(
        Calendar.MONDAY to "T2",
        Calendar.TUESDAY to "T3",
        Calendar.WEDNESDAY to "T4",
        Calendar.THURSDAY to "T5",
        Calendar.FRIDAY to "T6",
        Calendar.SATURDAY to "T7",
        Calendar.SUNDAY to "CN",
    )

    /** Tổng hợp [days] ngày gần nhất, tính cả hôm nay. */
    fun summarize(attempts: List<Attempt>, now: Long, days: Int = 7): AttemptSummary {
        val todayStart = startOfDay(now)
        val windowStart = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis
        val recent = attempts.filter { it.at in windowStart..now }
        val today = recent.filter { it.at >= todayStart }

        val buckets = (0 until days).map { offset ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = windowStart
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val start = cal.timeInMillis
            val label = weekdayLabels[cal.get(Calendar.DAY_OF_WEEK)].orEmpty()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val inDay = recent.filter { it.at >= start && it.at < cal.timeInMillis }
            DayBucket(
                label = label,
                total = inDay.size,
                failed = inDay.count { !it.ok },
                today = start == todayStart,
            )
        }

        return AttemptSummary(
            total = recent.size,
            ok = recent.count { it.ok },
            failed = recent.count { !it.ok },
            micFailed = recent.count { it.micFailed },
            avgMs = recent.filter { it.ok }.map { it.durationMs }.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            today = today.size,
            failedToday = today.count { !it.ok },
            last = attempts.maxByOrNull { it.at },
            days = buckets,
        )
    }

    fun startOfDay(at: Long): Long = Calendar.getInstance().apply {
        timeInMillis = at
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** Lịch sử lượt chạy, lưu file nhỏ trong bộ nhớ app để biểu đồ 7 ngày sống qua reboot. */
object AttemptLog {

    private const val FILE_NAME = "attempts.tsv"
    private const val MAX_ATTEMPTS = 500

    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()

    @Volatile
    private var file: File? = null

    private val _attempts = MutableStateFlow<List<Attempt>>(emptyList())
    val attempts: StateFlow<List<Attempt>> = _attempts.asStateFlow()

    fun init(context: Context) {
        if (file != null) return
        synchronized(lock) {
            if (file != null) return
            val target = File(context.applicationContext.filesDir, FILE_NAME)
            file = target
            io.execute {
                val loaded = runCatching {
                    if (target.exists()) target.readLines().mapNotNull(::decode) else emptyList()
                }.getOrDefault(emptyList())
                _attempts.update { current ->
                    (loaded + current)
                        .distinctBy { Triple(it.at, it.flow, it.detail) }
                        .takeLast(MAX_ATTEMPTS)
                }
                persist()
            }
        }
    }

    fun record(attempt: Attempt) {
        _attempts.update { current ->
            if (current.lastOrNull()?.let { it.at == attempt.at && it.flow == attempt.flow && it.detail == attempt.detail } == true) {
                current
            } else {
                (current + attempt).takeLast(MAX_ATTEMPTS)
            }
        }
        persist()
    }

    fun clear() {
        _attempts.value = emptyList()
        persist()
    }

    private fun persist() {
        val target = file ?: return
        val snapshot = _attempts.value
        io.execute {
            runCatching { target.writeText(snapshot.joinToString("\n", transform = ::encode)) }
        }
    }

    private fun clean(value: String) = value.replace('\t', ' ').replace('\n', ' ')

    private fun encode(a: Attempt) =
        listOf(a.at, clean(a.flow), if (a.ok) 1 else 0, a.durationMs, clean(a.detail), if (a.micFailed) 1 else 0)
            .joinToString("\t")

    private fun decode(line: String): Attempt? {
        val p = line.split('\t')
        if (p.size < 6) return null
        return Attempt(
            at = p[0].toLongOrNull() ?: return null,
            flow = p[1],
            ok = p[2] == "1",
            durationMs = p[3].toLongOrNull() ?: 0,
            detail = p[4],
            micFailed = p[5] == "1",
        )
    }
}

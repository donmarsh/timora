package org.marshsoft.timora.domain

/** 90 -> "1:30", 3700 -> "1:01:40", 5 -> "0:05". Negative values clamp to 0. */
fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Formats milliseconds rounding UP to whole seconds (countdown semantics). */
fun formatRemainingMs(ms: Long): String = formatDuration(((ms.coerceAtLeast(0) + 999) / 1000).toInt())

package com.nayakacctv.ui.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object Formatters {

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups])
    }

    fun formatIDR(amount: Long): String {
        val localeID = Locale("in", "ID")
        val formatter = NumberFormat.getCurrencyInstance(localeID)
        formatter.maximumFractionDigits = 0
        return formatter.format(amount).replace("Rp", "Rp ")
    }

    fun formatIsoDate(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "-"
        return try {
            val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            isoParser.timeZone = TimeZone.getTimeZone("UTC")
            val date = isoParser.parse(isoString) ?: return isoString

            val outFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID"))
            outFormatter.timeZone = TimeZone.getDefault()
            outFormatter.format(date)
        } catch (e: Exception) {
            isoString
        }
    }
}

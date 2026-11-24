package com.example.domain

import java.math.BigDecimal
import kotlin.math.abs

/**
 * Pure integer paise arithmetic and Indian Rupee (INR) formatting.
 * Never uses Double or Float for financial amounts.
 */
object Money {
    const val CURRENCY_INR = "INR"
    private val DECIMAL_INPUT_REGEX = Regex("^\\d+(?:\\.\\d{1,2})?$")
    private const val MAX_PAISE = 100_000_000_000L // ₹1,000 crore cap for simulator sanity

    /**
     * Parses a user-entered INR decimal string (e.g. "100", "1500.5", "1500.50") into integer paise.
     * Rejects negative values, >2 decimal places, scientific notation, and non-numeric strings.
     */
    fun parseInrToPaise(input: String, requirePositive: Boolean = true): Result<Long> {
        val trimmed = input.trim().replace(",", "")
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Amount is required."))
        }
        if (!DECIMAL_INPUT_REGEX.matches(trimmed)) {
            return Result.failure(
                IllegalArgumentException("Enter a valid non-negative amount with at most 2 decimal places.")
            )
        }
        return try {
            val bd = BigDecimal(trimmed)
            val paise = bd.movePointRight(2).longValueExact()
            if (requirePositive && paise <= 0L) {
                Result.failure(IllegalArgumentException("Amount must be greater than ₹0.00."))
            } else if (paise > MAX_PAISE) {
                Result.failure(IllegalArgumentException("Amount exceeds single-transaction simulator limit."))
            } else {
                Result.success(paise)
            }
        } catch (_: ArithmeticException) {
            Result.failure(IllegalArgumentException("Amount is out of supported 64-bit paise range."))
        }
    }

    /**
     * Formats integer paise using Indian numbering system (e.g. 150050 -> "₹1,500.50", 12345678 -> "₹1,23,456.78").
     */
    fun formatInr(paise: Long, includeSign: Boolean = false): String {
        val isNegative = paise < 0L
        val absPaise = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else abs(paise)
        val rupees = absPaise / 100L
        val fraction = absPaise % 100L
        val groupedRupees = formatIndianGrouping(rupees)
        val fractionStr = fraction.toString().padStart(2, '0')
        val sign = when {
            isNegative -> "-"
            includeSign && paise > 0L -> "+"
            else -> ""
        }
        return "${sign}₹$groupedRupees.$fractionStr"
    }

    fun formatCompactInr(paise: Long): String {
        val absRupees = abs(paise) / 100L
        val sign = if (paise < 0L) "-" else ""
        return when {
            absRupees >= 10_000_000L -> "${sign}₹${absRupees / 10_000_000L}.${((absRupees % 10_000_000L) / 1_000_000L)}Cr"
            absRupees >= 100_000L -> "${sign}₹${absRupees / 100_000L}.${((absRupees % 100_000L) / 10_000L)}L"
            absRupees >= 1_000L -> "${sign}₹${absRupees / 1_000L}.${((absRupees % 1_000L) / 100L)}K"
            else -> formatInr(paise)
        }
    }

    private fun formatIndianGrouping(rupees: Long): String {
        val digits = rupees.toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val remaining = digits.dropLast(3)
        val sb = StringBuilder()
        var idx = remaining.length
        while (idx > 0) {
            val start = maxOf(0, idx - 2)
            if (sb.isNotEmpty()) sb.insert(0, ',')
            sb.insert(0, remaining.substring(start, idx))
            idx = start
        }
        return "$sb,$lastThree"
    }
}

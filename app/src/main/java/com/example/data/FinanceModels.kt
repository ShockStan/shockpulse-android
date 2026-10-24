package com.example.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FinanceCategories {
  const val GROCERIES = "Groceries"
  const val DINING = "Dining"
  const val UTILITIES_BILLS = "Utilities & Bills"
  const val SHOPPING = "Shopping"
  const val TRANSIT = "Transit"
  const val SUBSCRIPTIONS = "Subscriptions"
  const val HEALTHCARE = "Healthcare"
  const val ENTERTAINMENT = "Entertainment"
  const val INCOME = "Income"

  val ALL = listOf(
    GROCERIES,
    DINING,
    UTILITIES_BILLS,
    SHOPPING,
    TRANSIT,
    SUBSCRIPTIONS,
    HEALTHCARE,
    ENTERTAINMENT,
    INCOME
  )

  val EXPENSE_CATEGORIES = ALL.filter { it != INCOME }
}

@IgnoreExtraProperties
data class TransactionRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val amountCents: Long = 0L,
  val type: String = "EXPENSE", // EXPENSE, INCOME, BILL_PAID
  val status: String = "APPROVED", // PENDING, APPROVED, REJECTED
  val category: String = FinanceCategories.SHOPPING,
  val paymentMethodId: String = "",
  val paymentMethodLabel: String = "Unassigned",
  val sourceType: String = "MANUAL", // MANUAL, SMS, EMAIL
  val linkedMessageId: String = "",
  val linkedMessageSnippet: String = "",
  val linkedSender: String = "",
  val itemsBoughtSummary: String = "",
  val billPaid: Boolean = false,
  val billReference: String = "",
  val transactionDateMillis: Long = 0L,
  val monthKey: String = "",
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PaymentMethodRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val institution: String = "",
  val methodType: String = "CREDIT_CARD", // CREDIT_CARD, DEBIT_CARD, BANK_ACCOUNT, DIGITAL_WALLET
  val lastFourDigits: String = "",
  val networkOrRouting: String = "Visa",
  val limitOrBalanceCents: Long = 0L,
  val colorHex: String = "#0F4C81",
  val defaultMethod: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  fun displayBadge(): String {
    val suffix = if (lastFourDigits.isNotBlank()) " ••$lastFourDigits" else ""
    return "$institution $name$suffix".trim()
  }
}

@IgnoreExtraProperties
data class CategoryBudgetRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val category: String = FinanceCategories.GROCERIES,
  val monthlyLimitCents: Long = 50000L,
  val alertThresholdPercent: Long = 80L,
  val monthKey: String = "",
  val alertsEnabled: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ParsedMessageRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val sourceType: String = "SMS", // SMS, EMAIL
  val sender: String = "",
  val subject: String = "",
  val body: String = "",
  val detectedAmountCents: Long = 0L,
  val detectedMerchant: String = "",
  val detectedCategory: String = FinanceCategories.SHOPPING,
  val detectedLastFour: String = "",
  val detectedType: String = "EXPENSE", // EXPENSE, INCOME, BILL_PAID
  val detectedItems: String = "",
  val linkedTransactionId: String = "",
  val approvalStatus: String = "PENDING", // PENDING, APPROVED, REJECTED
  val receivedAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class BudgetAlertLevel {
  NORMAL,
  WARNING,
  EXCEEDED
}

data class CategoryBudgetStatus(
  val budget: CategoryBudgetRecord,
  val spentCents: Long,
  val usagePercent: Int,
  val alertLevel: BudgetAlertLevel,
  val remainingCents: Long
)

enum class BaseCurrency(val code: String, val symbol: String, val displayName: String) {
  INR("INR", "₹", "Indian Rupee (₹)"),
  USD("USD", "$", "US Dollar ($)"),
  EUR("EUR", "€", "Euro (€)"),
  GBP("GBP", "£", "British Pound (£)"),
  CAD("CAD", "C$", "Canadian Dollar (C$)"),
  AUD("AUD", "A$", "Australian Dollar (A$)")
}

object CurrencyFormatter {
  var activeCurrency by mutableStateOf(BaseCurrency.INR)

  fun formatCents(cents: Long): String {
    val absCents = kotlin.math.abs(cents)
    val units = absCents / 100
    val rem = absCents % 100
    val sym = activeCurrency.symbol
    val prefix = if (cents < 0) "-$sym" else sym
    return String.format(Locale.US, "%s%,d.%02d", prefix, units, rem)
  }

  fun currentMonthKey(millis: Long = System.currentTimeMillis()): String {
    val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
    return sdf.format(Date(millis))
  }

  fun formatMonthDisplay(monthKey: String): String {
    return try {
      val parser = SimpleDateFormat("yyyy-MM", Locale.US)
      val formatter = SimpleDateFormat("MMMM yyyy", Locale.US)
      val date = parser.parse(monthKey)
      if (date != null) formatter.format(date) else monthKey
    } catch (e: Exception) {
      monthKey
    }
  }

  fun formatShortDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US)
    return sdf.format(Date(millis))
  }
}

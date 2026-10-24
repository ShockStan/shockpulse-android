package com.example.data

import java.util.Locale
import kotlin.math.roundToLong

data class ExtractionResult(
  val sourceType: String,
  val sender: String,
  val subject: String,
  val body: String,
  val amountCents: Long,
  val merchant: String,
  val category: String,
  val lastFourDigits: String,
  val transactionType: String, // EXPENSE, INCOME, BILL_PAID
  val itemsSummary: String,
  val billReference: String,
  val detectedBalanceOrLimitCents: Long? = null
)

data class PaymentMethodImpact(
  val updatedBalanceOrLimitCents: Long,
  val shouldPersistBalanceUpdate: Boolean,
  val flagAlertMessage: String? = null,
  val isFlaggedWarning: Boolean = false
)

data class SampleMessageTemplate(
  val sourceType: String,
  val sender: String,
  val subject: String,
  val body: String
)

object MessageParserEngine {

  private val amountRegexes = listOf(
    Regex("""(?:INR|₹|Rs\.?|USD|\$|EUR|£)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
    Regex("""([0-9]{1,3}(?:,[0-9]{2,3})*\.[0-9]{2})\s*(?:INR|₹|Rs\.?|USD|EUR|charged|debited|paid|billed)""", RegexOption.IGNORE_CASE)
  )

  private val lastFourRegexes = listOf(
    Regex("""(?:ending(?:\s+in)?|card|acct|account|a/c|xx|\*\*|••)\s*[-#*•xX]*([0-9]{4})\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(?:visa|mastercard|amex|checking|savings)\s*[-#*•xX]+([0-9]{4})\b""", RegexOption.IGNORE_CASE)
  )

  private val billRefRegex = Regex("""(?:Ref(?:\s*No\.?|\s*ID)?|Invoice(?:\s*#)?|Bill\s*ID|Confirmation(?:\s*#)?)\s*[:#-]?\s*([A-Z0-9-]{4,24})""", RegexOption.IGNORE_CASE)

  private val itemsLineRegex = Regex("""(?:Items|Order\s+Summary|Purchased|Line\s+Items|Plan)\s*:\s*([^\n.]+)""", RegexOption.IGNORE_CASE)

  private val balanceOrLimitRegex = Regex(
    """(?:Avl\.?\s*Bal(?:ance)?|Available\s+Bal(?:ance)?|Acct\.?\s*Bal(?:ance)?|Updated\s+Bal(?:ance)?|Closing\s+Bal(?:ance)?|Bal(?:ance)?|Avl\.?\s*Limit|Available\s+(?:Credit\s+)?Limit)\s*(?:is|:|-)?\s*(?:INR|₹|Rs\.?|USD|\$|EUR|£)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
    RegexOption.IGNORE_CASE
  )

  fun extractBalanceOrLimitCents(text: String): Long? {
    if (text.isBlank()) return null
    val match = balanceOrLimitRegex.find(text) ?: return null
    val raw = match.groupValues.getOrNull(1)?.replace(",", "") ?: return null
    val parsed = raw.toDoubleOrNull() ?: return null
    return if (parsed >= 0.0) (parsed * 100.0).roundToLong() else null
  }

  /**
   * Computes the updated bank balance or credit card utilization when a transaction is added/approved,
   * and generates a flag alert if a credit card limit (>=80% or >100%) or bank balance is breached.
   */
  fun evaluatePaymentMethodImpact(
    method: PaymentMethodRecord,
    transactionAmountCents: Long,
    transactionType: String,
    existingApprovedTransactions: List<TransactionRecord>,
    linkedMessageText: String = ""
  ): PaymentMethodImpact {
    val parsedMsgBalance = extractBalanceOrLimitCents(linkedMessageText)

    return if (method.methodType == "CREDIT_CARD") {
      // Credit card: limitOrBalanceCents is Credit Limit (unless message explicitly reports a new credit limit)
      val currentCardSpend = existingApprovedTransactions
        .filter {
          it.status == "APPROVED" &&
            it.type != "INCOME" &&
            (it.paymentMethodId == method.id ||
              (method.lastFourDigits.isNotBlank() && it.paymentMethodLabel.contains(method.lastFourDigits)))
        }
        .sumOf { it.amountCents }

      val addedSpend = if (transactionType == "INCOME") 0L else transactionAmountCents
      val newTotalSpend = currentCardSpend + addedSpend
      val limit = method.limitOrBalanceCents.coerceAtLeast(1L)
      val usagePct = ((newTotalSpend * 100L) / limit).toInt()
      val remainingCredit = limit - newTotalSpend

      val flagMsg = when {
        newTotalSpend > limit ->
          "⚠️ Credit Limit Exceeded on ${method.displayBadge()}: Spent ${CurrencyFormatter.formatCents(newTotalSpend)} / Limit ${CurrencyFormatter.formatCents(limit)}!"
        usagePct >= 80 ->
          "⚠️ Credit Limit Flag: ${method.displayBadge()} is at $usagePct% of its ${CurrencyFormatter.formatCents(limit)} limit (${CurrencyFormatter.formatCents(remainingCredit)} available)."
        else -> null
      }

      PaymentMethodImpact(
        updatedBalanceOrLimitCents = method.limitOrBalanceCents,
        shouldPersistBalanceUpdate = false,
        flagAlertMessage = flagMsg,
        isFlaggedWarning = flagMsg != null
      )
    } else {
      // Bank Account, Debit Card, or Digital Wallet: limitOrBalanceCents is the live Account Balance
      val currentBal = method.limitOrBalanceCents
      val newBal = when {
        parsedMsgBalance != null -> parsedMsgBalance
        transactionType == "INCOME" -> currentBal + transactionAmountCents
        else -> (currentBal - transactionAmountCents).coerceAtLeast(0L)
      }

      val isOverdrawn = transactionType != "INCOME" && parsedMsgBalance == null && transactionAmountCents > currentBal
      val isLowBalance = newBal in 1..49999L && currentBal >= 50000L // drops below 500.00 units

      val flagMsg = when {
        isOverdrawn ->
          "⚠️ Insufficient Bank Balance Flag: Transaction (${CurrencyFormatter.formatCents(transactionAmountCents)}) exceeds ${method.displayBadge()} balance (${CurrencyFormatter.formatCents(currentBal)})!"
        parsedMsgBalance != null ->
          "Synced ${method.displayBadge()} balance from SMS/Email: ${CurrencyFormatter.formatCents(newBal)}"
        isLowBalance ->
          "⚠️ Low Bank Balance Alert: ${method.displayBadge()} balance is now ${CurrencyFormatter.formatCents(newBal)}."
        else ->
          "Updated ${method.displayBadge()} balance: ${CurrencyFormatter.formatCents(newBal)}"
      }

      PaymentMethodImpact(
        updatedBalanceOrLimitCents = newBal,
        shouldPersistBalanceUpdate = true,
        flagAlertMessage = flagMsg,
        isFlaggedWarning = isOverdrawn || isLowBalance
      )
    }
  }

  fun parseRawMessage(
    sourceType: String,
    sender: String,
    subject: String,
    body: String
  ): ExtractionResult {
    val cleanSender = sender.trim().ifBlank {
      if (sourceType == "EMAIL") "receipts@merchant.com" else "BANK-ALERT"
    }.take(120)
    val cleanSubject = subject.trim().take(200)
    val cleanBody = body.trim().take(1000)
    val combined = "$cleanSubject $cleanBody"
    val lower = combined.lowercase(Locale.US)

    // 1. Parse Amount in Cents
    var amountCents = 0L
    for (regex in amountRegexes) {
      val match = regex.find(combined)
      if (match != null) {
        val rawNumber = match.groupValues[1].replace(",", "")
        val parsedDouble = rawNumber.toDoubleOrNull()
        if (parsedDouble != null && parsedDouble > 0.0) {
          amountCents = (parsedDouble * 100.0).roundToLong()
          break
        }
      }
    }
    if (amountCents <= 0L) {
      amountCents = 2500L // Fallback $25.00 if user pasted text without explicit currency symbol
    }

    // 2. Parse Last 4 Digits of Card/Bank Account
    var lastFour = ""
    for (regex in lastFourRegexes) {
      val match = regex.find(combined)
      if (match != null) {
        lastFour = match.groupValues[1].take(4)
        break
      }
    }

    // 3. Determine Transaction Type (EXPENSE,BILL_PAID,INCOME)
    val transactionType = when {
      lower.contains("salary") ||
        lower.contains("payroll") ||
        lower.contains("direct deposit") ||
        lower.contains("credited to your") ||
        lower.contains("refunded") -> "INCOME"

      lower.contains("bill paid") ||
        lower.contains("utility bill") ||
        lower.contains("electric bill") ||
        lower.contains("invoice") ||
        lower.contains("autopay") ||
        lower.contains("internet bill") ||
        lower.contains("statement paid") ||
        lower.contains("subscription renewal") -> "BILL_PAID"

      else -> "EXPENSE"
    }

    // 4. Extract Merchant / Payee
    val merchant = extractMerchant(cleanSender, cleanSubject, cleanBody, transactionType).take(120)

    // 5. Classify Category
    val category = classifyCategory(lower, merchant.lowercase(Locale.US), transactionType)

    // 6. Extract Items Bought or Bill Reference
    val itemsMatch = itemsLineRegex.find(combined)
    val itemsSummary = when {
      itemsMatch != null -> itemsMatch.groupValues[1].trim().take(500)
      cleanSubject.isNotBlank() && sourceType == "EMAIL" -> cleanSubject.take(500)
      else -> ""
    }

    val refMatch = billRefRegex.find(combined)
    val billReference = refMatch?.groupValues?.getOrNull(1)?.trim()?.take(120) ?: ""
    val detectedBalance = extractBalanceOrLimitCents(combined)

    return ExtractionResult(
      sourceType = if (sourceType == "EMAIL") "EMAIL" else "SMS",
      sender = cleanSender,
      subject = cleanSubject,
      body = cleanBody,
      amountCents = amountCents,
      merchant = merchant,
      category = category,
      lastFourDigits = lastFour,
      transactionType = transactionType,
      itemsSummary = itemsSummary,
      billReference = billReference,
      detectedBalanceOrLimitCents = detectedBalance
    )
  }

  private fun extractMerchant(
    sender: String,
    subject: String,
    body: String,
    transactionType: String
  ): String {
    val combined = "$subject $body"
    val patterns = listOf(
      Regex("""(?:at|to|from|merchant|biller|payee)\s+([A-Z][A-Za-z0-9&'\-\s]{2,32}?)(?:\s+(?:on|for|using|via|card|ending|with|ref|\.|,|$))"""),
      Regex("""(?:at|to|from)\s+([A-Z0-9][A-Za-z0-9&'\-\s]{2,28})""", RegexOption.IGNORE_CASE)
    )
    for (pattern in patterns) {
      val match = pattern.find(combined)
      if (match != null) {
        val candidate = match.groupValues[1].trim().trimEnd('.', ',', ';', '-')
        if (candidate.length >= 2 && !candidate.equals("your", ignoreCase = true)) {
          return candidate
        }
      }
    }
    if (subject.isNotBlank()) {
      return subject
        .replace(Regex("""^(?:Receipt|Invoice|Payment\s+Confirmation|Alert)\s*(?:from|for|:)\s*""", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { sender }
    }
    return if (transactionType == "INCOME") "Direct Deposit" else sender
  }

  private fun classifyCategory(lowerText: String, lowerMerchant: String, transactionType: String): String {
    if (transactionType == "INCOME") return FinanceCategories.INCOME
    val s = "$lowerText $lowerMerchant"
    return when {
      s.contains("whole foods") || s.contains("trader joe") || s.contains("grocery") ||
        s.contains("supermarket") || s.contains("mart") || s.contains("produce") ||
        s.contains("organic") || s.contains("costco") -> FinanceCategories.GROCERIES

      s.contains("coffee") || s.contains("starbucks") || s.contains("restaurant") ||
        s.contains("bistro") || s.contains("cafe") || s.contains("doordash") ||
        s.contains("uber eats") || s.contains("dining") || s.contains("pizza") ||
        s.contains("sushi") -> FinanceCategories.DINING

      s.contains("con edison") || s.contains("electric") || s.contains("water bill") ||
        s.contains("verizon") || s.contains("comcast") || s.contains("xfinity") ||
        s.contains("utility") || s.contains("power") || s.contains("gas bill") ||
        s.contains("internet") -> FinanceCategories.UTILITIES_BILLS

      s.contains("icloud") || s.contains("netflix") || s.contains("spotify") ||
        s.contains("youtube") || s.contains("subscription") || s.contains("membership") ||
        s.contains("adobe") || s.contains("chatgpt") -> FinanceCategories.SUBSCRIPTIONS

      s.contains("uber") || s.contains("lyft") || s.contains("metro") ||
        s.contains("shell") || s.contains("chevron") || s.contains("parking") ||
        s.contains("transit") || s.contains("airlines") || s.contains("fuel") -> FinanceCategories.TRANSIT

      s.contains("pharmacy") || s.contains("cvs") || s.contains("walgreens") ||
        s.contains("clinic") || s.contains("dental") || s.contains("hospital") ||
        s.contains("medical") -> FinanceCategories.HEALTHCARE

      s.contains("cinema") || s.contains("amc") || s.contains("concert") ||
        s.contains("ticket") || s.contains("steam") || s.contains("playstation") -> FinanceCategories.ENTERTAINMENT

      transactionType == "BILL_PAID" -> FinanceCategories.UTILITIES_BILLS
      else -> FinanceCategories.SHOPPING
    }
  }

  val realisticInboxBatch: List<SampleMessageTemplate> = listOf(
    SampleMessageTemplate(
      sourceType = "SMS",
      sender = "CHASE-ALERT",
      subject = "Card Charge Alert",
      body = "Chase Sapphire Alert: $96.40 charged at Whole Foods Market on card ending 4821. Items: Organic Honeycrisp Apples, Wild Salmon, Oat Milk x2."
    ),
    SampleMessageTemplate(
      sourceType = "EMAIL",
      sender = "billing@coned.com",
      subject = "Bill Paid Confirmation - Con Edison Electric",
      body = "Thank you! Your monthly electric utility bill of ₹142.80 was paid via Autopay from Checking account ending 9044. Avl Bal: ₹8,282.20. Invoice #CE-88412. Items: Residential Electricity 610 kWh."
    ),
    SampleMessageTemplate(
      sourceType = "EMAIL",
      sender = "orders@amazon.com",
      subject = "Receipt from Amazon.com",
      body = "Order charged ₹68.50 on Amex Gold card ending 3109 at Amazon Marketplace. Items: Anker USB-C GaN Charger; Braided Cable 2-Pack. Ref #AMZ-9941."
    ),
    SampleMessageTemplate(
      sourceType = "SMS",
      sender = "AMEX-3109",
      subject = "Dining Charge Alert",
      body = "American Express: ₹54.25 spent at Blue Bottle Coffee & Roastery on card ending 3109. Items: Single Origin Pour Over x2, Almond Croissant."
    ),
    SampleMessageTemplate(
      sourceType = "EMAIL",
      sender = "no-reply@spotify.com",
      subject = "Invoice for Spotify Duo Subscription",
      body = "Subscription renewal bill paid: ₹16.99 charged to Visa card ending 4821 at Spotify. Items: Spotify Premium Duo Monthly Plan. Invoice #SP-442910."
    ),
    SampleMessageTemplate(
      sourceType = "SMS",
      sender = "BOFA-ACH",
      subject = "Payroll Direct Deposit",
      body = "Bank of America: Direct deposit of ₹3,450.00 from ACME TECH PAYROLL credited to your Checking account ending 9044. Avl Bal: ₹11,732.20. Ref #PAY-202610."
    )
  )
}

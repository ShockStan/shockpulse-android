package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.ShockPulseMainLayout
import com.example.ShockPulseSection
import com.example.data.BudgetAlertLevel
import com.example.data.CategoryBudgetRecord
import com.example.data.CategoryBudgetStatus
import com.example.data.CurrencyFormatter
import com.example.data.FinanceCategories
import com.example.data.GeneratedPdfResult
import com.example.data.MessageParserEngine
import com.example.data.ParsedMessageRecord
import com.example.data.PaymentMethodRecord
import com.example.data.PdfReportGenerator
import com.example.data.TransactionRecord
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interactive ShockPulse UI workspace rendered immediately on launch before Google Sign-In
 * completes so the full Dashboard, Approvals, Transactions, Wallet, and Reports UI is visible
 * and interactive without making unauthenticated Firestore calls.
 * Tapping the Account / Sign-In action opens the Credential Manager Google Sign-In screen.
 */
@Composable
fun ShockPulseLocalWorkspace(
  initialSharedText: String = "",
  onOpenGoogleSignIn: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val currentMonth = remember { CurrencyFormatter.currentMonthKey() }

  var currentSection by rememberSaveable { mutableStateOf(ShockPulseSection.DASHBOARD) }
  if (currentSection != ShockPulseSection.DASHBOARD) {
    BackHandler {
      currentSection = ShockPulseSection.DASHBOARD
    }
  }

  var selectedMonthKey by rememberSaveable { mutableStateOf(currentMonth) }
  var selectedCategoryFilter by rememberSaveable { mutableStateOf("ALL") }
  var bannerMessage by rememberSaveable { mutableStateOf<String?>(null) }
  var lastExportedPdf by remember { mutableStateOf<GeneratedPdfResult?>(null) }
  var isBusy by remember { mutableStateOf(false) }

  var paymentMethods by remember {
    mutableStateOf(
      listOf(
        PaymentMethodRecord(
          id = "pm_chase_4821",
          name = "Sapphire Reserve",
          institution = "Chase",
          methodType = "CREDIT_CARD",
          lastFourDigits = "4821",
          networkOrRouting = "Visa Infinite",
          limitOrBalanceCents = 1500000L,
          colorHex = "#0B3B2E",
          defaultMethod = true
        ),
        PaymentMethodRecord(
          id = "pm_amex_3109",
          name = "Gold Card",
          institution = "American Express",
          methodType = "CREDIT_CARD",
          lastFourDigits = "3109",
          networkOrRouting = "Amex",
          limitOrBalanceCents = 1000000L,
          colorHex = "#78350F",
          defaultMethod = false
        ),
        PaymentMethodRecord(
          id = "pm_bofa_9044",
          name = "Advantage Checking",
          institution = "Bank of America",
          methodType = "BANK_ACCOUNT",
          lastFourDigits = "9044",
          networkOrRouting = "ACH Checking",
          limitOrBalanceCents = 842500L,
          colorHex = "#1E3A8A",
          defaultMethod = false
        )
      )
    )
  }

  var parsedMessages by remember {
    val now = System.currentTimeMillis()
    mutableStateOf(
      MessageParserEngine.realisticInboxBatch.mapIndexed { idx, sample ->
        val extracted = MessageParserEngine.parseRawMessage(
          sourceType = sample.sourceType,
          sender = sample.sender,
          subject = sample.subject,
          body = sample.body
        )
        ParsedMessageRecord(
          id = "msg_${idx + 1}",
          sourceType = sample.sourceType,
          sender = sample.sender,
          subject = sample.subject,
          body = sample.body,
          detectedMerchant = extracted.merchant,
          detectedAmountCents = extracted.amountCents,
          detectedCategory = extracted.category,
          detectedLastFour = extracted.lastFourDigits,
          detectedType = extracted.transactionType,
          detectedItems = extracted.itemsSummary,
          linkedTransactionId = "tx_${idx + 1}",
          receivedAtMillis = now - idx * 3_600_000L
        )
      }
    )
  }

  var transactions by remember {
    val now = System.currentTimeMillis()
    mutableStateOf(
      listOf(
        TransactionRecord(
          id = "tx_1",
          title = "Whole Foods Market",
          amountCents = 8450L,
          type = "EXPENSE",
          status = "APPROVED",
          category = FinanceCategories.GROCERIES,
          paymentMethodId = "pm_chase_4821",
          paymentMethodLabel = "Chase Sapphire Reserve ••4821",
          sourceType = "SMS",
          linkedMessageId = "msg_1",
          linkedMessageSnippet = "Chase Alert: Your Visa card ending 4821 was charged $84.50 at WHOLE FOODS MARKET.",
          linkedSender = "CHASE-ALERTS",
          itemsBoughtSummary = "Organic Produce, Wild Salmon, Cold Brew Coffee",
          billPaid = false,
          billReference = "AUTH-99310",
          transactionDateMillis = now - 3_600_000L,
          monthKey = currentMonth,
          notes = "Weekly groceries approved from SMS"
        ),
        TransactionRecord(
          id = "tx_2",
          title = "Con Edison Electric",
          amountCents = 14280L,
          type = "BILL_PAID",
          status = "APPROVED",
          category = FinanceCategories.UTILITIES_BILLS,
          paymentMethodId = "pm_bofa_9044",
          paymentMethodLabel = "Bank of America Advantage Checking ••9044",
          sourceType = "EMAIL",
          linkedMessageId = "msg_2",
          linkedMessageSnippet = "Bill Paid Confirmation: Your monthly electric utility bill of $142.80 was paid from Checking account ending 9044.",
          linkedSender = "billing@coned.com",
          itemsBoughtSummary = "Residential Electricity 610 kWh",
          billPaid = true,
          billReference = "CE-88412",
          transactionDateMillis = now - 7_200_000L,
          monthKey = currentMonth,
          notes = "Monthly electric bill auto-debit"
        ),
        TransactionRecord(
          id = "tx_3",
          title = "Acme Tech Payroll",
          amountCents = 425000L,
          type = "INCOME",
          status = "APPROVED",
          category = FinanceCategories.INCOME,
          paymentMethodId = "pm_bofa_9044",
          paymentMethodLabel = "Bank of America Advantage Checking ••9044",
          sourceType = "EMAIL",
          linkedMessageId = "msg_6",
          linkedMessageSnippet = "Direct Deposit Received: $4,250.00 credited to Checking account ending 9044 from ACME TECH PAYROLL.",
          linkedSender = "alerts@bankofamerica.com",
          itemsBoughtSummary = "Bi-weekly Salary Direct Deposit",
          billPaid = false,
          billReference = "DD-77401",
          transactionDateMillis = now - 14_400_000L,
          monthKey = currentMonth,
          notes = "Salary deposit"
        ),
        TransactionRecord(
          id = "tx_4",
          title = "Apple Store Online",
          amountCents = 24900L,
          type = "EXPENSE",
          status = "PENDING",
          category = FinanceCategories.SHOPPING,
          paymentMethodId = "pm_amex_3109",
          paymentMethodLabel = "American Express Gold Card ••3109",
          sourceType = "EMAIL",
          linkedMessageId = "msg_3",
          linkedMessageSnippet = "Receipt for your order #W94810229: Charge of $249.00 on Amex card ending 3109 at APPLE STORE ONLINE.",
          linkedSender = "no_reply@email.apple.com",
          itemsBoughtSummary = "AirPods Pro (2nd Gen) USB-C",
          billPaid = false,
          billReference = "W94810229",
          transactionDateMillis = now - 1_800_000L,
          monthKey = currentMonth,
          notes = "Parsed from email receipt — awaiting approval"
        ),
        TransactionRecord(
          id = "tx_5",
          title = "Sweetgreen",
          amountCents = 3840L,
          type = "EXPENSE",
          status = "PENDING",
          category = FinanceCategories.DINING,
          paymentMethodId = "pm_amex_3109",
          paymentMethodLabel = "American Express Gold Card ••3109",
          sourceType = "SMS",
          linkedMessageId = "msg_4",
          linkedMessageSnippet = "Amex Alert: $38.40 spent on Gold Card ending 3109 at SWEETGREEN NOMAD.",
          linkedSender = "AMEX-NOTIFY",
          itemsBoughtSummary = "Harvest Bowl x2, Hibiscus Iced Tea",
          billPaid = false,
          billReference = "SG-4120",
          transactionDateMillis = now - 900_000L,
          monthKey = currentMonth,
          notes = "Parsed from SMS — awaiting approval"
        )
      )
    )
  }

  var budgets by remember {
    mutableStateOf(
      listOf(
        CategoryBudgetRecord(
          id = "bud_utilities",
          category = FinanceCategories.UTILITIES_BILLS,
          monthlyLimitCents = 16000L,
          alertThresholdPercent = 75L,
          monthKey = currentMonth,
          alertsEnabled = true
        ),
        CategoryBudgetRecord(
          id = "bud_groceries",
          category = FinanceCategories.GROCERIES,
          monthlyLimitCents = 40000L,
          alertThresholdPercent = 75L,
          monthKey = currentMonth,
          alertsEnabled = true
        ),
        CategoryBudgetRecord(
          id = "bud_shopping",
          category = FinanceCategories.SHOPPING,
          monthlyLimitCents = 30000L,
          alertThresholdPercent = 80L,
          monthKey = currentMonth,
          alertsEnabled = true
        )
      )
    )
  }

  val pendingTransactions = remember(transactions) {
    transactions.filter { it.status == "PENDING" }
  }

  val budgetStatuses = remember(transactions, budgets, selectedMonthKey) {
    val approvedExpenses = transactions.filter {
      it.status == "APPROVED" &&
        it.type != "INCOME" &&
        (it.monthKey == selectedMonthKey || selectedMonthKey.isBlank())
    }
    budgets.map { budget ->
      val spent = approvedExpenses
        .filter { it.category.equals(budget.category, ignoreCase = true) }
        .sumOf { it.amountCents }
      val limit = budget.monthlyLimitCents.coerceAtLeast(1L)
      val pct = ((spent * 100L) / limit).toInt()
      val level = when {
        !budget.alertsEnabled -> BudgetAlertLevel.NORMAL
        pct >= 100 -> BudgetAlertLevel.EXCEEDED
        pct >= budget.alertThresholdPercent.toInt() -> BudgetAlertLevel.WARNING
        else -> BudgetAlertLevel.NORMAL
      }
      CategoryBudgetStatus(
        budget = budget,
        spentCents = spent,
        usagePercent = pct,
        alertLevel = level,
        remainingCents = limit - spent
      )
    }
  }

  val availableMonths = remember(transactions, selectedMonthKey) {
    (listOf(currentMonth, "2026-10", "2026-09") + transactions.map { it.monthKey })
      .filter { it.isNotBlank() }
      .distinct()
      .sortedDescending()
  }

  // Modal Dialog States
  var showParseDialog by rememberSaveable { mutableStateOf(initialSharedText.isNotBlank()) }
  var showManualTxDialog by rememberSaveable { mutableStateOf(false) }
  var showAddPaymentMethodDialog by rememberSaveable { mutableStateOf(false) }
  var showSetBudgetDialog by rememberSaveable { mutableStateOf(false) }
  var transactionToApprove by remember { mutableStateOf<TransactionRecord?>(null) }
  var transactionToLinkMessage by remember { mutableStateOf<TransactionRecord?>(null) }
  var paymentMethodToUpdateBalance by remember { mutableStateOf<PaymentMethodRecord?>(null) }

  val savePdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf")
  ) { targetUri ->
    if (targetUri != null) {
      scope.launch {
        val result = withContext(Dispatchers.IO) {
          PdfReportGenerator.generateMonthlyReportPdf(
            context = context,
            monthKey = selectedMonthKey,
            transactions = transactions,
            budgets = budgetStatuses,
            paymentMethods = paymentMethods
          )
        }
        lastExportedPdf = result
        PdfReportGenerator.copyReportToUri(context, result.file, targetUri)
        bannerMessage = "Saved PDF Report to device: ${result.file.name}"
      }
    }
  }

  LaunchedEffect(initialSharedText) {
    if (initialSharedText.isNotBlank()) {
      showParseDialog = true
    }
  }

  ShockPulseMainLayout(
    currentSection = currentSection,
    onSelectSection = { currentSection = it },
    currentUserId = "preview",
    userEmail = "Interactive Workspace • Tap Account to Sign In",
    isBusy = isBusy,
    bannerMessage = bannerMessage,
    onClearBanner = { bannerMessage = null },
    selectedMonthKey = selectedMonthKey,
    availableMonths = availableMonths,
    onSelectMonth = { selectedMonthKey = it },
    selectedCategoryFilter = selectedCategoryFilter,
    onSelectCategoryFilter = { selectedCategoryFilter = it },
    transactions = transactions,
    pendingTransactions = pendingTransactions,
    paymentMethods = paymentMethods,
    parsedMessages = parsedMessages,
    budgetStatuses = budgetStatuses,
    lastExportedPdf = lastExportedPdf,
    onOpenParseDialog = { showParseDialog = true },
    onOpenManualTransaction = { showManualTxDialog = true },
    onOpenAddPaymentMethod = { showAddPaymentMethodDialog = true },
    onOpenSetBudget = { showSetBudgetDialog = true },
    onOpenAccountDialog = onOpenGoogleSignIn,
    onSignOutClick = onOpenGoogleSignIn,
    onScanSampleInbox = {
      val now = System.currentTimeMillis()
      val newSample = MessageParserEngine.realisticInboxBatch.random()
      val extracted = MessageParserEngine.parseRawMessage(
        sourceType = newSample.sourceType,
        sender = newSample.sender,
        subject = newSample.subject,
        body = newSample.body
      )
      val msgId = "msg_${UUID.randomUUID().toString().take(6)}"
      val txId = "tx_${UUID.randomUUID().toString().take(6)}"
      val matchedPm = paymentMethods.firstOrNull {
        extracted.lastFourDigits.isNotBlank() && it.lastFourDigits == extracted.lastFourDigits
      } ?: paymentMethods.firstOrNull { it.defaultMethod }

      val newMsg = ParsedMessageRecord(
        id = msgId,
        sourceType = newSample.sourceType,
        sender = newSample.sender,
        subject = newSample.subject,
        body = newSample.body,
        detectedMerchant = extracted.merchant,
        detectedAmountCents = extracted.amountCents,
        detectedCategory = extracted.category,
        detectedLastFour = extracted.lastFourDigits,
        detectedType = extracted.transactionType,
        detectedItems = extracted.itemsSummary,
        linkedTransactionId = txId,
        receivedAtMillis = now
      )
      val newTx = TransactionRecord(
        id = txId,
        title = extracted.merchant,
        amountCents = extracted.amountCents,
        type = extracted.transactionType,
        status = "PENDING",
        category = extracted.category,
        paymentMethodId = matchedPm?.id.orEmpty(),
        paymentMethodLabel = matchedPm?.displayBadge() ?: "Card ••${extracted.lastFourDigits}",
        sourceType = newSample.sourceType,
        linkedMessageId = msgId,
        linkedMessageSnippet = newSample.body,
        linkedSender = newSample.sender,
        itemsBoughtSummary = extracted.itemsSummary,
        billPaid = extracted.transactionType == "BILL_PAID",
        billReference = extracted.billReference,
        transactionDateMillis = now,
        monthKey = selectedMonthKey
      )
      parsedMessages = listOf(newMsg) + parsedMessages
      transactions = listOf(newTx) + transactions
      bannerMessage = "Scanned ${newSample.sourceType} from ${newSample.sender} into Approvals Queue!"
    },
    onExportAndSharePdf = {
      scope.launch {
        isBusy = true
        try {
          val result = withContext(Dispatchers.IO) {
            PdfReportGenerator.generateMonthlyReportPdf(
              context = context,
              monthKey = selectedMonthKey,
              transactions = transactions,
              budgets = budgetStatuses,
              paymentMethods = paymentMethods
            )
          }
          lastExportedPdf = result
          bannerMessage =
            "PDF Report generated for ${result.monthDisplay} (${result.transactionCount} transactions): ${result.file.name}"
          PdfReportGenerator.sharePdfReport(context, result.file, result.monthDisplay)
        } finally {
          isBusy = false
        }
      }
    },
    onSavePdfToDevice = {
      savePdfLauncher.launch("ShockPulse_Report_${selectedMonthKey}.pdf")
    },
    onReviewTransaction = { tx -> transactionToApprove = tx },
    onQuickApproveTransaction = { tx ->
      val matchedPm = paymentMethods.firstOrNull { it.id == tx.paymentMethodId }
        ?: paymentMethods.firstOrNull { it.defaultMethod }
      val impact = matchedPm?.let { pm ->
        MessageParserEngine.evaluatePaymentMethodImpact(
          method = pm,
          transactionAmountCents = tx.amountCents,
          transactionType = tx.type,
          existingApprovedTransactions = transactions,
          linkedMessageText = tx.linkedMessageSnippet
        )
      }
      if (matchedPm != null && impact != null && impact.shouldPersistBalanceUpdate) {
        paymentMethods = paymentMethods.map {
          if (it.id == matchedPm.id) it.copy(limitOrBalanceCents = impact.updatedBalanceOrLimitCents) else it
        }
      }
      transactions = transactions.map {
        if (it.id == tx.id) {
          it.copy(
            status = "APPROVED",
            notes = if (impact?.isFlaggedWarning == true && !impact.flagAlertMessage.isNullOrBlank()) {
              listOf(it.notes, "[Flag: ${impact.flagAlertMessage}]").filter { s -> s.isNotBlank() }.joinToString(" • ")
            } else {
              it.notes
            }
          )
        } else {
          it
        }
      }
      bannerMessage = buildString {
        append("Approved & recorded '${tx.title}' to Ledger!")
        if (!impact?.flagAlertMessage.isNullOrBlank()) {
          append(" • ${impact?.flagAlertMessage}")
        }
      }
    },
    onRejectTransaction = { tx ->
      transactions = transactions.filterNot { it.id == tx.id }
      bannerMessage = "Dismissed pending transaction '${tx.title}'."
    },
    onLinkMessageClick = { tx -> transactionToLinkMessage = tx },
    onDeleteTransaction = { id ->
      transactions = transactions.filterNot { it.id == id }
      bannerMessage = "Transaction deleted."
    },
    onEditPaymentMethodBalance = { pm -> paymentMethodToUpdateBalance = pm },
    onDeletePaymentMethod = { id ->
      paymentMethods = paymentMethods.filterNot { it.id == id }
      bannerMessage = "Payment method removed."
    },
    onDeleteBudget = { id ->
      budgets = budgets.filterNot { it.id == id }
      bannerMessage = "Category budget alert removed."
    }
  )

  if (showParseDialog) {
    ParseMessageDialog(
      initialSharedText = initialSharedText,
      onDismiss = { showParseDialog = false },
      onSubmit = { sourceType, sender, subject, body ->
        val now = System.currentTimeMillis()
        val extracted = MessageParserEngine.parseRawMessage(
          sourceType = sourceType,
          sender = sender,
          subject = subject,
          body = body
        )
        val msgId = "msg_${UUID.randomUUID().toString().take(6)}"
        val txId = "tx_${UUID.randomUUID().toString().take(6)}"
        val matchedPm = paymentMethods.firstOrNull {
          extracted.lastFourDigits.isNotBlank() && it.lastFourDigits == extracted.lastFourDigits
        } ?: paymentMethods.firstOrNull { it.defaultMethod }

        val newMsg = ParsedMessageRecord(
          id = msgId,
          sourceType = sourceType,
          sender = sender,
          subject = subject,
          body = body,
          detectedMerchant = extracted.merchant,
          detectedAmountCents = extracted.amountCents,
          detectedCategory = extracted.category,
          detectedLastFour = extracted.lastFourDigits,
          detectedType = extracted.transactionType,
          detectedItems = extracted.itemsSummary,
          linkedTransactionId = txId,
          receivedAtMillis = now
        )
        val newTx = TransactionRecord(
          id = txId,
          title = extracted.merchant,
          amountCents = extracted.amountCents,
          type = extracted.transactionType,
          status = "PENDING",
          category = extracted.category,
          paymentMethodId = matchedPm?.id.orEmpty(),
          paymentMethodLabel = matchedPm?.displayBadge() ?: "Unassigned Method",
          sourceType = sourceType,
          linkedMessageId = msgId,
          linkedMessageSnippet = body,
          linkedSender = sender,
          itemsBoughtSummary = extracted.itemsSummary,
          billPaid = extracted.transactionType == "BILL_PAID",
          billReference = extracted.billReference,
          transactionDateMillis = now,
          monthKey = selectedMonthKey
        )
        parsedMessages = listOf(newMsg) + parsedMessages
        transactions = listOf(newTx) + transactions
        if (matchedPm != null && matchedPm.methodType != "CREDIT_CARD" && extracted.detectedBalanceOrLimitCents != null) {
          paymentMethods = paymentMethods.map {
            if (it.id == matchedPm.id) it.copy(limitOrBalanceCents = extracted.detectedBalanceOrLimitCents) else it
          }
          bannerMessage = "Parsed $sourceType into Approvals & synced ${matchedPm.institution} balance to ${CurrencyFormatter.formatCents(extracted.detectedBalanceOrLimitCents)}!"
        } else {
          bannerMessage = "Parsed $sourceType into Pending Approvals Queue!"
        }
      }
    )
  }

  val activeApproveTx = transactionToApprove
  if (activeApproveTx != null) {
    ReviewAndApproveDialog(
      transaction = activeApproveTx,
      paymentMethods = paymentMethods,
      onDismiss = { transactionToApprove = null },
      onApprove = { pm, title, category, amountCents, items, billPaid, billRef, notes ->
        val finalType = if (billPaid && activeApproveTx.type != "INCOME") "BILL_PAID" else activeApproveTx.type
        val impact = pm?.let { method ->
          MessageParserEngine.evaluatePaymentMethodImpact(
            method = method,
            transactionAmountCents = amountCents,
            transactionType = finalType,
            existingApprovedTransactions = transactions,
            linkedMessageText = activeApproveTx.linkedMessageSnippet
          )
        }
        if (pm != null && impact != null && impact.shouldPersistBalanceUpdate) {
          paymentMethods = paymentMethods.map {
            if (it.id == pm.id) it.copy(limitOrBalanceCents = impact.updatedBalanceOrLimitCents) else it
          }
        }
        val finalNotes = if (impact?.isFlaggedWarning == true && !impact.flagAlertMessage.isNullOrBlank()) {
          listOf(notes, "[Flag: ${impact.flagAlertMessage}]").filter { it.isNotBlank() }.joinToString(" • ")
        } else {
          notes
        }
        transactions = transactions.map {
          if (it.id == activeApproveTx.id) {
            it.copy(
              title = title.ifBlank { it.title },
              category = category,
              amountCents = amountCents,
              itemsBoughtSummary = items,
              billPaid = billPaid,
              type = finalType,
              billReference = billRef,
              notes = finalNotes,
              paymentMethodId = pm?.id ?: it.paymentMethodId,
              paymentMethodLabel = pm?.displayBadge() ?: it.paymentMethodLabel,
              status = "APPROVED"
            )
          } else {
            it
          }
        }
        bannerMessage = buildString {
          append("Approved & recorded '${title.ifBlank { activeApproveTx.title }}'!")
          if (!impact?.flagAlertMessage.isNullOrBlank()) {
            append(" • ${impact?.flagAlertMessage}")
          }
        }
      },
      onReject = {
        transactions = transactions.filterNot { it.id == activeApproveTx.id }
        bannerMessage = "Dismissed pending transaction."
      }
    )
  }

  if (showManualTxDialog) {
    AddManualTransactionDialog(
      paymentMethods = paymentMethods,
      parsedMessages = parsedMessages,
      onDismiss = { showManualTxDialog = false },
      onSave = { title, cents, type, cat, pm, items, billPaid, billRef, linkedMsg, snippet, sender, notes ->
        val now = System.currentTimeMillis()
        val msgSnippet = linkedMsg?.body ?: snippet
        val msgSender = linkedMsg?.sender ?: sender
        val finalType = if (billPaid && type != "INCOME") "BILL_PAID" else type
        val impact = pm?.let { method ->
          MessageParserEngine.evaluatePaymentMethodImpact(
            method = method,
            transactionAmountCents = cents,
            transactionType = finalType,
            existingApprovedTransactions = transactions,
            linkedMessageText = msgSnippet
          )
        }
        if (pm != null && impact != null && impact.shouldPersistBalanceUpdate) {
          paymentMethods = paymentMethods.map {
            if (it.id == pm.id) it.copy(limitOrBalanceCents = impact.updatedBalanceOrLimitCents) else it
          }
        }
        val finalNotes = if (impact?.isFlaggedWarning == true && !impact.flagAlertMessage.isNullOrBlank()) {
          listOf(notes, "[Flag: ${impact.flagAlertMessage}]").filter { it.isNotBlank() }.joinToString(" • ")
        } else {
          notes
        }
        val newTx = TransactionRecord(
          id = "tx_${UUID.randomUUID().toString().take(6)}",
          title = title,
          amountCents = cents,
          type = finalType,
          status = "APPROVED",
          category = cat,
          paymentMethodId = pm?.id.orEmpty(),
          paymentMethodLabel = pm?.displayBadge() ?: "Manual / Cash",
          sourceType = linkedMsg?.sourceType ?: if (msgSnippet.isNotBlank()) "EMAIL" else "MANUAL",
          linkedMessageId = linkedMsg?.id.orEmpty(),
          linkedMessageSnippet = msgSnippet,
          linkedSender = msgSender,
          itemsBoughtSummary = items,
          billPaid = billPaid || type == "BILL_PAID",
          billReference = billRef,
          transactionDateMillis = now,
          monthKey = selectedMonthKey,
          notes = finalNotes
        )
        transactions = listOf(newTx) + transactions
        bannerMessage = buildString {
          append("Added transaction '${newTx.title}' to Ledger!")
          if (!impact?.flagAlertMessage.isNullOrBlank()) {
            append(" • ${impact?.flagAlertMessage}")
          }
        }
      }
    )
  }

  val activeLinkTx = transactionToLinkMessage
  if (activeLinkTx != null) {
    LinkMessageToTransactionDialog(
      transaction = activeLinkTx,
      parsedMessages = parsedMessages,
      onDismiss = { transactionToLinkMessage = null },
      onLinkSelected = { msg ->
        transactions = transactions.map {
          if (it.id == activeLinkTx.id) {
            it.copy(
              sourceType = msg.sourceType,
              linkedMessageId = msg.id,
              linkedMessageSnippet = msg.body,
              linkedSender = msg.sender
            )
          } else {
            it
          }
        }
        bannerMessage = "Linked ${msg.sourceType} from ${msg.sender} to '${activeLinkTx.title}'!"
      }
    )
  }

  if (showAddPaymentMethodDialog) {
    AddPaymentMethodDialog(
      onDismiss = { showAddPaymentMethodDialog = false },
      onSave = { name, inst, type, last4, net, limitCents, colorHex, isDefault ->
        val newPm = PaymentMethodRecord(
          id = "pm_${UUID.randomUUID().toString().take(6)}",
          name = name,
          institution = inst,
          methodType = type,
          lastFourDigits = last4,
          networkOrRouting = net,
          limitOrBalanceCents = limitCents,
          colorHex = colorHex,
          defaultMethod = isDefault
        )
        paymentMethods = paymentMethods + newPm
        bannerMessage = "Added $inst $name to Wallet!"
      }
    )
  }

  val activeBalancePm = paymentMethodToUpdateBalance
  if (activeBalancePm != null) {
    UpdatePaymentMethodBalanceDialog(
      paymentMethod = activeBalancePm,
      onDismiss = { paymentMethodToUpdateBalance = null },
      onSaveBalanceOrLimit = { newBalanceOrLimitCents ->
        paymentMethods = paymentMethods.map {
          if (it.id == activeBalancePm.id) it.copy(limitOrBalanceCents = newBalanceOrLimitCents) else it
        }
        val label = if (activeBalancePm.methodType == "CREDIT_CARD") "credit limit" else "balance"
        bannerMessage = "Updated ${activeBalancePm.institution} ${activeBalancePm.name} $label to ${CurrencyFormatter.formatCents(newBalanceOrLimitCents)}!"
      }
    )
  }

  if (showSetBudgetDialog) {
    SetCategoryBudgetDialog(
      onDismiss = { showSetBudgetDialog = false },
      onSave = { category, limitCents, thresholdPct, enabled ->
        val filtered = budgets.filterNot { it.category.equals(category, ignoreCase = true) }
        val newBudget = CategoryBudgetRecord(
          id = "bud_${category.lowercase()}",
          category = category,
          monthlyLimitCents = limitCents,
          alertThresholdPercent = thresholdPct,
          monthKey = selectedMonthKey,
          alertsEnabled = enabled
        )
        budgets = filtered + newBudget
        bannerMessage =
          "Budget alert set for $category (${CurrencyFormatter.formatCents(limitCents)} @ $thresholdPct%)!"
      }
    )
  }
}

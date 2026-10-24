package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BudgetAlertLevel
import com.example.data.CategoryBudgetRecord
import com.example.data.CategoryBudgetStatus
import com.example.data.CurrencyFormatter
import com.example.data.FinanceCategories
import com.example.data.FinanceRepository
import com.example.data.GeneratedPdfResult
import com.example.data.MessageParserEngine
import com.example.data.ParsedMessageRecord
import com.example.data.PaymentMethodRecord
import com.example.data.PdfReportGenerator
import com.example.data.TransactionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

class FinanceViewModel(
  private val repository: FinanceRepository,
  private val currentUserId: String
) : ViewModel() {

  private val _selectedMonthKey = MutableStateFlow(CurrencyFormatter.currentMonthKey())
  val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

  private val _selectedCategoryFilter = MutableStateFlow("ALL")
  val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

  private val _bannerMessage = MutableStateFlow<String?>(null)
  val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

  private val _lastExportedPdf = MutableStateFlow<GeneratedPdfResult?>(null)
  val lastExportedPdf: StateFlow<GeneratedPdfResult?> = _lastExportedPdf.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  val transactionsState: StateFlow<UiState<List<TransactionRecord>>> =
    repository.observeTransactions(currentUserId)
      .map<List<TransactionRecord>, UiState<List<TransactionRecord>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing transactions", error)
        emit(UiState.Error(error.message ?: "Failed to load transactions"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
      )

  val paymentMethodsState: StateFlow<UiState<List<PaymentMethodRecord>>> =
    repository.observePaymentMethods(currentUserId)
      .map<List<PaymentMethodRecord>, UiState<List<PaymentMethodRecord>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing payment methods", error)
        emit(UiState.Error(error.message ?: "Failed to load payment methods"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
      )

  val budgetsState: StateFlow<UiState<List<CategoryBudgetRecord>>> =
    repository.observeBudgets(currentUserId)
      .map<List<CategoryBudgetRecord>, UiState<List<CategoryBudgetRecord>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing budgets", error)
        emit(UiState.Error(error.message ?: "Failed to load budgets"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
      )

  val parsedMessagesState: StateFlow<UiState<List<ParsedMessageRecord>>> =
    repository.observeParsedMessages(currentUserId)
      .map<List<ParsedMessageRecord>, UiState<List<ParsedMessageRecord>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing parsed messages", error)
        emit(UiState.Error(error.message ?: "Failed to load messages"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
      )

  val categoryBudgetStatuses: StateFlow<List<CategoryBudgetStatus>> = combine(
    transactionsState,
    budgetsState,
    _selectedMonthKey
  ) { txUi, budgetUi, monthKey ->
    val txs = (txUi as? UiState.Success)?.data ?: emptyList()
    val budgets = (budgetUi as? UiState.Success)?.data ?: emptyList()
    val approvedExpenses = txs.filter {
      it.status == "APPROVED" &&
        it.type != "INCOME" &&
        (it.monthKey == monthKey || monthKey.isBlank())
    }
    budgets
      .filter { it.monthKey == monthKey || it.monthKey.isBlank() }
      .map { budget ->
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
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
    initialValue = emptyList()
  )

  fun setSelectedMonthKey(monthKey: String) {
    _selectedMonthKey.value = monthKey
  }

  fun setSelectedCategoryFilter(category: String) {
    _selectedCategoryFilter.value = category
  }

  fun clearBannerMessage() {
    _bannerMessage.value = null
  }

  fun ingestMessageOrEmail(
    sourceType: String,
    sender: String,
    subject: String,
    body: String
  ) {
    if (body.isBlank()) {
      _bannerMessage.value = "Please enter or paste an SMS or Email message body."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val methods = (paymentMethodsState.value as? UiState.Success)?.data ?: emptyList()
      val res = repository.ingestParsedMessageAndQueueTransaction(
        sourceType = sourceType,
        sender = sender,
        subject = subject,
        body = body,
        paymentMethods = methods
      )
      _isBusy.value = false
      res.fold(
        onSuccess = {
          _bannerMessage.value =
            "Parsed $sourceType and added to Pending Approval Queue for your review!"
        },
        onFailure = {
          _bannerMessage.value = "Failed to parse message: ${it.localizedMessage}"
        }
      )
    }
  }

  fun scanAndSeedSampleMessagesAndAccounts() {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val currentMethods = (paymentMethodsState.value as? UiState.Success)?.data ?: emptyList()
        if (currentMethods.isEmpty()) {
          repository.createPaymentMethod(
            PaymentMethodRecord(
              name = "Sapphire Reserve",
              institution = "Chase",
              methodType = "CREDIT_CARD",
              lastFourDigits = "4821",
              networkOrRouting = "Visa Infinite",
              limitOrBalanceCents = 1500000L,
              colorHex = "#0B3B2E",
              defaultMethod = true
            )
          )
          repository.createPaymentMethod(
            PaymentMethodRecord(
              name = "Gold Card",
              institution = "American Express",
              methodType = "CREDIT_CARD",
              lastFourDigits = "3109",
              networkOrRouting = "Amex",
              limitOrBalanceCents = 1000000L,
              colorHex = "#78350F",
              defaultMethod = false
            )
          )
          repository.createPaymentMethod(
            PaymentMethodRecord(
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
        }

        val currentBudgets = (budgetsState.value as? UiState.Success)?.data ?: emptyList()
        val monthKey = _selectedMonthKey.value
        if (currentBudgets.isEmpty()) {
          repository.saveCategoryBudget(
            CategoryBudgetRecord(
              category = FinanceCategories.GROCERIES,
              monthlyLimitCents = 40000L,
              alertThresholdPercent = 75L,
              monthKey = monthKey,
              alertsEnabled = true
            )
          )
          repository.saveCategoryBudget(
            CategoryBudgetRecord(
              category = FinanceCategories.UTILITIES_BILLS,
              monthlyLimitCents = 18000L,
              alertThresholdPercent = 75L,
              monthKey = monthKey,
              alertsEnabled = true
            )
          )
          repository.saveCategoryBudget(
            CategoryBudgetRecord(
              category = FinanceCategories.DINING,
              monthlyLimitCents = 20000L,
              alertThresholdPercent = 80L,
              monthKey = monthKey,
              alertsEnabled = true
            )
          )
          repository.saveCategoryBudget(
            CategoryBudgetRecord(
              category = FinanceCategories.SHOPPING,
              monthlyLimitCents = 25000L,
              alertThresholdPercent = 80L,
              monthKey = monthKey,
              alertsEnabled = true
            )
          )
        }

        val refreshedMethods = (paymentMethodsState.value as? UiState.Success)?.data ?: listOf(
          PaymentMethodRecord(
            id = "temp_chase",
            name = "Sapphire Reserve",
            institution = "Chase",
            methodType = "CREDIT_CARD",
            lastFourDigits = "4821",
            defaultMethod = true
          )
        )

        val now = System.currentTimeMillis()
        MessageParserEngine.realisticInboxBatch.forEachIndexed { idx, sample ->
          repository.ingestParsedMessageAndQueueTransaction(
            sourceType = sample.sourceType,
            sender = sample.sender,
            subject = sample.subject,
            body = sample.body,
            paymentMethods = refreshedMethods,
            receivedAtMillis = now - idx * 3600_000L
          )
        }

        _bannerMessage.value =
          "Scanned 6 SMS & Email receipts into your Pending Approval Queue, plus cards & budget alerts!"
      } catch (e: Exception) {
        _bannerMessage.value = "Error scanning messages: ${e.localizedMessage}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun approveTransaction(
    record: TransactionRecord,
    paymentMethod: PaymentMethodRecord?,
    editedTitle: String,
    editedCategory: String,
    editedAmountCents: Long,
    editedItems: String,
    billPaid: Boolean,
    billReference: String,
    notes: String
  ) {
    viewModelScope.launch {
      val existingTxs = (transactionsState.value as? UiState.Success)?.data ?: emptyList()
      val effectiveType = if (billPaid && record.type != "INCOME") "BILL_PAID" else record.type
      val res = repository.approveTransaction(
        record = record,
        paymentMethod = paymentMethod,
        editedTitle = editedTitle,
        editedCategory = editedCategory,
        editedAmountCents = editedAmountCents,
        editedItems = editedItems,
        billPaid = billPaid,
        billReference = billReference,
        notes = notes
      )
      res.fold(
        onSuccess = {
          var statusNote = "Approved & recorded '${editedTitle.ifBlank { record.title }}'!"
          if (paymentMethod != null) {
            val impact = MessageParserEngine.evaluatePaymentMethodImpact(
              method = paymentMethod,
              transactionAmountCents = editedAmountCents,
              transactionType = effectiveType,
              existingApprovedTransactions = existingTxs,
              linkedMessageText = record.linkedMessageSnippet
            )
            if (impact.shouldPersistBalanceUpdate) {
              repository.updatePaymentMethodBalanceOrLimit(
                paymentMethod,
                impact.updatedBalanceOrLimitCents
              )
            }
            if (!impact.flagAlertMessage.isNullOrBlank()) {
              statusNote = "$statusNote • ${impact.flagAlertMessage}"
            }
          }
          _bannerMessage.value = statusNote
        },
        onFailure = {
          _bannerMessage.value = "Could not approve transaction: ${it.localizedMessage}"
        }
      )
    }
  }

  fun rejectTransaction(record: TransactionRecord) {
    viewModelScope.launch {
      val res = repository.rejectTransaction(record)
      res.fold(
        onSuccess = { _bannerMessage.value = "Dismissed pending transaction '${record.title}'." },
        onFailure = { _bannerMessage.value = "Failed to dismiss: ${it.localizedMessage}" }
      )
    }
  }

  fun addManualTransaction(
    title: String,
    amountCents: Long,
    type: String,
    category: String,
    paymentMethod: PaymentMethodRecord?,
    itemsBoughtSummary: String,
    billPaid: Boolean,
    billReference: String,
    linkedMessage: ParsedMessageRecord?,
    customMessageSnippet: String,
    customSender: String,
    notes: String
  ) {
    viewModelScope.launch {
      val existingTxs = (transactionsState.value as? UiState.Success)?.data ?: emptyList()
      val now = System.currentTimeMillis()
      val snippet = when {
        linkedMessage != null -> if (linkedMessage.subject.isNotBlank()) {
          "${linkedMessage.subject}: ${linkedMessage.body}"
        } else {
          linkedMessage.body
        }
        customMessageSnippet.isNotBlank() -> customMessageSnippet
        else -> ""
      }
      val sender = linkedMessage?.sender ?: customSender
      val sourceType = linkedMessage?.sourceType
        ?: if (customMessageSnippet.isNotBlank()) "EMAIL" else "MANUAL"
      val effectiveType = if (billPaid && type != "INCOME") "BILL_PAID" else type

      val record = TransactionRecord(
        title = title,
        amountCents = amountCents,
        type = effectiveType,
        status = "APPROVED",
        category = category,
        paymentMethodId = paymentMethod?.id ?: "",
        paymentMethodLabel = paymentMethod?.displayBadge() ?: "Manual / Cash",
        sourceType = sourceType,
        linkedMessageId = linkedMessage?.id ?: "",
        linkedMessageSnippet = snippet,
        linkedSender = sender,
        itemsBoughtSummary = itemsBoughtSummary,
        billPaid = billPaid || type == "BILL_PAID",
        billReference = billReference,
        transactionDateMillis = now,
        monthKey = _selectedMonthKey.value.ifBlank { CurrencyFormatter.currentMonthKey(now) },
        notes = notes
      )
      val res = repository.createTransaction(record)
      res.fold(
        onSuccess = {
          var statusNote = "Added '${record.title}' to ledger!"
          if (paymentMethod != null) {
            val impact = MessageParserEngine.evaluatePaymentMethodImpact(
              method = paymentMethod,
              transactionAmountCents = amountCents,
              transactionType = effectiveType,
              existingApprovedTransactions = existingTxs,
              linkedMessageText = snippet
            )
            if (impact.shouldPersistBalanceUpdate) {
              repository.updatePaymentMethodBalanceOrLimit(
                paymentMethod,
                impact.updatedBalanceOrLimitCents
              )
            }
            if (!impact.flagAlertMessage.isNullOrBlank()) {
              statusNote = "$statusNote • ${impact.flagAlertMessage}"
            }
          }
          _bannerMessage.value = statusNote
        },
        onFailure = {
          _bannerMessage.value = "Failed to add transaction: ${it.localizedMessage}"
        }
      )
    }
  }

  fun linkMessageToTransaction(transaction: TransactionRecord, message: ParsedMessageRecord) {
    viewModelScope.launch {
      val res = repository.linkMessageToTransaction(transaction, message)
      res.fold(
        onSuccess = {
          _bannerMessage.value =
            "Linked ${message.sourceType} from ${message.sender} to '${transaction.title}'!"
        },
        onFailure = {
          _bannerMessage.value = "Failed to link message: ${it.localizedMessage}"
        }
      )
    }
  }

  fun deleteTransaction(transactionId: String) {
    viewModelScope.launch {
      repository.deleteTransaction(transactionId)
      _bannerMessage.value = "Transaction deleted."
    }
  }

  fun addPaymentMethod(
    name: String,
    institution: String,
    methodType: String,
    lastFourDigits: String,
    networkOrRouting: String,
    limitOrBalanceCents: Long,
    colorHex: String,
    defaultMethod: Boolean
  ) {
    viewModelScope.launch {
      val res = repository.createPaymentMethod(
        PaymentMethodRecord(
          name = name,
          institution = institution,
          methodType = methodType,
          lastFourDigits = lastFourDigits,
          networkOrRouting = networkOrRouting,
          limitOrBalanceCents = limitOrBalanceCents,
          colorHex = colorHex,
          defaultMethod = defaultMethod
        )
      )
      res.fold(
        onSuccess = { _bannerMessage.value = "Added $institution $name to Wallet!" },
        onFailure = { _bannerMessage.value = "Failed to save payment method: ${it.localizedMessage}" }
      )
    }
  }

  fun updatePaymentMethodBalanceOrLimit(
    method: PaymentMethodRecord,
    newBalanceOrLimitCents: Long
  ) {
    viewModelScope.launch {
      val res = repository.updatePaymentMethodBalanceOrLimit(method, newBalanceOrLimitCents)
      val label = if (method.methodType == "CREDIT_CARD") "Credit Limit" else "Account Balance"
      res.fold(
        onSuccess = {
          _bannerMessage.value =
            "Updated ${method.displayBadge()} $label to ${CurrencyFormatter.formatCents(newBalanceOrLimitCents)}!"
        },
        onFailure = {
          _bannerMessage.value = "Failed to update $label: ${it.localizedMessage}"
        }
      )
    }
  }

  fun deletePaymentMethod(methodId: String) {
    viewModelScope.launch {
      repository.deletePaymentMethod(methodId)
      _bannerMessage.value = "Payment method removed."
    }
  }

  fun saveCategoryBudget(
    category: String,
    monthlyLimitCents: Long,
    alertThresholdPercent: Long,
    alertsEnabled: Boolean
  ) {
    viewModelScope.launch {
      val res = repository.saveCategoryBudget(
        CategoryBudgetRecord(
          category = category,
          monthlyLimitCents = monthlyLimitCents,
          alertThresholdPercent = alertThresholdPercent,
          monthKey = _selectedMonthKey.value,
          alertsEnabled = alertsEnabled
        )
      )
      res.fold(
        onSuccess = {
          _bannerMessage.value =
            "Budget alert saved for $category (${CurrencyFormatter.formatCents(monthlyLimitCents)} @ $alertThresholdPercent%)!"
        },
        onFailure = {
          _bannerMessage.value = "Failed to save budget: ${it.localizedMessage}"
        }
      )
    }
  }

  fun deleteCategoryBudget(budgetId: String) {
    viewModelScope.launch {
      repository.deleteCategoryBudget(budgetId)
      _bannerMessage.value = "Category budget removed."
    }
  }

  fun generateAndShareMonthlyPdf(
    context: Context,
    autoShare: Boolean = true,
    onPdfReady: (GeneratedPdfResult) -> Unit = {}
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val txs = (transactionsState.value as? UiState.Success)?.data ?: emptyList()
        val pms = (paymentMethodsState.value as? UiState.Success)?.data ?: emptyList()
        val budgetStats = categoryBudgetStatuses.value
        val monthKey = _selectedMonthKey.value

        val result = withContext(Dispatchers.IO) {
          PdfReportGenerator.generateMonthlyReportPdf(
            context = context,
            monthKey = monthKey,
            transactions = txs,
            budgets = budgetStats,
            paymentMethods = pms
          )
        }
        _lastExportedPdf.value = result
        _bannerMessage.value =
          "PDF Report generated for ${result.monthDisplay} (${result.transactionCount} transactions): ${result.file.name}"
        onPdfReady(result)
        if (autoShare) {
          PdfReportGenerator.sharePdfReport(context, result.file, result.monthDisplay)
        }
      } catch (e: Exception) {
        _bannerMessage.value = "Failed to export PDF: ${e.localizedMessage}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  private companion object {
    const val TAG = "FinanceViewModel"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}

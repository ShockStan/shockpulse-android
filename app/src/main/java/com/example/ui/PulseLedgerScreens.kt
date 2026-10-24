package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BudgetAlertLevel
import com.example.data.CategoryBudgetStatus
import com.example.data.CurrencyFormatter
import com.example.data.FinanceCategories
import com.example.data.GeneratedPdfResult
import com.example.data.ParsedMessageRecord
import com.example.data.PaymentMethodRecord
import com.example.data.TransactionRecord
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.HealthyMint

@Composable
fun OverviewTabContent(
  monthKey: String,
  availableMonths: List<String>,
  onSelectMonth: (String) -> Unit,
  transactions: List<TransactionRecord>,
  budgetStatuses: List<CategoryBudgetStatus>,
  paymentMethods: List<PaymentMethodRecord>,
  onOpenParseDialog: () -> Unit,
  onScanSampleInbox: () -> Unit,
  onGoToApprovalQueue: () -> Unit,
  onExportPdf: () -> Unit,
  onOpenManualTransaction: () -> Unit,
  onReviewTransaction: (TransactionRecord) -> Unit,
  onLinkMessageClick: (TransactionRecord) -> Unit,
  onDeleteTransaction: (String) -> Unit
) {
  val monthTxs = remember(transactions, monthKey) {
    transactions.filter { it.monthKey == monthKey || monthKey.isBlank() }
  }
  val approvedTxs = remember(monthTxs) { monthTxs.filter { it.status == "APPROVED" } }
  val pendingTxs = remember(transactions) { transactions.filter { it.status == "PENDING" } }

  val totalOutflow = remember(approvedTxs) {
    approvedTxs.filter { it.type != "INCOME" }.sumOf { it.amountCents }
  }
  val totalBillsPaid = remember(approvedTxs) {
    approvedTxs.filter { it.billPaid || it.type == "BILL_PAID" }.sumOf { it.amountCents }
  }
  val totalIncome = remember(approvedTxs) {
    approvedTxs.filter { it.type == "INCOME" }.sumOf { it.amountCents }
  }
  val totalBankBalance = remember(paymentMethods) {
    paymentMethods.filter { it.methodType != "CREDIT_CARD" }.sumOf { it.limitOrBalanceCents }
  }
  val triggeredAlerts = remember(budgetStatuses) {
    budgetStatuses.filter { it.alertLevel != BudgetAlertLevel.NORMAL }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("overview_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. Month Selector Row
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        availableMonths.forEach { mKey ->
          FilterChip(
            selected = monthKey == mKey,
            onClick = { onSelectMonth(mKey) },
            label = { Text(CurrencyFormatter.formatMonthDisplay(mKey)) }
          )
        }
      }
    }

    // 2. Clean Monthly Cashflow & Bank Balance Card
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.linearGradient(
                colors = listOf(
                  Color(0xFF062B22),
                  Color(0xFF0E4939),
                  Color(0xFF135E4B)
                )
              )
            )
            .padding(20.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top
            ) {
              Column(
                modifier = Modifier
                  .weight(1f)
                  .padding(end = 8.dp)
              ) {
                Text(
                  text = "${CurrencyFormatter.formatMonthDisplay(monthKey)} Spend",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color(0xFFA7F3D0),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = CurrencyFormatter.formatCents(totalOutflow),
                  style = MaterialTheme.typography.displayMedium,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Surface(
                color = Color(0x3334D399),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text(
                  text = "Bank Bal: ${CurrencyFormatter.formatCents(totalBankBalance)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFFA7F3D0),
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  softWrap = false,
                  modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("dashboard_bank_balance_badge")
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              SummaryMetricPill(
                label = "Bills Paid",
                value = CurrencyFormatter.formatCents(totalBillsPaid),
                accent = Color(0xFFFBBF24),
                modifier = Modifier.weight(1f)
              )
              SummaryMetricPill(
                label = "Income",
                value = CurrencyFormatter.formatCents(totalIncome),
                accent = Color(0xFF34D399),
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }
    }

    // 3. Compact 1-Row Pending Approvals Notice (Only shown if pending items exist)
    if (pendingTxs.isNotEmpty()) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onGoToApprovalQueue() }
            .testTag("pending_queue_banner")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "${pendingTxs.size} pending transactions to review",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.weight(1f)
            )
            TextButton(
              onClick = onGoToApprovalQueue,
              modifier = Modifier.testTag("review_queue_button")
            ) {
              Text("Review")
            }
          }
        }
      }
    }

    // 4. Compact Budget Alert Notice (Only shown if any category crosses alert threshold)
    if (triggeredAlerts.isNotEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "Budget Alert",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Budget Alerts (${triggeredAlerts.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
              )
            }
            triggeredAlerts.forEach { alert ->
              Text(
                text = "${alert.budget.category}: ${CurrencyFormatter.formatCents(alert.spentCents)} / ${CurrencyFormatter.formatCents(alert.budget.monthlyLimitCents)} (${alert.usagePercent}%)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
              )
            }
          }
        }
      }
    }

    // 5. Recent Approved Transactions
    item {
      Text(
        text = "Recent Transactions",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    if (approvedTxs.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Transactions Yet",
          subtitle = "Tap '+ Add Transaction' below to record a purchase or bill.",
          actionLabel = "Add Transaction",
          onAction = onOpenManualTransaction
        )
      }
    } else {
      items(approvedTxs.take(6), key = { "ov_app_${it.id}" }) { tx ->
        ApprovedTransactionCard(
          transaction = tx,
          onLinkMessageClick = { onLinkMessageClick(tx) },
          onDeleteClick = { onDeleteTransaction(tx.id) }
        )
      }
    }
  }
}

@Composable
fun ApprovalQueueTabContent(
  pendingTransactions: List<TransactionRecord>,
  parsedMessages: List<ParsedMessageRecord>,
  onOpenParseDialog: () -> Unit,
  onScanSampleInbox: () -> Unit,
  onEditAndApprove: (TransactionRecord) -> Unit,
  onQuickApprove: (TransactionRecord) -> Unit,
  onReject: (TransactionRecord) -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("approval_queue_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Pending Approvals (${pendingTransactions.size})",
            style = MaterialTheme.typography.titleLarge
          )
          Text(
            text = "Approve parsed SMS & email receipts to add them to your ledger.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
          onClick = onScanSampleInbox,
          modifier = Modifier.testTag("inbox_load_samples_button")
        ) {
          Text("Load Sample")
        }
      }
    }

    if (pendingTransactions.isEmpty()) {
      item {
        EmptyStateCard(
          title = "All Caught Up",
          subtitle = "No pending SMS or email transactions waiting for approval.",
          actionLabel = "Scan SMS / Email",
          onAction = onOpenParseDialog
        )
      }
    } else {
      items(pendingTransactions, key = { "pend_${it.id}" }) { tx ->
        PendingTransactionCard(
          transaction = tx,
          onEditAndApprove = { onEditAndApprove(tx) },
          onQuickApprove = { onQuickApprove(tx) },
          onReject = { onReject(tx) }
        )
      }
    }
  }
}

@Composable
fun LedgerTabContent(
  transactions: List<TransactionRecord>,
  selectedCategory: String,
  onSelectCategory: (String) -> Unit,
  onAddManualClick: () -> Unit,
  onLinkMessageClick: (TransactionRecord) -> Unit,
  onDeleteTransaction: (String) -> Unit
) {
  var filterMode by remember { mutableStateOf("ALL") } // ALL, BILLS, ITEMS

  val approvedTxs = remember(transactions, selectedCategory, filterMode) {
    transactions
      .filter { it.status == "APPROVED" }
      .filter {
        selectedCategory == "ALL" || it.category.equals(selectedCategory, ignoreCase = true)
      }
      .filter {
        when (filterMode) {
          "BILLS" -> it.billPaid || it.type == "BILL_PAID"
          "ITEMS" -> it.itemsBoughtSummary.isNotBlank()
          else -> true
        }
      }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("ledger_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(
          "ALL" to "All (${approvedTxs.size})",
          "BILLS" to "Bills Paid",
          "ITEMS" to "Items Bought"
        ).forEach { (key, label) ->
          FilterChip(
            selected = filterMode == key,
            onClick = { filterMode = key },
            label = { Text(label) }
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = selectedCategory == "ALL",
          onClick = { onSelectCategory("ALL") },
          label = { Text("All Categories") }
        )
        FinanceCategories.ALL.forEach { cat ->
          FilterChip(
            selected = selectedCategory == cat,
            onClick = { onSelectCategory(cat) },
            label = { Text(cat) }
          )
        }
      }
    }

    if (approvedTxs.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Matching Transactions",
          subtitle = "Tap '+ Add Transaction' below to log an expense, bill, or income.",
          actionLabel = "Add Transaction",
          onAction = onAddManualClick
        )
      }
    } else {
      items(approvedTxs, key = { "led_${it.id}" }) { tx ->
        ApprovedTransactionCard(
          transaction = tx,
          onLinkMessageClick = { onLinkMessageClick(tx) },
          onDeleteClick = { onDeleteTransaction(tx.id) }
        )
      }
    }
  }
}

@Composable
fun WalletTabContent(
  paymentMethods: List<PaymentMethodRecord>,
  transactions: List<TransactionRecord>,
  onAddPaymentMethodClick: () -> Unit,
  onEditPaymentMethodBalance: (PaymentMethodRecord) -> Unit = {},
  onDeletePaymentMethod: (String) -> Unit
) {
  val approvedExpenses = remember(transactions) {
    transactions.filter { it.status == "APPROVED" && it.type != "INCOME" }
  }
  val totalBankBalanceCents = remember(paymentMethods) {
    paymentMethods.filter { it.methodType != "CREDIT_CARD" }.sumOf { it.limitOrBalanceCents }
  }
  val totalCreditLimitCents = remember(paymentMethods) {
    paymentMethods.filter { it.methodType == "CREDIT_CARD" }.sumOf { it.limitOrBalanceCents }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("wallet_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "Bank Balance",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Text(
              text = CurrencyFormatter.formatCents(totalBankBalanceCents),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }
        }
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "Credit Limits",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = CurrencyFormatter.formatCents(totalCreditLimitCents),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }

    item {
      Text(
        text = "Cards & Bank Accounts (${paymentMethods.size})",
        style = MaterialTheme.typography.titleLarge
      )
    }

    if (paymentMethods.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Cards or Bank Accounts",
          subtitle = "Add your credit cards or bank accounts to track balances, limits, and spend.",
          actionLabel = "Add Card or Bank",
          onAction = onAddPaymentMethodClick
        )
      }
    } else {
      items(paymentMethods, key = { "pm_${it.id}" }) { pm ->
        val methodSpend = approvedExpenses.filter {
          it.paymentMethodId == pm.id ||
            (pm.lastFourDigits.isNotBlank() && it.paymentMethodLabel.contains(pm.lastFourDigits))
        }.sumOf { it.amountCents }

        val methodTxCount = approvedExpenses.count {
          it.paymentMethodId == pm.id ||
            (pm.lastFourDigits.isNotBlank() && it.paymentMethodLabel.contains(pm.lastFourDigits))
        }

        PaymentMethodVisualCard(
          paymentMethod = pm,
          totalSpentCents = methodSpend,
          transactionCount = methodTxCount,
          onEditBalance = { onEditPaymentMethodBalance(pm) },
          onDelete = { onDeletePaymentMethod(pm.id) }
        )
      }
    }
  }
}

@Composable
fun BudgetsAndPdfTabContent(
  monthKey: String,
  budgetStatuses: List<CategoryBudgetStatus>,
  lastExportedPdf: GeneratedPdfResult?,
  onAddBudgetClick: () -> Unit,
  onDeleteBudget: (String) -> Unit,
  onExportAndSharePdf: () -> Unit,
  onSavePdfToDevice: () -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("budgets_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Monthly PDF Report Exporter Card
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.PictureAsPdf,
              contentDescription = "Monthly PDF Report",
              tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Monthly PDF Report",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
              Text(
                text = CurrencyFormatter.formatMonthDisplay(monthKey),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
              )
            }
          }

          if (lastExportedPdf != null) {
            Text(
              text = "Generated: ${lastExportedPdf.file.name} (${lastExportedPdf.transactionCount} transactions)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = onExportAndSharePdf,
              modifier = Modifier
                .weight(1f)
                .testTag("export_share_pdf_button")
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Share PDF")
            }
            OutlinedButton(
              onClick = onSavePdfToDevice,
              modifier = Modifier
                .weight(1f)
                .testTag("save_pdf_device_button")
            ) {
              Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save PDF")
            }
          }
        }
      }
    }

    // Personalized Category Budget Alerts Section
    item {
      Text(
        text = "Category Budget Alerts (${budgetStatuses.size})",
        style = MaterialTheme.typography.titleLarge
      )
    }

    if (budgetStatuses.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Budget Alerts Set",
          subtitle = "Tap '+ Set Budget Alert' below to configure monthly category spending limits.",
          actionLabel = "Set Budget Alert",
          onAction = onAddBudgetClick
        )
      }
    } else {
      items(budgetStatuses, key = { "bud_${it.budget.id}" }) { status ->
        BudgetAlertStatusCard(
          status = status,
          onDelete = { onDeleteBudget(status.budget.id) }
        )
      }
    }
  }
}

// --- Shared Card Components ---

@Composable
private fun SummaryMetricPill(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    color = Color(0x26FFFFFF),
    shape = RoundedCornerShape(14.dp),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFFCBD5E1),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        color = accent,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun PendingTransactionCard(
  transaction: TransactionRecord,
  onEditAndApprove: () -> Unit,
  onQuickApprove: () -> Unit,
  onReject: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("pending_tx_card_${transaction.id}")
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = transaction.title,
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f)
        )
        Text(
          text = CurrencyFormatter.formatCents(transaction.amountCents),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = if (transaction.type == "INCOME") HealthyMint else MaterialTheme.colorScheme.primary
        )
      }

      Text(
        text = "${transaction.sourceType} • ${transaction.category} • ${transaction.paymentMethodLabel}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (transaction.itemsBoughtSummary.isNotBlank()) {
        Text(
          text = "Items: ${transaction.itemsBoughtSummary}",
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      if (transaction.linkedMessageSnippet.isNotBlank()) {
        Text(
          text = transaction.linkedMessageSnippet,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Button(
          onClick = onQuickApprove,
          modifier = Modifier
            .weight(1f)
            .testTag("quick_approve_${transaction.id}")
        ) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Approve")
        }
        FilledTonalButton(
          onClick = onEditAndApprove,
          modifier = Modifier.testTag("edit_approve_${transaction.id}")
        ) {
          Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Edit")
        }
        IconButton(
          onClick = onReject,
          modifier = Modifier.testTag("quick_reject_${transaction.id}")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss"
          )
        }
      }
    }
  }
}

@Composable
fun ApprovedTransactionCard(
  transaction: TransactionRecord,
  onLinkMessageClick: () -> Unit,
  onDeleteClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("approved_tx_card_${transaction.id}")
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = transaction.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            if (transaction.billPaid || transaction.type == "BILL_PAID") {
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "BILL",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }
          Text(
            text = "${transaction.category} • ${transaction.paymentMethodLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        val amountPrefix = if (transaction.type == "INCOME") "+" else "-"
        val amountColor = if (transaction.type == "INCOME") {
          HealthyMint
        } else {
          MaterialTheme.colorScheme.onSurface
        }
        Text(
          text = "$amountPrefix${CurrencyFormatter.formatCents(transaction.amountCents)}",
          style = MaterialTheme.typography.titleMedium,
          color = amountColor,
          fontWeight = FontWeight.Bold
        )
      }

      if (transaction.itemsBoughtSummary.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.ShoppingBag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = transaction.itemsBoughtSummary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onLinkMessageClick() }
            .padding(vertical = 4.dp, horizontal = 6.dp)
            .testTag("link_msg_${transaction.id}")
        ) {
          Icon(
            imageVector = if (transaction.linkedMessageSnippet.isNotBlank()) Icons.Default.Sms else Icons.Default.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (transaction.linkedMessageSnippet.isNotBlank()) {
              "Linked ${transaction.sourceType}"
            } else {
              "Link Receipt"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
          )
        }

        IconButton(
          onClick = onDeleteClick,
          modifier = Modifier
            .size(32.dp)
            .testTag("delete_tx_${transaction.id}")
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Delete Transaction",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun PaymentMethodVisualCard(
  paymentMethod: PaymentMethodRecord,
  totalSpentCents: Long,
  transactionCount: Int,
  onEditBalance: () -> Unit = {},
  onDelete: () -> Unit
) {
  val baseColor = try {
    Color(android.graphics.Color.parseColor(paymentMethod.colorHex))
  } catch (e: Exception) {
    Color(0xFF0B3B2E)
  }

  val isCreditCard = paymentMethod.methodType == "CREDIT_CARD"
  val limitOrBal = paymentMethod.limitOrBalanceCents
  val creditUsagePct = if (isCreditCard && limitOrBal > 0L) {
    ((totalSpentCents * 100L) / limitOrBal).toInt()
  } else {
    0
  }
  val flagText = when {
    isCreditCard && limitOrBal > 0L && totalSpentCents > limitOrBal ->
      "OVER LIMIT ($creditUsagePct%)"
    isCreditCard && limitOrBal > 0L && creditUsagePct >= 80 ->
      "LIMIT FLAG ($creditUsagePct%)"
    !isCreditCard && limitOrBal < 0L ->
      "OVERDRAWN"
    !isCreditCard && limitOrBal in 1L..49999L ->
      "LOW BALANCE"
    else -> null
  }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("pm_card_${paymentMethod.id}")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.linearGradient(
            colors = listOf(baseColor, baseColor.copy(alpha = 0.78f), Color(0xFF0F172A))
          )
        )
        .padding(18.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              imageVector = if (paymentMethod.methodType == "BANK_ACCOUNT") {
                Icons.Default.AccountBalance
              } else {
                Icons.Default.CreditCard
              },
              contentDescription = null,
              tint = Color(0xFFA7F3D0)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "${paymentMethod.institution} • ${paymentMethod.name}",
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onEditBalance,
              modifier = Modifier
                .size(32.dp)
                .testTag("edit_pm_balance_${paymentMethod.id}")
            ) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Update Balance or Limit",
                tint = Color(0xFFA7F3D0),
                modifier = Modifier.size(18.dp)
              )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
              Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete Payment Method",
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (paymentMethod.lastFourDigits.isNotBlank()) {
              "•••• ${paymentMethod.lastFourDigits}  •  ${paymentMethod.networkOrRouting}"
            } else {
              paymentMethod.networkOrRouting
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE2E8F0)
          )

          if (flagText != null) {
            Surface(
              color = Color(0xFFEF4444).copy(alpha = 0.25f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.WarningAmber,
                  contentDescription = null,
                  tint = Color(0xFFFCA5A5),
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = flagText,
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFFFCA5A5),
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom
        ) {
          Column(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable { onEditBalance() }
          ) {
            Text(
              text = if (isCreditCard) "Credit Limit (Tap to edit)" else "Available Balance (Tap to edit)",
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFF94A3B8)
            )
            Text(
              text = CurrencyFormatter.formatCents(paymentMethod.limitOrBalanceCents),
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = if (isCreditCard && limitOrBal > 0L) {
                "Spent ($creditUsagePct% of limit)"
              } else {
                "Spent ($transactionCount tx)"
              },
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFF94A3B8)
            )
            Text(
              text = CurrencyFormatter.formatCents(totalSpentCents),
              style = MaterialTheme.typography.titleMedium,
              color = Color(0xFFFBBF24)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BudgetAlertStatusCard(
  status: CategoryBudgetStatus,
  onDelete: () -> Unit
) {
  val barColor = when (status.alertLevel) {
    BudgetAlertLevel.EXCEEDED -> AlertCoral
    BudgetAlertLevel.WARNING -> AlertAmber
    BudgetAlertLevel.NORMAL -> HealthyMint
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("budget_card_${status.budget.category}")
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = status.budget.category,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "Remove Budget Alert",
            modifier = Modifier.size(18.dp)
          )
        }
      }

      LinearProgressIndicator(
        progress = { (status.usagePercent / 100f).coerceIn(0f, 1f) },
        color = barColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp))
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${CurrencyFormatter.formatCents(status.spentCents)} / ${CurrencyFormatter.formatCents(status.budget.monthlyLimitCents)} (${status.usagePercent}%)",
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp)
        )
        val badgeText = when (status.alertLevel) {
          BudgetAlertLevel.EXCEEDED -> "Over Limit"
          BudgetAlertLevel.WARNING -> "Alert (>=${status.budget.alertThresholdPercent}%)"
          BudgetAlertLevel.NORMAL -> "${CurrencyFormatter.formatCents(status.remainingCents)} left"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (status.alertLevel != BudgetAlertLevel.NORMAL) {
            Icon(
              imageVector = Icons.Default.WarningAmber,
              contentDescription = "Budget Warning",
              tint = barColor,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
          }
          Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
            color = barColor
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyStateCard(
  title: String,
  subtitle: String,
  actionLabel: String,
  onAction: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Button(onClick = onAction) {
        Text(actionLabel)
      }
    }
  }
}

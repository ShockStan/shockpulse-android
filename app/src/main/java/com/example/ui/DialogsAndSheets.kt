package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.CurrencyFormatter
import com.example.data.FinanceCategories
import com.example.data.MessageParserEngine
import com.example.data.ParsedMessageRecord
import com.example.data.PaymentMethodRecord
import com.example.data.TransactionRecord
import kotlin.math.roundToLong

@Composable
fun ParseMessageDialog(
  initialSharedText: String = "",
  onDismiss: () -> Unit,
  onSubmit: (sourceType: String, sender: String, subject: String, body: String) -> Unit
) {
  val clipboardManager = LocalClipboardManager.current
  var sourceType by remember { mutableStateOf(if (initialSharedText.contains("@")) "EMAIL" else "SMS") }
  var sender by remember { mutableStateOf(if (sourceType == "EMAIL") "receipts@wholefoods.com" else "CHASE-ALERT") }
  var subject by remember { mutableStateOf(if (sourceType == "EMAIL") "Your Receipt & Order Summary" else "Card Charge Alert") }
  var body by remember {
    mutableStateOf(
      initialSharedText.ifBlank {
        "HDFC Alert: ₹784.00 charged at Trader Joe's Market on account ending 9044. Avl Bal: ₹83,466.00. Items: Cold Brew Coffee, Organic Eggs."
      }
    )
  }

  val livePreview = remember(sourceType, sender, subject, body) {
    MessageParserEngine.parseRawMessage(sourceType, sender, subject, body)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Scan / Paste SMS or Email",
        style = MaterialTheme.typography.titleLarge
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = sourceType == "SMS",
            onClick = {
              sourceType = "SMS"
              if (sender.contains("@")) sender = "CHASE-ALERT"
            },
            label = { Text("SMS Message") },
            modifier = Modifier.testTag("chip_source_sms")
          )
          FilterChip(
            selected = sourceType == "EMAIL",
            onClick = {
              sourceType = "EMAIL"
              if (!sender.contains("@")) sender = "billing@coned.com"
            },
            label = { Text("Email Receipt") },
            modifier = Modifier.testTag("chip_source_email")
          )
          TextButton(
            onClick = {
              val clip = clipboardManager.getText()?.text
              if (!clip.isNullOrBlank()) {
                body = clip
              }
            }
          ) {
            Text("Paste Clipboard")
          }
        }

        OutlinedTextField(
          value = sender,
          onValueChange = { sender = it },
          label = { Text(if (sourceType == "EMAIL") "Sender Email" else "SMS Sender ID") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_message_sender")
        )

        if (sourceType == "EMAIL") {
          OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Email Subject") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("input_message_subject")
          )
        }

        OutlinedTextField(
          value = body,
          onValueChange = { body = it },
          label = { Text("Raw SMS / Email Content") },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_message_body")
        )

        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = "Live Extraction Preview (Queues for Approval)",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Merchant: ${livePreview.merchant} • ${CurrencyFormatter.formatCents(livePreview.amountCents)}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Type: ${livePreview.transactionType} | Category: ${livePreview.category}" +
                if (livePreview.lastFourDigits.isNotBlank()) " | Card/Acct ••${livePreview.lastFourDigits}" else "",
              style = MaterialTheme.typography.bodyMedium
            )
            if (livePreview.itemsSummary.isNotBlank()) {
              Text(
                text = "Items: ${livePreview.itemsSummary}",
                style = MaterialTheme.typography.bodyMedium
              )
            }
            if (livePreview.detectedBalanceOrLimitCents != null) {
              Text(
                text = "Detected Bank Balance / Limit: ${CurrencyFormatter.formatCents(livePreview.detectedBalanceOrLimitCents)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSubmit(sourceType, sender, subject, body)
          onDismiss()
        },
        modifier = Modifier.testTag("submit_parse_message_button")
      ) {
        Text("Queue for Approval")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun ReviewAndApproveDialog(
  transaction: TransactionRecord,
  paymentMethods: List<PaymentMethodRecord>,
  onDismiss: () -> Unit,
  onApprove: (
    paymentMethod: PaymentMethodRecord?,
    editedTitle: String,
    editedCategory: String,
    editedAmountCents: Long,
    editedItems: String,
    billPaid: Boolean,
    billReference: String,
    notes: String
  ) -> Unit,
  onReject: () -> Unit
) {
  var title by remember { mutableStateOf(transaction.title) }
  var amountText by remember {
    mutableStateOf(String.format("%.2f", transaction.amountCents / 100.0))
  }
  var selectedCategory by remember { mutableStateOf(transaction.category) }
  var selectedPaymentMethod by remember {
    mutableStateOf(
      paymentMethods.firstOrNull { it.id == transaction.paymentMethodId }
        ?: paymentMethods.firstOrNull { it.defaultMethod }
    )
  }
  var itemsBought by remember { mutableStateOf(transaction.itemsBoughtSummary) }
  var billPaid by remember { mutableStateOf(transaction.billPaid || transaction.type == "BILL_PAID") }
  var billReference by remember { mutableStateOf(transaction.billReference) }
  var notes by remember { mutableStateOf(transaction.notes) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Approve Parsed ${transaction.sourceType} Transaction",
        style = MaterialTheme.typography.titleLarge
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (transaction.linkedMessageSnippet.isNotBlank()) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                text = "Source ${transaction.sourceType} (${transaction.linkedSender})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = transaction.linkedMessageSnippet,
                style = MaterialTheme.typography.bodyMedium
              )
            }
          }
        }

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Merchant / Payee") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("approve_title_input")
        )

        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text("Amount (${CurrencyFormatter.activeCurrency.code} ${CurrencyFormatter.activeCurrency.symbol})") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("approve_amount_input")
        )

        Text(
          text = "Category",
          style = MaterialTheme.typography.labelLarge
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          FinanceCategories.ALL.forEach { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { selectedCategory = cat },
              label = { Text(cat) }
            )
          }
        }

        if (paymentMethods.isNotEmpty()) {
          Text(
            text = "Payment Method (Card / Bank Account)",
            style = MaterialTheme.typography.labelLarge
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            paymentMethods.forEach { pm ->
              FilterChip(
                selected = selectedPaymentMethod?.id == pm.id,
                onClick = { selectedPaymentMethod = pm },
                label = { Text(pm.displayBadge()) }
              )
            }
          }
          val activePm = selectedPaymentMethod
          if (activePm != null) {
            val previewCents = ((amountText.toDoubleOrNull() ?: (transaction.amountCents / 100.0)) * 100.0).roundToLong()
            val effectiveType = if (billPaid && transaction.type != "INCOME") "BILL_PAID" else transaction.type
            PaymentMethodBalanceFlagPreview(
              method = activePm,
              txAmountCents = previewCents,
              txType = effectiveType,
              linkedMessageText = transaction.linkedMessageSnippet
            )
          }
        }

        OutlinedTextField(
          value = itemsBought,
          onValueChange = { itemsBought = it },
          label = { Text("Items Bought (optional)") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("approve_items_input")
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Mark as Bill Paid", style = MaterialTheme.typography.bodyLarge)
          Switch(
            checked = billPaid,
            onCheckedChange = { billPaid = it },
            modifier = Modifier.testTag("approve_bill_paid_switch")
          )
        }

        if (billPaid) {
          OutlinedTextField(
            value = billReference,
            onValueChange = { billReference = it },
            label = { Text("Bill / Invoice Reference #") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedCents = ((amountText.toDoubleOrNull() ?: (transaction.amountCents / 100.0)) * 100.0)
            .roundToLong()
            .coerceAtLeast(1L)
          onApprove(
            selectedPaymentMethod,
            title,
            selectedCategory,
            parsedCents,
            itemsBought,
            billPaid,
            billReference,
            notes
          )
          onDismiss()
        },
        modifier = Modifier.testTag("confirm_approve_button")
      ) {
        Text("Approve & Record")
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
          onClick = {
            onReject()
            onDismiss()
          },
          modifier = Modifier.testTag("confirm_reject_button")
        ) {
          Text("Dismiss")
        }
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    }
  )
}

@Composable
private fun PaymentMethodBalanceFlagPreview(
  method: PaymentMethodRecord,
  txAmountCents: Long,
  txType: String,
  linkedMessageText: String
) {
  val parsedBalance = MessageParserEngine.extractBalanceOrLimitCents(linkedMessageText)
  val isCredit = method.methodType == "CREDIT_CARD"
  val currentVal = method.limitOrBalanceCents
  val isExpense = txType != "INCOME"

  val isFlagged = when {
    isCredit -> isExpense && txAmountCents > currentVal
    else -> isExpense && parsedBalance == null && txAmountCents > currentVal
  }

  val previewText = when {
    isCredit -> {
      val remAfter = currentVal - (if (isExpense) txAmountCents else 0L)
      if (remAfter < 0L) {
        "⚠️ Exceeds Credit Card Limit (${CurrencyFormatter.formatCents(currentVal)}) by ${CurrencyFormatter.formatCents(-remAfter)}!"
      } else {
        "Credit Limit: ${CurrencyFormatter.formatCents(currentVal)} • Remaining after tx: ${CurrencyFormatter.formatCents(remAfter)}"
      }
    }
    parsedBalance != null -> {
      "Bank Balance will sync from SMS/Email Avl Bal: ${CurrencyFormatter.formatCents(parsedBalance)}"
    }
    isExpense && txAmountCents > currentVal -> {
      "⚠️ Exceeds ${method.displayBadge()} balance (${CurrencyFormatter.formatCents(currentVal)})!"
    }
    else -> {
      val projected = if (isExpense) (currentVal - txAmountCents).coerceAtLeast(0L) else currentVal + txAmountCents
      "Bank Balance: ${CurrencyFormatter.formatCents(currentVal)} → ${CurrencyFormatter.formatCents(projected)}"
    }
  }

  Text(
    text = previewText,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = if (isFlagged) FontWeight.Bold else FontWeight.Medium,
    color = if (isFlagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
  )
}

@Composable
fun UpdatePaymentMethodBalanceDialog(
  paymentMethod: PaymentMethodRecord,
  onDismiss: () -> Unit,
  onSaveBalanceOrLimit: (Long) -> Unit
) {
  val isCreditCard = paymentMethod.methodType == "CREDIT_CARD"
  val fieldLabel = if (isCreditCard) {
    "Credit Card Limit (${CurrencyFormatter.activeCurrency.code} ${CurrencyFormatter.activeCurrency.symbol})"
  } else {
    "Current Bank Balance (${CurrencyFormatter.activeCurrency.code} ${CurrencyFormatter.activeCurrency.symbol})"
  }

  var amountText by remember(paymentMethod) {
    mutableStateOf(String.format("%.2f", paymentMethod.limitOrBalanceCents / 100.0))
  }
  var smsBalanceSnippet by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (isCreditCard) "Update Credit Card Limit" else "Update Bank Account Balance",
        style = MaterialTheme.typography.titleLarge
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = paymentMethod.displayBadge(),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary
        )
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text(fieldLabel) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("update_pm_balance_input")
        )
        OutlinedTextField(
          value = smsBalanceSnippet,
          onValueChange = { snippet ->
            smsBalanceSnippet = snippet
            val extracted = MessageParserEngine.extractBalanceOrLimitCents(snippet)
            if (extracted != null) {
              amountText = String.format("%.2f", extracted / 100.0)
            }
          },
          label = { Text("Or Paste Bank SMS / Email with 'Avl Bal: ₹...'") },
          minLines = 2,
          maxLines = 3,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("update_pm_sms_snippet_input")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val cents = ((amountText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
          onSaveBalanceOrLimit(cents)
          onDismiss()
        },
        modifier = Modifier.testTag("confirm_update_pm_balance_button")
      ) {
        Text(if (isCreditCard) "Update Limit" else "Update Balance")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}


@Composable
fun AddManualTransactionDialog(
  paymentMethods: List<PaymentMethodRecord>,
  parsedMessages: List<ParsedMessageRecord>,
  onDismiss: () -> Unit,
  onSave: (
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
  ) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var amountText by remember { mutableStateOf("") }
  var type by remember { mutableStateOf("EXPENSE") }
  var category by remember { mutableStateOf(FinanceCategories.GROCERIES) }
  var selectedPaymentMethod by remember {
    mutableStateOf(paymentMethods.firstOrNull { it.defaultMethod } ?: paymentMethods.firstOrNull())
  }
  var itemsBought by remember { mutableStateOf("") }
  var billPaid by remember { mutableStateOf(false) }
  var billReference by remember { mutableStateOf("") }
  var selectedMessage by remember { mutableStateOf<ParsedMessageRecord?>(null) }
  var customSnippet by remember { mutableStateOf("") }
  var customSender by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text("Add Manual Transaction", style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("EXPENSE" to "Expense", "BILL_PAID" to "Bill Paid", "INCOME" to "Income").forEach { (key, label) ->
            FilterChip(
              selected = type == key,
              onClick = {
                type = key
                if (key == "BILL_PAID") {
                  billPaid = true
                  category = FinanceCategories.UTILITIES_BILLS
                } else if (key == "INCOME") {
                  billPaid = false
                  category = FinanceCategories.INCOME
                }
              },
              label = { Text(label) }
            )
          }
        }

        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Merchant, Biller, or Title") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("manual_tx_title_input")
        )

        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text("Amount (${CurrencyFormatter.activeCurrency.code} ${CurrencyFormatter.activeCurrency.symbol}, e.g. 450.00)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("manual_tx_amount_input")
        )

        Text("Category", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          FinanceCategories.ALL.forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat) }
            )
          }
        }

        if (paymentMethods.isNotEmpty()) {
          Text("Card / Bank Account", style = MaterialTheme.typography.labelLarge)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            paymentMethods.forEach { pm ->
              FilterChip(
                selected = selectedPaymentMethod?.id == pm.id,
                onClick = { selectedPaymentMethod = pm },
                label = { Text(pm.displayBadge()) }
              )
            }
          }
          val activePm = selectedPaymentMethod
          if (activePm != null) {
            val previewCents = ((amountText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
            val effectiveType = if (billPaid && type != "INCOME") "BILL_PAID" else type
            PaymentMethodBalanceFlagPreview(
              method = activePm,
              txAmountCents = previewCents,
              txType = effectiveType,
              linkedMessageText = selectedMessage?.body ?: customSnippet
            )
          }
        }

        OutlinedTextField(
          value = itemsBought,
          onValueChange = { itemsBought = it },
          label = { Text("Items Bought (e.g. Organic Milk x2; Bread)") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("manual_tx_items_input")
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Bill Paid Confirmation", style = MaterialTheme.typography.bodyLarge)
          Switch(
            checked = billPaid,
            onCheckedChange = { billPaid = it }
          )
        }

        if (billPaid) {
          OutlinedTextField(
            value = billReference,
            onValueChange = { billReference = it },
            label = { Text("Bill Reference / Invoice ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }

        Text(
          text = "Link Email / SMS Receipt (Optional)",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
        if (parsedMessages.isNotEmpty()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            FilterChip(
              selected = selectedMessage == null,
              onClick = { selectedMessage = null },
              label = { Text("Custom / None") }
            )
            parsedMessages.take(8).forEach { msg ->
              FilterChip(
                selected = selectedMessage?.id == msg.id,
                onClick = { selectedMessage = msg },
                label = {
                  Text("${msg.sourceType}: ${msg.detectedMerchant.ifBlank { msg.sender }.take(16)}")
                }
              )
            }
          }
        }

        if (selectedMessage == null) {
          OutlinedTextField(
            value = customSender,
            onValueChange = { customSender = it },
            label = { Text("Linked Email / SMS Sender") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = customSnippet,
            onValueChange = { customSnippet = it },
            label = { Text("Linked Email / SMS Excerpt") },
            modifier = Modifier.fillMaxWidth()
          )
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val cents = ((amountText.toDoubleOrNull() ?: 15.0) * 100.0).roundToLong().coerceAtLeast(1L)
          onSave(
            title.ifBlank { "Purchase" },
            cents,
            type,
            category,
            selectedPaymentMethod,
            itemsBought,
            billPaid,
            billReference,
            selectedMessage,
            customSnippet,
            customSender,
            notes
          )
          onDismiss()
        },
        modifier = Modifier.testTag("save_manual_tx_button")
      ) {
        Text("Save Transaction")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun LinkMessageToTransactionDialog(
  transaction: TransactionRecord,
  parsedMessages: List<ParsedMessageRecord>,
  onDismiss: () -> Unit,
  onLinkSelected: (ParsedMessageRecord) -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text("Link SMS or Email to '${transaction.title}'", style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (parsedMessages.isEmpty()) {
          Text(
            "No scanned SMS or Email messages yet. Tap 'Scan / Paste SMS or Email' to add messages.",
            style = MaterialTheme.typography.bodyMedium
          )
        } else {
          parsedMessages.forEach { msg ->
            Card(
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  onLinkSelected(msg)
                  onDismiss()
                }
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "${msg.sourceType} • ${msg.sender} (${CurrencyFormatter.formatCents(msg.detectedAmountCents)})",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary
                )
                if (msg.subject.isNotBlank()) {
                  Text(
                    text = msg.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                  )
                }
                Text(
                  text = msg.body.take(140),
                  style = MaterialTheme.typography.bodyMedium
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    }
  )
}

@Composable
fun AddPaymentMethodDialog(
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    institution: String,
    methodType: String,
    lastFourDigits: String,
    networkOrRouting: String,
    limitOrBalanceCents: Long,
    colorHex: String,
    defaultMethod: Boolean
  ) -> Unit
) {
  var methodType by remember { mutableStateOf("CREDIT_CARD") }
  var institution by remember { mutableStateOf("Chase") }
  var name by remember { mutableStateOf("Sapphire Preferred") }
  var lastFour by remember { mutableStateOf("4821") }
  var networkOrRouting by remember { mutableStateOf("Visa") }
  var limitOrBalanceText by remember { mutableStateOf("10000") }
  var colorHex by remember { mutableStateOf("#0B3B2E") }
  var defaultMethod by remember { mutableStateOf(false) }

  val colorPresets = listOf(
    "#0B3B2E" to Color(0xFF0B3B2E),
    "#1E3A8A" to Color(0xFF1E3A8A),
    "#78350F" to Color(0xFF78350F),
    "#4C1D95" to Color(0xFF4C1D95),
    "#1F2937" to Color(0xFF1F2937)
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text("Add Card or Bank Account", style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(
            "CREDIT_CARD" to "Credit Card",
            "DEBIT_CARD" to "Debit Card",
            "BANK_ACCOUNT" to "Bank Account",
            "DIGITAL_WALLET" to "Digital Wallet"
          ).forEach { (key, label) ->
            FilterChip(
              selected = methodType == key,
              onClick = {
                methodType = key
                if (key == "BANK_ACCOUNT") networkOrRouting = "ACH Checking"
              },
              label = { Text(label) }
            )
          }
        }

        OutlinedTextField(
          value = institution,
          onValueChange = { institution = it },
          label = { Text("Bank / Issuer (e.g. Chase, Amex, Citi)") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pm_institution_input")
        )

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Card or Account Nickname") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pm_name_input")
        )

        OutlinedTextField(
          value = lastFour,
          onValueChange = { lastFour = it.filter { c -> c.isDigit() }.take(4) },
          label = { Text("Last 4 Digits (for SMS/Email auto-matching)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pm_last4_input")
        )

        OutlinedTextField(
          value = networkOrRouting,
          onValueChange = { networkOrRouting = it },
          label = { Text(if (methodType == "BANK_ACCOUNT") "Account Subtype (Checking/Savings)" else "Card Network (Visa/Mastercard/Amex)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = limitOrBalanceText,
          onValueChange = { limitOrBalanceText = it },
          label = {
            val curr = "${CurrencyFormatter.activeCurrency.code} ${CurrencyFormatter.activeCurrency.symbol}"
            Text(if (methodType == "CREDIT_CARD") "Credit Limit ($curr)" else "Current Balance ($curr)")
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text("Card Visual Theme", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          colorPresets.forEach { (hex, composeColor) ->
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(composeColor)
                .border(
                  width = if (colorHex == hex) 3.dp else 1.dp,
                  color = if (colorHex == hex) MaterialTheme.colorScheme.primary else Color.Transparent,
                  shape = CircleShape
                )
                .clickable { colorHex = hex }
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Set as Default Payment Method", style = MaterialTheme.typography.bodyLarge)
          Switch(checked = defaultMethod, onCheckedChange = { defaultMethod = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val cents = ((limitOrBalanceText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
          onSave(
            name,
            institution,
            methodType,
            lastFour,
            networkOrRouting,
            cents,
            colorHex,
            defaultMethod
          )
          onDismiss()
        },
        modifier = Modifier.testTag("save_payment_method_button")
      ) {
        Text("Save Method")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun SetCategoryBudgetDialog(
  initialCategory: String = FinanceCategories.GROCERIES,
  onDismiss: () -> Unit,
  onSave: (category: String, monthlyLimitCents: Long, alertThresholdPercent: Long, alertsEnabled: Boolean) -> Unit
) {
  var category by remember { mutableStateOf(initialCategory) }
  var limitDollarsText by remember { mutableStateOf("500") }
  var thresholdSlider by remember { mutableFloatStateOf(80f) }
  var alertsEnabled by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text("Personalized Category Budget Alert", style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("Select Category", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          FinanceCategories.EXPENSE_CATEGORIES.forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat) }
            )
          }
        }

        OutlinedTextField(
          value = limitDollarsText,
          onValueChange = { limitDollarsText = it },
          label = { Text("Monthly Budget Limit (${CurrencyFormatter.activeCurrency.code} ${CurrencyFormatter.activeCurrency.symbol})") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("budget_limit_input")
        )

        Column {
          Text(
            text = "Alert Threshold: ${thresholdSlider.toInt()}% of Monthly Limit",
            style = MaterialTheme.typography.labelLarge
          )
          Slider(
            value = thresholdSlider,
            onValueChange = { thresholdSlider = it },
            valueRange = 50f..100f,
            steps = 9
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Enable Proactive Budget Alerts", style = MaterialTheme.typography.bodyLarge)
          Switch(checked = alertsEnabled, onCheckedChange = { alertsEnabled = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val cents = ((limitDollarsText.toDoubleOrNull() ?: 500.0) * 100.0).roundToLong().coerceAtLeast(100L)
          onSave(category, cents, thresholdSlider.toLong(), alertsEnabled)
          onDismiss()
        },
        modifier = Modifier.testTag("save_budget_alert_button")
      ) {
        Text("Save Alert")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

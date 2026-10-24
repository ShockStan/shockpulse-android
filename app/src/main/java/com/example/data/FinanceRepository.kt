package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  // --- Real-Time Stream Observers ---

  fun observeTransactions(userId: String = requireUserId()): Flow<List<TransactionRecord>> {
    val path = "users/$userId/transactions"
    return db.collection("users").document(userId).collection("transactions")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          TransactionRecord::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).sortedByDescending { it.transactionDateMillis }
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  fun observePaymentMethods(userId: String = requireUserId()): Flow<List<PaymentMethodRecord>> {
    val path = "users/$userId/payment_methods"
    return db.collection("users").document(userId).collection("payment_methods")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          PaymentMethodRecord::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).sortedWith(compareByDescending<PaymentMethodRecord> { it.defaultMethod }.thenBy { it.name })
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  fun observeBudgets(userId: String = requireUserId()): Flow<List<CategoryBudgetRecord>> {
    val path = "users/$userId/budgets"
    return db.collection("users").document(userId).collection("budgets")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          CategoryBudgetRecord::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).sortedBy { it.category }
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  fun observeParsedMessages(userId: String = requireUserId()): Flow<List<ParsedMessageRecord>> {
    val path = "users/$userId/parsed_messages"
    return db.collection("users").document(userId).collection("parsed_messages")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          ParsedMessageRecord::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).sortedByDescending { it.receivedAtMillis }
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  // --- Transactions CRUD & Post-Approval Actions ---

  suspend fun createTransaction(record: TransactionRecord): Result<String> {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("transactions")
    val docRef = if (record.id.isNotBlank()) colRef.document(record.id) else colRef.document()
    val nowMillis = if (record.transactionDateMillis > 0L) record.transactionDateMillis else System.currentTimeMillis()
    val monthKey = record.monthKey.ifBlank { CurrencyFormatter.currentMonthKey(nowMillis) }
    val sanitized = record.copy(
      id = docRef.id,
      userId = uid,
      title = record.title.trim().ifBlank { "Transaction" }.take(120),
      amountCents = record.amountCents.coerceAtLeast(1L),
      category = record.category.trim().ifBlank { FinanceCategories.SHOPPING }.take(60),
      paymentMethodId = record.paymentMethodId.take(128),
      paymentMethodLabel = record.paymentMethodLabel.take(100),
      linkedMessageId = record.linkedMessageId.take(128),
      linkedMessageSnippet = record.linkedMessageSnippet.take(600),
      linkedSender = record.linkedSender.take(120),
      itemsBoughtSummary = record.itemsBoughtSummary.take(600),
      billReference = record.billReference.take(120),
      transactionDateMillis = nowMillis,
      monthKey = monthKey,
      notes = record.notes.take(500),
      createdAt = null,
      updatedAt = null
    )
    return try {
      docRef.set(sanitized).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun getTransactionById(ownerUid: String, transactionId: String): Result<TransactionRecord?> {
    val docRef = db.collection("users").document(ownerUid).collection("transactions").document(transactionId)
    return try {
      val snap = docRef.get().await()
      Result.success(
        snap.toObject(
          TransactionRecord::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateTransaction(record: TransactionRecord): Result<Unit> {
    val uid = requireUserId()
    require(record.id.isNotBlank()) { "Transaction ID must not be blank for update" }
    val docRef = db.collection("users").document(uid).collection("transactions").document(record.id)
    return try {
      val existingCreatedAt = record.createdAt ?: docRef.get().await().getTimestamp(
        "createdAt",
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      )
      requireNotNull(existingCreatedAt) { "Transaction createdAt must not be null for update" }
      val updated = record.copy(
        userId = uid,
        title = record.title.trim().ifBlank { "Transaction" }.take(120),
        amountCents = record.amountCents.coerceAtLeast(1L),
        category = record.category.trim().ifBlank { FinanceCategories.SHOPPING }.take(60),
        paymentMethodId = record.paymentMethodId.take(128),
        paymentMethodLabel = record.paymentMethodLabel.take(100),
        linkedMessageId = record.linkedMessageId.take(128),
        linkedMessageSnippet = record.linkedMessageSnippet.take(600),
        linkedSender = record.linkedSender.take(120),
        itemsBoughtSummary = record.itemsBoughtSummary.take(600),
        billReference = record.billReference.take(120),
        notes = record.notes.take(500),
        createdAt = existingCreatedAt,
        updatedAt = null
      )
      docRef.set(updated).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun approveTransaction(
    record: TransactionRecord,
    paymentMethod: PaymentMethodRecord?,
    editedTitle: String,
    editedCategory: String,
    editedAmountCents: Long,
    editedItems: String,
    billPaid: Boolean,
    billReference: String,
    notes: String
  ): Result<Unit> {
    val updatedTx = record.copy(
      title = editedTitle.ifBlank { record.title },
      category = editedCategory.ifBlank { record.category },
      amountCents = editedAmountCents.coerceAtLeast(1L),
      status = "APPROVED",
      type = if (billPaid && record.type != "INCOME") "BILL_PAID" else record.type,
      paymentMethodId = paymentMethod?.id ?: record.paymentMethodId,
      paymentMethodLabel = paymentMethod?.displayBadge() ?: record.paymentMethodLabel,
      itemsBoughtSummary = editedItems,
      billPaid = billPaid,
      billReference = billReference,
      notes = notes
    )
    val txResult = updateTransaction(updatedTx)
    if (txResult.isSuccess && record.linkedMessageId.isNotBlank()) {
      updateParsedMessageStatus(record.linkedMessageId, "APPROVED", record.id)
    }
    return txResult
  }

  suspend fun rejectTransaction(record: TransactionRecord): Result<Unit> {
    val txResult = updateTransaction(record.copy(status = "REJECTED"))
    if (txResult.isSuccess && record.linkedMessageId.isNotBlank()) {
      updateParsedMessageStatus(record.linkedMessageId, "REJECTED", record.id)
    }
    return txResult
  }

  suspend fun deleteTransaction(transactionId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("transactions").document(transactionId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- Payment Methods CRUD ---

  suspend fun createPaymentMethod(record: PaymentMethodRecord): Result<String> {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("payment_methods")
    val docRef = if (record.id.isNotBlank()) colRef.document(record.id) else colRef.document()
    val sanitized = record.copy(
      id = docRef.id,
      userId = uid,
      name = record.name.trim().ifBlank { "Primary Card" }.take(80),
      institution = record.institution.trim().ifBlank { "Bank" }.take(80),
      lastFourDigits = record.lastFourDigits.filter { it.isDigit() }.takeLast(4),
      networkOrRouting = record.networkOrRouting.trim().take(40),
      limitOrBalanceCents = record.limitOrBalanceCents.coerceAtLeast(0L),
      colorHex = if (record.colorHex.length == 7 && record.colorHex.startsWith("#")) record.colorHex else "#0F4C81",
      createdAt = null,
      updatedAt = null
    )
    return try {
      docRef.set(sanitized).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updatePaymentMethodBalanceOrLimit(
    record: PaymentMethodRecord,
    newBalanceOrLimitCents: Long
  ): Result<Unit> {
    val uid = requireUserId()
    require(record.id.isNotBlank()) { "PaymentMethod ID must not be blank for update" }
    val docRef = db.collection("users").document(uid).collection("payment_methods").document(record.id)
    return try {
      val existingCreatedAt = record.createdAt ?: docRef.get().await().getTimestamp(
        "createdAt",
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      )
      requireNotNull(existingCreatedAt) { "PaymentMethod createdAt must not be null for update" }
      val updated = record.copy(
        userId = uid,
        name = record.name.trim().ifBlank { "Primary Card" }.take(80),
        institution = record.institution.trim().ifBlank { "Bank" }.take(80),
        lastFourDigits = record.lastFourDigits.filter { it.isDigit() }.takeLast(4),
        networkOrRouting = record.networkOrRouting.trim().take(40),
        limitOrBalanceCents = newBalanceOrLimitCents.coerceAtLeast(0L),
        colorHex = if (record.colorHex.length == 7 && record.colorHex.startsWith("#")) record.colorHex else "#0F4C81",
        createdAt = existingCreatedAt,
        updatedAt = null
      )
      docRef.set(updated).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deletePaymentMethod(methodId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("payment_methods").document(methodId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- Category Budgets & Alerts CRUD ---

  suspend fun saveCategoryBudget(record: CategoryBudgetRecord): Result<String> {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("budgets")
    val docRef = if (record.id.isNotBlank()) colRef.document(record.id) else colRef.document()
    return try {
      val sanitized = record.copy(
        id = docRef.id,
        userId = uid,
        category = record.category.trim().ifBlank { FinanceCategories.GROCERIES }.take(60),
        monthlyLimitCents = record.monthlyLimitCents.coerceAtLeast(100L),
        alertThresholdPercent = record.alertThresholdPercent.coerceIn(10L, 100L),
        monthKey = record.monthKey.ifBlank { CurrencyFormatter.currentMonthKey() },
        createdAt = record.createdAt,
        updatedAt = null
      )
      docRef.set(sanitized).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteCategoryBudget(budgetId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("budgets").document(budgetId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- Message & Email Ingestion + Post-Approval Queue ---

  suspend fun ingestParsedMessageAndQueueTransaction(
    sourceType: String,
    sender: String,
    subject: String,
    body: String,
    paymentMethods: List<PaymentMethodRecord>,
    receivedAtMillis: Long = System.currentTimeMillis()
  ): Result<Pair<String, String>> {
    val uid = requireUserId()
    val extracted = MessageParserEngine.parseRawMessage(sourceType, sender, subject, body)

    val matchedMethod = paymentMethods.firstOrNull {
      extracted.lastFourDigits.isNotBlank() && it.lastFourDigits == extracted.lastFourDigits
    } ?: paymentMethods.firstOrNull { it.defaultMethod }

    val msgRef = db.collection("users").document(uid).collection("parsed_messages").document()
    val txRef = db.collection("users").document(uid).collection("transactions").document()

    val msgRecord = ParsedMessageRecord(
      id = msgRef.id,
      userId = uid,
      sourceType = extracted.sourceType,
      sender = extracted.sender,
      subject = extracted.subject,
      body = extracted.body,
      detectedAmountCents = extracted.amountCents,
      detectedMerchant = extracted.merchant,
      detectedCategory = extracted.category,
      detectedLastFour = extracted.lastFourDigits,
      detectedType = extracted.transactionType,
      detectedItems = extracted.itemsSummary,
      linkedTransactionId = txRef.id,
      approvalStatus = "PENDING",
      receivedAtMillis = receivedAtMillis,
      createdAt = null,
      updatedAt = null
    )

    val methodLabel = when {
      matchedMethod != null -> matchedMethod.displayBadge()
      extracted.lastFourDigits.isNotBlank() -> "Card/Acct ••${extracted.lastFourDigits}"
      else -> "Unassigned"
    }

    val pendingTx = TransactionRecord(
      id = txRef.id,
      userId = uid,
      title = extracted.merchant.ifBlank { "Parsed Transaction" },
      amountCents = extracted.amountCents.coerceAtLeast(1L),
      type = extracted.transactionType,
      status = "PENDING",
      category = extracted.category,
      paymentMethodId = matchedMethod?.id ?: "",
      paymentMethodLabel = methodLabel,
      sourceType = extracted.sourceType,
      linkedMessageId = msgRef.id,
      linkedMessageSnippet = if (extracted.subject.isNotBlank()) {
        "${extracted.subject}: ${extracted.body}".take(600)
      } else {
        extracted.body.take(600)
      },
      linkedSender = extracted.sender,
      itemsBoughtSummary = extracted.itemsSummary,
      billPaid = extracted.transactionType == "BILL_PAID",
      billReference = extracted.billReference,
      transactionDateMillis = receivedAtMillis,
      monthKey = CurrencyFormatter.currentMonthKey(receivedAtMillis),
      notes = "Auto-extracted from ${extracted.sourceType} (${extracted.sender}). Awaiting user approval.",
      createdAt = null,
      updatedAt = null
    )

    return try {
      msgRef.set(msgRecord).await()
      txRef.set(pendingTx).await()
      Result.success(msgRef.id to txRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun linkMessageToTransaction(
    transaction: TransactionRecord,
    message: ParsedMessageRecord
  ): Result<Unit> {
    val snippet = if (message.subject.isNotBlank()) {
      "${message.subject}: ${message.body}".take(600)
    } else {
      message.body.take(600)
    }
    val updatedTx = transaction.copy(
      linkedMessageId = message.id,
      linkedMessageSnippet = snippet,
      linkedSender = message.sender,
      sourceType = message.sourceType,
      itemsBoughtSummary = transaction.itemsBoughtSummary.ifBlank { message.detectedItems }
    )
    val res = updateTransaction(updatedTx)
    if (res.isSuccess) {
      updateParsedMessageStatus(message.id, transaction.status, transaction.id)
    }
    return res
  }

  private suspend fun updateParsedMessageStatus(
    messageId: String,
    approvalStatus: String,
    linkedTransactionId: String
  ) {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("parsed_messages").document(messageId)
    try {
      val snap = docRef.get().await()
      val existing = snap.toObject(
        ParsedMessageRecord::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      ) ?: return
      val existingCreatedAt = existing.createdAt ?: return
      docRef.set(
        existing.copy(
          approvalStatus = approvalStatus,
          linkedTransactionId = linkedTransactionId.take(128),
          createdAt = existingCreatedAt,
          updatedAt = null
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
    }
  }
}

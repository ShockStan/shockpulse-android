package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FinanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ingestMessageAndApproveTransaction_authenticatedOwner_succeedsAndEmits() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repository = FinanceRepository(firestore)

    // 1. Add a Payment Method (Card)
    val pmResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createPaymentMethod(
        PaymentMethodRecord(
          name = "Sapphire Reserve",
          institution = "Chase",
          methodType = "CREDIT_CARD",
          lastFourDigits = "4821",
          networkOrRouting = "Visa",
          limitOrBalanceCents = 1500000L,
          colorHex = "#0F4C81",
          defaultMethod = true
        )
      )
    }
    assertTrue("Expected payment method creation success: ${pmResult.exceptionOrNull()}", pmResult.isSuccess)

    val methods = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observePaymentMethods(aliceUid).first { it.isNotEmpty() }
    }

    // 2. Ingest an SMS message into pending approval queue
    val ingestResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.ingestParsedMessageAndQueueTransaction(
        sourceType = "SMS",
        sender = "CHASE-ALERT",
        subject = "Charge Alert",
        body = "Chase Alert: $84.50 charged at Whole Foods Market on card ending 4821. Items: Organic Milk, Berries.",
        paymentMethods = methods
      )
    }
    assertTrue("Expected message ingestion success: ${ingestResult.exceptionOrNull()}", ingestResult.isSuccess)
    val (_, txId) = ingestResult.getOrThrow()

    // 3. Observe pending transaction and approve it
    val pendingList = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeTransactions(aliceUid).first { list -> list.any { it.id == txId } }
    }
    val pendingTx = pendingList.first { it.id == txId }
    assertEquals("PENDING", pendingTx.status)
    assertEquals("4821", methods.first().lastFourDigits)

    val approveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.approveTransaction(
        record = pendingTx,
        paymentMethod = methods.first(),
        editedTitle = pendingTx.title,
        editedCategory = pendingTx.category,
        editedAmountCents = pendingTx.amountCents,
        editedItems = pendingTx.itemsBoughtSummary,
        billPaid = false,
        billReference = "",
        notes = "Approved by user"
      )
    }
    assertTrue("Expected approveTransaction success: ${approveResult.exceptionOrNull()}", approveResult.isSuccess)

    // 4. Create a Category Budget Alert
    val budgetResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.saveCategoryBudget(
        CategoryBudgetRecord(
          category = FinanceCategories.GROCERIES,
          monthlyLimitCents = 60000L,
          alertThresholdPercent = 80L,
          monthKey = pendingTx.monthKey,
          alertsEnabled = true
        )
      )
    }
    assertTrue("Expected budget creation success: ${budgetResult.exceptionOrNull()}", budgetResult.isSuccess)
  }

  @Test
  fun crossUserAccess_bobCannotReadAliceTransaction_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = FinanceRepository(firestore)
    val txId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createTransaction(
        TransactionRecord(
          title = "Private Bill",
          amountCents = 12000L,
          type = "BILL_PAID",
          status = "APPROVED",
          category = FinanceCategories.UTILITIES_BILLS,
          billPaid = true,
          billReference = "INV-100"
        )
      ).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = FinanceRepository(firestore)
    val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getTransactionById(aliceUid, txId)
    }
    assertTrue("Expected cross-user read to fail", bobReadResult.isFailure)
    val fsEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException", fsEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
  }

  @Test
  fun observeTransactions_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repository = FinanceRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeTransactions(aliceUid).first()
      }
      fail("Expected unauthenticated observeTransactions to throw")
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice@pulseledger.test"
    const val BOB_EMAIL = "bob@pulseledger.test"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}

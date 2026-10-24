package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.FinanceCategories
import com.example.data.MessageParserEngine
import com.example.data.PaymentMethodRecord
import com.example.data.PdfReportGenerator
import com.example.data.TransactionRecord
import com.example.ui.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
          .setApplicationId("com.aistudio.pulseledger.vqxnrt")
          .setProjectId("demo-no-project")
          .setApiKey("fake-api-key")
          .build()
      )
    }
  }

  @Test
  fun appNameAndFirebaseConfig_areProperlyConfigured() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("ShockPulse", context.getString(R.string.app_name))
    assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
    assertTrue(context.getString(R.string.default_web_client_id).isNotBlank())
  }

  @Test
  fun authScreen_displaysGoogleSignInAndSavedAccountButtons() {
    composeTestRule.setContent {
      MyApplicationTheme {
        AuthScreen(onAuthSuccess = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_screen_root").assertIsDisplayed()
    composeTestRule.onNodeWithText("ShockPulse").assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("authorized_account_sign_in_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun mainLayout_bottomNavigationSwitchesBetweenDashboardTransactionsAndReports() {
    composeTestRule.setContent {
      MyApplicationTheme {
        var activeSection by remember { mutableStateOf(ShockPulseSection.DASHBOARD) }
        ShockPulseMainLayout(
          currentSection = activeSection,
          onSelectSection = { activeSection = it },
          userEmail = "test@shockpulse.app"
        )
      }
    }
    composeTestRule.waitForIdle()

    // Verify ShockPulse title and Bottom Navigation Bar are displayed
    composeTestRule.onNodeWithTag("top_bar_app_title").assertIsDisplayed()
    composeTestRule.onNodeWithTag("bottom_navigation_bar").assertIsDisplayed()

    // Default section is Dashboard
    composeTestRule.onNodeWithTag("section_content_dashboard").assertIsDisplayed()

    // Navigate to Transactions via bottom navigation bar
    composeTestRule.onNodeWithTag("nav_tab_transactions").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("section_content_transactions").assertIsDisplayed()

    // Navigate to Reports via bottom navigation bar
    composeTestRule.onNodeWithTag("nav_tab_reports").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("section_content_reports").assertIsDisplayed()
  }

  @Test
  fun messageParserAndPdfExport_extractAndGenerateValidPdf() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val parsed = MessageParserEngine.parseRawMessage(
      sourceType = "EMAIL",
      sender = "billing@coned.com",
      subject = "Bill Paid Confirmation - Con Edison Electric",
      body = "Your monthly electric utility bill of $142.80 was paid from Checking account ending 9044. Invoice #CE-88412. Items: Residential Electricity 610 kWh."
    )
    assertEquals(14280L, parsed.amountCents)
    assertEquals("9044", parsed.lastFourDigits)
    assertEquals("BILL_PAID", parsed.transactionType)
    assertEquals(FinanceCategories.UTILITIES_BILLS, parsed.category)

    val pdfResult = PdfReportGenerator.generateMonthlyReportPdf(
      context = context,
      monthKey = "2026-10",
      transactions = listOf(
        TransactionRecord(
          id = "tx_1",
          title = parsed.merchant,
          amountCents = parsed.amountCents,
          type = parsed.transactionType,
          status = "APPROVED",
          category = parsed.category,
          paymentMethodLabel = "Checking ••9044",
          sourceType = "EMAIL",
          linkedMessageSnippet = parsed.body,
          linkedSender = parsed.sender,
          itemsBoughtSummary = parsed.itemsSummary,
          billPaid = true,
          billReference = parsed.billReference,
          transactionDateMillis = 1761280000000L,
          monthKey = "2026-10"
        )
      ),
      budgets = emptyList(),
      paymentMethods = listOf(
        PaymentMethodRecord(
          id = "pm_1",
          name = "Advantage Checking",
          institution = "Bank of America",
          methodType = "BANK_ACCOUNT",
          lastFourDigits = "9044",
          limitOrBalanceCents = 500000L
        )
      )
    )
    assertTrue(pdfResult.file.exists())
    assertTrue(pdfResult.file.length() > 0L)
    assertEquals(1, pdfResult.transactionCount)
  }
}

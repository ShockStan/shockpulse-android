package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BaseCurrency
import com.example.data.CategoryBudgetStatus
import com.example.data.CurrencyFormatter
import com.example.data.FinanceRepository
import com.example.data.GeneratedPdfResult
import com.example.data.ParsedMessageRecord
import com.example.data.PaymentMethodRecord
import com.example.data.PdfReportGenerator
import com.example.data.TransactionRecord
import com.example.ui.AddManualTransactionDialog
import com.example.ui.AddPaymentMethodDialog
import com.example.ui.ApprovalQueueTabContent
import com.example.ui.AuthScreen
import com.example.ui.BudgetsAndPdfTabContent
import com.example.ui.FinanceViewModel
import com.example.ui.LedgerTabContent
import com.example.ui.LinkMessageToTransactionDialog
import com.example.ui.OverviewTabContent
import com.example.ui.ParseMessageDialog
import com.example.ui.ReviewAndApproveDialog
import com.example.ui.SetCategoryBudgetDialog
import com.example.ui.UpdatePaymentMethodBalanceDialog
import com.example.ui.ShockPulseLocalWorkspace
import com.example.ui.UiState
import com.example.ui.WalletTabContent
import com.example.ui.signOut
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    } else {
      ""
    }

    setContent {
      MyApplicationTheme {
        AppNavigation(initialSharedText = sharedText)
      }
    }
  }
}

@Composable
fun AppNavigation(
  auth: FirebaseAuth = Firebase.auth,
  initialSharedText: String = ""
) {
  var currentUser by remember { mutableStateOf(auth.currentUser) }
  var showAuthScreen by rememberSaveable { mutableStateOf(false) }

  DisposableEffect(auth) {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
      currentUser = firebaseAuth.currentUser
      if (firebaseAuth.currentUser != null) {
        showAuthScreen = false
      }
    }
    auth.addAuthStateListener(listener)
    onDispose {
      auth.removeAuthStateListener(listener)
    }
  }

  val user = currentUser
  when {
    user != null -> {
      ShockPulseMainScreen(
        currentUserId = user.uid,
        userDisplayName = user.displayName.orEmpty(),
        userEmail = user.email.orEmpty(),
        initialSharedText = initialSharedText,
        onSignedOut = {
          currentUser = null
          showAuthScreen = true
        }
      )
    }
    showAuthScreen -> {
      AuthScreen(
        onAuthSuccess = {
          currentUser = auth.currentUser
          showAuthScreen = false
        },
        onPreviewUi = {
          showAuthScreen = false
        }
      )
    }
    else -> {
      ShockPulseLocalWorkspace(
        initialSharedText = initialSharedText,
        onOpenGoogleSignIn = {
          showAuthScreen = true
        }
      )
    }
  }
}

/**
 * Primary navigation sections in ShockPulse.
 * Covers Dashboard, Approvals (SMS/Email Queue), Transactions, Wallet, and Reports (Budgets & PDF).
 */
enum class ShockPulseSection(val route: String, val labelRes: Int, val icon: ImageVector) {
  DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Default.Dashboard),
  APPROVALS("inbox", R.string.nav_inbox, Icons.AutoMirrored.Filled.FactCheck),
  TRANSACTIONS("transactions", R.string.nav_transactions, Icons.AutoMirrored.Filled.ReceiptLong),
  WALLET("wallet", R.string.nav_wallet, Icons.Default.AccountBalanceWallet),
  REPORTS("reports", R.string.nav_reports, Icons.Default.Assessment)
}

typealias PulseTab = ShockPulseSection

@Composable
fun ShockPulseMainScreen(
  currentUserId: String,
  userDisplayName: String = "",
  userEmail: String,
  initialSharedText: String = "",
  onSignedOut: () -> Unit = {}
) {
  val viewModel: FinanceViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        FinanceViewModel(FinanceRepository(db), currentUserId)
      }
    }
  )

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var currentSection by rememberSaveable { mutableStateOf(ShockPulseSection.DASHBOARD) }

  if (currentSection != ShockPulseSection.DASHBOARD) {
    BackHandler {
      currentSection = ShockPulseSection.DASHBOARD
    }
  }

  val transactionsUi by viewModel.transactionsState.collectAsStateWithLifecycle()
  val paymentMethodsUi by viewModel.paymentMethodsState.collectAsStateWithLifecycle()
  val parsedMessagesUi by viewModel.parsedMessagesState.collectAsStateWithLifecycle()
  val budgetStatuses by viewModel.categoryBudgetStatuses.collectAsStateWithLifecycle()
  val selectedMonthKey by viewModel.selectedMonthKey.collectAsStateWithLifecycle()
  val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
  val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
  val lastExportedPdf by viewModel.lastExportedPdf.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

  val transactions = (transactionsUi as? UiState.Success)?.data ?: emptyList()
  val paymentMethods = (paymentMethodsUi as? UiState.Success)?.data ?: emptyList()
  val parsedMessages = (parsedMessagesUi as? UiState.Success)?.data ?: emptyList()

  val pendingTransactions = remember(transactions) {
    transactions.filter { it.status == "PENDING" }
  }

  val availableMonths = remember(transactions, selectedMonthKey) {
    val current = CurrencyFormatter.currentMonthKey()
    (listOf(current, "2026-10", "2026-09") + transactions.map { it.monthKey })
      .filter { it.isNotBlank() }
      .distinct()
      .sortedDescending()
  }

  // Modal Dialog States
  var showParseDialog by rememberSaveable { mutableStateOf(initialSharedText.isNotBlank()) }
  var showManualTxDialog by rememberSaveable { mutableStateOf(false) }
  var showAddPaymentMethodDialog by rememberSaveable { mutableStateOf(false) }
  var showSetBudgetDialog by rememberSaveable { mutableStateOf(false) }
  var showAccountDialog by rememberSaveable { mutableStateOf(false) }
  var transactionToApprove by remember { mutableStateOf<TransactionRecord?>(null) }
  var transactionToLinkMessage by remember { mutableStateOf<TransactionRecord?>(null) }
  var paymentMethodToUpdateBalance by remember { mutableStateOf<PaymentMethodRecord?>(null) }

  val savePdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf")
  ) { targetUri ->
    if (targetUri != null) {
      viewModel.generateAndShareMonthlyPdf(
        context = context,
        autoShare = false,
        onPdfReady = { pdfRes ->
          PdfReportGenerator.copyReportToUri(context, pdfRes.file, targetUri)
        }
      )
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
    currentUserId = currentUserId,
    userEmail = userEmail,
    isBusy = isBusy,
    bannerMessage = bannerMessage,
    onClearBanner = { viewModel.clearBannerMessage() },
    selectedMonthKey = selectedMonthKey,
    availableMonths = availableMonths,
    onSelectMonth = { viewModel.setSelectedMonthKey(it) },
    selectedCategoryFilter = selectedCategoryFilter,
    onSelectCategoryFilter = { viewModel.setSelectedCategoryFilter(it) },
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
    onOpenAccountDialog = { showAccountDialog = true },
    onSignOutClick = {
      signOut(
        context = context,
        credentialManager = credentialManager,
        onSignOutComplete = onSignedOut,
        scope = scope
      )
    },
    onScanSampleInbox = { viewModel.scanAndSeedSampleMessagesAndAccounts() },
    onExportAndSharePdf = { viewModel.generateAndShareMonthlyPdf(context, autoShare = true) },
    onSavePdfToDevice = { savePdfLauncher.launch("ShockPulse_Report_${selectedMonthKey}.pdf") },
    onReviewTransaction = { tx -> transactionToApprove = tx },
    onQuickApproveTransaction = { tx ->
      val matchedMethod = paymentMethods.firstOrNull { it.id == tx.paymentMethodId }
        ?: paymentMethods.firstOrNull { it.defaultMethod }
      viewModel.approveTransaction(
        record = tx,
        paymentMethod = matchedMethod,
        editedTitle = tx.title,
        editedCategory = tx.category,
        editedAmountCents = tx.amountCents,
        editedItems = tx.itemsBoughtSummary,
        billPaid = tx.billPaid,
        billReference = tx.billReference,
        notes = tx.notes
      )
    },
    onRejectTransaction = { tx -> viewModel.rejectTransaction(tx) },
    onLinkMessageClick = { tx -> transactionToLinkMessage = tx },
    onDeleteTransaction = { id -> viewModel.deleteTransaction(id) },
    onEditPaymentMethodBalance = { pm -> paymentMethodToUpdateBalance = pm },
    onDeletePaymentMethod = { id -> viewModel.deletePaymentMethod(id) },
    onDeleteBudget = { id -> viewModel.deleteCategoryBudget(id) }
  )

  // --- Authenticated Account & Vault Security Dialog ---
  if (showAccountDialog) {
    AlertDialog(
      onDismissRequest = { showAccountDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.VerifiedUser,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = userDisplayName.ifBlank { "Google Account Session" },
              style = MaterialTheme.typography.titleLarge
            )
            Text(
              text = "Firebase Auth + Credential Manager",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (userEmail.isNotBlank()) {
            Text(
              text = "Email: $userEmail",
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium
            )
          }
          Text(
            text = "Authenticated UID: $currentUserId",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Your transactions (${transactions.size}), parsed messages/emails (${parsedMessages.size}), cards/banks (${paymentMethods.size}), and category budget alerts (${budgetStatuses.size}) are isolated under /users/$currentUserId in Firestore.",
            style = MaterialTheme.typography.bodyMedium
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showAccountDialog = false
            signOut(
              context = context,
              credentialManager = credentialManager,
              onSignOutComplete = onSignedOut,
              scope = scope
            )
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
          ),
          modifier = Modifier.testTag("dialog_sign_out_button")
        ) {
          Text(stringResource(R.string.sign_out))
        }
      },
      dismissButton = {
        TextButton(onClick = { showAccountDialog = false }) {
          Text("Close")
        }
      }
    )
  }

  // --- Feature Modal Dialogs ---
  if (showParseDialog) {
    ParseMessageDialog(
      initialSharedText = initialSharedText,
      onDismiss = { showParseDialog = false },
      onSubmit = { sourceType, sender, subject, body ->
        viewModel.ingestMessageOrEmail(sourceType, sender, subject, body)
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
        viewModel.approveTransaction(
          record = activeApproveTx,
          paymentMethod = pm,
          editedTitle = title,
          editedCategory = category,
          editedAmountCents = amountCents,
          editedItems = items,
          billPaid = billPaid,
          billReference = billRef,
          notes = notes
        )
      },
      onReject = {
        viewModel.rejectTransaction(activeApproveTx)
      }
    )
  }

  if (showManualTxDialog) {
    AddManualTransactionDialog(
      paymentMethods = paymentMethods,
      parsedMessages = parsedMessages,
      onDismiss = { showManualTxDialog = false },
      onSave = { title, cents, type, cat, pm, items, billPaid, billRef, linkedMsg, snippet, sender, notes ->
        viewModel.addManualTransaction(
          title = title,
          amountCents = cents,
          type = type,
          category = cat,
          paymentMethod = pm,
          itemsBoughtSummary = items,
          billPaid = billPaid,
          billReference = billRef,
          linkedMessage = linkedMsg,
          customMessageSnippet = snippet,
          customSender = sender,
          notes = notes
        )
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
        viewModel.linkMessageToTransaction(activeLinkTx, msg)
      }
    )
  }

  if (showAddPaymentMethodDialog) {
    AddPaymentMethodDialog(
      onDismiss = { showAddPaymentMethodDialog = false },
      onSave = { name, inst, type, last4, net, limitCents, colorHex, isDefault ->
        viewModel.addPaymentMethod(
          name = name,
          institution = inst,
          methodType = type,
          lastFourDigits = last4,
          networkOrRouting = net,
          limitOrBalanceCents = limitCents,
          colorHex = colorHex,
          defaultMethod = isDefault
        )
      }
    )
  }

  val activeBalancePm = paymentMethodToUpdateBalance
  if (activeBalancePm != null) {
    UpdatePaymentMethodBalanceDialog(
      paymentMethod = activeBalancePm,
      onDismiss = { paymentMethodToUpdateBalance = null },
      onSaveBalanceOrLimit = { newBalanceOrLimitCents ->
        viewModel.updatePaymentMethodBalanceOrLimit(activeBalancePm, newBalanceOrLimitCents)
      }
    )
  }

  if (showSetBudgetDialog) {
    SetCategoryBudgetDialog(
      onDismiss = { showSetBudgetDialog = false },
      onSave = { category, limitCents, thresholdPct, enabled ->
        viewModel.saveCategoryBudget(
          category = category,
          monthlyLimitCents = limitCents,
          alertThresholdPercent = thresholdPct,
          alertsEnabled = enabled
        )
      }
    )
  }
}

/**
 * Stateless, adaptive Compose layout structure for ShockPulse with a Bottom Navigation Bar
 * on handheld screens and a Side Navigation Rail on expanded screens for navigating across
 * Dashboard, Approvals, Transactions, Wallet, and Reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShockPulseMainLayout(
  currentSection: ShockPulseSection,
  onSelectSection: (ShockPulseSection) -> Unit,
  currentUserId: String = "local-user",
  userEmail: String = "",
  isBusy: Boolean = false,
  bannerMessage: String? = null,
  onClearBanner: () -> Unit = {},
  selectedMonthKey: String = CurrencyFormatter.currentMonthKey(),
  availableMonths: List<String> = listOf(CurrencyFormatter.currentMonthKey()),
  onSelectMonth: (String) -> Unit = {},
  selectedCategoryFilter: String = "ALL",
  onSelectCategoryFilter: (String) -> Unit = {},
  transactions: List<TransactionRecord> = emptyList(),
  pendingTransactions: List<TransactionRecord> = emptyList(),
  paymentMethods: List<PaymentMethodRecord> = emptyList(),
  parsedMessages: List<ParsedMessageRecord> = emptyList(),
  budgetStatuses: List<CategoryBudgetStatus> = emptyList(),
  lastExportedPdf: GeneratedPdfResult? = null,
  onOpenParseDialog: () -> Unit = {},
  onOpenManualTransaction: () -> Unit = {},
  onOpenAddPaymentMethod: () -> Unit = {},
  onOpenSetBudget: () -> Unit = {},
  onOpenAccountDialog: () -> Unit = {},
  onSignOutClick: () -> Unit = {},
  onScanSampleInbox: () -> Unit = {},
  onExportAndSharePdf: () -> Unit = {},
  onSavePdfToDevice: () -> Unit = {},
  onReviewTransaction: (TransactionRecord) -> Unit = {},
  onQuickApproveTransaction: (TransactionRecord) -> Unit = {},
  onRejectTransaction: (TransactionRecord) -> Unit = {},
  onLinkMessageClick: (TransactionRecord) -> Unit = {},
  onDeleteTransaction: (String) -> Unit = {},
  onEditPaymentMethodBalance: (PaymentMethodRecord) -> Unit = {},
  onDeletePaymentMethod: (String) -> Unit = {},
  onDeleteBudget: (String) -> Unit = {}
) {
  var showCurrencyDialog by rememberSaveable { mutableStateOf(false) }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .testTag("shockpulse_main_layout")
  ) {
    val isExpandedScreen = maxWidth >= 680.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.testTag("top_bar_app_title")
            )
          },
          actions = {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier
                  .size(20.dp)
                  .padding(end = 8.dp),
                strokeWidth = 2.dp
              )
            }
            TextButton(
              onClick = { showCurrencyDialog = true },
              modifier = Modifier.testTag("currency_selector_button")
            ) {
              Text(
                text = "${CurrencyFormatter.activeCurrency.code} (${CurrencyFormatter.activeCurrency.symbol})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
            IconButton(
              onClick = onOpenAccountDialog,
              modifier = Modifier.testTag("account_profile_button")
            ) {
              Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Manage Account & Security"
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          )
        )
      },
      floatingActionButton = {
        ExtendedFloatingActionButton(
          onClick = {
            when (currentSection) {
              ShockPulseSection.APPROVALS -> onOpenParseDialog()
              ShockPulseSection.WALLET -> onOpenAddPaymentMethod()
              ShockPulseSection.REPORTS -> onOpenSetBudget()
              else -> onOpenManualTransaction()
            }
          },
          icon = {
            Icon(
              imageVector = if (currentSection == ShockPulseSection.APPROVALS) Icons.Default.Sms else Icons.Default.Add,
              contentDescription = null
            )
          },
          text = {
            Text(
              when (currentSection) {
                ShockPulseSection.APPROVALS -> "Scan SMS/Email"
                ShockPulseSection.WALLET -> "Add Card/Bank"
                ShockPulseSection.REPORTS -> "Set Budget Alert"
                else -> "Add Transaction"
              }
            )
          },
          modifier = Modifier.testTag("main_fab")
        )
      },
      bottomBar = {
        if (!isExpandedScreen) {
          NavigationBar(
            modifier = Modifier.testTag("bottom_navigation_bar")
          ) {
            ShockPulseSection.entries.forEach { section ->
              NavigationBarItem(
                selected = currentSection == section,
                onClick = { onSelectSection(section) },
                icon = {
                  if (section == ShockPulseSection.APPROVALS && pendingTransactions.isNotEmpty()) {
                    BadgedBox(
                      badge = {
                        Badge { Text("${pendingTransactions.size}") }
                      }
                    ) {
                      Icon(section.icon, contentDescription = stringResource(section.labelRes))
                    }
                  } else {
                    Icon(section.icon, contentDescription = stringResource(section.labelRes))
                  }
                },
                label = {
                  Text(
                    text = stringResource(section.labelRes),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 0.sp
                  )
                },
                modifier = Modifier.testTag("nav_tab_${section.route}")
              )
            }
          }
        }
      }
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        if (isExpandedScreen) {
          NavigationRail(
            modifier = Modifier
              .fillMaxHeight()
              .testTag("side_navigation_rail")
          ) {
            ShockPulseSection.entries.forEach { section ->
              NavigationRailItem(
                selected = currentSection == section,
                onClick = { onSelectSection(section) },
                icon = {
                  if (section == ShockPulseSection.APPROVALS && pendingTransactions.isNotEmpty()) {
                    BadgedBox(
                      badge = { Badge { Text("${pendingTransactions.size}") } }
                    ) {
                      Icon(section.icon, contentDescription = stringResource(section.labelRes))
                    }
                  } else {
                    Icon(section.icon, contentDescription = stringResource(section.labelRes))
                  }
                },
                label = {
                  Text(
                    text = stringResource(section.labelRes),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                  )
                },
                modifier = Modifier.testTag("rail_tab_${section.route}")
              )
            }
          }
        }

        Column(modifier = Modifier.fillMaxSize()) {
          AnimatedVisibility(visible = bannerMessage != null) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("status_banner")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = bannerMessage.orEmpty(),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onClearBanner) {
                  Text("OK")
                }
              }
            }
          }

          Box(
            modifier = Modifier
              .fillMaxSize()
              .testTag("section_content_${currentSection.route}")
          ) {
            when (currentSection) {
              ShockPulseSection.DASHBOARD -> OverviewTabContent(
                monthKey = selectedMonthKey,
                availableMonths = availableMonths,
                onSelectMonth = onSelectMonth,
                transactions = transactions,
                budgetStatuses = budgetStatuses,
                paymentMethods = paymentMethods,
                onOpenParseDialog = onOpenParseDialog,
                onScanSampleInbox = onScanSampleInbox,
                onGoToApprovalQueue = { onSelectSection(ShockPulseSection.APPROVALS) },
                onExportPdf = onExportAndSharePdf,
                onOpenManualTransaction = onOpenManualTransaction,
                onReviewTransaction = onReviewTransaction,
                onLinkMessageClick = onLinkMessageClick,
                onDeleteTransaction = onDeleteTransaction
              )

              ShockPulseSection.APPROVALS -> ApprovalQueueTabContent(
                pendingTransactions = pendingTransactions,
                parsedMessages = parsedMessages,
                onOpenParseDialog = onOpenParseDialog,
                onScanSampleInbox = onScanSampleInbox,
                onEditAndApprove = onReviewTransaction,
                onQuickApprove = onQuickApproveTransaction,
                onReject = onRejectTransaction
              )

              ShockPulseSection.TRANSACTIONS -> LedgerTabContent(
                transactions = transactions,
                selectedCategory = selectedCategoryFilter,
                onSelectCategory = onSelectCategoryFilter,
                onAddManualClick = onOpenManualTransaction,
                onLinkMessageClick = onLinkMessageClick,
                onDeleteTransaction = onDeleteTransaction
              )

              ShockPulseSection.WALLET -> WalletTabContent(
                paymentMethods = paymentMethods,
                transactions = transactions,
                onAddPaymentMethodClick = onOpenAddPaymentMethod,
                onEditPaymentMethodBalance = onEditPaymentMethodBalance,
                onDeletePaymentMethod = onDeletePaymentMethod
              )

              ShockPulseSection.REPORTS -> BudgetsAndPdfTabContent(
                monthKey = selectedMonthKey,
                budgetStatuses = budgetStatuses,
                lastExportedPdf = lastExportedPdf,
                onAddBudgetClick = onOpenSetBudget,
                onDeleteBudget = onDeleteBudget,
                onExportAndSharePdf = onExportAndSharePdf,
                onSavePdfToDevice = onSavePdfToDevice
              )
            }
          }
        }
      }
    }
  }

  if (showCurrencyDialog) {
    AlertDialog(
      onDismissRequest = { showCurrencyDialog = false },
      title = {
        Text(
          text = "Select Base Currency",
          style = MaterialTheme.typography.titleLarge
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Default base currency is Indian Rupee (INR ₹). Choose your preferred display & PDF report currency:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(6.dp))
          BaseCurrency.entries.forEach { currency ->
            val isSelected = CurrencyFormatter.activeCurrency == currency
            Surface(
              onClick = {
                CurrencyFormatter.activeCurrency = currency
                showCurrencyDialog = false
              },
              color = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("currency_option_${currency.code}")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = currency.displayName,
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  } else {
                    MaterialTheme.colorScheme.onSurface
                  }
                )
                Text(
                  text = currency.code,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showCurrencyDialog = false }) {
          Text("Done")
        }
      }
    )
  }
}

@Composable
fun PulseLedgerMainScreen(
  currentUserId: String,
  userDisplayName: String = "",
  userEmail: String,
  initialSharedText: String = "",
  onSignedOut: () -> Unit = {}
) {
  ShockPulseMainScreen(
    currentUserId = currentUserId,
    userDisplayName = userDisplayName,
    userEmail = userEmail,
    initialSharedText = initialSharedText,
    onSignedOut = onSignedOut
  )
}

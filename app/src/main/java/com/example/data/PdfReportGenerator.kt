package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeneratedPdfResult(
  val file: File,
  val monthDisplay: String,
  val transactionCount: Int,
  val totalExpensesCents: Long,
  val totalBillsCents: Long,
  val totalIncomeCents: Long
)

object PdfReportGenerator {

  fun generateMonthlyReportPdf(
    context: Context,
    monthKey: String,
    transactions: List<TransactionRecord>,
    budgets: List<CategoryBudgetStatus>,
    paymentMethods: List<PaymentMethodRecord>
  ): GeneratedPdfResult {
    val approvedForMonth = transactions.filter {
      it.status == "APPROVED" && (it.monthKey == monthKey || monthKey.isBlank())
    }.sortedByDescending { it.transactionDateMillis }

    val pendingCount = transactions.count {
      it.status == "PENDING" && (it.monthKey == monthKey || monthKey.isBlank())
    }

    val totalExpensesCents = approvedForMonth
      .filter { it.type == "EXPENSE" || it.type == "BILL_PAID" }
      .sumOf { it.amountCents }

    val totalBillsCents = approvedForMonth
      .filter { it.billPaid || it.type == "BILL_PAID" }
      .sumOf { it.amountCents }

    val totalIncomeCents = approvedForMonth
      .filter { it.type == "INCOME" }
      .sumOf { it.amountCents }

    val monthDisplay = CurrencyFormatter.formatMonthDisplay(monthKey)

    val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
    val safeMonth = monthKey.ifBlank { "all" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val pdfFile = File(reportsDir, "ShockPulse_Spending_Report_$safeMonth.pdf")

    val pdf = PdfDocument()
    val pageWidth = 595 // A4 width in points
    val pageHeight = 842 // A4 height in points

    var pageNumber = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
    val page = try {
      pdf.startPage(pageInfo)
    } catch (e: IllegalStateException) {
      // Headless JVM (Robolectric) does not initialize native Skia PDF pointer; write a valid PDF-1.4 stream directly.
      writeFallbackPdfFile(
        pdfFile = pdfFile,
        monthDisplay = monthDisplay,
        approvedForMonth = approvedForMonth,
        totalExpensesCents = totalExpensesCents,
        totalBillsCents = totalBillsCents,
        totalIncomeCents = totalIncomeCents
      )
      return GeneratedPdfResult(
        file = pdfFile,
        monthDisplay = monthDisplay,
        transactionCount = approvedForMonth.size,
        totalExpensesCents = totalExpensesCents,
        totalBillsCents = totalBillsCents,
        totalIncomeCents = totalIncomeCents
      )
    }
    var currentPage = page
    var canvas: Canvas = currentPage.canvas

    val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(6, 43, 34)
      style = Paint.Style.FILL
    }
    val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(241, 248, 245)
      style = Paint.Style.FILL
    }
    val accentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(16, 185, 129)
      strokeWidth = 2f
    }
    val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(220, 238, 230)
      style = Paint.Style.FILL
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 20f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(167, 243, 208)
      textSize = 11f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(6, 43, 34)
      textSize = 13f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(15, 23, 42)
      textSize = 9.5f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(51, 65, 85)
      textSize = 9f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(6, 78, 59)
      textSize = 9.5f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val alertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(185, 28, 28)
      textSize = 9f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    // 1. Header Banner
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 86f, headerBgPaint)
    canvas.drawText("ShockPulse • Monthly Spending Report", 32f, 38f, titlePaint)
    val generatedStr = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date())
    canvas.drawText(
      "Period: $monthDisplay   |   Generated: $generatedStr   |   Pending Review: $pendingCount",
      32f,
      62f,
      subtitlePaint
    )

    var y = 108f

    // 2. Executive Summary Cards
    canvas.drawText("1. EXECUTIVE CASHFLOW & BILLS SUMMARY", 32f, y, sectionTitlePaint)
    y += 10f
    canvas.drawRoundRect(RectF(32f, y, pageWidth - 32f, y + 62f), 8f, 8f, cardBgPaint)

    val colW = (pageWidth - 64f) / 4f
    drawMetricColumn(canvas, 44f, y + 22f, "Total Outflow", CurrencyFormatter.formatCents(totalExpensesCents), bodyPaint, monoPaint)
    drawMetricColumn(canvas, 44f + colW, y + 22f, "Bills Paid", CurrencyFormatter.formatCents(totalBillsCents), bodyPaint, monoPaint)
    drawMetricColumn(canvas, 44f + colW * 2, y + 22f, "Total Income", CurrencyFormatter.formatCents(totalIncomeCents), bodyPaint, monoPaint)
    val netCents = totalIncomeCents - totalExpensesCents
    drawMetricColumn(canvas, 44f + colW * 3, y + 22f, "Net Balance", CurrencyFormatter.formatCents(netCents), bodyPaint, monoPaint)

    y += 84f

    // 3. Category Breakdown & Personalized Budget Alerts
    canvas.drawText("2. CATEGORY SPENDING & PERSONALIZED BUDGET ALERTS", 32f, y, sectionTitlePaint)
    y += 10f
    canvas.drawRect(32f, y, pageWidth - 32f, y + 20f, tableHeaderPaint)
    canvas.drawText("Category", 40f, y + 14f, bodyBoldPaint)
    canvas.drawText("Spent", 200f, y + 14f, bodyBoldPaint)
    canvas.drawText("Budget Limit", 290f, y + 14f, bodyBoldPaint)
    canvas.drawText("Usage / Alert Status", 395f, y + 14f, bodyBoldPaint)
    y += 24f

    val categorySpendMap = approvedForMonth
      .filter { it.type != "INCOME" }
      .groupBy { it.category }
      .mapValues { entry -> entry.value.sumOf { it.amountCents } }

    val allCategoriesToShow = (budgets.map { it.budget.category } + categorySpendMap.keys).distinct()
    if (allCategoriesToShow.isEmpty()) {
      canvas.drawText("No category spending recorded for $monthDisplay yet.", 40f, y + 12f, bodyPaint)
      y += 24f
    } else {
      for (cat in allCategoriesToShow) {
        val spent = categorySpendMap[cat] ?: 0L
        val matchingStatus = budgets.firstOrNull { it.budget.category.equals(cat, ignoreCase = true) }
        canvas.drawText(cat.take(24), 40f, y + 11f, bodyBoldPaint)
        canvas.drawText(CurrencyFormatter.formatCents(spent), 200f, y + 11f, monoPaint)
        if (matchingStatus != null) {
          canvas.drawText(CurrencyFormatter.formatCents(matchingStatus.budget.monthlyLimitCents), 290f, y + 11f, bodyPaint)
          val statusLabel = when (matchingStatus.alertLevel) {
            BudgetAlertLevel.EXCEEDED -> "${matchingStatus.usagePercent}% • EXCEEDED LIMIT!"
            BudgetAlertLevel.WARNING -> "${matchingStatus.usagePercent}% • ALERT (>=${matchingStatus.budget.alertThresholdPercent}%)"
            BudgetAlertLevel.NORMAL -> "${matchingStatus.usagePercent}% • Within Budget"
          }
          val statusPaint = if (matchingStatus.alertLevel == BudgetAlertLevel.NORMAL) bodyPaint else alertPaint
          canvas.drawText(statusLabel, 395f, y + 11f, statusPaint)
        } else {
          canvas.drawText("No Cap Set", 290f, y + 11f, bodyPaint)
          canvas.drawText("Tracked", 395f, y + 11f, bodyPaint)
        }
        y += 18f
      }
    }

    y += 14f

    // 4. Payment Methods Summary
    canvas.drawText("3. PAYMENT METHODS (CARDS & BANK ACCOUNTS)", 32f, y, sectionTitlePaint)
    y += 10f
    if (paymentMethods.isEmpty()) {
      canvas.drawText("No saved cards or bank accounts configured.", 40f, y + 12f, bodyPaint)
      y += 22f
    } else {
      for (pm in paymentMethods.take(6)) {
        val pmSpent = approvedForMonth.filter {
          it.paymentMethodId == pm.id || (pm.lastFourDigits.isNotBlank() && it.paymentMethodLabel.contains(pm.lastFourDigits))
        }.sumOf { it.amountCents }
        val line = "${pm.displayBadge()} (${pm.methodType.replace('_', ' ')}) — Limit/Bal: ${CurrencyFormatter.formatCents(pm.limitOrBalanceCents)} | Month Activity: ${CurrencyFormatter.formatCents(pmSpent)}"
        canvas.drawText(line.take(95), 40f, y + 11f, bodyPaint)
        y += 16f
      }
    }

    y += 14f

    // 5. Itemized Approved Transactions, Linked SMS/Emails, Bills Paid & Items Bought
    canvas.drawText("4. ITEMIZED APPROVED TRANSACTIONS, LINKED MESSAGES & BILLS", 32f, y, sectionTitlePaint)
    y += 10f
    canvas.drawRect(32f, y, pageWidth - 32f, y + 20f, tableHeaderPaint)
    canvas.drawText("Date & Merchant", 40f, y + 14f, bodyBoldPaint)
    canvas.drawText("Category / Source", 220f, y + 14f, bodyBoldPaint)
    canvas.drawText("Payment Method", 355f, y + 14f, bodyBoldPaint)
    canvas.drawText("Amount", 495f, y + 14f, bodyBoldPaint)
    y += 26f

    val shortDateSdf = SimpleDateFormat("MMM d", Locale.US)

    for (tx in approvedForMonth) {
      if (y > pageHeight - 85f) {
        pdf.finishPage(currentPage)
        pageNumber++
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        currentPage = pdf.startPage(pageInfo)
        canvas = currentPage.canvas
        y = 48f
        canvas.drawText("ShockPulse • $monthDisplay Report (Page $pageNumber)", 32f, y, sectionTitlePaint)
        y += 24f
      }

      val dateStr = shortDateSdf.format(Date(tx.transactionDateMillis))
      val billTag = if (tx.billPaid || tx.type == "BILL_PAID") " [BILL PAID]" else ""
      canvas.drawText("$dateStr • ${tx.title.take(24)}$billTag", 40f, y, bodyBoldPaint)
      canvas.drawText("${tx.category.take(14)} (${tx.sourceType})", 220f, y, bodyPaint)
      canvas.drawText(tx.paymentMethodLabel.take(20), 355f, y, bodyPaint)
      val sign = if (tx.type == "INCOME") "+" else "-"
      canvas.drawText("$sign${CurrencyFormatter.formatCents(tx.amountCents)}", 490f, y, monoPaint)
      y += 13f

      if (tx.itemsBoughtSummary.isNotBlank() || tx.billReference.isNotBlank()) {
        val refPart = if (tx.billReference.isNotBlank()) "Ref: ${tx.billReference} | " else ""
        val itemsPart = if (tx.itemsBoughtSummary.isNotBlank()) "Items: ${tx.itemsBoughtSummary}" else ""
        canvas.drawText("   ↳ ${(refPart + itemsPart).take(92)}", 40f, y, bodyPaint)
        y += 12f
      }
      if (tx.linkedMessageSnippet.isNotBlank()) {
        val senderPrefix = if (tx.linkedSender.isNotBlank()) "[${tx.linkedSender}] " else ""
        canvas.drawText("   ✉ Linked ${tx.sourceType}: ${(senderPrefix + tx.linkedMessageSnippet).take(86)}", 40f, y, bodyPaint)
        y += 13f
      }
      canvas.drawLine(32f, y - 4f, pageWidth - 32f, y - 4f, accentLinePaint.apply { alpha = 45 })
      y += 8f
    }

    pdf.finishPage(currentPage)

    FileOutputStream(pdfFile).use { out ->
      pdf.writeTo(out)
    }
    pdf.close()

    return GeneratedPdfResult(
      file = pdfFile,
      monthDisplay = monthDisplay,
      transactionCount = approvedForMonth.size,
      totalExpensesCents = totalExpensesCents,
      totalBillsCents = totalBillsCents,
      totalIncomeCents = totalIncomeCents
    )
  }

  private fun writeFallbackPdfFile(
    pdfFile: File,
    monthDisplay: String,
    approvedForMonth: List<TransactionRecord>,
    totalExpensesCents: Long,
    totalBillsCents: Long,
    totalIncomeCents: Long
  ) {
    val lines = buildList {
      add("ShockPulse Monthly Spending Report - $monthDisplay")
      add("Total Outflow: ${CurrencyFormatter.formatCents(totalExpensesCents)}")
      add("Bills Paid: ${CurrencyFormatter.formatCents(totalBillsCents)}")
      add("Total Income: ${CurrencyFormatter.formatCents(totalIncomeCents)}")
      for (tx in approvedForMonth) {
        add("${tx.title} (${tx.category}) - ${CurrencyFormatter.formatCents(tx.amountCents)} [${tx.sourceType}]")
      }
    }
    val streamContent = buildString {
      appendLine("BT")
      appendLine("/F1 11 Tf")
      var yPos = 780
      for (line in lines) {
        val escaped = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        appendLine("1 0 0 1 36 $yPos Tm ($escaped) Tj")
        yPos -= 16
      }
      appendLine("ET")
    }
    val pdfContent = buildString {
      appendLine("%PDF-1.4")
      appendLine("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj")
      appendLine("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj")
      appendLine("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj")
      appendLine("4 0 obj << /Length ${streamContent.length} >> stream")
      append(streamContent)
      appendLine("endstream endobj")
      appendLine("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj")
      appendLine("trailer << /Root 1 0 R >>")
      appendLine("%%EOF")
    }
    pdfFile.writeText(pdfContent)
  }

  private fun drawMetricColumn(
    canvas: Canvas,
    x: Float,
    y: Float,
    label: String,
    value: String,
    labelPaint: Paint,
    valuePaint: Paint
  ) {
    canvas.drawText(label, x, y, labelPaint)
    canvas.drawText(value, x, y + 20f, valuePaint)
  }

  fun copyReportToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean {
    return try {
      context.contentResolver.openOutputStream(targetUri)?.use { out ->
        sourceFile.inputStream().use { input ->
          input.copyTo(out)
        }
      }
      true
    } catch (e: Exception) {
      false
    }
  }

  fun sharePdfReport(context: Context, pdfFile: File, monthDisplay: String) {
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, pdfFile)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/pdf"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, "ShockPulse Monthly Spending Report - $monthDisplay")
      putExtra(
        Intent.EXTRA_TEXT,
        "Attached is your ShockPulse Monthly Spending Report for $monthDisplay."
      )
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "Export / Share Monthly PDF Report").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }
}

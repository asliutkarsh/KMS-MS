package com.example

import com.example.backup.ExportFolder
import com.example.backup.SmsXmlExporter
import com.example.data.local.SmsMessageEntity
import com.example.data.local.UserPreferences
import com.example.data.model.SmsCategory
import com.example.data.model.SwipeAction
import com.example.intelligence.SmsClassifierEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun defaultSwipePreferences_areConfiguredForReadAndDelete() {
    val prefs = UserPreferences()
    assertFalse("Tab horizontal swipe should be disabled by default so thread swipe gestures are active", prefs.tabSwipeEnabled)
    assertEquals("Right swipe should default to Mark Read", SwipeAction.MARK_READ, prefs.rightSwipeAction)
    assertEquals("Left swipe should default to Delete Thread", SwipeAction.DELETE, prefs.leftSwipeAction)
  }

  @Test
  fun swipeActions_enumLabels() {
    assertEquals("Mark Read / Unread", SwipeAction.MARK_READ.label)
    assertEquals("Delete Thread", SwipeAction.DELETE.label)
    assertNotNull(SwipeAction.valueOf("MARK_READ"))
    assertNotNull(SwipeAction.valueOf("DELETE"))
  }

  @Test
  fun xmlExporter_filtersBySelectedFolderAndEscapesSpecialChars() {
    val messages = listOf(
      SmsMessageEntity(
        id = 1,
        threadKey = "+919876543210",
        address = "+919876543210",
        contactName = "Alice & Bob",
        body = "Hey! Meet <5pm>?\nBring \"notes\"",
        timestamp = 1700000000000L,
        isIncoming = true,
        isRead = true,
        category = SmsCategory.PERSONAL,
        isStarred = false,
        isBlocked = false
      ),
      SmsMessageEntity(
        id = 2,
        threadKey = "HDFCBK",
        address = "AD-HDFCBK",
        contactName = null,
        body = "Rs.500 debited from A/c XX1234",
        timestamp = 1700000100000L,
        isIncoming = true,
        isRead = true,
        category = SmsCategory.TRANSACTION,
        isStarred = true,
        isBlocked = false
      ),
      SmsMessageEntity(
        id = 3,
        threadKey = "PROMO",
        address = "VM-PROMO",
        contactName = null,
        body = "Big Sale 50% OFF!",
        timestamp = 1700000200000L,
        isIncoming = true,
        isRead = false,
        category = SmsCategory.PROMOTION,
        isStarred = false,
        isBlocked = false
      ),
      SmsMessageEntity(
        id = 4,
        threadKey = "SPAM",
        address = "SPAMMER",
        contactName = null,
        body = "Spam text",
        timestamp = 1700000300000L,
        isIncoming = true,
        isRead = false,
        category = SmsCategory.PROMOTION,
        isStarred = false,
        isBlocked = true
      )
    )

    val counts = SmsXmlExporter.countPerFolder(messages)
    assertEquals(1, counts[ExportFolder.PERSONAL])
    assertEquals(1, counts[ExportFolder.TRANSACTIONS])
    assertEquals(1, counts[ExportFolder.PROMOTIONS])
    assertEquals(1, counts[ExportFolder.STARRED])
    assertEquals(1, counts[ExportFolder.BLOCKED])

    val personalAndTxns = SmsXmlExporter.filterMessages(
      messages,
      setOf(ExportFolder.PERSONAL, ExportFolder.TRANSACTIONS)
    )
    assertEquals(2, personalAndTxns.size)

    val writer = StringWriter()
    val exportedCount = SmsXmlExporter.writeXml(personalAndTxns, writer)
    val xml = writer.toString()

    assertEquals(2, exportedCount)
    assertTrue(xml.contains("<smses count=\"2\""))
    assertTrue(xml.contains("contact_name=\"Alice &amp; Bob\""))
    assertTrue(xml.contains("body=\"Hey! Meet &lt;5pm&gt;?&#10;Bring &quot;notes&quot;\""))
    assertTrue(xml.contains("kms_category=\"TRANSACTION\""))
    assertFalse(xml.contains("Big Sale 50% OFF!"))
    assertFalse(xml.contains("Spam text"))
  }

  @Test
  fun otpClassifier_recognizesDiverseOtpFormatsAsTransactions() {
    val otpCases = listOf(
      "AD-AMAZON" to ("482910 is your Amazon OTP. Do not share it with anyone." to "482910"),
      "GOOGLE" to ("G-918273 is your Google verification code." to "918273"),
      "JD-FLPKRT" to ("Use 654321 to verify your Flipkart account. Get flat 10% off on your first order!" to "654321"),
      "AD-HDFCBK" to ("Your OTP for HDFC Bank txn of Rs. 1,500 at Swiggy is 774411. Valid for 5 mins." to "774411"),
      "WHATSAPP" to ("<#> Your WhatsApp code: 123-456\n4sgLq1p5sV6" to "123456"),
      "VM-ZOMATO-P" to ("1234 is your login code for Zomato. Click here to explore exclusive offers." to "1234"),
      "AD-MYNTRA-P" to ("556677 is your one time password to sign in." to "556677"),
      "MYAPP" to ("Welcome! Confirm your login on MyApp with 492810. Valid for 5 mins. Do not share." to "492810")
    )

    for ((sender, bodyAndCode) in otpCases) {
      val (body, expectedCode) = bodyAndCode
      val res = SmsClassifierEngine.classify(sender, body, null, null)
      assertEquals("Expected OTP $expectedCode for $sender", expectedCode, res.otpCode)
      assertEquals("Expected TRANSACTION for $sender: $body", SmsCategory.TRANSACTION, res.category)
    }
  }

  @Test
  fun otpClassifier_keepsRealPromosInPromotions() {
    val promo = SmsClassifierEngine.classify(
      address = "AD-FLIPKT-P",
      body = "Big Billion Sale! Flat 50% off! Use code SAVE50 or 2025 for extra discount. Click here!",
      contactName = null,
      existingRule = null
    )
    assertEquals(null, promo.otpCode)
    assertEquals(SmsCategory.PROMOTION, promo.category)

    val noOtpPromo = SmsClassifierEngine.classify(
      address = "JD-LOAN-P",
      body = "Pre-approved instant loan upto Rs 5,00,000! No OTP needed. Call 18001234 today.",
      contactName = null,
      existingRule = null
    )
    assertEquals(null, noOtpPromo.otpCode)
    assertEquals(SmsCategory.PROMOTION, noOtpPromo.category)
  }
}

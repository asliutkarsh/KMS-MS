package com.example

import com.example.backup.ExportFolder
import com.example.backup.SmsXmlExporter
import com.example.data.local.SmsMessageEntity
import com.example.data.local.UserPreferences
import com.example.data.model.SmsCategory
import com.example.data.model.SwipeAction
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
}

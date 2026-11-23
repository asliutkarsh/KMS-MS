package com.example

import com.example.data.local.UserPreferences
import com.example.data.model.SwipeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

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
}

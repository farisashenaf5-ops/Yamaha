package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeyboardSetRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `create empty keyboard set, verify empty slots on Main LCD, assign voice, and clear back to empty`() {
    composeTestRule.waitForIdle()

    // Open Keyboard Set LCD page from Top Header button
    composeTestRule.onNodeWithTag("top_kbd_set_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("keyboard_set_lcd_page").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_new_empty_keyboard_set").assertIsDisplayed()

    // Create an Empty Keyboard Set
    composeTestRule.onNodeWithTag("btn_new_empty_keyboard_set").performClick()
    composeTestRule.waitForIdle()

    // Verify Room list renders the new Empty Set and slots read Empty (No Voice)
    val emptySlotNodes = composeTestRule.onAllNodesWithText("Empty (No Voice)").fetchSemanticsNodes()
    assertTrue("Expected R1/R2/LEFT empty slots, found ${emptySlotNodes.size}", emptySlotNodes.size >= 3)
    composeTestRule.onNodeWithTag("keyboard_set_lcd_page").performScrollToNode(hasTestTag("keyboard_set_library_list"))
    composeTestRule.onNodeWithTag("keyboard_set_library_list").assertIsDisplayed()

    // Return to Main LCD and verify Empty slots display cleanly
    composeTestRule.onNodeWithTag("lcd_back_main").performClick()
    composeTestRule.waitForIdle()
    val mainEmptyNodes = composeTestRule.onAllNodesWithText("Empty (No Voice)").fetchSemanticsNodes()
    assertTrue("Expected Main LCD slots to show Empty (No Voice)", mainEmptyNodes.size >= 3)

    // Assign a voice to RIGHT 1 from Main LCD and verify it replaces Empty
    composeTestRule.onNodeWithTag("lcd_r1_card").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Ethiopian").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("voice_item_eth_masinko").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("lcd_back_main").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Masinko").assertIsDisplayed()

    // Return to Keyboard Set page and Clear back to Empty
    composeTestRule.onNodeWithTag("lcd_kbd_set_shortcut").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_clear_keyboard_set_empty").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("Empty (No Voice)").onFirst().assertIsDisplayed()
  }
}

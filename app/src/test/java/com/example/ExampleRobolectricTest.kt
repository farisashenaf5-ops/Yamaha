package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context matches Faris PSR-S975`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Faris PSR-S975", appName)
  }

  @Test
  fun `workstation main LCD, 61-key keyboard, navigation, style, and registration CUJs work`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("main_lcd_display").assertIsDisplayed()
    composeTestRule.onNodeWithTag("keyboard_61_container").assertIsDisplayed()

    // Start & Stop Style
    composeTestRule.onNodeWithTag("btn_style_start_stop").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("RUN").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_style_start_stop").performClick()
    composeTestRule.waitForIdle()

    // Open Menu and navigate to Mixer
    composeTestRule.onNodeWithTag("top_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lcd_menu_grid").performScrollToNode(hasTestTag("menu_item_mixer"))
    composeTestRule.onNodeWithTag("menu_item_mixer").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("RESET MIXER").assertIsDisplayed()

    // Back to Main LCD
    composeTestRule.onNodeWithTag("lcd_back_main").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lcd_r1_card").assertIsDisplayed()

    // Navigate to Voice Browser, verify SF2 importer button, and select Ethiopian Masinko
    composeTestRule.onNodeWithTag("lcd_r1_card").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_import_sf2").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Ethiopian").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("voice_item_eth_masinko").performClick()
    composeTestRule.waitForIdle()

    // Return to Main LCD and verify Masinko appears
    composeTestRule.onNodeWithTag("lcd_back_main").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Masinko").assertIsDisplayed()

    // Navigate to Style Browser and verify STY importer button
    composeTestRule.onNodeWithTag("lcd_style_card").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_import_sty").assertIsDisplayed()
    composeTestRule.onNodeWithTag("lcd_back_main").performClick()
    composeTestRule.waitForIdle()

    // Save Registration Memory Slot 1 via Registration LCD page
    composeTestRule.onNodeWithTag("top_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lcd_menu_grid").performScrollToNode(hasTestTag("menu_item_registration"))
    composeTestRule.onNodeWithTag("menu_item_registration").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("MEMORY").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lcd_reg_slot_1").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Navigate to Expansion Manager page and verify SF2, STY & Pack importer buttons
    composeTestRule.onNodeWithTag("top_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lcd_menu_grid").performScrollToNode(hasTestTag("menu_item_expansion_manager"))
    composeTestRule.onNodeWithTag("menu_item_expansion_manager").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_exp_import_sf2").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_exp_import_sty").assertIsDisplayed()
    composeTestRule.onNodeWithText("IMPORT PACK").performScrollTo().assertIsDisplayed()

    // Open Pitch & Modulation hardware deck tab and interact with Modulation preset
    composeTestRule.onNodeWithText("PITCH & MOD").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pitch_bend_wheel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("modulation_wheel").assertIsDisplayed()
    composeTestRule.onNodeWithText("50%").performClick()
    composeTestRule.waitForIdle()

    // Return to Main LCD and verify Quick Chords, Split Toggle, All Stop, and Voice Steppers
    composeTestRule.onNodeWithTag("lcd_back_main").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("CHORDS").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_chord_dm").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_split_toggle").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_all_stop").assertIsDisplayed()

    composeTestRule.onNodeWithText("VOICES & PART").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_voice_prev").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_voice_next").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    // Open Custom Drum Kit section from Top Header and test Import button, Library, Pad Preview & New Kit
    composeTestRule.onNodeWithTag("top_drum_kit_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("custom_drum_kit_section").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_import_drum_kit").assertIsDisplayed()
    composeTestRule.onNodeWithTag("drum_kit_library_list").assertIsDisplayed()
    composeTestRule.onNodeWithTag("drum_pad_preview_grid").assertIsDisplayed()
    composeTestRule.onNodeWithTag("drum_mapping_list").assertIsDisplayed()

    // Trigger Kick pad preview and create a new editable Custom Drum Kit
    composeTestRule.onNodeWithTag("drum_pad_kick_36").performClick()
    composeTestRule.onNodeWithTag("btn_new_drum_kit").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_confirm_new_drum_kit").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drum_mapping_list").performScrollToNode(hasTestTag("btn_add_drum_pad"))
    composeTestRule.onNodeWithTag("btn_add_drum_pad").assertIsDisplayed()

    // Verify Style/Arranger section exposes DRUM KIT SELECTOR + inline transport alongside IMPORT .STY
    composeTestRule.onNodeWithTag("btn_use_kit_for_style").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lcd_back_main").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("lcd_style_card").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("arranger_drum_kit_selector").assertIsDisplayed()
    composeTestRule.onNodeWithTag("arranger_inline_transport").assertIsDisplayed()
    composeTestRule.onNodeWithTag("arranger_start_stop").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("arranger_start_stop").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  @Config(sdk = [36], qualifiers = "w840dp-h480dp-land")
  fun `landscape PSR-S975 console displays full workstation deck and switches sections`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("main_lcd_display").assertIsDisplayed()
    composeTestRule.onNodeWithTag("keyboard_61_container").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pitch_bend_wheel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("modulation_wheel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_style_start_stop").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_voice_prev").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_voice_next").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_main_b").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_voice_cat_ethiopian").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("voice_item_eth_krar").assertIsDisplayed()

    // Verify new Keyboard (Church Organ, Accordion) & Guitar & Bass voices + preview button
    composeTestRule.onNodeWithTag("btn_voice_cat_keyboard").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("voice_list_lazy").performScrollToNode(hasTestTag("voice_item_kbd_accordion"))
    composeTestRule.onNodeWithTag("voice_item_kbd_accordion").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_preview_voice_kbd_accordion").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("btn_voice_cat_guitar").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("voice_list_lazy").performScrollToNode(hasTestTag("voice_item_bass_electric"))
    composeTestRule.onNodeWithTag("voice_item_bass_electric").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_voice_preview").performScrollTo().performClick()
    composeTestRule.waitForIdle()
  }
}

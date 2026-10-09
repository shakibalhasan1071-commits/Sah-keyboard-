package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.moekeyboard.ime.BengaliTypingHelper
import com.example.moekeyboard.ime.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Sah Keyboard", appName)
  }

  @Test
  fun `verify bengali layouts and modifiers`() {
    // Verify Bengali Kars
    assertTrue(BengaliTypingHelper.isBengaliModifier('া'))
    assertTrue(BengaliTypingHelper.isBengaliModifier('ি'))
    assertTrue(BengaliTypingHelper.isBengaliModifier('্'))
    assertTrue(BengaliTypingHelper.isBengaliModifier('ং'))

    // Verify Layout contains Bengali consonants and digits
    assertTrue(KeyboardLayouts.BENGALI_P1_ROW1.contains("ক"))
    assertTrue(KeyboardLayouts.NUMBERS_ROW_BN.contains("১"))
  }

  @Test
  fun `verify temp mail manager initialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = com.example.moekeyboard.tempmail.TempMailManager.getInstance(context)
    assertTrue(manager.availableDomains.isNotEmpty())
  }
}

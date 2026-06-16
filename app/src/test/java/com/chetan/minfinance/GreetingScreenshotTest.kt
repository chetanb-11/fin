package com.chetan.minfinance

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.chetan.minfinance.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent { 
      MyApplicationTheme { 
        com.chetan.minfinance.ui.UncategorizedExpenseCard(
          expense = com.chetan.minfinance.data.Expense(
            id = 1,
            merchantName = "Swiggy Intercept mock",
            amount = 150.0,
            timestamp = 1718567044000L // static timestamp for stable test screenshot
          ),
          formatter = java.text.DecimalFormat("₹#,##0.00"),
          onCategorize = {},
          onDelete = {}
        )
      } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

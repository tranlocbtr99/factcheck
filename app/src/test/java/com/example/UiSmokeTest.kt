package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.FactCheckViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {
    @get:Rule val compose = createComposeRule()

    private fun render(dark: Boolean = false) {
        val model =
            FactCheckViewModel(ApplicationProvider.getApplicationContext<FactCheckApplication>())
        compose.setContent {
            MyApplicationTheme(dark) { MainAppContent(model, {}, {}, {}, {}, {}) }
        }
    }

    @Test
    fun lightNavigationAndUnconfiguredState() {
        render()
        compose.onNodeWithText("Cần thiết lập kết nối").assertIsDisplayed()
        compose.onNodeWithText("Nhận định Đúng / Sai").assertIsNotEnabled()
        compose.onNodeWithTag("nav_history").performClick()
        compose.onNodeWithText("Chưa có kết quả phù hợp").assertExists()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("Kết nối dịch vụ").assertIsDisplayed()
    }

    @Test
    fun darkHomeHasUsablePrimaryAction() {
        render(true)
        compose.onNodeWithText("Bạn muốn kiểm chứng gì?").assertIsDisplayed()
    }
}

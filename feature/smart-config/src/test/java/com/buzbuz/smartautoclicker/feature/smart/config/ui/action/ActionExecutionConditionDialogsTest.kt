// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action

import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.feature.smart.config.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class ActionExecutionConditionDialogsTest {
    private val context get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.ScenarioConfigTheme)
    private val conditions = listOf(condition(1, "图像"), condition(2, "文字"))

    private fun condition(value: Long, label: String): ScreenCondition = mockk {
        every { id } returns Identifier(databaseId = value)
        every { name } returns label
    }

    private fun latestDialog(): AlertDialog = (ShadowDialog.getLatestDialog() as AlertDialog).also {
        // These dialogs are opened from a service, so an Activity token is unavailable.
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, it.window!!.attributes.type)
        assertTrue(it.isShowing)
    }

    private fun choose(dialog: AlertDialog, index: Int) {
        val list = dialog.listView
        list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun selectMultipleConditionsAndSaveAnd() {
        var saved: List<ScreenCondition>? = null
        var operator: Int? = null
        showActionExecutionConditionMenu(context, conditions, listOf(conditions[0].id), OR,
            { items, logic -> saved = items; operator = logic }, {}, {})
        choose(latestDialog(), 0)
        val selection = latestDialog()
        assertTrue(selection.listView.isItemChecked(0))
        assertFalse(selection.listView.isItemChecked(1))
        choose(selection, 1)
        selection.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(conditions, saved)
        assertEquals(AND, operator)
    }

    @Test fun saveOrKeepsSelectedConditions() {
        var saved: List<ScreenCondition>? = null
        var operator: Int? = null
        showActionExecutionConditionMenu(context, conditions, conditions.map { it.id }, AND,
            { items, logic -> saved = items; operator = logic }, {}, {})
        choose(latestDialog(), 0)
        latestDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(conditions, saved)
        assertEquals(OR, operator)
    }

    @Test fun cancellingDoesNotClearExistingConditions() {
        var saved = false
        showActionExecutionConditionMenu(context, conditions, listOf(conditions[0].id), AND,
            { _, _ -> saved = true }, {}, {})
        choose(latestDialog(), 0)
        val selection = latestDialog()
        choose(selection, 0)
        selection.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(saved)
    }

    @Test fun newImageAndTextInvokeTheirEditors() {
        var imageCount = 0
        var textCount = 0
        for (index in 1..2) {
            showActionExecutionConditionMenu(context, emptyList(), emptyList(), AND,
                { _, _ -> fail("Creating a condition must not clear the selection") },
                { imageCount++ }, { textCount++ })
            choose(latestDialog(), index)
        }
        assertEquals(1, imageCount)
        assertEquals(1, textCount)
    }
}

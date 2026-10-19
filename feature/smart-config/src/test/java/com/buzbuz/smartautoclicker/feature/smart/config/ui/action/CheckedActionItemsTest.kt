// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action

import android.app.Application
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import android.widget.ScrollView
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager.Companion.showAsOverlay
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class CheckedActionItemsTest {
    @Test fun checkboxAndRowClicksBothUpdateSelection() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.ScenarioConfigTheme)
        val selected = mutableSetOf(0)
        val dialog = MaterialAlertDialogBuilder(context).setCheckedActionItems(
            arrayOf("gesture", "pause"), booleanArrayOf(true, false),
        ) { index, checked -> if (checked) selected.add(index) else selected.remove(index) }.create()
        dialog.showAsOverlay()
        val custom = dialog.findViewById<android.widget.FrameLayout>(androidx.appcompat.R.id.custom)!!
        val rows = (custom.getChildAt(0) as ScrollView).getChildAt(0) as LinearLayout
        val first = rows.getChildAt(0) as LinearLayout
        val second = rows.getChildAt(1) as LinearLayout
        assertTrue((first.getChildAt(1) as MaterialCheckBox).isChecked)
        assertFalse((second.getChildAt(1) as MaterialCheckBox).isChecked)
        second.performClick()
        assertTrue((second.getChildAt(1) as MaterialCheckBox).isChecked)
        assertEquals(setOf(0, 1), selected)
        (first.getChildAt(1) as MaterialCheckBox).performClick()
        assertEquals(setOf(1), selected)
        dialog.dismiss()
    }
}

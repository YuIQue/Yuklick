// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Explicit checkboxes avoid OEM themes hiding AlertDialog's check mark drawable. */
internal fun MaterialAlertDialogBuilder.setCheckedActionItems(
    labels: Array<String>, checked: BooleanArray, onChecked: (Int, Boolean) -> Unit,
): MaterialAlertDialogBuilder {
    val density = context.resources.displayMetrics.density
    val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    labels.forEachIndexed { index, label ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = (56 * density).toInt()
            setPadding((24 * density).toInt(), 0, (16 * density).toInt(), 0)
        }
        val box = MaterialCheckBox(context).apply {
            isChecked = checked[index]
            contentDescription = label
            setOnCheckedChangeListener { _, selected -> onChecked(index, selected) }
        }
        row.addView(TextView(context).apply { text = label; textSize = 18f },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(box)
        row.setOnClickListener { box.isChecked = !box.isChecked }
        container.addView(row)
    }
    return setView(ScrollView(context).apply { addView(container) })
}

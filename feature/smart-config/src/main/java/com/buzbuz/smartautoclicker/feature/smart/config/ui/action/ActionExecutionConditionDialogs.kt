// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action

import android.content.Context
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager.Companion.showAsOverlay
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Select existing conditions or start creating one without leaving action configuration. */
internal fun showActionExecutionConditionMenu(
    context: Context,
    conditions: List<ScreenCondition>,
    selectedIds: List<Identifier>,
    operator: Int,
    onSelectionChanged: (List<ScreenCondition>, Int) -> Unit,
    onNewImage: () -> Unit,
    onNewText: () -> Unit,
    onNewCounter: (() -> Unit)? = null,
) {
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.field_action_condition)
        .setItems(arrayOf(
            context.getString(R.string.action_condition_choose),
            context.getString(R.string.action_condition_new_image),
            context.getString(R.string.action_condition_new_text),
            *if (onNewCounter != null) arrayOf(context.getString(R.string.condition_counter_if)) else emptyArray(),
        )) { _, which ->
            when (which) {
                0 -> showActionExecutionConditionSelection(context, conditions, selectedIds, operator, onSelectionChanged)
                1 -> onNewImage()
                2 -> onNewText()
                3 -> onNewCounter?.invoke()
            }
        }.create().showAsOverlay()
}

private fun showActionExecutionConditionSelection(
    context: Context,
    conditions: List<ScreenCondition>,
    selectedIds: List<Identifier>,
    operator: Int,
    onSelectionChanged: (List<ScreenCondition>, Int) -> Unit,
) {
    val selected = selectedIds.toMutableSet()
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.field_action_condition)
        .setMultiChoiceItems(
            conditions.map { it.name }.toTypedArray(),
            conditions.map { it.id in selected }.toBooleanArray(),
        ) { _, index, checked ->
            if (checked) selected.add(conditions[index].id) else selected.remove(conditions[index].id)
        }
        .setPositiveButton(R.string.action_condition_and) { _, _ ->
            onSelectionChanged(conditions.filter { it.id in selected }, AND)
        }
        .setNeutralButton(R.string.action_condition_or) { _, _ ->
            onSelectionChanged(conditions.filter { it.id in selected }, OR)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .create().showAsOverlay()
}

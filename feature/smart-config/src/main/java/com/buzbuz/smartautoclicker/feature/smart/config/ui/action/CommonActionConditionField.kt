// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.withExecutionConditions
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeFieldSelectorBinding
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.*
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.OnConditionConfigCompleteListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.image.CaptureMenu
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.image.ImageConditionDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.text.TextConditionDialog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.EntryPoints
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ActionConditionEntryPoint {
    fun editionRepository(): EditionRepository
}

/** Shared condition editor for action types that do not have gesture/position fields. */
internal fun OverlayDialog.attachActionConditionField(root: ViewGroup) {
    val repository = EntryPoints.get(context.applicationContext, ActionConditionEntryPoint::class.java).editionRepository()
    if ((repository.editionState.getEditedEvent() as? ScreenEvent)?.isSequential != true) return
    val field = IncludeFieldSelectorBinding.inflate(LayoutInflater.from(context), root, false)
    root.addView(field.root, 1)
    field.setTitle(context.getString(R.string.field_action_condition))
    fun refresh() {
        val action = repository.editionState.getEditedAction<Action>() ?: return
        val conditions = repository.editionState.getEditedEventConditions<ScreenCondition>().orEmpty()
        field.setDescription(action.executionConditionIds().mapNotNull { id -> conditions.find { it.id == id }?.name }
            .joinToString(if (action.executionConditionOperator == AND) " AND " else " OR ")
            .ifEmpty { context.getString(R.string.field_action_condition_none) })
    }
    fun select(conditions: List<ScreenCondition>, operator: Int) {
        repository.editionState.getEditedAction<Action>()?.let {
            repository.updateEditedAction(it.withExecutionConditions(conditions.map { condition -> condition.id }, operator))
        }
        refresh()
    }
    fun editNew(condition: ScreenCondition) {
        repository.startConditionEdition(condition)
        val listener = object : OnConditionConfigCompleteListener {
            override fun onConfirmClicked() {
                repository.upsertEditedCondition()
                repository.editionState.getEditedAction<Action>()?.let { action ->
                    repository.updateEditedAction(action.withExecutionConditions(
                        (action.executionConditionIds() + condition.id).distinct(), action.executionConditionOperator))
                }
                refresh()
            }
            override fun onDeleteClicked() { repository.stopConditionEdition() }
            override fun onDismissClicked() { repository.stopConditionEdition() }
        }
        val editor = when (condition) {
            is ScreenCondition.Image -> ImageConditionDialog(listener)
            is ScreenCondition.Text -> TextConditionDialog(listener)
            is ScreenCondition.Number -> com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.screen.number.NumberConditionDialog(listener)
            else -> return
        }
        overlayManager.navigateTo(context, editor, hideCurrent = true)
    }
    field.setOnClickListener {
        val action = repository.editionState.getEditedAction<Action>() ?: return@setOnClickListener
        showActionExecutionConditionMenu(context,
            repository.editionState.getEditedEventConditions<ScreenCondition>().orEmpty(),
            action.executionConditionIds(), action.executionConditionOperator, ::select,
            { overlayManager.navigateTo(context, CaptureMenu(::editNew), hideCurrent = true) },
            { editNew(repository.editedItemsBuilder.createNewTextCondition(context)) },
            { editNew(repository.editedItemsBuilder.createNewNumberCondition(context).copy(
                name = context.getString(R.string.condition_counter_if), sourceCounterName = "")) })
    }
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            repository.editionState.editedActionState.collect { refresh() }
        }
    }
    refresh()
}

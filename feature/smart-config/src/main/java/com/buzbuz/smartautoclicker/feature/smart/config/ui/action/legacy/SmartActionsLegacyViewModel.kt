// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.legacy

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.common.overlays.menu.implementation.brief.ItemBrief
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.event.ActionGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.domain.usecase.copy.availability.IsActionCopyAvailableUseCase
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.brief.ActionConfigurator
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.brief.createNewAction
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.selection.ActionTypeChoice
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.action.UiAction
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.action.toItemBriefs

import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


class SmartActionsLegacyViewModel @Inject constructor(
    @ApplicationContext context: Context,
    isActionCopyAvailableUseCase: IsActionCopyAvailableUseCase,
    private val editionRepository: EditionRepository,
) : ViewModel(), ActionConfigurator {

    private val collapsedGroups = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<SmartActionsLegacyUiState?> = combine(
        editionRepository.editionState.editedEventState.mapNotNull { it.value },
        editionRepository.editionState.editedEventActionsState,
        isActionCopyAvailableUseCase(),
        collapsedGroups,
    ) { event, actions, canCopy, collapsed ->
        val briefs = actions.toItemBriefs(context, event)
        val groups = (event as? ScreenEvent)?.actionGroups.orEmpty()
        val shownGroups = mutableSetOf<String>()
        val rows = buildList {
            briefs.forEach { brief ->
                val group = groups.firstOrNull { brief.id in it.actionIds }
                if (group != null && shownGroups.add(group.id)) {
                    add(ItemBrief(Identifier(id = group.id.hashCode().toLong(), asTemporary = true), group))
                }
                if (group == null || group.id !in collapsed) add(brief)
            }
        }
        SmartActionsLegacyUiState(
            actionBriefs = rows,
            isCopyButtonVisible = canCopy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The legacy dialog exposes copy through its own button, not through the type selection
    override fun getActionTypeChoices(): List<ActionTypeChoice> = listOf(
        ActionTypeChoice.Click,
        ActionTypeChoice.Gesture,
        ActionTypeChoice.Pause,
        ActionTypeChoice.SetText,
        ActionTypeChoice.System,
        ActionTypeChoice.ChangeCounter,
        ActionTypeChoice.ToggleEvent,
        ActionTypeChoice.Notification,
        ActionTypeChoice.Intent,
    )

    override fun createAction(context: Context, choice: ActionTypeChoice): Action =
        editionRepository.createNewAction(context, choice)

    override fun startActionEdition(action: Action) {
        editionRepository.startActionEdition(action)
    }

    override fun upsertEditedAction() {
        editionRepository.upsertEditedAction()
    }

    override fun removeEditedAction() {
        editionRepository.deleteEditedAction()
    }

    override fun dismissEditedAction() {
        editionRepository.stopActionEdition()
    }

    fun updateActionOrder(actionsBrief: List<ItemBrief>) {
        val all = getAllActions()
        val included = mutableSetOf<Identifier>()
        val actions = buildList {
            actionsBrief.forEach { row ->
                val ids = when (val data = row.data) {
                    is ActionGroup -> actionsBrief.mapNotNull { (it.data as? UiAction)?.action?.id }
                        .filter { it in data.actionIds }.ifEmpty { all.filter { it.id in data.actionIds }.map { it.id } }
                    is UiAction -> listOf(data.action.id)
                    else -> emptyList()
                }
                ids.forEach { id -> if (included.add(id)) all.find { it.id == id }?.let { add(it) } }
            }
            addAll(all.filter { included.add(it.id) })
        }
        editionRepository.updateActionsOrder(actions)
    }

    fun getAllActions(): List<Action> =
        editionRepository.editionState.getEditedEventActions<Action>().orEmpty()

    fun toggleGroup(group: ActionGroup) {
        collapsedGroups.update { if (group.id in it) it - group.id else it + group.id }
    }

    fun createGroup(selectedIds: Set<Identifier>, name: String) {
        val event = editionRepository.editionState.getEditedEvent() as? ScreenEvent ?: return
        val ids = getAllActions().map { it.id }.filter { it in selectedIds }
        if (ids.size < 2) return
        editionRepository.updateEditedEvent(event.copy(actionGroups =
            event.actionGroups.filterNot { old -> old.actionIds.any { it in selectedIds } } +
                ActionGroup(UUID.randomUUID().toString(), name, ids)))
    }

    fun renameGroup(group: ActionGroup, name: String) {
        val event = editionRepository.editionState.getEditedEvent() as? ScreenEvent ?: return
        editionRepository.updateEditedEvent(event.copy(actionGroups = event.actionGroups.map {
            if (it.id == group.id) it.copy(name = name) else it
        }))
    }

    fun updateGroupMembers(group: ActionGroup, selectedIds: Set<Identifier>) {
        val event = editionRepository.editionState.getEditedEvent() as? ScreenEvent ?: return
        val members = getAllActions().map { it.id }.filter { it in selectedIds }
        if (members.size < 2) return
        editionRepository.updateEditedEvent(event.copy(actionGroups = event.actionGroups.mapNotNull { existing ->
            if (existing.id == group.id) existing.copy(actionIds = members)
            else {
                val remaining = existing.actionIds.filterNot { it in selectedIds }
                if (remaining.size < 2) null else existing.copy(actionIds = remaining)
            }
        }))
    }

    fun ungroup(group: ActionGroup) {
        val event = editionRepository.editionState.getEditedEvent() as? ScreenEvent ?: return
        editionRepository.updateEditedEvent(event.copy(actionGroups = event.actionGroups.filterNot { it.id == group.id }))
    }

    fun copyGroup(group: ActionGroup) {
        val actions = getAllActions().filter { it.id in group.actionIds }
        val copies = actions.map { editionRepository.editedItemsBuilder.createNewActionFrom(it) }
        copies.forEach { copy ->
            editionRepository.startActionEdition(copy)
            editionRepository.upsertEditedAction()
        }
        val event = editionRepository.editionState.getEditedEvent() as? ScreenEvent ?: return
        editionRepository.updateEditedEvent(event.copy(actionGroups = event.actionGroups +
            ActionGroup(UUID.randomUUID().toString(), group.name, copies.map { it.id })))
    }

    fun deleteSelected(selectedIds: Set<Identifier>) {
        getAllActions().filter { it.id in selectedIds }.forEach { action ->
            editionRepository.startActionEdition(action)
            editionRepository.deleteEditedAction()
        }
        val event = editionRepository.editionState.getEditedEvent() as? ScreenEvent ?: return
        editionRepository.updateEditedEvent(event.copy(actionGroups = event.actionGroups.mapNotNull { group ->
            val remaining = group.actionIds.filterNot { it in selectedIds }
            if (remaining.size < 2) null else group.copy(actionIds = remaining)
        }))
    }
}

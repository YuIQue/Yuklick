// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
/*
 * Copyright (C) 2024 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.copy.action

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.action.withExecutionConditions
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.domain.usecase.copy.GetActionsForCopyUseCase
import com.buzbuz.smartautoclicker.feature.smart.config.domain.usecase.copy.model.ActionsForCopy
import com.buzbuz.smartautoclicker.feature.smart.config.domain.usecase.copy.unreachable.IsActionRelatedToUnreachableItemUseCase
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.action.toUiAction

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.collections.forEach
import kotlin.collections.mapNotNull

/**
 * View model for the [ActionCopyDialog].
 */
class ActionCopyViewModel @Inject constructor(
    @ApplicationContext context: Context,
    getActionsForCopyUseCase: GetActionsForCopyUseCase,
    smartRepository: com.buzbuz.smartautoclicker.core.domain.IRepository,
    private val isActionRelatedToUnreachableItemUseCase: IsActionRelatedToUnreachableItemUseCase,
    private val editionRepository: EditionRepository,
) : ViewModel() {

    /** The currently searched action name. Null if none is. */
    private val searchQuery: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Contains map of action id to the list index checked for copy. */
    private val checkedForCopy: MutableStateFlow<Map<Identifier, ActionWithIndex>> = MutableStateFlow(emptyMap())

    private val actionsToCopy: Flow<ActionsForCopy> = getActionsForCopyUseCase()

    private val groupEvents = combine(
        smartRepository.allScreenEvents,
        editionRepository.editionState.allEditedEventsFlow,
        editionRepository.editionState.editedEventState,
    ) { saved, edited, current ->
        (listOfNotNull(current.value) + edited + saved)
            .distinctBy { it.id }.filterIsInstance<com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent>()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var pendingConditions: List<com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition> = emptyList()
    private var pendingGroups: List<com.buzbuz.smartautoclicker.core.domain.model.event.ActionGroup> = emptyList()

    /** List of all actions available for copy */
    private val allCopyItemsState: Flow<ActionCopyUiState> =
        combine(actionsToCopy, checkedForCopy, groupEvents) { actions, checked, events ->
            ActionCopyUiState(
                items = buildList {
                    events.forEach { event -> event.actionGroups.forEach { group ->
                        val members = event.actions.filter { it.id in group.actionIds }.sortedBy { it.priority }
                        if (members.isNotEmpty() && members.all { it.isComplete() })
                            add(ActionCopyItem.GroupItem(event.id, group, members, members.all { checked.containsKey(it.id) }))
                    } }
                    if (actions.thisEvent.isNotEmpty()) {
                        add(ActionCopyItem.HeaderItem(R.string.list_header_copy_action_this))
                        addAll(actions.thisEvent.toCopyItems(context, checked).sortedBy { it.uiAction.name })
                    }
                    if (actions.thisScenario.isNotEmpty()) {
                        add(ActionCopyItem.HeaderItem(R.string.list_header_copy_action_this_scenario))
                        addAll(actions.thisScenario.toCopyItems(context, checked).sortedBy { it.uiAction.name })
                    }
                    if (actions.otherScenario.isNotEmpty()) {
                        add(ActionCopyItem.HeaderItem(R.string.list_header_copy_action_all))
                        addAll(actions.otherScenario.toCopyItems(context, checked).sortedBy { it.uiAction.name })
                    }
                },
                thisEventSize = if (actions.thisEvent.isNotEmpty()) actions.thisEvent.size + 1 else 0,
                thisScenarioSize = if (actions.thisScenario.isNotEmpty()) actions.thisScenario.size + 1 else 0,
                otherScenarioSize = if (actions.otherScenario.isNotEmpty()) actions.otherScenario.size + 1 else 0,
            )
        }

    /**
     * List of displayed action items.
     * This list can contain all events with headers, or the search result depending on the current search query.
     */
    val uiState: StateFlow<ActionCopyUiState?> = allCopyItemsState
        .combine(searchQuery) { state, query ->
            if (query.isNullOrEmpty()) state
            else state.copy(
                items = state.items
                    .filter { item -> when (item) {
                        is ActionCopyItem.ActionItem -> item.uiAction.name.contains(query, true)
                        is ActionCopyItem.GroupItem -> item.group.name.contains(query, true)
                        else -> false
                    } }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), null)


    fun updateSearchQuery(query: String?) {
        searchQuery.value = query
    }

    fun toggleCheckedForCopy(action: Action, index: Int) {
        checkedForCopy.update { old ->
            if (old.contains(action.id)) old - action.id
            else old + (action.id to ActionWithIndex(action, index))
        }
    }

    fun toggleGroupForCopy(group: ActionCopyItem.GroupItem, index: Int) {
        checkedForCopy.update { old ->
            if (group.actions.all { old.containsKey(it.id) }) old - group.actions.map { it.id }.toSet()
            else old + group.actions.associate { it.id to ActionWithIndex(it, index) }
        }
    }

    fun getActionsCopy(): List<Action> {
        val originals = checkedForCopy.value.values.sortedWith(compareBy({ it.index }, { it.action.priority })).map { it.action }
        val targetId = editionRepository.editionState.getEditedEvent()?.id
        val sourceConditions = groupEvents.value.flatMap { it.conditions }.associateBy { it.id }
        val conditionCopies = originals.filter { it.eventId != targetId }.flatMap { it.executionConditionIds() }
            .distinct().mapNotNull { id -> sourceConditions[id]?.let {
                id to editionRepository.editedItemsBuilder.createNewScreenConditionFrom(it)
            } }.toMap()
        pendingConditions = conditionCopies.values.toList()
        val copies = originals.map { action -> action.createCopy().withExecutionConditions(
            action.executionConditionIds().map { conditionCopies[it]?.id ?: it }, action.executionConditionOperator) }
        val mapping = originals.zip(copies).associate { it.first.id to it.second.id }
        pendingGroups = uiState.value?.items.orEmpty().filterIsInstance<ActionCopyItem.GroupItem>()
            .filter { it.actions.all { action -> action.id in mapping } }
            .map { it.group.copy(id = java.util.UUID.randomUUID().toString(), actionIds = it.actions.map { action -> mapping.getValue(action.id) }) }
        return copies
    }

    fun actionCopyShouldWarnUser(copyActions: List<Action>): Boolean {
        copyActions.forEach { action ->
            if (isActionRelatedToUnreachableItemUseCase(action)) return true
        }

        return false
    }

    fun saveCopyActions(actionCopies: List<Action>) {
        pendingConditions.forEach { condition ->
            editionRepository.startConditionEdition(condition)
            editionRepository.upsertEditedCondition()
        }
        pendingConditions = emptyList()
        actionCopies.forEach { action ->
            editionRepository.startActionEdition(action)
            editionRepository.upsertEditedAction()
        }
        val event = editionRepository.editionState.getEditedEvent() as? com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
        if (event != null) {
            val ids = event.actions.map { it.id }.toSet()
            editionRepository.updateEditedEvent(event.copy(actionGroups = event.actionGroups + pendingGroups.filter { group -> group.actionIds.all { it in ids } }))
        }
        pendingGroups = emptyList()
    }

    private fun Action.createCopy(): Action =
        editionRepository.editedItemsBuilder.createNewActionFrom(this)

    /** Creates copy items from a list of edited actions from this scenario. */
    private fun List<Action>.toCopyItems(context: Context, checked: Map<Identifier, ActionWithIndex>) = map { action ->
        ActionCopyItem.ActionItem(
            uiAction = action.toUiAction(context),
            checked = checked.contains(action.id),
        )
    }

    private data class ActionWithIndex(
        val action: Action,
        val index: Int,
    )
}
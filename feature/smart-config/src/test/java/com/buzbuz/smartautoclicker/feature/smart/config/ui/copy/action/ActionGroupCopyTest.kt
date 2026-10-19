// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.feature.smart.config.ui.copy.action

import android.app.Application
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.event.*
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditionRepository
import com.buzbuz.smartautoclicker.feature.smart.config.domain.EditedItemsBuilder
import com.buzbuz.smartautoclicker.feature.smart.config.domain.model.*
import com.buzbuz.smartautoclicker.feature.smart.config.domain.usecase.copy.GetActionsForCopyUseCase
import com.buzbuz.smartautoclicker.feature.smart.config.domain.usecase.copy.model.ActionsForCopy
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class ActionGroupCopyTest {
    @Test fun groupSelectionCopiesInExecutionOrderAndRetainsEditableGroup() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val id = Identifier(databaseId = 1)
            val first = Pause(Identifier(databaseId = 10), id, "Z first", 0, 100)
            val second = Pause(Identifier(databaseId = 11), id, "A second", 1, 200)
            val group = ActionGroup("original", "my group", listOf(first.id,second.id))
            val event = ScreenEvent(id, id, "event", AND, listOf(first,second),
                priority = 0, keepDetecting = false, cooldownMs = 0, isSequential = true, actionGroups = listOf(group))
            val state = mockk<IEditionState>()
            every { state.allEditedEventsFlow } returns MutableStateFlow(listOf<Event>(event))
            every { state.editedEventState } returns MutableStateFlow(EditedElementState<Event>(event,false,true))
            every { state.getEditedEvent() } returns event
            val builder = mockk<EditedItemsBuilder>()
            every { builder["getEditedEventIdOrThrow"]() } returns id
            every { builder.createNewActionFrom(any(), any()) } answers {
                val original = firstArg<Action>()
                original.copyBase(id = Identifier(databaseId = original.id.databaseId + 100))
            }
            val repository = mockk<EditionRepository>(relaxed = true)
            every { repository.editionState } returns state
            every { repository.editedItemsBuilder } returns builder
            val stored = mockk<IRepository>()
            every { stored.allScreenEvents } returns MutableStateFlow(listOf(event))
            val useCase = mockk<GetActionsForCopyUseCase>()
            every { useCase() } returns flowOf(ActionsForCopy(listOf(first,second), emptyList(),emptyList()))
            val model = ActionCopyViewModel(RuntimeEnvironment.getApplication(), useCase, stored, mockk(relaxed = true), repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect() }
            runCurrent()
            val item = model.uiState.value!!.items.filterIsInstance<ActionCopyItem.GroupItem>().single()
            model.toggleGroupForCopy(item,0)
            runCurrent()
            val copies = model.getActionsCopy()
            assertEquals(listOf("Z first","A second"), copies.map { it.name })
            every { state.getEditedEvent() } returns event.copy(actions = event.actions + copies)
            val saved = slot<Event>()
            every { repository.updateEditedEvent(capture(saved)) } returns Unit
            model.saveCopyActions(copies)
            val copiedGroup = (saved.captured as ScreenEvent).actionGroups.last()
            assertEquals(group.name,copiedGroup.name)
            assertNotEquals(group.id,copiedGroup.id)
            assertEquals(copies.map { it.id },copiedGroup.actionIds)
        } finally { Dispatchers.resetMain() }
    }
}

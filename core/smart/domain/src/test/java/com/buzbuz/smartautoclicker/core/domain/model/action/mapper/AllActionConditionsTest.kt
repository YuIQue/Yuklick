// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.core.domain.model.action.mapper

import com.buzbuz.smartautoclicker.core.database.entity.*
import com.buzbuz.smartautoclicker.core.domain.model.OR
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AllActionConditionsTest {
    @Test fun allNonGestureActionsPreserveConditionsThroughDatabaseAndCopies() {
        val types = listOf(ActionType.PAUSE, ActionType.TEXT, ActionType.SYSTEM,
            ActionType.CHANGE_COUNTER, ActionType.TOGGLE_EVENT, ActionType.NOTIFICATION, ActionType.INTENT)
        for (type in types) {
            val entity = ActionEntity(id = 1, eventId = 2, name = "test", type = type,
                executionConditionId = 3, additionalExecutionConditionIds = "4,5", executionConditionOperator = OR,
                pauseDuration = 100, textValue = "text", textValidateInput = false,
                systemActionType = SystemActionType.HOME, counterName = "counter",
                counterOperation = ChangeCounterOperationType.ADD,
                counterOperationValueType = CounterOperationValueType.NUMBER, counterOperationValue = 1.0,
                toggleAll = true, toggleAllType = EventToggleType.ENABLE,
                notificationMessageText = "message", notificationImportance = 3,
                isAdvanced = true, isBroadcast = true, intentAction = "test.action", flags = 0)
            val action = CompleteActionEntity(entity, emptyList(), emptyList(), emptyList()).toDomain()
            assertEquals(type.toString(), listOf(3L, 4L, 5L), action.executionConditionIds().map { it.databaseId })
            assertEquals(OR, action.executionConditionOperator)
            assertEquals(action.executionConditionIds(), action.deepCopy().executionConditionIds())
            assertEquals(action.executionConditionIds(), action.copyBase(name = "copy").executionConditionIds())
            val saved = action.toEntity()
            assertEquals(3L, saved.executionConditionId)
            assertEquals("4,5", saved.additionalExecutionConditionIds)
            assertEquals(OR, saved.executionConditionOperator)
        }
    }
}

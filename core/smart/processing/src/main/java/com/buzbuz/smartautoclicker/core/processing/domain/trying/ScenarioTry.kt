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
package com.buzbuz.smartautoclicker.core.processing.domain.trying

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.action.ToggleEvent
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.counter.Counter
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario

internal sealed class ScenarioTry {

    internal abstract val scenario: Scenario
    internal abstract val screenEvents: List<ScreenEvent>
    internal abstract val triggerEvents: List<TriggerEvent>
    internal abstract val counters: List<Counter>

}

internal class ImageEventTry(
    override val scenario: Scenario,
    override val counters: List<Counter>,
    val event: ScreenEvent,
) : ScenarioTry() {

    override val screenEvents: List<ScreenEvent> = listOf(getTestEvent())
    override val triggerEvents: List<TriggerEvent> = emptyList()

    private fun getTestEvent(): ScreenEvent =
        event.copy(
            enabledOnStart = true,
            actions = event.actions.filter { action -> action.canBeTried() }
        )

    private fun Action.canBeTried(): Boolean =
        this !is ToggleEvent
}

internal class ScreenConditionTry(
    override val scenario: Scenario,
    override val counters: List<Counter>,
    val condition: ScreenCondition,
) : ScenarioTry() {

    override val screenEvents: List<ScreenEvent> = listOf(getTestImageEvent())
    override val triggerEvents: List<TriggerEvent> = emptyList()

    private fun getTestImageEvent(): ScreenEvent {
        val tryEventId = Identifier(databaseId = 1L)
        return ScreenEvent(
            id = tryEventId,
            scenarioId = scenario.id,
            name = "Test Event",
            conditionOperator = AND,
            enabledOnStart = true,
            priority = 0,
            conditions = listOf(condition.copyCondition(eventId = tryEventId)),
            actions = listOf(getTestPauseAction(tryEventId)),
            keepDetecting = false,
            cooldownMs = 0L,
        )
    }

    private fun getTestPauseAction(eventId: Identifier): Pause =
        Pause(
            id = Identifier(databaseId = 1L),
            eventId = eventId,
            name = "Test Pause",
            pauseDuration = 500L,
            priority = 0,
        )
}

internal class ActionTry(
    override val scenario: Scenario,
    override val counters: List<Counter>,
    private val sourceEvent: ScreenEvent,
    private val actionsToTry: List<Action>,
) : ScenarioTry() {

    override val screenEvents: List<ScreenEvent> = listOf(getTestScreenEvent())
    override val triggerEvents: List<TriggerEvent> = emptyList()

    private fun getTestScreenEvent(): ScreenEvent {
        val tryEventId = Identifier(databaseId = 1L)
        return sourceEvent.copy(
            id = tryEventId,
            scenarioId = scenario.id,
            enabledOnStart = true,
            conditions = sourceEvent.conditions.map { it.copyCondition(eventId = tryEventId) },
            actions = actionsToTry.map { it.copyBase(eventId = tryEventId) },
            isSequential = true,
        )
    }
}

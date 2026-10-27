// SPDX-License-Identifier: GPL-3.0-or-later
// Added for Yuklick on 2026-10-21.
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario

import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ScenarioNavigationTest {
    private fun screen(sequential: Boolean): ScreenEvent = mockk {
        every { isSequential } returns sequential
    }
    @Test fun reopeningMultipleWorkflowEventsKeepsTriggerTabHidden() {
        for (count in 1..4) assertTrue(shouldHideTriggerTab(List(count) { screen(true) }))
    }
    @Test fun existingLegacyTriggersRemainAccessible() {
        assertFalse(shouldHideTriggerTab(listOf(screen(true), mockk<TriggerEvent>())))
        assertFalse(shouldHideTriggerTab(listOf(screen(false))))
        assertFalse(shouldHideTriggerTab(emptyList()))
    }
}

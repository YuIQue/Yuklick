// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.legacy

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.overlays.menu.implementation.brief.ItemBrief
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.event.ActionGroup
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.action.UiAction
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ActionGroupOrderTest {
    private fun row(id: Long): ItemBrief {
        val action = Pause(Identifier(databaseId = id), Identifier(databaseId = 1), "pause", 0, 100)
        return ItemBrief(action.id, UiAction(0, "pause", "", action, false))
    }
    private fun group(id: Long, members: List<ItemBrief>): ItemBrief = ItemBrief(
        Identifier(databaseId = id), ActionGroup(id.toString(), "group", members.map { it.id }))

    @Test fun draggingExpandedGroupMovesEveryMemberAndPreservesOrder() {
        val a = row(1); val b = row(2); val c = row(3); val d = row(4)
        val first = group(10, listOf(a,b)); val second = group(20, listOf(c,d))
        val adapter = ActionAdapter({}, {}, {}, {})
        adapter.submitList(listOf(first,a,b,second,c,d))
        adapter.moveActions(0,3)
        assertEquals(listOf(second,c,d,first,a,b), adapter.currentList)
        adapter.moveActions(3,0)
        assertEquals(listOf(first,a,b,second,c,d), adapter.currentList)
    }

    @Test fun collapsedGroupsCanSwapWithoutChangingTheirMembers() {
        val first = group(10, listOf(row(1),row(2)))
        val second = group(20, listOf(row(3),row(4)))
        val adapter = ActionAdapter({}, {}, {}, {})
        adapter.submitList(listOf(first,second))
        adapter.moveActions(0,1)
        assertEquals(listOf(second,first), adapter.currentList)
    }
}

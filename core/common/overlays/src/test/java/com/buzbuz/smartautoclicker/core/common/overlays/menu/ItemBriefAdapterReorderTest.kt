// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.core.common.overlays.menu

import com.buzbuz.smartautoclicker.core.common.overlays.menu.implementation.brief.ItemBrief
import com.buzbuz.smartautoclicker.core.common.overlays.menu.implementation.brief.ItemBriefAdapter
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfigManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ItemBriefAdapterReorderTest {
    @Test fun consecutiveMovesUseLatestOrderAndPersistWithoutAnotherMove() {
        val display = mockk<DisplayConfigManager> { every { displayConfig.orientation } returns 1 }
        val adapter = ItemBriefAdapter(display, { _, _ -> error("No holder needed") }, { _, _ -> }, { _, _ -> })
        val items = (1L..4L).map { ItemBrief(Identifier(databaseId = it), it) }
        adapter.submitList(items)
        adapter.moveItem(0, 1)
        adapter.moveItem(1, 2)
        val expected = listOf(items[1], items[2], items[0], items[3])
        assertEquals(expected, (0 until adapter.itemCount).map(adapter::getItem))
        adapter.submitList(expected)
        assertEquals(expected, (0 until adapter.itemCount).map(adapter::getItem))
        adapter.moveItem(-1, 2)
        assertEquals(expected, (0 until adapter.itemCount).map(adapter::getItem))
    }
}

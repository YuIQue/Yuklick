// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.core.domain.model.condition

import android.graphics.Rect
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.counter.ComparisonOperation
import com.buzbuz.smartautoclicker.core.domain.model.counter.CounterOperationValue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CounterIfConditionTest {
    @Test fun databaseRoundTripPreservesCounterSourceWithoutScreenArea() {
        val condition = ScreenCondition.Number(Identifier(databaseId = 1), Identifier(databaseId = 2),
            "IF count", 0, priority = 0, detectionArea = Rect(),
            comparisonOperation = ComparisonOperation.GREATER_OR_EQUALS,
            counterValue = CounterOperationValue.Number(3.0), sourceCounterName = "count")
        assertTrue(condition.isComplete())
        assertEquals(condition, condition.toEntity().toDomain())
        assertFalse(condition.copy(sourceCounterName = "").isComplete())
        assertFalse(condition.copy(sourceCounterName = null).isComplete())
    }
}

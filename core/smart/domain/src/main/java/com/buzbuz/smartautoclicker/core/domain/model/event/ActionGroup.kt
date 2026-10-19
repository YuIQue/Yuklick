// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
package com.buzbuz.smartautoclicker.core.domain.model.event

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier

/** Editable presentation group; its actions still execute in the event's ordered action list. */
data class ActionGroup(
    val id: String,
    val name: String,
    val actionIds: List<Identifier>,
)

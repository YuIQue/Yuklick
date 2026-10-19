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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.action.legacy

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.common.overlays.menu.implementation.brief.ItemBrief
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.event.ActionGroup
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonVisibility
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.setEmptyText
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.updateState
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogSmartActionsLegacyBinding
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemSmartActionLegacyBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.brief.showActionConfigDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.brief.showActionCopyDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.brief.showActionTypeSelectionDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.uistate.action.UiAction

import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager.Companion.showAsOverlay
import com.buzbuz.smartautoclicker.feature.smart.config.ui.action.setCheckedActionItems
import android.widget.EditText
import android.widget.Toast
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.Collections
import com.buzbuz.smartautoclicker.core.common.tutorial.domain.model.monitoring.MonitoredOverlayType


class SmartActionsLegacyDialog : OverlayDialog(R.style.ScenarioConfigTheme) {

    override fun tutorialMonitoringTag(): String = MonitoredOverlayType.SMART_ACTIONS_LEGACY.name


    /** View model for this content. */
    private val viewModel: SmartActionsLegacyViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { smartActionsLegacyViewModel() }
    )

    /** TouchHelper applied to [actionAdapter] allowing to drag and drop the items. */
    private val itemTouchHelper = ItemTouchHelper(ActionReorderTouchHelper())

    private lateinit var viewBinding: DialogSmartActionsLegacyBinding
    private lateinit var actionAdapter: ActionAdapter

    override fun onCreateView(): ViewGroup {
        actionAdapter = ActionAdapter(
            actionClickedListener = ::onActionClicked,
            actionLongClickedListener = ::onActionLongClicked,
            startDrag = itemTouchHelper::startDrag,
            actionReorderListener = viewModel::updateActionOrder,
        )

        viewBinding = DialogSmartActionsLegacyBinding.inflate(LayoutInflater.from(context)).apply {
            layoutTopBar.apply {
                setButtonVisibility(DialogNavigationButton.SAVE, View.GONE)
                setButtonVisibility(DialogNavigationButton.DELETE, View.GONE)
                dialogTitle.setText(R.string.menu_item_title_actions)

                buttonDismiss.setDebouncedOnClickListener { back() }
            }

            buttonNew.setDebouncedOnClickListener { onCreateButtonClicked() }
            buttonCopy.setDebouncedOnClickListener { onCopyButtonClicked() }
            buttonVisualEditor.setDebouncedOnClickListener {
                overlayManager.navigateTo(context,
                    com.buzbuz.smartautoclicker.feature.smart.config.ui.action.brief.SmartActionsBriefMenu(0),
                    hideCurrent = true)
            }

            layoutLoadableList.apply {
                setEmptyText(
                    id = R.string.message_empty_action_list_title,
                    secondaryId = R.string.message_empty_action_list_desc,
                )

                list.apply {
                    itemTouchHelper.attachToRecyclerView(this)
                    addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                    adapter = actionAdapter
                    layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
                }
            }
        }

        return viewBinding.root
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.filterNotNull().collect(::updateUiState)
            }
        }
    }

    private fun onCreateButtonClicked() {
        showActionTypeSelectionDialog(viewModel)
    }

    private fun onCopyButtonClicked() {
        showActionCopyDialog(viewModel)
    }

    private fun onActionClicked(item: ItemBrief) {
        when (val data = item.data) {
            is UiAction -> debounceUserInteraction { showActionConfigDialog(viewModel, data.action) }
            is ActionGroup -> viewModel.toggleGroup(data)
        }
    }

    private fun onActionLongClicked(item: ItemBrief) {
        when (val data = item.data) {
            is UiAction -> showBatchActionsDialog(data.action.id)
            is ActionGroup -> showGroupMenu(data)
        }
    }

    private fun showBatchActionsDialog(initialId: Identifier) {
        val actions = viewModel.getAllActions()
        val selected = mutableSetOf(initialId)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_batch_title)
            .setCheckedActionItems(
                actions.map { it.name.orEmpty() }.toTypedArray(),
                actions.map { it.id == initialId }.toBooleanArray(),
            ) { index, checked ->
                if (checked) selected.add(actions[index].id) else selected.remove(actions[index].id)
            }
            .setPositiveButton(R.string.action_batch_delete) { _, _ -> viewModel.deleteSelected(selected) }
            .setNeutralButton(R.string.action_batch_group) { _, _ ->
                if (selected.size >= 2) viewModel.createGroup(selected, context.getString(R.string.action_group_default_name))
                else Toast.makeText(context, R.string.action_group_min_two, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create().showAsOverlay()
    }

    private fun showGroupMenu(group: ActionGroup) {
        MaterialAlertDialogBuilder(context)
            .setTitle(group.name)
            .setItems(arrayOf(
                context.getString(R.string.action_group_toggle),
                context.getString(R.string.action_group_copy),
                context.getString(R.string.action_group_rename),
                context.getString(R.string.action_group_edit_members),
                context.getString(R.string.action_group_ungroup),
            )) { _, index ->
                when (index) {
                    0 -> viewModel.toggleGroup(group)
                    1 -> viewModel.copyGroup(group)
                    2 -> {
                        val input = EditText(context).apply { setText(group.name) }
                        MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.action_group_rename)
                            .setView(input)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val name = input.text.toString().trim()
                                if (name.isNotEmpty()) viewModel.renameGroup(group, name)
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .create().showAsOverlay()
                    }
                    3 -> showGroupMembersDialog(group)
                    4 -> viewModel.ungroup(group)
                }
            }.create().showAsOverlay()
    }

    private fun showGroupMembersDialog(group: ActionGroup) {
        val actions = viewModel.getAllActions()
        val selected = group.actionIds.toMutableSet()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_group_edit_members)
            .setCheckedActionItems(
                actions.map { it.name.orEmpty() }.toTypedArray(),
                actions.map { it.id in selected }.toBooleanArray(),
            ) { index, checked ->
                if (checked) selected.add(actions[index].id) else selected.remove(actions[index].id)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selected.size >= 2) viewModel.updateGroupMembers(group, selected)
                else Toast.makeText(context, R.string.action_group_min_two, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create().showAsOverlay()
    }

    private fun updateUiState(uiState: SmartActionsLegacyUiState) {
        viewBinding.buttonCopy.visibility = if (uiState.isCopyButtonVisible) View.VISIBLE else View.GONE
        updateActionList(uiState.actionBriefs)
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateActionList(newItems: List<ItemBrief>) {
        viewBinding.layoutLoadableList.apply {
            updateState(newItems)
            actionAdapter.submitList(newItems)
        }
    }
}

internal class ActionAdapter(
    private val actionClickedListener: (ItemBrief) -> Unit,
    private val actionLongClickedListener: (ItemBrief) -> Unit,
    private val startDrag: (RecyclerView.ViewHolder) -> Unit,
    private val actionReorderListener: (List<ItemBrief>) -> Unit,
) : RecyclerView.Adapter<ActionItemBriefViewHolder>() {
    var currentList: List<ItemBrief> = emptyList()
        private set
    override fun getItemCount() = currentList.size
    private fun getItem(position: Int) = currentList[position]
    fun submitList(items: List<ItemBrief>) {
        val old = currentList
        val next = items.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(a: Int, b: Int) = old[a].id == next[b].id
            override fun areContentsTheSame(a: Int, b: Int) = old[a].data == next[b].data
        })
        currentList = next
        diff.dispatchUpdatesTo(this)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionItemBriefViewHolder =
        ActionItemBriefViewHolder(ItemSmartActionLegacyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ActionItemBriefViewHolder, position: Int) {
        holder.onBind(getItem(position), actionClickedListener, actionLongClickedListener, startDrag)
    }

    /**
     * Swap the position of two events in the list.
     *
     * @param from the position of the click to be moved.
     * @param to the new position of the click to be moved.
     */
    fun moveActions(from: Int, to: Int) {
        if (from !in currentList.indices || to !in currentList.indices || from == to) return
        val newList = currentList.toMutableList()
        val moving = newList[from]
        val group = moving.data as? ActionGroup
        val block = if (group == null) listOf(moving) else newList.filter {
            it == moving || (it.data as? UiAction)?.action?.id in group.actionIds
        }
        val target = newList[to]
        if (target in block) return
        newList.removeAll(block.toSet())
        var insertion = newList.indexOf(target)
        if (to > from) {
            val targetGroup = (target.data as? ActionGroup)
            insertion = if (targetGroup == null) insertion + 1 else
                newList.indexOfLast { it == target || (it.data as? UiAction)?.action?.id in targetGroup.actionIds } + 1
        }
        newList.addAll(insertion.coerceAtLeast(0), block)
        submitList(newList)
    }

    /** Notify for an item drag and drop completion. */
    fun notifyMoveFinished() {
        actionReorderListener(currentList)
    }
}

private object ActionItemBriefDiffUtilCallback: DiffUtil.ItemCallback<ItemBrief>() {
    override fun areItemsTheSame(
        oldItem: ItemBrief,
        newItem: ItemBrief,
    ): Boolean = oldItem.id == newItem.id

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(
        oldItem: ItemBrief,
        newItem: ItemBrief,
    ): Boolean = oldItem.data == newItem.data
}

internal class ActionItemBriefViewHolder(
    private val viewBinding: ItemSmartActionLegacyBinding,
) : RecyclerView.ViewHolder(viewBinding.root) {

    fun onBind(item: ItemBrief, itemClickedListener: (ItemBrief) -> Unit,
               itemLongClickedListener: (ItemBrief) -> Unit,
               startDrag: (RecyclerView.ViewHolder) -> Unit) {
        viewBinding.apply {
            root.setOnClickListener { itemClickedListener(item) }
            root.setOnLongClickListener { itemLongClickedListener(item); true }
            btnReorder.setOnLongClickListener { startDrag(this@ActionItemBriefViewHolder); true }

            when (val details = item.data) {
                is UiAction -> {
                    btnReorder.visibility = View.VISIBLE
                    itemIcon.setImageResource(details.icon)
                    itemName.text = details.name
                    itemDescription.text = details.description
                    errorBadge.visibility = if (details.haveError) View.VISIBLE else View.GONE
                }
                is ActionGroup -> {
                    btnReorder.visibility = View.VISIBLE
                    itemIcon.setImageResource(android.R.drawable.ic_menu_agenda)
                    itemName.text = details.name
                    itemDescription.text = root.context.getString(R.string.action_group_summary, details.actionIds.size)
                    errorBadge.visibility = View.GONE
                }
            }
        }
    }
}

private class ActionReorderTouchHelper
    : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    override fun isLongPressDragEnabled(): Boolean = false

    override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder,
                             target: RecyclerView.ViewHolder): Boolean {
        val adapter = recyclerView.adapter as ActionAdapter
        return target.bindingAdapterPosition != RecyclerView.NO_POSITION
    }

    /** Tells if the user is currently dragging an item. */
    private var isDragging: Boolean = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        isDragging = true

        (recyclerView.adapter as ActionAdapter).moveActions(
            viewHolder.bindingAdapterPosition,
            target.bindingAdapterPosition
        )
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Nothing do to
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        if (isDragging) {
            (recyclerView.adapter as ActionAdapter).notifyMoveFinished()
            isDragging = false
        }
    }
}

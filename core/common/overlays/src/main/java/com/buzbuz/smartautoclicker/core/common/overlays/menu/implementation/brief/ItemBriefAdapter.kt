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
package com.buzbuz.smartautoclicker.core.common.overlays.menu.implementation.brief

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfigManager


internal class ItemBriefAdapter(
    private val displayConfigManager: DisplayConfigManager,
    private val viewHolderCreator: (parent: ViewGroup, orientation: Int) -> ItemBriefViewHolder<*>,
    private val itemBoundListener: (index: Int, itemView: View?) -> Unit,
    private val onItemClickedListener: (Int, ItemBrief) -> Unit,
) : RecyclerView.Adapter<ItemBriefViewHolder<*>>() {

    private var orientation: Int = displayConfigManager.displayConfig.orientation

    override fun getItemViewType(position: Int): Int = orientation

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemBriefViewHolder<*> =
        viewHolderCreator(parent, viewType)

    override fun onBindViewHolder(holder: ItemBriefViewHolder<*>, position: Int) {
        holder.onBind(getItem(position), onItemClickedListener)
        itemBoundListener(position, holder.itemView)
    }

    override fun onViewRecycled(holder: ItemBriefViewHolder<*>) {
        itemBoundListener(holder.bindingAdapterPosition, null)
        super.onViewRecycled(holder)
    }

    private var currentList: List<ItemBrief> = emptyList()

    override fun getItemCount(): Int = currentList.size

    fun getItem(position: Int): ItemBrief = currentList[position]

    fun moveItem(from: Int, to: Int) {
        if (from !in currentList.indices || to !in currentList.indices) return
        val moved = currentList.toMutableList()
        val item = moved.removeAt(from)
        moved.add(to, item)
        currentList = moved
        notifyItemMoved(from, to)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<ItemBrief>?) {
        val previous = currentList
        val updated = list?.toList().orEmpty()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = updated.size
            override fun areItemsTheSame(old: Int, new: Int) =
                ItemBriefDiffUtilCallback.areItemsTheSame(previous[old], updated[new])
            override fun areContentsTheSame(old: Int, new: Int) =
                ItemBriefDiffUtilCallback.areContentsTheSame(previous[old], updated[new])
        })
        currentList = updated
        if (orientation != displayConfigManager.displayConfig.orientation) {
            orientation = displayConfigManager.displayConfig.orientation
            notifyDataSetChanged()
        } else diff.dispatchUpdatesTo(this)
    }

}

internal object ItemBriefDiffUtilCallback: DiffUtil.ItemCallback<ItemBrief>() {
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

abstract class ItemBriefViewHolder<T: ViewBinding>(
    protected val viewBinding: T,
) : RecyclerView.ViewHolder(viewBinding.root) {

    abstract fun onBind(item: ItemBrief, itemClickedListener: (Int, ItemBrief) -> Unit)
}

data class ItemBrief(
    val id: Identifier,
    val data: Any,
)

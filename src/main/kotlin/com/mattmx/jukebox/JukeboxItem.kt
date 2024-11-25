package com.mattmx.jukebox

import com.mattmx.ktgui.utils.not
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object JukeboxItem {
    private val KEY = NamespacedKey.fromString("jukeboxgui:jukebox_item")
        ?: error("Failed to create key for jukebox item!")

    @JvmStatic
    fun getItem() = ItemStack(Material.JUKEBOX).apply { tagAndNameItem(this) }

    @JvmStatic
    fun tagItem(itemStack: ItemStack) = itemStack.editMeta { meta ->
        meta.persistentDataContainer.set(KEY, PersistentDataType.BOOLEAN, true)
    }

    @JvmStatic
    fun tagAndNameItem(itemStack: ItemStack) = itemStack.editMeta { meta ->
        meta.persistentDataContainer.set(KEY, PersistentDataType.BOOLEAN, true)
        meta.displayName(!"&dJukebox Item")
    }

    @JvmStatic
    fun isItem(itemStack: ItemStack) = itemStack.type == Material.JUKEBOX
            && itemStack.hasItemMeta()
            && itemStack.itemMeta.persistentDataContainer.has(KEY)

}
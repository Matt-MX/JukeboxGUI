package com.mattmx.jukebox

import com.mattmx.ktgui.GuiManager
import com.mattmx.ktgui.commands.rawCommand
import com.mattmx.ktgui.dsl.event
import com.mattmx.ktgui.scheduling.async
import com.mattmx.ktgui.utils.not
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.Collections

class JukeboxGuiPlugin : JavaPlugin() {
    private val jukeboxes = Collections.synchronizedMap(hashMapOf<Location, Jukebox>())

    override fun onEnable() {
        saveDefaultConfig()
        instance = this
        GuiManager.init(this)

        loadJukeboxes()

        event<BlockPlaceEvent>(ignoreCancelled = true) {
            if (JukeboxItem.isItem(itemInHand)) {
                if (!player.hasPermission(JukeboxPermissions.CREATE)) {
                    isCancelled = true
                    return@event
                }

                jukeboxes[block.location] = Jukebox(block.location)
                player.sendMessage(!"&aPlaced a jukebox item!")
                async { saveJukeboxes() }
            }
        }

        event<PlayerInteractEvent>(ignoreCancelled = true) {
            val loc = clickedBlock?.location ?: return@event
            val jukebox = jukeboxes[loc] ?: return@event

            isCancelled = true
            if (!player.hasPermission(JukeboxPermissions.OPEN_GUI)) {
                return@event
            }

            jukebox.createGui(player).open(player)
        }

        event<BlockBreakEvent>(ignoreCancelled = true) {
            if (isDropItems) return@event
            jukeboxes.remove(block.location)
                ?: return@event

            if (!player.hasPermission(JukeboxPermissions.DELETE)) {
                isCancelled = true
                return@event
            }

            player.sendMessage(!"&cRemoved a jukebox item")
        }

        event<BlockDropItemEvent>(ignoreCancelled = true) {
            jukeboxes.remove(block.location)
                ?: return@event

            if (!player.hasPermission(JukeboxPermissions.DELETE)) {
                isCancelled = true
                return@event
            }

            items.forEach { item ->
                if (item.itemStack.type == Material.JUKEBOX) {
                    JukeboxItem.tagAndNameItem(item.itemStack)
                }
            }

            player.sendMessage(!"&cRemoved a jukebox item")
        }

        rawCommand("get-jukebox") {
            permission = JukeboxPermissions.GET
            playerOnly = true
            runs {
                player.inventory.addItem(JukeboxItem.getItem())
                player.sendMessage(!"&aGiven you a jukebox item")
            }
        } register false
    }

    fun loadJukeboxes() {
        config.getList("locations")
            ?.filterIsInstance<ConfigurationSection>()
            ?.map { it.getLocation("") }
    }

    fun saveJukeboxes() {
        config.set("locations", jukeboxes.keys)
        saveConfig()
    }

    companion object {
        private lateinit var instance: JukeboxGuiPlugin
        fun get() = instance
    }

}
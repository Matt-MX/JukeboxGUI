package com.mattmx.jukebox

import com.mattmx.ktgui.GuiManager
import com.mattmx.ktgui.commands.declarative.arg.impl.intArgument
import com.mattmx.ktgui.commands.declarative.div
import com.mattmx.ktgui.commands.rawCommand
import com.mattmx.ktgui.dsl.event
import com.mattmx.ktgui.dsl.placeholder
import com.mattmx.ktgui.dsl.placeholderExpansion
import com.mattmx.ktgui.scheduling.async
import com.mattmx.ktgui.utils.not
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.MemorySection
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

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
            if (!jukeboxes.containsKey(block.location)) return@event

            if (!player.hasPermission(JukeboxPermissions.DELETE)) {
                isCancelled = true
                return@event
            }

            jukeboxes.remove(block.location)
                ?: return@event

            player.sendMessage(!"&cRemoved a jukebox item")
            async { saveJukeboxes() }
        }

        event<BlockDropItemEvent>(ignoreCancelled = true) {
            if (!jukeboxes.containsKey(block.location)) return@event

            if (!player.hasPermission(JukeboxPermissions.DELETE)) {
                isCancelled = true
                return@event
            }

            jukeboxes.remove(block.location)
                ?: return@event

            items.forEach { item ->
                if (item.itemStack.type == Material.JUKEBOX) {
                    JukeboxItem.tagAndNameItem(item.itemStack)
                }
            }

            player.sendMessage(!"&cRemoved a jukebox item")
            async { saveJukeboxes() }
        }

        rawCommand("get-jukebox") {
            permission = JukeboxPermissions.GET
            playerOnly = true
            runs {
                player.inventory.addItem(JukeboxItem.getItem())
                player.sendMessage(!"&aGiven you a jukebox item")
            }
        } register false

        placeholderExpansion {
            val x by intArgument()
            val y by intArgument()
            val z by intArgument()
            placeholder("now-playing" / x / y / z) {
                val world = requestedBy?.location?.world ?: return@placeholder "Invalid world"
                val loc = Location(world, x().toDouble(), y().toDouble(), z().toDouble())
                val jukebox = jukeboxes[loc] ?: return@placeholder "Unknown jukebox"

                jukebox.currentlyPlaying?.name() ?: "Nothing"
            }
        }
    }

    fun loadJukeboxes() {
        this.jukeboxes.putAll(
            config.get("locations")
                .let { it as? MemorySection }
                .also { println(it?.get(".")) }
                .let { it as? Collection<Location> }
                ?.map { loc -> loc to Jukebox(loc) }
                ?: emptyList()
        )
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
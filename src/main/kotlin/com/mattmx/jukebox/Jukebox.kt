package com.mattmx.jukebox

import com.mattmx.ktgui.GuiManager
import com.mattmx.ktgui.components.screen.GuiScreen
import com.mattmx.ktgui.dsl.button
import com.mattmx.ktgui.dsl.gui
import com.mattmx.ktgui.sound.sound
import com.mattmx.ktgui.utils.not
import com.mattmx.ktgui.utils.translatable
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag

class Jukebox(
    val location: Location
) {
    private val guiIdentifier = "jukebox_gui_${location.world.name}_${location.toVector()}"
    val isPlaying: Boolean
        get() = currentlyPlaying != null
    var currentlyPlaying: Sound? = null
        private set

    fun playIfNotAlready(song: Key): Boolean {
        if (isPlaying) {
            return false
        }

        play(song)
        return true
    }

    fun play(song: Key) {
        stop()

        val sound = sound(song)
            .location(location)
            .build()

        currentlyPlaying = sound

        location.world.playSound(sound)

        refreshGuis()
    }

    fun stop() {
        if (currentlyPlaying == null) return

        location.world.stopSound(currentlyPlaying!!)

        currentlyPlaying = null

        refreshGuis()
    }

    fun refreshGuis() {
        GuiManager.getPlayersInGui()
            .filterValues { gui -> gui is GuiScreen }
            .filterValues { gui -> (gui as GuiScreen).id == guiIdentifier }
            .forEach { (player, _) ->
                createGui(player).open(player)
            }
    }

    fun createGui(player: Player): GuiScreen {
        return gui(
            title = !"Jukebox",
            rows = 6
        ) {
            id = guiIdentifier

            // fill gui
            // Start at 2nd slot on first row
            var i = 9 + 1
            for (song in SONGS) {
                val asItem = Material.entries.firstOrNull { it.key() == song.key() }
                    ?: continue

                val isCurrentlyPlaying = currentlyPlaying?.name() == song.key()

                button(asItem) {
                    named(asItem.translationKey().translatable.color(NamedTextColor.LIGHT_PURPLE))
                    lore {
                        if (isCurrentlyPlaying) {
                            if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@lore
                            +Component.empty()
                            +!"&a⏹ Click to stop"
                        } else {
                            if (!player.hasPermission(JukeboxPermissions.ACTION_PLAY)) return@lore
                            +Component.empty()
                            +!"&a▶ Click to play"
                        }
                    }
                    if (isCurrentlyPlaying) {
                        enchant { put(Enchantment.MENDING, 1) }
                        postBuild {
                            editMeta { meta ->
                                meta.addItemFlags(*ItemFlag.entries.toTypedArray())
                            }
                        }

                        if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@button
                        click.left { stop() }
                    } else {
                        if (!player.hasPermission(JukeboxPermissions.ACTION_PLAY)) return@button
                        click.left { play(song.key()) }
                    }
                } slot i

                i++
                if (i % 9 == 0) {
                    i += 2
                }
            }

            if (currentlyPlaying == null) {
                button(Material.RED_STAINED_GLASS_PANE) {
                    named(!"&cNothing is playing")
                    lore {
                        if (!player.hasPermission(JukeboxPermissions.ACTION_PLAY)) return@lore
                        +Component.empty()
                        +!"&fChoose a song!"
                    }
                } slot last() - 4
            } else {
                button(Material.LIME_STAINED_GLASS_PANE) {
                    named(!"&aNow playing:")
                    lore {
                        val asItem = Material.entries.firstOrNull { it.key() == currentlyPlaying?.name() }
                            ?: return@lore

                        +asItem.translationKey().translatable.color(NamedTextColor.LIGHT_PURPLE)

                        if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@lore
                        +Component.empty()
                        +!"&c⏹ Click to stop"
                    }
                    if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@button
                    click.left { stop() }
                }
            }

            button(Material.SPECTRAL_ARROW) {
                named(!"&dClose")
                click.left { forceClose() }
            } slot last()
        }
    }

    companion object {
        val SONGS = org.bukkit.Sound.entries
            .toTypedArray()
            .filter { it.key().asString().contains("music_disc") }
    }
}
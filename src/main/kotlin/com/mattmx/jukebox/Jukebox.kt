package com.mattmx.jukebox

import com.destroystokyo.paper.ParticleBuilder
import com.mattmx.ktgui.GuiManager
import com.mattmx.ktgui.components.screen.GuiScreen
import com.mattmx.ktgui.dsl.button
import com.mattmx.ktgui.dsl.gui
import com.mattmx.ktgui.scheduling.TaskTracker
import com.mattmx.ktgui.scheduling.TaskTrackerTask
import com.mattmx.ktgui.sound.sound
import com.mattmx.ktgui.utils.not
import com.mattmx.ktgui.utils.translatable
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class Jukebox(
    val location: Location
) {
    private val guiIdentifier = "jukebox_gui_${location.world.name}_${location.toVector()}"
    val isPlaying: Boolean
        get() = currentlyPlaying != null

    private val particles = ParticleBuilder(Particle.NOTE)
        .offset(0.5, 0.5, 0.5)
        .location(location.toCenterLocation().add(0.0, 1.0, 0.0))
        .allPlayers()
        .count(5)
    var currentlyPlaying: Sound? = null
        private set
    val tasks = TaskTracker()

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
            .volume(2f)
            .build()

        currentlyPlaying = sound
        val ticks = getSongDuration(sound).inWholeSeconds * 20L

        var repeating: TaskTrackerTask? = null
        tasks.runAsyncRepeat(20L) {
            repeating = this
            particles.spawn()
        }
        tasks.runSyncLater(ticks) {
            repeating?.cancel()
            stop()
            tasks.cancelAll()
        }

        // TODO: maybe set block state?

        location.world.playSound(sound)

        refreshGuis()
    }

    fun stop() {
        if (currentlyPlaying == null) return

        location.world.stopSound(currentlyPlaying!!)

        currentlyPlaying = null
        tasks.cancelAll()

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
                val asItem = Material.entries.firstOrNull { it.key().asString() == song.key().asString().replace(".", "_") }
                    ?: continue

                val isCurrentlyPlaying = currentlyPlaying?.name() == song.key()

                button(asItem) {
                    named(asItem.translationKey().translatable.color(NamedTextColor.LIGHT_PURPLE).fallback("Unknown Disc (Outdated Client)"))
                    lore {
                        if (isCurrentlyPlaying) {
                            if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@lore
                            +Component.empty()
                            +!"&c⏹ Click to stop"
                        } else {
                            if (!player.hasPermission(JukeboxPermissions.ACTION_PLAY)) return@lore
                            +Component.empty()
                            +!"&a▶ Click to play"
                        }
                    }
                    if (isCurrentlyPlaying) {
                        enchant { put(Enchantment.MENDING, 1) }
                        postBuild {
                            editMeta { meta -> meta.addItemFlags(ItemFlag.HIDE_ENCHANTS) }
                        }

                        if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@button
                        click.left { stop() }
                    } else {
                        if (!player.hasPermission(JukeboxPermissions.ACTION_PLAY)) return@button
                        click.left { play(song.key()) }
                    }
                } slot i

                i++
                if ((i + 1) % 9 == 0) {
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
                val asItem = Material.entries.firstOrNull { it.key().asString() == currentlyPlaying!!.name().asString().replace(".", "_") }
                    ?: Material.LIME_STAINED_GLASS_PANE
                button(asItem) {
                    named(!"&aNow playing:")
                    lore {

                        // TODO incorrect key
//                        +asItem.translationKey().translatable.color(NamedTextColor.LIGHT_PURPLE)

                        if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@lore
                        +Component.empty()
                        +!"&c⏹ Click to stop"
                    }
                    if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@button
                    click.left { stop() }
                } slot last() - 4
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

        fun getSongDuration(song: Sound) = DURATION.getOrDefault(song.name().asString().replace("minecraft:music_disc.", ""), 3.minutes)

        val DURATION = hashMapOf(
            "5" to 2.minutes + 58.seconds,
            "cat" to 3.minutes + 5.seconds,
            "blocks" to 5.minutes + 45.seconds,
            "chirp" to 3.minutes + 5.seconds,
            "far" to 2.minutes + 54.seconds,
            "mall" to 3.minutes + 17.seconds,
            "mellohi" to 1.minutes + 36.seconds,
            "stal" to 2.minutes + 30.seconds,
            "strad" to 3.minutes + 8.seconds,
            "ward" to 4.minutes + 11.seconds,
            "11" to 1.minutes + 11.seconds,
            "wait" to 3.minutes + 58.seconds,
            "otherside" to 3.minutes + 15.seconds,
            "pigstep" to 2.minutes + 28.seconds,
            "relic" to 3.minutes + 38.seconds,
            "creator" to 2.minutes + 56.seconds,
            "creator_music_box" to 1.minutes + 13.seconds,
            "precipice" to 4.minutes + 59.seconds,
        )
    }
}
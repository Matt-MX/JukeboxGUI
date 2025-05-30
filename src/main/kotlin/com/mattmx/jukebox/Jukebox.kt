package com.mattmx.jukebox

import com.destroystokyo.paper.ParticleBuilder
import com.mattmx.ktgui.GuiManager
import com.mattmx.ktgui.components.screen.GuiScreen
import com.mattmx.ktgui.cooldown.ActionCoolDown
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
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class Jukebox(
    val location: Location
) {
    val limiter = ActionCoolDown<Player>(Duration.ofSeconds(30))
    private val guiIdentifier = "jukebox_gui_${location.world.name}_${location.toVector()}"
    val isPlaying: Boolean
        get() = currentlyPlaying != null

    private val particles = ParticleBuilder(Particle.NOTE)
        .offset(0.5, 0.5, 0.5)
        .location(location.toCenterLocation().add(0.0, 1.0, 0.0))
        .allPlayers()
        .count(5)
    var lastPlayedBy: String? = null
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
            .source(Sound.Source.RECORD)
            .location(location)
            .volume(0.5f)
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
        (location.block.state as? org.bukkit.block.Jukebox)?.let { state ->
            state.setPlaying(getMaterial(sound.name()))
            state.startPlaying()
        }
        location.world.playSound(sound)

        refreshGuis()
    }

    fun stop() {
        if (currentlyPlaying == null) return

        location.world.stopSound(currentlyPlaying!!)

        (location.block.state as? org.bukkit.block.Jukebox)?.let { state ->
            state.setPlaying(null)
            state.stopPlaying()
        }

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
                val asItem = getMaterial(song.key())
                    ?: continue

                val isCurrentlyPlaying = currentlyPlaying?.name() == song.key()

                button(asItem) {
                    named(
                        asItem.translationKey()
                            .translatable
                            .color(TextColor.fromHexString("#FF331C"))
                            .fallback("Unknown Disc (Outdated Client)")
                    )
                    lore {
                        if (isCurrentlyPlaying) {
                            if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@lore
                            +Component.empty()
                            +!"<error>⏹ Click to stop".branding()
                        } else {
                            if (!player.hasPermission(JukeboxPermissions.ACTION_PLAY)) return@lore
                            +Component.empty()
                            +!"<green>▶ Click to play".branding()
                        }
                    }
                    if (isCurrentlyPlaying) {
                        enchant { put(Enchantment.MENDING, 1) }
                        postBuild {
                            editMeta { meta -> meta.addItemFlags(ItemFlag.HIDE_ENCHANTS) }
                        }

                        if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@button
                        click.left {
                            if (limiter.test(player)) {
                                forceClose()
                                stop()
                            } else reply(!"<error>Please wait before doing that again.".branding())
                        }
                    } else {
                        if (!player.hasPermission(JukeboxPermissions.ACTION_PLAY)) return@button
                        click.left {
                            if (limiter.test(player)) {
                                lastPlayedBy = player.name
                                forceClose()
                                play(song.key())
                            } else reply(!"<error>Please wait before doing that again.".branding())
                        }
                    }
                } slot i

                i++
                if ((i + 1) % 9 == 0) {
                    i += 2
                }
            }

            if (currentlyPlaying == null) {
                button(Material.RED_STAINED_GLASS_PANE) {
                    named(!"<error>Nothing is playing".branding())
                    lore {
                        if (!player.hasPermission(JukeboxPermissions.ACTION_PLAY)) return@lore
                        +Component.empty()
                        +!"<light>Choose a song!".branding()
                    }
                } slot last() - 4
            } else {
                val asItem = getMaterial(currentlyPlaying!!.name())
                    ?: Material.LIME_STAINED_GLASS_PANE
                button(asItem) {
                    named(!"<green>Now playing:".branding())
                    lore {

                        // TODO incorrect key
//                        +asItem.translationKey().translatable.color(NamedTextColor.LIGHT_PURPLE)
                        +!"<dull>Played by: ${lastPlayedBy ?: "Unknown"}".branding()

                        if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@lore
                        +Component.empty()
                        +!"<error>⏹ Click to stop".branding()
                    }
                    if (!player.hasPermission(JukeboxPermissions.ACTION_STOP)) return@button
                    click.left {
                        if (limiter.test(player)) {
                            forceClose()
                            stop()
                        } else reply(!"<error>Please wait before doing that again.".branding())
                    }
                } slot last() - 4
            }

            button(Material.SPECTRAL_ARROW) {
                named(!"<title>Close".branding())
                click.left { forceClose() }
            } slot last()
        }
    }

    fun destroy() {
        ActionCoolDown.unregister(this.limiter)
        this.tasks.cancelAll()
    }

    companion object {
        val SONGS = org.bukkit.Sound
            .values()
            .filter { it.key().asString().contains("music_disc") }

        fun getMaterial(song: Key) =
            Material.entries.firstOrNull { it.key().asString() == song.asString().replace(".", "_") }

        fun getSongDuration(song: Sound) =
            DURATION.getOrDefault(song.name().asString().replace("minecraft:music_disc.", ""), 3.minutes)

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
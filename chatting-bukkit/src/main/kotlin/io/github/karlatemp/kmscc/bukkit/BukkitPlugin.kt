/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/25 18:03:24
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-bukkit.main/BukkitPlugin.kt
 */

package io.github.karlatemp.kmscc.bukkit

import io.github.karlatemp.kmscc.util.dump
import io.github.karlatemp.kmscc.util.getString
import io.github.karlatemp.kmscc.util.putString
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.Frame
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.*
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.chat.ComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.collections.HashMap


@KtorExperimentalAPI
@Suppress("unused")
class BukkitPlugin : JavaPlugin() {
    val client = HttpClient {
        install(WebSockets)
    }
    var shutdowning = false
    val group = ThreadGroup("KMSCC - Client Group")
    val counter = AtomicInteger()
    val service = Executors.newScheduledThreadPool(2) {
        Thread(group, it, "KMSCC - Client Thread #" + counter.getAndIncrement())
            .apply { isDaemon = true }
    }
    val dispatcher = service.asCoroutineDispatcher()

    var host = "localhost"
    var port = 5798

    private val nms: NmsInterface = Class.forName(
        "io.github.karlatemp.kmscc.bukkit.nms.$nmsVersion"
    ).asSubclass(NmsInterface::class.java).newInstance()

    private var prefix: String = "[Server]"
    private var worldNameMapping = mapOf<String, String>()

    override fun onEnable() {
        host = config.getString("host", "localhost")!!
        port = config.getInt("port", 5798)
        reconnect()
        println(obcClass("inventory.CraftItemStack"))
        println(nmsClass("ItemStack"))
        prefix = config.getString("server-name", prefix)!!
        worldNameMapping = config.getConfigurationSection("worlds")?.let { section ->
            val map = HashMap<String, String>()
            section.getKeys(false).forEach { key ->
                map[key] = section.getString(key) ?: return@forEach
            }
            map
        } ?: worldNameMapping
    }

    private val formatter = Pattern.compile("""\[([a-z]+)(|:([A-Za-z0-9]+))]""")

    private fun reconnect() {
        val plugin = this
        println(":Reconnect")
        CoroutineScope(dispatcher).launch {
            delay(5000)
            client.ws(HttpMethod.Get, host, port, "/standard-server", {
                headers["Authenticate-Token"] = ""
            }) {
                @Suppress("BlockingMethodInNonBlockingContext")
                val listener = object : Listener {
                    @EventHandler
                    fun PlayerJoinEvent.listen() {
                        runBlocking {
                            outgoing.send(Frame.Binary(true, ByteBuffer.allocateDirect(64).apply {
                                putShort(1)
                                putString(player.name)
                                flip()
                            }.dump()))
                            flush()
                            println("Fired PlayerJoinEvent")
                        }
                    }

                    @EventHandler
                    fun PlayerQuitEvent.listen() {
                        runBlocking {
                            outgoing.send(Frame.Binary(true, ByteBuffer.allocateDirect(64).apply {
                                putShort(2)
                                putString(player.name)
                                flip()
                            }.dump()))
                            flush()

                            println("Fired PlayerQuitEvent")
                        }
                    }

                    @EventHandler(priority = EventPriority.MONITOR)
                    fun PlayerDeathEvent.listen() {
                        val dm = deathMessage
                        if (dm != null) {
                            val comp = nms.getDeathMessageObject(player = entity)
                            val json = if (nms.deathMessageObjectToPlain(comp) == dm) {
                                nms.deathMessageObjectToComponent(comp)
                            } else ComponentSerializer.toString(*TextComponent.fromLegacyText(dm))
                            runBlocking {
                                val buf = Charsets.UTF_8.encode(json)
                                outgoing.send(
                                    Frame.Binary(
                                        true, ByteBuffer.allocateDirect(
                                            2 + 2 + buf.remaining()
                                        ).apply {
                                            putShort(4)
                                            putShort(buf.remaining().toShort())
                                            put(buf)
                                            flip()
                                        }.dump()
                                    )
                                )
                                flush()

                                println("Fired PlayerDeathEvent")
                            }
                        }
                    }

                    @EventHandler(priority = EventPriority.MONITOR)
                    fun AsyncPlayerChatEvent.listen() {
                        runBlocking {
                            val playerName = Charsets.UTF_8.encode(player.name)
                            val json = Charsets.UTF_8.encode(makeJson())
                            val size = 2 +
                                    2 + playerName.remaining() +
                                    2 +
                                    2 + json.remaining()
                            outgoing.send(Frame.Binary(true, ByteBuffer.allocateDirect(size).apply {
                                putShort(3) // ID

                                putShort(playerName.remaining().toShort())
                                put(playerName) // Player Name

                                putShort(0) // metadata

                                putShort(json.remaining().toShort())
                                put(json) // message

                                flip()
                            }.dump()))
                            outgoing.send(Frame.Text("SB Binary"))
                            flush()
                            println("Fired PlayerMessageEvent")
                        }
                        isCancelled = true
                    }

                    private fun AsyncPlayerChatEvent.makeJson(): String {
                        // [i] -> item
                        // [p] -> position
                        val matcher = formatter.matcher(message)
                        val components = LinkedList<BaseComponent>()
                        components.addAll(
                            listOf(
                                *TextComponent.fromLegacyText(prefix)
                            )
                        )
                        val location = player.location
                        worldNameMapping[location.world!!.name]?.let { display ->
                            components.addAll(
                                listOf(
                                    *TextComponent.fromLegacyText(display)
                                )
                            )
                        }
                        components.add(TextComponent(player.displayName).apply {
                            color = ChatColor.GOLD
                        })
                        components.add(TextComponent(">>> ").apply {
                            color = ChatColor.AQUA
                        })

                        var position = 0
                        loop@
                        while (matcher.find()) {
                            if (position != matcher.start()) {
                                val prev = message.substring(position, matcher.start())
                                components.add(TextComponent(prev))
                            }
                            position = matcher.end()
                            // 1 -> id
                            when (matcher.group(1)) {
                                "i", "I", "item" -> {
                                    // Build item
                                    val id = matcher.group(3)
                                    if (id == null) { // Item on main hand
                                        val item = player.inventory.itemInMainHand
                                        if (item.type != Material.AIR) {
                                            components.addAll(listOf(*nms.toTextComponent(item)))
                                            continue@loop
                                        }
                                    } else {
                                        val slot = id.toIntOrNull()
                                        if (slot != null) {
                                            val item = runCatching { player.inventory.getItem(slot) }.getOrNull()
                                            if (item != null && item.type != Material.AIR) {
                                                components.addAll(listOf(*nms.toTextComponent(item)))
                                                continue@loop
                                            }
                                        }
                                    }
                                    // Invalid item
                                    position = matcher.start()
                                }
                                "p", "loc", "location", "pos", "position" -> {
                                    // location
                                    components.add(TextComponent("[").apply {
                                        color = ChatColor.DARK_GRAY
                                        worldNameMapping[location.world!!.name]?.also { display ->
                                            TextComponent.fromLegacyText(display)
                                                .also { array ->
                                                    if (array.isNotEmpty()) {
                                                        array[0].apply {
                                                            if (colorRaw == null) {
                                                                color = ChatColor.GOLD
                                                            }
                                                        }
                                                    }
                                                }
                                                .forEach(this::addExtra)
                                        } ?: run {
                                            addExtra(TextComponent(location.world!!.name).apply {
                                                color = ChatColor.GOLD
                                            })
                                        }
                                        fun insertPos(int: Any) {
                                            addExtra(", ")
                                            addExtra(TextComponent(int.toString()).apply {
                                                color = ChatColor.AQUA
                                            })
                                        }
                                        insertPos(location.blockX)
                                        insertPos(location.blockY)
                                        insertPos(location.blockZ)
                                        addExtra("]")
                                    })
                                }
                                else -> position = matcher.start()
                            }
                        }
                        if (position != message.length) {
                            components.add(TextComponent(message.substring(position)))
                        }
                        return ComponentSerializer.toString(*components.toArray(arrayOf<BaseComponent>()))
                    }
                }
                coroutineContext[Job]!!.invokeOnCompletion {
                    HandlerList.unregisterAll(listener)
                }
                server.pluginManager.registerEvents(listener, plugin)

                for (frame in incoming) {
                    if (shutdowning) return@ws
                    println(frame)
                    (frame as? Frame.Binary)?.apply {
                        when (buffer.short.toInt()) {
                            1 -> {
                                Bukkit.getServer().consoleSender.sendMessage(buffer.getString())
                            }
                            2 -> {
                                buffer.getString().let {
                                    ComponentSerializer.parse(it)
                                }.let { message ->
                                    Bukkit.getOnlinePlayers().forEach { player ->
                                        player.spigot().sendMessage(
                                            *message
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.invokeOnCompletion { exception ->
            println(":Error $exception")
            if (!shutdowning)
                reconnect()
        }
    }

    override fun onDisable() {
        shutdowning = true
        client.close()
        service.shutdown()
    }

}

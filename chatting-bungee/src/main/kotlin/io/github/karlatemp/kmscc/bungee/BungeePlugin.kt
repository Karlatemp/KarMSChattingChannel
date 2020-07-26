/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/26 10:26:51
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-bungee.main/BungeePlugin.kt
 */

package io.github.karlatemp.kmscc.bungee

import io.github.karlatemp.kmscc.util.dump
import io.github.karlatemp.kmscc.util.getString
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.Frame
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.chat.ComponentSerializer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Suppress("unused")
@KtorExperimentalAPI
class BungeePlugin : Plugin() {
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

    override fun onEnable() {
        reconnect()
    }

    private fun reconnect() {
        println(":Reconnect")
        CoroutineScope(dispatcher).launch {
            delay(5000)
            client.ws(HttpMethod.Get, host, port, "/bungee-cord", {
                headers["Authenticate-Token"] = ""
            }) {
                for (frame in incoming) {
                    println(frame)
                    (frame as? Frame.Binary)?.apply {
                        if (shutdowning) return@ws
                        when (buffer.dump("I -> ").short.toInt()) {
                            1 -> {
                                ProxyServer.getInstance().console
                                    .sendMessage(
                                        *TextComponent.fromLegacyText(
                                            buffer.getString()
                                        )
                                    )
                            }
                            2 -> {
                                val components = ComponentSerializer.parse(buffer.getString())
                                ProxyServer.getInstance().players.forEach { player ->
                                    player.sendMessage(*components)
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
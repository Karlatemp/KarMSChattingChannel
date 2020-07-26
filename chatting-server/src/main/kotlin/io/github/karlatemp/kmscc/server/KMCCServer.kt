/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/25 15:02:05
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-server.main/KMCCServer.kt
 */

package io.github.karlatemp.kmscc.server

import io.github.karlatemp.kmscc.event.AbsEvent
import io.github.karlatemp.kmscc.event.broadcast
import io.github.karlatemp.kmscc.event.listen
import io.github.karlatemp.kmscc.event.listenNonCoroutine
import io.github.karlatemp.kmscc.util.dump
import io.github.karlatemp.kmscc.util.getString
import io.github.karlatemp.kmscc.util.putString
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.cio.websocket.Frame
import io.ktor.routing.Routing
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.pipeline.ContextDsl
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.asCoroutineDispatcher
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.chat.ComponentSerializer
import ninja.leaping.configurate.ConfigurationOptions
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.HashMap


val base = File("kmscc")
val config = File(base, "server.conf")
val loader = HoconConfigurationLoader.builder()
    .setFile(config)
    .setDefaultOptions(ConfigurationOptions.defaults().setShouldCopyDefaults(true))
    .build()
val conf = loader.load()

val authToken = conf["auth-token"].string

val NULL_MAP = mapOf<String, String>()

val service = Executors.newScheduledThreadPool(5, object : ThreadFactory {
    private val counter = AtomicInteger()
    private val group = ThreadGroup("KMCC - ThreadGroup")
    override fun newThread(r: Runnable): Thread {
        return Thread(group, r, "KMCC - Service #" + counter.getAndIncrement()).apply {
            isDaemon = false
        }
    }
})
val dispatcher = service.asCoroutineDispatcher()

operator fun CommentedConfigurationNode.get(vararg path: Any?): CommentedConfigurationNode {
    var node: CommentedConfigurationNode = this
    for (p in path) {
        node = node.getNode(p)
    }
    return node
}

@KtorExperimentalAPI
fun main() {
    val server = embeddedServer(CIO, environment = applicationEngineEnvironment {
        this.parentCoroutineContext = dispatcher
        this.module(Application::kmcc)

        connector {
            this.host = conf["host"].getString("localhost")
            this.port = conf["port"].getInt(5798)
        }
    })
    startControlCenter()
    server.start()
    listenNonCoroutine<AbsEvent>(context = dispatcher) { println(">>> $this") }
    service.execute { println("Hi") }
}

fun startControlCenter() {
    listenNonCoroutine<ExternalMessageIncomingEvent>(context = dispatcher) {
        val post = sender.name
        val msg = ComponentBuilder("[QQ]")
            .color(ChatColor.AQUA)
            .append(TextComponent.fromLegacyText(post), ComponentBuilder.FormatRetention.NONE)
            .append(" >>> ", ComponentBuilder.FormatRetention.NONE).color(ChatColor.GOLD)
            .append(
                TextComponent.fromLegacyText(
                    message
                        .replace('\r', ' ')
                        .replace('\n', ' ')
                ),
                ComponentBuilder.FormatRetention.NONE
            )
            .create()
        val legacyMsg = TextComponent(*msg).toLegacyText()
        ServerBroadcastEvent(legacyMsg).broadcast()
        BungeeBroadcastJsonEvent(ComponentSerializer.toString(*msg)).broadcast()
    }
    listenNonCoroutine<PlayerDeathEvent>(context = dispatcher) {
        val mg = ComponentSerializer.parse(json)
        translate(mg, StandardTranslator)
        val legacyMsg = TextComponent(*mg).toLegacyText()
        SendToBungee(legacyMsg).broadcast()
        ServerBroadcastJsonEvent(message = this.json, sourceServerId = serverId)
            .broadcast()
    }
    listenNonCoroutine<PlayerChattingEvent>(context = dispatcher) {
        val mg = ComponentSerializer.parse(message)
        translate(mg, StandardTranslator)
        val component = TextComponent(*mg)
        ExternalBroadcastEvent(component.toPlainText()).broadcast()
        ServerBroadcastEvent(component.toLegacyText()).broadcast()
        BungeeBroadcastJsonEvent(message).broadcast()
    }


}

@KtorExperimentalAPI
fun Application.kmcc() {
    install(WebSockets)
    routing {
        kmccSocket("/bungee-cord") {
            val serverId = UUID.randomUUID().toString()
            listen<ServerBroadcastEvent> {
                outgoing.send(Frame.Binary(true, buildMessage(size = 2048) {
                    putShort(1)
                    putString(message)
                }))
            }
            listen<SendToBungee> {
                outgoing.send(Frame.Binary(true, buildMessage(size = 2048) {
                    putShort(1)
                    putString(message)
                }))
            }
            listen<BungeeBroadcastJsonEvent> {
                outgoing.send(Frame.Binary(true, buildMessage(size = 2048) {
                    putShort(2)
                    putString(message)
                }))
            }
            for (frame in incoming) {
                (frame as? Frame.Binary)?.apply {
                    val buffer = buffer
                    when (buffer.short.toInt()) {
                        1 -> {
                            PlayerJoinedServerEvent(
                                ExternalSender(buffer.getString(), NULL_MAP),
                                serverId
                            ).broadcast()
                        }
                        2 -> {
                            PlayerLeavedServerEvent(
                                ExternalSender(buffer.getString(), NULL_MAP),
                                serverId
                            ).broadcast()
                        }
                    }
                }
            }
        }
        kmccSocket("/standard-server") {
            val serverId = UUID.randomUUID().toString()
            listen<ServerBroadcastEvent> {
                outgoing.send(Frame.Binary(true, buildMessage(size = 2048) {
                    putShort(1)
                    putString(message)
                }))
            }
            listen<ServerBroadcastJsonEvent> {
                if (this.sourceServerId != serverId)
                    outgoing.send(Frame.Binary(true, buildMessage(size = 2048) {
                        putShort(2)
                        putString(message)
                    }))
            }
            println(":looping")
            for (frame in incoming) {
                (frame as? Frame.Binary)?.apply {
                    val buffer = buffer
                    buffer.duplicate().apply {
                        while (hasRemaining()) {
                            val hex = Integer.toHexString(get().toInt() and 0xFF)
                            if (hex.length == 1) print("0$hex ")
                            else print("$hex ")
                        }
                        println()
                    }
                    when (buffer.short.toInt().apply { println(">  Event ID = $this") }) {
                        1 -> {
                            PlayerJoinedServerEvent(
                                ExternalSender(buffer.getString(), NULL_MAP),
                                serverId
                            ).broadcast()
                        }
                        2 -> {
                            PlayerLeavedServerEvent(
                                ExternalSender(buffer.getString(), NULL_MAP),
                                serverId
                            ).broadcast()
                        }
                        3 -> {
                            PlayerChattingEvent(
                                serverId,
                                ExternalSender(
                                    buffer.getString(),
                                    buffer.getUMap()
                                ),
                                buffer.getString()
                            ).broadcast()
                        }
                        4 -> {
                            PlayerDeathEvent(buffer.getString(), serverId).broadcast()
                        }
                    }
                    println()
                }
            }
            println(":AWA")
        }
        kmccSocket("/qq-group") {
            listen<ExternalBroadcastEvent> {
                outgoing.send(Frame.Binary(true, buildMessage(size = 2048) {
                    putShort(1)
                    putString(message)
                }))
            }
            for (frame in incoming) {
                (frame as? Frame.Binary)?.apply {
                    val buffer = buffer
                    if (buffer.dump().short.toInt() == 1) {
                        val senderName = buffer.getString()
                        val message = buffer.getString()
                        val metadata = buffer.getUMap()
                        ExternalMessageIncomingEvent(
                            message, ExternalSender(senderName, metadata)
                        ).broadcast()
                    }
                }
            }
        }
    }
}

private fun buildMessage(
    size: Int = 255,
    writer: ByteBuffer.() -> Unit
): ByteBuffer {
    return ByteBuffer.allocateDirect(size).apply(writer).apply { flip() }
}

fun ByteBuffer.getUMap(): Map<String, String> {
    val size = java.lang.Short.toUnsignedInt(short)
    return HashMap<String, String>(size).apply {
        repeat(size) {
            put(getString(), getString())
        }
    }
}

@KtorExperimentalAPI
@ContextDsl
private fun Routing.kmccSocket(
    path: String,
    body: suspend DefaultWebSocketServerSession.() -> Unit
) {
    webSocket(path) {
        println("New Session: $this, $path")
        val headers = call.request.headers
        authToken?.let { token ->
            if (token != headers["Authenticate-Token"]) {
                this.close()
                return@webSocket
            }
        }
        body()
    }
}
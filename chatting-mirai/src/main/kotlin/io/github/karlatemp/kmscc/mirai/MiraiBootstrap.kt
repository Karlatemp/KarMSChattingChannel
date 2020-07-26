/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/26 16:49:42
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-mirai.main/MiraiBootstrap.kt
 */

package io.github.karlatemp.kmscc.mirai

import io.github.karlatemp.kmscc.util.dump
import io.github.karlatemp.kmscc.util.getString
import io.github.karlatemp.miraikc.CoroutineThreadPool
import io.github.karlatemp.miraikc.bootstrap.main
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.Frame
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.data.LightApp
import net.mamoe.mirai.message.data.ServiceMessage
import java.nio.ByteBuffer
import java.util.logging.Level
import java.util.logging.Logger

@KtorExperimentalAPI
val client = HttpClient {
    install(WebSockets)
}

@KtorExperimentalAPI
@JvmName("main")
fun main0(args: Array<String>) {
    reconnect()
    main()
}

val groupId = 524743350L
val logger = Logger.getLogger("MiraiBootstrap")


@KtorExperimentalAPI
fun reconnect() {
    println("Reconnect")
    val scope = CoroutineScope(CoroutineThreadPool)
    scope.launch {
        delay(5000)
        client.ws(HttpMethod.Get, "localhost", 5798, "/qq-group", {
            headers["Authenticate-Token"] = ""
        }) {
            subscribeAlways<GroupMessageEvent> {
                //scope.launch llc@{
                run llc@{
                    println("FAQ")
                    if (message.any { it is LightApp || it is ServiceMessage }) return@llc

                    if (group.id == groupId) {
                        println("AF")
                        val name = Charsets.UTF_8.encode(sender.nameCardOrNick)
                        val message = Charsets.UTF_8.encode(message.contentToString())
                        outgoing.send(
                            Frame.Binary(
                                true, ByteBuffer.allocateDirect(
                                    2 + 2 + 2 + 2 + name.remaining() + message.remaining()
                                ).apply {
                                    putShort(1)

                                    putShort(name.remaining().toShort())
                                    put(name)

                                    putShort(message.remaining().toShort())
                                    put(message)

                                    putShort(0)
                                    flip()
                                }.dump()
                            )
                        )
                        println("FW")
                        flush()
                        println("FX")
                    }
                    println("Done")
                }
            }
            @Suppress("BlockingMethodInNonBlockingContext")
            for (frame in incoming) {
                println(frame)
                (frame as? Frame.Binary)?.apply {
                    when (buffer.short.toInt()) {
                        1 -> {
                            runCatching {
                                Bot.botInstances.random()
                                    .getGroup(groupId)
                                    .sendMessage(buffer.getString())
                            }.onFailure {
                                logger.log(Level.WARNING, "Send Message Failed", it)
                            }
                        }
                    }
                }
            }
        }
    }.invokeOnCompletion {
        println("Error $it")
        reconnect()
    }
}
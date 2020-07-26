/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/25 16:15:14
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-server.main/events.kt
 */

@file:Suppress("unused")

package io.github.karlatemp.kmscc.server

import io.github.karlatemp.kmscc.event.AbsEvent

data class ExternalSender(
    val name: String, val metadata: Map<String, String>
)

data class ExternalMessageIncomingEvent(
    val message: String,
    val sender: ExternalSender
) : AbsEvent()

data class PlayerDeathMessageEvent(
    val messageJson: String,
    val serverId: String
) : AbsEvent()

data class PlayerChattingEvent(
    val serverId: String,
    val sender: ExternalSender,
    val message: String
) : AbsEvent()

data class ExternalBroadcastEvent(
    val message: String
) : AbsEvent()

data class ServerBroadcastEvent(
    val message: String
) : AbsEvent()

data class SendToBungee(val message: String) : AbsEvent()

data class ServerBroadcastJsonEvent(
    val message: String,
    val sourceServerId: String
) : AbsEvent()

data class BungeeBroadcastJsonEvent(
    val message: String
) : AbsEvent()


data class PlayerJoinedServerEvent(
    val player: ExternalSender,
    val serverId: String
) : AbsEvent()

data class PlayerLeavedServerEvent(
    val player: ExternalSender,
    val serverId: String
) : AbsEvent()

data class PlayerDeathEvent(
    val json: String,
    val serverId: String
) : AbsEvent()
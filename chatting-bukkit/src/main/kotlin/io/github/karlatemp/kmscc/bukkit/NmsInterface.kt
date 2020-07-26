/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/26 13:33:05
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-bukkit.main/NmsInterface.kt
 */

package io.github.karlatemp.kmscc.bukkit

import net.md_5.bungee.api.chat.BaseComponent
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

interface NmsInterface {
    fun toTextComponent(stack: ItemStack): Array<BaseComponent>

    fun getDeathMessageObject(player: Player): Any
    fun deathMessageObjectToPlain(deathMessageObject: Any): String
    fun deathMessageObjectToComponent(deathMessageObject: Any): String
}
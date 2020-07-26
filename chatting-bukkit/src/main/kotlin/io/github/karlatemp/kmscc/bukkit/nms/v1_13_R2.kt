/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/26 13:32:22
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-bukkit.main/v1_12_R1.kt
 */

package io.github.karlatemp.kmscc.bukkit.nms

import io.github.karlatemp.kmscc.bukkit.NmsInterface
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.chat.ComponentSerializer
import net.minecraft.server.v1_13_R2.IChatBaseComponent
import net.minecraft.server.v1_13_R2.IChatBaseComponent.ChatSerializer
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_13_R2.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class v1_13_R2 : NmsInterface {
    override fun toTextComponent(stack: ItemStack): Array<BaseComponent> {
        return ComponentSerializer.parse(
            ChatSerializer.a(
                CraftItemStack.asNMSCopy(stack)
                    .A()
            )
        )
    }

    override fun getDeathMessageObject(player: Player): Any {
        return (player as CraftPlayer).handle.combatTracker
            .deathMessage
    }

    override fun deathMessageObjectToComponent(deathMessageObject: Any): String {
        return ChatSerializer.a(
            deathMessageObject as IChatBaseComponent
        )
    }

    override fun deathMessageObjectToPlain(deathMessageObject: Any): String {
        return (deathMessageObject as IChatBaseComponent).string
    }
}
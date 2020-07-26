/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/26 12:03:43
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-bukkit.main/ReflectionTools.kt
 */

package io.github.karlatemp.kmscc.bukkit

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

private val obcPackage: String by lazy {
    val path = Bukkit.getServer().javaClass.name
    path.substring(0, path.lastIndexOf('.') + 1).apply {
        println("OBC -> $this")
    }
}
val nmsVersion: String by lazy {
    obcPackage.let {
        it.substring(
            it.lastIndexOf(
                '.',
                it.length - 2
            ) + 1,
            it.length - 1
        )
    }.apply { println("NMS -> $this") }
}

private val nmsPackage: String by lazy { "net.minecraft.server.$nmsVersion." }

private val cachedNMSClasses = ConcurrentHashMap<String, Class<*>>()
private val cachedNMSClassesLoader = Function<String, Class<*>> { path ->
    Class.forName("$nmsPackage$path")
}

private val cachedOBCClasses = ConcurrentHashMap<String, Class<*>>()
private val cachedOBCClassLoader = Function<String, Class<*>> { path ->
    Class.forName("$obcPackage$path")
}

fun obcClass(path: String): Class<*> =
    cachedOBCClasses.computeIfAbsent(path, cachedOBCClassLoader)


fun nmsClass(path: String): Class<*> =
    cachedNMSClasses.computeIfAbsent(path, cachedNMSClassesLoader)

object ItemConvert {
    private val craftItemStackClass by lazy {
        obcClass("inventory.CraftItemStack")
    }
    private val asNMSCopyMethod by lazy {
        craftItemStackClass.getMethod("asNMSCopy", ItemStack::class.java).apply {
            isAccessible = true
        }
    }
    private val nmsItemStackClass by lazy {
        nmsClass("ItemStack")
    }
    private val nbtTagCompoundClass by lazy {
        nmsClass("NBTTagCompound")
    }
    private val saveNmsItemStackMethod by lazy {
        nmsItemStackClass.getMethod("save", nbtTagCompoundClass).apply {
            isAccessible = true
        }
    }

    fun convertItemToJson(itemStack: ItemStack): String {
        val nbtTagCompoundTag = nbtTagCompoundClass.newInstance()
        val nmsItemStack = asNMSCopyMethod.invoke(null, itemStack)
        val itemAsJsonObject = saveNmsItemStackMethod.invoke(nmsItemStack, nbtTagCompoundTag)
        return itemAsJsonObject.toString()
    }
}


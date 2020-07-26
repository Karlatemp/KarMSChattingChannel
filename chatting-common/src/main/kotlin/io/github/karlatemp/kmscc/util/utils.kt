/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/26 09:59:17
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-common.main/utils.kt
 */

package io.github.karlatemp.kmscc.util

import java.nio.ByteBuffer

@JvmOverloads
fun ByteBuffer.dump(prefix: String = ""): ByteBuffer {

    println(buildString {
        append(prefix)
        if (prefix.isNotEmpty()) append(' ')
        duplicate().apply {
            while (hasRemaining()) {
                val hex = Integer.toHexString(get().toInt() and 0xFF)
                if (hex.length == 1) append("0$hex ")
                else append("$hex ")
            }
        }
    })
    return this
}

fun ByteBuffer.putString(utf8: String) {
    val buf = Charsets.UTF_8.encode(utf8)
    putShort(buf.remaining().toShort())
    put(buf)
}

fun ByteBuffer.getString(): String {
    return Charsets.UTF_8.decode(
        ByteBuffer.wrap(
            ByteArray(java.lang.Short.toUnsignedInt(short)).apply {
                get(this)
            }
        )
    ).toString()
}

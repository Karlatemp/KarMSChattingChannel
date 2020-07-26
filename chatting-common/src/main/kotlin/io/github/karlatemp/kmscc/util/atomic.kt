/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/25 15:36:07
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-common.main/atomic.kt
 */

package io.github.karlatemp.kmscc.util


import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty


operator fun <V> AtomicReference<V>.setValue(
    ignored0: Any, property: KProperty<*>, value: V
) {
    set(value)
}

operator fun <V> AtomicReference<V>.getValue(
    ignored: Any, property: KProperty<*>
): V {
    return get()
}

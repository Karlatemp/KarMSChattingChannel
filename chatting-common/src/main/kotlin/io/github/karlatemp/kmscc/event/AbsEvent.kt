/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/25 15:32:16
 *
 * KarMSChattingChannel/KarMSChattingChannel.event-bus.main/AbsEvent.kt
 */

@file:Suppress("unused", "PropertyName")

package io.github.karlatemp.kmscc.event

import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class AbsEvent {
    @Transient
    internal var _interrupted = false

    val interrupted: Boolean get() = _interrupted
    fun interrupt() {
        _interrupted = true
    }

    open val simpleName: String get() = this.javaClass.simpleName
}

@JvmOverloads
suspend fun AbsEvent.broadcast(context: CoroutineContext = EmptyCoroutineContext) {
    withContext(context) {
        brocastInternal()
    }
}



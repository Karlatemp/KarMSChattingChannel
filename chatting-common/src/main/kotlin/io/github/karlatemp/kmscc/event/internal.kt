/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/25 15:45:37
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-common.main/internal.kt
 */

package io.github.karlatemp.kmscc.event

import io.github.karlatemp.kmscc.util.ConcurrentLinkedList
import kotlinx.coroutines.CoroutineExceptionHandler
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.coroutineContext

internal object EventLines {
    val lines = EnumMap<EventPriority, ConcurrentLinkedList<ListenerExecutor>>(EventPriority::class.java)
    val cachedPriorities = EventPriority.values()

    init {
        cachedPriorities.forEach { lines[it] = ConcurrentLinkedList() }
    }
}


internal val eventLogger = Logger.getLogger("KMSCC - EventBus")!!

internal suspend fun AbsEvent.brocastInternal() {
    _interrupted = false
    EventLines.cachedPriorities.forEach { priority ->
        EventLines.lines[priority]!!.forEach { listener ->
            if (_interrupted) return@brocastInternal
            kotlin.runCatching {
                listener.callEvent(this)
            }.onFailure { exception ->
                coroutineContext[CoroutineExceptionHandler]?.apply {
                    handleException(coroutineContext, exception)
                } ?: eventLogger.apply {
                    log(
                        Level.WARNING,
                        "Exception in posting event ${this@brocastInternal.simpleName} in priority $priority",
                        exception
                    )
                }
            }
        }
    }
}

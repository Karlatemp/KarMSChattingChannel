/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/07/25 15:38:23
 *
 * KarMSChattingChannel/KarMSChattingChannel.chatting-common.main/ListenerExecutor.kt
 */

@file:Suppress("unused")

package io.github.karlatemp.kmscc.event

import io.github.karlatemp.kmscc.util.ConcurrentLinkedList
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

interface ListenerExecutor {
    suspend fun callEvent(event: AbsEvent)
}

interface Listener : ListenerExecutor {
    val priority: EventPriority get() = EventPriority.MONITOR
}

enum class EventPriority {
    HIGHEST, HIGH, NORMAL, LOW, LOWEST, MONITOR
}

open class SimpleListener(
    override val priority: EventPriority = EventPriority.MONITOR,
    private val context: CoroutineContext = EmptyCoroutineContext,
    private val executor: ListenerExecutor
) : Listener {
    override suspend fun callEvent(event: AbsEvent) {
        withContext(context) {
            try {
                executor.callEvent(event)
            } catch (any: Throwable) {
                coroutineContext[CoroutineExceptionHandler]?.apply {
                    this.handleException(coroutineContext, any)
                } ?: throw any
            }
        }
    }
}

class RegisteredListener(
    priority: EventPriority,
    context: CoroutineContext,
    executor: ListenerExecutor
) : SimpleListener(
    priority,
    context,
    executor
) {
    internal lateinit var node: ConcurrentLinkedList.LinkNode<ListenerExecutor>
    private val unregistered = AtomicBoolean(false)
    fun unregister() {
        if (!unregistered.compareAndSet(false, true)) return
        node.remove()
    }
}

@JvmOverloads
fun Listener.registerNonCoroutine(context: CoroutineContext = EmptyCoroutineContext): RegisteredListener {
    val p = priority
    val line = EventLines.lines[p]!!
    val host = RegisteredListener(p, context, this)
    host.node = line.insertLast(host)
    return host
}

suspend fun Listener.register(context: CoroutineContext = EmptyCoroutineContext): RegisteredListener {
    val host = registerNonCoroutine(context)
    (context[Job] ?: coroutineContext[Job])?.invokeOnCompletion { host.unregister() }
    return host
}

fun <T : AbsEvent> Class<T>.filtered(executor: suspend (T) -> Unit): ListenerExecutor {
    return object : ListenerExecutor {
        override suspend fun callEvent(event: AbsEvent) {
            if (isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                executor(event as T)
            }
        }
    }
}

suspend inline fun <reified T : AbsEvent> listen(
    priority: EventPriority = EventPriority.MONITOR,
    context: CoroutineContext = EmptyCoroutineContext,
    noinline executor: suspend T.() -> Unit
): RegisteredListener {
    return SimpleListener(priority, context, T::class.java.filtered(executor)).register(context)
}

inline fun <reified T : AbsEvent> listenNonCoroutine(
    priority: EventPriority = EventPriority.MONITOR,
    context: CoroutineContext = EmptyCoroutineContext,
    noinline executor: suspend T.() -> Unit
): RegisteredListener {
    return SimpleListener(priority, context, T::class.java.filtered(executor)).registerNonCoroutine(context)
}


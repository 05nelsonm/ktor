/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.network.selector

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkerSelectorManager : SelectorManager {
    private val selectorContext = newSingleThreadContext("WorkerSelectorManager")
    private val job = Job()
    override val coroutineContext: CoroutineContext = selectorContext + job

    private val selector = SelectorHelper()

    init {
        makeShared()
        selector.start(this)
    }

    override fun notifyClosed(s: Selectable) {
        selector.notifyClosed(s.descriptor)
    }

    override suspend fun select(
        selectable: Selectable,
        interest: SelectInterest
    ) {
        require(selectable is SelectableNative)

        return suspendCancellableCoroutine { continuation ->
            val selectorState = EventInfo(selectable.descriptor, interest, continuation)
            if (!selector.interest(selectorState)) {
                continuation.resumeWithException(CancellationException("Selector closed."))
            }
        }
    }

    override fun close() {
        selector.requestTermination()
        selectorContext.close()
    }
}
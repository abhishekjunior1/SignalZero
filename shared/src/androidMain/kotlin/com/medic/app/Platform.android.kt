package com.medic.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The JVM has a dedicated pool for blocking work; use it. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

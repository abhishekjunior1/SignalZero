package com.medic.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Kotlin/Native has no IO dispatcher. Default is backed by a worker pool and is
 * the correct choice for keeping inference off the main thread on iOS.
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

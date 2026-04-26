package com.safeglow.edge

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * @HiltAndroidApp triggers compile-time Hilt component generation.
 * SingletonComponent lifecycle is bound to Application.onCreate().
 * Phase 1 contains no logic here — all bindings live in Hilt @Module objects.
 */
@HiltAndroidApp
class MainApplication : Application()

package com.ddsk.app.di

import com.ddsk.app.ui.screens.auth.LoginScreenModel
import com.ddsk.app.ui.screens.games.BoomScreenModel
import com.ddsk.app.ui.screens.games.FireballScreenModel
import com.ddsk.app.ui.screens.games.FourWayPlayScreenModel
import com.ddsk.app.ui.screens.games.FrizgilityScreenModel
import com.ddsk.app.ui.screens.games.FunKeyScreenModel
import com.ddsk.app.ui.screens.games.GreedyScreenModel
import com.ddsk.app.ui.screens.games.SevenUpScreenModel
import com.ddsk.app.ui.screens.games.ThrowNGoScreenModel
import com.ddsk.app.ui.screens.games.TimeWarpScreenModel
import org.koin.dsl.module

/**
 * Base app module.
 *
 * Platform-specific modules are expected to provide bindings for platform services like [com.ddsk.app.auth.AuthService].
 */
val appModuleBase = module {
    factory { LoginScreenModel(get()) }

    factory { BoomScreenModel() }
    factory { FireballScreenModel() }
    factory { FourWayPlayScreenModel() }
    factory { FrizgilityScreenModel() }
    factory { FunKeyScreenModel() }
    factory { GreedyScreenModel() }
    factory { SevenUpScreenModel() }
    factory { ThrowNGoScreenModel() }
    factory { TimeWarpScreenModel() }
}

// Backwards-compatible alias for existing Android/Desktop/iOS wiring.
// Those targets should include a platform module that binds AuthService.
val appModule = appModuleBase

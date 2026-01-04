package com.ddsk.app.di

import com.ddsk.app.auth.AuthService
import com.ddsk.app.auth.FirebaseAuthService
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

val appModule = module {
    single<AuthService> { FirebaseAuthService() }
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

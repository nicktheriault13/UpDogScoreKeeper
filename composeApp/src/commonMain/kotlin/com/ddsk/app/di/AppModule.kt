package com.ddsk.app.di

import com.ddsk.app.auth.AuthService
import com.ddsk.app.auth.FirebaseAuthService
import com.ddsk.app.ui.screens.auth.LoginScreenModel
import org.koin.dsl.module

val appModule = module {
    single<AuthService> { FirebaseAuthService() }
    factory { LoginScreenModel(get()) }
}

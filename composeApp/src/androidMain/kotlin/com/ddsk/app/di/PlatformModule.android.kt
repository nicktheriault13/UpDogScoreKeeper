package com.ddsk.app.di

import com.ddsk.app.auth.AuthService
import com.ddsk.app.auth.FirebaseAuthService
import org.koin.dsl.module

val platformModule = module {
    single<AuthService> { FirebaseAuthService() }
}

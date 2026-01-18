package com.ddsk.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.ddsk.app.auth.DemoAuthService
import com.ddsk.app.ui.screens.MainScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class LoginScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { LoginScreenModel(DemoAuthService()) }
        val navigator = LocalNavigator.currentOrThrow

        val email by screenModel.email.collectAsState()
        val password by screenModel.password.collectAsState()
        val loginState by screenModel.loginState.collectAsState()

        LaunchedEffect(loginState) {
            if (loginState is LoginState.Success) {
                // Replace the whole stack so Login can't be re-shown via back navigation
                // (and to avoid potential state issues on Desktop).
                navigator.replaceAll(MainScreen())
            }
        }

        LoginContent(
            email = email,
            password = password,
            loginState = loginState,
            onEmailChange = { screenModel.updateEmail(it) },
            onPasswordChange = { screenModel.updatePassword(it) },
            onSignIn = { screenModel.signIn() }
        )
    }
}

@Composable
private fun LoginContent(
    email: String,
    password: String,
    loginState: LoginState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.h4
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            isError = loginState is LoginState.Error
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            isError = loginState is LoginState.Error
        )

        if (loginState is LoginState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = loginState.message,
                color = MaterialTheme.colors.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (loginState is LoginState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In")
            }
        }
    }
}

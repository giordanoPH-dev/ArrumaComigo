package com.thesmallmarket.arrumacomigo.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thesmallmarket.arrumacomigo.auth.AuthManager
import com.thesmallmarket.arrumacomigo.ui.components.NeoButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoCard
import com.thesmallmarket.arrumacomigo.ui.components.NeoTextField
import kotlinx.coroutines.launch

/** Tela de login obrigatório com e-mail/senha. Sem ViewModel: estado local + AuthManager direto. */
@Composable
fun LoginScreen(authManager: AuthManager) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) {
        if (email.isBlank() || password.length < 6) {
            error = "Informe o e-mail e uma senha de pelo menos 6 caracteres."
            return
        }
        error = null
        loading = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = when {
                    e.message?.contains("confirme o e-mail") == true -> e.message
                    e.message?.contains("invalid_credentials") == true ||
                        e.message?.contains("Invalid login") == true ->
                        "E-mail ou senha incorretos."
                    e.message?.contains("already registered") == true ->
                        "Este e-mail já tem conta. Use Entrar."
                    else -> "Falha ao conectar. Verifique a internet e tente de novo."
                }
            } finally {
                loading = false
            }
        }
    }

    AuthScaffold {
        Text("🧹", style = MaterialTheme.typography.displayLarge)
        Text(
            "Arruma Comigo",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Entre com a sua conta para cuidar da casa em família.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        NeoTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = "E-mail",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        NeoTextField(
            value = password,
            onValueChange = { password = it },
            label = "Senha",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (loading) {
            CircularProgressIndicator()
        } else {
            NeoButton(
                text = "Entrar",
                onClick = { run { authManager.signIn(email, password) } },
            )
            NeoButton(
                text = "Criar conta",
                primary = false,
                onClick = { run { authManager.signUp(email, password) } },
            )
        }
        ErrorText(error)
    }
}

/** Depois do login: entrar numa família com código de convite, ou criar uma nova. */
@Composable
fun HouseholdSetupScreen(authManager: AuthManager) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) {
        error = null
        loading = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "Algo deu errado. Tente de novo."
            } finally {
                loading = false
            }
        }
    }

    AuthScaffold {
        Text(
            "Sua família",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Entre na família de quem te convidou ou crie uma nova.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        NeoTextField(
            value = code,
            onValueChange = { code = it.uppercase() },
            label = "Código de convite",
            modifier = Modifier.fillMaxWidth(),
        )
        if (loading) {
            CircularProgressIndicator()
        } else {
            NeoButton(
                text = "Entrar na família",
                onClick = { if (code.isNotBlank()) run { authManager.joinHousehold(code.trim()) } },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NeoTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nome da nova família",
            modifier = Modifier.fillMaxWidth(),
        )
        if (!loading) {
            NeoButton(
                text = "Criar nova família",
                primary = false,
                onClick = { if (name.isNotBlank()) run { authManager.createHousehold(name.trim()) } },
            )
        }
        ErrorText(error)
        TextButton(onClick = { scope.launch { authManager.signOut() } }) {
            Text("Sair", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AuthScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NeoCard(
            modifier = Modifier.widthIn(max = 420.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(28.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

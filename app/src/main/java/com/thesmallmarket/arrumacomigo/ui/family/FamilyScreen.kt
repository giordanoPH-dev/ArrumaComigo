package com.thesmallmarket.arrumacomigo.ui.family

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thesmallmarket.arrumacomigo.auth.AuthManager
import com.thesmallmarket.arrumacomigo.ui.components.NeoButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoCard
import com.thesmallmarket.arrumacomigo.ui.components.SectionHeader
import kotlinx.coroutines.launch

/** Tela da família: código de convite, membros e saída. Sem ViewModel — padrão das AuthScreens. */
@Composable
fun FamilyScreen(authManager: AuthManager) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var familyName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<AuthManager.Member>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    var memberToRemove by remember { mutableStateOf<AuthManager.Member?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        try {
            val (name, code) = authManager.householdInfo()
            familyName = name
            inviteCode = code
            members = authManager.members()
        } catch (e: Exception) {
            error = "Não foi possível carregar a família. Verifique a internet."
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SectionHeader("Família")
        if (loading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            NeoButton(text = "Tentar de novo", onClick = { reloadKey++ })
        } else {
            NeoCard(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        familyName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Código de convite",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        inviteCode,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeoButton(
                            text = "Copiar",
                            icon = Icons.Rounded.ContentCopy,
                            primary = false,
                            onClick = { clipboard.setText(AnnotatedString(inviteCode)) },
                        )
                        NeoButton(
                            text = "Compartilhar",
                            icon = Icons.Rounded.Share,
                            primary = false,
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Entre na nossa família \"$familyName\" no Arruma Comigo com o código: $inviteCode",
                                    )
                                }
                                context.startActivity(Intent.createChooser(send, "Compartilhar código"))
                            },
                        )
                    }
                }
            }

            NeoCard(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Membros",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    members.forEachIndexed { index, member ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                member.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            if (member.userId == authManager.userId) {
                                Text(
                                    "você",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                TextButton(onClick = { memberToRemove = member }) {
                                    Text("Remover", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            NeoButton(text = "Sair da família", primary = false, onClick = { showLeaveDialog = true })
            TextButton(onClick = { scope.launch { authManager.signOut() } }) {
                Text("Sair da conta", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remover ${member.name}?") },
            text = { Text("${member.name} vai sair da família e perder acesso às tarefas da casa.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = member
                    memberToRemove = null
                    scope.launch {
                        try {
                            authManager.removeMember(target.userId)
                            reloadKey++
                        } catch (e: Exception) {
                            error = "Não foi possível remover. Tente de novo."
                        }
                    }
                }) { Text("Remover", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancelar") }
            },
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Sair da família?") },
            text = { Text("As tarefas da casa somem deste aparelho. Para voltar, é só usar o código de convite de novo.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    scope.launch {
                        try {
                            authManager.leaveHousehold()
                        } catch (e: Exception) {
                            error = "Não foi possível sair. Tente de novo."
                        }
                    }
                }) { Text("Sair", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

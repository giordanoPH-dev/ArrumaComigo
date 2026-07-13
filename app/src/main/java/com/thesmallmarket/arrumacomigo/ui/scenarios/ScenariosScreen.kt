package com.thesmallmarket.arrumacomigo.ui.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thesmallmarket.arrumacomigo.data.entity.Scenario
import com.thesmallmarket.arrumacomigo.data.entity.ScenarioItem
import com.thesmallmarket.arrumacomigo.ui.AppViewModelProvider
import com.thesmallmarket.arrumacomigo.ui.components.NeoButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoCard
import com.thesmallmarket.arrumacomigo.ui.components.NeoCheckbox
import com.thesmallmarket.arrumacomigo.ui.components.NeoIconButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoTextField
import com.thesmallmarket.arrumacomigo.ui.components.SectionHeader
import com.thesmallmarket.arrumacomigo.ui.theme.NeumorphicEdgeInset

@Composable
fun ScenariosScreen(
    twoPane: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ScenariosViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val scenarios by viewModel.scenarios.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    val selected = scenarios.firstOrNull { it.id == selectedId }

    Box(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                ScenarioList(
                    scenarios = scenarios,
                    counts = counts,
                    selectedId = selectedId,
                    onSelect = viewModel::select,
                    onAddScenario = { showCreate = true },
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                )
                Spacer(Modifier.width(20.dp))
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    ScenarioDetailPane(
                        scenario = selected,
                        items = items,
                        onToggle = viewModel::toggle,
                        onAddItem = viewModel::addItem,
                        onDeleteItem = viewModel::deleteItem,
                        onReset = viewModel::reset,
                        onDeleteScenario = viewModel::deleteScenario,
                        showBack = false,
                        onBack = {},
                    )
                }
            }
        } else {
            if (selectedId == null) {
                ScenarioList(
                    scenarios = scenarios,
                    counts = counts,
                    selectedId = null,
                    onSelect = viewModel::select,
                    onAddScenario = { showCreate = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ScenarioDetailPane(
                    scenario = selected,
                    items = items,
                    onToggle = viewModel::toggle,
                    onAddItem = viewModel::addItem,
                    onDeleteItem = viewModel::deleteItem,
                    onReset = viewModel::reset,
                    onDeleteScenario = viewModel::deleteScenario,
                    showBack = true,
                    onBack = { viewModel.select(null) },
                )
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Novo cenário",
            label = "Nome do cenário",
            confirmText = "Criar",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                viewModel.createScenario(name)
                showCreate = false
            },
        )
    }
}

@Composable
private fun ScenarioList(
    scenarios: List<Scenario>,
    counts: Map<Long, Pair<Int, Int>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAddScenario: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHeader(title = "Cenários", modifier = Modifier.padding(top = 28.dp, bottom = 12.dp)) {
            NeoIconButton(Icons.Rounded.Add, onClick = onAddScenario, contentDescription = "Novo cenário")
        }
        if (scenarios.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Crie seu primeiro cenário ✈️\nEx.: \"Pré-viagem\", \"Recebendo visitas\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = NeumorphicEdgeInset),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(scenarios, key = { it.id }) { scenario ->
                    val (done, total) = counts[scenario.id] ?: (0 to 0)
                    NeoCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(scenario.id) },
                        elevation = if (scenario.id == selectedId) 3.dp else 8.dp,
                    ) {
                        Column {
                            Text(
                                scenario.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (total == 0) "Sem itens" else "$done de $total feitos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioDetailPane(
    scenario: Scenario?,
    items: List<ScenarioItem>,
    onToggle: (ScenarioItem) -> Unit,
    onAddItem: (String) -> Unit,
    onDeleteItem: (ScenarioItem) -> Unit,
    onReset: () -> Unit,
    onDeleteScenario: (Scenario) -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    if (scenario == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Selecione um cenário para ver o checklist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    var showAddItem by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                NeoIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack, contentDescription = "Voltar", size = 44.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                scenario.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            NeoIconButton(
                Icons.Rounded.Delete,
                onClick = { showDeleteConfirm = true },
                contentDescription = "Excluir cenário",
                size = 44.dp,
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NeoButton(
                text = "Adicionar item",
                icon = Icons.Rounded.Add,
                onClick = { showAddItem = true },
            )
            NeoButton(
                text = "Resetar",
                icon = Icons.Rounded.Refresh,
                onClick = { showResetConfirm = true },
                primary = false,
            )
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhum item neste cenário ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = NeumorphicEdgeInset),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    NeoCard(modifier = Modifier.fillMaxWidth().animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NeoCheckbox(checked = item.checked, onCheckedChange = { onToggle(item) })
                            Spacer(Modifier.width(14.dp))
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                color = if (item.checked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            NeoIconButton(
                                Icons.Rounded.Delete,
                                onClick = { onDeleteItem(item) },
                                contentDescription = "Excluir item",
                                size = 40.dp,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddItem) {
        NameDialog(
            title = "Novo item",
            label = "O que precisa ser feito?",
            confirmText = "Adicionar",
            onDismiss = { showAddItem = false },
            onConfirm = { title ->
                onAddItem(title)
                showAddItem = false
            },
        )
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Resetar cenário") },
            text = { Text("Desmarcar todos os itens?") },
            confirmButton = {
                TextButton(onClick = { onReset(); showResetConfirm = false }) { Text("Resetar") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancelar") } },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir cenário") },
            text = { Text("Excluir \"${scenario.name}\" e todos os seus itens?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteScenario(scenario)
                        onBack()
                    },
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") } },
        )
    }
}

/** Diálogo simples de um campo de texto (criar cenário / adicionar item). */
@Composable
private fun NameDialog(
    title: String,
    label: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NeoTextField(value = value, onValueChange = { value = it }, label = label, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

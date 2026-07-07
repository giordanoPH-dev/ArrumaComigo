@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.thesmallmarket.arrumacomigo.ui.rooms

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thesmallmarket.arrumacomigo.ui.theme.NeumorphicEdgeInset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.RoomType
import com.thesmallmarket.arrumacomigo.data.template.RoomTemplates
import com.thesmallmarket.arrumacomigo.ui.AppViewModelProvider
import com.thesmallmarket.arrumacomigo.ui.RoomDetailUi
import androidx.compose.runtime.LaunchedEffect
import com.thesmallmarket.arrumacomigo.ui.components.CelebrationBus
import com.thesmallmarket.arrumacomigo.ui.components.NeoButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoCard
import com.thesmallmarket.arrumacomigo.ui.components.NeoIconButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoTextField
import com.thesmallmarket.arrumacomigo.ui.components.SectionHeader
import com.thesmallmarket.arrumacomigo.ui.components.TaskCard

@Composable
fun RoomsScreen(
    twoPane: Boolean,
    onAddTask: (roomId: Long) -> Unit,
    onEditTask: (taskId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoomsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val detail by viewModel.selectedRoom.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    // Comemoração global (confete em tela cheia + vibração + plim) a cada conclusão.
    LaunchedEffect(Unit) {
        viewModel.completedEvents.collect { CelebrationBus.celebrate() }
    }

    Box(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                RoomList(
                    rooms = rooms,
                    selectedId = selectedId,
                    onSelect = viewModel::select,
                    onAddRoom = { showCreate = true },
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                )
                Spacer(Modifier.width(20.dp))
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    RoomDetailPane(
                        detail = detail,
                        onAddTask = onAddTask,
                        onEditTask = onEditTask,
                        onToggle = viewModel::toggle,
                        onDeleteRoom = { viewModel.deleteRoom(it) },
                        showBack = false,
                        onBack = {},
                    )
                }
            }
        } else {
            if (selectedId == null) {
                RoomList(
                    rooms = rooms,
                    selectedId = null,
                    onSelect = viewModel::select,
                    onAddRoom = { showCreate = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                RoomDetailPane(
                    detail = detail,
                    onAddTask = onAddTask,
                    onEditTask = onEditTask,
                    onToggle = viewModel::toggle,
                    onDeleteRoom = { viewModel.deleteRoom(it) },
                    showBack = true,
                    onBack = { viewModel.select(null) },
                )
            }
        }
    }

    if (showCreate) {
        CreateRoomDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, type, titles ->
                viewModel.createRoom(name, type, titles)
                showCreate = false
            },
        )
    }
}

@Composable
private fun RoomList(
    rooms: List<RoomEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAddRoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHeader(title = "Cômodos", modifier = Modifier.padding(top = 28.dp, bottom = 12.dp)) {
            NeoIconButton(Icons.Rounded.Add, onClick = onAddRoom, contentDescription = "Adicionar cômodo")
        }
        if (rooms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Crie seu primeiro cômodo 🏠",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = NeumorphicEdgeInset),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(rooms, key = { it.id }) { room ->
                    NeoCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(room.id) },
                        elevation = if (room.id == selectedId) 3.dp else 8.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(room.type.emoji, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    room.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    room.type.label,
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
}

@Composable
private fun RoomDetailPane(
    detail: RoomDetailUi?,
    onAddTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onToggle: (com.thesmallmarket.arrumacomigo.ui.TaskCardUi) -> Unit,
    onDeleteRoom: (RoomEntity) -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    if (detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Selecione um cômodo para ver as tarefas",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
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
                "${detail.room.type.emoji}  ${detail.room.name}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            NeoIconButton(
                Icons.Rounded.Delete,
                onClick = { onDeleteRoom(detail.room); onBack() },
                contentDescription = "Excluir cômodo",
                size = 44.dp,
                tint = MaterialTheme.colorScheme.error,
            )
        }
        NeoButton(
            text = "Adicionar tarefa",
            icon = Icons.Rounded.Add,
            onClick = { onAddTask(detail.room.id) },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (detail.tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma tarefa neste cômodo ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            var selectedDay by remember(detail.room.id) { mutableStateOf<DayOfWeek?>(null) }
            DayFilter(selected = selectedDay, onSelect = { selectedDay = it })
            // Diárias aparecem em qualquer dia; as demais filtram pelo dia de vencimento.
            val tasks = detail.tasks.filter { item ->
                val day = selectedDay
                day == null ||
                    item.task.recurrence == Recurrence.DAILY ||
                    item.task.nextDueDate.dayOfWeek == day
            }
            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nada agendado para esse dia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = NeumorphicEdgeInset),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(tasks, key = { it.task.id }) { item ->
                        TaskCard(
                            item = item,
                            onToggle = { onToggle(item) },
                            onEdit = { onEditTask(item.task.id) },
                            showRoom = false,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: RoomType, taskTitles: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RoomType.KITCHEN) }
    // Mapa título -> selecionado para as tarefas padrão do tipo atual.
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    // Recarrega as tarefas padrão quando o tipo muda.
    LaunchedSelection(type, selected)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo cômodo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NeoTextField(value = name, onValueChange = { name = it }, label = "Nome do cômodo", modifier = Modifier.fillMaxWidth())
                Text("Tipo", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RoomType.entries.forEach { rt ->
                        FilterChip(
                            selected = rt == type,
                            onClick = { type = rt },
                            label = { Text("${rt.emoji} ${rt.label}") },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
                Text("Tarefas padrão", style = MaterialTheme.typography.labelMedium)
                RoomTemplates.tasksFor(type).forEach { title ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected[title] ?: true,
                            onCheckedChange = { selected[title] = it },
                        )
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalName = name.ifBlank { type.label }
                    val titles = RoomTemplates.tasksFor(type).filter { selected[it] ?: true }
                    onCreate(finalName.trim(), type, titles)
                },
            ) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Filtro de dia da semana para as tarefas do cômodo. */
@Composable
private fun DayFilter(selected: DayOfWeek?, onSelect: (DayOfWeek?) -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Todos os dias") },
        )
        DayOfWeek.entries.forEach { day ->
            val label = day.getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))
                .replaceFirstChar { it.uppercase() }
            FilterChip(
                selected = selected == day,
                onClick = { onSelect(day) },
                label = { Text(label) },
            )
        }
    }
}

/** Sempre que [type] muda, repõe a seleção padrão (todas marcadas). */
@Composable
private fun LaunchedSelection(type: RoomType, selected: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>) {
    androidx.compose.runtime.LaunchedEffect(type) {
        selected.clear()
        RoomTemplates.tasksFor(type).forEach { selected[it] = true }
    }
}

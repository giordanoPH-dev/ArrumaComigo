package com.thesmallmarket.arrumacomigo.ui.today

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.ui.AppViewModelProvider
import com.thesmallmarket.arrumacomigo.ui.TaskCardUi
import com.thesmallmarket.arrumacomigo.ui.components.CelebrationBus
import com.thesmallmarket.arrumacomigo.ui.components.PersonPickerDialog
import com.thesmallmarket.arrumacomigo.ui.components.SectionHeader
import com.thesmallmarket.arrumacomigo.ui.components.TaskCard
import com.thesmallmarket.arrumacomigo.ui.theme.NeumorphicEdgeInset
import kotlinx.coroutines.withTimeoutOrNull
import com.thesmallmarket.arrumacomigo.ui.weekdayShortLabels
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ptBR = Locale("pt", "BR")

private data class TaskGroup(val label: String, val items: List<TaskCardUi>)

@Composable
fun TodayScreen(
    onTaskClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var reassignTarget by remember { mutableStateOf<TaskCardUi?>(null) }

    val isToday = state.selectedDate == state.today
    val isPast = state.selectedDate.isBefore(state.today)

    // Comemoração global (confete + vibração + plim) e snackbar "Desfazer" por 2s.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TodayEvent.TaskCompleted -> {
                    CelebrationBus.celebrate()
                    val result = withTimeoutOrNull(2_000) {
                        snackbarHostState.showSnackbar(
                            message = "\"${event.title}\" concluída 🎉",
                            actionLabel = "Desfazer",
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoComplete(event.taskId)
                }
            }
        }
    }

    // Aplica os filtros (pessoa, cômodo e estado) antes de agrupar.
    val visible = state.all.filter { item ->
        (state.selectedPersonId == null || item.person?.id == state.selectedPersonId) &&
            (state.selectedRoomId == null || item.room?.id == state.selectedRoomId) &&
            when (state.doneFilter) {
                DoneFilter.ALL -> true
                DoneFilter.PENDING -> !item.done
                DoneFilter.DONE -> item.done
            }
    }
    val groups = buildGroups(visible, state.people, state.selectedPersonId)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            DayHeader(
                selectedDate = state.selectedDate,
                today = state.today,
                all = state.all,
                people = state.people,
            )

            if (state.week.isNotEmpty()) {
                WeekTabs(
                    week = state.week,
                    selected = state.selectedDate,
                    today = state.today,
                    onSelect = viewModel::selectDate,
                )
            }

            // --- Filtros: uma linha de pills com dropdown ---
            FiltersRow(
                state = state,
                onDoneFilter = viewModel::setDoneFilter,
                onSelectPerson = viewModel::selectPerson,
                onSelectRoom = viewModel::selectRoom,
            )

            if (visible.isEmpty() && !state.loading) {
                EmptyDay(isPast = isPast, isToday = isToday)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(vertical = NeumorphicEdgeInset, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    groups.forEach { group ->
                        if (group.items.isEmpty()) return@forEach
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(group.label, modifier = Modifier.padding(top = 8.dp))
                        }
                        // Pendentes vêm antes das concluídas (pendingFirst): o índice no grupo
                        // é o índice entre as pendentes. Só pendentes reordenam.
                        val pendingTasks = group.items.filter { !it.done }.map { it.task }
                        itemsIndexed(group.items, key = { _, it -> it.task.id }) { index, item ->
                            val movable = !item.done
                            TaskCard(
                                item = item,
                                onToggle = { viewModel.toggle(item) },
                                onEdit = { onTaskClick(item.task.id) },
                                // Pular/adiar agem sobre a ocorrência corrente — só fazem sentido hoje.
                                onSkip = if (isToday) ({ viewModel.skip(item.task) }) else null,
                                onPostpone = if (isToday) ({ viewModel.postpone(item.task) }) else null,
                                onMoveUp = if (movable && index > 0)
                                    ({ viewModel.moveTask(pendingTasks, index, index - 1) }) else null,
                                onMoveDown = if (movable && index < pendingTasks.lastIndex)
                                    ({ viewModel.moveTask(pendingTasks, index, index + 1) }) else null,
                                onPersonClick = if (!isPast) ({ reassignTarget = item }) else null,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    reassignTarget?.let { target ->
        PersonPickerDialog(
            taskTitle = target.task.title,
            people = state.people,
            selectedPersonId = target.person?.id,
            onSelect = { personId ->
                viewModel.reassign(target.task, personId)
                reassignTarget = null
            },
            onDismiss = { reassignTarget = null },
        )
    }
}

private fun buildGroups(
    all: List<TaskCardUi>,
    people: List<Person>,
    selectedId: Long?,
): List<TaskGroup> {
    // Dentro de cada grupo, as pendentes vêm antes das concluídas.
    fun List<TaskCardUi>.pendingFirst() = sortedBy { it.done }
    if (selectedId != null) {
        val name = people.firstOrNull { it.id == selectedId }?.name ?: "Tarefas"
        return listOf(TaskGroup(name, all.filter { it.person?.id == selectedId }.pendingFirst()))
    }
    val groups = people.map { person ->
        TaskGroup(person.name, all.filter { it.person?.id == person.id }.pendingFirst())
    }.filter { it.items.isNotEmpty() }
    val unassigned = all.filter { it.person == null }.pendingFirst()
    return if (unassigned.isEmpty()) groups else groups + TaskGroup("Sem responsável", unassigned)
}

/** Abas do calendário semanal: seg→dom, com hoje destacado. */
@Composable
private fun WeekTabs(
    week: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        week.forEach { date ->
            val label = if (date == today) {
                "Hoje"
            } else {
                "${weekdayShortLabels[date.dayOfWeek.ordinal]} ${date.dayOfMonth}"
            }
            FilterChip(
                selected = date == selected,
                onClick = { onSelect(date) },
                label = { Text(label) },
            )
        }
    }
}

/** Cabeçalho compacto em uma linha: "Hoje · qua, 1 de jul" + progresso do dia à direita. */
@Composable
private fun DayHeader(
    selectedDate: LocalDate,
    today: LocalDate,
    all: List<TaskCardUi>,
    people: List<Person>,
) {
    val isToday = selectedDate == today
    val isPast = selectedDate.isBefore(today)
    val formatter = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", ptBR)
    val title = if (isToday) {
        "Hoje"
    } else {
        selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, ptBR).replaceFirstChar { it.uppercase() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "· ${selectedDate.format(formatter)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Progresso do dia por pessoa ("🧔 3/5 · 👩 2/4") ou contagem simples.
        val progress = people.mapNotNull { person ->
            val theirs = all.filter { it.person?.id == person.id }
            if (theirs.isEmpty()) null else "${person.emoji} ${theirs.count { it.done }}/${theirs.size}"
        }
        val right = when {
            progress.isNotEmpty() -> progress.joinToString(" · ")
            isPast -> "${all.count { it.done }} feitas"
            all.isEmpty() -> "✨"
            else -> "${all.count { !it.done }} pendentes"
        }
        Text(
            right,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Linha única de filtros: pills que abrem dropdown (estado, pessoa, cômodo). */
@Composable
private fun FiltersRow(
    state: TodayUiState,
    onDoneFilter: (DoneFilter) -> Unit,
    onSelectPerson: (Long?) -> Unit,
    onSelectRoom: (Long?) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val doneLabel = when (state.doneFilter) {
            DoneFilter.ALL -> "Todas"
            DoneFilter.PENDING -> "Pendentes"
            DoneFilter.DONE -> "Concluídas"
        }
        FilterPill(
            label = doneLabel,
            active = state.doneFilter != DoneFilter.ALL,
            options = listOf(
                "Todas" to { onDoneFilter(DoneFilter.ALL) },
                "Pendentes" to { onDoneFilter(DoneFilter.PENDING) },
                "Concluídas" to { onDoneFilter(DoneFilter.DONE) },
            ),
        )
        val person = state.people.firstOrNull { it.id == state.selectedPersonId }
        FilterPill(
            label = person?.let { "${it.emoji} ${it.name}" } ?: "👥 Todos",
            active = person != null,
            options = listOf<Pair<String, () -> Unit>>("👥 Todos" to { onSelectPerson(null) }) +
                state.people.map { p -> "${p.emoji} ${p.name}" to { onSelectPerson(p.id) } },
        )
        val room = state.rooms.firstOrNull { it.id == state.selectedRoomId }
        FilterPill(
            label = room?.let { "${it.type.emoji} ${it.name}" } ?: "🏠 Todos",
            active = room != null,
            options = listOf<Pair<String, () -> Unit>>("🏠 Todos os cômodos" to { onSelectRoom(null) }) +
                state.rooms.map { r -> "${r.type.emoji} ${r.name}" to { onSelectRoom(r.id) } },
        )
    }
}

/** Pill de filtro que abre um menu com as opções; o rótulo mostra a seleção atual. */
@Composable
private fun FilterPill(
    label: String,
    active: Boolean,
    options: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, onSelect) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { expanded = false; onSelect() },
                )
            }
        }
    }
}

@Composable
private fun EmptyDay(isPast: Boolean, isToday: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isPast) "🗓️" else "🧹", style = MaterialTheme.typography.displayLarge)
            Text(
                when {
                    isPast -> "Nada registrado neste dia."
                    isToday -> "Nenhuma tarefa por aqui!"
                    else -> "Nada agendado para este dia 🌿"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (isToday) {
                Text(
                    "Ajuste os filtros ou aproveite o descanso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

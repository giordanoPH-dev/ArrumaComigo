@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.thesmallmarket.arrumacomigo.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thesmallmarket.arrumacomigo.data.RecurrenceCalculator
import com.thesmallmarket.arrumacomigo.data.entity.Priority
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.ui.AppViewModelProvider
import com.thesmallmarket.arrumacomigo.ui.components.NeoButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoCard
import com.thesmallmarket.arrumacomigo.ui.components.NeoIconButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoTextField
import com.thesmallmarket.arrumacomigo.ui.components.TimePickerDialog
import com.thesmallmarket.arrumacomigo.ui.weekdayShortLabels
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBR = Locale("pt", "BR")
private val dateFmt = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ptBR)
private val shortDateFmt = DateTimeFormatter.ofPattern("d 'de' MMM", ptBR)

// Savers para o formulário sobreviver à rotação da tela (rememberSaveable).
private val localDateSaver = Saver<LocalDate, String>(save = { it.toString() }, restore = LocalDate::parse)
private val localTimeSaver = Saver<LocalTime, String>(save = { it.toString() }, restore = LocalTime::parse)
private val nullableLongSaver = Saver<Long?, Long>(save = { it ?: -1L }, restore = { if (it < 0) null else it })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    taskId: Long,
    presetRoomId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()

    val isNew = taskId <= 0L
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var existing by remember { mutableStateOf<Task?>(null) }

    // Estado do formulário — rememberSaveable para não perder nada ao girar o tablet.
    var title by rememberSaveable { mutableStateOf("") }
    var titleError by rememberSaveable { mutableStateOf(false) }
    var roomId by rememberSaveable { mutableStateOf(presetRoomId) }
    var personId by rememberSaveable(stateSaver = nullableLongSaver) { mutableStateOf<Long?>(null) }
    var priority by rememberSaveable { mutableStateOf(Priority.MEDIUM) }
    var minutes by rememberSaveable { mutableStateOf("") }
    var recurrence by rememberSaveable { mutableStateOf(Recurrence.NONE) }
    var interval by rememberSaveable { mutableStateOf("1") }
    var daysOfWeek by rememberSaveable { mutableStateOf(0) }
    var dueDate by rememberSaveable(stateSaver = localDateSaver) { mutableStateOf(LocalDate.now()) }
    var reminderEnabled by rememberSaveable { mutableStateOf(false) }
    var reminderTime by rememberSaveable(stateSaver = localTimeSaver) { mutableStateOf(LocalTime.of(9, 0)) }

    // Carrega a tarefa existente uma única vez (loaded sobrevive à rotação: não sobrescreve edições).
    LaunchedEffect(taskId) {
        if (!isNew) {
            val t = viewModel.getTask(taskId)
            existing = t
            if (t != null && !loaded) {
                title = t.title
                roomId = t.roomId
                personId = t.assignedPersonId
                priority = t.priority
                minutes = t.estimatedMinutes?.toString() ?: ""
                recurrence = t.recurrence
                interval = t.recurrenceInterval.toString()
                daysOfWeek = t.daysOfWeek
                dueDate = t.nextDueDate
                reminderEnabled = t.reminderEnabled
                reminderTime = t.reminderTime ?: LocalTime.of(9, 0)
            }
            loaded = true
        }
    }

    // Garante um cômodo padrão para tarefas novas quando a lista chegar.
    LaunchedEffect(rooms) {
        if (isNew && roomId <= 0L && rooms.isNotEmpty()) roomId = rooms.first().id
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Não mostra o formulário antes de carregar: evita campos vazios sobrescrevendo a digitação.
    if (!loaded) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.padding(top = 28.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeoIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = onDone, contentDescription = "Voltar", size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                if (isNew) "Nova tarefa" else "Editar tarefa",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            existing?.let {
                NeoIconButton(
                    Icons.Rounded.Delete,
                    onClick = { showDeleteConfirm = true },
                    contentDescription = "Excluir",
                    size = 44.dp,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            NeoTextField(
                value = title,
                onValueChange = { title = it; if (it.isNotBlank()) titleError = false },
                label = "O que precisa ser feito?",
                modifier = Modifier.fillMaxWidth(),
                isError = titleError,
                errorMessage = if (titleError) "Dê um nome para a tarefa" else null,
            )

            FieldLabel("Cômodo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rooms.forEach { room ->
                    FilterChip(
                        selected = room.id == roomId,
                        onClick = { roomId = room.id },
                        label = { Text("${room.type.emoji} ${room.name}") },
                    )
                }
            }

            FieldLabel("Responsável")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = personId == null, onClick = { personId = null }, label = { Text("Ninguém") })
                people.forEach { person ->
                    FilterChip(
                        selected = personId == person.id,
                        onClick = { personId = person.id },
                        label = { Text("${person.emoji} ${person.name}") },
                    )
                }
            }

            FieldLabel("Prioridade")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { p ->
                    FilterChip(selected = p == priority, onClick = { priority = p }, label = { Text(p.label) })
                }
            }

            FieldLabel("Primeira data")
            NeoButton(text = dueDate.format(dateFmt), onClick = { showDatePicker = true }, primary = false)

            FieldLabel("Repetição")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Recurrence.entries.forEach { r ->
                    FilterChip(
                        selected = r == recurrence,
                        onClick = {
                            recurrence = r
                            // Semanal sem dia marcado confunde: pré-marca o dia da primeira data.
                            if (r == Recurrence.WEEKLY && daysOfWeek == 0) {
                                daysOfWeek = RecurrenceCalculator.toggleDay(0, dueDate.dayOfWeek)
                            }
                        },
                        label = { Text(r.label) },
                    )
                }
            }
            if (recurrence == Recurrence.WEEKLY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEachIndexed { index, day ->
                        FilterChip(
                            selected = RecurrenceCalculator.isDaySelected(day, daysOfWeek),
                            onClick = { daysOfWeek = RecurrenceCalculator.toggleDay(daysOfWeek, day) },
                            label = { Text(weekdayShortLabels[index]) },
                        )
                    }
                }
            }
            if (recurrence != Recurrence.NONE) {
                NeoTextField(
                    value = interval,
                    onValueChange = { interval = it.filter(Char::isDigit) },
                    label = "A cada quantos períodos",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                // Resumo legível + prévia das próximas datas, para conferir antes de salvar.
                RecurrenceSummary(
                    recurrence = recurrence,
                    interval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    daysOfWeek = effectiveDaysOfWeek(recurrence, daysOfWeek, dueDate),
                    dueDate = dueDate,
                )
            }

            NeoTextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit) },
                label = "Tempo estimado (min) — opcional",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            NeoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Lembrete",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                    }
                    if (reminderEnabled) {
                        NeoButton(
                            text = "Avisar às ${reminderTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                            onClick = { showTimePicker = true },
                            primary = false,
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))
            NeoButton(
                text = "Salvar tarefa",
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                    } else if (roomId > 0L) {
                        val effDays = effectiveDaysOfWeek(recurrence, daysOfWeek, dueDate)
                        // Semanal criada num dia não marcado nasce na próxima ocorrência, não "atrasada".
                        val firstDue = if (recurrence == Recurrence.WEEKLY) {
                            RecurrenceCalculator.firstWeeklyOccurrence(dueDate, effDays)
                        } else dueDate
                        val task = (existing ?: Task(roomId = roomId, title = "", nextDueDate = firstDue)).copy(
                            roomId = roomId,
                            title = title.trim(),
                            assignedPersonId = personId,
                            priority = priority,
                            estimatedMinutes = minutes.toIntOrNull(),
                            recurrence = recurrence,
                            recurrenceInterval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            daysOfWeek = effDays,
                            nextDueDate = firstDue,
                            reminderEnabled = reminderEnabled,
                            reminderTime = if (reminderEnabled) reminderTime else null,
                        )
                        viewModel.save(task) { onDone() }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Spacer(Modifier.width(24.dp))
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        dueDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Ok") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = dpState) }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = reminderTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { reminderTime = it; showTimePicker = false },
        )
    }

    if (showDeleteConfirm) {
        val target = existing
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir tarefa?") },
            text = { Text("\"${target?.title ?: title}\" e o histórico dela serão apagados. Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    target?.let { viewModel.delete(it) { onDone() } }
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") } },
        )
    }
}

/** Dias efetivos da repetição semanal: sem dia marcado, usa o dia da primeira data. */
private fun effectiveDaysOfWeek(recurrence: Recurrence, daysOfWeek: Int, dueDate: LocalDate): Int = when {
    recurrence != Recurrence.WEEKLY -> 0
    daysOfWeek == 0 -> RecurrenceCalculator.toggleDay(0, dueDate.dayOfWeek)
    else -> daysOfWeek
}

/** Texto legível da recorrência + prévia das próximas 3 datas. */
@Composable
private fun RecurrenceSummary(
    recurrence: Recurrence,
    interval: Int,
    daysOfWeek: Int,
    dueDate: LocalDate,
) {
    val description = when (recurrence) {
        Recurrence.NONE -> return
        Recurrence.DAILY -> if (interval == 1) "Repete todo dia" else "Repete a cada $interval dias"
        Recurrence.MONTHLY -> if (interval == 1) "Repete todo mês" else "Repete a cada $interval meses"
        Recurrence.WEEKLY -> {
            val dayNames = DayOfWeek.entries
                .filter { RecurrenceCalculator.isDaySelected(it, daysOfWeek) }
                .joinToString(", ") { weekdayShortLabels[it.ordinal] }
            if (interval == 1) "Repete toda semana: $dayNames" else "Repete a cada $interval semanas: $dayNames"
        }
    }
    // A primeira ocorrência pode ser ajustada (semanal criada num dia não marcado).
    val first = if (recurrence == Recurrence.WEEKLY) {
        RecurrenceCalculator.firstWeeklyOccurrence(dueDate, daysOfWeek)
    } else dueDate
    val preview = generateSequence(first) {
        RecurrenceCalculator.next(it, recurrence, interval, daysOfWeek)
    }.drop(1).take(3).toList()
    val firstLine = if (first != dueDate) "\nPrimeira: ${first.format(shortDateFmt)}" else ""
    Text(
        "$description$firstLine\nPróximas: ${preview.joinToString(" • ") { it.format(shortDateFmt) }}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

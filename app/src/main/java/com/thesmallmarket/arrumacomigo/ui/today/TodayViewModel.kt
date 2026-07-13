package com.thesmallmarket.arrumacomigo.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thesmallmarket.arrumacomigo.data.RecurrenceCalculator
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import com.thesmallmarket.arrumacomigo.notification.ReminderScheduler
import com.thesmallmarket.arrumacomigo.ui.TaskCardUi
import com.thesmallmarket.arrumacomigo.ui.currentDateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** Filtro de estado das tarefas na tela Hoje. */
enum class DoneFilter { ALL, PENDING, DONE }

data class TodayUiState(
    /** Os 7 dias da semana atual (seg→dom). */
    val week: List<LocalDate> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** Aba selecionada no calendário semanal. */
    val selectedDate: LocalDate = LocalDate.now(),
    /** Cartões do dia selecionado (pendentes projetadas + feitas no dia). */
    val all: List<TaskCardUi> = emptyList(),
    val people: List<Person> = emptyList(),
    val rooms: List<RoomEntity> = emptyList(),
    val selectedPersonId: Long? = null,
    val selectedRoomId: Long? = null,
    val doneFilter: DoneFilter = DoneFilter.ALL,
    val loading: Boolean = true,
)

/** Eventos pontuais da tela Hoje (ex.: snackbar de desfazer). */
sealed interface TodayEvent {
    data class TaskCompleted(val taskId: Long, val title: String) : TodayEvent
}

private data class Filters(
    val personId: Long?,
    val roomId: Long?,
    val done: DoneFilter,
    val date: LocalDate?,
)

class TodayViewModel(
    private val repository: HouseholdRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val selectedPersonId = MutableStateFlow<Long?>(null)
    private val selectedRoomId = MutableStateFlow<Long?>(null)
    private val doneFilter = MutableStateFlow(DoneFilter.ALL)

    /** Aba escolhida pelo usuário; null = acompanhar o dia de hoje. */
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val _events = MutableSharedFlow<TodayEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TodayEvent> = _events

    /** Tarefas com operação de conclusão em andamento, para ignorar toques duplos. */
    private val inFlight = mutableSetOf<Long>()

    private val filters = combine(selectedPersonId, selectedRoomId, doneFilter, selectedDate) { person, room, done, date ->
        Filters(person, room, done, date)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = currentDateFlow().flatMapLatest { today ->
        val monday = today.with(DayOfWeek.MONDAY)
        val week = (0L..6L).map { monday.plusDays(it) }
        combine(
            repository.allTasks(),
            repository.completionsBetween(monday.atStartOfDay(), monday.plusDays(7).atStartOfDay()),
            repository.rooms(),
            repository.people(),
            filters,
        ) { tasks, weekCompletions, rooms, people, filter ->
            // A aba selecionada precisa estar na semana visível; senão volta para hoje.
            val date = filter.date?.takeIf { it in week } ?: today
            val roomById = rooms.associateBy { it.id }
            val personById = people.associateBy { it.id }
            val taskById = tasks.associateBy { it.id }

            // Feitas no dia: conclusões cujo completedAt cai na aba selecionada.
            val completionsOnDay = weekCompletions.filter { it.completedAt.toLocalDate() == date }
            val doneByTask = completionsOnDay.associateBy { it.taskId }

            fun card(task: Task) = TaskCardUi(
                task = task,
                room = roomById[task.roomId],
                person = task.assignedPersonId?.let { personById[it] },
                done = doneByTask.containsKey(task.id),
                completion = doneByTask[task.id],
                // Em abas futuras a ocorrência exibida é a do próprio dia da aba;
                // em hoje mantém nextDueDate (preserva o rótulo "Atrasada").
                dueDate = if (date == today) task.nextDueDate else date,
            )

            val pending = tasks.filter { task ->
                !task.isArchived && !doneByTask.containsKey(task.id) && RecurrenceCalculator.occursOn(
                    nextDueDate = task.nextDueDate,
                    recurrence = task.recurrence,
                    interval = task.recurrenceInterval,
                    daysOfWeek = task.daysOfWeek,
                    date = date,
                    today = today,
                )
            }.map(::card)
            val done = completionsOnDay.mapNotNull { completion -> taskById[completion.taskId]?.let(::card) }

            TodayUiState(
                week = week,
                today = today,
                selectedDate = date,
                all = pending + done,
                people = people,
                rooms = rooms,
                selectedPersonId = filter.personId,
                selectedRoomId = filter.roomId,
                doneFilter = filter.done,
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun selectPerson(id: Long?) {
        selectedPersonId.value = id
    }

    fun selectRoom(id: Long?) {
        selectedRoomId.value = id
    }

    fun setDoneFilter(filter: DoneFilter) {
        doneFilter.value = filter
    }

    /** Alterna entre concluída e não concluída. Toques repetidos durante a gravação são ignorados. */
    fun toggle(item: TaskCardUi) {
        if (!inFlight.add(item.task.id)) return
        viewModelScope.launch {
            try {
                if (item.done && item.completion != null) {
                    val reverted = repository.uncompleteTask(item.task, item.completion)
                    scheduler.schedule(reverted)
                } else {
                    // Conclui no dia da aba selecionada (não em "hoje"): quem marca na sexta, fez na sexta.
                    val completedAt = uiState.value.selectedDate.atTime(java.time.LocalTime.now())
                    val updated = repository.completeTask(item.task, completedAt)
                    if (updated != null) scheduler.schedule(updated) else scheduler.cancel(item.task.id)
                    _events.tryEmit(TodayEvent.TaskCompleted(item.task.id, item.task.title))
                }
            } finally {
                inFlight.remove(item.task.id)
            }
        }
    }

    /** Desfaz a conclusão mais recente da tarefa (ação do snackbar). */
    fun undoComplete(taskId: Long) {
        if (!inFlight.add(taskId)) return
        viewModelScope.launch {
            try {
                val task = repository.taskOnce(taskId) ?: return@launch
                val completion = repository.latestCompletionFor(taskId) ?: return@launch
                val reverted = repository.uncompleteTask(task, completion)
                scheduler.schedule(reverted)
            } finally {
                inFlight.remove(taskId)
            }
        }
    }

    /** Ignora a ocorrência atual (avança para o próximo dia agendado, sem registrar conclusão). */
    fun skip(task: Task) {
        viewModelScope.launch {
            val updated = repository.skipTask(task)
            if (updated != null) scheduler.schedule(updated) else scheduler.cancel(task.id)
        }
    }

    /** Adia a ocorrência atual para amanhã, sem registrar conclusão. */
    fun postpone(task: Task) {
        viewModelScope.launch {
            val updated = repository.postponeTask(task)
            scheduler.schedule(updated)
        }
    }

    /** Move a tarefa dentro da lista visível de pendentes (renumera as posições). */
    fun moveTask(tasks: List<Task>, from: Int, to: Int) {
        viewModelScope.launch { repository.moveTask(tasks, from, to) }
    }

    /** Troca (ou remove) o responsável pela tarefa — troca pontual feita no cartão. */
    fun reassign(task: Task, personId: Long?) {
        viewModelScope.launch {
            repository.upsertTask(task.copy(assignedPersonId = personId))
        }
    }
}

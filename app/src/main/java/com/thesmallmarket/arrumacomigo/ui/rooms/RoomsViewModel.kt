package com.thesmallmarket.arrumacomigo.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.RoomType
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import com.thesmallmarket.arrumacomigo.notification.ReminderScheduler
import com.thesmallmarket.arrumacomigo.ui.RoomDetailUi
import com.thesmallmarket.arrumacomigo.ui.TaskCardUi
import com.thesmallmarket.arrumacomigo.ui.currentDateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RoomsViewModel(
    private val repository: HouseholdRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val rooms: StateFlow<List<RoomEntity>> =
        repository.rooms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val people: StateFlow<List<Person>> =
        repository.people().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedRoomId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = selectedRoomId

    val selectedRoom: StateFlow<RoomDetailUi?> = selectedRoomId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else roomDetailFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Conclusões de hoje: para o cartão aparecer riscado após concluir (como na tela Hoje).
    private fun roomDetailFlow(id: Long): Flow<RoomDetailUi?> = currentDateFlow().flatMapLatest { today ->
        combine(
            repository.room(id),
            repository.tasksByRoom(id),
            repository.people(),
            repository.completionsSince(today.atStartOfDay()),
        ) { room, tasks, people, completions ->
            val personById = people.associateBy { it.id }
            val doneByTask = completions.associateBy { it.taskId }
            room?.let {
                RoomDetailUi(
                    room = it,
                    tasks = tasks.map { task ->
                        TaskCardUi(
                            task = task,
                            room = it,
                            person = task.assignedPersonId?.let { pid -> personById[pid] },
                            done = doneByTask.containsKey(task.id),
                            completion = doneByTask[task.id],
                        )
                    },
                )
            }
        }
    }

    fun select(id: Long?) {
        selectedRoomId.value = id
    }

    fun createRoom(name: String, type: RoomType, taskTitles: List<String>) {
        viewModelScope.launch {
            val id = repository.createRoomWithTasks(name, type, taskTitles, LocalDate.now())
            selectedRoomId.value = id
        }
    }

    fun renameRoom(room: RoomEntity, name: String, type: RoomType) {
        viewModelScope.launch { repository.upsertRoom(room.copy(name = name, type = type)) }
    }

    fun deleteRoom(room: RoomEntity) {
        viewModelScope.launch {
            repository.deleteRoom(room)
            if (selectedRoomId.value == room.id) selectedRoomId.value = null
        }
    }

    /** Tarefas com operação de conclusão em andamento, para ignorar toques duplos. */
    private val inFlight = mutableSetOf<Long>()

    /** Alterna entre concluída e não concluída (mesmo comportamento da tela Hoje). */
    fun toggle(item: TaskCardUi) {
        if (!inFlight.add(item.task.id)) return
        viewModelScope.launch {
            try {
                if (item.done && item.completion != null) {
                    val reverted = repository.uncompleteTask(item.task, item.completion)
                    scheduler.schedule(reverted)
                } else {
                    val updated = repository.completeTask(item.task)
                    if (updated != null) scheduler.schedule(updated) else scheduler.cancel(item.task.id)
                }
            } finally {
                inFlight.remove(item.task.id)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            scheduler.cancel(task.id)
        }
    }
}

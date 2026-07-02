package com.thesmallmarket.arrumacomigo.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import com.thesmallmarket.arrumacomigo.notification.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskEditViewModel(
    private val repository: HouseholdRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val rooms: StateFlow<List<RoomEntity>> =
        repository.rooms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val people: StateFlow<List<Person>> =
        repository.people().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun getTask(id: Long): Task? = repository.taskOnce(id)

    fun save(task: Task, onSaved: () -> Unit) {
        viewModelScope.launch {
            val id = repository.upsertTask(task)
            scheduler.schedule(task.copy(id = id))
            onSaved()
        }
    }

    fun delete(task: Task, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteTask(task)
            scheduler.cancel(task.id)
            onDeleted()
        }
    }
}

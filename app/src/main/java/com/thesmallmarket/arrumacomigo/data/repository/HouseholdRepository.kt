package com.thesmallmarket.arrumacomigo.data.repository

import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.RoomType
import com.thesmallmarket.arrumacomigo.data.entity.Scenario
import com.thesmallmarket.arrumacomigo.data.entity.ScenarioItem
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/** Fonte única de dados do app (offline / Room). */
interface HouseholdRepository {
    // Pessoas
    fun people(): Flow<List<Person>>
    fun person(id: Long): Flow<Person?>
    suspend fun peopleCount(): Int
    suspend fun upsertPerson(person: Person): Long
    suspend fun deletePerson(person: Person)

    // Cômodos
    fun rooms(): Flow<List<RoomEntity>>
    fun room(id: Long): Flow<RoomEntity?>
    suspend fun roomsCount(): Int
    suspend fun upsertRoom(room: RoomEntity): Long
    suspend fun deleteRoom(room: RoomEntity)
    suspend fun createRoomWithTasks(
        name: String,
        type: RoomType,
        taskTitles: List<String>,
        firstDueDate: LocalDate,
    ): Long

    // Tarefas
    fun activeTasks(): Flow<List<Task>>

    /** Todas as tarefas, incluindo arquivadas — para mapear conclusões de não-recorrentes. */
    fun allTasks(): Flow<List<Task>>
    fun tasksByRoom(roomId: Long): Flow<List<Task>>
    fun tasksDueOnOrBefore(date: LocalDate): Flow<List<Task>>

    /** Agenda de [date]: tarefas pendentes do dia + as já concluídas hoje (para permanecerem visíveis). */
    fun todayView(date: LocalDate): Flow<List<Task>>
    fun task(id: Long): Flow<Task?>
    suspend fun taskOnce(id: Long): Task?
    suspend fun upsertTask(task: Task): Long
    suspend fun deleteTask(task: Task)
    suspend fun tasksWithReminders(): List<Task>

    /**
     * Marca a tarefa como concluída: grava no histórico e, se recorrente,
     * avança [Task.nextDueDate]; senão arquiva. Retorna a tarefa atualizada ou null se arquivada.
     */
    suspend fun completeTask(task: Task, completedAt: LocalDateTime = LocalDateTime.now()): Task?

    /**
     * Desfaz a conclusão de hoje: remove o registro do histórico e devolve a tarefa ao estado
     * pendente (restaura [Task.nextDueDate] e desarquiva). Retorna a tarefa revertida.
     */
    suspend fun uncompleteTask(task: Task, completion: TaskCompletion): Task

    /**
     * Ignora a ocorrência atual sem registrar conclusão: avança [Task.nextDueDate] para o próximo
     * dia agendado; se não recorrente, arquiva. Retorna a tarefa atualizada ou null se arquivada.
     */
    suspend fun skipTask(task: Task): Task?

    /**
     * Adia a ocorrência atual para amanhã, sem registrar conclusão nem pular o padrão
     * de recorrência. Retorna a tarefa atualizada.
     */
    suspend fun postponeTask(task: Task): Task

    /**
     * Move a tarefa de [from] para [to] dentro da lista visível [tasks], renumerando
     * `position` pelo índice. Só persiste as que mudaram de posição.
     */
    suspend fun moveTask(tasks: List<Task>, from: Int, to: Int)

    // Histórico
    fun completions(): Flow<List<TaskCompletion>>
    fun completionsSince(since: LocalDateTime): Flow<List<TaskCompletion>>

    /** Conclusões com `completedAt` em [start, end) — ex.: a semana visível no calendário. */
    fun completionsBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<TaskCompletion>>

    /** Conclusão mais recente de uma tarefa, para desfazer via snackbar. */
    suspend fun latestCompletionFor(taskId: Long): TaskCompletion?

    // Cenários (checklists avulsos)
    fun scenarios(): Flow<List<Scenario>>
    fun scenarioItems(scenarioId: Long): Flow<List<ScenarioItem>>

    /** Todos os itens de todos os cenários — para o "X de Y feitos" da lista. */
    fun allScenarioItems(): Flow<List<ScenarioItem>>
    suspend fun upsertScenario(scenario: Scenario): Long
    suspend fun upsertScenarioItem(item: ScenarioItem): Long
    suspend fun deleteScenario(scenario: Scenario)
    suspend fun deleteScenarioItem(item: ScenarioItem)

    /** Move o item de [from] para [to] dentro de [items], renumerando `position` pelo índice. */
    suspend fun moveScenarioItem(items: List<ScenarioItem>, from: Int, to: Int)

    /** Desmarca todos os itens do cenário. */
    suspend fun resetScenario(scenarioId: Long)
}

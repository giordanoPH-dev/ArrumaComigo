package com.thesmallmarket.arrumacomigo.ui

import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion

/** Tarefa enriquecida com o cômodo e a pessoa responsável, para exibição. */
data class TaskCardUi(
    val task: Task,
    val room: RoomEntity?,
    val person: Person?,
    /** Se já foi concluída hoje (marcada na tela Hoje). */
    val done: Boolean = false,
    /** A conclusão de hoje, quando [done], para permitir desfazer. */
    val completion: TaskCompletion? = null,
)

/** Detalhe de um cômodo com suas tarefas. */
data class RoomDetailUi(
    val room: RoomEntity,
    val tasks: List<TaskCardUi>,
)

/** Contagem de tarefas concluídas por pessoa, para o balanço. */
data class PersonBalance(
    val person: Person?,
    val count: Int,
)

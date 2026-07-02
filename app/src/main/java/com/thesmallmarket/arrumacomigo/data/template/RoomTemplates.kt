package com.thesmallmarket.arrumacomigo.data.template

import com.thesmallmarket.arrumacomigo.data.entity.RoomType

/**
 * Tarefas padrão sugeridas para cada tipo de cômodo.
 * Ao criar um cômodo, o usuário pode escolher quais dessas tarefas incluir.
 */
object RoomTemplates {

    private val defaults: Map<RoomType, List<String>> = mapOf(
        RoomType.KITCHEN to listOf(
            "Lavar louça", "Tirar o lixo", "Limpar o fogão",
            "Limpar a bancada", "Varrer o chão", "Limpar a geladeira",
        ),
        RoomType.BATHROOM to listOf(
            "Limpar o vaso", "Limpar a pia", "Limpar o espelho",
            "Trocar as toalhas", "Varrer e passar pano", "Repor papel higiênico",
        ),
        RoomType.BEDROOM to listOf(
            "Arrumar a cama", "Aspirar / varrer", "Trocar os lençóis",
            "Tirar o pó", "Organizar o guarda-roupa",
        ),
        RoomType.LIVING_ROOM to listOf(
            "Aspirar / varrer", "Tirar o pó", "Organizar",
            "Limpar a TV", "Arrumar as almofadas",
        ),
        RoomType.LAUNDRY to listOf(
            "Lavar roupa", "Estender / secar", "Dobrar roupa",
            "Passar roupa", "Guardar roupa",
        ),
        RoomType.KIDS_ROOM to listOf(
            "Arrumar a cama", "Guardar os brinquedos", "Aspirar / varrer",
            "Trocar os lençóis", "Organizar",
        ),
        RoomType.OFFICE to listOf(
            "Organizar a mesa", "Tirar o pó", "Aspirar / varrer",
            "Esvaziar a lixeira",
        ),
        RoomType.OUTDOOR to listOf(
            "Varrer", "Regar as plantas", "Tirar o lixo",
            "Limpar a área", "Cuidar do jardim",
        ),
        RoomType.GARAGE to listOf(
            "Varrer", "Organizar", "Tirar o lixo",
        ),
        RoomType.OTHER to listOf(
            "Limpar", "Organizar",
        ),
    )

    fun tasksFor(type: RoomType): List<String> = defaults[type] ?: emptyList()
}

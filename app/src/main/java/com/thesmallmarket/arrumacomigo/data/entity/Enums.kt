package com.thesmallmarket.arrumacomigo.data.entity

/** Tipos de cômodo, cada um com nome em pt-BR e um emoji para o visual skeuomórfico. */
enum class RoomType(val label: String, val emoji: String) {
    KITCHEN("Cozinha", "🍳"),
    BATHROOM("Banheiro", "🛁"),
    BEDROOM("Quarto", "🛏️"),
    LIVING_ROOM("Sala", "🛋️"),
    LAUNDRY("Lavanderia", "🧺"),
    KIDS_ROOM("Quarto das crianças", "🧸"),
    OFFICE("Escritório", "💻"),
    OUTDOOR("Área externa", "🪴"),
    GARAGE("Garagem", "🚗"),
    OTHER("Outro", "🏠");

    companion object {
        fun fromName(value: String): RoomType =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/** Frequência de repetição de uma tarefa. */
enum class Recurrence(val label: String) {
    NONE("Uma vez"),
    DAILY("Diária"),
    WEEKLY("Semanal"),
    MONTHLY("Mensal");

    companion object {
        fun fromName(value: String): Recurrence =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

/** Prioridade da tarefa. */
enum class Priority(val label: String) {
    LOW("Baixa"),
    MEDIUM("Média"),
    HIGH("Alta");

    companion object {
        fun fromName(value: String): Priority =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

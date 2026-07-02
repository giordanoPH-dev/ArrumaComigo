package com.thesmallmarket.arrumacomigo.data.seed

import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.Priority
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.RoomType
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Popula o app, na primeira abertura, com a casa do Giordano e da Amanda seguindo a
 * "Rotina da Casa": um cômodo por dia para cada um, de domingo a sexta (sábado livre).
 * Só roda com o banco vazio.
 *
 * Cadências (o dia da semana vem do weekday de [Task.nextDueDate]):
 * DAILY (todo dia), WEEKLY (toda semana), WEEKLY+interval 2 (quinzenal),
 * MONTHLY (mensal), MONTHLY+interval 3 (trimestral).
 */
object HouseSeeder {

    suspend fun seedIfEmpty(repo: HouseholdRepository) {
        if (repo.peopleCount() > 0 || repo.roomsCount() > 0) return
        val today = LocalDate.now()

        // --- Pessoas ---
        val giordano = repo.upsertPerson(Person(name = "Giordano", colorHex = "#6C4DDB", emoji = "🧔"))
        val amanda = repo.upsertPerson(Person(name = "Amanda", colorHex = "#E5739D", emoji = "👩"))

        // --- Cômodos ---
        suspend fun room(name: String, type: RoomType) = repo.upsertRoom(RoomEntity(name = name, type = type))

        val cozinha = room("Cozinha", RoomType.KITCHEN)
        val quartoCasal = room("Quarto do Casal", RoomType.BEDROOM)
        val salaEstar = room("Sala de Estar", RoomType.LIVING_ROOM)
        val salaJantar = room("Sala de Jantar", RoomType.LIVING_ROOM)
        val banheiroCasal = room("Banheiro do Casal", RoomType.BATHROOM)
        val lavabo = room("Lavabo", RoomType.BATHROOM)
        val escritorio = room("Escritório", RoomType.OFFICE)
        val quartoGuto = room("Quarto do Guto", RoomType.KIDS_ROOM)
        val lavanderia = room("Lavanderia", RoomType.LAUNDRY)
        val banheiroVisitas = room("Banheiro de Visitas", RoomType.BATHROOM)
        val quartoVisitas = room("Quarto de Visitas", RoomType.BEDROOM)
        val fundos = room("Fundos (cães)", RoomType.OUTDOOR)
        val casaToda = room("Geral — Casa toda", RoomType.OTHER)

        // --- Helper para adicionar tarefas ---
        suspend fun add(
            room: Long,
            title: String,
            person: Long?,
            recurrence: Recurrence,
            day: DayOfWeek? = null,
            interval: Int = 1,
            priority: Priority = Priority.MEDIUM,
        ) {
            val due = when {
                recurrence == Recurrence.DAILY -> today
                day != null -> nextOnOrAfter(today, day)
                else -> today
            }
            repo.upsertTask(
                Task(
                    roomId = room,
                    title = title,
                    assignedPersonId = person,
                    recurrence = recurrence,
                    recurrenceInterval = interval,
                    nextDueDate = due,
                    priority = priority,
                )
            )
        }

        val daily = Recurrence.DAILY
        val weekly = Recurrence.WEEKLY
        val monthly = Recurrence.MONTHLY

        // --- DIÁRIAS (todos os dias) ---
        add(fundos, "Dar comida aos cães (manhã e noite)", giordano, daily, priority = Priority.HIGH)
        add(fundos, "Recolher cocô e limpar xixi (fundos)", giordano, daily, priority = Priority.HIGH)
        add(cozinha, "Tirar o lixo orgânico quando cheio", giordano, daily)
        add(quartoCasal, "Arrumar a cama", amanda, daily, priority = Priority.LOW)
        add(quartoCasal, "Organizar o quarto do casal", amanda, daily, priority = Priority.LOW)

        // --- DOMINGO · Giordano: Cozinha · Amanda: Quarto do Casal ---
        add(cozinha, "Limpar as bancadas", giordano, weekly, SUNDAY)
        add(cozinha, "Limpar o fogão", giordano, weekly, SUNDAY)
        add(cozinha, "Limpar os eletros", giordano, weekly, SUNDAY)
        add(cozinha, "Organizar a geladeira", giordano, weekly, SUNDAY)
        add(cozinha, "Limpar a lata de lixo", giordano, weekly, SUNDAY, interval = 2)
        add(cozinha, "Limpar o congelador", giordano, monthly, SUNDAY)
        add(cozinha, "Limpar o forno", giordano, monthly, SUNDAY)
        add(quartoCasal, "Tirar o pó", amanda, weekly, SUNDAY)
        add(quartoCasal, "Trocar a roupa de cama", amanda, weekly, SUNDAY, interval = 2)
        add(quartoCasal, "Limpar a janela", amanda, weekly, SUNDAY, interval = 2)
        add(quartoCasal, "Organizar o closet", amanda, monthly, SUNDAY)
        add(quartoCasal, "Limpar e organizar as gavetas", amanda, monthly, SUNDAY)

        // --- SEGUNDA · Giordano: Sala de Estar · Amanda: Sala de Jantar ---
        add(salaEstar, "Tirar o pó", giordano, weekly, MONDAY)
        add(salaEstar, "Aspirar o sofá", giordano, weekly, MONDAY)
        add(salaEstar, "Limpar o painel", giordano, weekly, MONDAY, interval = 2)
        add(salaJantar, "Arrumar e organizar", amanda, weekly, MONDAY)
        add(salaJantar, "Passar pano na mesa", amanda, weekly, MONDAY)
        add(salaJantar, "Tirar o pó dos quadros", amanda, weekly, MONDAY, interval = 2)

        // --- TERÇA · Giordano: Banheiro do Casal · Amanda: Lavabo ---
        add(banheiroCasal, "Limpar a pia e os espelhos", giordano, weekly, TUESDAY)
        add(banheiroCasal, "Limpar o vaso sanitário", giordano, weekly, TUESDAY)
        add(banheiroCasal, "Limpar o chão", giordano, weekly, TUESDAY)
        add(banheiroCasal, "Trocar as toalhas", giordano, weekly, TUESDAY, interval = 2)
        add(banheiroCasal, "Limpar o box e os vidros", giordano, weekly, TUESDAY, interval = 2)
        add(lavabo, "Limpar a pia e os espelhos", amanda, weekly, TUESDAY)
        add(lavabo, "Limpar o vaso sanitário", amanda, weekly, TUESDAY)
        add(lavabo, "Limpar o chão", amanda, weekly, TUESDAY)
        add(lavabo, "Trocar a escova de dentes", amanda, monthly, TUESDAY)

        // --- QUARTA · Giordano: Escritório · Amanda: Quarto do Guto ---
        add(escritorio, "Tirar o pó e limpar os objetos", giordano, weekly, WEDNESDAY)
        add(escritorio, "Passar o aspirador", giordano, weekly, WEDNESDAY)
        add(escritorio, "Limpar a janela", giordano, weekly, WEDNESDAY, interval = 2)
        add(quartoGuto, "Arrumar e organizar", amanda, weekly, WEDNESDAY)
        add(quartoGuto, "Tirar o pó", amanda, weekly, WEDNESDAY)
        add(quartoGuto, "Trocar a roupa de cama", amanda, weekly, WEDNESDAY, interval = 2)
        add(quartoGuto, "Organizar e limpar os armários", amanda, monthly, WEDNESDAY)

        // --- QUINTA · Giordano: Lavanderia · Amanda: Banheiro de Visitas ---
        add(lavanderia, "Lavar a roupa", giordano, weekly, THURSDAY)
        add(lavanderia, "Lavar o pote dos cães", giordano, weekly, THURSDAY)
        add(lavanderia, "Lavar a roupa de cama", giordano, weekly, THURSDAY, interval = 2)
        add(banheiroVisitas, "Limpar a pia e os espelhos", amanda, weekly, THURSDAY)
        add(banheiroVisitas, "Limpar o vaso sanitário", amanda, weekly, THURSDAY)
        add(banheiroVisitas, "Limpar o chão", amanda, weekly, THURSDAY)
        add(banheiroVisitas, "Lavar/trocar as toalhas", amanda, weekly, THURSDAY, interval = 2)
        add(banheiroVisitas, "Organizar os armários", amanda, monthly, THURSDAY)

        // --- SEXTA · Giordano: Casa toda · Amanda: Quarto de Visitas + Fundos ---
        add(casaToda, "Aspirar o primeiro andar", giordano, weekly, FRIDAY)
        add(casaToda, "Aspirar o segundo andar", giordano, weekly, FRIDAY)
        add(casaToda, "Tirar o lixo reciclável", giordano, weekly, FRIDAY)
        add(casaToda, "Fazer o mercado", giordano, weekly, FRIDAY)
        add(casaToda, "Cozinhar as refeições (Mario)", giordano, weekly, FRIDAY, interval = 2)
        add(quartoVisitas, "Arrumar e organizar o quarto de visitas", amanda, weekly, FRIDAY)
        add(quartoVisitas, "Tirar o pó", amanda, weekly, FRIDAY)
        add(quartoVisitas, "Limpar a janela", amanda, weekly, FRIDAY, interval = 2)
        add(fundos, "Molhar as plantas", amanda, weekly, FRIDAY)
        add(cozinha, "Trocar os panos de prato", amanda, weekly, FRIDAY)

        // Sábado: livre para os dois.
    }

    /** Hoje, se for o mesmo dia da semana; senão, a próxima ocorrência desse dia. */
    private fun nextOnOrAfter(today: LocalDate, day: DayOfWeek): LocalDate {
        val diff = (day.value - today.dayOfWeek.value + 7) % 7
        return today.plusDays(diff.toLong())
    }
}

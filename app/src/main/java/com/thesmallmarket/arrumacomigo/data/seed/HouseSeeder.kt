package com.thesmallmarket.arrumacomigo.data.seed

import com.thesmallmarket.arrumacomigo.data.RecurrenceCalculator
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
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Popula o app, na primeira abertura, com a casa do Giordano e da Amanda seguindo a
 * "Rotina da Casa": zonas por dia de terça a domingo — segunda-feira é sempre livre.
 * Só roda com o banco vazio.
 *
 * Cadências (o dia da semana vem do weekday de [Task.nextDueDate]):
 * WEEKLY (toda semana), WEEKLY+interval 2 (quinzenal), MONTHLY (mensal), DAILY (todo dia,
 * inclusive segunda — usada nas tarefas realmente diárias, com interval 2 para "a cada 2 dias"),
 * e WEEKLY com bitmask de 6 dias (terça a domingo) para as "quase-diárias" que pulam a segunda —
 * não existe um modo DAILY-com-exceção no modelo (Recurrence.DAILY ignora daysOfWeek).
 */
object HouseSeeder {

    /** Terça a domingo — usado nas tarefas quase-diárias, que nunca caem na segunda. */
    private val semDaSegunda = DayOfWeek.entries
        .filter { it != MONDAY }
        .fold(0) { mask, day -> RecurrenceCalculator.toggleDay(mask, day) }

    suspend fun seedIfEmpty(repo: HouseholdRepository) {
        if (repo.peopleCount() > 0 || repo.roomsCount() > 0) return
        val today = LocalDate.now()

        // --- Pessoas ---
        val giordano = repo.upsertPerson(Person(name = "Giordano", colorHex = "#6C4DDB", emoji = "🧔"))
        val amanda = repo.upsertPerson(Person(name = "Amanda", colorHex = "#E5739D", emoji = "👩"))

        // --- Cômodos ---
        suspend fun room(name: String, type: RoomType) = repo.upsertRoom(RoomEntity(name = name, type = type))

        val cozinha = room("Cozinha", RoomType.KITCHEN)
        val salaEstar = room("Sala de Estar", RoomType.LIVING_ROOM)
        val salaJantar = room("Sala de Jantar", RoomType.LIVING_ROOM)
        val lavabo = room("Lavabo", RoomType.BATHROOM)
        val lavanderia = room("Lavanderia", RoomType.LAUNDRY)
        val quartoCasal = room("Quarto do Casal", RoomType.BEDROOM)
        val banheiroCasal = room("Banheiro do Casal", RoomType.BATHROOM)
        val quartoVisitas = room("Quarto de Visitas", RoomType.BEDROOM)
        val banheiroVisitas = room("Banheiro de Visitas", RoomType.BATHROOM)
        val quartoAugusto = room("Quarto do Augusto", RoomType.KIDS_ROOM)
        val escritorio = room("Escritório", RoomType.OFFICE)
        val garagem = room("Garagem", RoomType.GARAGE)
        val fundos = room("Fundos", RoomType.OUTDOOR)
        val jardim = room("Jardim", RoomType.OUTDOOR)
        val geral = room("Geral", RoomType.OTHER)

        // --- Helper para adicionar tarefas ---
        suspend fun add(
            room: Long,
            title: String,
            person: Long?,
            recurrence: Recurrence,
            day: DayOfWeek? = null,
            interval: Int = 1,
            daysOfWeek: Int = 0,
            priority: Priority = Priority.MEDIUM,
        ) {
            val due = when {
                daysOfWeek != 0 -> RecurrenceCalculator.firstWeeklyOccurrence(today, daysOfWeek)
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
                    daysOfWeek = daysOfWeek,
                    nextDueDate = due,
                    priority = priority,
                )
            )
        }

        val daily = Recurrence.DAILY
        val weekly = Recurrence.WEEKLY
        val monthly = Recurrence.MONTHLY

        // --- TERÇA · Banheiros (chão+vaso=Giordano, resto=Amanda) + Garagem ---
        add(lavabo, "Limpar chão", giordano, weekly, TUESDAY)
        add(lavabo, "Limpar vaso sanitário", giordano, weekly, TUESDAY)
        add(lavabo, "Limpar espelho", amanda, weekly, TUESDAY)
        add(lavabo, "Limpar pia", amanda, weekly, TUESDAY)
        add(banheiroCasal, "Limpar chão", giordano, weekly, TUESDAY)
        add(banheiroCasal, "Limpar vaso sanitário", giordano, weekly, TUESDAY)
        add(banheiroCasal, "Trocar toalhas", amanda, weekly, TUESDAY, interval = 2)
        add(banheiroCasal, "Limpar box", amanda, weekly, TUESDAY, interval = 2)
        add(banheiroCasal, "Limpar vidros", amanda, weekly, TUESDAY, interval = 2)
        add(banheiroCasal, "Limpar espelhos", amanda, weekly, TUESDAY)
        add(banheiroCasal, "Limpar pia", amanda, weekly, TUESDAY)
        add(banheiroVisitas, "Limpar chão", giordano, weekly, TUESDAY)
        add(banheiroVisitas, "Limpar vaso sanitário", giordano, weekly, TUESDAY)
        add(banheiroVisitas, "Limpar espelhos", amanda, weekly, TUESDAY)
        add(banheiroVisitas, "Limpar pia", amanda, weekly, TUESDAY)
        add(garagem, "Varrer", giordano, weekly, TUESDAY)
        add(garagem, "Lavar", giordano, weekly, TUESDAY, interval = 2)

        // --- QUARTA · Cozinha (exceto bancada, agora diária) + Quarto do Casal ---
        add(cozinha, "Passar pano", giordano, weekly, WEDNESDAY)
        add(cozinha, "Limpar e organizar gavetas", giordano, monthly, WEDNESDAY)
        add(cozinha, "Limpar eletros", giordano, weekly, WEDNESDAY)
        add(cozinha, "Limpar fogão", giordano, weekly, WEDNESDAY)
        add(cozinha, "Limpar lata de lixo", giordano, weekly, WEDNESDAY, interval = 2)
        add(cozinha, "Organizar geladeira", giordano, weekly, WEDNESDAY)
        add(cozinha, "Trocar panos de prato", amanda, weekly, WEDNESDAY)
        add(cozinha, "Limpar e organizar armários", giordano, monthly, WEDNESDAY)
        add(cozinha, "Limpar congelador", giordano, monthly, WEDNESDAY)
        add(cozinha, "Limpar forno", giordano, monthly, WEDNESDAY)
        add(cozinha, "Limpar micro-ondas", giordano, monthly, WEDNESDAY)
        add(quartoCasal, "Limpar janela", amanda, weekly, WEDNESDAY, interval = 2)
        add(quartoCasal, "Organizar closet", amanda, monthly, WEDNESDAY)
        add(quartoCasal, "Tirar pó", amanda, weekly, WEDNESDAY)
        add(quartoCasal, "Trocar roupa de cama", amanda, weekly, WEDNESDAY)
        add(quartoCasal, "Dobrar roupas", amanda, weekly, WEDNESDAY)
        add(lavanderia, "Lavar roupa de cama", amanda, weekly, WEDNESDAY)

        // --- QUINTA · Escritório + Lavanderia ---
        add(escritorio, "Limpar janela", giordano, weekly, THURSDAY, interval = 2)
        add(escritorio, "Limpar objetos", giordano, weekly, THURSDAY)
        add(escritorio, "Passar aspirador", giordano, weekly, THURSDAY)
        add(escritorio, "Tirar pó", giordano, weekly, THURSDAY)
        add(escritorio, "Arrumar", giordano, weekly, THURSDAY)
        add(lavanderia, "Lavar toalha de banho", amanda, weekly, THURSDAY)
        add(lavanderia, "Lavar panos de limpeza", amanda, weekly, THURSDAY)
        add(lavanderia, "Lavar potes dos cachorros", giordano, weekly, THURSDAY)
        add(lavanderia, "Lavar roupa escura", amanda, weekly, THURSDAY)
        add(lavanderia, "Lavar roupas claras", amanda, weekly, THURSDAY)
        add(lavanderia, "Lavar roupas do Augusto", amanda, weekly, THURSDAY)
        add(lavanderia, "Organizar armários", amanda, monthly, THURSDAY)

        // --- SEXTA · Sala de Estar + Sala de Jantar ---
        add(salaEstar, "Arrumar", giordano, weekly, FRIDAY)
        add(salaEstar, "Limpar janela", giordano, weekly, FRIDAY, interval = 2)
        add(salaEstar, "Limpar painel", giordano, weekly, FRIDAY, interval = 2)
        add(salaEstar, "Tirar pó", giordano, weekly, FRIDAY)
        add(salaJantar, "Arrumar", amanda, weekly, FRIDAY)
        add(salaJantar, "Limpar e organizar aparador", amanda, monthly, FRIDAY)
        add(salaJantar, "Passar pano na mesa", amanda, weekly, FRIDAY)
        add(salaJantar, "Tirar pó dos quadros", amanda, weekly, FRIDAY, interval = 2)

        // --- SÁBADO · Geral (casa toda) + Quarto do Augusto ---
        add(geral, "Tirar lixo reciclável", giordano, weekly, SATURDAY)
        add(quartoAugusto, "Arrumar", amanda, weekly, SATURDAY)
        add(quartoAugusto, "Organizar e limpar armários", amanda, monthly, SATURDAY)
        add(quartoAugusto, "Tirar pó", amanda, weekly, SATURDAY)
        add(quartoAugusto, "Trocar roupa de cama", amanda, weekly, SATURDAY, interval = 2)

        // --- DOMINGO · Quarto de Visitas + Jardim + Fundos ---
        add(quartoVisitas, "Arrumar", amanda, weekly, SUNDAY)
        add(quartoVisitas, "Limpar janela", amanda, weekly, SUNDAY, interval = 2)
        add(quartoVisitas, "Tirar pó", amanda, weekly, SUNDAY)
        add(jardim, "Cortar grama", giordano, monthly, SUNDAY)
        add(fundos, "Lavar", giordano, weekly, SUNDAY)

        // --- QUASE-DIÁRIAS (terça a domingo, nunca segunda) ---
        add(fundos, "Tirar cocô", giordano, weekly, daysOfWeek = semDaSegunda, priority = Priority.HIGH)
        add(geral, "Molhar plantas", amanda, weekly, daysOfWeek = semDaSegunda)

        // --- DIÁRIAS PURAS (todo dia, inclusive segunda) ---
        add(cozinha, "Lavar louça", giordano, daily)
        add(cozinha, "Limpar bancadas", giordano, daily, interval = 2)
        add(geral, "Tirar lixo orgânico", giordano, daily)
        add(quartoCasal, "Arrumar cama", amanda, daily)
        add(geral, "Aspirar primeiro andar", giordano, daily)
        add(geral, "Aspirar sofá", giordano, daily, interval = 2)
        add(geral, "Aspirar segundo andar", giordano, daily, interval = 2)
    }

    /** Hoje, se for o mesmo dia da semana; senão, a próxima ocorrência desse dia. */
    private fun nextOnOrAfter(today: LocalDate, day: DayOfWeek): LocalDate {
        val diff = (day.value - today.dayOfWeek.value + 7) % 7
        return today.plusDays(diff.toLong())
    }
}

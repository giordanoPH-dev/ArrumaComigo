package com.thesmallmarket.arrumacomigo.ui.history

import androidx.lifecycle.ViewModel
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import com.thesmallmarket.arrumacomigo.ui.PersonBalance
import com.thesmallmarket.arrumacomigo.ui.currentDateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

data class HistoryUiState(
    val balances: List<PersonBalance> = emptyList(),
    val recent: List<TaskCompletion> = emptyList(),
    val total: Int = 0,
)

class HistoryViewModel(
    private val repository: HouseholdRepository,
) : ViewModel() {

    // Janela rolante: recalculada quando o dia vira, para o balanço não congelar com o app aberto.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = currentDateFlow().flatMapLatest { today ->
        combine(
            repository.completionsSince(today.minusDays(30).atStartOfDay()),
            repository.people(),
        ) { completions, people ->
        val personById = people.associateBy { it.id }
        val byPerson = completions.groupBy { it.personId }
        val balances = byPerson.map { (personId, list) ->
            PersonBalance(person = personId?.let { personById[it] }, count = list.size)
        }.sortedByDescending { it.count }
            HistoryUiState(
                balances = balances,
                recent = completions.take(30),
                total = completions.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

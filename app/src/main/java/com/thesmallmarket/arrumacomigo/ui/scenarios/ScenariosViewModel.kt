package com.thesmallmarket.arrumacomigo.ui.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thesmallmarket.arrumacomigo.data.entity.Scenario
import com.thesmallmarket.arrumacomigo.data.entity.ScenarioItem
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ScenariosViewModel(private val repository: HouseholdRepository) : ViewModel() {

    val scenarios: StateFlow<List<Scenario>> =
        repository.scenarios().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** scenarioId → (feitos, total), para o "X de Y feitos" dos cartões. */
    val counts: StateFlow<Map<Long, Pair<Int, Int>>> = repository.allScenarioItems()
        .map { items ->
            items.groupBy { it.scenarioId }
                .mapValues { (_, list) -> list.count { it.checked } to list.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val selectedScenarioId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = selectedScenarioId

    val items: StateFlow<List<ScenarioItem>> = selectedScenarioId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.scenarioItems(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(id: Long?) {
        selectedScenarioId.value = id
    }

    fun createScenario(name: String) {
        viewModelScope.launch {
            selectedScenarioId.value = repository.upsertScenario(Scenario(name = name))
        }
    }

    fun renameScenario(scenario: Scenario, name: String) {
        viewModelScope.launch { repository.upsertScenario(scenario.copy(name = name)) }
    }

    fun deleteScenario(scenario: Scenario) {
        viewModelScope.launch {
            repository.deleteScenario(scenario)
            if (selectedScenarioId.value == scenario.id) selectedScenarioId.value = null
        }
    }

    fun addItem(title: String) {
        val scenarioId = selectedScenarioId.value ?: return
        val position = items.value.size
        viewModelScope.launch {
            repository.upsertScenarioItem(ScenarioItem(scenarioId = scenarioId, title = title, position = position))
        }
    }

    fun renameItem(item: ScenarioItem, title: String) {
        viewModelScope.launch { repository.upsertScenarioItem(item.copy(title = title)) }
    }

    fun deleteItem(item: ScenarioItem) {
        viewModelScope.launch { repository.deleteScenarioItem(item) }
    }

    /** Move o item do checklist de [from] para [to] (renumera as posições). */
    fun moveItem(from: Int, to: Int) {
        val current = items.value
        if (from !in current.indices || to !in current.indices) return
        viewModelScope.launch { repository.moveScenarioItem(current, from, to) }
    }

    fun toggle(item: ScenarioItem) {
        viewModelScope.launch { repository.upsertScenarioItem(item.copy(checked = !item.checked)) }
    }

    fun reset() {
        val scenarioId = selectedScenarioId.value ?: return
        viewModelScope.launch { repository.resetScenario(scenarioId) }
    }
}

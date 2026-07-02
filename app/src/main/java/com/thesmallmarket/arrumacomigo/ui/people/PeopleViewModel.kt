package com.thesmallmarket.arrumacomigo.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(
    private val repository: HouseholdRepository,
) : ViewModel() {

    val people: StateFlow<List<Person>> =
        repository.people().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(person: Person) {
        viewModelScope.launch { repository.upsertPerson(person) }
    }

    fun delete(person: Person) {
        viewModelScope.launch { repository.deletePerson(person) }
    }
}

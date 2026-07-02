package com.thesmallmarket.arrumacomigo.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thesmallmarket.arrumacomigo.HouseholdApplication
import com.thesmallmarket.arrumacomigo.ui.history.HistoryViewModel
import com.thesmallmarket.arrumacomigo.ui.people.PeopleViewModel
import com.thesmallmarket.arrumacomigo.ui.rooms.RoomsViewModel
import com.thesmallmarket.arrumacomigo.ui.tasks.TaskEditViewModel
import com.thesmallmarket.arrumacomigo.ui.today.TodayViewModel

/** Fábrica que injeta o container manual nos ViewModels (padrão dos codelabs). */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val app = arrumaApp()
            TodayViewModel(app.container.repository, app.container.reminderScheduler)
        }
        initializer {
            val app = arrumaApp()
            RoomsViewModel(app.container.repository, app.container.reminderScheduler)
        }
        initializer {
            val app = arrumaApp()
            TaskEditViewModel(app.container.repository, app.container.reminderScheduler)
        }
        initializer {
            PeopleViewModel(arrumaApp().container.repository)
        }
        initializer {
            HistoryViewModel(arrumaApp().container.repository)
        }
    }
}

private fun CreationExtras.arrumaApp(): HouseholdApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HouseholdApplication

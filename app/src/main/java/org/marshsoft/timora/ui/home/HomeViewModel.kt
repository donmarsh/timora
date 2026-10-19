package org.marshsoft.timora.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.marshsoft.timora.data.TimerRepository
import org.marshsoft.timora.domain.Timer

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val timers: List<Timer>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

sealed interface HomeEvent {
    /** A timer was deleted; [timer] is a copy that can be passed to [HomeViewModel.undoDelete]. */
    data class TimerDeleted(val timer: Timer) : HomeEvent
    data class Message(val text: String) : HomeEvent
}

class HomeViewModel(private val repository: TimerRepository) : ViewModel() {

    val state: StateFlow<HomeUiState> = repository.observeAll()
        .map<List<Timer>, HomeUiState> { HomeUiState.Content(it) }
        .catch { emit(HomeUiState.Error(it.message ?: "Could not load timers")) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    fun delete(timer: Timer) {
        viewModelScope.launch {
            runCatchingNonCancel { repository.delete(timer.id) }
                .onSuccess { eventChannel.send(HomeEvent.TimerDeleted(timer)) }
                .onFailure { eventChannel.send(HomeEvent.Message("Could not delete timer")) }
        }
    }

    /** Re-creates a deleted timer (it receives a new id). */
    fun undoDelete(timer: Timer) {
        viewModelScope.launch {
            runCatchingNonCancel { repository.save(timer.copy(id = 0)) }
                .onFailure { eventChannel.send(HomeEvent.Message("Could not restore timer")) }
        }
    }

    fun duplicate(timerId: Long) {
        viewModelScope.launch {
            runCatchingNonCancel { repository.duplicate(timerId) }
                .onFailure { eventChannel.send(HomeEvent.Message("Could not duplicate timer")) }
        }
    }

    private inline fun <T> runCatchingNonCancel(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        fun factory(repository: TimerRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}

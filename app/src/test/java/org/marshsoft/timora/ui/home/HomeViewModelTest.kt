package org.marshsoft.timora.ui.home

import androidx.lifecycle.viewmodel.CreationExtras
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.marshsoft.timora.domain.SegmentType

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `loading before first emission`() = runTest {
        val repo = FakeTimerRepository().apply { observeOverride = flow { awaitCancellation() } }
        val vm = HomeViewModel(repo)
        assertEquals(HomeUiState.Loading, vm.state.value)
    }

    @Test fun `empty repository yields empty content`() = runTest {
        val vm = HomeViewModel(FakeTimerRepository())
        assertEquals(HomeUiState.Content(emptyList()), vm.state.value)
    }

    @Test fun `exposes timers and follows repository changes`() = runTest {
        val repo = FakeTimerRepository(listOf(sampleTimer(1, "A")))
        val vm = HomeViewModel(repo)
        assertEquals(listOf("A"), (vm.state.value as HomeUiState.Content).timers.map { it.name })
        repo.save(sampleTimer(0, "B"))
        assertEquals(listOf("A", "B"), (vm.state.value as HomeUiState.Content).timers.map { it.name })
    }

    @Test fun `observe failure becomes error state`() = runTest {
        val repo = FakeTimerRepository().apply { observeOverride = flow { throw IllegalStateException("boom") } }
        val vm = HomeViewModel(repo)
        assertEquals(HomeUiState.Error("boom"), vm.state.value)
    }

    @Test fun `observe failure without message uses fallback text`() = runTest {
        val repo = FakeTimerRepository().apply { observeOverride = flow { throw RuntimeException() } }
        val vm = HomeViewModel(repo)
        assertEquals(HomeUiState.Error("Could not load timers"), vm.state.value)
    }

    @Test fun `delete removes timer and emits event with copy`() = runTest {
        val timer = sampleTimer(1, "A")
        val repo = FakeTimerRepository(listOf(timer))
        val vm = HomeViewModel(repo)
        vm.events.test {
            vm.delete(timer)
            assertEquals(HomeEvent.TimerDeleted(timer), awaitItem())
        }
        assertTrue(repo.current.isEmpty())
        assertEquals(HomeUiState.Content(emptyList()), vm.state.value)
    }

    @Test fun `undo delete restores timer with a new id`() = runTest {
        val timer = sampleTimer(1, "A")
        val repo = FakeTimerRepository(listOf(timer))
        val vm = HomeViewModel(repo)
        vm.delete(timer)
        vm.undoDelete(timer)
        assertEquals(1, repo.current.size)
        assertEquals("A", repo.current.single().name)
        assertTrue(repo.current.single().id != 1L)
        assertEquals(0L, repo.saved.single().id)
    }

    @Test fun `delete failure emits message and keeps timer`() = runTest {
        val timer = sampleTimer(1, "A")
        val repo = FakeTimerRepository(listOf(timer)).apply { failDelete = IllegalStateException("x") }
        val vm = HomeViewModel(repo)
        vm.events.test {
            vm.delete(timer)
            assertEquals(HomeEvent.Message("Could not delete timer"), awaitItem())
        }
        assertEquals(1, repo.current.size)
    }

    @Test fun `undo failure emits message`() = runTest {
        val timer = sampleTimer(1, "A")
        val repo = FakeTimerRepository().apply { failSave = IllegalStateException("x") }
        val vm = HomeViewModel(repo)
        vm.events.test {
            vm.undoDelete(timer)
            assertEquals(HomeEvent.Message("Could not restore timer"), awaitItem())
        }
    }

    @Test fun `duplicate adds a copy`() = runTest {
        val repo = FakeTimerRepository(listOf(sampleTimer(1, "A")))
        val vm = HomeViewModel(repo)
        vm.duplicate(1)
        assertEquals(listOf("A", "A (copy)"), repo.current.map { it.name })
    }

    @Test fun `duplicate failure emits message`() = runTest {
        val repo = FakeTimerRepository(listOf(sampleTimer(1, "A"))).apply { failDuplicate = IllegalStateException("x") }
        val vm = HomeViewModel(repo)
        vm.events.test {
            vm.duplicate(1)
            assertEquals(HomeEvent.Message("Could not duplicate timer"), awaitItem())
        }
    }

    @Test fun `factory creates a view model`() {
        val vm = HomeViewModel.factory(FakeTimerRepository()).create(HomeViewModel::class.java, CreationExtras.Empty)
        assertTrue(vm is HomeViewModel)
    }

    // ---- summary helpers ----

    @Test fun `compactDuration formats seconds minutes and hours`() {
        assertEquals("0s", compactDuration(0))
        assertEquals("40s", compactDuration(40))
        assertEquals("2m", compactDuration(120))
        assertEquals("1m 30s", compactDuration(90))
        assertEquals("1h", compactDuration(3600))
        assertEquals("1h 5m", compactDuration(3900))
        assertEquals("1h 5s", compactDuration(3605))
        assertEquals("0s", compactDuration(-5))
    }

    @Test fun `summarize lists chips and repeat markers`() {
        val items = summarize(sampleTimer(1, cycles = 8))
        assertEquals(
            listOf(
                SummaryItem.SegmentChip(SegmentType.WORK, "Work 40s"),
                SummaryItem.SegmentChip(SegmentType.REST, "Rest 20s"),
                SummaryItem.Repeat("×8"),
            ),
            items,
        )
        // single-cycle blocks have no repeat marker
        assertEquals(2, summarize(sampleTimer(1, cycles = 1)).size)
    }
}

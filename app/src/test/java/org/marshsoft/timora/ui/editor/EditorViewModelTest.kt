package org.marshsoft.timora.ui.editor

import androidx.lifecycle.viewmodel.CreationExtras
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.ValidationError
import org.marshsoft.timora.ui.home.FakeTimerRepository
import org.marshsoft.timora.ui.home.sampleTimer

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun newVm(repo: FakeTimerRepository = FakeTimerRepository(), id: Long = 0L) = EditorViewModel(repo, id)

    private val EditorViewModel.timer get() = state.value.timer

    // ---- loading ----

    @Test fun `new timer starts with sensible default block`() {
        val vm = newVm()
        val s = vm.state.value
        assertFalse(s.loading)
        assertTrue(s.isNew)
        assertEquals("", s.timer.name)
        val block = s.timer.blocks.single()
        assertEquals(5, block.cycles)
        assertEquals(listOf("Work", "Rest"), block.segments.map { it.name })
        assertEquals(listOf(30, 15), block.segments.map { it.durationSeconds })
        assertEquals(listOf(SegmentType.WORK, SegmentType.REST), block.segments.map { it.type })
        assertTrue(block.id < 0 && block.segments.all { it.id < 0 })
        assertEquals(5 * 45, s.totalSeconds)
        assertFalse(s.dirty)
    }

    @Test fun `existing timer is loaded`() {
        val repo = FakeTimerRepository(listOf(sampleTimer(3, "Tabata", prep = 10)))
        val vm = newVm(repo, 3)
        val s = vm.state.value
        assertFalse(s.loading)
        assertFalse(s.isNew)
        assertEquals("Tabata", s.timer.name)
        assertEquals(10 + 8 * 60, s.totalSeconds)
        assertFalse(s.dirty)
    }

    @Test fun `unknown id is reported as not found`() {
        val vm = newVm(FakeTimerRepository(), 99)
        assertTrue(vm.state.value.notFound)
        assertFalse(vm.state.value.loading)
        vm.save() // no-op, must not crash
        assertFalse(vm.state.value.saving)
    }

    @Test fun `repository failure while loading is reported as not found`() {
        val repo = FakeTimerRepository(listOf(sampleTimer(3))).apply { failGet = IllegalStateException() }
        assertTrue(newVm(repo, 3).state.value.notFound)
    }

    // ---- timer-level edits ----

    @Test fun `name prep and color updates mark dirty`() {
        val vm = newVm()
        vm.updateName("Run")
        vm.updatePrepSeconds(10)
        vm.updateColorIndex(4)
        assertEquals("Run", vm.timer.name)
        assertEquals(10, vm.timer.prepSeconds)
        assertEquals(4, vm.timer.colorIndex)
        assertTrue(vm.state.value.dirty)
        assertEquals(10 + 225, vm.state.value.totalSeconds)
    }

    @Test fun `prep is clamped`() {
        val vm = newVm()
        vm.updatePrepSeconds(-5)
        assertEquals(0, vm.timer.prepSeconds)
        vm.updatePrepSeconds(99_999)
        assertEquals(3_600, vm.timer.prepSeconds)
    }

    @Test fun `reverting a change clears dirty`() {
        val vm = newVm()
        vm.updateName("x")
        assertTrue(vm.state.value.dirty)
        vm.updateName("")
        assertFalse(vm.state.value.dirty)
    }

    // ---- blocks ----

    @Test fun `add block appends a one segment block with a unique key`() {
        val vm = newVm()
        vm.addBlock()
        val blocks = vm.timer.blocks
        assertEquals(2, blocks.size)
        assertEquals(1, blocks[1].cycles)
        assertEquals(1, blocks[1].segments.size)
        assertEquals(blocks.size, blocks.map { it.id }.toSet().size)
    }

    @Test fun `remove block and out of range remove`() {
        val vm = newVm()
        vm.addBlock()
        vm.removeBlock(5)
        assertEquals(2, vm.timer.blocks.size)
        vm.removeBlock(0)
        assertEquals(1, vm.timer.blocks.size)
        assertEquals(1, vm.timer.blocks.single().cycles)
    }

    @Test fun `move block reorders and ignores invalid moves`() {
        val vm = newVm()
        vm.addBlock()
        vm.updateBlockName(0, "first")
        vm.updateBlockName(1, "second")
        vm.moveBlock(0, 1)
        assertEquals(listOf("second", "first"), vm.timer.blocks.map { it.name })
        vm.moveBlock(0, 7)
        vm.moveBlock(-1, 0)
        vm.moveBlock(1, 1)
        assertEquals(listOf("second", "first"), vm.timer.blocks.map { it.name })
    }

    @Test fun `block name blank becomes null`() {
        val vm = newVm()
        vm.updateBlockName(0, "x")
        assertEquals("x", vm.timer.blocks[0].name)
        vm.updateBlockName(0, "")
        assertNull(vm.timer.blocks[0].name)
    }

    @Test fun `cycles are clamped to 1 to 999`() {
        val vm = newVm()
        vm.updateCycles(0, 0)
        assertEquals(1, vm.timer.blocks[0].cycles)
        vm.updateCycles(0, 5000)
        assertEquals(999, vm.timer.blocks[0].cycles)
        vm.updateCycles(0, 12)
        assertEquals(12, vm.timer.blocks[0].cycles)
    }

    @Test fun `skip last rest toggles and shortens total`() {
        val vm = newVm()
        vm.setSkipLastRest(0, true)
        assertTrue(vm.timer.blocks[0].skipLastRest)
        assertEquals(5 * 45 - 15, vm.state.value.totalSeconds)
    }

    @Test fun `block edits with bad index are ignored`() {
        val vm = newVm()
        val before = vm.timer
        vm.updateCycles(9, 3)
        vm.setSkipLastRest(9, true)
        vm.addSegment(9)
        assertEquals(before, vm.timer)
    }

    // ---- segments ----

    @Test fun `add segment alternates type after work`() {
        val vm = newVm()
        vm.addSegment(0) // last was REST -> WORK
        vm.addSegment(0) // last was WORK -> REST
        val segs = vm.timer.blocks[0].segments
        assertEquals(4, segs.size)
        assertEquals(SegmentType.WORK, segs[2].type)
        assertEquals("Work", segs[2].name)
        assertEquals(30, segs[2].durationSeconds)
        assertEquals(SegmentType.REST, segs[3].type)
        assertEquals(15, segs[3].durationSeconds)
        assertEquals(4, segs.map { it.id }.toSet().size)
    }

    @Test fun `remove and move segments`() {
        val vm = newVm()
        vm.addSegment(0)
        vm.updateSegmentName(0, 2, "Third")
        vm.moveSegment(0, 2, 0)
        assertEquals(listOf("Third", "Work", "Rest"), vm.timer.blocks[0].segments.map { it.name })
        vm.moveSegment(0, 0, 9)
        assertEquals(listOf("Third", "Work", "Rest"), vm.timer.blocks[0].segments.map { it.name })
        vm.removeSegment(0, 1)
        assertEquals(listOf("Third", "Rest"), vm.timer.blocks[0].segments.map { it.name })
        vm.removeSegment(0, 8)
        assertEquals(2, vm.timer.blocks[0].segments.size)
    }

    @Test fun `update segment applies transform and ignores bad index`() {
        val vm = newVm()
        vm.updateSegment(0, 0) { it.copy(name = "Sprint", durationSeconds = 10) }
        assertEquals("Sprint", vm.timer.blocks[0].segments[0].name)
        assertEquals(10, vm.timer.blocks[0].segments[0].durationSeconds)
        val before = vm.timer
        vm.updateSegment(0, 7) { it.copy(name = "nope") }
        assertEquals(before, vm.timer)
    }

    @Test fun `type change renames default names only`() {
        val vm = newVm()
        vm.updateSegmentType(0, 0, SegmentType.COOLDOWN)
        assertEquals("Cooldown", vm.timer.blocks[0].segments[0].name)
        assertEquals(SegmentType.COOLDOWN, vm.timer.blocks[0].segments[0].type)
        vm.updateSegmentName(0, 1, "Recover")
        vm.updateSegmentType(0, 1, SegmentType.PREP)
        assertEquals("Recover", vm.timer.blocks[0].segments[1].name)
        assertEquals(SegmentType.PREP, vm.timer.blocks[0].segments[1].type)
    }

    @Test fun `duration set and adjust are clamped`() {
        val vm = newVm()
        vm.updateSegmentDuration(0, 0, 90)
        assertEquals(90, vm.timer.blocks[0].segments[0].durationSeconds)
        vm.updateSegmentDuration(0, 0, -4)
        assertEquals(0, vm.timer.blocks[0].segments[0].durationSeconds)
        vm.updateSegmentDuration(0, 0, 1_000_000)
        assertEquals(86_400, vm.timer.blocks[0].segments[0].durationSeconds)
        vm.updateSegmentDuration(0, 0, 3)
        vm.adjustSegmentDuration(0, 0, -5)
        assertEquals(1, vm.timer.blocks[0].segments[0].durationSeconds)
        vm.adjustSegmentDuration(0, 0, 5)
        assertEquals(6, vm.timer.blocks[0].segments[0].durationSeconds)
        vm.updateSegmentDuration(0, 0, 86_399)
        vm.adjustSegmentDuration(0, 0, 50)
        assertEquals(86_400, vm.timer.blocks[0].segments[0].durationSeconds)
    }

    // ---- validation / save ----

    @Test fun `errors are hidden until save is attempted`() {
        val vm = newVm()
        assertTrue(vm.state.value.errors.isEmpty())
        assertFalse(vm.state.value.nameError)
    }

    @Test fun `save with blank name shows error and does not persist`() = runTest {
        val repo = FakeTimerRepository()
        val vm = newVm(repo)
        vm.save()
        val s = vm.state.value
        assertTrue(s.nameError)
        assertTrue(ValidationError.BlankName in s.errors)
        assertTrue(repo.saved.isEmpty())
        // errors stay live afterwards
        vm.updateName("Ok")
        assertFalse(vm.state.value.nameError)
    }

    @Test fun `field level errors map to blocks and segments`() {
        val vm = newVm()
        vm.updateName("T")
        vm.updateSegmentDuration(0, 1, 0)
        vm.updateSegmentName(0, 0, " ")
        vm.addBlock()
        vm.removeSegment(1, 0)
        vm.save()
        val s = vm.state.value
        assertTrue(s.durationError(0, 1))
        assertFalse(s.durationError(0, 0))
        assertTrue(s.segmentNameError(0, 0))
        assertFalse(s.segmentNameError(0, 1))
        assertTrue(s.emptyBlockError(1))
        assertFalse(s.emptyBlockError(0))
        assertFalse(s.cyclesError(0))
        assertFalse(s.prepError)
    }

    @Test fun `removing every block raises the no blocks error`() {
        val vm = newVm()
        vm.updateName("T")
        vm.removeBlock(0)
        vm.save()
        assertTrue(vm.state.value.noBlocksError)
    }

    @Test fun `successful save of new timer maps temp ids to zero and emits Saved`() = runTest {
        val repo = FakeTimerRepository()
        val vm = newVm(repo)
        vm.updateName("Mine")
        vm.events.test {
            vm.save()
            val event = awaitItem()
            assertTrue(event is EditorEvent.Saved)
            assertEquals(1L, (event as EditorEvent.Saved).id)
        }
        val saved = repo.saved.single()
        assertEquals(0L, saved.id)
        assertEquals(0L, saved.blocks[0].id)
        assertTrue(saved.blocks[0].segments.all { it.id == 0L })
        assertEquals("Mine", repo.current.single().name)
        assertFalse(vm.state.value.dirty)
        assertFalse(vm.state.value.saving)
    }

    @Test fun `saving existing timer keeps persistent ids`() = runTest {
        val repo = FakeTimerRepository(listOf(sampleTimer(3, "Tabata")))
        val vm = newVm(repo, 3)
        vm.updateName("Renamed")
        vm.addSegment(0)
        vm.events.test {
            vm.save()
            assertEquals(EditorEvent.Saved(3), awaitItem())
        }
        val saved = repo.saved.single()
        assertEquals(3L, saved.id)
        assertEquals(30L, saved.blocks[0].id)
        assertEquals(300L, saved.blocks[0].segments[0].id)
        assertEquals(0L, saved.blocks[0].segments[2].id)
        assertEquals("Renamed", repo.current.single().name)
    }

    @Test fun `save failure surfaces message and keeps draft`() = runTest {
        val repo = FakeTimerRepository().apply { failSave = IllegalStateException("disk") }
        val vm = newVm(repo)
        vm.updateName("Mine")
        vm.save()
        val s = vm.state.value
        assertNotNull(s.saveError)
        assertFalse(s.saving)
        assertTrue(s.dirty)
        // editing clears the banner
        vm.updateName("Mine2")
        assertNull(vm.state.value.saveError)
    }

    @Test fun `repository validation failure maps to field error message`() = runTest {
        val repo = FakeTimerRepository().apply {
            failSave = org.marshsoft.timora.data.InvalidTimerException(listOf(ValidationError.BlankName))
        }
        val vm = newVm(repo)
        vm.updateName("Mine")
        vm.save()
        assertNotNull(vm.state.value.saveError)
        assertTrue(vm.state.value.showErrors)
    }

    @Test fun `factory creates a view model`() {
        val f = EditorViewModel.factory(FakeTimerRepository(), 0L)
        assertTrue(f.create(EditorViewModel::class.java, CreationExtras.Empty) is EditorViewModel)
    }

    @Test fun `moved helper`() {
        assertEquals(listOf(2, 1, 3), listOf(1, 2, 3).moved(1, 0))
        assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).moved(2, 2))
    }

    @Test fun `combine helper`() {
        assertEquals(0, combine("", ""))
        assertEquals(90, combine("1", "30"))
        assertEquals(135, combine("1", "75"))
        assertEquals(86_400, combine("9999", "0"))
    }
}

package org.marshsoft.timora.ui.editor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.ui.home.FakeTimerRepository
import org.marshsoft.timora.ui.home.sampleTimer
import org.marshsoft.timora.ui.theme.TimoraTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h4000dp")
class EditorScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private var backCalls = 0

    private fun show(vm: EditorViewModel) {
        rule.setContent {
            TimoraTheme {
                val state by vm.state.collectAsState()
                EditorScreen(state = state, actions = vm, onBack = { backCalls++ })
            }
        }
    }

    private fun newVm(repo: FakeTimerRepository = FakeTimerRepository(), id: Long = 0L) = EditorViewModel(repo, id)

    private val EditorViewModel.timer get() = state.value.timer

    @Test fun loadingState() {
        val vm = newVm()
        rule.setContent { TimoraTheme { EditorScreen(EditorUiState(loading = true), vm, {}) } }
        rule.onNodeWithTag("editor_loading").assertIsDisplayed()
        rule.onAllNodesWithTag("editor_save").assertCountEquals(0)
    }

    @Test fun notFoundState() {
        show(newVm(id = 42))
        rule.onNodeWithTag("editor_not_found").assertIsDisplayed()
        rule.onAllNodesWithTag("editor_save").assertCountEquals(0)
        rule.onNodeWithTag("editor_back").performClick()
        assertEquals(1, backCalls)
    }

    @Test fun newTimerShowsDefaults() {
        show(newVm())
        rule.onNodeWithText("New timer").assertIsDisplayed()
        rule.onNodeWithTag("editor_total").assertTextEquals("Total 3:45")
        rule.onNodeWithTag("cycles_0_value").assertTextEquals("5")
        rule.onNodeWithTag("segment_0_0").assertIsDisplayed()
        rule.onNodeWithTag("segment_0_1").assertIsDisplayed()
        rule.onNodeWithTag("seg_dur_0_0_min").assertIsDisplayed()
    }

    @Test fun existingTimerShowsEditTitle() {
        show(newVm(FakeTimerRepository(listOf(sampleTimer(3, "Tabata"))), 3))
        rule.onNodeWithText("Edit timer").assertIsDisplayed()
        rule.onNodeWithTag("editor_total").assertTextEquals("Total 8:00")
    }

    @Test fun editingNameUpdatesState() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("editor_name").performTextInput("Morning")
        assertEquals("Morning", vm.timer.name)
    }

    @Test fun prepStepper() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("editor_prep_dec").assertIsNotEnabled()
        rule.onNodeWithTag("editor_prep_inc").performClick()
        rule.onNodeWithTag("editor_prep_inc").performClick()
        rule.onNodeWithTag("editor_prep_value").assertTextEquals("10s")
        rule.onNodeWithTag("editor_total").assertTextEquals("Total 3:55")
        rule.onNodeWithTag("editor_prep_dec").performClick()
        assertEquals(5, vm.timer.prepSeconds)
    }

    @Test fun colorSwatchSelection() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("editor_color_3").performClick()
        assertEquals(3, vm.timer.colorIndex)
    }

    @Test fun cyclesStepperUpdatesTotal() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("cycles_0_inc").performClick()
        rule.onNodeWithTag("cycles_0_value").assertTextEquals("6")
        rule.onNodeWithTag("editor_total").assertTextEquals("Total 4:30")
        rule.onNodeWithTag("cycles_0_dec").performClick()
        rule.onNodeWithTag("cycles_0_dec").performClick()
        assertEquals(4, vm.timer.blocks[0].cycles)
    }

    @Test fun cyclesDecrementDisabledAtOne() {
        val vm = newVm()
        vm.updateCycles(0, 1)
        show(vm)
        rule.onNodeWithTag("cycles_0_dec").assertIsNotEnabled()
    }

    @Test fun skipLastRestSwitch() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("skip_rest_0").performClick()
        assertTrue(vm.timer.blocks[0].skipLastRest)
        rule.onNodeWithTag("editor_total").assertTextEquals("Total 3:30")
    }

    @Test fun blockNameField() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("block_name_0").performTextInput("Main set")
        assertEquals("Main set", vm.timer.blocks[0].name)
    }

    @Test fun segmentNameAndTypeEditing() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("seg_name_0_0").performTextReplacement("Sprint")
        rule.onNodeWithTag("seg_type_0_0_COOLDOWN").performClick()
        assertEquals("Sprint", vm.timer.blocks[0].segments[0].name)
        assertEquals(SegmentType.COOLDOWN, vm.timer.blocks[0].segments[0].type)
    }

    @Test fun durationSteppersAndFields() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("seg_dur_0_0_inc").performClick()
        assertEquals(35, vm.timer.blocks[0].segments[0].durationSeconds)
        rule.onNodeWithTag("seg_dur_0_0_dec").performClick()
        rule.onNodeWithTag("seg_dur_0_0_dec").performClick()
        assertEquals(25, vm.timer.blocks[0].segments[0].durationSeconds)
        rule.onNodeWithTag("seg_dur_0_0_min").performTextReplacement("2")
        assertEquals(145, vm.timer.blocks[0].segments[0].durationSeconds)
        rule.onNodeWithTag("seg_dur_0_0_sec").performTextReplacement("0")
        assertEquals(120, vm.timer.blocks[0].segments[0].durationSeconds)
    }

    @Test fun addAndRemoveSegments() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("add_segment_0").performClick()
        assertEquals(3, vm.timer.blocks[0].segments.size)
        rule.onNodeWithTag("segment_0_2").assertIsDisplayed()
        rule.onNodeWithTag("seg_delete_0_2").performClick()
        assertEquals(2, vm.timer.blocks[0].segments.size)
        rule.onAllNodesWithTag("segment_0_2").assertCountEquals(0)
    }

    @Test fun moveSegmentButtons() {
        val vm = newVm()
        show(vm)
        rule.onNodeWithTag("seg_up_0_0").assertIsNotEnabled()
        rule.onNodeWithTag("seg_down_0_1").assertIsNotEnabled()
        rule.onNodeWithTag("seg_down_0_0").performClick()
        assertEquals(listOf("Rest", "Work"), vm.timer.blocks[0].segments.map { it.name })
        rule.onNodeWithTag("seg_up_0_1").performClick()
        assertEquals(listOf("Work", "Rest"), vm.timer.blocks[0].segments.map { it.name })
    }

    @Test fun addMoveAndRemoveBlocks() {
        val vm = newVm()
        vm.updateBlockName(0, "A")
        show(vm)
        rule.onNodeWithTag("editor_add_block").performClick()
        assertEquals(2, vm.timer.blocks.size)
        rule.onNodeWithTag("block_1").assertIsDisplayed()
        rule.onNodeWithTag("block_up_1").performClick()
        assertEquals("A", vm.timer.blocks[1].name)
        rule.onNodeWithTag("block_down_0").assertIsDisplayed()
        rule.onNodeWithTag("block_delete_1").performClick()
        assertEquals(1, vm.timer.blocks.size)
        rule.onAllNodesWithTag("block_1").assertCountEquals(0)
        rule.onNodeWithTag("block_up_0").assertIsNotEnabled()
        rule.onNodeWithTag("block_down_0").assertIsNotEnabled()
    }

    @Test fun saveWithBlankNameShowsInlineErrorAndDoesNotSave() {
        val repo = FakeTimerRepository()
        val vm = newVm(repo)
        show(vm)
        rule.onAllNodesWithTag("editor_name_error", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("editor_save").performClick()
        rule.onNodeWithTag("editor_name_error", useUnmergedTree = true).assertExists()
        assertTrue(repo.saved.isEmpty())
    }

    @Test fun saveShowsFieldErrorsForSegmentsAndBlocks() {
        val vm = newVm()
        vm.updateName("T")
        vm.updateSegmentDuration(0, 0, 0)
        vm.updateSegmentName(0, 1, "")
        vm.addBlock()
        vm.removeSegment(1, 0)
        show(vm)
        rule.onNodeWithTag("editor_save").performClick()
        rule.onNodeWithTag("seg_dur_error_0_0").assertIsDisplayed()
        rule.onNodeWithTag("seg_name_error_0_1", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("block_empty_error_1").assertIsDisplayed()
    }

    @Test fun saveWithNoBlocksShowsBanner() {
        val vm = newVm()
        vm.updateName("T")
        vm.removeBlock(0)
        show(vm)
        rule.onNodeWithTag("editor_save").performClick()
        rule.onNodeWithTag("editor_no_blocks_error").assertIsDisplayed()
    }

    @Test fun successfulSavePersistsTimer() {
        val repo = FakeTimerRepository()
        val vm = newVm(repo)
        show(vm)
        rule.onNodeWithTag("editor_name").performTextInput("Mine")
        rule.onNodeWithTag("editor_save").performClick()
        assertEquals(1, repo.saved.size)
        assertEquals("Mine", repo.current.single().name)
    }

    @Test fun saveFailureShowsBanner() {
        val repo = FakeTimerRepository().apply { failSave = IllegalStateException("x") }
        val vm = newVm(repo)
        vm.updateName("Mine")
        show(vm)
        rule.onNodeWithTag("editor_save").performClick()
        rule.onNodeWithTag("editor_save_error").assertIsDisplayed()
    }

    @Test fun backWithoutChangesLeavesImmediately() {
        show(newVm())
        rule.onNodeWithTag("editor_back").performClick()
        assertEquals(1, backCalls)
        rule.onAllNodesWithTag("discard_confirm").assertCountEquals(0)
    }

    @Test fun backWithChangesAsksToDiscard() {
        show(newVm())
        rule.onNodeWithTag("editor_name").performTextInput("x")
        rule.onNodeWithTag("editor_back").performClick()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals(0, backCalls)

        rule.onNodeWithTag("discard_cancel").performClick()
        rule.onAllNodesWithTag("discard_confirm").assertCountEquals(0)
        assertEquals(0, backCalls)

        rule.onNodeWithTag("editor_back").performClick()
        rule.onNodeWithTag("discard_confirm").performClick()
        assertEquals(1, backCalls)
    }
}

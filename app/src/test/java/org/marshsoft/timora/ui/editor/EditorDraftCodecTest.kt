package org.marshsoft.timora.ui.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.marshsoft.timora.domain.Block
import org.marshsoft.timora.domain.Segment
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.Timer

@RunWith(AndroidJUnit4::class)
class EditorDraftCodecTest {
    private val timer = Timer(
        id = 5, name = "HIIT \"quoted\" é\n", prepSeconds = 10, createdAt = 111L, updatedAt = 222L, colorIndex = 3,
        blocks = listOf(
            Block(-1, null, listOf(Segment(-2, "Work", 40, SegmentType.WORK), Segment(-3, "", 0, SegmentType.REST)), 8, true),
            Block(7, "Cool", listOf(Segment(9, "Cooldown", 120, SegmentType.COOLDOWN)), 1, false),
        ),
    )

    @Test fun roundTripsFullTimer() {
        assertEquals(timer, EditorDraftCodec.decode(EditorDraftCodec.encode(timer)))
    }

    @Test fun roundTripsEmptyBlocksAndNullBlockName() {
        val t = Timer(name = "", blocks = emptyList())
        assertEquals(t, EditorDraftCodec.decode(EditorDraftCodec.encode(t)))
        val b = Timer(name = "x", blocks = listOf(Block(1, null, emptyList())))
        assertEquals(b, EditorDraftCodec.decode(EditorDraftCodec.encode(b)))
    }

    @Test fun corruptOrMissingInputReturnsNull() {
        assertNull(EditorDraftCodec.decode(null))
        assertNull(EditorDraftCodec.decode(""))
        assertNull(EditorDraftCodec.decode("not json"))
        assertNull(EditorDraftCodec.decode("{}"))
        assertNull(EditorDraftCodec.decode(EditorDraftCodec.encode(timer).replace("COOLDOWN", "BOGUS")))
    }
}

package org.marshsoft.timora.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.marshsoft.timora.domain.Block
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.Timer
import org.marshsoft.timora.domain.ValidationError
import org.marshsoft.timora.domain.seg
import org.marshsoft.timora.domain.validate
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimerRepositoryTest {
    private lateinit var db: TimoraDatabase
    private lateinit var repo: TimerRepositoryImpl
    private var now = 1_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, TimoraDatabase::class.java).allowMainThreadQueries().build()
        repo = TimerRepositoryImpl(db.timerDao()) { now }
    }

    @After fun tearDown() = db.close()

    private fun sample(name: String = "HIIT") = Timer(
        name = name,
        prepSeconds = 10,
        colorIndex = 3,
        blocks = listOf(
            Block(name = "Warm", segments = listOf(seg("Jog", 60, SegmentType.PREP))),
            Block(
                name = null,
                cycles = 8,
                skipLastRest = true,
                segments = listOf(seg("Work", 40), seg("Rest", 20, SegmentType.REST), seg("Cool", 5, SegmentType.COOLDOWN)),
            ),
        ),
    )

    @Test fun saveAndGetRoundTrip() = runBlocking {
        val id = repo.save(sample())
        assertTrue(id > 0)
        val loaded = repo.get(id)!!
        val expected = sample()
        assertEquals(expected.name, loaded.name)
        assertEquals(10, loaded.prepSeconds)
        assertEquals(3, loaded.colorIndex)
        assertEquals(1_000L, loaded.createdAt)
        assertEquals(1_000L, loaded.updatedAt)
        assertEquals(2, loaded.blocks.size)
        assertEquals("Warm", loaded.blocks[0].name)
        assertNull(loaded.blocks[1].name)
        assertEquals(8, loaded.blocks[1].cycles)
        assertTrue(loaded.blocks[1].skipLastRest)
        assertEquals(listOf("Work", "Rest", "Cool"), loaded.blocks[1].segments.map { it.name })
        assertEquals(
            listOf(SegmentType.WORK, SegmentType.REST, SegmentType.COOLDOWN),
            loaded.blocks[1].segments.map { it.type },
        )
        assertEquals(listOf(40, 20, 5), loaded.blocks[1].segments.map { it.durationSeconds })
    }

    @Test fun getMissingReturnsNull() = runBlocking { assertNull(repo.get(999)) }

    @Test fun saveInvalidThrows() = runBlocking {
        try {
            repo.save(sample(name = " "))
            fail("expected exception")
        } catch (e: InvalidTimerException) {
            assertEquals(listOf(ValidationError.BlankName), e.errors)
        }
        assertEquals(0, db.timerDao().count())
    }

    @Test fun updateReplacesChildrenAndKeepsCreatedAt() = runBlocking {
        val id = repo.save(sample())
        now = 5_000
        val loaded = repo.get(id)!!
        val updated = loaded.copy(
            name = "Renamed",
            blocks = listOf(loaded.blocks[1].copy(segments = listOf(seg("Only", 9)), cycles = 2)),
        )
        assertEquals(id, repo.save(updated))
        val again = repo.get(id)!!
        assertEquals("Renamed", again.name)
        assertEquals(1, again.blocks.size)
        assertEquals(listOf("Only"), again.blocks[0].segments.map { it.name })
        assertEquals(1_000L, again.createdAt)
        assertEquals(5_000L, again.updatedAt)
        assertEquals(1, db.timerDao().count())
    }

    @Test fun saveWithUnknownIdInsertsNew() = runBlocking {
        val id = repo.save(sample().copy(id = 777))
        assertNotEquals(777L, id)
        assertNotNull(repo.get(id))
    }

    @Test fun deleteCascades() = runBlocking {
        val id = repo.save(sample())
        repo.delete(id)
        assertNull(repo.get(id))
        assertEquals(0, db.query("SELECT * FROM blocks", null).count)
        assertEquals(0, db.query("SELECT * FROM segments", null).count)
    }

    @Test fun duplicateCopiesWithNewIdAndName() = runBlocking {
        val id = repo.save(sample("Orig"))
        now = 9_000
        val copyId = repo.duplicate(id)!!
        assertNotEquals(id, copyId)
        val copy = repo.get(copyId)!!
        assertEquals("Orig (copy)", copy.name)
        assertEquals(9_000L, copy.createdAt)
        assertEquals(repo.get(id)!!.blocks.map { it.segments.map { s -> s.name } }, copy.blocks.map { it.segments.map { s -> s.name } })
        assertEquals(2, db.timerDao().count())
    }

    @Test fun duplicateMissingReturnsNull() = runBlocking { assertNull(repo.duplicate(42)) }

    @Test fun observeAllOrderedByUpdatedDescAndReactive() = runBlocking {
        repo.observeAll().test {
            assertEquals(emptyList<Timer>(), awaitItem())
            now = 1; val a = repo.save(sample("A"))
            assertEquals(listOf("A"), awaitItem().map { it.name })
            now = 2; repo.save(sample("B"))
            var names = awaitItem().map { it.name }
            while (names.size < 2) names = awaitItem().map { it.name }
            assertEquals(listOf("B", "A"), names)
            now = 3
            repo.save(repo.get(a)!!.copy(name = "A2"))
            names = awaitItem().map { it.name }
            while (names != listOf("A2", "B")) names = awaitItem().map { it.name }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observeSingle() = runBlocking {
        val id = repo.save(sample())
        assertEquals("HIIT", repo.observe(id).first()!!.name)
        repo.delete(id)
        assertNull(repo.observe(id).first())
    }

    @Test fun unknownSegmentTypeFallsBackToWork() {
        val e = SegmentEntity(1, 1, 0, "x", 5, "BOGUS")
        assertEquals(SegmentType.WORK, e.toDomain().type)
    }

    @Test fun sampleTimersAreValid() {
        SampleTimers.all(0).forEach {
            assertEquals(emptyList<ValidationError>(), it.validate())
        }
        assertEquals(listOf("Pomodoro", "HIIT 20/10 Tabata"), SampleTimers.all(100).map { it.name })
    }
}

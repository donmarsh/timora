package org.marshsoft.timora.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.marshsoft.timora.domain.Timer
import org.marshsoft.timora.domain.ValidationError
import org.marshsoft.timora.domain.validate

interface TimerRepository {
    fun observeAll(): Flow<List<Timer>>
    fun observe(id: Long): Flow<Timer?>
    suspend fun get(id: Long): Timer?

    /** Inserts (id == 0) or updates. Returns the saved id. Throws [InvalidTimerException] if invalid. */
    suspend fun save(timer: Timer): Long
    suspend fun delete(id: Long)

    /** Copies the timer as "<name> (copy)". Returns the new id or null if [id] does not exist. */
    suspend fun duplicate(id: Long): Long?
}

class InvalidTimerException(val errors: List<ValidationError>) :
    IllegalArgumentException("Invalid timer: $errors")

class TimerRepositoryImpl(
    private val dao: TimerDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : TimerRepository {
    override fun observeAll(): Flow<List<Timer>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Timer?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun get(id: Long): Timer? = dao.get(id)?.toDomain()

    override suspend fun save(timer: Timer): Long {
        val errors = timer.validate()
        if (errors.isNotEmpty()) throw InvalidTimerException(errors)
        val now = clock()
        val existingCreated = if (timer.id != 0L) dao.get(timer.id)?.timer?.createdAt else null
        val toSave = timer.copy(
            createdAt = existingCreated ?: timer.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
        )
        return dao.upsert(toSave)
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun duplicate(id: Long): Long? {
        val original = get(id) ?: return null
        val now = clock()
        return dao.upsert(original.copy(id = 0, name = "${original.name} (copy)", createdAt = now, updatedAt = now))
    }
}

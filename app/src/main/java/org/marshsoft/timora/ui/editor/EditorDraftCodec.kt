package org.marshsoft.timora.ui.editor

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.marshsoft.timora.domain.Block
import org.marshsoft.timora.domain.Segment
import org.marshsoft.timora.domain.SegmentType
import org.marshsoft.timora.domain.Timer

/**
 * Compact hand-rolled JSON encoding of a [Timer] draft, used to keep the editor draft in a
 * `SavedStateHandle` across process death. Decoding never throws: corrupt input returns null.
 */
object EditorDraftCodec {

    fun encode(timer: Timer): String = JSONObject().apply {
        put("i", timer.id)
        put("n", timer.name)
        put("p", timer.prepSeconds)
        put("c", timer.createdAt)
        put("u", timer.updatedAt)
        put("k", timer.colorIndex)
        put("b", JSONArray().apply { timer.blocks.forEach { put(encodeBlock(it)) } })
    }.toString()

    fun decode(text: String?): Timer? {
        if (text.isNullOrEmpty()) return null
        return try {
            val o = JSONObject(text)
            val blocks = o.getJSONArray("b")
            Timer(
                id = o.getLong("i"),
                name = o.getString("n"),
                prepSeconds = o.getInt("p"),
                createdAt = o.getLong("c"),
                updatedAt = o.getLong("u"),
                colorIndex = o.getInt("k"),
                blocks = List(blocks.length()) { decodeBlock(blocks.getJSONObject(it)) },
            )
        } catch (e: JSONException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun encodeBlock(b: Block) = JSONObject().apply {
        put("i", b.id)
        if (b.name != null) put("n", b.name)
        put("c", b.cycles)
        put("r", b.skipLastRest)
        put("s", JSONArray().apply { b.segments.forEach { put(encodeSegment(it)) } })
    }

    private fun decodeBlock(o: JSONObject): Block {
        val segs = o.getJSONArray("s")
        return Block(
            id = o.getLong("i"),
            name = if (o.has("n")) o.getString("n") else null,
            cycles = o.getInt("c"),
            skipLastRest = o.getBoolean("r"),
            segments = List(segs.length()) { decodeSegment(segs.getJSONObject(it)) },
        )
    }

    private fun encodeSegment(s: Segment) = JSONObject().apply {
        put("i", s.id)
        put("n", s.name)
        put("d", s.durationSeconds)
        put("t", s.type.name)
    }

    private fun decodeSegment(o: JSONObject) = Segment(
        id = o.getLong("i"),
        name = o.getString("n"),
        durationSeconds = o.getInt("d"),
        type = SegmentType.valueOf(o.getString("t")),
    )
}

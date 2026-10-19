package org.marshsoft.timora.domain

fun seg(name: String, sec: Int, type: SegmentType = SegmentType.WORK) = Segment(0, name, sec, type)

fun timer(
    blocks: List<Block>,
    prep: Int = 0,
    name: String = "T",
) = Timer(name = name, blocks = blocks, prepSeconds = prep)

fun workRest(work: Int = 40, rest: Int = 20, cycles: Int = 2, skip: Boolean = false) = Block(
    segments = listOf(seg("Work", work), seg("Rest", rest, SegmentType.REST)),
    cycles = cycles,
    skipLastRest = skip,
)

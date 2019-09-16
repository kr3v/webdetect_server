package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.graph.AVGE
import com.cloudlinux.webdetect.graph.CGE
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.time.ZonedDateTime


fun buildContextByCsv(pathToCsv: String): WebdetectContext {
    val webdetectCtx = WebdetectContext(pathToCsv)
    println("${ZonedDateTime.now()}: $pathToCsv processing started")
    read(
        path = pathToCsv,
        separator = "\t",
        handler = { list ->
            webdetectCtx.doPooling(list[0], list[1], list[2], list.getOrNull(3)?.toInt(), list.getOrNull(4))
        }
    )
    println("${ZonedDateTime.now()}: $pathToCsv processing done")
    webdetectCtx.pool.cleanup()
    return webdetectCtx
}

fun buildDepths(
    `in`: String,
    avDict: Map<AppVersion.Single, AVGE>,
    csDict: Map<Checksum, CGE>
): Map<CGE, IntOpenHashSet> {
    println("${ZonedDateTime.now()}: looking for checksums depths started")
    val result = FMutableMap<CGE, IntOpenHashSet>()
    read(
        path = `in`,
        separator = "\t",
        handler = { list ->
            val app = list[0]
            val version = list[1]
            val checksum = list[2]
            val depth = list.getOrNull(3)?.toInt()
            if (depth != null) {
                val av = avDict[AppVersion.Single(app, version)] ?: error("should not happen")
                val cs = csDict[ChecksumLong(checksum)]
                if (cs in av.checksums) {
                    result.computeIfAbsent(cs) { IntMutableSet() }.add(depth)
                }
            }
        }
    )
    println("${ZonedDateTime.now()}: looking for checksums depths done")
    return result
}

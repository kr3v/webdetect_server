package kr3v.webdetect.graph.pq

import kr3v.webdetect.AppVersion
import kr3v.webdetect.FMutableMap
import kr3v.webdetect.graph.AppVersionGraphEntry
import kr3v.webdetect.graph.HasIntProperties
import java.time.ZonedDateTime
import java.util.Comparator

var HasIntProperties.exclusiveChecksums
    get() = properties[0]
    set(value) {
        properties[0] = value
    }

var HasIntProperties.pqIndex
    get() = properties[1]
    set(value) {
        properties[1] = value
    }

class PriorityQueueBasedSolution private constructor(
    private val queue: PriorityQueue<AppVersionGraphEntry>
) {
    companion object {
        private fun cmp() = Comparator
            .comparingInt<AppVersionGraphEntry> { it.exclusiveChecksums }
            .thenComparingInt { it.checksums.size }
    }

    constructor(avDict: FMutableMap<AppVersion, AppVersionGraphEntry>) : this(
        PriorityQueue(
            avDict.values.onEach {
                it.properties = IntArray(2)
                it.exclusiveChecksums = it.checksums.sumBy { cs -> if (cs.appVersions.size == 1) 1 else 0 }
            },
            cmp()
        )
    )

    private fun removeNonExclusiveChecksums(av: AppVersionGraphEntry) {
        val it = av.checksums.iterator()
        while (it.hasNext()) {
            val cs = it.next()
            if (cs.appVersions.size == 1) continue

            it.remove()
            av.released += cs
            cs.dependsOn += av
            cs.appVersions -= av
            for (adjacentAv in cs.appVersions) {
                adjacentAv.exclusiveChecksums += 1
                queue.update(adjacentAv)
            }
        }
    }

    fun process() {
        println("${ZonedDateTime.now()}: priority queue based solution started")
        while (queue.isNotEmpty()) {
            removeNonExclusiveChecksums(queue.pop())
        }
        println("${ZonedDateTime.now()}: priority queue based solution done")
    }
}

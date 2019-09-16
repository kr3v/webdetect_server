package com.cloudlinux.webdetect.graph.pq

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.HasIntProperties
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
        val iterator = av.checksums.iterator()
        while (iterator.hasNext()) {
            val checksum = iterator.next()
            if (checksum.appVersions.size == 1) continue

            iterator.remove()
            av.released += checksum
            checksum.dependsOn.add(av)
            checksum.appVersions.remove(av)
            for (adjacentAppVersion in checksum.appVersions) {
                adjacentAppVersion.exclusiveChecksums += 1
                queue.update(adjacentAppVersion)
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

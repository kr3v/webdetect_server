package com.cloudlinux.webdetect.graph.pq

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry

class PriorityQueueBasedSolution internal constructor(
    private val queue: PriorityQueue<AppVersionGraphEntry>
) {

    constructor(avDict: MutableMap<AppVersion, AppVersionGraphEntry>)
        : this(PriorityQueue(avDict.values) { it.checksums.sumBy { cs -> if (cs.appVersions.size == 1) 1 else 0 } })

    private fun removeNonExclusiveChecksums(av: AppVersionGraphEntry) {
        val iterator = av.checksums.iterator()
        while (iterator.hasNext()) {
            val checksum = iterator.next()
            if (checksum.appVersions.size == 1) continue

            iterator.remove()
            checksum.dependsOn.add(av)
            checksum.appVersions.remove(av)
            for (adjacentAppVersion in checksum.appVersions) {
                queue.inc(adjacentAppVersion, 1)
            }
        }
    }

    fun process() {
        while (queue.isNotEmpty()) {
            removeNonExclusiveChecksums(queue.pop().value)
        }
    }
}

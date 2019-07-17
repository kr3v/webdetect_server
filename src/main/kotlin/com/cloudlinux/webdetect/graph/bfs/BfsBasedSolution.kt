package com.cloudlinux.webdetect.graph.bfs

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.MutableLinkedSet
import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumGraphEntry

class BfsBasedSolution(
    private val avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    private val sufficientChecksumsRange: IntProgression,
    private val bfsQueue: MutableLinkedSet<AppVersionGraphEntry> = MutableLinkedSet(),
    private var sufficientChecksums: Int = sufficientChecksumsRange.first
) {

    init {
        for (av in avDict.values) {
            av.intField = av.checksums.sumBy { if (it.appVersions.size == 1) 1 else 0 }
        }
    }

    private fun isDefined(av: AppVersionGraphEntry) = av.intField >= sufficientChecksums

    private fun removeNonExclusiveChecksums(av: AppVersionGraphEntry) {
        val iterator = av.checksums.iterator()
        while (iterator.hasNext()) {
            val checksum = iterator.next()
            if (checksum.appVersions.size == 1) continue

            iterator.remove()
            checksum.dependsOn.add(av)
            checksum.appVersions.remove(av)
            updateQueue(checksum)
        }
    }

    private fun updateQueue(checksum: ChecksumGraphEntry) {
        for (adjacentAppVersion in checksum.appVersions) {
            if (checksum.appVersions.size == 1) {
                adjacentAppVersion.intField++
                if (adjacentAppVersion.intField == sufficientChecksums) {
                    bfsQueue.add(adjacentAppVersion)
                }
            }
        }
    }

    fun process(): MutableMap<AppVersion, AppVersionGraphEntry> {
        val result = MutableMap<AppVersion, AppVersionGraphEntry>()
        for (sufficientChecksums in sufficientChecksumsRange) {
            this.sufficientChecksums = sufficientChecksums
            avDict.values.filterTo(bfsQueue, ::isDefined)
            while (bfsQueue.isNotEmpty()) {
                val av = bfsQueue.removeFirst()
                if (isDefined(av) && result.put(av.key, av) == null) {
                    removeNonExclusiveChecksums(av)
                }
            }
        }
        return result
    }
}

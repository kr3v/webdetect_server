package com.cloudlinux.webdetect.graph.bfs

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableLinkedSet
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumKey
import com.cloudlinux.webdetect.graph.HasIntProperties

var HasIntProperties.exclusiveChecksums
    get() = properties[0]
    set(value) {
        properties[0] = value
    }

@Deprecated("Does not support [AVGE.released]")
class BfsBasedSolution<C : ChecksumKey<C>>(
    private val avDict: FMutableMap<AppVersion, AppVersionGraphEntry<C>>,
    private val sufficientChecksumsRange: IntProgression,
    private val bfsQueue: FMutableLinkedSet<AppVersionGraphEntry<C>> = FMutableLinkedSet(),
    private var sufficientChecksums: Int = sufficientChecksumsRange.first
) {

    init {
        for (av in avDict.values) {
            av.properties = intArrayOf(av.checksums.sumBy { if (it.appVersions.size == 1) 1 else 0 })
        }
    }

    private fun isDefined(av: AppVersionGraphEntry<C>) = av.exclusiveChecksums >= sufficientChecksums

    private fun removeNonExclusiveChecksums(av: AppVersionGraphEntry<C>) {
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

    private fun updateQueue(checksum: ChecksumGraphEntry<C>) {
        for (adjacentAppVersion in checksum.appVersions) {
            if (checksum.appVersions.size == 1) {
                adjacentAppVersion.exclusiveChecksums++
                if (adjacentAppVersion.exclusiveChecksums == sufficientChecksums) {
//                    if (adjacentAppVersion.key.versions().any { it.isReleaseVersion() }) {
                    bfsQueue.add(adjacentAppVersion)
//                    } else {
//                        bfsQueue.addAndMoveToFirst(adjacentAppVersion)
//                    }
                }
            }
        }
    }

    fun process(): FMutableMap<AppVersion, AppVersionGraphEntry<C>> {
        val result = FMutableMap<AppVersion, AppVersionGraphEntry<C>>()
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

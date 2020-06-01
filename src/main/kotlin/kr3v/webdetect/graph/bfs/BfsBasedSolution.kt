package kr3v.webdetect.graph.bfs

import kr3v.webdetect.AppVersion
import kr3v.webdetect.FMutableLinkedSet
import kr3v.webdetect.FMutableMap
import kr3v.webdetect.graph.AppVersionGraphEntry
import kr3v.webdetect.graph.ChecksumGraphEntry
import kr3v.webdetect.graph.HasIntProperties

var HasIntProperties.exclusiveChecksums: Int
    get() = properties[0]
    set(value) {
        properties[0] = value
    }

class BfsBasedSolution(
    private val avDict: FMutableMap<AppVersion, AppVersionGraphEntry>,
    private val sufficientChecksumsRange: IntProgression,
    private val bfsQueue: FMutableLinkedSet<AppVersionGraphEntry> = FMutableLinkedSet(),
    private var sufficientChecksums: Int = sufficientChecksumsRange.first
) {

    init {
        for (av in avDict.values) {
            av.properties = intArrayOf(av.checksums.sumBy { if (it.appVersions.size == 1) 1 else 0 })
        }
    }

    private fun isDefined(av: AppVersionGraphEntry) = av.exclusiveChecksums >= sufficientChecksums

    private fun removeNonExclusiveChecksums(av: AppVersionGraphEntry) {
        val iterator = av.checksums.iterator()
        while (iterator.hasNext()) {
            val checksum = iterator.next()
            if (checksum.appVersions.size == 1) continue

            iterator.remove()
            av.released += checksum
            checksum.dependsOn += av
            checksum.appVersions -= av
            updateQueue(checksum)
        }
    }

    private fun updateQueue(checksum: ChecksumGraphEntry) {
        for (adjacentAppVersion in checksum.appVersions) {
            if (checksum.appVersions.size == 1) {
                adjacentAppVersion.exclusiveChecksums++
                if (adjacentAppVersion.exclusiveChecksums == sufficientChecksums) {
                    bfsQueue += adjacentAppVersion
                }
            }
        }
    }

    fun process(): FMutableMap<AppVersion, AppVersionGraphEntry> {
        val result = FMutableMap<AppVersion, AppVersionGraphEntry>()
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

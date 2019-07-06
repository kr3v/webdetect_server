package com.cloudlinux.webdetect

class AppVersionGraphEntry(
    val key: AppVersion,
    val checksums: SortedSet<ChecksumGraphEntry>,
    override var queueIndex: Int
) : PriorityQueue.Indexable {
    override fun equals(other: Any?) = this === other || other is AppVersionGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key
}

class ChecksumGraphEntry(
    val key: Checksum,
    val appVersions: MutableSet<AppVersionGraphEntry>,
    val dependsOn: MutableSet<AppVersionGraphEntry>
) {
    override fun equals(other: Any?) = this === other || other is ChecksumGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key.toString()
}

class GraphTaskContext(
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
            checksum.dependsOn += av
            checksum.appVersions -= av
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

fun createGraph(
    checksumToAppVersion: Map<Checksum, Set<AppVersion>>,
    appVersions: Set<AppVersion>
): MutableMap<AppVersion, AppVersionGraphEntry> {
    val avDict = MutableMap<AppVersion, AppVersionGraphEntry>(appVersions.size, 1f)

    for (av in appVersions) {
        avDict[av] = AppVersionGraphEntry(av, SortedSet(), -1)
    }
    for ((cs, avs) in checksumToAppVersion) {
        val csEntry = ChecksumGraphEntry(cs, MutableSet(avs.size, 1f), MutableSet())
        for (av in avs) {
            val avEntry = avDict[av]!!
            csEntry.appVersions.add(avEntry)
            avEntry.checksums.add(csEntry)
        }
    }
    return avDict
}
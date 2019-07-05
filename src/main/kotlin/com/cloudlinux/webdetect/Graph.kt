package com.cloudlinux.webdetect

class AppVersionGraphEntry(
    val key: AppVersion,
    val checksums: SortedSet<ChecksumGraphEntry>,
    var exclusiveChecksums: Int = -1
) {
    override fun equals(other: Any?) = this === other || other is AppVersionGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key
}

class ChecksumGraphEntry(
    val key: Checksum,
    val appVersions: MutableSet<AppVersionGraphEntry>,
    val dependsOn: MutableSet<AppVersionGraphEntry>
) : Comparable<ChecksumGraphEntry> {
    override fun compareTo(other: ChecksumGraphEntry) = key.compareTo(other.key)
    override fun equals(other: Any?) = this === other || other is ChecksumGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key.toString()
}

class GraphTaskContext(
    private val sufficientChecksums: Int
) {

    fun isDefined(av: AppVersionGraphEntry) = av.exclusiveChecksums >= sufficientChecksums

    fun removeNonExclusiveChecksums(av: AppVersionGraphEntry): Set<AppVersionGraphEntry> {
        val result = MutableSet<AppVersionGraphEntry>()
        val iterator = av.checksums.iterator()
        while (iterator.hasNext()) {
            val checksum = iterator.next()
            if (checksum.appVersions.size == 1) continue

            iterator.remove()
            checksum.dependsOn += av
            checksum.appVersions -= av
            updateQueue(checksum, result)
        }
        return result
    }

    private fun updateQueue(
        checksum: ChecksumGraphEntry,
        result: MutableSet<AppVersionGraphEntry>
    ) {
        for (adjacentAppVersion in checksum.appVersions) {
            if (checksum.appVersions.size == 1) {
                adjacentAppVersion.exclusiveChecksums++
                if (adjacentAppVersion.exclusiveChecksums == sufficientChecksums) {
                    result += adjacentAppVersion
                }
            }
        }
    }
}

fun createGraph(
    checksumToAppVersion: Map<Checksum, Set<AppVersion>>,
    appVersions: Set<AppVersion>
): MutableMap<AppVersion, AppVersionGraphEntry> {
    val avDict = MutableMap<AppVersion, AppVersionGraphEntry>(appVersions.size, 1f)

    for (av in appVersions) {
        avDict[av] = AppVersionGraphEntry(av, SortedSet())
    }
    for ((cs, avs) in checksumToAppVersion) {
        val csEntry = ChecksumGraphEntry(cs, MutableSet(avs.size, 1f), MutableSet())
        for (av in avs) {
            val avEntry = avDict[av]!!
            csEntry.appVersions.add(avEntry)
            avEntry.checksums.add(csEntry)
        }
    }
    for (av in avDict.values) {
        av.exclusiveChecksums = av.checksums.sumBy { if (it.appVersions.size == 1) 1 else 0 }
    }

    return avDict
}

fun findDefinedAppVersions(
    avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    sufficientChecksums: Int
): MutableMap<AppVersion, AppVersionGraphEntry> {
    val ctx = GraphTaskContext(sufficientChecksums)
    val definedAppVersions = MutableMap<AppVersion, AppVersionGraphEntry>()

    val bfsQueue = avDict.values.filterTo(MutableLinkedSet<AppVersionGraphEntry>(), ctx::isDefined)
    while (bfsQueue.isNotEmpty()) {
        val av = bfsQueue.removeFirst()
        if (ctx.isDefined(av) && definedAppVersions.put(av.key, av) == null) {
            bfsQueue.addAll(ctx.removeNonExclusiveChecksums(av))
        }
    }

    return definedAppVersions
}
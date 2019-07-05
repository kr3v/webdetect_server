package com.cloudlinux.webdetect

class AppVersionGraphEntry(
    val key: AppVersion,
    val checksums: SortedSet<ChecksumGraphEntry>,
    var exclusiveChecksums: Int = -1
) {
    override fun equals(other: Any?) = this === other || other is AppVersionGraphEntry && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString() = key

    fun updateExclusiveChecksums() {
        exclusiveChecksums = checksums.sumBy { if (it.appVersions.size == 1) 1 else 0 }
    }

    fun isDefined(ctx: GraphTaskContext) = exclusiveChecksums >= ctx.sufficientChecksums

    fun removeNonExclusiveChecksums(ctx: GraphTaskContext): Set<AppVersionGraphEntry> {
        val result = MutableSet<AppVersionGraphEntry>()
        val iterator = checksums.iterator()
        while (iterator.hasNext()) {
            val checksum = iterator.next()
            if (checksum.appVersions.size == 1) continue

            iterator.remove()
            checksum.dependsOn += this
            checksum.appVersions -= this
            for (adjacentAppVersion in checksum.appVersions) {
                if (checksum.appVersions.size == 1) {
                    adjacentAppVersion.exclusiveChecksums++
                    if (adjacentAppVersion.exclusiveChecksums == ctx.sufficientChecksums) {
                        result += adjacentAppVersion
                    }
                }
            }
        }
        return result
    }
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

data class GraphTaskContext(
    val sufficientChecksums: Int
)

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
    for (it in avDict.values) {
        it.updateExclusiveChecksums()
    }

    return avDict
}

fun findDefinedAppVersions(
    avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    sufficientChecksums: Int
): MutableMap<AppVersion, AppVersionGraphEntry> {
    val ctx = GraphTaskContext(sufficientChecksums)
    val definedAppVersions = MutableMap<AppVersion, AppVersionGraphEntry>()

    val bfsQueue = avDict.values.filterTo(MutableLinkedSet<AppVersionGraphEntry>()) { it.isDefined(ctx) }
    while (bfsQueue.isNotEmpty()) {
        val av = bfsQueue.removeFirst()
        if (av.isDefined(ctx) && definedAppVersions.put(av.key, av) == null) {
            bfsQueue.addAll(av.removeNonExclusiveChecksums(ctx))
        }
    }

    return definedAppVersions
}
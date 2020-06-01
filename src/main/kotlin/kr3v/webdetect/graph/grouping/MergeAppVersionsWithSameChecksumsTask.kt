package kr3v.webdetect.graph.grouping

import kr3v.webdetect.AppVersion
import kr3v.webdetect.FMutableMap
import kr3v.webdetect.FMutableSet
import kr3v.webdetect.graph.AppVersionGraphEntry
import kr3v.webdetect.graph.ChecksumGraphEntry
import java.time.ZonedDateTime

/**
 * If two or more [AppVersion.Single] have same list of [AppVersionGraphEntry.checksums] in appropriate [AppVersionGraphEntry],
 * then those app-versions are merged into single [AppVersion.Merged] entry in this task.
 */
class MergeAppVersionsWithSameChecksumsTask(
    private val avDict: FMutableMap<AppVersion, AppVersionGraphEntry>
) {
    fun process() {
        println("${ZonedDateTime.now()}: merging same app-versions started")
        val toBeMerged = avDict.values
            .groupingBy { it.checksums }
            .foldTo(
                FMutableMap<FMutableSet<ChecksumGraphEntry>, FMutableSet<AppVersionGraphEntry>>(),
                initialValueSelector = { _, v -> FMutableSet<AppVersionGraphEntry>().also { it.add(v) } },
                operation = { cs, acc, v ->
                    if (v.checksums == cs) acc.add(v)
                    acc
                }
            )
            .filter { (_, v) -> v.size > 1 }
        println("${ZonedDateTime.now()}: merging ${toBeMerged.size} app-versions in total")
        toBeMerged.forEach { (cs, av) -> merge(cs, av) }
        println("${ZonedDateTime.now()}: merging same app-versions done")
    }

    private fun merge(
        checksums: FMutableSet<ChecksumGraphEntry>,
        appVersionsToBeMerged: FMutableSet<AppVersionGraphEntry>
    ) {
        if (appVersionsToBeMerged.size == 1) return
        if (checksums.size == 0) return
        val appVersions = excludeTrunkIfPossible(appVersionsToBeMerged)
        val mergedAv = when (appVersions.size) {
            1 -> appVersions.single()
            else -> AppVersion.Merged(appVersions)
        }
        val mergedAvGraphEntry = AppVersionGraphEntry(mergedAv, checksums)
        for (av in appVersionsToBeMerged) {
            avDict.remove(av.key)
        }
        for (cs in checksums) {
            cs.appVersions.removeAll(appVersionsToBeMerged)
            cs.appVersions.add(mergedAvGraphEntry)
        }
        avDict[mergedAv] = mergedAvGraphEntry
    }

    private fun excludeTrunkIfPossible(appVersions: Set<AppVersionGraphEntry>): FMutableSet<AppVersion> {
        val apps = appVersions.flatMapTo(FMutableSet()) { it.key.apps() }
        val simplified = when (apps.size) {
            1 -> appVersions.filterNot { it.key.versions().single() == TRUNK }
            else -> appVersions
        }
        return simplified.mapTo(FMutableSet()) { it.key }
    }

    companion object {
        const val TRUNK = "trunk"
    }
}

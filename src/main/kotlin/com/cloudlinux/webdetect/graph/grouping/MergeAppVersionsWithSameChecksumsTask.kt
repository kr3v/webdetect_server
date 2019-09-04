package com.cloudlinux.webdetect.graph.grouping

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.FMutableSet
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumKey
import java.time.ZonedDateTime

/**
 * If two or more [AppVersion.Single] and [AppVersionGraphEntry] have same [AppVersionGraphEntry.checksums],
 * then those app-versions are merged into single [AppVersion.Merged] entry in this task.
 */
class MergeAppVersionsWithSameChecksumsTask<C : ChecksumKey<C>>(
    private val avDict: FMutableMap<AppVersion, AppVersionGraphEntry<C>>
) {
    fun process() {
        println("${ZonedDateTime.now()}: merging same app-versions started")
        val toBeMerged = avDict.values
            .groupingBy { it.checksums }
            .foldTo(
                FMutableMap<FMutableSet<ChecksumGraphEntry<C>>, FMutableSet<AppVersionGraphEntry<C>>>(),
                initialValueSelector = { _, v -> FMutableSet<AppVersionGraphEntry<C>>().also { it.add(v) } },
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
        checksums: FMutableSet<ChecksumGraphEntry<C>>,
        appVersionsToBeMerged: FMutableSet<AppVersionGraphEntry<C>>
    ) {
        if (appVersionsToBeMerged.size == 1) return
        if (checksums.size == 0) return
        val mergedAv = AppVersion.Merged(appVersionsToBeMerged.mapTo(FMutableSet()) { it.key })
        val mergedAvGraphEntry = AppVersionGraphEntry<C>(mergedAv, checksums)
        for (av in appVersionsToBeMerged) {
            avDict.remove(av.key)
        }
        for (cs in checksums) {
            cs.appVersions.removeAll(appVersionsToBeMerged)
            cs.appVersions.add(mergedAvGraphEntry)
        }
        avDict[mergedAv] = mergedAvGraphEntry
    }
}

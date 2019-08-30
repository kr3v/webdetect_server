package com.cloudlinux.webdetect.graph.grouping

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.FMutableSet
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import java.time.ZonedDateTime

/**
 * If app-version A fully contains app-version B (all checksums of B are present in A), then A can imply present of B.
 * As all B checksums depend on A, we'd discard B if A is present, but we can avoid this by finding all
 * such 'B' app-version for all app-versions.
 *
 * Note: should run after [MergeAppVersionsWithSameChecksumsTask].
 */
class FindAppVersionsInclusionsTask(
    private val avDict: FMutableMap<AppVersion, AppVersionGraphEntry>
) {
    fun process() {
        println("${ZonedDateTime.now()}: lookup for app-versions inclusions started")
        for ((_, av) in avDict) {
            val checksums = av.checksums
            if (checksums.size == 0) continue
            val ms =
                FMutableSet<AppVersionGraphEntry>(checksums.first().appVersions)
            for (cs in checksums.asSequence().drop(1)) {
                ms.removeIf { avv -> avv !in cs.appVersions }
            }
            ms -= av
            for (implyingAv in ms) {
                implyingAv.implies.add(av)
            }
        }
        println("${ZonedDateTime.now()}: lookup for app-versions inclusions done")
    }
}

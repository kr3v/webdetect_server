package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.FMutableSet

/**
 * Creates [AppVersionGraphEntry] and [ChecksumGraphEntry<Checksum>] objects, which keep references on each over in [FMutableSet].
 * This creates kind of graph relation instead of linkage by [checksumToAppVersion]-like maps (which improves performance and usability).
 */
fun <C : ChecksumKey<C>> createGraph(
    checksumToAppVersion: Map<C, Set<AppVersion>>,
    appVersionsToChecksums: FMutableMap<AppVersion, FMutableSet<C>>
): Pair<FMutableMap<AppVersion, AppVersionGraphEntry<C>>, FMutableMap<C, ChecksumGraphEntry<C>>> {
    val avDict = FMutableMap<AppVersion, AppVersionGraphEntry<C>>(appVersionsToChecksums.size, 0.5f)
    val csDict = FMutableMap<C, ChecksumGraphEntry<C>>(checksumToAppVersion.size, 0.5f)

    for ((appVersion, checksums) in appVersionsToChecksums) {
        avDict[appVersion] = AppVersionGraphEntry<C>(appVersion, FMutableSet(checksums.size, 0.5f))
    }
    for ((checksum, appVersions) in checksumToAppVersion) {
        val csEntry = ChecksumGraphEntry(checksum, FMutableSet(appVersions.size, 0.5f))
        csDict[checksum] = csEntry
        for (av in appVersions) {
            val avEntry = avDict[av]!!
            csEntry.appVersions.add(avEntry)
            avEntry.checksums.add(csEntry)
        }
    }
    return avDict to csDict
}

package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.FMutableSet

/**
 * Creates [AppVersionGraphEntry] and [ChecksumGraphEntry] objects, which keep references on each over in [FMutableSet].
 * This creates kind of graph relation instead of linkage by [checksumToAppVersion]-like maps (which improves performance and usability).
 */
fun createGraph(
    checksumToAppVersion: Map<Checksum, Set<AppVersion>>,
    appVersionsToChecksums: FMutableMap<AppVersion, FMutableSet<Checksum>>
): Pair<FMutableMap<AppVersion, AppVersionGraphEntry>, FMutableMap<Checksum, ChecksumGraphEntry>> {
    val avDict = FMutableMap<AppVersion, AppVersionGraphEntry>(appVersionsToChecksums.size, 0.5f)
    val csDict = FMutableMap<Checksum, ChecksumGraphEntry>(checksumToAppVersion.size, 0.5f)

    for ((appVersion, checksums) in appVersionsToChecksums) {
        avDict[appVersion] = AppVersionGraphEntry(appVersion, FMutableSet(checksums.size, 0.5f))
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

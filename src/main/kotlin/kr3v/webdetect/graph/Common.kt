package kr3v.webdetect.graph

import kr3v.webdetect.AppVersion
import kr3v.webdetect.Checksum
import kr3v.webdetect.FMutableMap
import kr3v.webdetect.FMutableSet
import java.time.ZonedDateTime

/**
 * Creates [AppVersionGraphEntry] and [ChecksumGraphEntry<Checksum>] objects, which keep references on each over in [FMutableSet].
 * This creates kind of graph relation instead of linkage by [checksumToAppVersion]-like maps (which improves performance and usability).
 */
fun createGraph(
    checksumToAppVersion: Map<Checksum, Set<AppVersion>>,
    appVersionsToChecksums: FMutableMap<AppVersion, FMutableSet<Checksum>>
): Pair<FMutableMap<AppVersion, AppVersionGraphEntry>, FMutableMap<Checksum, ChecksumGraphEntry>> {
    println("${ZonedDateTime.now()}: creating graph started")
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
    println("${ZonedDateTime.now()}: creating graph done")
    return avDict to csDict
}

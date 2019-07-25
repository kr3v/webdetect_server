package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.MutableSet

fun createGraph(
    checksumToAppVersion: Map<Checksum, Set<AppVersion>>,
    appVersions: Set<AppVersion>
): MutableMap<AppVersion, AppVersionGraphEntry> {
    val avDict = MutableMap<AppVersion, AppVersionGraphEntry>(appVersions.size, 1f)

    for (av in appVersions) {
        avDict[av] = AppVersionGraphEntry(av, MutableSet())
    }
    for ((cs, avs) in checksumToAppVersion) {
        val csEntry = ChecksumGraphEntry(
            cs,
            MutableSet(avs.size, 1f),
            MutableSet()
        )
        for (av in avs) {
            val avEntry = avDict[av]!!
            csEntry.appVersions.add(avEntry)
            avEntry.checksums.add(csEntry)
        }
    }
    return avDict
}

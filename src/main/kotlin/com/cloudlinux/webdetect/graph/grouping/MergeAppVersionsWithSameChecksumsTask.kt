package com.cloudlinux.webdetect.graph.grouping

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.MutableSet
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumGraphEntry

class MergeAppVersionsWithSameChecksumsTask(
    private val avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    private val definedAvDict: MutableMap<AppVersion, AppVersionGraphEntry>
) {

    fun process() {
        (avDict.keys - definedAvDict.keys)
            .groupBy { avDict[it]!!.checksums }
            .mapValues { (_, v) -> v.mapTo(MutableSet()) { avDict[it]!! } }
            .filter { (v, k) -> k.all { a -> a.checksums == v } }
            .forEach { (v, k) -> merge(k, v) }
    }

    private fun merge(
        appVersionsToBeMerged: MutableSet<AppVersionGraphEntry>,
        checksums: MutableSet<ChecksumGraphEntry>
    ) {
        if (appVersionsToBeMerged.size == 1) return
        val mergedAv = AppVersion.Merged(appVersionsToBeMerged.mapTo(MutableSet()) { it.key })
        val mergedAvGraphEntry = AppVersionGraphEntry(mergedAv, checksums)
        for (av in appVersionsToBeMerged.toList()) { // todo: should we do a copy?
            avDict.remove(av.key)
        }
        for (cs in checksums) {
            cs.appVersions.removeAll(appVersionsToBeMerged)
            cs.appVersions.add(mergedAvGraphEntry)
        }
        avDict[mergedAv] = mergedAvGraphEntry
    }
}

package com.cloudlinux.webdetect.graph.grouping

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.IAppVersion
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumGraphEntry
import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.MutableSet

class MergeSameAppVersionsTask(
//    private val csDict: MutableMap<Checksum, ChecksumGraphEntry>,
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
        val mergedAv = IAppVersion.Merged(appVersionsToBeMerged.mapTo(MutableSet()) { it.key })
        val mergedAvGraphEntry = AppVersionGraphEntry(mergedAv, checksums)
        for (av in appVersionsToBeMerged.toList()) {
            avDict.remove(av.key)
        }
        for (cs in checksums) {
            cs.appVersions.removeAll(appVersionsToBeMerged)
            cs.appVersions.add(mergedAvGraphEntry)
        }
        avDict[mergedAv] = mergedAvGraphEntry
    }
}

//private fun findGroups(csSetToAvs: MutableMap<Set<AppVersionGraphEntry>, MutableSet<ChecksumGraphEntry>>): Sequence<Map<AppVersionGraphEntry, List<ChecksumGraphEntry>>> {
//    val csSetToAvs = MutableMap<MutableSet<AppVersionGraphEntry>, MutableSet<ChecksumGraphEntry>>()
//    for ((_, v) in csDict) {
//        if (v.appVersions.size != 1 || v.appVersions.single().key !in definedAvDict) {
//            csSetToAvs.computeIfAbsent(v.appVersions) { MutableSet() }.add(v)
//        }
//    }
//    return csSetToAvs
//        .entries
//        .asSequence()
//        .sortedBy { (_, v) -> v.size }
//        .onEach { (k, v) -> println("${k.size}, ${v.size}  -> ${k.map { it.key }}") }
//        .map { (avs, _) ->
//            avs.associateWith { it.checksums.filter { cs -> exactlyOneIntersect(cs.appVersions, avs) } }
//        }
//        .onEach { v ->
//            v.forEach { (av, cs) ->
//                println("\t${av.key}: ${cs.size}")
//            }
//        }
//}
//
//private fun exactlyOneIntersect(lhs: Set<AppVersionGraphEntry>, rhs: Set<AppVersionGraphEntry>) =
//    lhs.size > rhs.size && exactlyOneIntersectImpl(rhs, lhs) || exactlyOneIntersectImpl(lhs, rhs)
//
//private fun exactlyOneIntersectImpl(
//    lhs: Set<AppVersionGraphEntry>,
//    rhs: Set<AppVersionGraphEntry>
//): Boolean {
//    var found = false
//    for (l in lhs)
//        if (l in rhs)
//            if (found) return false
//            else found = true
//    return found
//}

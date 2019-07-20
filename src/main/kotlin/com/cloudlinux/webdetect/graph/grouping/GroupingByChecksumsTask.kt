package com.cloudlinux.webdetect.graph.grouping

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.MutableSet
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumGraphEntry
import com.cloudlinux.webdetect.util.exactlyOneIntersect

class GroupingByChecksumsTask(
    private val csDict: MutableMap<Checksum, ChecksumGraphEntry>,
    private val definedAvDict: MutableMap<AppVersion, AppVersionGraphEntry>
) {
    fun findGroups(): Sequence<Map<AppVersionGraphEntry, List<ChecksumGraphEntry>>> {
        val csSetToAvs =
            MutableMap<MutableSet<AppVersionGraphEntry>, MutableSet<ChecksumGraphEntry>>()
        for ((_, v) in csDict) {
            if (v.appVersions.size != 1 || v.appVersions.single().key !in definedAvDict) {
                csSetToAvs.computeIfAbsent(v.appVersions) { MutableSet() }.add(v)
            }
        }
        return csSetToAvs
            .entries
            .asSequence()
            .sortedBy { (_, v) -> v.size }
            .onEach { (k, v) -> println("${k.size}, ${v.size}  -> ${k.map { it.key }}") }
            .map { (avs, _) ->
                avs.associateWith {
                    it.checksums.filter { cs ->
                        exactlyOneIntersect(
                            cs.appVersions,
                            avs
                        )
                    }
                }
            }
            .onEach { v ->
                v.forEach { (av, cs) ->
                    println("\t${av.key}: ${cs.size}")
                }
            }
    }
}

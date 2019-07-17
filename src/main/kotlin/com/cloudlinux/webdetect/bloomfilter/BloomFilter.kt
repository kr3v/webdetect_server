package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.MutableSet
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import orestes.bloomfilter.BloomFilter
import orestes.bloomfilter.FilterBuilder
import java.util.concurrent.atomic.AtomicInteger

fun bloomFilterBasedSolution(
    avDict: Iterable<AppVersionGraphEntry>,
    toBeChecked: List<String>
) {
    val twoLevelBf = buildTwoLevelFilters(avDict)
    doMatching(toBeChecked, twoLevelBf)
}

private fun doMatching(
    toBeChecked: List<String>,
    twoLevelBf: Set<Map.Entry<List<String>, Pair<BloomFilter<String>, Set<Map.Entry<List<String>, BloomFilter<String>>>>>>
) {
    val matches = MutableMap<List<String>, MutableMap<List<String>, AtomicInteger>>()
    for (cs in toBeChecked) {
        for ((app, filterAndSecondLevel) in twoLevelBf) {
            val (bf, secondLevel) = filterAndSecondLevel
            if (cs in bf) {
                for ((version, filter) in secondLevel) {
                    if (cs in filter) {
                        matches.computeIfAbsent(app) { MutableMap() }.computeIfAbsent(version) { AtomicInteger() }.incrementAndGet()
                    }
                }
            }
        }
    }
    println()
    for ((app, versions) in matches) {
        if (versions.isNotEmpty()) {
            println("App $app:")
            versions
                .entries
                .sortedBy { it.value.get() }
                .takeLast(5)
                .forEach { (versions, count) ->
                    println("\t$versions: $count")
                }
        }
    }
}

private fun buildTwoLevelFilters(avDict: Iterable<AppVersionGraphEntry>): Set<Map.Entry<List<String>, Pair<BloomFilter<String>, Set<Map.Entry<List<String>, BloomFilter<String>>>>>> {
    var size = 0
    val r = avDict
        .groupBy { it.key.apps() }
        .mapValues { (_, v) ->
            val perAppChecksums = v.asSequence().map { it.checksums }.flatten().toCollection(MutableSet()).size
            val appBf = FilterBuilder(perAppChecksums, 0.01).buildBloomFilter<String>()
            size += appBf.size
            appBf to v
                .groupBy { it.key.versions() }
                .mapValues { (_, v) ->
                    val perVersionsChecksums =
                        v.asSequence().map { it.checksums }.flatten().toCollection(MutableSet()).size
                    val versionBf = FilterBuilder(perVersionsChecksums, 0.01).buildBloomFilter<String>()
                    size += versionBf.size
                    for (av in v) {
                        for (cs in av.checksums) {
                            versionBf.add(cs.key.toString())
                            appBf.add(cs.key.toString())
                        }
                    }
                    versionBf
                }
                .entries
        }
        .entries
    println("All filters: ${size / 8} B")
    return r
}
package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.MutableSet
import com.cloudlinux.webdetect.asChecksumLong
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import orestes.bloomfilter.BloomFilter
import orestes.bloomfilter.FilterBuilder

fun bloomFilterBasedSolution(
    avDict: Map<AppVersion, AppVersionGraphEntry>,
    graphEntries: Iterable<AppVersionGraphEntry>,
    toBeChecked: List<String>
) {
    val twoLevelBf = buildTwoLevelFilters(graphEntries)
    doMatching(avDict, toBeChecked, twoLevelBf)
}

private fun doMatching(
    avDict: Map<AppVersion, AppVersionGraphEntry>,
    toBeChecked: List<String>,
    twoLevelBf: List<Map.Entry<List<String>, Pair<BloomFilter<String>, List<Map.Entry<List<String>, BloomFilter<String>>>>>>
) {
    val matches = Object2IntOpenHashMap<AppVersion.Single>()
    for (cs in toBeChecked.distinct()) {
        val bytes = cs.asChecksumLong().asByteArray()
        for (i in twoLevelBf.indices) {
            val (app, filterAndSecondLevel) = twoLevelBf[i]
            val (bf, secondLevel) = filterAndSecondLevel
            if (bytes in bf) {
                for (j in secondLevel.indices) {
                    val (version, filter) = secondLevel[j]
                    if (bytes in filter) {
                        matches.addTo(AppVersion.Single(app.single(), version.single()), 1)
                    }
                }
            }
        }
    }
    println()
    matches
        .object2IntEntrySet()
        .map { it to it.intValue.toDouble() / avDict.getValue(it.key).checksums.size.toDouble() }
        .filter { (_, v) -> v > 0.5 }
        .sortedBy { (_, v) -> v }
        .forEach { (kk, vv) ->
            val (k, v) = kk
            println(
                "$k -> $v / ${avDict.getValue(k).checksums.size} = $vv"
            )
        }
}

private fun buildTwoLevelFilters(avDict: Iterable<AppVersionGraphEntry>): List<Map.Entry<List<String>, Pair<BloomFilter<String>, List<Map.Entry<List<String>, BloomFilter<String>>>>>> {
    var size = 0L
    val fpProbability = 1e-3
    val r = avDict
        .groupBy { it.key.apps() }
        .mapValues { (_, v) ->
            val perAppChecksums = v.asSequence().map { it.checksums }.flatten().toCollection(MutableSet()).size
            val appBf = FilterBuilder(perAppChecksums, fpProbability).buildBloomFilter<String>()
            size += appBf.size
            appBf to v
                .groupBy { it.key.versions() }
                .mapValues { (_, v) ->
                    val perVersionsChecksums =
                        v.asSequence().map { it.checksums }.flatten().toCollection(MutableSet()).size
                    val versionBf = FilterBuilder(perVersionsChecksums, fpProbability).buildBloomFilter<String>()
                    size += versionBf.size
                    for (av in v) {
                        for (cs in av.checksums) {
                            versionBf.addRaw(cs.key.asByteArray())
                            appBf.addRaw(cs.key.asByteArray())
                        }
                    }
                    versionBf
                }
                .entries
                .toList()
        }
        .entries
        .toList()
    println("All filters: ${size / 8} B")
    return r
}

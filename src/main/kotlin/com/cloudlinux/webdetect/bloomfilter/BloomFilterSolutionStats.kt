package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.asChecksumLong
import orestes.bloomfilter.HashProvider
import java.util.concurrent.ConcurrentHashMap

typealias MatchingResult = ConcurrentHashMap<Pair<AppVersion.Single, MutableSet<Checksum>>, MutableSet<Checksum>>

fun doMatching(
    toBeChecked: List<String>,
    twoLevelBf: LayeredBloomFilter<Pair<List<String>, LayeredBloomFilter<List<String>>>>
): Pair<MatchingResult, MatchingResult> {
    val matches = MatchingResult()
    val falsePositives = MatchingResult()

    toBeChecked
        .parallelStream()
        .distinct()
        .forEach { cs ->
            val csl = cs.asChecksumLong()
            val bytes = csl.asByteArray()

            val hash1 = HashProvider.murmur3(0, bytes)
            val hash2 = HashProvider.murmur3(hash1.toInt(), bytes)

            for (appNode in twoLevelBf.lookup(hash1, hash2)) {
                val (app, secondLevel) = appNode.value
                for (versionNode in secondLevel.lookup(hash1, hash2)) {
                    val checksums = versionNode.filter.items
                    val key = AppVersion.Single(app.single(), versionNode.value.single()) to checksums
                    if (csl in checksums) {
                        matches.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(csl)
                    } else {
                        falsePositives.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(csl)
                    }
                }
            }
        }

    return matches to falsePositives
}

fun debugPrint(
    params: BloomFilterSolutionParameters,
    matches: MatchingResult,
    falsePositives: MatchingResult
) {
    println("Notable false positive stats: ")
    falsePositives
        .map { (k, v) -> Triple(k.first, k.second, v) }
        .filter { (_, all, matched) -> matched.size.toDouble() / all.size.toDouble() > params.matchingThreshold }
        .sortedBy { (_, all, matched) -> matched.size.toDouble() / all.size.toDouble() }
        .forEach { (av, all, matched) -> println("$av: ${matched.size}/${all.size}") }

    println("Total correct matches: ${matches.entries.sumBy { it.value.size }}")
    println("Total FP: ${falsePositives.entries.sumBy { it.value.size }}")
    println("Notable FP: ${falsePositives
        .entries
        .map { (k, v) -> Triple(k.first, k.second, v) }
        .filter { (_, allChecksums, matched) -> matched.size.toDouble() / allChecksums.size.toDouble() > params.matchingThreshold }
        .sumBy { it.second.size }}"
    )
}
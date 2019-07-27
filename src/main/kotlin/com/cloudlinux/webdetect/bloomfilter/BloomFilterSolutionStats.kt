package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.ChecksumLong
import com.cloudlinux.webdetect.asChecksumLong
import orestes.bloomfilter.HashProvider
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.MutableSet as KMutableSet

typealias MatchingResult = ConcurrentHashMap<Pair<AppVersion, KMutableSet<Checksum>>, KMutableSet<Checksum>>

fun doMatching(
    toBeChecked: List<String>,
    twoLevelBf: HierarchicalBloomFilter<Pair<List<String>, HierarchicalBloomFilter<List<String>>>>
): Pair<MatchingResult, MatchingResult> = doMatching(toBeChecked.map(String::asChecksumLong), twoLevelBf)

fun murmurHash(cslBytes: ByteArray): Pair<Long, Long> {
    val hash1 = HashProvider.murmur3(0, cslBytes)
    val hash2 = HashProvider.murmur3(hash1.toInt(), cslBytes)
    return hash1 to hash2
}

fun doMatching(
    toBeChecked: Collection<ChecksumLong>,
    twoLevelBf: HierarchicalBloomFilter<Pair<List<String>, HierarchicalBloomFilter<List<String>>>>
): Pair<MatchingResult, MatchingResult> {
    val matches = MatchingResult()
    val falsePositives = MatchingResult()

    toBeChecked
        .parallelStream()
        .distinct()
        .forEach { csl ->
            for (appNode in twoLevelBf.lookup(csl)) {
                val (app, secondLevel) = appNode.value
                for (versionNode in secondLevel.lookup(csl)) {
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

fun PrintWriter.print(
    params: BloomFilterSolutionParameters,
    matches: MatchingResult,
    falsePositives: MatchingResult,
    printFalsePositive: Boolean = false,
    printMatches: Boolean = false
) {
    if (printFalsePositive) {
        println()
        println()
        println("Notable false positive stats: ")
        print(falsePositives, params)
    }
    if (printMatches) {
        println()
        println()
        println("Notable matches stats: ")
        print(matches, params)
    }

    println()
    println("Total correct matches: ${matches.entries.sumBy { it.value.size }}")
    println("Total FP: ${falsePositives.entries.sumBy { it.value.size }}")
    println("Notable FP: ${falsePositives
        .entries
        .map { (k, v) -> Triple(k.first, k.second, v) }
        .filter { (_, allChecksums, matched) -> matched.size.toDouble() / allChecksums.size.toDouble() > params.matchingThreshold }
        .sumBy { it.second.size }}"
    )

    flush()
}

private fun PrintWriter.print(result: MatchingResult, params: BloomFilterSolutionParameters) = result
    .map { (k, v) -> Triple(k.first, k.second, v) }
    .filter { (_, all, matched) -> matched.size.toDouble() / all.size.toDouble() > params.matchingThreshold }
    .sortedBy { (_, all, matched) -> matched.size.toDouble() / all.size.toDouble() }
    .forEach { (av, all, matched) -> println("$av: ${matched.size}/${all.size}") }
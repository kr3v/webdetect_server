package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.bloomfilter.BloomFilterSolutionParameters
import com.cloudlinux.webdetect.bloomfilter.HierarchicalBloomFilter
import com.cloudlinux.webdetect.bloomfilter.HierarchicalBloomFilterBuilder
import com.cloudlinux.webdetect.bloomfilter.ImmutableBloomFilter
import com.cloudlinux.webdetect.bloomfilter.bloomFilter
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.streams.toList

/// defines best params for [BloomFilterSolutionParameters] via brute-force
fun find(
    definedAvDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    detect: List<String>
) {
    definedAvDict.forEach { (_, v) ->
        v.checksums.removeAll(v.checksums - v.checksums
            .sortedBy { cs -> cs.dependsOn.size }
            .take(5))
    }

    val defined = definedAvDict.values.map { it.checksums.mapTo(MutableSet()) { it.key } to it.key }
    val detectTmp = detect.flatMapTo(MutableSet()) { File(it).readLines() }.map(String::asChecksumLong)

    definedAvDict.clear()
    definedAvDict.trim()
    System.gc()

    val falsePositiveRatiosRange = IntProgression.fromClosedRange(51, 202, 5)
    val bloomFilterMinimumSizeRange = IntProgression.fromClosedRange(5, 15, 1)
    val leafsRange = IntProgression.fromClosedRange(2, 17, 1).toList()

    data class T1(
        val fp: Double,
        val bfSize: Int
    )

    data class T2(
        val fp: Double,
        val bfSize: Int,
        val leafs: Int,
        val filters: List<Pair<ImmutableBloomFilter, AppVersion>>,
        val filtersTotalSize: Long
    )

    println("FP\tLeafs\tMinimum BF size\tResulting size")
    data class T3(
        val cfg: BloomFilterSolutionParameters,
        val lbf: HierarchicalBloomFilter<AppVersion>,
        val filtersTotalSize: Long
    ) {
        override fun toString(): String {
            val size = (filtersTotalSize.toDouble() * 100 / (1024 * 1024)).roundToInt().toDouble() / 100
            return "${cfg.bloomFilterFalsePositiveProbability}\t${cfg.leafsPerNode}\t${cfg.bloomFilterMinimumSize}\t$size"
        }
    }

    val total = AtomicInteger()
    val correct = AtomicInteger()
    falsePositiveRatiosRange
        .flatMap { fp -> bloomFilterMinimumSizeRange.map { bf -> T1(fp.toDouble() / 1000, bf) } }
        .parallelStream()
        .flatMap { (fp, bfSize) ->
            val cfg1 = BloomFilterSolutionParameters(
                bloomFilterFalsePositiveProbability = fp,
                leafsPerNode = -1,
                matchingThreshold = 0.5,
                bloomFilterMinimumSize = bfSize
            )
            val filters = defined.parallelStream().map { bloomFilter(it.first, cfg1) to it.second }.toList()
            val filtersTotalSize = filters.fold(0L) { acc, (a, _) -> acc + a.config.size().toLong() }
            leafsRange.stream().map { T2(fp, bfSize, it, filters, filtersTotalSize) }
        }
        .map { (fp, bfSize, leafs, filters, filtersTotalSize) ->
            val cfg = BloomFilterSolutionParameters(
                bloomFilterFalsePositiveProbability = fp,
                leafsPerNode = leafs,
                matchingThreshold = 0.5,
                bloomFilterMinimumSize = bfSize
            )
            val lbfBuilder = HierarchicalBloomFilterBuilder(cfg)
            val lbf = lbfBuilder.build(filters)
            T3(cfg, lbf, (filtersTotalSize + lbf.size()) / 8)
        }
        .filter { (cfg, lbf, _) ->
            val chm = ConcurrentHashMap<AppVersion, AtomicInteger>()
            val k = detectTmp
                .parallelStream()
                .allMatch { csl ->
                    for (match in lbf.lookup(csl)) {
                        if (csl !in match.filter.items) {
                            val v = chm.computeIfAbsent(match.value) { AtomicInteger() }.incrementAndGet()
                            if (v.toDouble() / match.filter.items.size > cfg.matchingThreshold) {
                                return@allMatch false
                            }
                        }
                    }
                    true
                }
            total.getAndIncrement()
            if (k) correct.getAndIncrement()
            k
        }
        .forEach(::println)
    println("$correct / $total")
}
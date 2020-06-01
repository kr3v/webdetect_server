package kr3v.webdetect.bloomfilter

import kr3v.webdetect.AppVersion
import kr3v.webdetect.ChecksumLong
import kr3v.webdetect.FMutableMap
import kr3v.webdetect.FMutableSet
import kr3v.webdetect.WebdetectContext
import kr3v.webdetect.graph.AppVersionGraphEntry
import kr3v.webdetect.util.toList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/// defines best params for [BloomFilterSolutionParameters] via brute-force
fun find(
    ctx: WebdetectContext,
    definedAvDict: FMutableMap<AppVersion, AppVersionGraphEntry>,
    detect: List<ChecksumLong>,
    maxChecksums: Int
) {
    definedAvDict.forEach { (_, v) ->
        TODO("v.takeSerializableChecksums(maxChecksums, ctx).map { it.entry }")
//        v.checksums.removeAll(v.checksums - todo)
    }

    val defined = definedAvDict.values.map { it.checksums.mapTo(FMutableSet()) { it.key } to it.key }

    definedAvDict.clear()
    definedAvDict.trim()
    System.gc()

    val falsePositiveProbabilityRange = IntProgression.fromClosedRange(1, 162, 10).map { it.toDouble() / 1000 }
    val bloomFilterMinimumSizeRange = IntProgression.fromClosedRange(6, 30, 3)
    val leafsRange = listOf(2, 4, 8, 16, 32, 64)

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
            return "${cfg.bfFpProbability}\t${cfg.leafsPerNode}\t${cfg.bfMinimumSize}\t$size"
        }
    }

    val total = AtomicInteger()
    val correct = AtomicInteger()
    falsePositiveProbabilityRange
        .flatMap { fp -> bloomFilterMinimumSizeRange.map { bf -> T1(fp, bf) } }
        .parallelStream()
        .flatMap { (fp, bfSize) ->
            val cfg1 = BloomFilterSolutionParameters(
                bfFpProbability = fp,
                leafsPerNode = -1,
                matchingThreshold = 0.5,
                bfMinimumSize = bfSize
            )
            val filters = defined.parallelStream().unordered()
                .map { bloomFilter(it.first, cfg1) to it.second }
                .toList(defined.size)
            val filtersTotalSize = filters.fold(0L) { acc, (a, _) -> acc + a.config.size().toLong() }
            leafsRange.stream().map { T2(fp, bfSize, it, filters, filtersTotalSize) }
        }
        .map { (fp, bfSize, leafs, filters, filtersTotalSize) ->
            val cfg = BloomFilterSolutionParameters(
                bfFpProbability = fp,
                leafsPerNode = leafs,
                matchingThreshold = 0.5,
                bfMinimumSize = bfSize
            )
            val lbf = HierarchicalBloomFilterBuilder(cfg).build(filters)
            T3(cfg, lbf, (filtersTotalSize + lbf.size()) / 8)
        }
        .filter {
            val (cfg, lbf, _) = it
            val chm = ConcurrentHashMap<AppVersion, AtomicInteger>()
            val failed = ConcurrentHashMap.newKeySet<AppVersion>()
            val k = detect.parallelStream().allMatch { csl ->
                for (match in lbf.lookup(csl)) {
                    if (csl in match.filter.items) continue
                    val v = chm.computeIfAbsent(match.value) { AtomicInteger() }.incrementAndGet()
                    if (v.toDouble() / match.filter.items.size <= cfg.matchingThreshold) continue
                    failed += match.value
                    if (failed.size > 1000) return@allMatch false
                }
                true
            }
            total.getAndIncrement()
            if (k) {
                correct.getAndIncrement()
                println("$it\t${failed.size}")
            }
            k
        }
//        .collect(Collectors.toList())
//        .sortedByDescending { it.filtersTotalSize }
        .forEach { /* no-op */ }
    println("$correct / $total = ${correct.toDouble() / total.toDouble()}")
}
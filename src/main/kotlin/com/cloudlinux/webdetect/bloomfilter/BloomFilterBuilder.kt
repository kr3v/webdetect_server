package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.FMutableSet
import com.cloudlinux.webdetect.WebdetectContext
import com.cloudlinux.webdetect.util.toList
import orestes.bloomfilter.FilterBuilder
import java.lang.Integer.max
import java.util.BitSet

fun buildHierarchicalBloomFilter(
    solutionCtx: BloomFilterSolutionParameters,
    dataCtx: WebdetectContext
): HierarchicalBloomFilter<Pair<List<String>, HierarchicalBloomFilter<List<String>>>> {
    val lbfBuilder = HierarchicalBloomFilterBuilder(solutionCtx)
    return lbfBuilder.build(
        buildLinearBloomFilter(solutionCtx, dataCtx).map { (filter, value) ->
            val (app, versionFilters) = value
            filter to (app to lbfBuilder.build(versionFilters))
        }
    )
}

fun buildLinearBloomFilter(
    solutionCtx: BloomFilterSolutionParameters,
    dataCtx: WebdetectContext
): List<Pair<ImmutableBloomFilter, Pair<List<String>, List<Pair<ImmutableBloomFilter, List<String>>>>>> {
    val appEntries = dataCtx.appVersionsToChecksums.keys.groupBy { it.apps() }.entries
    return appEntries
        .parallelStream()
        .unordered()
        .map { (k, v) ->
            val versionEntries = v.groupBy { it.versions() }.entries
            val versionsBf = versionEntries
                .parallelStream()
                .unordered()
                .map { (versions, v) -> bloomFilter(v, dataCtx, solutionCtx) to versions }
                .toList(versionEntries.size)
            bloomFilter(v, dataCtx, solutionCtx) to (k to versionsBf)
        }
        .toList(appEntries.size)
}

fun bloomFilter(appVersions: List<AppVersion>, ctx: WebdetectContext, solutionCtx: BloomFilterSolutionParameters) =
    bloomFilter(appVersions.flatMapTo(FMutableSet()) { ctx.appVersionsToChecksums[it]!! }, solutionCtx)

fun bloomFilter(checksums: FMutableSet<Checksum>, solutionCtx: BloomFilterSolutionParameters): ImmutableBloomFilter {
    val cfg = FilterBuilder(
        max(solutionCtx.bloomFilterMinimumSize, checksums.size),
        solutionCtx.bloomFilterFalsePositiveProbability
    ).complete()
    val immutableBloomFilter = ImmutableBloomFilter(
        BitSet(cfg.size()),
        cfg,
        checksums,
        ImmutableBloomFilter.HashingType.SHA256
    )
    checksums.forEach { checksum -> immutableBloomFilter.addRaw(checksum) }
    return immutableBloomFilter
}

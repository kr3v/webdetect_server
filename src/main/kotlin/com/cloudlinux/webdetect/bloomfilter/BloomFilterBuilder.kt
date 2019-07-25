package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.DataContext
import com.cloudlinux.webdetect.MutableSet
import orestes.bloomfilter.FilterBuilder
import java.lang.Integer.max
import kotlin.streams.toList

fun buildLayeredBloomFilter(
    solutionCtx: BloomFilterSolutionParameters,
    dataCtx: DataContext
): LayeredBloomFilter<Pair<List<String>, LayeredBloomFilter<List<String>>>> {
    val lbfBuilder = LayeredBloomFilterBuilder(solutionCtx)
    return lbfBuilder.build(
        buildLinearBloomFilter(solutionCtx, dataCtx).map { (filter, value) ->
            val (app, versionFilters) = value
            filter to (app to lbfBuilder.build(versionFilters))
        }
    )
}

fun buildLinearBloomFilter(solutionCtx: BloomFilterSolutionParameters, dataCtx: DataContext) =
    dataCtx.appVersions.keys
        .groupBy { it.apps() }
        .entries
        .parallelStream()
        .map { (k, v) ->
            val versionsBf = v
                .groupBy { it.versions() }
                .entries
                .parallelStream()
                .map { (versions, v) -> bloomFilter(v, dataCtx, solutionCtx) to versions }
                .toList()
            bloomFilter(v, dataCtx, solutionCtx) to (k to versionsBf)
        }
        .toList()

fun bloomFilter(appVersions: List<AppVersion>, ctx: DataContext, solutionCtx: BloomFilterSolutionParameters) =
    bloomFilter(appVersions.flatMapTo(MutableSet()) { ctx.appVersions[it]!! }, solutionCtx)

fun bloomFilter(checksums: MutableSet<Checksum>, solutionCtx: BloomFilterSolutionParameters): ImmutableBloomFilter {
    val bloomFilter = FilterBuilder(
        max(solutionCtx.bloomFilterMinimumSize, checksums.size),
        solutionCtx.bloomFilterFalsePositiveProbability
    ).buildBloomFilter<String>()
    checksums.forEach { checksum -> bloomFilter.addRaw(checksum.asByteArray()) }
    return ImmutableBloomFilter(bloomFilter.bitSet, bloomFilter.config(), checksums)
}

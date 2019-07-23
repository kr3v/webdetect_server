package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.MutableSet
import com.cloudlinux.webdetect.PooledCtx
import com.cloudlinux.webdetect.asChecksumLong
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import orestes.bloomfilter.BloomFilter
import orestes.bloomfilter.FilterBuilder
import orestes.bloomfilter.HashProvider
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

fun bloomFilterBasedSolution(
    ctx: PooledCtx,
    toBeChecked: List<String>
) {
    val twoLevelBf = buildTwoLevelFilters(ctx)
    doMatching(ctx, toBeChecked, twoLevelBf)
}

private fun doMatching(
    ctx: PooledCtx,
    toBeChecked: List<String>,
    twoLevelBf: List<Pair<List<String>, Pair<ImmutableBloomFilter<String>, List<Pair<List<String>, Pair<ImmutableBloomFilter<String>, Set<Checksum>>>>>>>
) {
    fun merge(
        to: MutableMap<AppVersion.Single, MutableSet<Checksum>>,
        from: MutableMap<AppVersion.Single, MutableSet<Checksum>>
    ) {
        to.putAll(from - to.keys)
        for ((k, v) in to) {
            v += (from[k] ?: emptySet())
        }
    }

    fun merge(to: MutableMap<AppVersion.Single, MutableSet<Checksum>>, from: MutableMap<AppVersion.Single, Checksum>) {
        (from - to.keys)
            .forEach { t, u -> to[t] = MutableSet<Checksum>().also { it.add(u) } }
        for ((k, v) in to) {
            val value = from[k]
            if (value != null) v += value
        }
    }

    val (matches, falsePositives) = toBeChecked
        .parallelStream()
        .unordered()
        .distinct()
        .map { cs ->
            val matches = Object2ObjectOpenHashMap<AppVersion.Single, Checksum>()
            val falsePositives = Object2ObjectOpenHashMap<AppVersion.Single, Checksum>()

            val csl = cs.asChecksumLong()
            val bytes = csl.asByteArray()

            val hash1: Long = HashProvider.murmur3(0, bytes)
            val hash2: Long = HashProvider.murmur3(hash1.toInt(), bytes)

            for (i in twoLevelBf.indices) {
                val (app, filterAndSecondLevel) = twoLevelBf[i]
                val (bf, secondLevel) = filterAndSecondLevel
                if (bf.contains(hash1, hash2)) {
                    for (j in secondLevel.indices) {
                        val (version, filterAndRealCsSet) = secondLevel[j]
                        val (filter, csSet) = filterAndRealCsSet
                        if (filter.contains(hash1, hash2)) {
                            if (csl in csSet) {
                                matches[AppVersion.Single(app.single(), version.single())] = csl
                            } else {
//                                falsePositives[AppVersion.Single(app.single(), version.single())] = csl
                            }
                        }
                    }
                }
            }
            matches to falsePositives
        }
        .collect(
            {
                Pair(
                    ConcurrentHashMap<AppVersion.Single, MutableSet<Checksum>>(),
                    ConcurrentHashMap<AppVersion.Single, MutableSet<Checksum>>()
                )
            },
            { a, b ->
                merge(a.first, b.first)
                merge(a.second, b.second)
            },
            { a, b ->
                merge(a.first, b.first)
                merge(a.second, b.second)
            }
        )

    println()
    matches
        .entries
        .map { it to it.value.size.toDouble() / ctx.appVersions[it.key]!!.size.toDouble() }
        .filter { (_, v) -> v > 0.5 }
        .sortedBy { (_, v) -> v }
//        .groupBy { (k, _) -> k.key.app }
//        .values
//        .flatten()
        .forEach { (kk, vv) ->
            val (k, v) = kk
            println("$k -> ${v.size} / ${ctx.appVersions[k]!!.size} = $vv")
        }

//    println()
//    falsePositives
//        .entries
//        .map { it to it.value.size.toDouble() / ctx.appVersions[it.key]!!.size.toDouble() }
//        .sortedBy { (_, v) -> v }
//        .groupBy { (k, _) -> k.key.app }
//        .values
//        .flatten()
//        .forEach { (kk, vv) ->
//            val (k, v) = kk
//            println("(false positive!) $k -> ${v.size} / ${ctx.appVersions[k]!!.size} = $vv")
//        }
}

private fun buildTwoLevelFilters(ctx: PooledCtx): List<Pair<List<String>, Pair<ImmutableBloomFilter<String>, List<Pair<List<String>, Pair<ImmutableBloomFilter<String>, Set<Checksum>>>>>>> {
    var size = 0L
    val fpProbability = 1e-3
    val r = ctx.appVersions.keys
        .groupBy { it.apps() }
        .entries
        .parallelStream()
        .map { (k, v) ->
            val perAppChecksums = v.flatMapTo(MutableSet()) { ctx.appVersions[it]!! }.size
            val appBf = FilterBuilder(perAppChecksums, fpProbability).buildBloomFilter<String>()
            size += appBf.size
            val versionsBf = v
                .groupBy { it.versions() }
                .entries
                .parallelStream()
                .map { (k, v) ->
                    val perVersion = v.flatMapTo(MutableSet()) { ctx.appVersions[it]!! }
                    val perVersionsChecksums = perVersion.size
                    val versionBf = FilterBuilder(perVersionsChecksums, fpProbability).buildBloomFilter<String>()
                    size += versionBf.size
                    for (av in v) {
                        for (cs in ctx.appVersions[av]!!) {
                            versionBf.addRaw(cs.asByteArray())
                            appBf.addRaw(cs.asByteArray())
                        }
                    }
                    k to (ImmutableBloomFilter(versionBf) to perVersion)
                }
                .toList()
            k to (ImmutableBloomFilter(appBf) to versionsBf)
        }
        .toList()
    println("All filters: ${size / 8} B")
    return r
}

data class ImmutableBloomFilter<T>(
    val bloom: BitSet,
    val config: FilterBuilder
) {

    constructor(filter: BloomFilter<T>) : this(filter.bitSet, filter.config())

    fun contains(hash1: Long, hash2: Long): Boolean {
        for (position in hashCassandra(config.size(), config.hashes(), hash1, hash2)) {
            if (!getBit(position)) {
                return false
            }
        }
        return true
    }

    private fun hashCassandra(m: Int, k: Int, hash1: Long, hash2: Long): IntArray {
        val result = IntArray(k)
        for (i in 0 until k) {
            result[i] = ((hash1 + i * hash2) % m).toInt()
        }
        return result
    }

    private fun getBit(index: Int) = bloom.get(index)
}
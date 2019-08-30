package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumGraphEntry
import com.cloudlinux.webdetect.graph.GraphBasedSolutionSerializer
import com.cloudlinux.webdetect.graph.graphBasedSolution
import com.cloudlinux.webdetect.graph.writeUndetected
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.util.Optional
import kotlin.streams.toList

fun main(args: Array<String>) {
    if (args.size < 2) throw Exception("Usage: webdetect_gen <csv path> <output path>")
    val `in`: String = args[0]
    val detect = Files.list(Paths.get(args[1]))
        .filter { it.fileName.toString().endsWith("sha") }
        .map { it.fileName to Files.newBufferedReader(it) }
        .toList()

    val webdetectCtx = buildContextFromCsv(`in`)
    System.gc()
    val (avDict, csDict, definedAvDict, undetected) = graphBasedSolution(webdetectCtx)

    val maxChecksums = 5
    GraphBasedSolutionSerializer(avDict, definedAvDict, maxChecksums).serialize(
        Optional.of("db.json"),
        Optional.empty()
    )
    println("${ZonedDateTime.now()}: finding undetected")
    writeUndetected(undetected.keys, avDict, PrintWriter(FileOutputStream(File("undetected"))))

    System.gc()

    val db = definedAvDict
        .values
        .flatMap { av -> av.checksums.sortedBy { it.dependsOn.size }.take(maxChecksums) }
        .associateByTo(FMutableMap()) { it.key.toString() }

    var misverdict = 0
    detect.forEach { (file, io) ->
        val used = FMutableSet<ChecksumGraphEntry>()
        val unused = FMutableSet<ChecksumGraphEntry>()
        io.forEachLine { ln ->
            val verdict = ln[17]
            when (val line = ln.drop(18).take(64)) {
                in db -> {
                    if (verdict != '1') misverdict++
                    used += db[line]
                }
                else -> csDict[ChecksumLong(line)]?.let {
                    if (verdict != '1') misverdict++
                    unused.add(it)
                }
            }
        }

        val wc = WebdetectClient(used, checksumsPartitionFilter = 0.5, maxChecksums = maxChecksums)
        val avUnfiltered = wc.foundAvs
        val avFilteredByDependsOn = wc.doMatching()
        val avs = avFilteredByDependsOn

//        val avUnfiltered = used.groupBy { it.appVersions.single() }
//        val csFilteredByDependsOn = used.filter { it.dependsOn.none(avUnfiltered::containsKey) }
//        val avFilteredByDependsOn = csFilteredByDependsOn.groupBy { it.appVersions.single() }
//        val avs = avFilteredByDependsOn.filter { (av, cs) -> cs.size.toDouble() / av.checksums.size.coerceAtMost(5) >= 0.5 }

//        val hasNotWordpressCore = avs.flatMap { it.key.key.appVersions() }.none { it.app == "wordpress-cores" }
//        if (hasNotWordpressCore) return@forEach

//        val errors = ((used - avs.values.flatten()) + unused)
//            .filter { it.dependsOn.none(avs::containsKey) && it.appVersions.none(avs::containsKey) }
//            .sortedBy { it.key.path ?: "<sorry>" }

        fun <T> Collection<T>.format() = joinToString(separator = "\n\t", prefix = "\n\t")
        fun List<ChecksumGraphEntry>.format() = map { k -> k.key.toString() + " -> " + k.key.path }.format()
        fun Map<AppVersionGraphEntry, List<ChecksumGraphEntry>>.format() = entries
            .map { (k, v) -> k.key.toString() + ": " + v.size + "/" + k.checksums.size + " : " + v.joinToString(" ") }
            .sorted()
            .format()

        println()
        println("DB: $file")
        val formattedFoundAvs = avs.format()
        println("Found AVs: $formattedFoundAvs")
        val formattedImpliedAvs = avs.flatMapTo(FMutableSet()) { it.key.implies }.format()
        println("Implied AVs: $formattedImpliedAvs")
//        val formattedCoeffAvs = (avFilteredByDependsOn - avs.keys).format()
        val formattedCoeffAvs = (avUnfiltered - avs.keys).format()
        println("Filtered by 0.5-filter: $formattedCoeffAvs")
//        val formattedFilteredByDependsOn = (avUnfiltered - avFilteredByDependsOn.keys).format()
//        println("Filtered by depends on: $formattedFilteredByDependsOn")
//        val formattedErrors = errors.format()
//        println("Errors with paths: $formattedErrors")
        println()
    }

    println()
    println("Verdict != '1', but found in DB: $misverdict")
}

class WebdetectClient(
    found: Collection<ChecksumGraphEntry>,
    private val checksumsPartitionFilter: Double,
    private val maxChecksums: Int
) {
    val foundAvs = found.groupByTo(FMutableMap(), { it.appVersions.single() })
    private val memoizedIsValidMap = Object2BooleanOpenHashMap<AppVersionGraphEntry>()

    fun doMatching(): Map<AppVersionGraphEntry, MutableList<ChecksumGraphEntry>> = foundAvs
        .asSequence()
        .filter { (k, _) -> k.memoizedIsValid() }
        .filter { (k, v) -> v.size.toDouble() / k.checksums.size.coerceAtMost(maxChecksums) >= checksumsPartitionFilter }
        .associate { (k, v) -> k to v }

    private fun AppVersionGraphEntry.memoizedIsValid(): Boolean =
        if (this in memoizedIsValidMap) memoizedIsValidMap.getBoolean(this)
        else isValid().also { memoizedIsValidMap[this] = it }

    private fun AppVersionGraphEntry.isValid() =
        this in foundAvs && foundAvs[this]!!.any { cs -> cs.dependsOn.none { doAv -> doAv.memoizedIsValid() } }
}


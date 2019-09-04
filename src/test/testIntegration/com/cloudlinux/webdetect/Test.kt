package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumKey
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

    val webdetectCtx = buildContextByCsv(`in`, ChecksumObjects)
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
        val used = FMutableSet<ChecksumGraphEntry<Checksum>>()
        val unused = FMutableSet<ChecksumGraphEntry<Checksum>>()
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

        val wc = WebdetectClient(used, requiredChecksumsCoeff = 0.5, maxChecksums = maxChecksums)
        val avs = wc.resultAvs

//        val errors = ((used - avs.values.flatten()) + unused)
//            .filter { it.dependsOn.none(avs::containsKey) && it.appVersions.none(avs::containsKey) }
//            .sortedBy { it.key.path ?: "<sorry>" }

        fun <T> Collection<T>.format() = joinToString(separator = "\n\t", prefix = "\n\t")
        fun List<ChecksumGraphEntry<Checksum>>.format() = map { k -> k.key.toString() + " -> " + k.key.path }.format()
        fun Map<AppVersionGraphEntry<Checksum>, List<ChecksumGraphEntry<Checksum>>>.format() = entries
            .map { (k, v) -> k.key.toString() + ": " + v.size + "/" + k.checksums.size/* + " : " + v.joinToString(" ")*/ }
            .sorted()
            .format()

        println()
        println("DB: $file")
        val formattedFoundAvs = avs.format()
        println("Found AVs: $formattedFoundAvs")
        val formattedImpliedAvs = wc.impliedAvs.format()
        println("Implied AVs: $formattedImpliedAvs")
        val potentiallyMissedImpliedAvs = wc.potentiallyMissedImpliedAvs.format()
        println("Potentially missed implied AVs: $potentiallyMissedImpliedAvs")
        val formattedFilteredByDependsOn = (wc.validByEnoughChecksums - wc.resultAvs.keys).format()
        println("Filtered by depends on: $formattedFilteredByDependsOn")
        val formattedCoeffAvs = (wc.foundAvs - wc.validByEnoughChecksums.keys).format()
        println("Filtered by 0.5-filter: $formattedCoeffAvs")
//        val formattedErrors = errors.format()
//        println("Errors with paths: $formattedErrors")
        println()
    }

    println()
    println("Verdict != '1', but found in DB: $misverdict")
}

class WebdetectClient<C : ChecksumKey<C>>(
    found: Collection<ChecksumGraphEntry<C>>,
    private val requiredChecksumsCoeff: Double,
    private val maxChecksums: Int
) {
    val foundAvs = found.groupByTo(FMutableMap(), { it.appVersions.single() })

    val validByEnoughChecksums by lazy { foundAvs.filter { (k, v) -> k.hasEnoughMatchedChecksums(v) } }
    val resultAvs by lazy { validByEnoughChecksums.filter { (k, _) -> k.memoizedIsValidByDependsOn() } }

    val impliedAvs by lazy {
        resultAvs.keys
            .flatMapTo(FMutableSet()) { it.implies }
            .filter { it in foundAvs }
            .filterNot { it.memoizedIsValidByDependsOn() }
            .filter { it.hasEnoughMatchedChecksums(foundAvs.getValue(it)) }
            .associateWith(foundAvs::getValue)
    }

    val potentiallyMissedImpliedAvs by lazy {
        foundAvs.keys
            .filter {
                it in foundAvs && it !in impliedAvs && it !in resultAvs &&
                    !it.memoizedIsValidByDependsOn() && it.hasEnoughMatchedChecksums(foundAvs.getValue(it))
            }
            .associateWith(foundAvs::getValue)
    }

    private fun AppVersionGraphEntry<C>.hasEnoughMatchedChecksums(matchedChecksums: List<ChecksumGraphEntry<C>>) =
        matchedChecksums.size.toDouble() / checksums.size.coerceAtMost(maxChecksums) >= requiredChecksumsCoeff

    private val memoizedIsValidMap = Object2BooleanOpenHashMap<AppVersionGraphEntry<C>>()
    private fun AppVersionGraphEntry<C>.memoizedIsValidByDependsOn(): Boolean = when (this) {
        in memoizedIsValidMap -> memoizedIsValidMap.getBoolean(this)
        !in foundAvs -> false
        else -> isValidByDependsOn().also { memoizedIsValidMap[this] = it }
    }

    private fun AppVersionGraphEntry<C>.isValidByDependsOn() =
        foundAvs[this]?.any { cs -> cs.dependsOn.none { doAv -> doAv.memoizedIsValidByDependsOn() } } ?: false
}

package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.IntMutableSet
import com.cloudlinux.webdetect.OBJECT_MAPPER
import com.cloudlinux.webdetect.buildDepths
import com.cloudlinux.webdetect.util.asByteArray
import com.cloudlinux.webdetect.util.deleteDirectoryRecursively
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.fusesource.leveldbjni.JniDBFactory
import org.iq80.leveldb.Options
import java.io.File
import java.nio.ByteBuffer
import java.util.Optional

private typealias Checksums = FMutableMap<Checksum, ByteBuffer>
private typealias AppVersions = FMutableMap<Int, AppVersionGraphEntry>

var HasIntProperties.intIndex
    get() = properties[0]
    set(value) {
        properties[0] = value
    }

class GraphBasedSolutionSerializer(
    private val avDict: FMutableMap<AppVersion, AppVersionGraphEntry>,
    private val definedAvDict: Map<AppVersion, AppVersionGraphEntry>,
    private val serializableChecksumsChooser: SerializableChecksumsChooser
) {

    companion object {
        private const val CHECKSUM_VALUE_BARRIER = (-1).toByte()
    }

    fun serialize(
        jsonOut: Optional<String> = Optional.empty(),
        levelDbOut: Optional<String> = Optional.empty()
    ) {
        if (!jsonOut.isPresent && !levelDbOut.isPresent) return
        val (checksums, appVersions) = prepareToSerialize()
        jsonOut.ifPresent { serializeAsJson(checksums, appVersions, it) }
        levelDbOut.ifPresent { serializeAsLevelDb(checksums, appVersions, it) }
    }

    private fun prepareToSerialize(): Pair<Checksums, AppVersions> {
        definedAvDict.values.forEachIndexed { index, appVersionGraphEntry ->
            appVersionGraphEntry.properties = intArrayOf(index)
        }

        val checksums = definedAvDict
            .values
            .flatMap { serializableChecksumsChooser.chooseFor(it) }
            .associateTo(FMutableMap()) { (it, depths) ->
                val avAndDependsOnSize = 4 * (1 + it.dependsOn.size)
                val depthsSize = if (depths.isNotEmpty()) 1 + depths.size else 0
                val values = ByteBuffer.allocate(avAndDependsOnSize + depthsSize)
                values.putInt(it.appVersions.single().intIndex)
                it.dependsOn.forEach { value -> values.putInt(value.intIndex) }
                if (depths.isNotEmpty()) {
                    values.put(CHECKSUM_VALUE_BARRIER)
                    depths.forEach { value -> values.put(value.toByte()) }
                }
                it.key to values
            }

        val appVersions = avDict.values.associateByTo(FMutableMap(), AppVersionGraphEntry::intIndex)

        return checksums to appVersions
    }

    private fun AppVersionGraphEntry.prepareAppVersion() = mapOf(
        "av" to key.appVersions(),
        "impl" to implies.map { it.intIndex },
        "total" to checksums.size.coerceAtMost(serializableChecksumsChooser.maxChecksums)
    )

    private fun serializeAsJson(
        checksums: Checksums,
        appVersions: AppVersions,
        pathToJson: String
    ) {
        fun intBuffer(v: ByteBuffer): List<Int> {
            val barrierIndex =
                v.array().withIndex().first { (idx, value) -> idx % 4 == 0 && value == CHECKSUM_VALUE_BARRIER }.index
            val avAndDo = ByteBuffer.wrap(v.array().sliceArray(0 until barrierIndex)).asIntBuffer()
            val avAndDoArray = IntArray(avAndDo.capacity())
            avAndDo.get(avAndDoArray)
            return avAndDoArray.toList() + CHECKSUM_VALUE_BARRIER.toInt() + v.array().drop(barrierIndex + 1).map { it.toInt() }
        }

        val map = FMutableMap<String, Any>()
        map += checksums.map { (k, v) -> k.toString() to intBuffer(v) }
        map += appVersions.map { (k, v) -> k.toString() to v.prepareAppVersion() }
        OBJECT_MAPPER.writeValue(
            File(pathToJson),
            map
        )
    }

    private fun serializeAsLevelDb(
        checksums: Checksums,
        appVersions: AppVersions,
        pathToLevelDb: String
    ) {
        deleteDirectoryRecursively(File(pathToLevelDb).toPath())
        JniDBFactory.factory
            .open(File(pathToLevelDb), Options().createIfMissing(true))
            .use { db ->
                val wb = db.createWriteBatch()
                for ((k, v) in checksums) {
                    wb.put(k.byteArray, v.array())
                }
                for ((k, v) in appVersions) {
                    wb.put(k.asByteArray(), OBJECT_MAPPER.writeValueAsBytes(v.prepareAppVersion()))
                }
                db.write(wb)
            }
    }
}

class SerializableChecksumsChooser(
    pathToCsv: String,
    csDict: Map<Checksum, CGE>,
    avDict: FMutableMap<AppVersion, AppVersionGraphEntry>,
    val maxChecksums: Int
) {

    private val depths: Map<CGE, IntOpenHashSet> = buildDepths(
        pathToCsv,
        csDict = csDict,
        avDict = avDict.entries.asSequence()
            .flatMap { (k, v) -> k.appVersions().asSequence().map { it to v } }
            .toMap(FMutableMap())
    )

    data class SerializableCGE(
        val entry: CGE,
        val depths: IntOpenHashSet
    )

    fun chooseFor(av: AppVersionGraphEntry): List<SerializableCGE> {
        val checksums = av.checksums.map { SerializableCGE(it, depths[it] ?: IntMutableSet()) }
        val checksumsSortedByDependsOnSize = checksums.sortedBy { cs -> cs.entry.dependsOn.size }
        val firstMaxChecksums = checksumsSortedByDependsOnSize.take(maxChecksums)
        val checksumsByWhichWeCanDeducePath = checksums.filter { it.depths.size == 1 }
        return if (firstMaxChecksums.none { it.depths.size == 1 } && checksumsByWhichWeCanDeducePath.isNotEmpty()) {
            val toBeReplaced = (firstMaxChecksums.size / 2).coerceAtMost(checksumsByWhichWeCanDeducePath.size)
            firstMaxChecksums.dropLast(toBeReplaced) + checksumsByWhichWeCanDeducePath.take(toBeReplaced)
        } else {
            firstMaxChecksums
        }
    }
}
package kr3v.webdetect.graph

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kr3v.webdetect.AppVersion
import kr3v.webdetect.Checksum
import kr3v.webdetect.FMutableMap
import kr3v.webdetect.IntMutableSet
import kr3v.webdetect.OBJECT_MAPPER
import kr3v.webdetect.buildDepths
import kr3v.webdetect.util.asByteArray
import kr3v.webdetect.util.deleteDirectoryRecursively
import kr3v.webdetect.util.intForEach
import org.fusesource.leveldbjni.JniDBFactory
import org.iq80.leveldb.Options
import java.io.File
import java.nio.ByteBuffer
import java.util.Optional

private typealias Checksums = FMutableMap<CGE, IntArrayList>
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
        private const val CHECKSUM_VALUE_BARRIER = -1
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
        definedAvDict.values
            .forEachIndexed { index, av -> av.properties = intArrayOf(index) }

        val checksums = definedAvDict
            .values
            .flatMap { serializableChecksumsChooser.chooseFor(it) }
            .associateTo(FMutableMap()) { (it, depths) ->
                it to IntArrayList(1 + it.dependsOn.size + 1 + depths.size).also { value ->
                    value.add(it.appVersions.single().intIndex)
                    it.dependsOn.forEach { value.add(it.intIndex) }
                    value.add(CHECKSUM_VALUE_BARRIER)
                    value.addAll(depths)
                }
            }

        val appVersions = avDict.values.associateByTo(FMutableMap(), AppVersionGraphEntry::intIndex)

        return checksums to appVersions
    }

    data class AppVersionValue(
        val av: List<AppVersion.Single>,
        val implications: IntList,
        val total: Int
    )

    private fun AppVersionGraphEntry.prepareAppVersion() = AppVersionValue(
        av = key.appVersions(),
        implications = implies.mapTo(IntArrayList()) { it.intIndex },
        total = checksums.size.coerceAtMost(serializableChecksumsChooser.maxChecksums)
    )

    private fun serializeAsJson(
        checksums: Checksums,
        appVersions: AppVersions,
        pathToJson: String
    ) {
        val map = FMutableMap<String, Any>()
        map += checksums.map { (k, v) -> k.toString() to v }
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
        fun IntArrayList.toByteArray(): ByteArray {
            val barrierIndex = indexOf(CHECKSUM_VALUE_BARRIER)
            val array = ByteArray(4 * barrierIndex + (size - barrierIndex))
            val byteBuffer = ByteBuffer.wrap(array)
            subList(0, barrierIndex).intForEach { byteBuffer.putInt(it) }
            subList(barrierIndex, size).intForEach { byteBuffer.put(it.toByte()) }
            return byteBuffer.array()
        }

        fun AppVersionValue.toByteArray(): ByteArray {
            val prepared = av.flatMap { listOf(it.app, it.version) }.map { it.toByteArray(Charsets.UTF_8) }
            val size = prepared.size + prepared.sumBy { it.size } + 1 + (1 + 4 * implications.size)
            val array = ByteArray(size)
            val byteBuffer = ByteBuffer.wrap(array)
            prepared.forEach { ba ->
                byteBuffer.put(ba)
                byteBuffer.put(0.toByte())
            }
            byteBuffer.put(0.toByte())
            byteBuffer.put(total.toByte())
            implications.intForEach { impl ->
                byteBuffer.putInt(impl)
            }
            return array
        }

        deleteDirectoryRecursively(File(pathToLevelDb).toPath())
        JniDBFactory.factory
            .open(File(pathToLevelDb), Options().createIfMissing(true))
            .use { db ->
                val wb = db.createWriteBatch()
                for ((k, v) in checksums) {
                    wb.put(k.key.byteArray, v.toByteArray())
                }
                for ((k, v) in appVersions) {
                    wb.put(k.asByteArray(), v.prepareAppVersion().toByteArray())
                }
                db.write(wb)
            }
    }
}

class SerializableChecksumsChooser(
    pathToCsv: String,
    csDict: Map<Checksum, CGE>,
    avDict: Map<AppVersion, AppVersionGraphEntry>,
    val maxChecksums: Int
) {
    private val depths: Map<CGE, IntOpenHashSet> = buildDepths(
        pathToCsv,
        csDict = csDict,
        avDict = avDict.entries
            .flatMap { (k, v) -> k.appVersions().map { it to v } }
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
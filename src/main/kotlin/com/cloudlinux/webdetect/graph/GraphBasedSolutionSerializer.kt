package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.OBJECT_MAPPER
import com.cloudlinux.webdetect.util.asByteArray
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.fusesource.leveldbjni.JniDBFactory
import org.iq80.leveldb.Options
import java.io.File
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

private typealias Checksums = MutableMap<Checksum, IntArray>
private typealias AppVersions = MutableMap<Int, List<AppVersion.Single>>

class GraphBasedSolutionSerializer(
    private val avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    private val definedAvDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    private val maxChecksums: Int
) {
    fun serialize(
        jsonOut: Optional<String> = Optional.empty(),
        levelDbOut: Optional<String> = Optional.empty()
    ) {
        if (!jsonOut.isPresent && !levelDbOut.isPresent) return
        val (checksums, appVersions) = prepareToSerialize()
        jsonOut.ifPresent { serializeAsJson(checksums, appVersions, it) }
        levelDbOut.ifPresent { serializeAsLevelDb(checksums, appVersions, it) }
        checksums.clear()
        checksums.trim()
        appVersions.clear()
        appVersions.trim()
    }

    private fun prepareToSerialize(): Pair<Checksums, AppVersions> {
        val idx = AtomicInteger()
        val avToInt = avDict.mapValuesTo(Object2IntOpenHashMap()) { idx.getAndIncrement() }
        avToInt.defaultReturnValue(-1)

        val checksums = definedAvDict
            .values
            .map {
                it.checksums
                    .sortedBy { cs -> cs.dependsOn.size }
                    .take(maxChecksums)
            }
            .flatten()
            .associateTo(MutableMap()) {
                val appVersionsAndListOfDependencies = IntArray(1 + it.dependsOn.size)
                appVersionsAndListOfDependencies[0] = avToInt.getInt(it.appVersions.single().key)
                it.dependsOn.forEachIndexed { index, value ->
                    appVersionsAndListOfDependencies[index + 1] = avToInt.getInt(value.key)
                }
                it.key to appVersionsAndListOfDependencies
            }

        val appVersions = avDict.keys.associateTo(MutableMap()) { k -> avToInt.getInt(k) to k.appVersions() }

        return checksums to appVersions
    }

    private fun serializeAsJson(
        checksums: Checksums,
        appVersions: AppVersions,
        pathToJson: String
    ) {
        val map = MutableMap<String, Any>()
        for ((k, v) in checksums) {
            map[k.toString()] = v
        }
        for ((k, v) in appVersions) {
            map[k.toString()] = v
        }
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
        JniDBFactory.factory
            .open(File(pathToLevelDb), Options().createIfMissing(true))
            .use { db ->
                val wb = db.createWriteBatch()
                for ((k, v) in checksums) {
                    wb.put(k.asByteArray(), v.asByteArray())
                }
                for ((k, v) in appVersions) {
                    wb.put(k.asByteArray(), OBJECT_MAPPER.writeValueAsBytes(v))
                }
                db.write(wb)
            }
    }
}
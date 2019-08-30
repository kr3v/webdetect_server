package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.OBJECT_MAPPER
import com.cloudlinux.webdetect.util.asByteArray
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.fusesource.leveldbjni.JniDBFactory
import org.iq80.leveldb.Options
import java.io.File
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

private typealias Checksums = FMutableMap<Checksum, IntArray>
private typealias AppVersions = FMutableMap<Int, AppVersionGraphEntry>
private typealias AppVersionsReversed = Object2IntOpenHashMap<AppVersion>

class GraphBasedSolutionSerializer(
    private val avDict: FMutableMap<AppVersion, AppVersionGraphEntry>,
    private val definedAvDict: Map<AppVersion, AppVersionGraphEntry>,
    private val maxChecksums: Int
) {
    fun serialize(
        jsonOut: Optional<String> = Optional.empty(),
        levelDbOut: Optional<String> = Optional.empty()
    ) {
        if (!jsonOut.isPresent && !levelDbOut.isPresent) return
        val (checksums, appVersions, avR) = prepareToSerialize()
        jsonOut.ifPresent { serializeAsJson(checksums, appVersions, avR, it) }
        levelDbOut.ifPresent { serializeAsLevelDb(checksums, appVersions, it) }
        checksums.clear()
        checksums.trim()
        appVersions.clear()
        appVersions.trim()
    }

    private fun prepareToSerialize(): Triple<Checksums, AppVersions, AppVersionsReversed> {
        val idx = AtomicInteger()
        val avToInt = avDict.mapValuesTo(Object2IntOpenHashMap()) { idx.getAndIncrement() }
        avToInt.defaultReturnValue(-1)

        val checksums = definedAvDict
            .values
            .flatMap {
                it.checksums
                    .sortedBy { cs -> cs.dependsOn.size }
                    .take(maxChecksums)
            }
            .associateTo(FMutableMap()) {
                val avsAndDependsOn = IntArray(1 + it.dependsOn.size)
                avsAndDependsOn[0] = avToInt.getInt(it.appVersions.single().key)
                it.dependsOn.forEachIndexed { idx, value -> avsAndDependsOn[idx + 1] = avToInt.getInt(value.key) }
                it.key to avsAndDependsOn
            }

        val appVersions = avDict.values.associateByTo(FMutableMap()) { k -> avToInt.getInt(k.key) }

        return Triple(checksums, appVersions, avToInt)
    }

    private fun serializeAsJson(
        checksums: Checksums,
        appVersions: AppVersions,
        appVersionsReversed: AppVersionsReversed,
        pathToJson: String
    ) {
        val map = FMutableMap<String, Any>()
        for ((k, v) in checksums) {
            map[k.toString()] = v
        }
        for ((k, v) in appVersions) {
            map[k.toString()] = mapOf(
                "av" to v.key.appVersions(),
                "impl" to v.implies.map { appVersionsReversed.getInt(it.key) },
                "total" to v.checksums.size.coerceAtMost(maxChecksums)
            )
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
                    wb.put(k.byteArray, v.asByteArray())
                }
                for ((k, v) in appVersions) {
                    wb.put(k.asByteArray(), OBJECT_MAPPER.writeValueAsBytes(v))
                }
                db.write(wb)
            }
    }
}
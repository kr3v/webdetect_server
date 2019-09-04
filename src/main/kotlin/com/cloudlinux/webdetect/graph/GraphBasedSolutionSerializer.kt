package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.OBJECT_MAPPER
import com.cloudlinux.webdetect.util.asByteArray
import com.cloudlinux.webdetect.util.deleteDirectoryRecursively
import org.fusesource.leveldbjni.JniDBFactory
import org.iq80.leveldb.Options
import java.io.File
import java.util.Optional

private typealias Checksums<C> = FMutableMap<C, IntArray>
private typealias AppVersions<C> = FMutableMap<Int, AppVersionGraphEntry<C>>

var HasIntProperties.intIndex
    get() = properties[0]
    set(value) {
        properties[0] = value
    }

class GraphBasedSolutionSerializer<C : ChecksumKey<C>>(
    private val avDict: FMutableMap<AppVersion, AppVersionGraphEntry<C>>,
    private val definedAvDict: Map<AppVersion, AppVersionGraphEntry<C>>,
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
    }

    private fun prepareToSerialize(): Pair<FMutableMap<C, IntArray>, FMutableMap<Int, AppVersionGraphEntry<C>>> {
        definedAvDict.values.forEachIndexed { index, appVersionGraphEntry ->
            appVersionGraphEntry.properties = intArrayOf(index)
        }

        val checksums = definedAvDict
            .values
            .flatMap {
                it.checksums
                    .sortedBy { cs -> cs.dependsOn.size }
                    .take(maxChecksums)
            }
            .associateTo(FMutableMap()) {
                val avsAndDependsOn = IntArray(1 + it.dependsOn.size)
                avsAndDependsOn[0] = it.appVersions.single().intIndex
                it.dependsOn.forEachIndexed { idx, value -> avsAndDependsOn[idx + 1] = value.intIndex }
                it.key to avsAndDependsOn
            }

        val appVersions = avDict.values.associateByTo(FMutableMap(), AppVersionGraphEntry<C>::intIndex)

        return checksums to appVersions
    }

    private fun AppVersionGraphEntry<C>.prepareAppVersion() = mapOf(
        "av" to key.appVersions(),
        "impl" to implies.map { it.intIndex },
        "total" to checksums.size.coerceAtMost(maxChecksums)
    )

    private fun serializeAsJson(
        checksums: Checksums<C>,
        appVersions: AppVersions<C>,
        pathToJson: String
    ) {
        val map = FMutableMap<String, Any>()
        map += checksums.mapKeys { (k, _) -> k.toString() }
        map += appVersions.map { (k, v) -> k.toString() to v.prepareAppVersion() }
        OBJECT_MAPPER.writeValue(
            File(pathToJson),
            map
        )
    }

    private fun serializeAsLevelDb(
        checksums: Checksums<C>,
        appVersions: AppVersions<C>,
        pathToLevelDb: String
    ) {
        deleteDirectoryRecursively(File(pathToLevelDb).toPath())
        JniDBFactory.factory
            .open(File(pathToLevelDb), Options().createIfMissing(true))
            .use { db ->
                val wb = db.createWriteBatch()
                for ((k, v) in checksums) {
                    wb.put(k.asByteArray(), v.asByteArray())
                }
                for ((k, v) in appVersions) {
                    wb.put(k.asByteArray(), OBJECT_MAPPER.writeValueAsBytes(v.prepareAppVersion()))
                }
                db.write(wb)
            }
    }
}

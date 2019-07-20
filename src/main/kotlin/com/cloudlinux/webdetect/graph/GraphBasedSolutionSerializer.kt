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

    private fun prepareToSerialize(): Pair<MutableMap<Checksum, IntArray>, MutableMap<Int, AppVersionValue.Value>> {
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

        val appVersions = avDict.entries.associateTo(MutableMap()) { (it, v) ->
            val value = when (it) {
                is AppVersion.Single -> AppVersionValue.Value.Single(it.app, it.version)
                is AppVersion.Merged -> when {
                    it.appVersions.size == 1 -> {
                        val (app, version) = it.appVersions().single()
                        AppVersionValue.Value.Single(app, version)
                    }
                    else -> {
                        val m = it.appVersions().groupBy(AppVersion.Single::app)
                        if (m.size == 1) {
                            val (app, versions) = m.entries.single()
                            AppVersionValue.Value.MergedWithSingleApp(app, versions.map(AppVersion.Single::version))
                        } else {
                            AppVersionValue.Value.Merged(m.mapValues { (_, v) -> v.map(AppVersion.Single::version) })
                        }
                    }
                }
            }
            avToInt.getInt(v.key) to value
        }

        return checksums to appVersions
    }

    private fun serializeAsJson(
        checksums: Map<Checksum, IntArray>,
        appVersions: Map<Int, AppVersionValue.Value>,
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
        checksums: Map<Checksum, IntArray>,
        appVersions: Map<Int, AppVersionValue.Value>,
        pathToLevelDb: String
    ) {
        JniDBFactory.factory.open(File(pathToLevelDb), Options().also { it.createIfMissing(true) }).use { db ->
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

    private data class AppVersionValue(
        val value: Value,
        val type: Type = value.type
    ) {
        sealed class Value {
            abstract val type: Type

            data class Single(
                val app: String,
                val version: String
            ) : Value() {
                override val type: Type get() = Type.SINGLE
            }

            data class MergedWithSingleApp(
                val app: String,
                val versions: List<String>
            ) : Value() {
                override val type: Type get() = Type.MERGED_WITHIN_SINGLE_APP
            }

            data class Merged(
                val m: Map<String, List<String>>
            ) : Map<String, List<String>> by m, Value() {
                override val type: Type get() = Type.MERGED
            }
        }

        enum class Type {
            SINGLE,
            MERGED_WITHIN_SINGLE_APP,
            MERGED
        }
    }
}
package com.cloudlinux.webdetect

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import java.io.File


private val mapper = CsvMapper().also {
    it.enable(CsvParser.Feature.WRAP_AS_ARRAY)
}

val checksumToAppVersions: MutableMap<Checksum, MutableSet<AppVersion>> = MutableMap()
val appVersions: MutableSet<AppVersion> = MutableSet()

// tab-separated
fun read(path: String): Pair<MutableMap<Checksum, MutableSet<AppVersion>>, MutableSet<AppVersion>> {
    val csvFile = File(path)
    val schema = mapper.schemaFor(Array<String>::class.java).withColumnSeparator('\t')
    val it = mapper.readerFor(Array<String>::class.java).with(schema).readValues<Array<String>>(csvFile)

    while (it.hasNext()) {
        val row = it.next().dropLastWhile { it.isBlank() }
        val (app, version, checksum) = when {
            row.size > 3 -> listOf(
                row[0],
                (0 until row.lastIndex).joinToString(separator = ".") { row[it] },
                row.last()
            )
            row.size == 3 -> row
            else -> throw Exception(row.toString())
        }
        `do`(app, version, checksum)
    }

    return checksumToAppVersions to appVersions
}

// manually parsed 2-tab separated (app, version, hash)
fun readV2(path: String): Pair<MutableMap<Checksum, MutableSet<AppVersion>>, MutableSet<AppVersion>> {
    val csvFile = File(path)

    csvFile.forEachLine { row ->
        val split = row.split("\t\t")
        if (split.size == 1) return@forEachLine
        if (split.size != 4) throw Exception(row)
        `do`(split[0], split[1], split[2])
    }

    return checksumToAppVersions to appVersions
}

// manually parsed 2-tab separated (app, ?, hash, path)
fun readV3(path: String): Pair<MutableMap<Checksum, MutableSet<AppVersion>>, MutableSet<AppVersion>> {
    val csvFile = File(path)

    csvFile.forEachLine { row ->
        val split = row.split("\t\t")
        if (split.size == 1) return@forEachLine
        if (split.size != 4) throw Exception(row)
        `do`(split[0], split[3].split("/")[4], split[2])
    }

    return checksumToAppVersions to appVersions
}

private fun `do`(
    app: String,
    version: String,
    checksum: String
) {
    val av = appVersion(app, version)
    val cs = checksum(checksum)
    appVersions += av
    checksumToAppVersions.computeIfAbsent(cs) { MutableSet() } += av
}

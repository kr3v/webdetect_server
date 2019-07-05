package com.cloudlinux.webdetect

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import java.io.File

private val mapper = CsvMapper().also {
    it.enable(CsvParser.Feature.WRAP_AS_ARRAY)
}

fun read(path: String): Pair<MutableMap<Checksum, MutableSet<AppVersion>>, MutableSet<AppVersion>> {
    val checksumToAppVersions: MutableMap<Checksum, MutableSet<AppVersion>> = MutableMap()
    val appVersions: MutableSet<AppVersion> = MutableSet()

    val csvFile = File(path)
    val it = mapper.readerFor(Array<String>::class.java).readValues<Array<String>>(csvFile)

    while (it.hasNext()) {
        val row = it.next().dropLastWhile { it.isBlank() }
        val (app, version, checksum) = if (row.size != 3) {
            listOf(row[0], (0 until row.lastIndex).joinToString(separator = ".") { row[it] }, row.last())
        } else {
            row
        }
        val av = appVersion(app, version)
        val cs = checksum(checksum)
        appVersions += av
        checksumToAppVersions.computeIfAbsent(cs) { MutableSet() } += av
    }

    return checksumToAppVersions to appVersions
}
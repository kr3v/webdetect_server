package kr3v.webdetect.util

import java.nio.file.Files
import java.nio.file.Path

fun deleteDirectoryRecursively(path: Path) {
    if (Files.exists(path)) {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach { it.delete() }
    }
}

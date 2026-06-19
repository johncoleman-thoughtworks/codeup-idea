package com.codeup.util

fun compareSemver(a: String, b: String): Int {
    val pa = normalizeSemver(a); val pb = normalizeSemver(b)
    for (i in 0..2) {
        val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
        if (x != y) return if (x < y) -1 else 1
    }
    return 0
}

private fun normalizeSemver(version: String): List<Int> {
    val stripped = version.removePrefix("v").split("-")[0].split("+")[0]
    val parts = stripped.split(".").map { it.toIntOrNull() ?: 0 }
    return if (parts.any { it < 0 }) listOf(0, 0, 0) else parts
}

fun isNewer(remoteVersion: String, installedVersion: String): Boolean =
    compareSemver(remoteVersion, installedVersion) > 0

fun dueForCheck(lastCheckedMs: Long?, intervalMs: Long, nowMs: Long): Boolean {
    if (lastCheckedMs == null || lastCheckedMs == 0L) return true
    return nowMs - lastCheckedMs >= intervalMs
}

data class ParsedRelease(
    val tag: String,
    val htmlUrl: String,
    val prerelease: Boolean,
)

fun parseRelease(raw: Map<String, Any?>?): ParsedRelease? {
    if (raw == null) return null
    val tag = raw["tag_name"] as? String ?: return null
    val htmlUrl = raw["html_url"] as? String ?: return null
    val prerelease = (raw["prerelease"] as? Boolean) ?: false
    return ParsedRelease(tag = tag, htmlUrl = htmlUrl, prerelease = prerelease)
}
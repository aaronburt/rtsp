package uk.co.aaronburt.rtsp.update

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        val core = compareValuesBy(this, other, Version::major, Version::minor, Version::patch)
        if (core != 0) return core
        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    companion object {
        private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(raw: String): Version? {
            val match = pattern.matchEntire(raw.trim()) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return Version(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: return null,
                patch = patch.toIntOrNull() ?: return null,
                preRelease = pre.ifEmpty { null },
            )
        }

        private fun comparePreRelease(a: String, b: String): Int {
            val left = a.split('.')
            val right = b.split('.')
            for (i in 0 until minOf(left.size, right.size)) {
                val l = left[i]
                val r = right[i]
                val ln = l.toIntOrNull()
                val rn = r.toIntOrNull()
                val cmp = when {
                    ln != null && rn != null -> ln.compareTo(rn)
                    ln != null -> -1
                    rn != null -> 1
                    else -> l.compareTo(r)
                }
                if (cmp != 0) return cmp
            }
            return left.size.compareTo(right.size)
        }
    }
}

object UpdateDecision {
    fun isNewer(remoteTag: String, installedVersionName: String): Boolean {
        val remote = Version.parse(remoteTag) ?: return false
        val installed = Version.parse(installedVersionName) ?: return true
        return remote > installed
    }

    fun shouldAutoCheck(lastCheckMs: Long, nowMs: Long, intervalMs: Long): Boolean =
        lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= intervalMs
}

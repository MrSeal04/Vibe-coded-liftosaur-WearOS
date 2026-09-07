package dev.fquo.liftwear.api

/**
 * Liftosaur returns weights and measurements as unit-suffixed strings:
 * "100kg", "180lb", "37cm", "18%". Parse once, never do string math.
 */
data class Weight(val value: Double, val unit: String) {
    override fun toString(): String {
        val v = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
        return "$v$unit"
    }

    companion object {
        private val PATTERN = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*([a-zA-Z%]+)\s*$""")

        fun parse(raw: String): Weight? {
            val m = PATTERN.matchEntire(raw) ?: return null
            val value = m.groupValues[1].toDoubleOrNull() ?: return null
            return Weight(value, m.groupValues[2])
        }
    }
}

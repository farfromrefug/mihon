package tachiyomi.domain.history.model

sealed interface HistoryDisplayMode {

    data object CompactGrid : HistoryDisplayMode
    data object ComfortableGrid : HistoryDisplayMode
    data object List : HistoryDisplayMode

    object Serializer {
        fun deserialize(serialized: String): HistoryDisplayMode {
            return HistoryDisplayMode.deserialize(serialized)
        }

        fun serialize(value: HistoryDisplayMode): String {
            return value.serialize()
        }
    }

    companion object {
        val values by lazy { setOf(CompactGrid, ComfortableGrid, List) }
        val default = List

        fun deserialize(serialized: String): HistoryDisplayMode {
            return when (serialized) {
                "COMFORTABLE_GRID" -> ComfortableGrid
                "COMPACT_GRID" -> CompactGrid
                "LIST" -> List
                else -> default
            }
        }
    }

    fun serialize(): String {
        return when (this) {
            ComfortableGrid -> "COMFORTABLE_GRID"
            CompactGrid -> "COMPACT_GRID"
            List -> "LIST"
        }
    }
}

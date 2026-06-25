package com.superdl.launcher.environment

enum class ObjectCategory(
    val id: String,
    val hungarianName: String,
    val cocoLabels: Set<String>
) {
    DOOR("door", "Ajtó", emptySet()),
    CHAIR(
        "chair",
        "Szék",
        setOf("chair", "couch", "bench")
    ),
    TABLE("table", "Asztal", setOf("dining table")),
    PERSON("person", "Személy", setOf("person")),
    FLOOR_OBJECT(
        "floor_object",
        "Földön lévő tárgy",
        setOf(
            "bottle",
            "cup",
            "book",
            "backpack",
            "handbag",
            "suitcase",
            "laptop",
            "umbrella"
        )
    ),
    PHONE("phone", "Telefon", setOf("cell phone"));

    companion object {
        private val labelToCategory: Map<String, ObjectCategory> =
            entries.flatMap { category ->
                category.cocoLabels.map { label -> label.lowercase() to category }
            }.toMap()

        fun fromCocoLabel(label: String): ObjectCategory? =
            labelToCategory[label.trim().lowercase()]
    }
}
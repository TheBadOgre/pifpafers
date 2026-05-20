package net.rafkos.neuroshima.editor.publish

data class ExportResult(
    val written: Int,
    val skipped: Int,
    val failed: List<String>,
)

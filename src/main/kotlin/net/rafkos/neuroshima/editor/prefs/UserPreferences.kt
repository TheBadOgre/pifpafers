package net.rafkos.neuroshima.editor.prefs

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val collectionThumbSize: Int = 96,
    val layersThumbSize: Int = 64,
    val assetsThumbSize: Int = 64,
    val lastOpenedBag: String? = null,
)

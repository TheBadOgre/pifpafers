package net.rafkos.neuroshima.editor.prefs

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class PrefsStore(private val file: Path) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): UserPreferences {
        if (!Files.isRegularFile(file)) return UserPreferences()
        return json.decodeFromString(UserPreferences.serializer(), Files.readString(file))
    }

    fun save(prefs: UserPreferences) {
        Files.createDirectories(file.parent)
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(tmp, json.encodeToString(UserPreferences.serializer(), prefs))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

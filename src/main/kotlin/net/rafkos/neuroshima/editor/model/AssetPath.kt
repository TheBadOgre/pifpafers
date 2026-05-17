package net.rafkos.neuroshima.editor.model

sealed class AssetPath {
    abstract val relativePath: String
    abstract val scheme: String
    val uri: String get() = "$scheme://$relativePath"

    data class Bundled(override val relativePath: String) : AssetPath() {
        override val scheme = "bundled"
    }

    data class User(override val relativePath: String) : AssetPath() {
        override val scheme = "user"
    }

    companion object {
        fun parse(uri: String): AssetPath {
            val idx = uri.indexOf("://")
            require(idx > 0) { "Malformed AssetPath URI: $uri" }
            val scheme = uri.substring(0, idx)
            val rel = uri.substring(idx + 3)
            return when (scheme) {
                "bundled" -> Bundled(rel)
                "user" -> User(rel)
                else -> throw IllegalArgumentException("Unknown AssetPath scheme: $scheme")
            }
        }
    }
}

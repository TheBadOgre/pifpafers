package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class RemoveTokenCommand(
    private val tokenId: UUID,
) : Command {
    override val label: String = "Remove token"
    private var snapshotKind: net.rafkos.neuroshima.editor.model.TokenKind? = null
    private var snapshotLayers: List<Layer> = emptyList()
    private var snapshotIndex: Int = -1

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        snapshotKind = token.kind
        snapshotLayers = token.layers.toList()
        snapshotIndex = bag.tokens.indexOfFirst { it.id == tokenId }
        bag.removeToken(tokenId)
    }

    override fun undo(bag: TokenBag) {
        val kind = snapshotKind ?: return
        val restored = Token(tokenId, kind)
        for (l in snapshotLayers) restored.addLayer(l)
        bag.addToken(restored, snapshotIndex.coerceIn(0, bag.tokens.size))
    }

    override fun mergeWith(next: Command): Command? = null
}

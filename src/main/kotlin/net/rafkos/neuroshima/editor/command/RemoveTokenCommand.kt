package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import java.util.UUID

class RemoveTokenCommand(
    private val tokenId: UUID,
) : Command {
    override val label: String = "Remove token"
    private var snapshotKind: TokenKind? = null
    private var snapshotFront: List<Layer> = emptyList()
    private var snapshotBack: List<Layer> = emptyList()
    private var snapshotIndex: Int = -1

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        snapshotKind = token.kind
        snapshotFront = token.layers(TokenSide.FRONT).toList()
        snapshotBack = token.layers(TokenSide.BACK).toList()
        snapshotIndex = bag.tokens.indexOfFirst { it.id == tokenId }
        bag.removeToken(tokenId)
    }

    override fun undo(bag: TokenBag) {
        val kind = snapshotKind ?: return
        val restored = Token(tokenId, kind)
        for (l in snapshotFront) restored.addLayer(TokenSide.FRONT, l)
        for (l in snapshotBack) restored.addLayer(TokenSide.BACK, l)
        bag.addToken(restored, snapshotIndex.coerceIn(0, bag.tokens.size))
    }

    override fun mergeWith(next: Command): Command? = null
}

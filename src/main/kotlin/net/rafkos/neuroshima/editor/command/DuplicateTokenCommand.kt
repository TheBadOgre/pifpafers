package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import java.util.UUID

class DuplicateTokenCommand(private val sourceTokenId: UUID) : Command {
    override val label: String = "Duplicate token"
    var newTokenId: UUID? = null
        private set
    private var snapshotKind: TokenKind? = null
    private var snapshotLayers: List<Layer>? = null

    override fun execute(bag: TokenBag) {
        val source = bag.findToken(sourceTokenId) ?: throw NoSuchElementException("Token $sourceTokenId")
        val id = newTokenId ?: UUID.randomUUID().also { newTokenId = it }
        val kind = source.kind.also { snapshotKind = it }
        val layers = snapshotLayers
            ?: source.layers.map { l -> Layer(UUID.randomUUID(), l.assetPath, l.props) }.also { snapshotLayers = it }
        val copy = Token(id, kind)
        for (l in layers) copy.addLayer(l)
        val idx = bag.tokens.indexOfFirst { it.id == sourceTokenId }
        bag.addToken(copy, idx + 1)
    }

    override fun undo(bag: TokenBag) {
        val id = newTokenId ?: return
        bag.removeToken(id)
    }

    override fun mergeWith(next: Command): Command? = null
}

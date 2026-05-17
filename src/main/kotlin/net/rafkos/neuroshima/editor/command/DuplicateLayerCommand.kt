package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class DuplicateLayerCommand(
    private val tokenId: UUID,
    private val sourceLayerId: UUID,
) : Command {
    override val label: String = "Duplicate layer"
    private var newLayerId: UUID? = null

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        val srcIdx = token.layers.indexOfFirst { it.id == sourceLayerId }
        if (srcIdx < 0) throw NoSuchElementException("Layer $sourceLayerId")
        val src = token.layers[srcIdx]
        val id = newLayerId ?: UUID.randomUUID().also { newLayerId = it }
        val copy = Layer(id = id, assetPath = src.assetPath, props = src.props)
        bag.addLayer(tokenId, copy, srcIdx + 1)
    }

    override fun undo(bag: TokenBag) {
        val id = newLayerId ?: return
        bag.removeLayer(tokenId, id)
    }

    override fun mergeWith(next: Command): Command? = null
}

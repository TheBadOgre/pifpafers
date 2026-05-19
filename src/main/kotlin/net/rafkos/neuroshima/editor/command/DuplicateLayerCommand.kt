package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import java.util.UUID

class DuplicateLayerCommand(
    private val tokenId: UUID,
    private val side: TokenSide,
    private val sourceLayerId: UUID,
) : Command {
    override val label: String = "Duplicate layer"
    private var newLayerId: UUID? = null

    override fun execute(bag: TokenBag) {
        val token = bag.findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId")
        val list = token.layers(side)
        val srcIdx = list.indexOfFirst { it.id == sourceLayerId }
        if (srcIdx < 0) throw NoSuchElementException("Layer $sourceLayerId on $side")
        val src = list[srcIdx]
        val id = newLayerId ?: UUID.randomUUID().also { newLayerId = it }
        val copy = Layer(id = id, assetPath = src.assetPath, props = src.props)
        bag.addLayer(tokenId, side, copy, srcIdx + 1)
    }

    override fun undo(bag: TokenBag) {
        val id = newLayerId ?: return
        bag.removeLayer(tokenId, side, id)
    }

    override fun mergeWith(next: Command): Command? = null
}

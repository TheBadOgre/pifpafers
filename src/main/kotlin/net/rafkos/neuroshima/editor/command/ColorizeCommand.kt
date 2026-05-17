package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class ColorizeCommand(
    private val tokenId: UUID,
    private val changes: List<LayerChange>,
) : Command {
    data class LayerChange(val layerId: UUID, val oldProps: LayerProperties, val newProps: LayerProperties)

    override val label: String = "Colorize"

    override fun execute(bag: TokenBag) {
        for (c in changes) bag.updateLayerProps(tokenId, c.layerId, c.newProps)
    }

    override fun undo(bag: TokenBag) {
        for (c in changes) bag.updateLayerProps(tokenId, c.layerId, c.oldProps)
    }

    override fun mergeWith(next: Command): Command? = null
}

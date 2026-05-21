package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerSnapshot
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import java.util.UUID

class PasteLayersCommand(
    private val tokenId: UUID,
    private val side: TokenSide,
    private val snapshots: List<LayerSnapshot>,
    private val insertIndex: Int,
    private val layerIds: List<UUID>,
) : Command {
    override val label: String = "Paste layers"

    init {
        require(snapshots.size == layerIds.size) { "snapshots and layerIds must have equal size" }
    }

    override fun execute(bag: TokenBag) {
        snapshots.forEachIndexed { i, snapshot ->
            val layer = Layer(id = layerIds[i], assetPath = snapshot.assetPath, props = snapshot.props)
            bag.addLayer(tokenId, side, layer, insertIndex + i)
        }
    }

    override fun undo(bag: TokenBag) {
        for (id in layerIds) {
            bag.removeLayer(tokenId, side, id)
        }
    }

    override fun mergeWith(next: Command): Command? = null
}

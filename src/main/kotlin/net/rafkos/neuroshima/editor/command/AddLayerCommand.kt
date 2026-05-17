package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.TokenBag
import java.util.UUID

class AddLayerCommand(
    private val tokenId: UUID,
    private val assetPath: AssetPath,
    private val props: LayerProperties = LayerProperties(),
    private val atIndex: Int? = null,
) : Command {
    override val label: String = "Add layer"
    private var layerId: UUID? = null

    override fun execute(bag: TokenBag) {
        val id = layerId ?: UUID.randomUUID().also { layerId = it }
        val layer = Layer(id = id, assetPath = assetPath, props = props)
        bag.addLayer(tokenId, layer, atIndex)
    }

    override fun undo(bag: TokenBag) {
        val id = layerId ?: return
        bag.removeLayer(tokenId, id)
    }

    override fun mergeWith(next: Command): Command? = null
}

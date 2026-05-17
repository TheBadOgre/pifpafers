package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultiLayerPropertyCommandTest {

    @Test
    fun `execute applies per-target new value - undo restores per-target old values`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        val l2 = Layer.create(AssetPath.Bundled("b.png"))
        t.addLayer(l1); t.addLayer(l2)

        val cmd = MultiLayerPropertyCommand(
            property = LayerProperty.OPACITY,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t.id, l1.id, oldValue = 1.0, newValue = 0.5),
                MultiLayerPropertyCommand.Target(t.id, l2.id, oldValue = 0.8, newValue = 0.3),
            ),
        )
        cmd.execute(bag)
        assertEquals(0.5f, t.layers[0].props.opacity)
        assertEquals(0.3f, t.layers[1].props.opacity)
        cmd.undo(bag)
        assertEquals(1f, t.layers[0].props.opacity)
        assertEquals(0.8f, t.layers[1].props.opacity)
    }

    @Test
    fun `relative move preserves per-layer offsets`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        val l2 = Layer.create(AssetPath.Bundled("b.png"))
        t.addLayer(l1); t.addLayer(l2)
        bag.updateLayerProps(t.id, l1.id, l1.props.copy(offsetX = 100))
        bag.updateLayerProps(t.id, l2.id, l2.props.copy(offsetX = 50))

        val delta = 20
        val cmd = MultiLayerPropertyCommand(
            property = LayerProperty.OFFSET_X,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t.id, l1.id, oldValue = 100.0, newValue = 120.0),
                MultiLayerPropertyCommand.Target(t.id, l2.id, oldValue = 50.0, newValue = 70.0),
            ),
        )
        cmd.execute(bag)
        assertEquals(120, t.findLayer(l1.id)!!.props.offsetX)
        assertEquals(70, t.findLayer(l2.id)!!.props.offsetX)
    }

    @Test
    fun `merge collapses consecutive multi commands - keeps first old values, last new values`() {
        val t = java.util.UUID.randomUUID()
        val l1 = java.util.UUID.randomUUID()
        val l2 = java.util.UUID.randomUUID()
        val first = MultiLayerPropertyCommand(
            LayerProperty.OPACITY,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t, l1, oldValue = 1.0, newValue = 0.7),
                MultiLayerPropertyCommand.Target(t, l2, oldValue = 1.0, newValue = 0.7),
            ),
        )
        val second = MultiLayerPropertyCommand(
            LayerProperty.OPACITY,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t, l1, oldValue = 0.7, newValue = 0.3),
                MultiLayerPropertyCommand.Target(t, l2, oldValue = 0.7, newValue = 0.3),
            ),
        )
        val merged = first.mergeWith(second) as MultiLayerPropertyCommand
        assertEquals(0.3, merged.targets[0].newValue, 0.001)
        assertEquals(1.0, merged.targets[0].oldValue, 0.001)
        assertEquals(0.3, merged.targets[1].newValue, 0.001)
    }
}

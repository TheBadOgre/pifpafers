package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultiLayerPropertyCommandTest {

    @Test
    fun `execute applies same value to all targets - undo restores per-target old values`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        val l2 = Layer.create(AssetPath.Bundled("b.png"))
        t.addLayer(l1); t.addLayer(l2)

        val cmd = MultiLayerPropertyCommand(
            property = LayerProperty.OPACITY,
            newValue = 0.5,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t.id, l1.id, oldValue = 1.0),
                MultiLayerPropertyCommand.Target(t.id, l2.id, oldValue = 1.0),
            ),
        )
        cmd.execute(bag)
        assertEquals(0.5f, t.layers[0].props.opacity)
        assertEquals(0.5f, t.layers[1].props.opacity)
        cmd.undo(bag)
        assertEquals(1f, t.layers[0].props.opacity)
        assertEquals(1f, t.layers[1].props.opacity)
    }

    @Test
    fun `merge collapses consecutive multi commands with identical target set and property`() {
        val t = java.util.UUID.randomUUID()
        val l1 = java.util.UUID.randomUUID()
        val l2 = java.util.UUID.randomUUID()
        val first = MultiLayerPropertyCommand(
            LayerProperty.OPACITY, newValue = 0.7,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t, l1, 1.0),
                MultiLayerPropertyCommand.Target(t, l2, 1.0),
            ),
        )
        val second = MultiLayerPropertyCommand(
            LayerProperty.OPACITY, newValue = 0.3,
            targets = listOf(
                MultiLayerPropertyCommand.Target(t, l1, 0.7),
                MultiLayerPropertyCommand.Target(t, l2, 0.7),
            ),
        )
        val merged = first.mergeWith(second) as MultiLayerPropertyCommand
        assertEquals(0.3, merged.newValue)
        assertEquals(1.0, merged.targets[0].oldValue)
    }
}

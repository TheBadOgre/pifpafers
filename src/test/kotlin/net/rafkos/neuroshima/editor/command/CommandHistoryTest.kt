package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class TrackingCommand(
    override val label: String,
    val onDo: () -> Unit,
    val onUndo: () -> Unit,
) : Command {
    override fun execute(bag: TokenBag) { onDo() }
    override fun undo(bag: TokenBag) { onUndo() }
    override fun mergeWith(next: Command): Command? = null
}

class CommandHistoryTest {

    @Test
    fun `execute pushes to done stack and runs command`() {
        val bag = TokenBag()
        val history = CommandHistory()
        var did = 0
        history.execute(bag, TrackingCommand("x", { did++ }, { }))
        assertEquals(1, did)
        assertTrue(history.canUndo())
        assertFalse(history.canRedo())
    }

    @Test
    fun `undo runs command undo and moves to undone stack`() {
        val bag = TokenBag()
        val history = CommandHistory()
        var undid = 0
        history.execute(bag, TrackingCommand("x", { }, { undid++ }))
        history.undo(bag)
        assertEquals(1, undid)
        assertFalse(history.canUndo())
        assertTrue(history.canRedo())
    }

    @Test
    fun `redo re-executes`() {
        val bag = TokenBag()
        val history = CommandHistory()
        var redid = 0
        history.execute(bag, TrackingCommand("x", { redid++ }, { }))
        history.undo(bag)
        history.redo(bag)
        assertEquals(2, redid)
        assertTrue(history.canUndo())
        assertFalse(history.canRedo())
    }

    @Test
    fun `new execute clears undone stack`() {
        val bag = TokenBag()
        val history = CommandHistory()
        history.execute(bag, TrackingCommand("a", { }, { }))
        history.undo(bag)
        history.execute(bag, TrackingCommand("b", { }, { }))
        assertFalse(history.canRedo())
    }

    @Test
    fun `clear empties both stacks`() {
        val bag = TokenBag()
        val history = CommandHistory()
        history.execute(bag, TrackingCommand("a", { }, { }))
        history.undo(bag)
        history.clear()
        assertFalse(history.canUndo())
        assertFalse(history.canRedo())
    }
}

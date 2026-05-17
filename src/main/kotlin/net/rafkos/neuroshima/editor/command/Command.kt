package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag

interface Command {
    val label: String
    fun execute(bag: TokenBag)
    fun undo(bag: TokenBag)
    fun mergeWith(next: Command): Command?
}

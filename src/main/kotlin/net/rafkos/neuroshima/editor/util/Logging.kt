package net.rafkos.neuroshima.editor.util

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

inline fun <reified T> logger(): Logger = LogManager.getLogger(T::class.java)

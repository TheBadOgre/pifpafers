package net.rafkos.neuroshima.editor.i18n

import java.text.MessageFormat
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

class LocaleService(locale: Locale = Locale.getDefault()) {

    private val bundle: ResourceBundle = ResourceBundle.getBundle("i18n.messages", locale)

    fun t(key: String, vararg args: Any?): String = try {
        val raw = bundle.getString(key)
        if (args.isEmpty()) raw else MessageFormat.format(raw, *args)
    } catch (_: MissingResourceException) {
        key
    }
}

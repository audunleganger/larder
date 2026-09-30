package com.caloriecompanion.shared.service

import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{M}+")

/**
 * Folds text for search (NF-2): lowercase, diacritics removed, and the Nordic letters that don't
 * decompose mapped to ASCII (æ→ae, ø→o), so "blabaer", "blåbær" and "BLÅBÆR" all match.
 */
fun foldForSearch(text: String): String {
    val lower = text.lowercase(Locale.ROOT).replace("æ", "ae").replace("ø", "o").replace("ß", "ss")
    return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(combiningMarks, "")
}

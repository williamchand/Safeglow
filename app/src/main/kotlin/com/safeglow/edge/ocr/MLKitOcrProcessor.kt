package com.safeglow.edge.ocr

import com.google.mlkit.vision.text.Text

/**
 * Helper conversions and token extraction logic for ML Kit Text Recognition v2.
 *
 * Keeps OCR-specific ML Kit detail out of OcrRepository.
 * Token extraction: one token per TextLine, split on comma/semicolon/newline,
 * trimmed and blank-filtered.
 *
 * Pre-filter heuristic (Pitfall 4 in RESEARCH): rejects tokens that are purely
 * numeric, match common non-INCI patterns (weight, lot, expiry labels), or are
 * shorter than 4 characters.
 */
object MLKitOcrProcessor {

    private val NON_INCI_PATTERN = Regex(
        """^(\d+(\s?[mMlLgG%])?|NET\s*WT.*|EXP.*|LOT.*|BATCH.*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Converts a ML Kit [Text] result to a list of raw ingredient tokens.
     *
     * Split strategy: comma, semicolon, newline. Each fragment is trimmed and
     * blank entries are removed. Non-ingredient noise is pre-filtered.
     */
    fun Text.toRawTokens(): List<String> =
        textBlocks
            .flatMap { block -> block.lines }
            .flatMap { line ->
                line.text
                    .split(",", ";", "\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .filter { token -> !NON_INCI_PATTERN.matches(token) }
                    .filter { token -> token.length >= 4 }
            }
}

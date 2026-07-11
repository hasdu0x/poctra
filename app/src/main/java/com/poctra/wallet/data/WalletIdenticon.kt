package com.poctra.wallet.data

object WalletIdenticon {
    private const val BackgroundColor = "#EAE8DF"
    private const val ForegroundColor = "#757571"
    private const val AccentColor = "#D9D6C8"

    fun createSvg(address: String, label: String): String {
        val letter = firstLetter(label, address)
        val safeAddress = address.takeLast(8)
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" data-letter="$letter" data-bg="$BackgroundColor" data-primary="$ForegroundColor" data-secondary="$AccentColor"><rect width="64" height="64" rx="14" fill="$BackgroundColor"/><circle cx="50" cy="14" r="9" fill="$AccentColor"/><text x="32" y="39" text-anchor="middle" font-family="monospace" font-size="27" font-weight="700" fill="$ForegroundColor">$letter</text><title>poctra wallet $safeAddress</title></svg>"""
    }

    fun previewSvg(address: String, label: String = ""): String = createSvg(address, label)

    fun matchesLabel(svg: String, address: String, label: String): Boolean {
        val expected = firstLetter(label, address)
        return svg.contains("""data-letter="$expected"""")
    }

    private fun firstLetter(label: String, address: String): String {
        val source = label.trim().ifBlank { address.trim() }.ifBlank { "W" }
        val first = source.firstOrNull { it.isLetterOrDigit() } ?: 'W'
        return first.uppercaseChar().toString()
    }
}

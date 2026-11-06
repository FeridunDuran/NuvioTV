package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.domain.model.Stream

internal fun groupStreamsByProvider(streams: List<Stream>): List<Stream> =
    streams.filterNot(::isPromoEntry).groupBy { it.addonName }.values.flatMap { addonStreams ->
        addonStreams.groupBy { it.pluginSourceName?.takeIf(String::isNotBlank) ?: it.addonName }
            .entries.sortedBy { it.key.lowercase() }
            .flatMap { orderForWeakTv(it.value) }
    }

internal fun streamProviderHeader(stream: Stream, previous: Stream? = null): String? =
    stream.pluginSourceName?.takeIf {
        it.isNotBlank() && it != stream.addonName &&
            (previous?.addonName != stream.addonName || previous.pluginSourceName != it)
    }

// Personal build: hide add-on promo entries ("Support project", "Join Discord", "Add to liked"...).
// They are not videos: no direct URL, magnet, YouTube id or resolver, only a web link, or their
// text names a promo action.
private val PROMO_PATTERN = Regex(
    "(?i)discord|support (the )?project|donat|ko-?fi|patreon|buy me a coffee|add to liked|" +
        "add to (your )?library|telegram|follow us|rate (this|us)"
)

private val promoCache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Stream, Boolean>())

private fun isPromoEntry(stream: Stream): Boolean = promoCache.getOrPut(stream) { computeIsPromo(stream) }

private fun computeIsPromo(stream: Stream): Boolean {
    val hasVideo = !stream.url.isNullOrBlank() || !stream.infoHash.isNullOrBlank() ||
        !stream.ytId.isNullOrBlank() || stream.clientResolve != null || stream.serverTarget != null ||
        stream.torrentMagnetUri() != null
    if (!hasVideo) return true
    val label = listOfNotNull(stream.name, stream.title).joinToString(" ")
    return stream.infoHash.isNullOrBlank() && PROMO_PATTERN.containsMatchIn(label)
}

// Personal build: this TV struggles with 4K HEVC. Within each provider list 1080p first, then
// 720p, then 4K, then the rest; inside each tier the most seeded torrent comes first.
// sortedWith is stable, so streams without a seed count keep the add-on's own order.
private val SEED_PATTERNS = listOf(
    Regex("👤\\s*([0-9][0-9.,]*)"),
    Regex("(?i)seed(?:er)?s?\\s*[:=]?\\s*([0-9][0-9.,]*)"),
)

private fun Stream.searchText(): String =
    listOfNotNull(name, title, description, quality).joinToString(" ")

private fun resolutionTier(stream: Stream): Int {
    val text = stream.searchText().lowercase()
    return when {
        "1080" in text -> 0
        "720" in text -> 1
        "2160" in text || "4k" in text || "uhd" in text -> 2
        else -> 3
    }
}

private fun seedCount(stream: Stream): Int {
    val text = stream.searchText()
    for (pattern in SEED_PATTERNS) {
        val raw = pattern.find(text)?.groupValues?.getOrNull(1) ?: continue
        return raw.filter(Char::isDigit).toIntOrNull() ?: continue
    }
    return -1
}

// Keys are computed once per stream (not inside the comparator) and cached by identity, because
// this runs again every time an add-on delivers results while the sources screen is open.
private val orderKeyCache = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Stream, Long>())

private fun orderKey(stream: Stream): Long =
    orderKeyCache.getOrPut(stream) {
        resolutionTier(stream).toLong() * 10_000_000_000L - seedCount(stream).coerceAtLeast(-1).toLong()
    }

private fun orderForWeakTv(streams: List<Stream>): List<Stream> =
    if (streams.size < 2) streams else streams.sortedBy(::orderKey)

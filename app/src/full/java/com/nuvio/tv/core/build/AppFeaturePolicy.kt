package com.nuvio.tv.core.build

object AppFeaturePolicy {
    val pluginsEnabled: Boolean = true
    // Personal fork: updates are built from this repository, never fetched by the app.
    val inAppUpdatesEnabled: Boolean = false
    val inAppTrailerPlaybackEnabled: Boolean = true
    val externalTrailerPlaybackEnabled: Boolean = true
    val supportNuvioEnabled: Boolean = true
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
    val imdbRatingLogoEnabled: Boolean = true
}

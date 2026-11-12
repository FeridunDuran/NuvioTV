# Nuvio TV Turbo (Android TV fork)

A personal fork of [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV), the Nuvio client for Android TV.

**Goal:** smooth, stutter-free use on a weak TV. Priorities, in order: UI fluency, memory, torrent and HTTPS playback startup.

**Target TV:** Philips QM163E, Android 8.0 (API 26), MediaTek MT5890, 32-bit `armeabi-v7a`, 1.3 GB RAM. Sister project for a Samsung Q70T Tizen TV: [NuvioTVSmart fork](https://github.com/FeridunDuran/NuvioTVSmart).

This is not an official Nuvio build. All credit for the app goes to the Nuvio team; see the upstream repository for full documentation and support links.

## What is different from upstream

| Area | Change |
|---|---|
| Updates | In-app updates are off. The build installs beside the official app as `com.nuvio.tv.turbo`. |
| Player engine | ExoPlayer only; mpv's native libraries are not packaged and engine failover is off. |
| Source list | Inside each provider: 1080p first, then 720p, then 4K, then the rest; most seeded first inside a tier. Add-on promo rows are hidden. |
| Subtitles | Add-on subtitles load after playback starts. The subtitle menu is composed only while open. |
| Torrent | The engine stays warm for 5 minutes after the player closes. The per-second diagnostic log is opt-in (`setprop log.tag.NuvioP2PDiag DEBUG`). |
| Home | No full-metadata prefetch per focused card; hero backdrop decoded at half size; image cache capped on low-RAM devices. |
| Startup | Trakt and plugin sync are delayed until after the first screen. Sentry is not started. |
| Trailers | Limited to 720p with a small buffer on low-RAM TVs. |
| UI | Screen transitions 80 ms; marquee text scrolls once; focused player controls show a label. Hero banner, poster expand and focus scale are kept. |
| Playback fix | Matroska files whose track list sits at the end of the file now expose audio and video. |
| Diagnostics | `TurboPerf` logcat tag: frame, memory and startup marks. |

Measured on the target TV (not a controlled before/after benchmark): player open to first frame went from 4 to 9 s down to about 2.3 to 5.6 s; the worst stall while opening the subtitle menu went from 3.7 s to under 1 s.

Changes live on the `turbo` branch. `main` tracks upstream.

## Build

```bash
./gradlew :app:assembleFullRelease -x lint
```

Needs JDK 21, Android SDK 36 and a `local.properties` (see `local.example.properties`); it is not committed. Only the `full` flavor and `armeabi-v7a` are built.

## License

GPL-3.0, same as upstream; see [LICENSE](LICENSE).

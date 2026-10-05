# Music Maker Sync

Self-use fork of [ercanserteli/xercamods](https://github.com/ercanserteli/xercamods), branch `neoforge-1.21.1`, baseline `d88bf6577846c505cd142ffd77eec04a11eb276d`. GPL-3.0; original authors and resources retained.

Target: Minecraft 1.21.1, NeoForge 21.1.248, Java 21. Only XercaMusicMod is built. Install the same JAR on client and dedicated server; protocol 2 requires all participants to update. Do not install official Music Maker alongside this fork (same `xercamusic` identity).

Live keyboard and MIDI events retain monotonic input timestamps. MIDI timestamps are captured before entering Minecraft's task queue. Events are batched at frames, at most 32 per packet. The server validates the held instrument or block, dimension, reach, note ranges, timestamp order, sequence and per-player limits. A fixed server timeline starts at first accepted input plus 150 ms; listeners estimate server clock offset using low-RTT probes. Input-relative intervals are preserved despite packet arrival jitter. The player hears local notes immediately. Different performers are not given a common input-time origin; this is not a promise of perfectly synchronized ensemble attacks.

Playback uses one bounded queue per remote performer, drained at render frames with a tick fallback. There are no per-note threads. Late attacks over 100 ms are dropped rather than moving the remaining timeline; releases still apply. Closing the GUI flushes releases. Disconnect, invalid instrument, removed block, dimension change and heartbeat timeout release remote voices. Instrument sounds follow their player or block; Sable storage coordinates are retained for its positional wrapper, and reach checks use its distance API.

Music sheets and Music Box playback retain the upstream playback mechanism. This fork does not add synchronized seeking or mid-song joining for recorded sheets. Available content remains upstream's 22 instruments and piano, drum kit, music box and metronome blocks; electric piano and organ are item instruments.

The paired Sound Physics Aeronautics `local.7` recognizes Music Maker NoteSound, including Sable wrappers. Music Box RECORDS notes automatically use acoustics with general record processing disabled, respect deny lists, update at 10 Hz and are excluded from Doppler pitch changes. UI sounds are not opted in.

`/musicsync` reports clock RTT, received/played/dropped events and scheduler lateness. Validation-only MIDI generation is gated by `-Dxercamusic.syncDiagnostics=true`; it is inactive normally. Actual sound onset still includes Minecraft sound-engine/OpenAL and device buffering and is not sample-accurate.

Build locally with `gradlew.bat build -PskipModPublish=true`. Normal `build` uses the workspace release hook. Optional Sable 2.0.5 and companion 1.6.0 are compile-only and are not bundled.

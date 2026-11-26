# Update client dependencies

- Apache Commons Compress 1.27.1, Apache License 2.0. Its license and notice are in `licenses/commons-compress-1.27.1-license-txt` and `licenses/commons-compress-1.27.1-notice-txt`.
- Apache Commons IO 2.18.0, Apache License 2.0. Its license and notice are in `licenses/commons-io-2.18.0-license-txt` and `licenses/commons-io-2.18.0-notice-txt`.
- `bsdiff4` 1.4.2 is used only by the release-side patch generator and is BSD-licensed; it is not bundled in the Android app.

The Commons libraries are embedded in the updater DEX to decode BSDIFF40 patch streams on Android.

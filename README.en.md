# Rain Compatibility Tools

[日本語](README.md) | English

A Root app for investigating intermittent Rain startup/reopen crashes on Android 17, with a narrowly scoped recovery for missing activity resources. **It does not claim to fix every crash.**

## Supported environment

- Rain `dev.raincord.app` 344.13 Stable (344013), also checked against the installed APK SHA-256.
- Android 17 / SDK 37, primary Android user, with Root access.
- Uses Rain’s existing embedded LSPatch engine. Do not additionally inject Vector into Rain: the two engines caused a reproducible native startup crash during testing.

## Usage

1. Install the APK and grant this app Root access.
2. Turn on “Recover missing activity resources”.
3. The change takes effect at Rain’s next natural startup. This app does not force-stop Rain or restart the device.
4. After a failure, use “Read diagnostic events” to inspect the records.

Before uninstalling this helper, turn recovery off or clear Rain’s cache.

Turning the switch off restores the original module cache extracted from the installed Rain APK. Restoration also takes effect on the next natural startup. Clearing Rain’s cache removes this repair; apply it again if needed.

## What changes

This app adds a small Kotlin hook to the extracted module at `cache/lspatch/dev.raincord.xposed/3404327306.apk`. It verifies the main APK, embedded module, current cache hashes, and cache ownership before atomically renaming a completed replacement into place. Unknown cache modifications are not overwritten.

Rain’s installed APK, account data, messages, and system services are unchanged. There is no resident service or periodic polling.

## Recovery and diagnostics

Only when Android returns no Resources while creating an activity Context, retry **the same Android method once with the same arguments**. Healthy results remain untouched; errors are not treated as success and system-wide Resources are never substituted.

Diagnostics stay in Rain’s private `files/raincompat/` directory. They record startup phases, resource retry outcomes, and Android’s previous exit reason/status/timestamp/PID. They do not record messages, tokens, screens, audio, or stack contents. Two rotating files are limited to approximately 32 KB each.

## Limitations

- The original intermittent failure does not reproduce on every launch. Long-term improvement is not established.
- This Java resource repair does not claim to fix previously observed native LSPatch / ART crashes.
- Root apply/restore and diagnostic UI support is limited to the explicitly checked build and environment.
- Rain APKs and modules are not distributed here. The app generates the cache from your supported installation locally.

## License and development

[Apache License 2.0](LICENSE). Component license information is available in the app. See [developer documentation](docs/development.md) and [validation notes](docs/validation.md).

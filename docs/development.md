# Development

## Build

Use JDK 17+, Android SDK 37, Build Tools 37.0.0, and the included Gradle wrapper. All Android implementation is Kotlin.

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
```

For a signed APK, set `ONEUI_SIGNING_PROPERTIES` to a private UTF-8 Java properties file containing `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Never commit the file or key. Use `python3 tools/package_release.py` after committing the release sources.

`dexPayload` compiles only `dev.ryuya.raincompat.payload` to a separate DEX asset. The installed helper app is NOT a Vector module. It does not contain modern module metadata or package an Xposed implementation. Rain supplies its existing LSPatch bridge (API 93) and Kotlin runtime. The payload uses the legacy bridge ABI, compiled against API 82 declarations; modern API 102 cannot be loaded as a legacy-only embedded module. No second ART hook engine is loaded.

## Checks and architecture

`CacheInstaller` accepts only the supported Rain APK and embedded module hashes. It checks the cache owner against PackageManager’s current UID, permits only the original hash or this helper’s last installed hash, and checks again before replacement. Existing module DEX and entrypoints are retained. Only this project’s payload and entrypoint are added. Embedded module signatures are removed from the generated cache: the cache is an internal code container, never an APK for installation/distribution.

`RainResourceHook` gates on Rain’s process/package and SDK 37. It resolves exact framework method signatures, rolls back partial hook installation on failure, and confines retries to activity Context creation. Successful calls take the original path; a missing result gets one original-method retry. No fallback resources, generic exception suppression, global framework modification or native ART patch is used.

`Diagnostics` writes bounded events only at startup, first activity creation, and an actual retry. It records previous process exit metadata from ApplicationExitInfo on the next launch. It has no polling thread and no long-running service. The helper reads these private records only on explicit user action through Root, avoiding package-visibility dependencies between Rain and the helper.

Applying or restoring the embedded loader updates the next startup’s cache. It does not start or stop Rain. Once this payload has loaded, the existing hook callbacks can obtain a new resource recovery implementation from the companion APK while Rain keeps running. The loader checks the companion’s installed signing certificate and version before replacing the implementation. Activity scoping, the one-attempt limit, and native exceptions remain enforced by the resident hook. The companion must be visible to Rain through Android package discovery. Changes to the legacy hook adapter itself take effect on the next normal Rain startup.

## Privacy and release

Keep device logs, third-party APKs/decompilation, keys, passwords, and generated patched caches outside Git and releases. `third_party/components.tsv` is checked against resolved dependencies and license texts are bundled. Release packaging verifies the signer, target SDK, non-debuggable variant, DEX payload equality and checksums. Japanese and English READMEs must remain in sync.

[Build environment / ビルド環境](build-environment.md)

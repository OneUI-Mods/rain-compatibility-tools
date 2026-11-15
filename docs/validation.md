# Validation scope

## Verified

- A real startup Java crash had a null activity Resources receiver at `ActivityThread.performLaunchActivity` before `Resources.addLoaders`.
- Historical native crashes also exist. Their runtime build differed from the currently inspected ART; they must not be conflated with the null Resources error.
- Adding an external Vector module to LSPatch-patched Rain produced a repeatable native crash through `Vector_.createOrUpdateClassLoaderLocked`. The experiment was disabled and its Rain scope removed. The current app contains no Vector module entry.
- The user confirmed that disabling external injection fixed the new repeatable crash. The original issue is intermittent, roughly ten incidents per day, not every launch.
- Two cold launches passed after external injection was removed. Repeated launch testing was then stopped at the user’s request.
- The internal LSPatch payload logged `READY` and Rain remained alive. Initial cross-app provider reporting was unavailable; the final source instead uses bounded target-private logs.
- Final diagnostic APK and extracted module cache were updated without starting/stopping Rain, using an atomic rename. Boot ID, system_server, SystemUI and SELinux identities remained unchanged throughout that update. No first-run diagnostic event has yet been verified; it activates on the next natural startup.
- Unit tests verify that healthy resources are untouched, recovery retries once, persistent null does not loop, and exceptions are not converted into successful resources. Release builds and lint are checked.

## Not established

- No naturally occurring missing-Resources event has yet been observed recovering with this hook. The repair is a targeted mitigation, not a proven complete resolution.
- Final diagnostic payload operation and helper UI have not been validated by repeatedly opening Rain; the user is using the app normally. Next-start diagnostic collection is intended to provide evidence without forced reproduction.
- Native LSPatch / ART failures are not fixed by a Java retry. Rare-failure frequency must be evaluated over normal use.

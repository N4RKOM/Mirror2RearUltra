# M2RU Rear Unlock

LSPosed/Vector module for [Mirror2RearUltra](../README.md). It is built and attached to every release together with the app.

Package: `com.n4r.rearunlock`.
Target app: `com.tpkarras.mirror2rearultra` only.

The legacy Xposed module hooks these firmware methods in `system_server`:

- `ActivityStarterImpl.isShouldShowOnRearDisplay(ActivityInfo)`
- `ActivityStarterImpl.isAllowedToStartOnRearDisplay(ActivityRecord)`

Only a matching target package receives `true`. Other packages keep the firmware's
original result. This does not dismiss keyguard or grant screen-capture consent.
Framework files and partitions are not modified.

## Build and enable

Build with Java 17 or newer: `./gradlew assembleDebug` in this folder.
Install the APK with `adb install -r`, or take the signed APK from the Releases page.
Enable the module in Vector/LSPosed and select **System Framework** (`system`, user 0).
The legacy callback reports package `android` and process `android` in Vector.
A reboot is required to load a changed module into the system process.

After reboot, check the framework module log for both `[M2RU-rear] hooked ...` lines.
An enabled module/saved scope alone does not prove successful loading or hooking.

## 1.0.1

- Replaced handwritten compile stubs with the official compile-only Xposed API 82.
- Fixed the binary return descriptor mismatch that caused NoSuchMethodError.
- Use separate callback instances for the two hooks, compatible with Vector's binding.
- Restrict execution to system_server and recommend the system scope.

## Rollback

Disable `com.n4r.rearunlock` in Vector/LSPosed, then reboot.
No firmware patch needs to be undone.

## 1.1.0 power compatibility

On star / API 37, the inspected firmware's legacy rear wake/sleep methods are empty.
The power adapter forwards calls only from the target app UID to the current rear
power group APIs under the power-service lock. It does not authorize other callers.
While a live client token owns the session, the rear group does not follow the main
display's power-key sleep. Explicit stop and binder death release ownership; other
sleep reasons and other displays keep their original behavior.

The accompanying app build opts out of firmware double-tap sleep through the native
window flag and uses a per-window brightness override on supporting firmware. The
system brightness setting is not overwritten. Older firmware retains sysfs control.
On-device validation on 2026-10-31 confirmed loaded hooks, rear output while the main
display was locked, and physical single/double-tap interaction after a Quick Settings
tile launch. The system rear brightness remained unchanged after those interactions.

## 1.2.0 load stamp

Once the system is up the module writes `<module versionCode>:<boot count>` to the secure
setting `m2ru_rearunlock_stamp` (boot count is `Settings.Global.BOOT_COUNT`). An installed,
enabled module with a saved scope proves nothing until it is loaded into `system_server`; the
stamp does. A stamp from an earlier boot, or left by a module that has since been disabled,
carries an older boot count and does not count. Read it without any permission:
`adb shell settings get secure m2ru_rearunlock_stamp`.

The recommended scope is declared as `android` in `xposedscope`. For legacy modules Vector/LSPosed
swap the two names, so `android` there is shown as **System Framework**; declaring `system`
would be shown as Android System. If the module was enabled before 1.2.0 with Android System
ticked, untick it: only System Framework is needed.

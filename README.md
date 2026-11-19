# Morning Lock

[![build](https://github.com/JackVermaak/morning-lock/actions/workflows/build.yml/badge.svg)](https://github.com/JackVermaak/morning-lock/actions/workflows/build.yml)
[![release](https://img.shields.io/github/v/release/JackVermaak/morning-lock)](https://github.com/JackVermaak/morning-lock/releases/latest)
[![licence: MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](LICENSE)

Gives you free phone time each morning (20 minutes by default, counted from the first unlock on or after 05:00). After that, every app except the ones you allow is blocked until you tick off your morning tasks.

## How it works

- **GuardService** (an Accessibility service) watches which app is in front. While the phone is locked, anything not allowed is covered by the lock screen. That includes the home screen, recent apps and Settings, so the guard cannot be switched off mid-lock.
- **LockActivity** shows your task checklist, buttons for your allowed apps, the Phone app, and an emergency unlock (you have to type a sentence to skip the day).
- **MainActivity** is setup: free minutes, start hour, tasks, allowed apps. Settings cannot be changed while a session is running.
- Phone calls, emergency calls, keyboards and permission prompts always work.
- On the day you install it, nothing locks. The first session is the next morning, or use **Test it now**.

## Permissions and privacy

**No data leaves the phone.** Morning Lock has no internet permission, so it cannot send anything
anywhere. Your tasks, allowed apps and settings are stored only in the app's private storage, and
backups are switched off (`allowBackup="false"`).

| Permission | Why it needs it |
| --- | --- |
| **Accessibility service** (*Morning Lock guard*) | The only way Android lets an app see which app is in front and cover it with the lock screen. The service listens only for window changes and is set up with `canRetrieveWindowContent="false"`, so it gets the app's package name and **cannot read text, passwords or anything else on the screen**. See [`guard_service.xml`](app/src/main/res/xml/guard_service.xml). |
| Ignore battery optimisations | Stops the phone from killing the guard in the background. |
| See installed apps (`<queries>` for launcher apps) | Fills the "allowed while locked" picker. |
| `WRITE_SECURE_SETTINGS` (optional) | Battery Saver and grayscale while locked. Never granted unless you run the `adb` command below yourself. |

## OxygenOS ultra power saving

Android does not allow regular apps to switch on OxygenOS's Super/Ultra power saving mode, so Morning Lock does its own app blocking. It can also turn on normal **Battery Saver** and a **grayscale** screen while the phone is locked. Both options need a one-time permission granted from a computer:

```
adb shell pm grant com.morninglock.app android.permission.WRITE_SECURE_SETTINGS
```

## Build and install

**Easiest:** download the debug APK from the [latest release](https://github.com/JackVermaak/morning-lock/releases/latest) and open it on the phone.

**From source:** You need the Android SDK (Android Studio, or the command-line tools) and JDK 17 or newer.

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then on the phone:

1. Open Morning Lock and tap **Turn on guard**, then enable *Morning Lock guard* under Accessibility. If Android says the setting is restricted (this happens with apps installed from a file), go to App info → ⋮ → *Allow restricted settings* first.
2. Tap **Let it run in the background**. Also go to Settings → Battery → Morning Lock and choose *Unrestricted*, so OxygenOS does not stop the guard.
3. Set your tasks and allowed apps, then tap **Save**.
4. Tap **Test it now** to try it out.

## Known loopholes

The lock can still be bypassed with deliberate effort: restarting in safe mode, using adb from a computer, or turning off the guard before the session starts.

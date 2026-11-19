# Morning Lock

Gives you free phone time each morning (20 minutes by default, counted from the first unlock on or after 05:00). After that, every app except the ones you allow is blocked until you tick off your morning tasks.

## How it works

- **GuardService** (an Accessibility service) watches which app is in front. While the phone is locked, anything not allowed is covered by the lock screen. That includes the home screen, recent apps and Settings, so the guard cannot be switched off mid-lock.
- **LockActivity** shows your task checklist, buttons for your allowed apps, the Phone app, and an emergency unlock (you have to type a sentence to skip the day).
- **MainActivity** is setup: free minutes, start hour, tasks, allowed apps. Settings cannot be changed while a session is running.
- Phone calls, emergency calls, keyboards and permission prompts always work.
- On the day you install it, nothing locks. The first session is the next morning, or use **Test it now**.

## OxygenOS ultra power saving

Android does not allow regular apps to switch on OxygenOS's Super/Ultra power saving mode, so Morning Lock does its own app blocking. It can also turn on normal **Battery Saver** and a **grayscale** screen while the phone is locked. Both options need a one-time permission granted from a computer:

```
adb shell pm grant com.morninglock.app android.permission.WRITE_SECURE_SETTINGS
```

## Build and install

You need the Android SDK (Android Studio, or the command-line tools) and JDK 17 or newer.

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

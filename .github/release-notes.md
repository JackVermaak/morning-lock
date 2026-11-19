A debug build of Morning Lock, so you can try it without Android Studio. Android 8.0 or newer.

**Install**

1. Download `morning-lock-*-debug.apk` on the phone and open it. Allow installs from your browser or file manager when Android asks.
2. Open Morning Lock and tap **Turn on guard**. Android blocks Accessibility for apps installed from a file at first: go to Settings → Apps → Morning Lock → ⋮ → *Allow restricted settings*, then enable *Morning Lock guard* under Accessibility.
3. Tap **Let it run in the background**, set your tasks and allowed apps, then **Save** and **Test it now**.

**Privacy:** the app has no internet permission. The guard only sees which app is in front, never what is on screen, and everything stays on the phone. See the [README](https://github.com/JackVermaak/morning-lock#permissions-and-privacy).

From v1.1 on, every APK is signed with the same debug key (kept in the repository), so a new release installs over the previous one. Coming from v1.0, uninstall it once first.

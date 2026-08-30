# Do not build from this directory

The Android app builds from the **repository root**, not from here:

```bash
cd ..           # repository root
./gradlew :app:assembleDebug
```

`android/settings.gradle.kts` and `android/gradle.properties` are the original
Windows + WSL configuration, kept so that toolchain still works. They point
`org.gradle.java.home` at a Windows JDK path and set `useLocalAar=true`, which
expects an ExecuTorch AAR built under WSL. Running `./gradlew` from this
directory on macOS or Linux fails immediately for both reasons.

The root build also lets `:app` depend on `:shared`, which is where the domain
logic and the tests live.

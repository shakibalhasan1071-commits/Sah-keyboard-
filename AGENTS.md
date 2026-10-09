# Project Workflow Rules

## Automatic APK Generation Rule
After making any code changes requested by the user, ALWAYS run `gradle assembleDebug` and copy the generated debug APK to `./apk/sah-keyboard-latest.apk` so the latest compiled binary is always ready and synced for the user.

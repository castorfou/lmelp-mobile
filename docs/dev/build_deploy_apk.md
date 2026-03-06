## build apk depuis vscode


JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/vscode/android-sdk ./gradlew assembleDebug


## deploy apk sur telephone

via adb

~/android-sdk/platform-tools/adb devices

~/android-sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk

## desinstaller (INSTALL_FAILED_UPDATE_INCOMPATIBLE Existing package com.lmelp.mobile signatures do not match newer version; ignoring!)

~/android-sdk/platform-tools/adb uninstall com.lmelp.mobile

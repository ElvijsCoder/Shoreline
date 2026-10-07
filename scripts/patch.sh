#!/bin/sh
set -e
mkdir -p android/app/src/main/res/raw
cp assets/drop.wav android/app/src/main/res/raw/drop.wav
M=android/app/src/main/AndroidManifest.xml
sed -i 's#</manifest>#<uses-permission android:name="android.permission.USE_EXACT_ALARM"/><uses-permission android:name="android.permission.VIBRATE"/></manifest>#' $M

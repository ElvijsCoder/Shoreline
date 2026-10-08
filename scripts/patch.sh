#!/bin/sh
set -e
D=android/app/src/main
mkdir -p $D/res/raw
cp assets/drop.wav $D/res/raw/drop.wav
cp -r native/res/* $D/res/
cp -r native/icons/* $D/res/
cp native/java/*.java $D/java/app/shoreline/water/
M=$D/AndroidManifest.xml
sed -i 's#</manifest>#<uses-permission android:name="android.permission.USE_EXACT_ALARM"/><uses-permission android:name="android.permission.VIBRATE"/><uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/><uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/><uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/></manifest>#' $M
sed -i 's#<activity#<activity android:screenOrientation="portrait"#' $M
python3 - <<'PY'
m='android/app/src/main/AndroidManifest.xml'
s=open(m).read();r=open('native/receivers.xml').read()
open(m,'w').write(s.replace('</application>',r+'</application>',1))
PY

#!/bin/sh
# Turns the freshly generated Capacitor Android project into Shoreline:
# native code and resources, manifest entries, signing and version numbers.
set -e
D=android/app/src/main
M=$D/AndroidManifest.xml

# Sounds, resources, icons, Java
mkdir -p $D/res/raw
cp www/drop.wav $D/res/raw/drop.wav
cp -r native/res/* $D/res/
cp -r native/icons/* $D/res/
# Copy Shoreline's own Java by name, so a leftover file from an older version can't break the build.
for f in MainActivity ShorePlugin Store Scheduler Notify ActionReceiver BootReceiver WidgetBase SmallWidget WideWidget ShorelineTile HealthSync; do
  cp native/java/$f.java $D/java/app/shoreline/water/
done

# Only the permissions Shoreline actually uses (no exact alarms, no precise location;
# Health Connect may only write water, never read)
sed -i 's#</manifest>#<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/><uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/><uses-permission android:name="android.permission.VIBRATE"/><uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/><uses-permission android:name="android.permission.health.WRITE_HYDRATION"/></manifest>#' $M
sed -i 's#<activity#<activity android:screenOrientation="portrait"#' $M
python3 - <<'PY'
m = 'android/app/src/main/AndroidManifest.xml'
s = open(m).read()
r = open('native/receivers.xml').read()
open(m, 'w').write(s.replace('</application>', r + '</application>', 1))
PY

# Android 8+ (notification channels and the APIs the native code uses)
sed -i 's/minSdkVersion = [0-9]*/minSdkVersion = 26/' android/variables.gradle

# A fixed signing key, so every build installs over the last one and keeps your data,
# and a version number that goes up with every build.
cp keys/shoreline-debug.keystore android/app/shoreline-debug.keystore
cat >> android/app/build.gradle <<'GRADLE'

android {
    signingConfigs {
        debug {
            storeFile file('shoreline-debug.keystore')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }
    }
    defaultConfig {
        versionCode((System.getenv('GITHUB_RUN_NUMBER') ?: '1') as Integer)
        versionName "1.1.${System.getenv('GITHUB_RUN_NUMBER') ?: '0'}"
    }
}
GRADLE

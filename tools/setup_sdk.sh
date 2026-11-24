#!/usr/bin/env bash
set -euo pipefail
mkdir -p /var/tmp/dl /var/tmp/jdk17
cd /var/tmp/dl

[ -f jdk17.tar.gz ] || curl -sL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
if [ ! -x /var/tmp/jdk17/bin/java ]; then
  mkdir -p /var/tmp/jdk17
  tar xzf jdk17.tar.gz -C /var/tmp/jdk17 --strip-components=1
fi

[ -f cmdtools.zip ] || curl -sL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
if [ ! -d /var/tmp/aosdk/cmdline-tools/latest ]; then
  mkdir -p /var/tmp/aosdk/cmdline-tools
  unzip -q -o cmdtools.zip -d /var/tmp/aosdk/cmdline-tools
  mv /var/tmp/aosdk/cmdline-tools/cmdline-tools /var/tmp/aosdk/cmdline-tools/latest
fi

export JAVA_HOME=/var/tmp/jdk17
export PATH=/var/tmp/jdk17/bin:/var/tmp/aosdk/cmdline-tools/latest/bin:$PATH
export ANDROID_SDK_ROOT=/var/tmp/aosdk

yes | sdkmanager --sdk_root=/var/tmp/aosdk --licenses > /dev/null 2>&1 || true
sdkmanager --sdk_root=/var/tmp/aosdk "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /var/tmp/sdk_install.log 2>&1 || { tail -60 /var/tmp/sdk_install.log; exit 1; }
echo SETUP_OK
/var/tmp/jdk17/bin/java -version 2>&1
ls /var/tmp/aosdk/build-tools/34.0.0 | head -20
ls /var/tmp/aosdk/platforms

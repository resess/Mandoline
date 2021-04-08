#! /bin/bash
export ANDROID_JARS=/Users/khaledea/Library/Android/sdk/platforms/
export PATH=$PATH:/Users/khaledea/Library/Android/sdk/build-tools/
export PATH=$PATH:/Users/khaledea/Library/Android/sdk/platform-tools/
export APK=/Users/khaledea/data/2018_just_in_time_malware_analysis/code/datasets/malware_reports/revived-malware/fraudbot/mod-apk/com.netaudio.vam_10000.apk
export OUTDIR=/Users/khaledea/data/Mandoline/temp
export PKG=com.netaudio.vam
export MODE=m
export DEVICE=emulator-5554


OUT_APK=$(basename $APK)
OUT_APK=${OUT_APK%.*}
OUT_APK=${OUT_APK}_${MODE}.apk

java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m i -a $APK -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -im $MODE -o $OUTDIR/ -sl $OUTDIR/static_log.log -pk $PKG -ml Mandoline/bytecode-gen/MandolineLogger.jar

python3 scripts/sign_apk.py $OUTDIR/$OUT_APK
python3 scripts/clean_install.py $DEVICE $OUTDIR/$OUT_APK

read -p "Play with the app, press enter to continue"

python3 scripts/extract_trace.py $DEVICE $OUTDIR/trace.log
adb uninstall $PKG

java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m g -a $APK -t $OUTDIR/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o $OUTDIR/ -sl $OUTDIR/static_log.log -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt

echo "Enter line #"
read LINE

echo "Enter variables"
read VARS


java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m $MODE -a $APK -t $OUTDIR/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o $OUTDIR/ -sl $OUTDIR/static_log.log -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt -sp $LINE -sv $VARS
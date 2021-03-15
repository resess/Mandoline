#! /bin/bash
export ANDROID_JARS=/data/khaledea/tools/Android/SDK/platforms/
export PATH=$PATH:/data/khaledea/tools/Android/SDK/build-tools/
export PATH=$PATH:/data/khaledea/tools/Android/SDK/platform-tools/
export APK=/data/khaledea/Mandoline/Dataset/1.anki/1.anki.apk
export OUTDIR=/data/khaledea/Mandoline/temp
export PKG=com.ichi2.anki
export MODE=m

OUT_APK=$(basename $APK)
OUT_APK=${OUT_APK%.*}
OUT_APK=${OUT_APK}_${MODE}.apk


java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m i -a $APK -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -im $MODE -o $OUTDIR/ -sl $OUTDIR/static_log.log -pk $PKG -ml Mandoline/bytecode-gen/MandolineLogger.jar

python3 scripts/sign_apk.py $OUTDIR/$OUT_APK
python3 scripts/clean_install.py 712KPWQ1047XXX $OUTDIR/$OUT_APK

read -p "Play with the app, press enter to continue"

python3 scripts/extract_trace.py 712KPWQ1047XXX $OUTDIR/trace.log
adb uninstall $PKG

java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m d -a $APK -t $OUTDIR/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o $OUTDIR/ -sl $OUTDIR/static_log.log -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt

echo "Enter line #"
read LINE

echo "Enter variables"
read VARS


java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m $MODE -a $APK -t $OUTDIR/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o $OUTDIR/ -sl $OUTDIR/static_log.log -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt -sp $LINE -sv $VARS
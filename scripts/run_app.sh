#! /bin/bash
export ANDROID_JARS=/Users/khaledea/Library/Android/sdk/platforms/
export PATH=$PATH:/Users/khaledea/Library/Android/sdk/build-tools/
export PATH=$PATH:/Users/khaledea/Library/Android/sdk/platform-tools/
export APK=/Users/khaledea/data/Mandoline/Dataset/1.anki/1.anki.apk
export OUTDIR=/Users/khaledea/data/Mandoline/temp
export DEVICE=712KPWQ1047761


OUT_APK=$(basename $APK)
OUT_APK=${OUT_APK%.*}
OUT_APK=${OUT_APK}_i.apk

java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m i -a $APK -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o $OUTDIR/ -sl $OUTDIR/static_log.log -lc ../DynamicSlicingCore/DynamicSlicingLoggingClasses/DynamicSlicingLogger.jar

python3 scripts/sign_apk.py $OUTDIR/$OUT_APK
python3 scripts/clean_install.py $DEVICE $OUTDIR/$OUT_APK

read -p "Play with the app, press enter to continue"

python3 scripts/extract_trace.py $DEVICE $OUTDIR/trace.log
python3 scripts/remove.py $DEVICE $OUTDIR/$OUT_APK

java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m g -a $APK -t $OUTDIR/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o $OUTDIR/ -sl $OUTDIR/static_log.log -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt

echo "Enter line #"
read LINE

echo "Enter variables"
read VARS


java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m s -a $APK -t $OUTDIR/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o $OUTDIR/ -sl $OUTDIR/static_log.log -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt -sp $LINE -sv $VARS -debug
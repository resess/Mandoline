# Mandoline


<img align="right" src="img/mandoline_Logo.png" alt="drawing" width="250"/>


This repository hosts Mandoline, an accurate, low-overhead dynamic slicer for Android. 
Mandoline automatically generates a backward dynamic slice from a user selected executed statement and variables used in the statement. Mandoline first creates an Inter-Callback Dependency Graph (icdg) from an execution trace. The user selects a node in the icdg and used variables in the node to start slicing from (slicing criterion). Mandoline is the first dynamic slicer for Android apps that accounts for data flows through fields and framework methods.


This repository also hosts the ground truth that Mandoline is evaluated on. The ground truth consists of manually generated slices of 12 applications.

## Table of Contents
1. [Pre-requisites](#pre-requisites)
2. [Building The Tool](#Building-The-Tool)
3. [Using The Tool](#Using-The-Tool)
    1. [Instrumenting](#Instrumenting)
    2. [Running apps](#Running-apps)
    3. [Generating icdg](#Generating-icdg)
    4. [Slicing](#Slicing)

---


## Pre-requisites

* Install the Android SDK and build tools: https://developer.android.com/studio/intro/update

* Install python3

    * Linux: https://docs.python-guide.org/starting/install3/linux/
    * Mac: https://docs.python-guide.org/starting/install3/osx/
    * Windows: https://docs.python.org/3/using/windows.html

* Enable developer options and usb debugging on the Android device: https://developer.android.com/studio/debug/dev-options#enable

---
## Building The Tool


Build Mandoline
```
cd Mandoline/
mvn -Dmaven.test.skip=true clean package
cd -
```

---

## Using The Tool


Setup the environment.


<pre>
export ANDROID_JARS=<b>path/to/sdk/platforms</b>
</pre>

<b>path/to/sdk/platforms: </b> Android SDK platforms path. ex: /Users/khaledea/Library/Android/SDK/platforms


<pre>
export PATH=$PATH:<b>path/to/sdk/build-tools/</b>
</pre>

<b>path/to/sdk/build-tools: </b> Android SDK build-tools path. ex: /Users/khaledea/Library/Android/SDK/build-tools/28.0.3/


<pre>
export PATH=$PATH:<b>path/to/sdk/platform-tools/</b>
</pre>

<b>path/to/sdk/platform-tools: </b> Android SDK platform-tools path. ex: /Users/khaledea/Library/Android/SDK/platform-tools/


Display the command line options using:
```
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -h
```
---

### Instrumenting

<pre>
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m i -a <b>path/to/apk</b> -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -im <b>instrument-mode</b> -o <b>path/to/output/directory</b> -pk <b>package-name </b> -ml Mandoline/bytecode-gen/MandolineLogger.jar
</pre>

<b>path/to/apk: </b> path to the apk file to instrument


<b>instrument-mode:</b> Instrumentation mode:
* m: mandoline, basic block instrumentation, thread id recording and execution time recording
* as: AndroidSlicer++, statement level instrumentation, thread id recording, field object address recording, and execution time recording

Add "j" to the end of any mode to print the jimple code instead of producing the instrumented apk. The jimple code is placed in the output directory under "jimple_code".

<b>path/to/output/directory: </b> path to directory to store instrumentation output

<b>package-name: </b> package name of the apk

Example on the anki app:
```
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m i -a Dataset/1.anki/1.anki.apk -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -im m -o outDir -pk com.ichi2.anki -ml Mandoline/bytecode-gen/MandolineLogger.jar
```

Sign the instrumented apk using the sign_apk.py script

<pre>
python3 scripts/sign_apk.py path/to/instrumented/apk
</pre>

Example: 

<pre>
python3 scripts/sign_apk.py outDir/1.anki_m.apk
</pre>

---

## Running apps

Clean up the logcat, remove old installations of the app, and install the instrumented app using the command

<pre>
python3 scripts/clean_install.py <b>device_id</b> path/to/instrumented/apk 
</pre>


<b>device_id:</b> Id of Android device to install the app on (obtainable using `adb devices`)

Example:

<pre>
python3 scripts/clean_install.py 712KPWQ104XXX outDir/1.anki_m.apk
</pre>

play with the app, then extract the trace using the extract_trace.py script


<pre>
python3 scripts/extract_trace.py <b>device_id</b> <b>trace_file</b>
</pre>

<b>trace_file:</b> trace file name to save (with path)

<pre>
python3 scripts/extract_trace.py 712KPWQ104XXX outDir/trace.log
</pre>

---

## Generating ICDG

<pre>
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m d -a <b>path/to/apk</b> -t <b>path/to/trace</b> -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o <b>path/to/output/directory</b> -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt
</pre>

<b>path/to/apk: </b> path to the original apk (not the instrumented one)

<b>path/to/trace: </b> path to the trace file saved by the extract_trace.py script

<b>path/to/output/directory: </b> same output directory where the instrumentation outputs are places

The icdg is placed in outDir with the name path/to/trace<b>_icdg.log</b>

Example on the anki app:

```
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m d -a Dataset/1.anki/1.anki.apk -t outDir/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o outDir/ -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt
```

---


## Slicing

Select a statement to slice from in the icdg, the statements numbers are on the left of each line in the icdg file, before the ", " delimiter.


<pre>
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m <b>mode</b> -a <b>path/to/apk</b> -t <b>path/to/trace</b> -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o <b>path/to/output/directory</b> -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt -sp <b>statement_number</b> -sv <b>used-variables-to-slice-from</b>
</pre>


<b>mode:</b> Slicing mode:
* m: mandoline, slice using field alias analysis
* as: AndroidSlicer++, slice without field data dependence

<b>path/to/apk: </b> path to the original apk (not the instrumented one)

<b>path/to/trace: </b> path to the trace file saved by the extract_trace.py script

<b>path/to/output/directory: </b> same output directory where the instrumentation outputs are places

<b>statement_number: </b> the statement to slice from

<b>used-variables-to-slice-from</b> list of variables used at the statement specified by -sp. The list is "-" separated. Do not include the "$" in the variable name

The slices are placed as a csv file in the output directory with the name `result_{mode}_{date}.csv`

Example: 
<pre>
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" mandoline.slicer.Slicer -m m -a Dataset/1.anki/1.anki.apk -t outDir/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o outDir/ -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt -sp 450275 -sv r1-r2
</pre>

You can also run the script `scripts/run_app.sh*` to run all the steps. Just modify the first fiew lines: the environment variables, the output directory, full path to the APK, APK package name, and tool mode. Run the script for the project's base directory.

---

# Publication

MANDOLINE: Dynamic Slicing of Android Applications with Trace-Based Alias Analysis, ICST, 2021
# Contact

If you experience any issues, please submit an issue or contact us at khaledea@ece.ub.ca

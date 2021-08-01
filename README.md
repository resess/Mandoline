# Mandoline

[![Build](https://github.com/resess/Mandoline/actions/workflows/maven.yml/badge.svg)](https://github.com/resess/Mandoline/actions/workflows/maven.yml)
![example workflow](https://github.com/resess/Mandoline/actions/workflows/maven_test.yml/badge.svg)
<img align="right" src="img/mandoline_Logo.png" alt="drawing" width="250"/>


This repository hosts Mandoline, an accurate, low-overhead dynamic slicer for Android. 
Mandoline automatically generates a backward dynamic slice from a user selected executed statement and variables used in the statement. Mandoline first creates an Inter-Callback Dependency Graph (ICDG) from an execution trace. The user selects a node in the ICDG and used variables in the node to start slicing from (slicing criterion). Mandoline is the first dynamic slicer for Android apps that accounts for data flows through fields and framework methods.


This repository also hosts the ground truth that Mandoline is evaluated on. The ground truth consists of manually generated slices of 12 applications.


<b>If you use this tool, please cite:</b>

Khaled Ahmed, Mieszko Lis, and Julia Rubin. [MANDOLINE: Dynamic Slicing of Android Applications with Trace-Based Alias Analysis](https://www.ece.ubc.ca/~mjulia/publications/Mandoline_2021.pdf). IEEE International Conference on Software Testing, Verification and Validation (ICST), 2021

## Table of Contents
1. [Pre-requisites](#pre-requisites)
2. [Building The Tool](#Building-The-Tool)
3. [Using The Tool](#Using-The-Tool)
    1. [Instrumenting](#Instrumenting)
    2. [Running apps](#Running-apps)
    3. [Generating ICDG](#Generating-icdg)
    4. [Slicing](#Slicing)

---


## Pre-requisites

* Install the Android SDK and build tools: https://developer.android.com/studio/intro/update

* Install python3

    * Linux: https://docs.python-guide.org/starting/install3/linux/
    * Mac: https://docs.python-guide.org/starting/install3/osx/
    * Windows: https://docs.python.org/3/using/windows.html

* Enable developer options and usb debugging on the Android device: https://developer.android.com/studio/debug/dev-options#enable

* Clone the dynamic slicing core: https://github.com/resess/DynamicSlicingCore

---
## Building The Tool

Build and install the dynamic slicing core, go to the core's repo: (https://github.com/resess/DynamicSlicingCore)

```
cd core/
mvn -Dmaven.test.skip=true clean install
cd -
```


Build Mandoline, go back to Mandoline's repo
```
cd Mandoline/
mvn -Dmaven.test.skip=true clean install
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
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -h
```
---

### Instrumenting

<pre>
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m i -a <b>path/to/apk</b> -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o <b>path/to/output/directory</b> -lc <b>path/to/logger/jar</b>
</pre>

<b>path/to/apk: </b> path to the apk file to instrument

The instrumentation also generates the jimple code, placed in the output directory under "jimple_code".

<b>path/to/output/directory: </b> path to directory to store instrumentation output

<b>ath/to/logger/classes: </b> path to logger JAR from the dynamic slicing core repository.

Example on the anki app:
```
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m i -a Dataset/1.anki/1.anki.apk -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o outDir -lc ../DynamicSlicingCore/DynamicSlicingLoggingClasses/DynamicSlicingLogger.jar
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
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m g -a <b>path/to/apk</b> -t <b>path/to/trace</b> -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o <b>path/to/output/directory</b> -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt
</pre>

<b>path/to/apk: </b> path to the original apk (not the instrumented one)

<b>path/to/trace: </b> path to the trace file saved by the extract_trace.py script

<b>path/to/output/directory: </b> same output directory where the instrumentation outputs are places

The ICDG is placed in outDir with the name path/to/trace<b>_icdg.log</b>

Example on the anki app:

```
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m g -a Dataset/1.anki/1.anki.apk -t outDir/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o outDir/ -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt
```

---


## Slicing

Select a statement to slice from in the ICDG, the statements numbers are on the left of each line in the ICDG file, before the ", " delimiter.


<pre>
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m s -a <b>path/to/apk</b> -t <b>path/to/trace</b> -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o <b>path/to/output/directory</b> -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt -sp <b>statement_number</b> -sv <b>used-variables-to-slice-from</b>
</pre>

<b>path/to/apk: </b> path to the original apk (not the instrumented one)

<b>path/to/trace: </b> path to the trace file saved by the extract_trace.py script

<b>path/to/output/directory: </b> same output directory where the instrumentation outputs are places

<b>statement_number: </b> the statement to slice from

<b>used-variables-to-slice-from</b> list of variables used at the statement specified by -sp. The list is "-" separated. Do not include the "$" in the variable name

The slices are placed as a csv file in the output directory with the name `result_s_{date}.csv`

Example: 
<pre>
java -cp "Mandoline/target/mandoline-jar-with-dependencies.jar:Mandoline/target/lib/*" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m s -a Dataset/1.anki/1.anki.apk -t outDir/trace.log -p $ANDROID_JARS -c FlowDroid/soot-infoflow-android/AndroidCallbacks.txt -o outDir/ -sd FlowDroid/soot-infoflow-summaries/summariesManual -tw FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt -sp 450275 -sv r1-r2
</pre>



You can also run the script `scripts/run_app.sh` to run all the steps. Just modify the first few lines: the environment variables, the output directory, full path to the APK, APK package name, and tool mode. Run the script for the project's base directory.

---

# Publication

Khaled Ahmed, Mieszko Lis, and Julia Rubin. [MANDOLINE: Dynamic Slicing of Android Applications with Trace-Based Alias Analysis](https://www.ece.ubc.ca/~mjulia/publications/Mandoline_2021.pdf). IEEE International Conference on Software Testing, Verification and Validation (ICST), 2021

# Contact

If you experience any issues, please submit an issue or contact us at khaledea@ece.ubc.ca

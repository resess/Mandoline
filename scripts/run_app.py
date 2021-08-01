import os
import sys


android_jars="/Users/khaledea/Library/Android/sdk/platforms/"
sep = os.path.sep
base_dir = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))




apk_path = sys.argv[1]
device = sys.argv[2]
out_dir = sys.argv[3]
out_apk = os.path.basename(apk_path).split(".apk")[0] + "_i.apk"

if not os.path.isdir(out_dir):
    os.makedirs(out_dir)


class_path = f"{base_dir}/Mandoline/target/mandoline-jar-with-dependencies.jar:{base_dir}/Mandoline/target/lib/*".replace("/", sep)
flowdroid_callbacks = f"{base_dir}/FlowDroid/soot-infoflow-android/AndroidCallbacks.txt".replace("/", sep)
stubdroid_summaries = f"{base_dir}/FlowDroid/soot-infoflow-summaries/summariesManual".replace("/", sep)
flowdroid_taint_wrapper = f"{base_dir}/FlowDroid/soot-infoflow/EasyTaintWrapperSource.txt".replace("/", sep)
logging_classes = f"{base_dir}/../DynamicSlicingCore/DynamicSlicingLoggingClasses/DynamicSlicingLogger.jar".replace("/", sep)

cmd = f"java -cp \"{class_path}\" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m i -a {apk_path} -p {android_jars} -c {flowdroid_callbacks} -o {out_dir}{sep} -sl {out_dir}{sep}static_log.log -lc {logging_classes} > {out_dir}{sep}instr-log.log 2>&1"
os.system(cmd)

cmd = f"python3 {base_dir}{sep}scripts{sep}sign_apk.py {out_dir}{sep}{out_apk}"
os.system(cmd)

cmd = f"python3 {base_dir}{sep}scripts{sep}clean_install.py {device} {out_dir}{sep}{out_apk}"
os.system(cmd)

input("Play with the app, press enter to continue")

cmd = f"python3 {base_dir}{sep}scripts{sep}extract_trace.py {device} {out_dir}{sep}trace.log"
os.system(cmd)

cmd = f"python3 {base_dir}{sep}scripts{sep}remove.py {device} {out_dir}{sep}{out_apk}"
os.system(cmd)

cmd = f"java -cp \"{class_path}\" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m g -a {apk_path} -t {out_dir}{sep}trace.log -p {android_jars} -c {flowdroid_callbacks} -o {out_dir}{sep} -sl {out_dir}{sep}static_log.log -sd {stubdroid_summaries} -tw {flowdroid_taint_wrapper}"
os.system(cmd)

slice_line = input("Enter line #\n")
slice_vars = input("Enter variables\n")


cmd = f"java -cp \"{class_path}\" ca.ubc.ece.resess.slicer.dynamic.mandoline.Slicer -m s -a {apk_path} -t {out_dir}{sep}trace.log -p {android_jars} -c {flowdroid_callbacks} -o {out_dir}{sep} -sl {out_dir}{sep}static_log.log -sd {stubdroid_summaries} -tw {flowdroid_taint_wrapper} -sp {slice_line} -sv {slice_vars} -debug > {out_dir}{sep}slice-file.log 2>&1"
os.system(cmd)
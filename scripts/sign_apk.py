
import os
import sys

apk_name = sys.argv[1]

scripts_dir = os.path.dirname(os.path.realpath(__file__))

os.system(f"zip {apk_name} -d META-INF/*.SF")
os.system(f"zip {apk_name} -d META-INF/*.MF")
os.system(f"zip {apk_name} -d META-INF/*.DSA")
os.system(f"zip {apk_name} -d META-INF/*.RSA")


os.system(f"jarsigner -sigalg SHA1withRSA -digestalg SHA1 " +
        f"-keystore {scripts_dir}/mandoline.keystore {apk_name} mandoline -storepass mandoline")


os.system(f"jarsigner -verify -keystore {scripts_dir}/mandoline.keystore {apk_name}")
os.system(f"zipalign -f 4 {apk_name} {apk_name}-aligned.apk")
os.system(f"mv {apk_name}-aligned.apk {apk_name}")
#!/bin/bash

rm MandolineLogger.jar
export JAVA_HOME="/Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home"
javac MandolineLogger.java MandolineWriter.java MandolineShutdown.java
jar cf MandolineLogger.jar MandolineLogger.class MandolineWriter.class MandolineShutdown.class
rm MandolineLogger.class MandolineWriter.class MandolineShutdown.class
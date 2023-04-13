#!/bin/bash

implementorJar="../../java-advanced-2023/artifacts/info.kgeorgiy.java.advanced.implementor.jar"
implementorClass="../java-solutions/info/kgeorgiy/ja/bondarev/implementor/Implementor.java"

javac -cp "$implementorJar" -d build $implementorClass

cd build
jar cfm ../implementor.jar ../MANIFEST.MF $(find . -name "Implementor*.class")
cd ..
rm -rf build

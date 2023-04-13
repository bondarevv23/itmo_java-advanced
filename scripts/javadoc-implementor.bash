#!/bin/bash

implementor="../java-solutions/info/kgeorgiy/ja/bondarev/implementor/Implementor.java"
impler="../../java-advanced-2023/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/Impler.java"
jarimpler="../../java-advanced-2023/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/JarImpler.java"
implerexception="../../java-advanced-2023/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/ImplerException.java"
directory="../javadoc"

javadoc -d $directory -private $implementor $impler $jarimpler $implerexception

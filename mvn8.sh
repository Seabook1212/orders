#!/usr/bin/env bash
# Helper script to run Maven with Java 8

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_241.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# Run maven with all passed arguments
mvn "$@"

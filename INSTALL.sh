#!/bin/sh

# Clean m2 and gradle 
rm -rf ~/.m2 ~/.gradle

# Clean and build
./gradlew clean
./gradlew install


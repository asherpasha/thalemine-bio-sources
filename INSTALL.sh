#!/bin/sh

# Clean m2 and gradle 
rm -rf ~/.m2 
rm -rf ~/.gradle

# Clean and build
./gradlew clean
./gradlew install


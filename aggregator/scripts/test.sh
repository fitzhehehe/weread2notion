#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p sdk/build/classes sdk/build/test
find sdk/src/main -name '*.java' > sdk/build/sources.txt
javac -encoding UTF-8 -d sdk/build/classes @sdk/build/sources.txt
find sdk/src/test -name '*.java' > sdk/build/tests.txt
javac -encoding UTF-8 -cp sdk/build/classes -d sdk/build/test @sdk/build/tests.txt
java -cp sdk/build/classes:sdk/build/test com.aggregator.sdk.SdkTest
PYTHONPATH=server python3 -m unittest discover -s tests -v

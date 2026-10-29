#!/usr/bin/env sh
set -e
cd "$(dirname "$0")"
rm -rf out
javac --release 17 -encoding UTF-8 -d out $(find src -name '*.java')
echo "Compilado en la carpeta out/"

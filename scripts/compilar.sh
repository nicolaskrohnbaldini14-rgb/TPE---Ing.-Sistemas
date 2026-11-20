#!/usr/bin/env sh
set -e
# Los scripts están en scripts/, pero trabajan desde la carpeta del proyecto.
cd "$(dirname "$0")/.."
rm -rf out
javac --release 17 -encoding UTF-8 -d out $(find src -name '*.java')
echo "Compilado en la carpeta out/"

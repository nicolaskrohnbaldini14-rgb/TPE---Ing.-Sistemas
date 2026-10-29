#!/usr/bin/env sh
set -e
cd "$(dirname "$0")"
./compilar.sh
java -cp out trotamundos.App

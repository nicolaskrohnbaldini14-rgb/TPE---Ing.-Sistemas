#!/usr/bin/env sh
set -e
cd "$(dirname "$0")/.."
./scripts/compilar.sh
java -cp out trotamundos.App

#!/usr/bin/env sh
set -e
cd "$(dirname "$0")/.."
./scripts/compilar.sh
java -cp out trotamundos.PruebaFronteras
java -cp out trotamundos.PruebaOutbox
java -cp out trotamundos.PruebaFlujo

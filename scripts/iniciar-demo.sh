#!/usr/bin/env bash
set -eu
BANK_ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd)"
export MAVEN_USER_HOME="$BANK_ROOT/.cache/maven"
mkdir -p "$BANK_ROOT/.cache/tmp"
export TMPDIR="$BANK_ROOT/.cache/tmp"
exec bash "$BANK_ROOT/mvnw" "-Dmaven.repo.local=$BANK_ROOT/.cache/repository" -f "$BANK_ROOT/pom.xml" spring-boot:run -Dspring-boot.run.profiles=demo

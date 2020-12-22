#!/usr/bin/env bash

ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." >/dev/null 2>&1 && pwd )"

cd modules/alia
lein install
cd ../../modules/alia-async
lein install
cd ../../modules/alia-java-legacy-time
lein install
cd ../../modules/alia-joda-time
lein install
cd ../../modules/alia-manifold
lein install
cd ../../modules/alia-spec
lein install
cd ../../modules/alia-component
lein install

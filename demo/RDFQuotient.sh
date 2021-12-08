#!/bin/bash

VERSION="2.0"

java -Xmx90g -jar ../target/RDFQuotient-$VERSION-with-dependencies.jar "$@"

#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  start)
    exec ./gradlew bootRun --args='--regatta.demo.enabled=false'
    ;;
  test)
    exec ./gradlew test
    ;;
  *)
    echo "usage: ./do {start|test}"
    echo "  start  boot the capture app against the real S4 (demo disabled)"
    echo "  test   run the test suite"
    exit 1
    ;;
esac

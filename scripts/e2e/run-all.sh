#!/usr/bin/env bash
# 리소스 E2E 스크립트를 차례로 실행하고 결과를 요약한다. 하나라도 실패하면 exit 1
# 사용: run-all.sh            — *-e2e.sh 전체
#       run-all.sh movie search — 일부만 (리소스 이름)
set -uo pipefail

E2E_DIR="$(cd "$(dirname "$0")" && pwd)"

if ! "$E2E_DIR/server.sh" status >/dev/null 2>&1; then
  echo "E2E 서버가 떠 있지 않다. 먼저 scripts/e2e/server.sh start 를 실행한다" >&2
  exit 1
fi

names=()
if [ $# -gt 0 ]; then
  names=("$@")
else
  for f in "$E2E_DIR"/*-e2e.sh; do
    n=$(basename "$f")
    names+=("${n%-e2e.sh}")
  done
fi

results=()
failed=0
total_started=$(date +%s)
for name in "${names[@]}"; do
  script="$E2E_DIR/$name-e2e.sh"
  echo
  echo "========== $name =========="
  started=$(date +%s)
  if [ -f "$script" ] && bash "$script"; then
    results+=("PASS  $name ($(($(date +%s) - started))s)")
  else
    [ -f "$script" ] || echo "스크립트가 없다: $script" >&2
    results+=("FAIL  $name ($(($(date +%s) - started))s)")
    failed=$((failed + 1))
  fi
done

echo
echo "========== 요약 =========="
for r in "${results[@]}"; do
  echo "$r"
done
echo "전체 ${#names[@]}개, 실패 ${failed}개, $(($(date +%s) - total_started))s"
[ "$failed" -eq 0 ]

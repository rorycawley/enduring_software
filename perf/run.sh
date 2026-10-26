#!/usr/bin/env bash
# Three warmed, independent Vegeta measurements of one HTTP use case.
# API guardrails are not browser-visible feedback/completion acceptance.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

mode="${PERF_MODE:-report}"
case "$mode" in report|check) ;; *) echo "PERF_MODE must be report or check" >&2; exit 2;; esac
for required in clojure curl vegeta git java; do
  if ! command -v "$required" >/dev/null 2>&1; then
    if [[ "$required" == "vegeta" ]]; then
      echo "Vegeta is required. On macOS: brew install vegeta" >&2
    else
      echo "Missing required command: $required" >&2
    fi
    exit 1
  fi
done

rate="${PERF_RATE:-20}"
duration="${PERF_DURATION:-30s}"
warmup="${PERF_WARMUP:-5s}"
timeout="${PERF_TIMEOUT:-5s}"
run_count=3  # Keep in sync with :run-count in perf/budgets.edn.
revision="$(git rev-parse --short HEAD)"
stamp="$(date -u '+%Y%m%dT%H%M%SZ')"
results_dir="$root/perf/results/${stamp}-${revision}-$$"
mkdir -p "$results_dir"

scratch="$(mktemp -d "${TMPDIR:-/tmp}/enduring-perf.XXXXXX")"
server_pid=""
cleanup() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  rm -rf "$scratch"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

PERF_PORT_FILE="$scratch/port" clojure -M perf/start_server.clj >"$results_dir/server.log" 2>&1 &
server_pid=$!
port=""
ready=false
for ((i=0; i<120; i++)); do
  if ! kill -0 "$server_pid" 2>/dev/null; then
    echo "Benchmark app stopped during startup:" >&2
    cat "$results_dir/server.log" >&2
    exit 1
  fi
  if [[ -s "$scratch/port" ]]; then
    port="$(cat "$scratch/port")"
    if curl --silent --fail --max-time 1 --output /dev/null "http://127.0.0.1:$port/health"; then
      ready=true
      break
    fi
  fi
  sleep 0.5
done
if [[ "$ready" != true ]]; then
  echo "Benchmark app was not ready within 60 seconds." >&2
  cat "$results_dir/server.log" >&2
  exit 1
fi

url="http://127.0.0.1:$port/hello?name=Ada"
response="$(curl --silent --show-error --fail --max-time 5 "$url")"
if [[ "$response" != "Welcome to Clojure, Ada!" ]]; then
  echo "Unexpected greeting; refusing to measure: $response" >&2
  exit 1
fi

cat > "$results_dir/metadata.txt" <<META
Use case: Request a personalised greeting
Boundary: HTTP API only; NOT human-visible UI acceptance
Endpoint: GET /hello?name=Ada
Git commit: $(git rev-parse HEAD)
Git working tree: $(if [[ -z "$(git status --porcelain)" ]]; then echo clean; else echo modified; fi)
Measured at (UTC): $(date -u '+%Y-%m-%dT%H:%M:%SZ')
OS: $(uname -srm)
Java: $(java -version 2>&1 | head -n 1)
Vegeta: $(vegeta -version 2>&1 | head -n 1)
Rate: ${rate} requests/second
Warmup: ${warmup} before EACH of ${run_count} independent runs
Measured duration: ${duration} per run
Per-request timeout: ${timeout}
Evaluation mode: ${mode}
Budget: perf/budgets.edn (provisional HTTP guardrail, not human UX target)
NOTE: Compare baselines only with the SAME workload and machine class.
META

reports=()
for ((i=1; i<=run_count; i++)); do
  echo "Warm-up $i/$run_count: $url at $rate requests/second for $warmup ..."
  printf 'GET %s\n' "$url" | vegeta attack \
    -rate="${rate}/s" -duration="$warmup" -timeout="$timeout" \
    -name="hello-warmup-$i" -output=/dev/null

  echo "Measured run $i/$run_count at $rate requests/second for $duration ..."
  binary="$results_dir/run-$i.bin"
  report="$results_dir/run-$i.json"
  printf 'GET %s\n' "$url" | vegeta attack \
    -rate="${rate}/s" -duration="$duration" -timeout="$timeout" \
    -name="hello-run-$i" -output="$binary"
  vegeta report -type=text "$binary" | tee "$results_dir/run-$i.txt"
  vegeta report -type=json "$binary" > "$report"
  reports+=("$report")
done

args=(perf/budgets.edn "${reports[@]}")
if [[ "$mode" == "check" ]]; then args=(--enforce "${args[@]}"); fi
set +e
clojure -M:perf -m foo.perf.cli "${args[@]}" 2>&1 | tee "$results_dir/evaluation.txt"
evaluation_exit=${PIPESTATUS[0]}
set -e

echo "Reports saved to: $results_dir"
echo "API results do NOT prove 100 ms feedback / 1-second visible task completion."
exit "$evaluation_exit"

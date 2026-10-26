#!/usr/bin/env bash
# Repeatable, report-only HTTP latency baseline for GET /hello?name=Ada.
# Not a substitute for future browser-measured perceived-performance acceptance.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

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
ready="false"
for ((i=0; i<120; i++)); do
  if ! kill -0 "$server_pid" 2>/dev/null; then
    echo "The benchmark application stopped during startup:" >&2
    cat "$results_dir/server.log" >&2
    exit 1
  fi
  if [[ -s "$scratch/port" ]]; then
    port="$(cat "$scratch/port")"
    if curl --silent --fail --max-time 1 --output /dev/null "http://127.0.0.1:$port/health"; then
      ready="true"
      break
    fi
  fi
  sleep 0.5
done
if [[ "$ready" != "true" ]]; then
  echo "Benchmark application was not ready within 60 seconds." >&2
  cat "$results_dir/server.log" >&2
  exit 1
fi

url="http://127.0.0.1:$port/hello?name=Ada"
response="$(curl --silent --show-error --fail --max-time 5 "$url")"
if [[ "$response" != "Welcome to Clojure, Ada!" ]]; then
  echo "Unexpected greeting response; refusing to benchmark: $response" >&2
  exit 1
fi

cat > "$results_dir/metadata.txt" <<META
Use case: Request a personalised greeting
Type: HTTP API latency proxy; NOT human-visible UI responsiveness
Endpoint: GET /hello?name=Ada
Git commit: $(git rev-parse HEAD)
Git working tree: $(if [[ -z "$(git status --porcelain)" ]]; then echo clean; else echo modified; fi)
Measured at (UTC): $(date -u '+%Y-%m-%dT%H:%M:%SZ')
OS: $(uname -srm)
Java: $(java -version 2>&1 | head -n 1)
Vegeta: $(vegeta -version 2>&1 | head -n 1)
Rate: ${rate} requests/second
Warmup: ${warmup}
Measured duration: ${duration}
Per-request timeout: ${timeout}
Result: report-only baseline, not a pass/fail latency gate
META

echo "Warming up $url at $rate requests/second for $warmup ..."
printf 'GET %s\n' "$url" | vegeta attack \
  -rate="${rate}/s" -duration="$warmup" -timeout="$timeout" \
  -name="hello-warmup" -output=/dev/null

echo "Measuring at $rate requests/second for $duration ..."
printf 'GET %s\n' "$url" | vegeta attack \
  -rate="${rate}/s" -duration="$duration" -timeout="$timeout" \
  -name="hello-baseline" -output="$results_dir/samples.bin"

vegeta report -type=text "$results_dir/samples.bin" | tee "$results_dir/report.txt"
vegeta report -type=json "$results_dir/samples.bin" > "$results_dir/report.json"

echo
printf 'Report saved: %s\n' "$results_dir/report.txt"
echo "This is API latency evidence, not proof of perceived UI responsiveness."

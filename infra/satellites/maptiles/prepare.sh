#!/usr/bin/env bash
# Prepare OpenMapTiles-schema MBTiles for GTR MapLibre basemap satellite.
# Requires: Docker (full region), curl (smoke), disk for region extract.
# Git Bash / MSYS: disable path rewriting for Docker -v mounts.
set -euo pipefail
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/data"
SMOKE="${MAPTILES_SMOKE:-0}"
OUT_NAME="${MAPTILES_MBTILES:-basemap.mbtiles}"
OUT_FILE="${DATA_DIR}/${OUT_NAME}"
PLANETILER_IMAGE="${PLANETILER_IMAGE:-ghcr.io/onthegomap/planetiler:0.8.2}"
JAVA_OPTS="${PLANETILER_JAVA_OPTS:--Xmx2g}"
# Small OMT-compatible sample for server smoke (not Zimbabwe geography).
SMOKE_MBTILES_URL="${MAPTILES_SMOKE_URL:-https://github.com/maptiler/tileserver-gl/releases/download/v1.3.0/zurich_switzerland.mbtiles}"

if command -v cygpath >/dev/null 2>&1; then
  DATA_MOUNT="$(cygpath -w "${DATA_DIR}")"
else
  DATA_MOUNT="${DATA_DIR}"
fi

mkdir -p "${DATA_DIR}"

if [[ -f "${OUT_FILE}" && "${MAPTILES_FORCE:-0}" != "1" ]]; then
  echo "Using existing ${OUT_FILE} (set MAPTILES_FORCE=1 to rebuild)"
elif [[ "${SMOKE}" == "1" || "${SMOKE}" == "true" ]]; then
  echo "Smoke: downloading sample MBTiles → ${OUT_NAME}"
  echo "URL: ${SMOKE_MBTILES_URL}"
  # cd into data/ so curl -o never sees spaces in the absolute path (Win Git Bash).
  (
    cd "${DATA_DIR}"
    curl -fL --progress-bar -o "${OUT_NAME}" "${SMOKE_MBTILES_URL}"
  )
else
  REGION="${MAPTILES_REGION:-zimbabwe}"
  echo "Planetiler area=${REGION} → ${OUT_NAME}"
  echo "Note: first run downloads shared water/Natural Earth sources into data/sources/ (~1GB+)."
  echo "Image: ${PLANETILER_IMAGE}  JAVA_TOOL_OPTIONS=${JAVA_OPTS}"
  docker run --rm -t \
    -e "JAVA_TOOL_OPTIONS=${JAVA_OPTS}" \
    -v "${DATA_MOUNT}:/data" \
    "${PLANETILER_IMAGE}" \
    --download \
    --area="${REGION}" \
    --output="/data/${OUT_NAME}"
fi

echo "Done. MBTiles: ${OUT_FILE}"
echo "Start: docker compose -f docker-compose.satellites.yml --profile maptiles up -d"
echo "Style: http://127.0.0.1:\${MAPTILES_HOST_PORT:-8081}/styles/basic-preview/style.json"
echo "Env:   NEXT_PUBLIC_MAP_STYLE_URL / MAPLIBRE_STYLE_URL  (emulator: http://10.0.2.2:8081/...)"
echo "Smoke: MAPTILES_SMOKE=1 bash infra/satellites/maptiles/prepare.sh"

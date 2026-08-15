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
# Windows Docker Desktop: paths with spaces break Planetiler SQLite (SQLITE_CANTOPEN / slow NE).
# Override with a space-free junction, e.g. MAPTILES_DATA_MOUNT=C:\gtr-maptiles-data
if [[ -n "${MAPTILES_DATA_MOUNT:-}" ]]; then
  DATA_MOUNT="${MAPTILES_DATA_MOUNT}"
  echo "Using MAPTILES_DATA_MOUNT=${DATA_MOUNT}"
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
  FORCE_ARGS=()
  if [[ "${MAPTILES_FORCE:-0}" == "1" || "${MAPTILES_FORCE:-0}" == "true" ]]; then
    FORCE_ARGS+=(--force)
    echo "MAPTILES_FORCE=1 → Planetiler --force (overwrite ${OUT_NAME})"
  fi
  # Zimbabwe is landlocked — default simplified water (~23MB) not full oceans (~885MB).
  # MAPTILES_WATER=full for coastline-grade global water polygons.
  WATER_MODE="${MAPTILES_WATER:-simplified}"
  WATER_ARGS=()
  if [[ "${WATER_MODE}" == "simplified" || "${WATER_MODE}" == "simple" ]]; then
    WATER_ARGS+=(
      --water_polygons_path=data/sources/simplified-water-polygons-split-3857.zip
      --water_polygons_url=https://osmdata.openstreetmap.de/download/simplified-water-polygons-split-3857.zip
    )
    echo "Water: simplified (~23MB). Set MAPTILES_WATER=full for ~885MB oceans file."
  else
    echo "Water: full water-polygons-split-3857.zip (~885MB)."
  fi
  # Optional host-pre-unzipped Natural Earth (faster on Windows bind mounts).
  NE_ARGS=()
  NE_SQLITE=""
  for candidate in \
    "${DATA_DIR}/sources/natural_earth_vector/packages/natural_earth_vector.sqlite" \
    "${DATA_DIR}/sources/natural_earth_vector/natural_earth_vector.sqlite/packages/natural_earth_vector.sqlite"
  do
    if [[ -f "${candidate}" ]]; then
      NE_SQLITE="${candidate#"${DATA_DIR}/"}"
      break
    fi
  done
  if [[ -n "${NE_SQLITE}" ]]; then
    NE_ARGS+=(
      --natural_earth_path="data/${NE_SQLITE}"
      --natural_earth_keep_unzipped=true
    )
    echo "Natural Earth: using pre-unzipped data/${NE_SQLITE}"
  fi
  echo "Planetiler area=${REGION} → ${OUT_NAME}"
  echo "Note: first run downloads shared sources into data/sources/ (NE ~400MB + water + region OSM)."
  echo "Image: ${PLANETILER_IMAGE}  JAVA_TOOL_OPTIONS=${JAVA_OPTS}"
  docker run --rm -t \
    -e "JAVA_TOOL_OPTIONS=${JAVA_OPTS}" \
    -v "${DATA_MOUNT}:/data" \
    "${PLANETILER_IMAGE}" \
    --download \
    --area="${REGION}" \
    --output="/data/${OUT_NAME}" \
    "${WATER_ARGS[@]}" \
    "${NE_ARGS[@]}" \
    "${FORCE_ARGS[@]}"
fi

echo "Done. MBTiles: ${OUT_FILE}"
echo "Start: docker compose -f docker-compose.satellites.yml --profile maptiles up -d"
echo "Style: http://127.0.0.1:\${MAPTILES_HOST_PORT:-8081}/styles/basic-preview/style.json"
echo "Env:   NEXT_PUBLIC_MAP_STYLE_URL / MAPLIBRE_STYLE_URL  (emulator: http://10.0.2.2:8081/...)"
echo "Smoke: MAPTILES_SMOKE=1 bash infra/satellites/maptiles/prepare.sh"

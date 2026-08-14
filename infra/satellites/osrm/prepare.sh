#!/usr/bin/env bash
# Prepare OSRM MLD graph for GTR routing satellite (H6 / B-OSRM-1).
# Requires: Docker, curl, ~1–2 GB disk for Zimbabwe extract + graph.
# Git Bash / MSYS: disable path rewriting so container paths like /opt/car.lua
# are not turned into C:/Program Files/Git/opt/car.lua.
set -euo pipefail
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DATA_DIR="$(cd "$(dirname "$0")/data" && pwd)"
REGION="${OSRM_REGION:-zimbabwe}"
PBF_URL="${OSM_PBF_URL:-https://download.geofabrik.de/africa/${REGION}-latest.osm.pbf}"
PBF_FILE="${DATA_DIR}/${REGION}-latest.osm.pbf"
IMAGE="${OSRM_IMAGE:-ghcr.io/project-osrm/osrm-backend:v5.27.1}"

# Docker Desktop on Windows prefers a Windows-style host path for -v.
if command -v cygpath >/dev/null 2>&1; then
  DATA_MOUNT="$(cygpath -w "${DATA_DIR}")"
else
  DATA_MOUNT="${DATA_DIR}"
fi

mkdir -p "${DATA_DIR}"

if [[ ! -f "${PBF_FILE}" ]]; then
  echo "Downloading ${PBF_URL}"
  curl -fL --progress-bar -o "${PBF_FILE}" "${PBF_URL}"
else
  echo "Using existing ${PBF_FILE}"
fi

echo "osrm-extract…"
docker run --rm -t -v "${DATA_MOUNT}:/data" "${IMAGE}" \
  osrm-extract -p /opt/car.lua "/data/${REGION}-latest.osm.pbf"

echo "osrm-partition…"
docker run --rm -t -v "${DATA_MOUNT}:/data" "${IMAGE}" \
  osrm-partition "/data/${REGION}-latest.osrm"

echo "osrm-customize…"
docker run --rm -t -v "${DATA_MOUNT}:/data" "${IMAGE}" \
  osrm-customize "/data/${REGION}-latest.osrm"

echo "Done. Graph: ${DATA_DIR}/${REGION}-latest.osrm"
echo "Start: docker compose -f docker-compose.satellites.yml --profile routing up -d"
echo "Env:   OSRM_URL=http://127.0.0.1:\${OSRM_HOST_PORT:-5000}  OSRM_REGION=${REGION}"

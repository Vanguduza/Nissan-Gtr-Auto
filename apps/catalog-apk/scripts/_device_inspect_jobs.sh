#!/system/bin/sh
for d in files/catalog-jobs/*; do
  echo "==== $d ===="
  ls -la "$d"
  echo "--out--"
  ls -la "$d/out" 2>/dev/null | head -40
  if [ -f "$d/pause.flag" ]; then
    echo "HAS_PAUSE_FLAG"
    cat "$d/pause.flag"
  fi
  if [ -f "$d/heartbeat.json" ]; then
    echo "HEARTBEAT:"
    cat "$d/heartbeat.json"
    echo
  fi
  if [ -f "$d/out/pipeline_error.txt" ]; then
    echo "PIPELINE_ERROR:"
    head -c 2500 "$d/out/pipeline_error.txt"
    echo
  fi
  if [ -f "$d/out/argv.txt" ]; then
    echo "ARGV:"
    head -c 1500 "$d/out/argv.txt"
    echo
  fi
  echo "FILES:"
  find "$d" -type f | head -60
  echo
done

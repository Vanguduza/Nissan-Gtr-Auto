#!/system/bin/sh
# Unlimited depth enqueue helper for Catalog APK
PKG=co.zw.nissangtr.catalogapk
RECV=$PKG/.worker.DebugEnqueueReceiver
ACT=$PKG.DEBUG_ENQUEUE

am start -n $PKG/.MainActivity >/dev/null 2>&1
sleep 2

# Set prefs via first enqueue (max_pages=0, concurrent=2, do not pause others)
am broadcast -a $ACT -n $RECV \
  --es profile_id megazip \
  --es maker Nissan \
  --es model_slug frontier-2140 \
  --es model_name Frontier \
  --es chassis D40 \
  --ei max_pages 0 \
  --ei max_concurrent 2 \
  --ez pause_others false

sleep 2

am broadcast -a $ACT -n $RECV \
  --es profile_id partsouq \
  --es maker Nissan \
  --es model_slug patrol-y61 \
  --es model_name PatrolY61 \
  --es chassis Y61 \
  --ei max_pages 0 \
  --ei max_concurrent 2 \
  --ez pause_others false

sleep 2

am broadcast -a $ACT -n $RECV \
  --es profile_id 7zap \
  --es maker Nissan \
  --es model_slug patrol-y61 \
  --es model_name PatrolY61 \
  --es chassis Y61 \
  --ei max_pages 0 \
  --ei max_concurrent 2 \
  --ez pause_others false

echo DONE

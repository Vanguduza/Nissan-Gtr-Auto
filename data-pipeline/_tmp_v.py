import sqlite3
from pathlib import Path

con = sqlite3.connect(Path("out/megazip/nissan/megazip_state.db"))
rows = con.execute(
    "select page_type, length(payload_json) from parsed_pages "
    "where payload_json like '%350z-2133%' limit 10"
).fetchall()
print(rows)
url_rows = con.execute(
    "select url, page_type, status from queue where url like '%350z-2133%' or model_slug='350z-2133'"
).fetchall()
print("queue", url_rows)

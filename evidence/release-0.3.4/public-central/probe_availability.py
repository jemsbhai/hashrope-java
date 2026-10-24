"""One canonical public-POM request; append its observed status for release evidence."""
from datetime import datetime, timezone
import json
from pathlib import Path
import urllib.error
import urllib.request

URL = 'https://repo.maven.apache.org/maven2/io/github/jemsbhai/hashrope/0.3.4/hashrope-0.3.4.pom'
row = {'checked_at_utc': datetime.now(timezone.utc).isoformat(), 'url': URL}
try:
    with urllib.request.urlopen(URL, timeout=30) as response:
        row.update(status=response.status, bytes=len(response.read()), final_url=response.url)
except urllib.error.HTTPError as error:
    row.update(status=error.code)
with (Path(__file__).resolve().parent/'availability.jsonl').open('a', encoding='utf-8', newline='\n') as stream:
    stream.write(json.dumps(row)+'\n')
print(json.dumps(row))

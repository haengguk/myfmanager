"""Resume reviewed player-photos.json candidates; no identity inference or roster edits.
Usage: python3 scripts/collect-player-photos.py --cache /tmp/player-photo-originals
Requires Pillow in the execution environment, not an application dependency.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
from urllib.request import Request, urlopen
from PIL import Image, ImageOps

frontend = Path(__file__).resolve().parents[1]
mapping = frontend / 'src/features/team-player/player-photos.json'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--cache', type=Path, required=True)
args = parser.parse_args()
args.cache.mkdir(parents=True, exist_ok=True)
records = json.loads(mapping.read_text())
output = frontend / 'public/images/players'
output.mkdir(parents=True, exist_ok=True)

def collect(record):
    if record['status'] == 'acquired' and (output / (record['playerId'] + '.webp')).is_file():
        return record
    if record['status'] not in ('candidate', 'download_failed', 'acquired'):
        return record
    url = record['originalImageUrl']
    cache = args.cache / sha256(url.encode()).hexdigest()
    try:
        if cache.exists():
            data = cache.read_bytes()
        else:
            request = Request(url, headers={'User-Agent': 'LoLManagerPortraitCollection/1.0'})
            with urlopen(request, timeout=30) as response:
                if not response.headers.get_content_type().startswith('image/'):
                    raise ValueError('Server did not return an image')
                data = response.read(25 * 1024 * 1024 + 1)
            if len(data) > 25 * 1024 * 1024:
                raise ValueError('Image exceeds 25 MB download limit')
        with Image.open(BytesIO(data)) as source:
            source.load()
            picture = ImageOps.exif_transpose(source).convert('RGBA')
            if min(picture.size) < 100:
                raise ValueError(f'Insufficient portrait dimensions: {picture.size}')
            if record.get('cropBox'):
                left, top, right, bottom = record['cropBox']
                if not (0 <= left < right <= picture.width and 0 <= top < bottom <= picture.height):
                    raise ValueError('Crop box is outside source image')
                picture = picture.crop((left, top, right, bottom))
            picture.thumbnail((512, 512), Image.Resampling.LANCZOS)
            filename = record['playerId'] + '.webp'
            picture.save(output / filename, 'WEBP', quality=85, method=6)
        cache.write_bytes(data)
        record.update(status='acquired', localPath='/images/players/' + filename,
                      width=picture.width, height=picture.height, reason=None)
    except Exception as error:
        record.update(status='download_failed', localPath=None, reason=str(error))
    print(record['playerId'], record['status'], flush=True)
    return record

# Results are persisted after each completed item, so interrupted runs reuse their cache.
with ThreadPoolExecutor(max_workers=3) as executor:
    for record in executor.map(collect, records.values()):
        records[record['playerId']] = record
        temporary = mapping.with_suffix('.json.tmp')
        temporary.write_text(json.dumps(records, ensure_ascii=False, indent=2) + '\n')
        temporary.replace(mapping)

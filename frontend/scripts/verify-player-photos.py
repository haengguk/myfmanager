"""Lightweight local portrait audit. Run from any directory with Python + Pillow."""
from collections import Counter, defaultdict
from hashlib import sha256
import json
from pathlib import Path
from urllib.parse import urlparse
from PIL import Image

frontend = Path(__file__).resolve().parents[1]
players = [p for f in (frontend.parent / 'backend/src/main/resources/players').glob('*-player-identities-*.json')
           for p in json.loads(f.read_text())['players']]
photos = json.loads((frontend / 'src/features/team-player/player-photos.json').read_text())
assert len(players) == len({p['playerId'] for p in players}) == 280
assert set(photos) == {p['playerId'] for p in players}
assert all(photos[p['playerId']]['playerId'] == p['playerId'] for p in players)
print('PASS 1/7: exact 280 existing PlayerIds, no duplicates or extra IDs')

for p in players:
    record = photos[p['playerId']]
    assert record['nickname'] == p['nickname'] and record['team'] == p['team']
    assert record['status'] in ('acquired', 'missing', 'download_failed')
print('PASS 2/7: display identity and final acquisition states')

acquired = [p for p in photos.values() if p['status'] == 'acquired']
for p in photos.values():
    assert p['checkedSources'], p['playerId']
    if p['status'] == 'acquired':
        assert p['identityEvidence']
        for key in ('sourcePageUrl', 'originalImageUrl'):
            assert urlparse(p[key]).scheme == 'https', (p['playerId'], key)
        assert p['photoYear'] is None or 2009 <= p['photoYear'] <= 2026
    else:
        assert p['localPath'] is None and p['reason']
print('PASS 3/7: identity/source evidence and explicit missing-photo reasons')

files = []
for p in acquired:
    assert p['localPath'] == '/images/players/' + p['playerId'] + '.webp'
    file = frontend / 'public' / p['localPath'].lstrip('/')
    assert file.is_file()
    files.append(file)
assert set(files) == set((frontend / 'public/images/players').glob('*'))
print('PASS 4/7: safe local paths, file presence, no orphan assets')

for p, file in zip(acquired, files):
    with Image.open(file) as image:
        image.load()  # Rejects HTML/error bodies and corrupted images.
        assert image.format == 'WEBP'
        assert image.size == (p['width'], p['height'])
        assert 100 <= min(image.size) and max(image.size) <= 512
        assert file.stat().st_size < 200_000
print('PASS 5/7: WebP decoding, recorded dimensions, bounded size; no HTML responses')

hashes = defaultdict(list)
for p, file in zip(acquired, files):
    hashes[sha256(file.read_bytes()).hexdigest()].append(p['playerId'])
    with Image.open(file) as image:
        hashes['pixels:' + sha256(image.convert('RGBA').tobytes()).hexdigest()].append(p['playerId'])
duplicates = [ids for ids in hashes.values() if len(ids) > 1]
assert not duplicates, f'Suspected shared photo: review source identity before accepting: {duplicates}'
print('PASS 6/7: no identical files or decoded pixels assigned to different players')

assert all(p['originalImageUrl'] and p['sourcePageUrl'] for p in acquired)
assert not any('silhouette' in p['localPath'] or 'placeholder' in p['localPath'] for p in acquired)
print('PASS 7/7: fallback/missing entries excluded from actual photo count')
for league in ('LCK', 'LPL', 'LEC', 'LCS', 'LCP', 'CBLOL'):
    subset = [p for p in photos.values() if p['league'] == league]
    print(league, dict(Counter(p['status'] for p in subset)), 'target', len(subset))
print(f'Actual photos: {len(acquired)}/280; {sum(f.stat().st_size for f in files):,} bytes')

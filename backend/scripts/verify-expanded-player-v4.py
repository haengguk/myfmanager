#!/usr/bin/env python3
"""Linear authoring-to-runtime check; no matches, research, or data mutation."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / '선수정보/fa,2군,후보'
RUNTIME = ROOT / 'backend/src/main/resources/rosters/expanded-player-directory-v1.json'
SKILLS = dict(zip(
    'mechanics decisionMaking mapAwareness positioning combatExecution consistency csAcquisition trading waveManagement lanePressure initiativeConversion sideLaneManagement pathing jungleResourceManagement enemyJungleTracking laneIntervention objectiveDecision objectiveSecuring visionControl laneSupport roamCoordination engage allyProtection zoneSetup'.split(),
    'MECHANICS DECISION_MAKING MAP_AWARENESS POSITIONING COMBAT_EXECUTION CONSISTENCY FARMING TRADING WAVE_MANAGEMENT LANE_PRESSURE PRIORITY_CONVERSION SIDE_LANE PATHING JUNGLE_RESOURCE_MANAGEMENT ENEMY_JUNGLE_TRACKING LANE_INTERVENTION OBJECTIVE_DECISION OBJECTIVE_SECURE VISION_CONTROL LANE_SUPPORT ROTATION_PLANNING ENGAGE_EXECUTION ALLY_PROTECTION AREA_SETUP'.split()))

for line in (SOURCE / 'SHA256SUMS.txt').read_text().splitlines():
    digest, name = line.split(maxsplit=1)
    assert hashlib.sha256((SOURCE / name).read_bytes()).hexdigest() == digest, name
master = json.loads((SOURCE / 'extended-player-master-2026-09-06-v4.json').read_text())
runtime = json.loads(RUNTIME.read_text())
authored = {p['playerId']: p for p in master['players']}
assert len(authored) == len(runtime['players']) == 180
assert set(authored) == {p['playerId'] for p in runtime['players']}
assert len({o['organizationId'] for o in runtime['organizations']}) == 26
for p in runtime['players']:
    m = authored[p['playerId']]
    assert p['ratings'] == {SKILLS[k]: v for k, v in m['ratings'].items()}, p['playerId']
    assert len(p['ratings']) == 12
    for k in ['position', 'defaultLegalRoleValue', 'overrides', 'legalRoleChampionCount']:
        assert p['championProficiencies'][k] == m['championProficiencies'][k], (p['playerId'], k)
    for k in ['personal', 'contract', 'career', 'careerAuthoringDetail', 'honors', 'sources', 'rosterValidation']:
        assert p['details'][k] == m[k], (p['playerId'], k)
    assert p['details']['automaticSelectionAllowed'] is False
    assert p['details']['career']['debutDate'] is None
    assert p['details']['sourceVersion'] == master['version']
    if p['initialOrganizationId'] is not None:
        org = next(o for o in runtime['organizations'] if o['organizationId'] == p['initialOrganizationId']) if p['initialOrganizationId'] != p['initialOwnerTeam'] else None
        if org:
            assert org['competitiveTeam'] == p['initialOwnerTeam']
assert sum(len(p['championProficiencies']['overrides']) for p in runtime['players']) == 1243
assert sum(p['initialOrganizationId'] is None for p in runtime['players']) == 14
assert sum(p['details']['careerAuthoringDetail']['firstKnownRosterRecordDate'] is not None for p in runtime['players']) == 75
print('PASS: 180 IDs, 2160 rating values, 1243 authored overrides, 26 additional organizations; metadata and 27 source checksums match.')

# Pre-Jungle Tempo Runtime Baseline V1

This local immutable oracle was generated before any Jungle Tempo production code was added.

- Baseline ID: `PRE_JUNGLE_TEMPO_RUNTIME_BASELINE_V1`
- Schedule: 4 existing runtime profiles × 3 real Draft/Match cases = 12 matches
- JSON SHA-256: `17f703a48949b63bf4ca25f4b32be2bc22fac87a439cdd8cb7c18aadc7f82074`
- Engine at capture: `MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V2`
- Canonical production guard: 464 files / `674b3148d782e98753bfd9e79f8b5f78b12cc3ecea8a85bc34ff8fbcaa2def0d`
- Full regression status reused from the clean Jungle V1-A final tree: `CLEAN_PASS`

`SHA256SUMS.txt` pins the canonical CRLF source JSON bytes. Git line-ending conversion is disabled for baseline JSON, and the source-guarded generator emits CRLF independent of the host OS, refuses a different production tree, and refuses to overwrite an existing artifact with different bytes.

After Jungle Tempo V1-B, run:

```text
gradlew.bat verifyPreJungleTempoParity --console=plain
```

The comparison requires exact configuration, Draft/final assignment, complete timeline, Random draw count/trace, winner, duration, event count, and snapshot count for all 12 matches. Replay provenance is expected to differ because the engine implementation version changed from V2 to V3.

# Desktop MVP Smoke Checklist

Date baseline: 2026-02-25

## Automated pre-check
1. `./gradlew :desktop-app:compileKotlin`
2. `bash scripts/run-tests.sh`

## Manual smoke flow
1. Launch app: `./gradlew :desktop-app:run`
2. Workspace panel:
   - set repos root.
   - set sampling `%`, `seed`, and `mode`.
   - confirm sampled repo estimate updates.
3. Index panel:
   - choose `Save As...` path.
   - click `Build Index`.
   - confirm metadata updates and log entries.
4. Persistence panel:
   - click `Open Index...` on saved file.
   - confirm metadata loads without error.
5. Query panel:
   - provide a valid component ID from loaded index.
   - run query.
   - confirm ranked results and similarity values render.
6. Error handling:
   - try build with invalid repos path and confirm failure is shown in logs (no crash).
   - try query before loading index and confirm friendly error in logs.

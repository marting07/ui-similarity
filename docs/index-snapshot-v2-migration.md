# Index Snapshot v2 Migration Path

## Current runtime state
1. Load-compatible version: `v1`.
2. Latest target version: `v2` (planned).
3. Runtime compatibility report is available through:
   - `persistence.IndexSnapshotCompatibility`
   - CLI: `validate --index-file ...`

## Migration policy
1. Never silently drop fields when migrating.
2. Preserve deterministic ordering for arrays/maps where relevant.
3. Migrations must be idempotent (re-running on already-migrated file should be no-op).
4. Keep previous version reader available for one full release cycle after promotion.

## Planned v2 delta
1. Keep existing v1 payload fields.
2. Add explicit schema metadata block:
   - `schemaName`
   - `schemaVersion`
   - `createdByVersion`
3. Add optional index hash/checksum fields for integrity.

## Execution steps
1. Detect source version from JSON (`version` field).
2. If source is `v1`:
   - normalize JSON through canonical v1 serializer (`IndexSnapshotCompatibility.normalizeV1`).
   - map v1 fields into v2 schema envelope.
3. Run compatibility validation on resulting v2 file.
4. Keep original file untouched and write migrated file separately (`*.v2.json`).

## Rollback
1. Keep v1 artifact as source of truth until v2 validation passes in CI.
2. If v2 consumer regressions appear, continue operating with v1 loader path.

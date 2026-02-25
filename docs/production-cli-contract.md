# Production CLI Contract

Main entrypoint: `cli.SimilarityCliMainKt`

## Commands

1. `scan-index`
- Required:
  - `--repos <repos-root>`
  - `--out <snapshot-file>`
- Optional:
  - `--mode simple|ast|hybrid` (default `hybrid`)
  - `--dom-ast-enabled true|false`
  - `--css-ast-enabled true|false`
  - `--behavior-ast-enabled true|false`
  - `--frameworks react,angular,vue`
  - `--pivot-count <n>` (default `16`)
  - `--pivot-seed <n>` (default `42`)
  - `--sample-percent <1..100>`
  - `--sample-seed <n>` (default `42`)
  - `--sample-mode global|stratified-framework` (default `global`)
  - `--json-out <file>`

2. `query`
- Required:
  - `--index-file <snapshot-file>`
  - exactly one of:
    - `--component-id <id>`
    - `--query-file <file>`
- Optional:
  - `--query-framework react|angular|vue` (used with `--query-file`)
  - `--top-k <n>` (default `10`)
  - `--top-n <n>` (default `20`)
  - `--json-out <file>`

3. `inspect`
- Required:
  - `--index-file <snapshot-file>`
- Optional:
  - `--json-out <file>`

4. `validate`
- Required:
  - `--index-file <snapshot-file>`
- Optional:
  - `--json-out <file>`

Output notes:
1. Human output includes integrity result and compatibility summary.
2. JSON output includes:
   - `valid`
   - `detected_version`
   - `load_supported`
   - `migration_required`
   - `compatibility_message`
   - `errors[]`

## Distribution

1. Installable distribution:
- `./gradlew installDist`

2. Zip artifact:
- `./gradlew distZip`

3. Executable jar:
- `./gradlew jar`

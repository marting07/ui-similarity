#!/usr/bin/env bash
set -euo pipefail

# -----------------------------
# Big-corpus repo discovery + cloning (balanced React/Angular/Vue)
#
# Goals:
# - Produce lots of UI components (apps + dashboards + storybook + design systems)
# - Keep repo set balanced across frameworks
# - Avoid disk bloat: shallow clones by default
# - Resume safely and log failures
#
# Examples:
# - TARGET_PER_FRAMEWORK=150 JOBS=6 SHALLOW=1 bash clone-repos.sh
# - TARGET_PER_FRAMEWORK=500 JOBS=6 SHALLOW=1 bash clone-repos.sh
#
# If network is stable, you can try JOBS=8. If you see failures/timeouts, drop to JOBS=4.
# -----------------------------

# 1) authenticate once (gives higher rate limits)
if ! gh auth status -h github.com >/dev/null 2>&1; then
  gh auth login
fi

# 2) set destination root + lists/logs
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR_DEFAULT="$SCRIPT_DIR/../data"
ROOT="${ROOT:-$DATA_DIR_DEFAULT/repos}"
LIST_ROOT="${LIST_ROOT:-$DATA_DIR_DEFAULT/repo-lists}"
LOG_ROOT="${LOG_ROOT:-$DATA_DIR_DEFAULT/logs}"
mkdir -p "$ROOT" "$LIST_ROOT" "$LOG_ROOT"

REACT_LIST="$LIST_ROOT/react.txt"
ANGULAR_LIST="$LIST_ROOT/angular.txt"
VUE_LIST="$LIST_ROOT/vue.txt"
FAIL_LOG="$LOG_ROOT/clone-failures.txt"

# Always create the failure log (even if nothing fails)
: > "$FAIL_LOG"

TARGET_PER_FRAMEWORK="${TARGET_PER_FRAMEWORK:-500}"
JOBS="${JOBS:-6}"
SHALLOW="${SHALLOW:-1}"
RETRIES="${RETRIES:-2}"
SKIP_DISCOVERY="${SKIP_DISCOVERY:-0}"

# Make sure worker shells see these
export ROOT SHALLOW RETRIES FAIL_LOG

# 4) discovery filters (avoid framework cores and a few known "not what we want")
EXCLUDE_REPOS_REGEX='^(facebook/react|vuejs/core|angular/angular|vercel/next\.js)$'
EXCLUDE_OWNER_REGEX='^(facebook|vuejs|angular)$'

is_excluded() {
  local full="$1"
  local owner="${full%%/*}"

  if printf '%s\n' "$full" | grep -Eiq "$EXCLUDE_REPOS_REGEX"; then
    return 0
  fi
  if printf '%s\n' "$owner" | grep -Eiq "$EXCLUDE_OWNER_REGEX"; then
    return 0
  fi
  return 1
}

# --- helpers ---------------------------------------------------------------

count_cloned_for_framework() {
  local fw="$1" # react|angular|vue
  # We store clones at $ROOT/<framework>/<owner>/<repo> by default below.
  # Count repos by finding .git dirs.
  if [ ! -d "$ROOT/$fw" ]; then
    echo 0
    return
  fi
  find "$ROOT/$fw" -type d -name .git 2>/dev/null | wc -l | tr -d ' '
}

dedupe_list() {
  local file="$1"
  [ -f "$file" ] || return 0
  # stable-ish dedupe
  awk 'NF' "$file" | sort -u > "${file}.tmp"
  mv "${file}.tmp" "$file"
}

discover_into_list() {
  local fw="$1"     # react|angular|vue
  local query="$2"  # gh search query string
  local out="$3"    # list file

  gh search repos "$query" --limit 200 --json fullName -q '.[].fullName' 2>/dev/null \
    | while read -r full; do
        [ -n "$full" ] || continue
        if is_excluded "$full"; then
          continue
        fi
        printf '%s\n' "$full" >> "$out"
      done
}

clone_one() {
  local fw="$1"      # react|angular|vue
  local full="$2"    # owner/repo
  local owner="${full%%/*}"
  local name="${full##*/}"
  local dest="$ROOT/$fw/$owner/$name"

  mkdir -p "$(dirname "$dest")"

  if [ -d "$dest/.git" ]; then
    return 0
  fi

  local attempt=0
  while true; do
    attempt=$((attempt + 1))
    if [ "$SHALLOW" = "1" ]; then
      # Prefer git directly for reliable shallow clone behavior.
      if git clone --depth 1 "https://github.com/$full.git" "$dest" >/dev/null 2>&1; then
        return 0
      fi
    else
      if git clone "https://github.com/$full.git" "$dest" >/dev/null 2>&1; then
        return 0
      fi
    fi

    if [ "$attempt" -gt "$RETRIES" ]; then
      printf '%s\t%s\n' "$fw" "$full" >> "$FAIL_LOG"
      return 1
    fi
    # small backoff
    sleep 2
  done
}

clone_from_list_until_quota() {
  local fw="$1"       # react|angular|vue
  local list="$2"     # list file

  dedupe_list "$list"

  local already
  already="$(count_cloned_for_framework "$fw")"
  if [ "$already" -ge "$TARGET_PER_FRAMEWORK" ]; then
    echo "[$fw] quota reached ($already / $TARGET_PER_FRAMEWORK). Skipping clone."
    return 0
  fi

  echo "[$fw] cloning until quota: currently $already / $TARGET_PER_FRAMEWORK"
  mkdir -p "$ROOT/$fw"

  # Print a small sample so you can see it's about to do work
  echo "[$fw] sample candidates:"
  head -n 3 "$list" | sed 's/^/  - /'

  remaining=$((TARGET_PER_FRAMEWORK - already))
  if [ "$remaining" -le 0 ]; then
    echo "[$fw] quota reached ($already / $TARGET_PER_FRAMEWORK). Skipping clone."
    return 0
  fi

  head -n "$remaining" "$list" \
    | awk 'NF' \
    | xargs -n 1 -P "$JOBS" -I {} bash -c '
        set -euo pipefail
        fw="$1"; full="$2"

        owner="${full%%/*}"
        name="${full##*/}"
        dest="$ROOT/$fw/$owner/$name"
        mkdir -p "$(dirname "$dest")"
        [ -d "$dest/.git" ] && exit 0

        echo "cloning [$fw] $full" 1>&2

        attempt=0
        while true; do
          attempt=$((attempt + 1))
          if [ "$SHALLOW" = "1" ]; then
            git clone --depth 1 "https://github.com/$full.git" "$dest" && exit 0
          else
            git clone "https://github.com/$full.git" "$dest" && exit 0
          fi

          if [ "$attempt" -gt "$RETRIES" ]; then
            printf "%s\t%s\n" "$fw" "$full" >> "$FAIL_LOG"
            exit 1
          fi
          sleep 2
        done
      ' _ "$fw" "{}"
}

# --- discovery plan --------------------------------------------------------
# Use multiple queries per framework and stars slicing for diversity.
# Tune stars ranges to adjust size; the 50..500 band is often very component-rich.

STARS_RANGES=(
  "stars:50..200"
  "stars:200..500"
  "stars:500..1000"
  "stars:>1000"
)

REACT_QUERIES=(
  'topic:react language:TypeScript'
  'topic:storybook language:TypeScript'
  'react in:readme "design system" language:TypeScript'
  'react in:readme storybook language:TypeScript'
  'react in:readme dashboard language:TypeScript'
)

ANGULAR_QUERIES=(
  'topic:angular language:TypeScript'
  'angular in:readme components language:TypeScript'
  'angular in:readme storybook language:TypeScript'
  'angular in:readme "design system" language:TypeScript'
  'angular in:readme dashboard language:TypeScript'
)

VUE_QUERIES=(
  'topic:vue language:TypeScript'
  'topic:vuejs language:TypeScript'
  'vue in:readme components language:TypeScript'
  'vue in:readme storybook language:TypeScript'
  'vue in:readme "design system" language:TypeScript'
)

# 5) discovery (append-only; dedupe later)
if [ "$SKIP_DISCOVERY" = "1" ]; then
  echo "[discover] skipped (SKIP_DISCOVERY=1). Using existing lists in $LIST_ROOT"
  # Ensure list files exist but do not truncate them.
  touch "$REACT_LIST" "$ANGULAR_LIST" "$VUE_LIST"
  echo "[discover] react candidates:   $(wc -l < "$REACT_LIST" | tr -d ' ')"
  echo "[discover] angular candidates: $(wc -l < "$ANGULAR_LIST" | tr -d ' ')"
  echo "[discover] vue candidates:     $(wc -l < "$VUE_LIST" | tr -d ' ')"
else
  echo "[discover] building candidate lists in $LIST_ROOT"
  for r in "${STARS_RANGES[@]}"; do
    for q in "${REACT_QUERIES[@]}"; do
      discover_into_list "react" "$q $r" "$REACT_LIST"
    done
    for q in "${ANGULAR_QUERIES[@]}"; do
      discover_into_list "angular" "$q $r" "$ANGULAR_LIST"
    done
    for q in "${VUE_QUERIES[@]}"; do
      discover_into_list "vue" "$q $r" "$VUE_LIST"
    done
  done

  dedupe_list "$REACT_LIST"
  dedupe_list "$ANGULAR_LIST"
  dedupe_list "$VUE_LIST"

  echo "[discover] react candidates:   $(wc -l < "$REACT_LIST" | tr -d ' ')"
  echo "[discover] angular candidates: $(wc -l < "$ANGULAR_LIST" | tr -d ' ')"
  echo "[discover] vue candidates:     $(wc -l < "$VUE_LIST" | tr -d ' ')"
fi

# 6) cloning (balanced quotas, parallel, resume-safe)
: > "$FAIL_LOG" || true

clone_from_list_until_quota "react" "$REACT_LIST"
clone_from_list_until_quota "angular" "$ANGULAR_LIST"
clone_from_list_until_quota "vue" "$VUE_LIST"

echo "[done] cloned counts:"
echo "  react:   $(count_cloned_for_framework react)"
echo "  angular: $(count_cloned_for_framework angular)"
echo "  vue:     $(count_cloned_for_framework vue)"
echo "Failures (if any) logged to: $FAIL_LOG"

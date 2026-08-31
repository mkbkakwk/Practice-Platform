#!/usr/bin/env bash

# Fixture-only tests for the read-only Stage 9D operator check.  They never
# inspect or mutate a long-lived Docker environment.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
source "$script_dir/backup-lib.sh"

tmp="$(mktemp -d "${TMPDIR:-/tmp}/practice-platform-ops-check.XXXXXX")"
cleanup() { rm -rf -- "$tmp"; }
trap cleanup EXIT INT TERM

sha="f35df465eea405b569691cdfd681dbf05fdae61d"
backup_root="$tmp/backups"
backup_dir="$backup_root/consistent/$(date -u +%Y-%m-%dT%H%M%SZ)_$sha"
mkdir -p "$backup_dir/office"
printf 'fixture database dump\n' > "$backup_dir/database.dump"
printf 'fixture document\n' > "$backup_dir/office/example.docx"
tar -C "$backup_dir/office" -czf "$backup_dir/office.tar.gz" .
rm -rf -- "$backup_dir/office"
created="$(date -u +%Y-%m-%dT%H%M%SZ)"
cat > "$backup_dir/manifest.json" <<EOF
{"formatVersion":1,"backupId":"fixture","createdAt":"$created","mode":"consistent","gitSha":"$sha","flywayVersion":"9","database":{"format":"pg_custom","file":"database.dump"},"office":{"format":"tar.gz","file":"office.tar.gz"},"rabbitmq":{"authoritative":false}}
EOF
(cd "$backup_dir" && sha256sum database.dump office.tar.gz manifest.json > SHA256SUMS)
touch "$backup_dir/.complete"

fixture="$tmp/status.json"
write_fixture() {
  local git_sha="$1" backend="$2" dlq="$3" outbox="$4"
  cat > "$fixture" <<EOF
{"version":{"gitSha":"$git_sha","flywayVersion":"9"},"components":{"backend":{"status":"$backend"},"postgresql":{"status":"UP"},"rabbitmq":{"status":"UP"},"worker":{"status":"UP"},"runner":{"status":"UP","sandboxAvailable":true}},"queues":{"main":0,"retry":0,"dlq":$dlq},"outbox":{"nonterminal":$outbox}}
EOF
}

run_check() {
  OPS_CHECK_TEST_MODE=1 OPS_CHECK_STATUS_FILE="$fixture" OPS_CHECK_AVAILABLE_BYTES=2147483648 \
    "$BASH" "$script_dir/ops-check.sh" --environment staging --expected-sha "$sha" --backup-root "$backup_root" \
    --max-backup-age-seconds 93600 --minimum-free-bytes 1
}

write_fixture "$sha" UP 0 0
if ! healthy_output="$(run_check 2>&1)"; then
  printf '%s\n' "$healthy_output" >&2
  echo "FAIL: healthy ops fixture was rejected" >&2
  exit 1
fi

write_fixture "0123456789abcdef0123456789abcdef01234567" UP 0 0
if run_check >/dev/null 2>&1; then echo "FAIL: SHA mismatch was accepted" >&2; exit 1; fi

write_fixture "$sha" DOWN 0 0
if run_check >/dev/null 2>&1; then echo "FAIL: unhealthy component was accepted" >&2; exit 1; fi

write_fixture "$sha" UP 1 0
if run_check >/dev/null 2>&1; then echo "FAIL: DLQ backlog was accepted" >&2; exit 1; fi

write_fixture "$sha" UP 0 1
if run_check >/dev/null 2>&1; then echo "FAIL: Outbox backlog was accepted" >&2; exit 1; fi

write_fixture "$sha" UP 0 0
OPS_CHECK_TEST_MODE=1 OPS_CHECK_STATUS_FILE="$fixture" OPS_CHECK_AVAILABLE_BYTES=0 \
  "$BASH" "$script_dir/ops-check.sh" --environment staging --expected-sha "$sha" --backup-root "$backup_root" \
  --max-backup-age-seconds 93600 --minimum-free-bytes 1 >/dev/null 2>&1 \
  && { echo "FAIL: low disk fixture was accepted" >&2; exit 1; }

old_created="2000-01-01T000000Z"
sed -i "s/$created/$old_created/" "$backup_dir/manifest.json"
(cd "$backup_dir" && sha256sum database.dump office.tar.gz manifest.json > SHA256SUMS)
if run_check >/dev/null 2>&1; then echo "FAIL: stale backup fixture was accepted" >&2; exit 1; fi

"$BASH" "$script_dir/staging-deploy-sha.sh" --sha "$sha" --dry-run >/dev/null \
  || { echo "FAIL: exact-SHA staging dry-run failed" >&2; exit 1; }
if "$BASH" "$script_dir/staging-deploy-sha.sh" --sha HEAD --dry-run >/dev/null 2>&1; then
  echo "FAIL: staging deploy helper accepted a symbolic revision" >&2
  exit 1
fi
if "$BASH" "$script_dir/restore.sh" --backup "$backup_dir" --target isolated --confirm-isolated \
  --project practice-platform-staging --db-container ignored --db-name ignored --db-user ignored --office-volume ignored >/dev/null 2>&1; then
  echo "FAIL: restore guard accepted the live Staging project" >&2
  exit 1
fi

for file in docs/release-runbook.md docs/rollback-runbook.md docs/backup-restore-runbook.md docs/incident-runbook.md docs/pre-release-checklist.md; do
  [[ -f "$repo_root/$file" ]] || { echo "FAIL: runbook is missing: $file" >&2; exit 1; }
done
grep -Fq 'staging-deploy-sha.sh --sha "$DEPLOY_SHA"' "$repo_root/docs/release-runbook.md" \
  || { echo "FAIL: release runbook does not reference exact-SHA deployment" >&2; exit 1; }
grep -Fqi 'application rollback' "$repo_root/docs/rollback-runbook.md" \
  || { echo "FAIL: rollback runbook omits application-versus-database policy" >&2; exit 1; }
grep -Fq 'practice-platform-stage9d-drill-*' "$repo_root/docs/backup-restore-runbook.md" \
  || { echo "FAIL: backup runbook omits the Stage 9D isolated target" >&2; exit 1; }
grep -Fq 'docker system prune' "$repo_root/docs/incident-runbook.md" \
  || { echo "FAIL: incident runbook omits destructive-remediation prohibition" >&2; exit 1; }
grep -Fq 'ROLLBACK_SHA' "$repo_root/docs/pre-release-checklist.md" \
  || { echo "FAIL: release checklist omits rollback SHA" >&2; exit 1; }

echo "Stage 9D ops-check tests passed"

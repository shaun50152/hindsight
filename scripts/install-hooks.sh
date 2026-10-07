#!/usr/bin/env bash
# Installs a commit-msg hook that removes "Co-authored-by: ...Cursor..." trailers.
# Re-run this after cloning the repo on a new machine.
set -euo pipefail
cat > .git/hooks/commit-msg <<'HOOK'
#!/usr/bin/env bash
sed -i.bak -E '/^[Cc]o-[Aa]uthored-[Bb]y:.*([Cc]ursor|cursoragent)/d' "$1" && rm -f "$1.bak"
HOOK
chmod +x .git/hooks/commit-msg
echo "commit-msg hook installed."

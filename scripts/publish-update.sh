#!/usr/bin/env bash
# signs the current build and publishes it to Teslanator20/teslamaps
# (teslamaps-releases too, clients <=2.2.4 still read only that repo)
set -euo pipefail
cd "$(dirname "$0")/.."

KEY=/c/Users/tim/.claude/keys/teslamaps-update-private.pem
REPOS="Teslanator20/teslamaps Teslanator20/teslamaps-releases"

JAR=$(ls build/libs/teslamaps-*.jar | grep -v -E '(-all|-sources)\.jar$')
VERSION=$(basename "$JAR" | sed -E 's/teslamaps-(.+)\.jar/\1/')
# clients <=2.1.44 write the jar under the manifest name, so pinning it keeps the
# launcher's mod entry pointing at a file that still exists
NAME=${ASSET_NAME:-$(basename "$JAR")}
SHA=$(sha256sum "$JAR" | cut -d' ' -f1)
# 26.3 line: own tag suffix, never "latest" (26.1.2 clients follow /releases/latest)
MC=$(grep '^minecraft_version=' gradle.properties | cut -d= -f2 | tr -d '\r')
TAG="v$VERSION-$MC"

TMP=$(mktemp -d)
cp "$JAR" "$TMP/$NAME"
# optional: CHANGELOG_FILE=path, one line per entry, shown in the in-game update popup
CHANGELOG="[]"
if [ -n "${CHANGELOG_FILE:-}" ] && [ -f "$CHANGELOG_FILE" ]; then
    CHANGELOG=$(python -c 'import json,sys; print(json.dumps([l.strip() for l in open(sys.argv[1],encoding="utf-8") if l.strip()]))' "$CHANGELOG_FILE")
fi
printf '{"version":"%s","mc":"%s","jar":"%s","sha256":"%s","published":%s,"changelog":%s}' \
    "$VERSION" "$MC" "$NAME" "$SHA" "$(date +%s)" "$CHANGELOG" > "$TMP/manifest.json"
openssl pkeyutl -sign -inkey "$KEY" -rawin -in "$TMP/manifest.json" | base64 -w0 > "$TMP/manifest.sig"

HEAD_SHA=$(git rev-parse HEAD)
git merge-base --is-ancestor "$HEAD_SHA" "origin/$(git branch --show-current)" 2>/dev/null \
    || { echo "push the branch first (tag needs the commit on github)"; exit 1; }
for REPO in $REPOS; do
    TARGET=()
    [ "$REPO" = Teslanator20/teslamaps ] && TARGET=(--target "$HEAD_SHA")
    gh release delete "$TAG" --repo "$REPO" --yes --cleanup-tag 2>/dev/null || true
    gh release create "$TAG" --repo "$REPO" "${TARGET[@]}" --title "$TAG" --notes "" --latest=false \
        "$TMP/$NAME" "$TMP/manifest.json" "$TMP/manifest.sig"
done
rm -rf "$TMP"
echo "published $TAG sha256=$SHA"

#!/usr/bin/env bash
# signs the current build and publishes it to Teslanator20/teslamaps-releases
set -euo pipefail
cd "$(dirname "$0")/.."

KEY=/c/Users/tim/.claude/keys/teslamaps-update-private.pem
REPO=Teslanator20/teslamaps-releases

JAR=$(ls build/libs/teslamaps-*.jar | grep -v -E '(-all|-sources)\.jar$')
NAME=$(basename "$JAR")
VERSION=$(echo "$NAME" | sed -E 's/teslamaps-(.+)\.jar/\1/')
SHA=$(sha256sum "$JAR" | cut -d' ' -f1)

TMP=$(mktemp -d)
printf '{"version":"%s","jar":"%s","sha256":"%s","published":%s}' \
    "$VERSION" "$NAME" "$SHA" "$(date +%s)" > "$TMP/manifest.json"
openssl pkeyutl -sign -inkey "$KEY" -rawin -in "$TMP/manifest.json" | base64 -w0 > "$TMP/manifest.sig"

gh release delete "v$VERSION" --repo "$REPO" --yes 2>/dev/null || true
gh release create "v$VERSION" --repo "$REPO" --title "v$VERSION" --notes "" \
    "$JAR" "$TMP/manifest.json" "$TMP/manifest.sig"
rm -rf "$TMP"
echo "published v$VERSION sha256=$SHA"

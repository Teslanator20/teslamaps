#!/usr/bin/env bash
# signs the current build and publishes it to Teslanator20/teslamaps-releases
set -euo pipefail
cd "$(dirname "$0")/.."

KEY=/c/Users/tim/.claude/keys/teslamaps-update-private.pem
REPO=Teslanator20/teslamaps-releases

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
    CHANGELOG="[$(sed -e 's/\r$//' -e 's/\t/ /g' -e '/^[[:space:]]*$/d' -e 's/\/\\/g' -e 's/"/\\"/g' -e 's/.*/"&"/' "$CHANGELOG_FILE" | paste -sd, -)]"
fi
printf '{"version":"%s","mc":"%s","jar":"%s","sha256":"%s","published":%s,"changelog":%s}' \
    "$VERSION" "$MC" "$NAME" "$SHA" "$(date +%s)" "$CHANGELOG" > "$TMP/manifest.json"
openssl pkeyutl -sign -inkey "$KEY" -rawin -in "$TMP/manifest.json" | base64 -w0 > "$TMP/manifest.sig"

gh release delete "$TAG" --repo "$REPO" --yes 2>/dev/null || true
gh release create "$TAG" --repo "$REPO" --title "$TAG" --notes "" --latest=false \
    "$TMP/$NAME" "$TMP/manifest.json" "$TMP/manifest.sig"
rm -rf "$TMP"
echo "published $TAG sha256=$SHA"

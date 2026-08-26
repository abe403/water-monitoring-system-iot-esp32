#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BIN_DIR="${KIND_BIN_DIR:-$ROOT/.tools/bin}"
KIND_VERSION="${KIND_VERSION:-v0.32.0}"
KUBECTL_VERSION="${KUBECTL_VERSION:-v1.36.4}"
mkdir -p "$BIN_DIR"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    # A previous WSL invocation may have left Linux binaries in the shared
    # repo-local cache.  Git Bash resolves extensionless entries before .exe;
    # remove those stale files so the Windows clients are actually selected.
    rm -f "$BIN_DIR/kind" "$BIN_DIR/kubectl"
    kind_asset="kind-windows-amd64"
    kubectl_os=windows
    kubectl_arch=amd64
    executable_suffix=.exe
    ;;
  Darwin*)
    kubectl_os=darwin
    executable_suffix=
    if [[ "$(uname -m)" == arm64 ]]; then kind_asset=kind-darwin-arm64; kubectl_arch=arm64; else kind_asset=kind-darwin-amd64; kubectl_arch=amd64; fi
    ;;
  Linux*)
    kubectl_os=linux
    executable_suffix=
    if [[ "$(uname -m)" == aarch64 || "$(uname -m)" == arm64 ]]; then kind_asset=kind-linux-arm64; kubectl_arch=arm64; else kind_asset=kind-linux-amd64; kubectl_arch=amd64; fi
    ;;
  *) echo "unsupported host: $(uname -s)" >&2; exit 1 ;;
esac

kind_bin="$BIN_DIR/kind$executable_suffix"
kubectl_bin="$BIN_DIR/kubectl$executable_suffix"

kind_is_current() {
  [[ -x "$kind_bin" ]] && "$kind_bin" version 2>/dev/null | grep -q "kind $KIND_VERSION"
}

kubectl_is_current() {
  [[ -x "$kubectl_bin" ]] && "$kubectl_bin" version --client=true --output=yaml 2>/dev/null | grep -q "gitVersion: $KUBECTL_VERSION"
}

if ! kind_is_current; then
  echo "installing kind $KIND_VERSION into $kind_bin" >&2
  curl --fail --location --retry 3 --output "$kind_bin" \
    "https://kind.sigs.k8s.io/dl/$KIND_VERSION/$kind_asset"
  chmod +x "$kind_bin"
fi

if ! kubectl_is_current; then
  echo "installing kubectl $KUBECTL_VERSION into $kubectl_bin" >&2
  curl --fail --location --retry 3 --output "$kubectl_bin" \
    "https://dl.k8s.io/release/$KUBECTL_VERSION/bin/$kubectl_os/$kubectl_arch/kubectl$executable_suffix"
  chmod +x "$kubectl_bin"
fi

"$kind_bin" version
"$kubectl_bin" version --client=true --output=yaml

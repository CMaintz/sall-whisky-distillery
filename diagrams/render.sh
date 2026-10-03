#!/usr/bin/env sh
# Renders every diagrams/*.mmd to a PNG next to it.
set -e
cd "$(dirname "$0")"
for src in *.mmd; do
  npx -y @mermaid-js/mermaid-cli@11 -i "$src" -o "${src%.mmd}.png" \
    -c mermaid.config.json -b white -s 2 -w 2400
done

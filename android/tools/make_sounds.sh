#!/bin/sh
# Copies the iOS sound set into res/raw. The iOS files are IMA4 CAF, which Android can't play: this decodes them
# to 16-bit WAV (no further loss). Resource names can't have dashes, so sfx-chips-big becomes sfx_chips_big.
set -e
cd "$(dirname "$0")/.."
out=app/src/main/res/raw
mkdir -p $out
for f in ../ios/CardGame3/Resources/Assets.xcassets/Sounds/*.dataset/*.caf; do
  name=$(basename "$f" .caf | tr '-' '_')
  afconvert -f WAVE -d LEI16 "$f" "$out/$name.wav"
done

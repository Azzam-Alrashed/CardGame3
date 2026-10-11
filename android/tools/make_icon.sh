#!/bin/sh
# Builds the adaptive launcher icon's foreground from assets/logo.svg (macOS only: Quick Look renders the SVG).
# The logo fills 76 of the icon's 108 dp, so the launcher's mask (72 dp) trims its corners like iOS does.
# The background layer is the logo's lime (@color/ic_launcher_background).
set -e
cd "$(dirname "$0")/.."
work=$(mktemp -d)
qlmanage -t -s 1024 -o "$work" ../assets/logo.svg >/dev/null
for pair in mdpi:108 hdpi:162 xhdpi:216 xxhdpi:324 xxxhdpi:432; do
  density=${pair%%:*}; size=${pair##*:}
  inner=$((size * 76 / 108))
  mkdir -p app/src/main/res/mipmap-$density
  sips -z $inner $inner "$work/logo.svg.png" --out "$work/inner.png" >/dev/null
  sips --padToHeightWidth $size $size --padColor 7DE31A "$work/inner.png" --out app/src/main/res/mipmap-$density/ic_launcher_foreground.png >/dev/null 2>&1
done
# Play Store listing icon.
sips -z 512 512 "$work/logo.svg.png" --out tools/play-icon-512.png >/dev/null
rm -rf "$work"

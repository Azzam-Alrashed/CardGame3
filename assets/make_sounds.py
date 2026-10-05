"""Builds the app's sound set (ios/CardGame3/Resources/Assets.xcassets/Sounds).

Sources, all CC0 (no attribution required): Kenney's Casino Audio, Interface Sounds and Music Jingles
(https://kenney.nl/assets). The darbuka roll and doum are synthesized here.

    1. Unzip the three Kenney packs into one folder, and decode them:  oggdec -o wav/<name>.wav <name>.ogg
       (iOS can't play OGG; oggdec is in `brew install vorbis-tools`)
    2. python3 make_sounds.py <that folder>       -> <that folder>/out/sfx-*.wav
    3. afconvert -f caff -d ima4 out/sfx-x.wav sfx-x.caf, into Sounds/sfx-x.dataset/
"""
import array
import math
import os
import random
import sys
import wave

SR = 44100
HERE = sys.argv[1] if len(sys.argv) > 1 else "."
WAV = os.path.join(HERE, "wav")
OUT = os.path.join(HERE, "out")
os.makedirs(OUT, exist_ok=True)
rng = random.Random(7)


def write(name, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = 0.89 / peak
    data = array.array("h", (int(max(-1, min(1, s * scale)) * 32767) for s in samples))
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def read_mono(name):
    with wave.open(os.path.join(WAV, name + ".wav")) as w:
        assert w.getframerate() == SR, name
        ch = w.getnchannels()
        a = array.array("h", w.readframes(w.getnframes()))
    if ch == 2:
        return [(a[i] + a[i + 1]) / 65536 for i in range(0, len(a), 2)]
    return [x / 32768 for x in a]


def trim(samples, seconds, fade=0.08):
    n = min(len(samples), int(seconds * SR))
    f = int(fade * SR)
    out = samples[:n]
    for i in range(max(0, n - f), n):
        out[i] *= (n - i) / f
    return out


# --- Darbuka synthesis -------------------------------------------------------
# A drumhead rings at a few inharmonic modes; strokes differ in which modes they excite.

def stroke(modes, decay, noise_amp, noise_ms, length, pitch_drop=0.0):
    n = int(length * SR)
    out = [0.0] * n
    for freq, amp, dec in modes:
        phase = rng.random() * 2 * math.pi
        for i in range(n):
            t = i / SR
            f = freq * (1 - pitch_drop * (1 - math.exp(-t * 18)))
            out[i] += amp * math.exp(-t / (dec * decay)) * math.sin(phase + 2 * math.pi * f * t)
    nn = int(noise_ms / 1000 * SR)
    for i in range(min(nn, n)):
        out[i] += noise_amp * (rng.random() * 2 - 1) * (1 - i / nn) ** 2
    return out


def tek(amp=1.0):
    # Rim stroke: bright, short.
    modes = [(690, 0.7, 0.05), (1180, 0.5, 0.035), (1960, 0.35, 0.025), (2870, 0.2, 0.018)]
    return [s * amp for s in stroke(modes, 1.0, 0.9, 3, 0.16)]


def ka(amp=1.0):
    # Other-hand rim stroke: a touch lower and softer.
    modes = [(640, 0.6, 0.045), (1090, 0.45, 0.03), (1820, 0.3, 0.022), (2700, 0.15, 0.015)]
    return [s * amp * 0.8 for s in stroke(modes, 1.0, 0.7, 2.5, 0.15)]


def doum(amp=1.0):
    # Center stroke: deep, with a little pitch drop.
    modes = [(118, 1.0, 0.28), (236, 0.25, 0.12), (355, 0.12, 0.08), (520, 0.06, 0.05)]
    return [s * amp for s in stroke(modes, 1.0, 0.25, 6, 0.75, pitch_drop=0.12)]


def mix(into, sound, at):
    start = int(at * SR)
    for i, s in enumerate(sound):
        if start + i < len(into):
            into[start + i] += s


def darbuka_roll(seconds=1.15):
    # Tek-ka strokes speeding up from ~7 to ~22 per second and getting louder; a doum to open.
    out = [0.0] * int((seconds + 0.2) * SR)
    mix(out, doum(0.7), 0.0)
    t, i = 0.12, 0
    while t < seconds:
        p = t / seconds
        rate = 7 + 15 * p ** 1.4
        amp = 0.35 + 0.65 * p ** 1.2
        jitter = rng.uniform(-0.004, 0.004)
        mix(out, (tek if i % 2 == 0 else ka)(amp), t + jitter)
        t += 1 / rate
        i += 1
    return trim(out, seconds + 0.2, fade=0.15)


write("sfx-roll", darbuka_roll())
write("sfx-doum", trim(doum(1.0), 0.75, fade=0.2))

# --- Kenney sounds -----------------------------------------------------------
# asset name -> (source, max seconds or None)
PICKS = {
    "sfx-shuffle": ("card-shuffle", 0.9),
    "sfx-deal-1": ("card-slide-1", None),
    "sfx-deal-2": ("card-slide-2", None),
    "sfx-deal-3": ("card-slide-4", None),
    "sfx-deal-4": ("card-slide-5", None),
    "sfx-land-1": ("card-place-1", None),
    "sfx-land-2": ("card-place-3", None),
    "sfx-flip-1": ("card-place-2", None),
    "sfx-flip-2": ("card-place-4", None),
    "sfx-fold-1": ("card-shove-1", None),
    "sfx-fold-2": ("card-shove-3", None),
    "sfx-chip-1": ("chip-lay-1", None),
    "sfx-chip-2": ("chip-lay-2", None),
    "sfx-chip-3": ("chip-lay-3", None),
    "sfx-chips-1": ("chips-stack-1", None),
    "sfx-chips-2": ("chips-stack-3", None),
    "sfx-chips-3": ("chips-stack-4", None),
    "sfx-chips-big": ("chips-collide-3", None),
    "sfx-all-in": ("chips-handle-5", None),
    "sfx-offer": ("pluck_002", None),
    "sfx-accept": ("confirmation_001", None),
    "sfx-reject": ("bong_001", None),
    "sfx-turn": ("glass_001", None),
    "sfx-tick": ("tick_002", None),
    "sfx-match": ("glass_003", None),
    "sfx-join": ("drop_002", None),
    "sfx-toggle": ("toggle_001", None),
    "sfx-boss": ("jingles_PIZZI16", None),
    "sfx-win": ("jingles_PIZZI02", None),
    "sfx-lose": ("jingles_PIZZI05", None),
    "sfx-verdict": ("jingles_PIZZI04", None),
    "sfx-quads": ("jingles_STEEL10", None),
    "sfx-fanfare": ("jingles_STEEL02", None),
}

for asset, (source, seconds) in PICKS.items():
    s = read_mono(source)
    write(asset, trim(s, seconds) if seconds else s)

print(len(os.listdir(OUT)), "sounds written to", OUT)

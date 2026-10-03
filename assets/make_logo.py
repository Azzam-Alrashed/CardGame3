# Generates logo.svg (app icon) and logo-wordmark.svg: four fanned aces.
INK = "#121214"
RED = "#FF618F"   # Theme.hotPink
FONT = "'Arial Rounded MT Bold', ArialRoundedMTBold, Nunito, 'Varela Round', sans-serif"

# Suit shapes in a unit box centred on (0, 0).
SUITS = {
    "spade": "M0 -0.5 C-0.42 -0.16 -0.52 0.06 -0.38 0.22 C-0.26 0.34 -0.1 0.3 -0.05 0.2 L-0.15 0.5 L0.15 0.5 L0.05 0.2 C0.1 0.3 0.26 0.34 0.38 0.22 C0.52 0.06 0.42 -0.16 0 -0.5 Z",
    "heart": "M0 0.46 C-0.3 0.22 -0.5 0.02 -0.5 -0.2 C-0.5 -0.4 -0.34 -0.5 -0.22 -0.5 C-0.1 -0.5 -0.02 -0.42 0 -0.32 C0.02 -0.42 0.1 -0.5 0.22 -0.5 C0.34 -0.5 0.5 -0.4 0.5 -0.2 C0.5 0.02 0.3 0.22 0 0.46 Z",
    "diamond": "M0 -0.5 C0.12 -0.3 0.26 -0.14 0.38 0 C0.26 0.14 0.12 0.3 0 0.5 C-0.12 0.3 -0.26 0.14 -0.38 0 C-0.26 -0.14 -0.12 -0.3 0 -0.5 Z",
    "club": "M-0.22 -0.24 a0.22 0.22 0 1 0 0.44 0 a0.22 0.22 0 1 0 -0.44 0 Z M-0.46 0.1 a0.22 0.22 0 1 0 0.44 0 a0.22 0.22 0 1 0 -0.44 0 Z M0.02 0.1 a0.22 0.22 0 1 0 0.44 0 a0.22 0.22 0 1 0 -0.44 0 Z M-0.07 0 L0.07 0 L0.16 0.5 L-0.16 0.5 Z",
}

def suit(name, x, y, size, color):
    # One <path> per piece so overlapping pieces (the club's circles) never cut holes in each other.
    pieces = [p.strip() for p in SUITS[name].split("Z") if p.strip()]
    paths = "".join(f'<path d="{p} Z"/>' for p in pieces)
    return f'<g transform="translate({x} {y}) scale({size})" fill="{color}">{paths}</g>'

def card(angle, name, color, big=False):
    # Card is 150x210, its bottom centre sits 130 above the fan's pivot.
    out = [f'<g transform="rotate({angle})">',
           f'<rect x="-75" y="-340" width="150" height="210" rx="20" fill="#FFFFFF" stroke="{INK}" stroke-width="7"/>',
           f'<text x="-56" y="-284" font-family="{FONT}" font-weight="700" font-size="50" fill="{color}">A</text>',
           suit(name, -38, -250, 30, color)]
    if big:
        out.append(suit(name, 8, -212, 80, color))
    out.append('</g>')
    return "".join(out)

DEFS = '''
    <filter id="soft" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="8" stdDeviation="10" flood-color="#000000" flood-opacity="0.14"/>
    </filter>'''

MARK = f'''
  <g transform="translate(256 466)" filter="url(#soft)">
    {card(-24, "club", INK)}
    {card(-8, "diamond", RED)}
    {card(8, "heart", RED)}
    {card(24, "spade", INK, big=True)}
  </g>'''

def svg(w, h, body, extra_defs=""):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">\n  <defs>{DEFS}{extra_defs}\n  </defs>\n  <rect width="{w}" height="{h}" fill="#FFFFFF"/>{body}\n</svg>\n'

BUBBLE = '''
    <path d="M0 40 Q0 0 50 0 L140 0 Q190 0 190 40 Q190 80 140 80 L64 80 L30 106 L40 78 Q0 74 0 40 Z" fill="#111111"/>
    <text x="95" y="56" text-anchor="middle" font-family="Geeza Pro, Noto Sans Arabic, sans-serif" font-weight="700" font-size="40" fill="#FFFFFF">مداقش!</text>'''

# App icon: the home-screen scene from the app — lime background, the four aces,
# character blobs around them and a "Deal?" bubble. Colors come from Theme.swift.
LIME = "#7DE31A"

ICON_DEFS = '''
    <linearGradient id="sheen" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.6"/>
      <stop offset="0.5" stop-color="#FFFFFF" stop-opacity="0"/>
      <stop offset="1" stop-color="#000000" stop-opacity="0.08"/>
    </linearGradient>
    <filter id="glow" x="-40%" y="-40%" width="180%" height="180%">
      <feGaussianBlur in="SourceGraphic" stdDeviation="9"/>
      <feColorMatrix type="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 0.45 0"/>
      <feOffset dy="6"/>
      <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>'''

def eye(x, y, r=13, look=(3, 2)):
    return (f'<circle cx="{x}" cy="{y}" r="{r}" fill="#FFFFFF"/>'
            f'<circle cx="{x + look[0]}" cy="{y + look[1]}" r="{r * 0.55}" fill="{INK}"/>')

def blob(cx, cy, r, color, face):
    return (f'<g filter="url(#glow)"><circle cx="{cx}" cy="{cy}" r="{r}" fill="{color}"/></g>'
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#sheen)"/>'
            f'<g transform="translate({cx} {cy})">{face}</g>')

LINE = f'fill="none" stroke="{INK}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"'

BLUE = blob(52, 150, 118, "#70B3F7",
    eye(-2, -12, 16) + eye(42, -12, 16) +
    f'<path d="M4 26 Q22 44 42 26" {LINE}/>' +
    f'<path d="M-40 -62 Q-30 -120 40 -126 Q70 -128 80 -118" {LINE}/>'
    f'<circle cx="88" cy="-112" r="9" {LINE}/>')
PINK = blob(468, 178, 100, "#F599C9",
    eye(-40, -8, 15) +
    f'<path d="M-6 -12 Q6 2 18 -14" {LINE}/>' +
    f'<path d="M-36 22 Q-16 42 6 20" {LINE}/>')
YELLOW = blob(92, 452, 86, "#FFC94D",
    eye(-14, -12, 14) + eye(22, -12, 14) +
    f'<circle cx="4" cy="22" r="8" {LINE}/>')
CORAL = blob(432, 452, 72, "#FF7A6E",
    eye(-16, -14, 11, (2, 3)) + eye(10, -14, 11, (2, 3)) +
    f'<path d="M-14 12 Q-2 24 12 10" {LINE}/>')

DEAL = f'''
  <g transform="translate(318 34) rotate(-6)">
    <path d="M0 34 Q0 0 40 0 L118 0 Q150 0 150 34 Q150 66 118 66 L104 66 L112 88 L84 66 L40 66 Q0 66 0 34 Z" fill="{INK}"/>
    <text x="75" y="46" text-anchor="middle" font-family="{FONT}" font-weight="800" font-size="32" fill="#FFFFFF">Deal?</text>
  </g>'''

ICON = f'''
  <rect width="512" height="512" fill="{LIME}"/>
  {BLUE}{YELLOW}{CORAL}{PINK}{DEAL}
  <g transform="translate(256 286) scale(1.0) translate(-256 -247)">{MARK.replace('stroke-width="7"', 'stroke-width="8"')}
  </g>'''

open("logo.svg", "w").write(svg(512, 512, ICON, ICON_DEFS))

WORDMARK = f'''
  <g transform="translate(20 40) scale(0.86)">{MARK}
  </g>
  <g transform="translate(350 50) rotate(-8)">{BUBBLE}
  </g>
  <text x="456" y="360" font-family="{FONT}" font-weight="800" font-size="128" letter-spacing="-4" fill="{INK}">CardGame3</text>'''

open("logo-wordmark.svg", "w").write(svg(1226, 520, WORDMARK))

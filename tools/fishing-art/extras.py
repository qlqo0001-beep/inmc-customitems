"""Fillets, baits and rods."""
import math
from PIL import Image
from kinds import *

GRADE_MEAT = {
    'f': '#f0c8b4', 'e': '#f28a5a', 'd': '#d9474c', 'c': '#e676a8',
    'b': '#a64a9a', 'a': '#f2b43c', 's': '#ff4a3a',
}


def star_badge(col, outline):
    """Plus-shaped star at the bottom-right corner."""
    def fn(px, owner):
        cx, cy = 13, 13
        ring = [(cx + dx, cy + dy) for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2)
                if abs(dx) + abs(dy) == 2 or (abs(dx) == 2 and dy == 0) or (abs(dy) == 2 and dx == 0)]
        for x, y in ring:
            put(px, x, y, outline)
        for x, y in ((cx, cy), (cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
            put(px, x, y, col)
        put(px, cx, cy, adjust(col, 1.15, 0.5))
    return fn


def fillet(grade, trophy):
    meat = GRADE_MEAT[grade]
    pal = ramp(meat, belly=adjust_hex(meat, 1.2, 0.6))
    skin = ramp('#9aa4b0')
    s = Sprite()
    pred, geo = body_profile(-8.4, 8.6, 4.4, 3.8, peak=0.45, nose=1.2, tailw=1.4)

    def sh(u, v, x, y):
        t, ht, hb, c = geo(u)
        n = v / ht if v >= 0 else v / hb
        if n > 0.62:
            return skin['base'] if n < 0.85 else skin['light']
        # fat lines (chevrons like a real fillet)
        if ((u * 0.95 + abs(v) * 0.75) % 2.6) < 0.55:
            return 'belly'
        if n < -0.5:
            return 'dark'
        return 'base'
    s.add(pred, pal, sh)
    post = []
    if grade in ('a', 's'):
        post.append(sparkle([(2, 2)], (255, 245, 200), None))
    if trophy == 'trophy':
        post.append(star_badge((215, 220, 232), (70, 74, 90)))
    elif trophy == 'rare':
        post.append(glow(adjust(hexrgb(meat), 1.2, 0.8), 80))
        post.append(star_badge((255, 210, 60), (120, 70, 10)))
        post.append(sparkle([(1, 12)], (255, 240, 170), None))
    s.post.extend(post)
    return s


# ------------------------------------------------------------------ baits

def hook_px(px, x0, y0, col=(200, 204, 214), dark=(80, 84, 96)):
    """Small J hook: shank downwards from (x0,y0)."""
    pts = [(x0, y0), (x0, y0 + 1), (x0, y0 + 2), (x0, y0 + 3), (x0 - 1, y0 + 4), (x0 - 2, y0 + 4), (x0 - 3, y0 + 3), (x0 - 3, y0 + 2)]
    for i, (x, y) in enumerate(pts):
        put(px, x, y, col if i % 3 else dark)
    put(px, x0 - 2, y0 + 2, dark)  # barb


def worm(body, belly, seg, post=()):
    s = eel(body, belly=belly, width=2.0, amp=2.6, waves=1.4, tailfin=False, eye_col=adjust(hexrgb(body), 0.4),
            stripes=lambda T, n, x, y: seg if int(T * 12) % 2 == 0 and abs(n) < 0.9 else None, post=post)
    return s


def dough():
    pal = ramp('#e6d29a', belly='#fff2c8')
    s = Sprite()
    s.add(ellipse(-2.5, -1.2, 5.0, 4.4), pal, lambda u, v, x, y: 'dark' if noise(x, y, 1) < 0.18 else ('belly' if v > 1.5 else 'base'))
    s.add(ellipse(3.6, 1.6, 4.0, 3.6), pal, lambda u, v, x, y: 'dark' if noise(x, y, 2) < 0.18 else ('belly' if v > 3.4 else 'base'))
    s.post.append(lambda px, owner: [put(px, x, y, (190, 150, 90), owner, only_filled=True) for x, y in ((6, 9), (9, 5), (4, 11))])
    return s


def shrimp(body, ice):
    pal = ramp(body, belly='#fff0ea')
    ip = ramp(ice)
    s = Sprite(rot=0)
    pts = []
    for i in range(20):
        t = i / 19
        a = math.pi * (0.15 + 1.25 * t)
        r = 5.6 - 1.8 * t
        pts.append((r * math.cos(a) + 0.5, r * math.sin(a) - 1.0))
    al = along(pts)
    rad = lambda T: 2.3 - 1.4 * T
    s.add(polyline(pts, rad), pal, lambda u, v, x, y: ('dark' if int(al(u, v)[0] * 7) % 2 == 0 else 'base'))
    # tail fan
    s.add(tri((pts[-1][0], pts[-1][1]), (pts[-1][0] - 2.0, pts[-1][1] - 2.4), (pts[-1][0] + 1.6, pts[-1][1] - 2.6)), pal, lambda u, v, x, y: 'dark')
    # antennae
    s.add(polyline([(pts[0][0] + 0.6, pts[0][1] + 0.5), (7.2, 3.0), (6.6, 7.2)], 0.3), pal, lambda u, v, x, y: 'dark', outline=False)
    s.post.append(eye(pts[1][0] + 0.4, pts[1][1] + 1.0, (20, 20, 20), rot=0))
    s.post.append(lambda px, owner: [put(px, x, y, ip['light'], owner, only_filled=True) for x, y in ((5, 6), (9, 9), (7, 12), (10, 4))])
    s.post.append(sparkle([(2, 2), (13, 13), (1, 9)], ip['light'], None))
    return s


def spoon(metal, post=()):
    pal = ramp(metal, belly=adjust_hex(metal, 1.25, 0.5))
    s = Sprite()
    s.add(ellipse(1.8, 0.0, 5.6, 3.0), pal, lambda u, v, x, y: 'belly' if (v > 0.8 and u > 0) else ('dark' if v < -1.2 else 'base'))
    s.add(ellipse(-2.0, 0.0, 1.0, 1.0), ramp('#d8302a'), None)
    s.post.append(lambda px, owner: hook_px(px, 3, 11))
    s.post.append(lambda px, owner: [put(px, x, y, (230, 230, 230)) for x, y in ((12, 3), (13, 2), (14, 1))])
    s.post.extend(post)
    return s


def crankbait(body, belly, lip, post=()):
    """Small fish-shaped plug with a clear lip and treble hooks."""
    s = fish(body, fin=body, belly=belly, u0=-3.0, u1=7.4, ht=3.4, hb=3.2, tail='fork', tail_w=2.6, tail_len=3.0,
             pectoral=False, eye_at=(5.0, 0.8), eye_col=(20, 20, 20), glint=(255, 255, 255),
             extra_front=[(tri((7.0, -0.6), (10.2, -1.6), (9.0, -3.2)), ramp(lip), None, True)], post=post)
    s.post.append(lambda px, owner: hook_px(px, 6, 11))
    s.post.append(lambda px, owner: hook_px(px, 11, 8))
    return s


def orb(core, ring, post=()):
    pal = ramp(core, belly=adjust_hex(core, 1.3, 0.4))
    rp = ramp(ring)
    s = Sprite(rot=0)
    s.add(polyline([(0, 7.5), (0, 3.4)], 0.45), ramp('#5a5a6a'), lambda u, v, x, y: 'base')
    s.add(ellipse(0, 3.4, 1.5, 0.9), rp, None)
    s.add(ellipse(0, -1.4, 4.4, 4.4), pal, lambda u, v, x, y: 'belly' if (u < -0.5 and v > 0.2) else ('dark' if v < -2.6 else 'base'))
    s.post.append(lambda px, owner: hook_px(px, 9, 12))
    s.post.extend(post)
    return s


def fly(post=()):
    """Feathered fly lure."""
    s = Sprite()
    feathers = [('#e8402a', 1.6), ('#f2c22a', 0.4), ('#3aa0e8', -0.9)]
    for col, off in feathers:
        s.add(polyline([(3.0, off * 0.4), (-2.0, off * 1.2), (-7.0, off * 2.2)], lambda T: 1.4 - 0.9 * T), ramp(col),
              lambda u, v, x, y: 'base' if (x + y) % 3 else 'light')
    s.add(ellipse(4.6, 0.0, 2.8, 1.8), ramp('#2a2a2a', belly='#6a6a6a'), None)
    s.add(ellipse(7.2, 0.0, 0.9, 0.9), ramp('#d8b040'), None)
    s.post.append(lambda px, owner: hook_px(px, 12, 5))
    s.post.extend(post)
    return s


def star_lure(core, edge, post=()):
    pal = ramp(core, belly=adjust_hex(core, 1.3, 0.4))
    s = Sprite(rot=0)
    pts = []
    for i in range(10):
        a = math.pi / 2 + i * math.pi / 5
        r = 6.6 if i % 2 == 0 else 2.8
        pts.append((r * math.cos(a), r * math.sin(a) - 0.4))
    tris = [tri((0, -0.4), pts[i], pts[(i + 1) % 10]) for i in range(10)]
    s.add(union(*tris), pal, lambda u, v, x, y: 'belly' if (u * u + (v + 0.4) ** 2) < 3 else ('dark' if v < -3 else 'base'))
    s.post.append(lambda px, owner: hook_px(px, 11, 11))
    s.post.extend(post)
    return s


def eye_lure(post=()):
    pal = ramp('#2a8a6a', belly='#8af0c0')
    s = Sprite(rot=0)
    s.add(ellipse(0, 0.2, 5.4, 5.4), ramp('#5a2a8a'), lambda u, v, x, y: 'base' if noise(x, y, 4) > 0.2 else 'dark')
    s.add(ellipse(0, 0.2, 3.4, 3.4), pal, lambda u, v, x, y: 'belly' if u < -1 and v > 1 else 'base')
    s.add(ellipse(0.2, 0.0, 0.9, 2.2), ramp('#101010'), None, False)
    s.post.append(lambda px, owner: hook_px(px, 12, 11, col=(220, 200, 255)))
    s.post.extend(post)
    return s


def simple_hook_bait():
    """Hook with a chunk of bait on it, hanging from a line."""
    s = Sprite(rot=0)
    s.add(ellipse(0.0, -1.8, 4.4, 3.8), ramp('#c89868', belly='#f0d8b0'),
          lambda u, v, x, y: 'belly' if (u < -0.5 and v > -0.8) else ('dark' if noise(x, y, 6) < 0.15 or v < -4.2 else 'base'))

    def fn(px, owner):
        for y in range(0, 6):
            put(px, 8, y, (232, 232, 232))
        # hook point sticking out below the bait
        for x, y in ((11, 12), (12, 11), (12, 10), (11, 13), (10, 13)):
            put(px, x, y, (200, 204, 214))
        put(px, 12, 9, (80, 84, 96))
    s.post.append(fn)
    return s


BAITS = {
    'basic_bait': simple_hook_bait,
    'worm_bait': lambda: worm('#e07a8a', '#f2b0b8', (180, 80, 100)),
    'dough_bait': dough,
    'frozen_shrimp_bait': lambda: shrimp('#f2a08a', '#a8e8ff'),
    'golden_worm_bait': lambda: worm('#f2c030', '#fff0a0', (190, 130, 20), post=[glow((255, 220, 90), 70), sparkle([(1, 1), (14, 14)], (255, 250, 200), None)]),
    'silver_bait': lambda: spoon('#c0c8d4', post=[sparkle([(2, 2)], (255, 255, 255), None)]),
    'big_fish_lure': lambda: crankbait('#f2b02a', '#fff0a0', '#e8f0f8', post=[sparkle([(1, 2)], (255, 240, 170), None)]),
    'deep_sea_bait': lambda: orb('#2ad8f0', '#1a5a8a', post=[glow((80, 220, 255), 110), sparkle([(2, 12), (13, 2)], (180, 250, 255), None)]),
    'premium_bait': lambda: fly(post=[sparkle([(1, 1)], (255, 240, 170), None)]),
    'legendary_bait': lambda: star_lure('#ff4a2a', '#f2c030', post=[glow((255, 120, 60), 120), sparkle([(1, 14), (14, 1)], (255, 240, 170), (255, 170, 80))]),
    'master_fisher_bait': lambda: eye_lure(post=[glow((170, 90, 255), 110), sparkle([(1, 1), (14, 14)], (230, 200, 255), None)]),
}


# ------------------------------------------------------------------ rods

def rod(shaft, handle, reel, tip=None, grip_band=None, line=(232, 232, 232), extra=None, cast=False, glow_col=None):
    """Vanilla-style diagonal rod: handle bottom-left, tip top-right, line hanging from the tip."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    S = hexrgb(shaft)
    Sd = adjust(S, 0.62, 1.1)
    Sl = adjust(S, 1.25, 0.8)
    H = hexrgb(handle)
    Hd = adjust(H, 0.6, 1.1)
    R = hexrgb(reel)
    T = hexrgb(tip) if tip else Sl
    # shaft: pixels along x+y=15 from (1,14) to (13,2), two px thick on the lower half
    for i in range(0, 13):
        x, y = 1 + i, 14 - i
        body = H if i < 4 else S
        dark = Hd if i < 4 else Sd
        put(px, x, y, body)
        if i < 9:
            put(px, x + 1, y, dark)  # thickness (lower-right side = shade)
        if grip_band and i in (1, 3):
            put(px, x, y, hexrgb(grip_band))
        if i >= 11:
            put(px, x, y, T)
    put(px, 1, 14, Hd)
    put(px, 0, 15, Hd)
    # highlight line on upper-left side of the mid shaft
    for i in range(5, 10):
        put(px, 1 + i - 1, 14 - i, Sl)
    # reel
    for x, y, c in ((4, 12, R), (5, 12, adjust(R, 0.6)), (4, 13, adjust(R, 0.7)), (5, 13, adjust(R, 0.45)), (3, 12, adjust(R, 1.2, 0.7))):
        put(px, x, y, c)
    if not cast:
        # line from tip hanging down with a hook
        for y in range(2, 11):
            put(px, 14, y, line)
        put(px, 14, 11, (150, 150, 160))
        put(px, 13, 12, (150, 150, 160))
        put(px, 12, 11, (150, 150, 160))
    else:
        # taut line leaving to the top-right
        put(px, 14, 1, line)
        put(px, 15, 0, line)
    if extra:
        extra(px, cast)
    if glow_col:
        glow(glow_col, 80)(px, None)
    return img


def gem(x, y, col):
    def fn(px, cast):
        put(px, x, y, col)
        put(px, x + 1, y, adjust(col, 0.6))
    return fn


RODS = {
    'weathered_rod': lambda cast: rod('#8a7058', '#5a4636', '#6a6a6a', extra=lambda px, c: [put(px, 7, 8, (60, 46, 34)), put(px, 10, 5, (60, 46, 34))], cast=cast),
    'basic_rod': lambda cast: rod('#b08850', '#6a4a2a', '#8a8a8a', cast=cast),
    'bamboo_rod': lambda cast: rod('#9ac048', '#5a7a28', '#c8b060', extra=lambda px, c: [put(px, 1 + i, 14 - i, (70, 100, 30)) for i in (5, 8, 11)], cast=cast),
    'iron_rod': lambda cast: rod('#d0d4dc', '#4a4a52', '#9aa0aa', grip_band='#2a2a30', cast=cast),
    'gold_rod': lambda cast: rod('#f2c83a', '#8a5a1a', '#fff0a0', grip_band='#5a3a10', cast=cast),
    'obsidian_rod': lambda cast: rod('#3a2a5a', '#1a1426', '#8a5ad8', tip='#b080ff', cast=cast),
    'advanced_rod': lambda cast: rod('#3aa06a', '#2a2a2a', '#d0d8e0', tip='#8af0b8', grip_band='#e8e8e8', cast=cast),
    'master_rod': lambda cast: rod('#2a5ab8', '#2a1a10', '#f2c83a', tip='#f2e090', grip_band='#f2c83a',
                                   extra=gem(8, 7, (255, 220, 90)), cast=cast),
    'mythic_rod': lambda cast: rod('#b048e0', '#2a1040', '#f0d0ff', tip='#ffffff', grip_band='#f2c83a',
                                   extra=gem(8, 7, (120, 255, 255)), cast=cast, glow_col=(200, 120, 255)),
    'legendary_rod': lambda cast: rod('#e8a020', '#5a1010', '#ff4a2a', tip='#ffffff', grip_band='#ff4a2a',
                                      extra=gem(8, 7, (255, 80, 60)), cast=cast, glow_col=(255, 170, 60)),
}

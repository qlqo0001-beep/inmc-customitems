"""Every fish in fish.yml -> sprite builder."""
import math
from kinds import *

W = (255, 255, 255)
GOLD = (255, 214, 90)


def spotted(color, rate, seed=1, region=-0.2):
    return lambda u, v, t, n, x, y: color if n > region and noise(x, y, seed) < rate else None


def bars(color, period, width=1.0, region=-0.3, slant=0.6):
    return lambda u, v, t, n, x, y: color if n > region and ((u + v * slant) % period) < width else None


def shark(body, belly, fin=None, extra_post=(), teeth=False, stripes=None, eye_col=(10, 10, 12), glint=None, big=False):
    ht, hb = (4.3, 3.6) if not big else (4.9, 4.2)
    def sp(u, v, t, n, x, y):
        if stripes:
            r = stripes(u, v, t, n, x, y)
            if r is not None:
                return r
        return None
    post = list(extra_post)
    if teeth:
        post.append(lambda px, owner: [put(px, *local_to_px(u_, -1.3), (240, 240, 230), owner, only_filled=True) for u_ in (7.0, 8.2, 9.2)])
    return fish(body, fin=fin or body, belly=belly, ht=ht, hb=hb, peak=0.5, nose=0.8, u0=-3.4, u1=10.6,
                tail='crescent', tail_len=4.6, tail_w=4.0,
                dorsal=[((1.2, ht - 0.6), (4.2, ht - 0.6), (1.0, ht + 2.6))],
                anal=[((-1.5, -hb + 0.5), (0.3, -hb + 0.5), (-1.2, -hb - 1.4))],
                stripes=sp, eye_at=(7.4, 0.9), eye_col=eye_col, glint=glint, post=post)


def koi(base, patch, patch2=None, rate=0.35, fin=None, post=(), glint=None, sparkle_pts=None):
    def sp(u, v, t, n, x, y):
        k = noise(x // 2, y // 2, 42)
        if k < rate:
            return patch
        if patch2 and k > 1 - rate * 0.6:
            return patch2
        return None
    extra = []
    # barbels at the mouth
    extra.append((polyline([(9.6, -0.6), (10.6, -1.8)], 0.35), ramp(adjust_hex(base, 0.7)), None, False))
    p = list(post)
    if sparkle_pts:
        p.append(sparkle(sparkle_pts, W, None))
    return fish(base, fin=fin or adjust_hex(base, 0.95, 0.7), belly=adjust_hex(base, 1.1, 0.6), tail='fork', notch=0.2,
                tail_w=4.0, tail_len=4.8, dorsal=[((-1.0, 3.9), (5.0, 3.9), (0.0, 5.4))],
                spots=sp, glint=glint, extra_front=extra, post=p)


def serpent(body, belly, crest=None, spikes=None, horns=None, whisk=None, stripes=None, amp=2.2, width=2.3,
            waves=1.3, post=(), eye_col=(255, 60, 40), glint=None, phase=0.1):
    extra = []
    if horns:
        extra.append((polyline([(8.2, 1.8), (6.6, 3.8), (5.2, 4.2)], lambda T: 0.7 - 0.35 * T), ramp(horns), None, True))
    if whisk:
        extra.append((polyline([(9.8, -0.6), (9.4, -2.8), (7.6, -4.2)], 0.35), ramp(whisk), None, False))
    s = eel(body, belly=belly, amp=amp, waves=waves, width=width, crest=crest, stripes=stripes, eye_col=eye_col,
            glint=glint, post=post, extra=extra, phase=phase)
    if spikes:
        # re-render crest as spikes: sample along back
        pass
    return s


SPECIES = {}


def sp(fid):
    def deco(fn):
        SPECIES[fid] = fn
        return fn
    return deco


# ------------------------------------------------------------------ F
@sp('cod')
def _():
    return fish('#b39a6c', fin='#8a7352', belly='#ece2c6',
                spots=spotted('dark', 0.16, 1, -0.1),
                dorsal=[((1.5, 3.8), (5.0, 3.8), (2.4, 5.8))])


@sp('salmon')
def _():
    return fish('#c85a57', fin='#8f3a3d', belly='#f0b8a4', top='#5e4a66',
                spots=spotted('dark', 0.12, 2, 0.3), dorsal=[((1.5, 3.8), (4.2, 3.8), (2.2, 5.6))])


@sp('tropical_fish')
def _():
    return fish('#f08a24', fin='#1c1c1c', belly='#ffb45a', ht=5.4, hb=5.0, u0=-2.4, u1=9.6, peak=0.5, nose=1.4,
                tail='round', tail_w=3.6, tail_len=4.6,
                stripes=lambda u, v, t, n, x, y: (245, 245, 245) if abs(u - 6.0) < 0.9 or abs(u - 1.6) < 1.0 or abs(u + 2.2) < 0.6 else None,
                dorsal=[((-0.5, 4.8), (4.5, 4.8), (1.0, 6.4))])


@sp('mackerel')
def _():
    return fish('#4f8c86', fin='#3a5f66', belly='#dfe6ea', top='#1f4a55', ht=3.8, hb=3.4,
                stripes=lambda u, v, t, n, x, y: (20, 40, 50) if n > 0.15 and (math.sin(u * 1.7 + v * 1.1) > 0.55) else None,
                dorsal=[((0.5, 3.2), (3.0, 3.2), (1.2, 4.8))])


@sp('carp')
def _():
    return fish('#b88a3a', fin='#8e6a2c', belly='#ead69a', ht=4.9, hb=4.3, peak=0.5,
                spots=lambda u, v, t, n, x, y: 'dark' if (x + 2 * y) % 4 == 0 and n > -0.2 else None,
                dorsal=[((-1.0, 4.4), (4.0, 4.4), (0.0, 6.0))],
                extra_front=[(polyline([(9.8, -0.8), (10.8, -2.0)], 0.35), ramp('#6e5222'), None, False)])


@sp('catfish')
def _():
    wh = ramp('#d8c8a0')
    return fish('#5a5a4e', fin='#3e3e36', belly='#b7b39a', ht=3.4, hb=3.4, peak=0.7, nose=2.4, tail='round',
                tail_w=3.0, spots=spotted('dark', 0.2, 4, -0.2), pectoral=True,
                eye_at=(8.2, 1.2),
                extra_front=[(polyline([(9.0, 0.2), (8.2, 2.4), (6.2, 3.6)], 0.4), wh, None, False),
                             (polyline([(9.0, -0.6), (8.0, -2.8), (6.0, -3.8)], 0.4), wh, None, False)])


@sp('bass')
def _():
    return fish('#6d8a3e', fin='#4c6230', belly='#e1e0b0', top='#3d5226', ht=4.3, hb=3.9, nose=1.8,
                stripes=lambda u, v, t, n, x, y: (40, 55, 25) if abs(n - 0.0) < 0.2 and (int(u * 1.3) % 2 == 0) else None,
                dorsal=[((-1.0, 3.8), (2.0, 3.8), (0.0, 5.8)), ((2.0, 3.8), (5.0, 3.8), (3.0, 5.2))],
                eye_at=(8.0, 0.9), glint=None)


@sp('old_boot')
def _():
    return boot('#6b4a2e', '#3a2a1c', '#4f8a3a')


# ------------------------------------------------------------------ E
@sp('pufferfish')
def _():
    return puffer('#e8c24a', '#f5ecc0', '#b8872a')


@sp('cod_rare')
def _():
    return fish('#8c7a4a', fin='#6a5a32', belly='#e8dcb4', top='#4e4428', ht=4.8, hb=4.3,
                spots=spotted((70, 56, 30), 0.25, 9, -0.2),
                dorsal=[((0.0, 4.2), (2.6, 4.2), (0.8, 6.0)), ((3.0, 4.0), (5.6, 4.0), (3.8, 5.6))],
                post=[sparkle([(3, 2)], GOLD, None)])


@sp('salmon_rare')
def _():
    # 산란기 홍연어: 붉은 몸 · 초록 머리
    return fish('#d23a2e', fin='#6a8a4a', belly='#f07a5a', top='#9a1f1a', ht=4.8, hb=4.2, nose=1.4,
                stripes=lambda u, v, t, n, x, y: ((70, 110, 60) if n > -0.2 else (170, 190, 150)) if t > 0.8 else None,
                dorsal=[((0.8, 4.2), (3.6, 4.2), (1.4, 6.2))])


@sp('bluefish')
def _():
    # 방어: 푸른 등 · 노란 옆줄 · 노란 꼬리
    return fish('#5f86b8', fin='#e8c23a', belly='#e8eef4', top='#2e4f86', ht=4.0, hb=3.6,
                stripes=lambda u, v, t, n, x, y: (240, 200, 60) if abs(n + 0.02) < 0.16 else None,
                dorsal=[((1.0, 3.4), (4.4, 3.4), (1.6, 5.0))])


@sp('halibut')
def _():
    return flatfish('#8a6e4c', 'dark', belly='#c8b08a')


@sp('squid')
def _():
    return squid('#e6d6c8', arm='#d8b8a8', spots=lambda u, v, t, n, x, y: (190, 110, 100) if noise(x, y, 13) < 0.3 else None)


@sp('octopus')
def _():
    return octopus('#c4502e')


@sp('sea_bream')
def _():
    # 참돔: 분홍빛 붉은 몸 · 파란 점
    return fish('#e0706a', fin='#c0504e', belly='#f7d0c4', ht=5.0, hb=4.4, peak=0.52, nose=1.6,
                spots=lambda u, v, t, n, x, y: (90, 190, 240) if n > -0.1 and noise(x, y, 21) < 0.14 else None,
                dorsal=[((-1.5, 4.4), (4.0, 4.4), (-0.4, 6.2))])


@sp('yellowtail')
def _():
    return fish('#c9d0d8', fin='#f2c21a', belly='#f4f6f8', top='#6c8aa6', ht=4.0, hb=3.6,
                stripes=lambda u, v, t, n, x, y: (240, 196, 40) if abs(n + 0.05) < 0.18 else None,
                dorsal=[((1.0, 3.4), (4.4, 3.4), (1.6, 5.0))], tail_w=4.2)


@sp('phantom_eel')
def _():
    return eel('#9fe8e0', belly='#e6fffb', width=2.2, amp=1.8, eye_col=(40, 220, 255), glint=None,
               post=[glow((120, 255, 240), 90), sparkle([(2, 3), (12, 12)], (200, 255, 250), None)])


# ------------------------------------------------------------------ D
@sp('swordfish')
def _():
    bill = ramp('#5a5a6a')
    return fish('#4d5a78', fin='#343d56', belly='#dcdfe6', top='#2b3350', ht=3.6, hb=3.2, u0=-4.4, u1=7.2, nose=0.7,
                tail='crescent', tail_len=4.4, tail_w=3.8,
                dorsal=[((1.5, 3.0), (4.0, 3.0), (1.2, 6.0))],
                extra_front=[(capsule((6.4, 0.4), (10.8, 0.4), lambda t: 0.8 - 0.35 * t), bill, None, True)],
                eye_at=(5.4, 0.9))


@sp('tuna')
def _():
    return fish('#3a5a8c', fin='#f2c94c', belly='#d8e0ea', top='#23355e', ht=4.8, hb=4.3, tail='crescent', tail_w=3.6,
                dorsal=[((2, 4.2), (4.5, 4.2), (2.6, 6.4))],
                spots=lambda u, v, t, n, x, y: (242, 201, 76) if -1.5 < n < -0.9 and u < 0 and (x + y) % 2 == 0 else None)


@sp('shark_small')
def _():
    return shark('#7d8a96', '#eef1f4')


@sp('sturgeon')
def _():
    return fish('#6b6a52', fin='#4f4e3c', belly='#d0c8a8', ht=3.2, hb=2.8, u0=-4.0, u1=10.2, peak=0.45, nose=0.6,
                tail='crescent', tail_w=3.4,
                stripes=lambda u, v, t, n, x, y: (225, 220, 190) if (abs(n - 0.55) < 0.2 or abs(n + 0.05) < 0.16) and int(u * 1.2) % 2 == 0 else None,
                extra_front=[(polyline([(9.2, -1.0), (9.4, -2.4)], 0.3), ramp('#3a3a2a'), None, False)],
                dorsal=[((-2.0, 2.4), (-0.4, 2.4), (-1.6, 3.8))], eye_at=(7.8, 0.6))


@sp('giant_catfish')
def _():
    wh = ramp('#c8b890')
    return fish('#454538', fin='#2e2e26', belly='#a8a484', ht=4.2, hb=4.2, peak=0.72, nose=2.6, tail='round',
                tail_w=3.6, spots=spotted('dark', 0.28, 5, -0.2), eye_at=(8.0, 1.4),
                extra_front=[(polyline([(9.0, 0.4), (8.0, 3.0), (5.6, 4.8)], 0.45), wh, None, False),
                             (polyline([(9.0, -0.8), (7.8, -3.4), (5.4, -5.0)], 0.45), wh, None, False)])


@sp('marlin')
def _():
    bill = ramp('#3a4a7a')
    return fish('#2f5fae', fin='#233f7a', belly='#e8eef6', top='#1a2f66', ht=3.7, hb=3.3, u0=-4.4, u1=7.2, nose=0.7,
                tail='crescent', tail_len=4.4, tail_w=3.8,
                stripes=lambda u, v, t, n, x, y: (140, 190, 255) if n > -0.5 and int(u * 1.1) % 3 == 0 else None,
                dorsal=[((-0.5, 3.0), (5.0, 3.0), (0.2, 7.4))],
                extra_front=[(capsule((6.4, 0.5), (10.8, 0.5), lambda t: 0.8 - 0.35 * t), bill, None, True)],
                eye_at=(5.4, 1.0))


@sp('grouper')
def _():
    return fish('#8a5a3a', fin='#6a4228', belly='#d8b89a', ht=5.0, hb=4.6, peak=0.55, nose=2.2, tail='round', tail_w=3.8,
                spots=lambda u, v, t, n, x, y: (70, 40, 26) if noise(x // 2, y // 2, 8) < 0.35 else ((200, 160, 120) if noise(x, y, 9) < 0.1 else None),
                dorsal=[((-1.5, 4.4), (4.0, 4.4), (-0.5, 5.8))], eye_at=(7.8, 1.4))


@sp('barracuda')
def _():
    return fish('#9aa6ae', fin='#5a646e', belly='#eef2f4', top='#4a5866', ht=2.9, hb=2.6, u0=-4.6, u1=10.6, peak=0.5, nose=0.5,
                tail_w=3.6, tail_len=4.2,
                stripes=lambda u, v, t, n, x, y: (50, 60, 70) if n > 0.0 and int((u + 20) * 0.9) % 3 == 0 else None,
                dorsal=[((-2.4, 2.2), (-0.8, 2.2), (-2.0, 3.8)), ((2.2, 2.4), (3.6, 2.4), (2.4, 3.8))],
                eye_at=(7.8, 0.6),
                post=[lambda px, owner: put(px, *local_to_px(9.2, -0.5), (240, 240, 240), owner, only_filled=True)])


@sp('arapaima')
def _():
    return fish('#6d7560', fin='#8a2a22', belly='#c8c8a8', top='#3e4636', ht=3.6, hb=3.2, u0=-4.2, u1=10.2, nose=1.4,
                tail='round', tail_w=3.4,
                stripes=lambda u, v, t, n, x, y: ((190, 50, 40) if (x + y) % 2 else (150, 36, 30)) if t < 0.38 else None,
                dorsal=[((-3.4, 2.8), (-0.6, 2.8), (-3.0, 4.4))], anal=[((-3.4, -2.6), (-0.6, -2.6), (-3.0, -4.2))])


@sp('crystal_koi')
def _():
    return koi('#dff6ff', (150, 220, 255), (255, 255, 255), rate=0.3, fin='#bfefff', glint=W,
               post=[alpha_scale(0.9), glow((180, 240, 255), 80)], sparkle_pts=[(2, 3), (13, 12)])


# ------------------------------------------------------------------ C
@sp('whale_small')
def _():
    return whale('#5a7188', '#dfe6ee')


@sp('giant_squid')
def _():
    return squid('#b0382e', arm='#8e2a24', big=True,
                 spots=lambda u, v, t, n, x, y: (230, 140, 120) if noise(x, y, 17) < 0.25 else None)


@sp('manta_ray')
def _():
    return ray('#242830', belly='#e8e8ec',
               pattern=lambda u, v, x, y: (230, 230, 236) if 2.0 < abs(v) < 3.4 and 1.0 < u < 4.0 else None,
               tail_col='#1a1c22')


@sp('electric_eel')
def _():
    return eel('#4a4a2e', belly='#c8a44a', width=2.3, amp=1.6,
               stripes=lambda T, n, x, y: (255, 230, 60) if abs(n) < 0.35 and (x * 2 + y) % 5 == 0 else None,
               post=[sparkle([(3, 1), (13, 13)], (255, 240, 100), None)])


@sp('piranha_king')
def _():
    crown = ramp('#f2c230')
    return fish('#8a96a0', fin='#5e6a74', belly='#e0402e', top='#4e5a66', ht=5.0, hb=4.8, peak=0.5, nose=1.8, u1=9.0,
                tail_w=3.6, spots=spotted((200, 210, 220), 0.14, 23, 0.0),
                dorsal=[((-0.5, 4.4), (2.8, 4.4), (0.6, 5.8))],
                extra_front=[(union(tri((4.0, 5.2), (7.8, 5.2), (4.2, 7.2)), tri((5.2, 5.4), (7.0, 5.4), (6.4, 7.6)), tri((6.0, 5.2), (7.8, 5.2), (7.9, 7.0))), crown,
                              lambda u, v, x, y: 'light' if v < 6 else 'base', True)],
                eye_at=(6.8, 1.2), eye_col=(200, 20, 20),
                post=[lambda px, owner: [put(px, *local_to_px(u_, -1.4), (250, 250, 240), owner, only_filled=True) for u_ in (7.2, 8.2)]])


@sp('anglerfish')
def _():
    lure = ramp('#fff27a')
    stalk = ramp('#4a3a2e')
    return fish('#5a4636', fin='#3e3026', belly='#8a7058', ht=5.0, hb=4.6, peak=0.72, nose=3.0, u0=-2.6, u1=9.6,
                tail='round', tail_w=3.0, tail_len=3.8,
                spots=lambda u, v, t, n, x, y: (30, 22, 18) if t > 0.72 and -0.55 < n < -0.1 else None,
                extra_front=[(polyline([(6.4, 4.2), (8.6, 7.0), (10.4, 6.2)], 0.35), stalk, None, False),
                             (ellipse(10.6, 5.6, 0.95, 0.95), lure, lambda u, v, x, y: 'light', False)],
                eye_at=(6.4, 1.6), eye_col=(230, 220, 120),
                post=[lambda px, owner: [put(px, *local_to_px(u_, -0.9 + (0.6 if i % 2 else 0)), (245, 245, 235), owner, only_filled=True) for i, u_ in enumerate((6.8, 7.6, 8.4, 9.2))],
                      glow((255, 240, 120), 70)])


@sp('coelacanth')
def _():
    lobe = ramp('#34507a')
    return fish('#35507c', fin='#26395a', belly='#6c84a8', ht=4.3, hb=4.0, tail='round', tail_w=3.8, nose=1.6,
                spots=lambda u, v, t, n, x, y: (235, 240, 250) if noise(x, y, 31) < 0.15 else None,
                extra_front=[(ellipse(3.0, -4.4, 1.8, 1.0), lobe, None, True), (ellipse(-1.0, -4.0, 1.6, 1.0), lobe, None, True)],
                dorsal=[((0.5, 3.8), (3.0, 3.8), (1.2, 5.6)), ((-2.6, 3.0), (-1.0, 3.0), (-2.2, 4.4))])


@sp('oarfish')
def _():
    return eel('#c8ccd8', belly='#f2f4f8', width=2.4, amp=2.2, waves=1.2, crest='#e0302a',
               stripes=lambda T, n, x, y: (110, 120, 150) if n > -0.1 and (x + y) % 3 == 0 else None)


@sp('sunfish')
def _():
    return sunfish('#8a96a4', '#dde3ea')


@sp('jade_koi')
def _():
    return koi('#2e9a6a', (150, 230, 190), (20, 80, 55), rate=0.3, fin='#3cb884', glint=W, sparkle_pts=[(2, 2)])


# ------------------------------------------------------------------ B
@sp('megalodon')
def _():
    return shark('#4a5664', '#dfe3e8', teeth=True, big=True, glint=(255, 60, 60), eye_col=(20, 20, 20),
                 stripes=lambda u, v, t, n, x, y: (36, 42, 50) if n > 0.2 and noise(x, y, 44) < 0.15 else None)


@sp('kraken_tentacle')
def _():
    return tentacle('#7a3a8e', (240, 200, 230))


@sp('leviathan')
def _():
    return serpent('#2a7a7e', '#a8e0d0', crest='#1c5a60',
                   stripes=lambda T, n, x, y: (18, 70, 76) if n > 0.1 and (x + y) % 2 == 0 else None,
                   eye_col=(255, 230, 60))


@sp('ghost_whale')
def _():
    return whale('#b8d6e8', '#f2fbff', fluke='#a0c8e0',
                 post=[alpha_scale(0.78), glow((190, 235, 255), 90), sparkle([(1, 2), (14, 13)], (230, 250, 255), None)])


@sp('dragon_fish')
def _():
    wh = ramp('#f2d060')
    return fish('#d8442a', fin='#f2a62a', belly='#f7d070', top='#8a1e14', ht=4.2, hb=3.8, nose=1.4, tail_w=4.0,
                spots=lambda u, v, t, n, x, y: (255, 210, 90) if (x + y) % 3 == 0 and (x - y) % 2 == 0 and n > -0.3 else None,
                dorsal=[((-2.4, 3.4), (2.0, 3.4), (-1.6, 5.6))],
                extra_front=[(polyline([(10.2, -0.6), (10.6, -2.6), (9.2, -4.4)], 0.38), wh, None, False),
                             (polyline([(10.0, 0.6), (10.8, 2.6)], 0.38), wh, None, False)],
                glint=W, post=[sparkle([(2, 2)], GOLD, None)])


@sp('abyssal_shark')
def _():
    return shark('#1e2230', '#3e4658', fin='#141824', eye_col=(80, 255, 255),
                 stripes=lambda u, v, t, n, x, y: (60, 220, 240) if abs(n + 0.1) < 0.14 and int(u) % 2 == 0 else None,
                 extra_post=[glow((60, 200, 255), 60)])


@sp('titan_grouper')
def _():
    return fish('#6a6a62', fin='#4a4a42', belly='#b8b4a0', ht=5.4, hb=5.0, peak=0.55, nose=2.4, u0=-2.8, tail='round', tail_w=3.6,
                spots=lambda u, v, t, n, x, y: (80, 110, 60) if noise(x // 2, y // 2, 61) < 0.3 else ((40, 40, 36) if noise(x, y, 62) < 0.12 else None),
                dorsal=[((-1.5, 4.8), (4.0, 4.8), (-0.5, 6.2))], eye_at=(7.8, 1.6), eye_col=(255, 180, 40))


@sp('void_eel')
def _():
    return eel('#2a1840', belly='#5a3a80', width=2.3, amp=1.8, eye_col=(255, 80, 255),
               stripes=lambda T, n, x, y: (220, 80, 255) if abs(n) < 0.25 and (x + 2 * y) % 5 == 0 else None,
               post=[glow((180, 60, 255), 90), sparkle([(2, 2), (13, 12)], (240, 180, 255), None)])


@sp('ancient_sturgeon')
def _():
    return fish('#b8a67a', fin='#8a7850', belly='#ece2c0', top='#7a6a44', ht=3.4, hb=3.0, u0=-4.0, u1=10.2, peak=0.45, nose=0.6,
                tail='crescent', tail_w=3.4,
                stripes=lambda u, v, t, n, x, y: (255, 220, 120) if (abs(n - 0.55) < 0.22 or abs(n + 0.05) < 0.18) and int(u * 1.2) % 2 == 0 else None,
                extra_front=[(polyline([(9.2, -1.0), (9.4, -2.4)], 0.3), ramp('#5a4a2a'), None, False)],
                dorsal=[((-2.0, 2.6), (-0.4, 2.6), (-1.6, 4.0))], eye_at=(7.8, 0.6), eye_col=(90, 200, 255),
                post=[sparkle([(2, 3), (12, 1)], GOLD, None)])


@sp('ruby_koi')
def _():
    return koi('#c8142e', (255, 240, 240), (120, 0, 20), rate=0.28, fin='#e04058', glint=W,
               post=[glow((255, 60, 90), 70)], sparkle_pts=[(2, 2), (13, 13)])


# ------------------------------------------------------------------ A
@sp('world_serpent')
def _():
    return serpent('#3e7a3a', '#c8d890', crest='#2a5428', amp=2.4, width=2.5,
                   stripes=lambda T, n, x, y: (28, 70, 30) if n > 0.0 and (x + y) % 2 == 0 else ((230, 210, 110) if abs(n) < 0.15 and x % 3 == 0 else None),
                   eye_col=(255, 210, 40), post=[sparkle([(2, 1)], GOLD, None)])


@sp('celestial_whale')
def _():
    return whale('#23306e', '#8ca0e6', fluke='#1a2458',
                 pattern=lambda u, v, x, y, n: (255, 240, 170) if n > -0.3 and noise(x, y, 71) < 0.12 else None,
                 post=[glow((150, 170, 255), 80), sparkle([(1, 2), (14, 13), (13, 1)], (255, 244, 180), None)])


@sp('phoenix_fish')
def _():
    flame = ramp('#ffb020')
    return fish('#f25a1e', fin=flame, belly='#ffd460', top='#b8200e', ht=4.3, hb=3.9, tail_w=4.4, tail_len=5.0, notch=0.45,
                stripes=lambda u, v, t, n, x, y: (255, 230, 120) if n < -0.1 and noise(x, y, 81) < 0.25 else None,
                dorsal=[((-2.4, 3.6), (1.0, 3.6), (-3.4, 6.6)), ((0.8, 3.8), (4.2, 3.8), (0.2, 6.8))],
                glint=W, post=[glow((255, 150, 40), 90), sparkle([(1, 12), (3, 14)], (255, 220, 80), None)])


@sp('time_eel')
def _():
    return eel('#a07a2e', belly='#f2dc8a', width=2.3, amp=1.9, eye_col=(80, 220, 255),
               stripes=lambda T, n, x, y: (60, 40, 16) if int(T * 14) % 2 == 0 and abs(n) < 0.8 else None,
               post=[glow((255, 220, 120), 60), sparkle([(2, 2), (13, 13)], (255, 240, 170), None)])


@sp('storm_ray')
def _():
    def pat(u, v, x, y):
        # zig-zag lightning down the middle of each wing
        for side in (1, -1):
            if abs(v * side - (3.4 + (1.0 if int(u) % 2 else -0.2))) < 0.6 and -2 < u < 6:
                return (255, 230, 60)
        return None
    return ray('#3e4a66', belly='#a0acc8', pattern=pat, tail_col='#2a3248',
               post=[sparkle([(1, 1), (14, 3)], (255, 240, 120), None)])


@sp('crystal_leviathan')
def _():
    return serpent('#5ad0e8', '#e6fbff', crest='#a8f0ff', amp=2.3, width=2.4,
                   stripes=lambda T, n, x, y: (255, 255, 255) if (x * 3 + y) % 7 == 0 else ((40, 150, 190) if n > 0.4 else None),
                   eye_col=(40, 60, 255),
                   post=[glow((160, 240, 255), 90), sparkle([(1, 3), (14, 12)], W, None)])


@sp('shadow_shark')
def _():
    return shark('#2e2438', '#5a4a6a', fin='#1e1826', eye_col=(200, 60, 255),
                 stripes=lambda u, v, t, n, x, y: (90, 50, 130) if noise(x, y, 91) < 0.12 else None,
                 extra_post=[alpha_scale(0.92), glow((120, 60, 180), 90)])


@sp('spirit_koi')
def _():
    return koi('#e6f7e6', (140, 230, 170), (255, 250, 200), rate=0.3, fin='#c8f0d0', glint=(80, 220, 120),
               post=[alpha_scale(0.9), glow((160, 255, 190), 90)], sparkle_pts=[(2, 2), (13, 13), (1, 9)])


@sp('void_serpent')
def _():
    return serpent('#1c1030', '#4a2a6e', crest='#3a1a5e', amp=2.3, width=2.4,
                   stripes=lambda T, n, x, y: (255, 255, 255) if noise(x, y, 101) < 0.08 else ((170, 80, 255) if abs(n) < 0.15 else None),
                   eye_col=(255, 60, 255), post=[glow((140, 60, 220), 100)])


@sp('diamond_koi')
def _():
    return koi('#b8f4f4', (255, 255, 255), (70, 200, 220), rate=0.3, fin='#9aeaf0', glint=W,
               post=[glow((180, 255, 255), 100)], sparkle_pts=[(1, 2), (14, 13), (13, 1), (2, 14)])


# ------------------------------------------------------------------ S
@sp('cosmic_serpent')
def _():
    def gal(T, n, x, y):
        k = noise(x, y, 111)
        if k < 0.09:
            return (255, 255, 255)
        if n > -0.1:
            return (120, 60, 200) if (x + y) % 3 == 0 else ((230, 80, 190) if (x + y) % 5 == 0 else None)
        return None
    return serpent('#1a1a4a', '#6a4ab8', crest='#e050c0', amp=2.4, width=2.5, stripes=gal, eye_col=(255, 240, 120),
                   post=[glow((170, 110, 255), 110), sparkle([(1, 1), (14, 14), (13, 2)], (255, 250, 200), (180, 150, 255))])


@sp('god_whale')
def _():
    halo = ramp('#ffe070')
    s = whale('#f4efe0', '#fffbef', fluke='#e8dcb8',
              pattern=lambda u, v, x, y, n: (255, 214, 90) if abs(n) < 0.12 else None,
              post=[glow((255, 230, 140), 120), sparkle([(1, 1), (14, 14), (1, 13)], (255, 250, 210), (255, 220, 120))])
    return s


@sp('eternal_dragon')
def _():
    return serpent('#b01a1a', '#f2c040', crest='#f2a020', horns='#f2e0b0', whisk='#f2d070', amp=2.3, width=2.5,
                   stripes=lambda T, n, x, y: (255, 200, 60) if n > 0.2 and (x + y) % 2 == 0 else None,
                   eye_col=(255, 240, 80),
                   post=[glow((255, 150, 60), 110), sparkle([(1, 1), (14, 14)], (255, 240, 170), (255, 180, 80))])


@sp('dimension_fish')
def _():
    def split(u, v, t, n, x, y):
        if (x + y) % 5 == 0 and noise(x, y, 121) < 0.5:
            return (255, 255, 255)
        return (250, 60, 200) if (x - y) > (int(u) % 3) - 1 else (60, 230, 255)
    return fish('#8a50e0', fin='#40e0ff', belly='#e0c0ff', ht=4.6, hb=4.2, tail_w=4.2, notch=0.45, spots=split,
                dorsal=[((-1.0, 4.0), (3.0, 4.0), (-0.4, 6.4))], eye_col=(255, 255, 255),
                post=[glow((200, 120, 255), 110), sparkle([(1, 1), (14, 14), (13, 1)], W, (200, 140, 255))])


@sp('chaos_leviathan')
def _():
    return serpent('#2a0a0a', '#b8260e', crest='#ff5a1a', horns='#3a3030', amp=2.4, width=2.5,
                   stripes=lambda T, n, x, y: (255, 110, 30) if abs(n) < 0.2 and (x + y) % 2 == 0 else None,
                   eye_col=(255, 240, 60),
                   post=[glow((255, 80, 30), 110), sparkle([(1, 1), (14, 14)], (255, 180, 60), (200, 40, 20))])


@sp('infinity_eel')
def _():
    return infinity('#6a4ae0', (140, 120, 255),
                    post=[glow((150, 130, 255), 110), sparkle([(1, 1), (14, 14)], (220, 230, 255), (140, 120, 255))])


@sp('primordial_shark')
def _():
    return shark('#5a6a3a', '#d8d0a0', fin='#3e4a28', teeth=True, big=True, eye_col=(255, 170, 30),
                 stripes=lambda u, v, t, n, x, y: (160, 130, 60) if n > 0.1 and int((u + v) * 0.9) % 3 == 0 else None,
                 extra_post=[glow((200, 190, 90), 100), sparkle([(1, 1), (14, 14)], (255, 230, 150), (200, 170, 80))])


@sp('astral_ray')
def _():
    return ray('#141a4a', belly='#6a7ad8',
               pattern=lambda u, v, x, y: (255, 250, 210) if noise(x, y, 131) < 0.12 else ((120, 150, 255) if abs(v) > 5.2 else None),
               tail_col='#2a3480',
               post=[glow((120, 150, 255), 110), sparkle([(1, 1), (14, 14), (1, 14)], (255, 250, 210), (150, 170, 255))])


@sp('omega_koi')
def _():
    return koi('#fff6e6', (255, 190, 40), (220, 30, 40), rate=0.3, fin='#ffe0a0', glint=W,
               post=[glow((255, 210, 90), 120)], sparkle_pts=[(1, 1), (14, 14), (13, 1), (1, 13)])


@sp('genesis_fish')
def _():
    def rainbow(u, v, t, n, x, y):
        if n < -0.2:
            return None
        k = int((u + 12) * 0.9) % 6
        return [(255, 120, 120), (255, 200, 110), (255, 250, 150), (150, 255, 170), (140, 200, 255), (210, 150, 255)][k] if n > 0.2 else None
    return fish('#fffaf0', fin='#ffe8a0', belly='#ffffff', ht=4.6, hb=4.2, tail_w=4.4, tail_len=5.0, notch=0.4,
                stripes=rainbow, dorsal=[((-2.0, 4.0), (3.0, 4.0), (-2.0, 6.6))], eye_col=(255, 180, 40),
                post=[glow((255, 245, 190), 140), sparkle([(1, 1), (14, 14), (13, 1), (1, 13)], W, (255, 230, 150))])

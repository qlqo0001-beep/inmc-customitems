"""Archetype builders. Each returns a Sprite."""
import math
AMPK = 1.2
from engine import *


def fin_pal(pal_or_hex):
    return ramp(pal_or_hex) if isinstance(pal_or_hex, str) else pal_or_hex


def fish(body, fin=None, belly=None, top=None,
         u0=-3.8, u1=9.8, ht=4.4, hb=3.9, peak=0.58, nose=1.0, camber=0.0,
         tail='fork', tail_len=4.4, tail_w=3.6, notch=0.3,
         dorsal=None, anal=None, pectoral=True,
         stripes=None, spots=None, eye_at=None, eye_col=(250, 214, 64), glint=None, gill=True,
         extra_back=(), extra_front=(), post=(), rot=45.0, tailw=0.9):
    pal = ramp(body, belly, top)
    fp = fin_pal(fin or adjust_hex(body, 0.85, 1.1))
    s = Sprite(rot)
    pred, geo = body_profile(u0, u1, ht, hb, peak=peak, camber=camber, nose=nose, tailw=tailw)
    # things behind body
    if tail == 'fork':
        s.add(tail_fork(u0 + 0.8, u0 - tail_len, 0.9, tail_w, notch=notch), fp,
              lambda u, v, x, y: 'dark' if abs(v) > tail_w * 0.55 or (x - y) % 3 == 0 else 'base')
    elif tail == 'round':
        s.add(tail_round(u0 + 0.8, u0 - tail_len, 1.0, tail_w), fp,
              lambda u, v, x, y: 'dark' if abs(v) > tail_w * 0.6 else 'base')
    elif tail == 'crescent':
        # lunate: tall thin crescent
        def cres(u, v):
            if u > u0 + 0.8 or u < u0 - tail_len:
                return False
            s_ = (u0 + 0.8 - u) / (tail_len + 0.8)
            w = 0.9 + (tail_w - 0.9) * s_ ** 0.7
            back = u0 - tail_len + (abs(v) / tail_w) ** 2 * tail_len * 0.55
            return abs(v) <= w and u >= back
        s.add(cres, fp, lambda u, v, x, y: 'dark' if abs(v) > tail_w * 0.5 else 'base')
    for d in (dorsal or []):
        s.add(tri(*d), fp, lambda u, v, x, y: 'dark' if v > ht + 0.8 or (x + y) % 2 == 0 else 'base')
    for a in (anal or []):
        s.add(tri(*a), fp, lambda u, v, x, y: 'base')
    for p in extra_back:
        s.add(*p)
    s.add(pred, pal, body_shader(geo, pal, stripes, spots))
    if pectoral:
        pu = u1 - (u1 - u0) * 0.38
        s.add(tri((pu, -0.2), (pu - 2.6, -1.6), (pu - 0.6, -1.9)), fp, lambda u, v, x, y: 'dark', outline=False)
    for p in extra_front:
        s.add(*p)
    eu, ev = eye_at if eye_at else (u1 - 2.0, 0.6)
    if gill:
        # 아가미 선 — 눈 뒤로 몸을 가로지르는 짙은 호
        def gills(px, owner, eu=eu):
            for vv in (-1.6, -0.8, 0.0, 0.8, 1.6):
                put(px, *local_to_px(eu - 1.8 + 0.18 * vv * vv, vv), pal['dark'], owner, only_filled=True)
        s.post.append(gills)
    # 입
    s.post.append(lambda px, owner: put(px, *local_to_px(u1 - 0.7, -0.5), pal['out'], owner, only_filled=True))
    s.post.append(eye(eu, ev, eye_col, glint))
    s.post.extend(post)
    return s


def adjust_hex(h, v=1.0, s=1.0, hshift=0.0):
    r, g, b = adjust(hexrgb(h), v, s, hshift)
    return '#%02x%02x%02x' % (r, g, b)


def eel(body, fin=None, belly=None, amp=1.6, waves=1.2, width=1.7, u0=-9.4, u1=9.6, head=1.25,
        crest=None, stripes=None, eye_col=(250, 214, 64), glint=None, post=(), extra=(), phase=0.0, tailfin=True):
    """Sinuous body along the diagonal."""
    pal = ramp(body, belly)
    fp = fin_pal(fin or adjust_hex(body, 0.8, 1.1))
    pts = []
    n = 24
    for i in range(n + 1):
        u = u0 + (u1 - u0) * i / n
        t = i / n
        v = AMPK * amp * math.sin((t * waves * 2 + phase) * math.pi) * (0.45 + 0.55 * (1 - t))
        pts.append((u, v))

    def r(T):
        # thin tail -> thick near head, rounded head
        base = 0.45 + (width * 0.85 - 0.45) * min(1, T / 0.6) ** 0.8
        if T > 0.88:
            base *= head * math.sqrt(max(0.0, 1 - ((T - 0.88) / 0.12) ** 2)) if T < 1 else 0
            base = max(base, 0.0)
        return base
    tube = polyline(pts, r)
    al = along(pts)
    s = Sprite()
    crest_rgb = hexrgb(crest) if crest else None
    if tailfin:
        tu, tv = pts[0]
        s.add(ellipse(tu - 0.2, tv, 1.6, 1.3), fp)
    for p in extra:
        s.add(*p)

    def shade(u, v, x, y):
        T, side = al(u, v)
        rr = max(r(T), 0.01)
        nrm = side / rr
        col = 'base'
        if nrm > 0.3:
            col = 'dark'
        elif nrm < -0.35:
            col = 'belly'
        elif 0.0 < nrm <= 0.3 and 0.2 < T < 0.85:
            col = 'light'
        if stripes:
            rr2 = stripes(T, nrm, x, y)
            if rr2 is not None:
                col = rr2
        if crest_rgb and nrm > 0.3 and 0.08 < T < 0.9:
            col = crest_rgb
        return col
    s.add(tube, pal, shade)
    hu, hv = pts[-3]
    s.post.append(eye(hu + 0.2, hv + 0.55, eye_col, glint))
    s.post.extend(post)
    return s


def noise(x, y, seed=0):
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xffffffff
    h = ((h ^ (h >> 13)) * 1274126177) & 0xffffffff
    return (h ^ (h >> 16)) / 0xffffffff


def puffer(body, belly, spike, post=()):
    pal = ramp(body, belly)
    sp = ramp(spike)
    s = Sprite()
    cu, cv, R = 1.0, 0.0, 5.6
    body_p = ellipse(cu, cv, R, R * 0.95)
    # spikes: small triangles around
    spikes = []
    for i in range(12):
        a = i / 12 * 2 * math.pi + 0.2
        bu, bv = cu + math.cos(a) * (R - 0.4), cv + math.sin(a) * (R - 0.4)
        tu, tv = cu + math.cos(a) * (R + 1.6), cv + math.sin(a) * (R + 1.6)
        pa = (cu + math.cos(a + 0.18) * (R - 0.3), cv + math.sin(a + 0.18) * (R - 0.3))
        pb = (cu + math.cos(a - 0.18) * (R - 0.3), cv + math.sin(a - 0.18) * (R - 0.3))
        if math.cos(a) < -0.8:
            continue
        spikes.append(tri(pa, pb, (tu, tv)))
    s.add(union(*spikes), sp, lambda u, v, x, y: 'base')
    s.add(tail_fork(cu - R + 0.6, cu - R - 3.0, 0.9, 2.6, notch=0.25), sp, lambda u, v, x, y: 'base')

    def sh(u, v, x, y):
        n = (v - cv) / R
        if n > 0.35:
            return 'dark'
        if n < -0.2:
            return 'belly'
        if noise(x, y, 3) < 0.18 and n > -0.2:
            return 'dark'
        return 'base'
    s.add(body_p, pal, sh)
    s.post.append(eye(cu + 3.2, 1.4, (250, 250, 250)))
    s.post.extend(post)
    return s


def flatfish(body, spot, belly=None, post=()):
    """Halibut viewed from above: oval disc with fringe fins, both eyes on top near head."""
    pal = ramp(body, belly)
    fp = ramp(adjust_hex(body, 0.8, 1.0))
    s = Sprite()
    s.add(ellipse(0.8, 0.0, 8.2, 5.8), fp, lambda u, v, x, y: 'base')  # fringe fins
    s.add(tail_fork(-6.0, -9.2, 1.2, 2.8, notch=0.15), fp, lambda u, v, x, y: 'base')
    s.add(ellipse(1.0, 0.0, 7.0, 4.3), pal,
          lambda u, v, x, y: spot if noise(x, y, 7) < 0.22 else ('light' if v > 1.5 and u > 0 else 'base'))
    s.post.append(eye(5.4, 1.6, (240, 230, 170)))
    s.post.append(eye(6.2, -0.4, (240, 230, 170)))
    s.post.extend(post)
    return s


def squid(body, arm=None, spots=None, big=False, post=()):
    pal = ramp(body)
    ap = ramp(arm or adjust_hex(body, 0.9, 1.0))
    s = Sprite()
    # arms trail toward tail-end (bottom-left), mantle toward head (top-right)
    arms = []
    for i, off in enumerate((-2.2, -1.1, 0.0, 1.1, 2.2)):
        pts = [(-1.0, off * 0.6), (-4.5, off * 0.9 + 0.4 * math.sin(i)), (-8.3 - (1.2 if i == 2 else 0) - (0.8 if big and i in (0, 4) else 0), off * 1.2)]
        arms.append(polyline(pts, lambda T: 0.9 - 0.45 * T))
    s.add(union(*arms), ap, lambda u, v, x, y: 'base' if (x + y) % 3 else 'dark')
    # fin at the mantle tip
    s.add(tri((8.8, 0), (5.0, 3.6), (5.0, -3.6)), pal, lambda u, v, x, y: 'dark')
    pred, geo = body_profile(-1.8, 10.8, 3.1, 3.1, peak=0.35, nose=0.6, tailw=2.6)
    s.add(pred, pal, body_shader(geo, pal, spots=spots))
    s.post.append(eye(-0.6, 1.3, (250, 250, 250)))
    s.post.extend(post)
    return s


def octopus(body, post=()):
    pal = ramp(body)
    s = Sprite(rot=0)  # upright: head on top
    arms = []
    # rot=0: u = x-8 (right), v = -(y-8) (up)
    for i, (sx, curl) in enumerate(((-4.5, -1), (-2.2, 1), (0.3, -1), (2.6, 1), (4.8, 1))):
        pts = [(sx * 0.5, -1.0), (sx * 0.9, -3.5), (sx * 1.2, -5.8), (sx * 1.2 + curl * 1.4, -7.0), (sx * 1.2 + curl * 2.0, -5.9)]
        arms.append(polyline(pts, lambda T: 1.25 - 0.8 * T))
    s.add(union(*arms), pal, lambda u, v, x, y: 'belly' if (x * 3 + y) % 4 == 0 else 'base')
    s.add(ellipse(0.0, 2.2, 5.2, 5.0), pal,
          lambda u, v, x, y: 'dark' if v > 4.0 or (u > 1.5 and v > 1.5 and noise(x, y, 5) < 0.3) else ('light' if u < -1.5 and v > 2 else 'base'))
    s.post.append(eye(-2.2, 0.8, (15, 15, 15), (255, 240, 180), rot=0))
    s.post.append(eye(2.0, 0.8, (15, 15, 15), (255, 240, 180), rot=0))
    s.post.extend(post)
    return s


def ray(body, belly=None, pattern=None, post=(), tail_col=None):
    """Top view diamond, head toward top-right."""
    pal = ramp(body, belly)
    tp = ramp(tail_col or adjust_hex(body, 0.75))
    s = Sprite()
    s.add(polyline([(-2.0, 0.0), (-6.0, 0.4), (-10.5, -0.6)], lambda T: 0.8 - 0.5 * T), tp, lambda u, v, x, y: 'base')

    def wing(u, v):
        # diamond: front at u=8, back at u=-3.5, wingtips at v=+-7.8 around u=2
        if u >= 2:
            return abs(v) <= (8.4 - u) * 1.22
        return abs(v) <= (u + 4.0) * 1.3 and u >= -4.0
    def sh(u, v, x, y):
        if pattern:
            r = pattern(u, v, x, y)
            if r is not None:
                return r
        if abs(v) > 5.2:
            return 'dark'
        if abs(v) < 1.6 and u > -1:
            return 'light'
        return 'base'
    s.add(wing, pal, sh)
    s.post.append(eye(6.2, 1.6, (15, 15, 15)))
    s.post.append(eye(6.2, -1.6, (15, 15, 15)))
    s.post.extend(post)
    return s


def whale(body, belly, fluke=None, pattern=None, post=(), mouth=True):
    pal = ramp(body, belly)
    fp = ramp(fluke or body)
    s = Sprite()
    # fluke: wide, shallow fork seen from the side-top
    s.add(tail_fork(-3.4, -8.6, 0.8, 3.9, notch=0.5), fp, lambda u, v, x, y: 'base')
    s.add(tri((-0.5, 4.2), (1.6, 4.2), (-1.2, 5.4)), fp, lambda u, v, x, y: 'dark')
    pred, geo = body_profile(-4.2, 10.4, 5.3, 4.5, peak=0.8, nose=4.5, tailw=0.9)

    def sh(u, v, x, y):
        t, ht, hb, c = geo(u)
        n = v / ht if v >= 0 else v / hb
        if pattern:
            r = pattern(u, v, x, y, n)
            if r is not None:
                return r
        if n < -0.3:
            # throat grooves
            return 'belly' if (x - y) % 3 else 'light'
        if n > 0.5:
            return 'dark'
        if 0.1 < n <= 0.5 and 0.4 < t < 0.85:
            return 'light'
        return 'base'
    s.add(pred, pal, sh)
    s.add(tri((4.6, -1.6), (1.2, -4.6), (2.8, -5.2)), fp, lambda u, v, x, y: 'dark')
    if mouth:
        s.post.append(lambda px, owner: [put(px, *local_to_px(u_, -1.0 - 0.15 * (10 - u_)), pal['out'], owner, only_filled=True) for u_ in (5.6, 6.6, 7.6, 8.6, 9.5)])
    s.post.append(eye(5.0, -0.2, (210, 214, 220)))
    s.post.extend(post)
    return s


def sunfish(body, belly, post=()):
    pal = ramp(body, belly)
    fp = ramp(adjust_hex(body, 0.85))
    s = Sprite()
    s.add(tri((-1.0, 3.5), (2.5, 3.5), (-1.8, 9.4)), fp, lambda u, v, x, y: 'base')
    s.add(tri((-1.0, -3.5), (2.5, -3.5), (-1.8, -9.4)), fp, lambda u, v, x, y: 'base')
    s.add(lambda u, v: -4.4 <= u <= -2.5 and abs(v) <= 4.8 - (abs(-3.4 - u)), fp, lambda u, v, x, y: 'dark')
    pred, geo = body_profile(-3.4, 8.8, 5.6, 5.6, peak=0.45, nose=1.6, tailw=4.5)
    s.add(pred, pal, body_shader(geo, pal, spots=lambda u, v, t, n, x, y: 'light' if noise(x, y, 11) < 0.12 else None))
    s.post.append(eye(5.5, 0.9, (240, 240, 240)))
    s.post.extend(post)
    return s


def tentacle(body, sucker, post=()):
    pal = ramp(body)
    s = Sprite()
    pts = []
    for i in range(30):
        t = i / 29
        a = 2.6 * math.pi * t ** 1.4
        r = 1.0 + 8.5 * (1 - t) ** 0.9
        pts.append((-r * math.cos(a) + 1.8, r * math.sin(a) * 0.95 - 0.6))
    pts = list(reversed(pts))  # thick base first
    al = along(pts)
    rad = lambda T: 2.5 - 2.1 * T ** 0.9

    def sh(u, v, x, y):
        T, side = al(u, v)
        n = side / max(rad(T), 0.01)
        if n < -0.25 and (x + y) % 2 == 0:
            return sucker
        return 'dark' if n > 0.45 else 'base'
    s.add(polyline(pts, rad), pal, sh)
    s.post.extend(post)
    return s


def boot(leather, sole, weed, post=()):
    """Upright boot (rot=0: u right, v up)."""
    pal = ramp(leather)
    so = ramp(sole)
    wd = ramp(weed)
    s = Sprite(rot=0)
    shaft = lambda u, v: -3.2 <= u <= 1.6 and -2.5 <= v <= 6.0
    foot = lambda u, v: -3.2 <= u <= 5.6 and -5.6 <= v <= -1.0 and not (u > 3.2 and v > -2.2 and (u - 3.2) + (v + 2.2) > 0.2)
    s.add(union(shaft, foot), pal, lambda u, v, x, y: 'dark' if u < -1.8 or noise(x, y, 2) < 0.1 else ('light' if -0.8 < u < 0.4 and v > -1 else 'base'))
    s.add(lambda u, v: -3.6 <= u <= 6.0 and -7.0 <= v <= -5.4, so, lambda u, v, x, y: 'base')
    s.add(lambda u, v: -3.6 <= u <= 2.0 and 5.2 <= v <= 6.6, pal, lambda u, v, x, y: 'dark')
    s.add(polyline([(1.4, 6.0), (2.8, 3.6), (2.0, 1.2), (3.2, -0.4)], 0.6), wd, lambda u, v, x, y: 'base')
    s.add(polyline([(-2.4, 6.2), (-4.6, 4.0), (-4.2, 1.6)], 0.55), wd, lambda u, v, x, y: 'light')
    s.post.extend(post)
    return s


def infinity(body, glow_col, post=()):
    pal = ramp(body)
    s = Sprite(rot=0)
    pts = []
    for i in range(61):
        t = i / 60 * 2 * math.pi
        d = 1 + math.sin(t) ** 2
        pts.append((7.3 * math.cos(t) / d, 7.3 * math.sin(t) * math.cos(t) / d * 1.5))
    al = along(pts)
    rad = lambda T: 0.75 + 0.6 * math.sin(T * math.pi) ** 0.6

    def sh(u, v, x, y):
        T, side = al(u, v)
        n = side / max(rad(T), 0.01)
        c = int(T * 6) % 3
        if n > 0.35:
            return 'dark'
        return ('base', 'light', 'belly')[c]
    s.add(polyline(pts, rad), pal, sh)
    hx, hy = pts[31]
    s.post.append(eye(hx + 0.6, hy + 0.3, (255, 255, 255), rot=0))
    s.post.extend(post)
    return s

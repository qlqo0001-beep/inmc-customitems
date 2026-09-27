"""16x16 diagonal pixel-art sprite engine.

Local coords: u along body (tail -> head, head at top-right), v dorsal (+ = up-left).
Each part is (kind, predicate(u, v) -> bool, palette key, shade fn or None).
"""
import colorsys
import math
from PIL import Image

N = 16
SS = 4
R2 = math.sqrt(2)


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def adjust(rgb, v=1.0, s=1.0, h=0.0):
    r, g, b = [c / 255 for c in rgb]
    hh, ss, vv = colorsys.rgb_to_hsv(r, g, b)
    hh = (hh + h) % 1.0
    ss = max(0, min(1, ss * s))
    vv = max(0, min(1, vv * v))
    r, g, b = colorsys.hsv_to_rgb(hh, ss, vv)
    return (round(r * 255), round(g * 255), round(b * 255))


def ramp(base, belly=None, top=None):
    """outline, dark, base, light, belly"""
    b = hexrgb(base)
    return {
        'out': adjust(b, 0.3, 1.2, -0.01),
        'dark': hexrgb(top) if top else adjust(b, 0.72, 1.08),
        'base': b,
        'light': adjust(b, 1.18, 0.8),
        'belly': hexrgb(belly) if belly else adjust(b, 1.3, 0.55),
    }


def to_local(x, y, rot=45.0):
    dx, dy = x - 8.0, y - 8.0
    a = math.radians(rot)
    # head direction: rot=45 -> (1,-1)/sqrt2 ; rot=0 -> (1,0)
    du = (math.cos(a), -math.sin(a))
    dv = (-math.sin(a), -math.cos(a))
    return dx * du[0] + dy * du[1], dx * dv[0] + dy * dv[1]


class Sprite:
    def __init__(self, rot=45.0):
        self.parts = []  # (pred, paletteDict, shader)
        self.rot = rot
        self.post = []   # callables(img_pixels, filled)

    def add(self, pred, pal, shader=None, outline=True):
        self.parts.append((pred, pal, shader, outline))
        return self

    def render(self):
        owner = [[None] * N for _ in range(N)]
        for y in range(N):
            for x in range(N):
                counts = {}
                for sy in range(SS):
                    for sx in range(SS):
                        px = x + (sx + 0.5) / SS
                        py = y + (sy + 0.5) / SS
                        u, v = to_local(px, py, self.rot)
                        top = None
                        for i, (pred, _, _, _) in enumerate(self.parts):
                            if pred(u, v):
                                top = i
                        if top is not None:
                            counts[top] = counts.get(top, 0) + 1
                total = sum(counts.values())
                if total >= SS * SS * 0.45:
                    owner[y][x] = max(counts.items(), key=lambda kv: (kv[1], kv[0]))[0]
        img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        px = img.load()
        for y in range(N):
            for x in range(N):
                i = owner[y][x]
                if i is None:
                    continue
                pred, pal, shader, outline = self.parts[i]
                u, v = to_local(x + 0.5, y + 0.5, self.rot)
                edge = False
                if outline:
                    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + ox, y + oy
                        if not (0 <= nx < N and 0 <= ny < N) or owner[ny][nx] is None:
                            edge = True
                if edge:
                    col = pal['out']
                else:
                    col = shader(u, v, x, y) if shader else pal['base']
                    if isinstance(col, str):
                        col = pal[col]
                px[x, y] = col + (255,) if len(col) == 3 else col
        for fn in self.post:
            fn(px, owner)
        return img


# ---------------------------------------------------------------- shape helpers

def body_profile(u0, u1, top, bot, peak=0.62, camber=0.0, nose=1.0, tailw=0.0):
    """Returns (pred, half-height fn). h(t) with t in [0,1] tail->nose."""
    def h(t, H):
        if t <= 0 or t >= 1:
            return 0.0
        if t < peak:
            s = t / peak
            base = math.sin(s * math.pi / 2) ** 0.9
            return tailw + (H - tailw) * base
        s = (t - peak) / (1 - peak)
        return H * max(0.0, 1 - s ** (1.6 / nose)) ** 0.55

    def geo(u):
        t = (u - u0) / (u1 - u0)
        return t, h(t, top), h(t, bot), camber * math.sin(t * math.pi)

    def pred(u, v):
        if u < u0 or u > u1:
            return False
        t, ht, hb, c = geo(u)
        vv = v - c
        return -hb <= vv <= ht

    return pred, geo


def body_shader(geo, pal, stripes=None, spots=None, belly_line=-0.25, top_line=0.35, highlight=True):
    def sh(u, v, x, y):
        t, ht, hb, c = geo(u)
        vv = v - c
        n = vv / ht if vv >= 0 and ht > 0 else (vv / hb if hb > 0 else 0)
        col = pal['base']
        if n > top_line:
            col = pal['dark']
        elif n < belly_line:
            col = pal['belly']
        elif highlight and 0.05 < n <= top_line and 0.35 < t < 0.8:
            col = pal['light']
        elif -0.2 < n <= 0.05 and (x * 2 + y) % 4 == 0:
            col = pal['light']  # 비늘 반짝임
        if stripes:
            r = stripes(u, v, t, n, x, y)
            if r is not None:
                col = pal[r] if isinstance(r, str) else r
        if spots:
            r = spots(u, v, t, n, x, y)
            if r is not None:
                col = pal[r] if isinstance(r, str) else r
        return col
    return sh


def tail_fork(ua, ub, w0, w1, notch=0.45, vc=0.0):
    """Forked tail from ua (peduncle, half-width w0) back to ub (<ua, end, half-width w1)."""
    def pred(u, v):
        if u > ua or u < ub:
            return False
        s = (ua - u) / (ua - ub)
        w = w0 + (w1 - w0) * s ** 0.55
        vv = abs(v - vc)
        if vv > w:
            return False
        # notch: shallow V cut at the end
        cut = (s - (1 - notch)) / notch
        if cut > 0 and vv < w1 * cut * 0.55:
            return False
        return True
    return pred


def tail_round(ua, ub, w0, w1, vc=0.0):
    def pred(u, v):
        if u > ua or u < ub:
            return False
        s = (ua - u) / (ua - ub)
        w = w0 + (w1 - w0) * math.sin(min(1, s * 1.3) * math.pi / 2)
        if s > 0.8:
            w *= math.sqrt(max(0, 1 - ((s - 0.8) / 0.2) ** 2))
        return abs(v - vc) <= w
    return pred


def tri(p1, p2, p3):
    def sign(a, b, c):
        return (a[0] - c[0]) * (b[1] - c[1]) - (b[0] - c[0]) * (a[1] - c[1])

    def pred(u, v):
        p = (u, v)
        d1, d2, d3 = sign(p, p1, p2), sign(p, p2, p3), sign(p, p3, p1)
        neg = d1 < 0 or d2 < 0 or d3 < 0
        pos = d1 > 0 or d2 > 0 or d3 > 0
        return not (neg and pos)
    return pred


def ellipse(cu, cv, a, b):
    return lambda u, v: ((u - cu) / a) ** 2 + ((v - cv) / b) ** 2 <= 1


def circle_xy(cx, cy, r):
    """In raw pixel coords (for non-rotated shapes)."""
    return cx, cy, r


def union(*ps):
    return lambda u, v: any(p(u, v) for p in ps)


def minus(a, b):
    return lambda u, v: a(u, v) and not b(u, v)


def capsule(p, q, r):
    def pred(u, v):
        ax, ay = p
        bx, by = q
        dx, dy = bx - ax, by - ay
        L = dx * dx + dy * dy
        t = 0 if L == 0 else max(0, min(1, ((u - ax) * dx + (v - ay) * dy) / L))
        cx, cy = ax + dx * t, ay + dy * t
        rr = r(t) if callable(r) else r
        return (u - cx) ** 2 + (v - cy) ** 2 <= rr * rr
    return pred


def polyline(points, r):
    """Tapered tube along points; r(t) with t in [0,1] along the whole path."""
    segs = []
    total = 0
    for a, b in zip(points, points[1:]):
        L = math.dist(a, b)
        segs.append((a, b, total, L))
        total += L

    def pred(u, v):
        for a, b, off, L in segs:
            dx, dy = b[0] - a[0], b[1] - a[1]
            t = 0 if L == 0 else max(0, min(1, ((u - a[0]) * dx + (v - a[1]) * dy) / (L * L)))
            cx, cy = a[0] + dx * t, a[1] + dy * t
            T = (off + t * L) / total
            rr = r(T) if callable(r) else r
            if (u - cx) ** 2 + (v - cy) ** 2 <= rr * rr:
                return True
        return False
    return pred


def along(points):
    """Returns fn(u,v)->(T, signed offset) nearest param along polyline (for shading)."""
    segs = []
    total = 0
    for a, b in zip(points, points[1:]):
        L = math.dist(a, b)
        segs.append((a, b, total, L))
        total += L

    def fn(u, v):
        best = None
        for a, b, off, L in segs:
            dx, dy = b[0] - a[0], b[1] - a[1]
            t = 0 if L == 0 else max(0, min(1, ((u - a[0]) * dx + (v - a[1]) * dy) / (L * L)))
            cx, cy = a[0] + dx * t, a[1] + dy * t
            d2 = (u - cx) ** 2 + (v - cy) ** 2
            if best is None or d2 < best[0]:
                nx, ny = (-dy / L, dx / L) if L else (0, 1)
                side = (u - cx) * nx + (v - cy) * ny
                best = (d2, (off + t * L) / total, side)
        return best[1], best[2]
    return fn


# ---------------------------------------------------------------- post effects

def put(px, x, y, col, owner=None, only_filled=False, only_empty=False):
    if not (0 <= x < N and 0 <= y < N):
        return
    if only_filled and owner is not None and owner[y][x] is None:
        return
    if only_empty and owner is not None and owner[y][x] is not None:
        return
    px[x, y] = tuple(col) + (255,) if len(col) == 3 else tuple(col)


def local_to_px(u, v, rot=45.0):
    a = math.radians(rot)
    du = (math.cos(a), -math.sin(a))
    dv = (-math.sin(a), -math.cos(a))
    x = 8 + u * du[0] + v * dv[0]
    y = 8 + u * du[1] + v * dv[1]
    return int(math.floor(x)), int(math.floor(y))


def eye(u, v, iris=(250, 214, 64), glint=None, rot=45.0, big=False, pupil=(16, 16, 20)):
    """Two-pixel eye: iris behind, pupil toward the nose (+u). A dark iris makes a plain dark eye."""
    def fn(px, owner):
        x, y = local_to_px(u, v, rot)
        fx, fy = local_to_px(u + 1.0, v, rot)
        if (fx, fy) == (x, y):
            fx = x + 1
        # 눈두덩 — 눈 뒤 한 칸을 어둡게 해 밝은 몸에서도 눈이 보이게
        bx, by = 2 * x - fx, 2 * y - fy
        if 0 <= bx < N and 0 <= by < N and px[bx, by][3] == 255:
            put(px, bx, by, adjust(px[bx, by][:3], 0.55))
        put(px, x, y, iris)
        put(px, fx, fy, pupil, owner, only_filled=True)
        if glint:
            put(px, x, y - 1, glint, owner, only_filled=True)
    return fn


def pixels(points, col, only_filled=False, only_empty=False):
    def fn(px, owner):
        for (x, y) in points:
            put(px, x, y, col, owner, only_filled, only_empty)
    return fn


def sparkle(points, col=(255, 255, 255), dim=None):
    """Plus-shaped sparkles on empty pixels."""
    def fn(px, owner):
        for (x, y) in points:
            put(px, x, y, col, owner, only_empty=True)
            if dim:
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    put(px, x + ox, y + oy, dim, owner, only_empty=True)
    return fn


def alpha_scale(a):
    def fn(px, owner):
        for y in range(N):
            for x in range(N):
                r, g, b, al = px[x, y]
                if al:
                    px[x, y] = (r, g, b, int(al * a))
    return fn


def glow(col, alpha=110):
    """Soft 1px halo on empty pixels touching the sprite."""
    def fn(px, owner):
        halo = []
        for y in range(N):
            for x in range(N):
                if px[x, y][3] != 0:
                    continue
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ox, y + oy
                    if 0 <= nx < N and 0 <= ny < N and px[nx, ny][3] == 255:
                        halo.append((x, y))
                        break
        for x, y in halo:
            px[x, y] = tuple(col) + (alpha,)
    return fn

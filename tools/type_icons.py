# 커스텀아이템 관리 화면의 종류 아이콘(16x16) — 도안을 글자로 적고 테두리는 자동으로 두른다.
import struct, sys, zlib, os

# 쓰는 법(저장소 뿌리에서): py -X utf8 tools/type_icons.py — 그림을 src/main/resources/pack/gui/type/ 에 쓰고,
# 10배 미리보기를 build/type_icons_preview.png 에 쓴다. 종류를 더하면 ICONS 와 ORDER 에 도안을 더한다(CLAUDE.md 규칙 90).
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "pack", "gui", "type") + os.sep
PREVIEW = os.path.join(HERE, "..", "build", "type_icons_preview.png")
OUTLINE = (0x24, 0x22, 0x2a, 255)


def hexc(s):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


ICONS = {}

# 1. 무기 — 칼(오른쪽 위가 끝)
ICONS["weapon"] = ({
    'w': '#ffffff', 'l': '#d9dde3', 'm': '#9aa3ad',
    'g': '#f2c14e', 'G': '#b4812a', 'b': '#8a5a2e', 'r': '#d6453d',
}, [
    "................",
    "..............w.",
    ".............wl.",
    "............wlm.",
    "...........wlm..",
    "..........wlm...",
    ".........wlm....",
    "........wlm.....",
    "...g...wlm......",
    "....g.wlm.......",
    ".....gGm........",
    "....bGgg........",
    "...bb..gG.......",
    "..bb....G.......",
    ".rr.............",
    ".r..............",
])

# 2. 방어구 — 방패(가운데 금 십자)
ICONS["armor"] = ({
    's': '#c9ced6', 'S': '#8c939e', 'b': '#3f6fc4', 'B': '#6f97e0', 'd': '#2b4f93', 'g': '#f2c14e', 'G': '#c4922f',
}, [
    "................",
    "...ssssssssss...",
    "..sBBbbbbbbbbS..",
    "..sBbbbggbbbdS..",
    "..sbbbbggbbbdS..",
    "..sbggggggggdS..",
    "..sbGGGggGGGdS..",
    "..sbbbbggbbbdS..",
    "...sbbbggbbdS...",
    "...sbbbggbbdS...",
    "....sbbggbdS....",
    ".....sbGGdS.....",
    "......sddS......",
    ".......SS.......",
    "................",
    "................",
])

# 3. 도구 — 곡괭이
ICONS["tool"] = ({
    'i': '#e3e7ec', 'I': '#aab2bc', 'D': '#737c87', 'b': '#9a6a3a', 'B': '#6b4524',
}, [
    "................",
    "....iiiiii......",
    "...iIIIIIIi.....",
    "..iID...DIIi....",
    "..iD.....DII....",
    "..........DIi...",
    ".........bDIi...",
    "........bB.DI...",
    ".......bB...i...",
    "......bB........",
    ".....bB.........",
    "....bB..........",
    "...bB...........",
    "..bB............",
    ".bB.............",
    "................",
])

# 4. 소모품 — 물약병
ICONS["consumable"] = ({
    'c': '#9b6b3f', 'C': '#6e4a2a', 'g': '#d8eef2', 'G': '#9fc3cc', 'w': '#ffffff',
    'r': '#e0344d', 'R': '#9e1f35', 'p': '#ff7a8c',
}, [
    "................",
    "......ccCC......",
    "......ccCC......",
    ".......gG.......",
    ".......gG.......",
    "......gggG......",
    "....ggggggGG....",
    "...gwpprrrrrG...",
    "..gwpprrrrrrrG..",
    "..gwprrrrrrrRG..",
    "..gprrrrrrrrRG..",
    "..grrrrrrrrRRG..",
    "...grrrrrrRRG...",
    "....GRRRRRRG....",
    ".....GGGGGG.....",
    "................",
])

# 5. 장신구 — 보석 반지
ICONS["accessory"] = ({
    'p': '#b36bff', 'P': '#7d3fd1', 'w': '#f3e3ff', 'y': '#f2c14e', 'Y': '#b4812a', 'h': '#fff1b8',
}, [
    "................",
    "......pppP......",
    ".....pwppPP.....",
    ".....ppppPP.....",
    "......pPPP......",
    "......yhyY......",
    "....yyhyyYYY....",
    "...yh......YY...",
    "..yh........YY..",
    "..yh........YY..",
    "..yy........YY..",
    "..yY........YY..",
    "...yY......YY...",
    "....yYYYYYYY....",
    "................",
    "................",
])

# 6. 부적 — 노란 종이 부적(붉은 글)
ICONS["talisman"] = ({
    'y': '#f7df6b', 'Y': '#d9b93e', 'r': '#d02a2a', 'R': '#9c1c1c',
}, [
    "................",
    "....yyyyyyyY....",
    "....yrrrrrrY....",
    "....yyyyyyyY....",
    "....yyyryyyY....",
    "....yrrrrryY....",
    "....yyyryyyY....",
    "....yyrrryyY....",
    "....yryryryY....",
    "....yyyryyyY....",
    "....yyryryyY....",
    "....yryyyryY....",
    "....yyyyyyyY....",
    "....yrrrrrrY....",
    "....YYYYYYYY....",
    "................",
])

# 7. 유물 — 받침 위의 빛나는 구슬
ICONS["relic"] = ({
    'c': '#5fe0e8', 'C': '#2aa7c4', 'D': '#1b6f8f', 'w': '#ffffff', 'o': '#f2c14e', 'O': '#b4812a', 'h': '#fff1b8',
}, [
    "................",
    "......cccc......",
    ".....cwwcccC....",
    "....cwwcccccC...",
    "....cwccccccC...",
    "....ccccccCCD...",
    ".....ccccCCD....",
    "......CCDD......",
    ".....ohhhooO....",
    "....ohoooooOO...",
    "......oooO......",
    ".......oO.......",
    "......ohoO......",
    "....ohhooooO....",
    "....OOOOOOOO....",
    "................",
])

# 8. 배낭 — 가죽 배낭(덮개·버클·주머니)
ICONS["backpack"] = ({
    'b': '#a8733f', 'B': '#6e4524', 'l': '#cf985b', 'y': '#f2c14e', 'd': '#8a5a2e',
}, [
    "................",
    "......BBBB......",
    ".....B....B.....",
    "....BBBBBBBB....",
    "...BllllllllB...",
    "...BllllllllB...",
    "...BlllyylllB...",
    "...BdddyydddB...",
    "...BbbbbbbbbB...",
    "...BbbbbbbbbB...",
    "...BbddddddbB...",
    "...BbdbbbbdbB...",
    "...BbddddddbB...",
    "...BbbbbbbbbB...",
    "....BBBBBBBB....",
    "................",
])

# 9. 재료 — 주괴
ICONS["material"] = ({
    't': '#f4f6f8', 'T': '#d3d8de', 'f': '#b7bec7', 'F': '#9ba3ad', 's': '#79828d',
}, [
    "................",
    "................",
    "................",
    "................",
    ".....ttttttttt..",
    "....tTTTTTTTTs..",
    "...tTTTTTTTTs...",
    "..fffffffffss...",
    "..fFFFFFFFFs....",
    "..fFFFFFFFFs....",
    "..fFFFFFFFFs....",
    "..sssssssss.....",
    "................",
    "................",
    "................",
    "................",
])

# 10. 보석 — 깎은 보석
ICONS["gem"] = ({
    'e': '#34d17a', 'E': '#8ff0b6', 'w': '#ffffff', 'D': '#1f9a56', 'x': '#15703d',
}, [
    "................",
    "................",
    "....eeeeeeee....",
    "...eEwEEeeeee...",
    "..eEwEEeeeeeDe..",
    ".eeeeeeeeeeeeDe.",
    ".xEEEEEeeeDDDDx.",
    "..xEEEEeeDDDDx..",
    "...xEEEeeDDDx...",
    "....xEEeDDDx....",
    ".....xEeDDx.....",
    "......xeDx......",
    ".......xx.......",
    "................",
    "................",
    "................",
])

# 11. 블록 — 잔디 블록(아이소메트릭)
ICONS["block"] = ({
    'T': '#7cc24f', 't': '#a4dd6c', 'g': '#5a9a37', 'L': '#9b6a3c', 'l': '#b98552', 'R': '#6c4526', 'r': '#5a381e',
}, [
    "................",
    ".......tT.......",
    ".....ttTTTTT....",
    "...ttTTTTTTTTT..",
    ".tTTTTTTTTTTTTT.",
    ".ggTTTTTTTTTTgg.",
    ".LlggTTTTTTggrR.",
    ".LLllggTTggrrRR.",
    ".LLLlllggrrrRRR.",
    ".LlLLLLLRrRRRRR.",
    ".LLLLlLLRRRRrRR.",
    ".LLlLLLLRRrRRRR.",
    "..LLLLLLRRRRRR..",
    "....LLlLRrRR....",
    "......LLRR......",
    "................",
])

# 12. 기타 — 반짝임(✦)
ICONS["misc"] = ({
    'y': '#ffd94d', 'Y': '#e0a92a', 'w': '#ffffff', 'p': '#ffe98f',
}, [
    "................",
    ".......y........",
    ".......y........",
    "......yYy.......",
    "......yYy....p..",
    ".....yYwYy..pwp.",
    ".yyyyYwwwYyyyp..",
    "..yyYwwwwwYyy...",
    ".yyyyYwwwYyyyy..",
    ".....yYwYy......",
    "......yYy.......",
    "..p...yYy.......",
    ".pwp...y........",
    "..p....y........",
    "................",
    "................",
])

def drawn(palette, cells):
    """(x, y) → 글자 표에서 16줄 도안을 만든다."""
    g = [['.'] * 16 for _ in range(16)]
    for (x, y), ch in cells.items():
        if 0 <= x < 16 and 0 <= y < 16:
            g[y][x] = ch
    return palette, [''.join(r) for r in g]


def sword():
    cells = {}
    # 날 — 오른쪽 위 끝에서 왼쪽 아래로, 한 줄에 밝은 날·가운데·그늘 세 칸.
    cells[(14, 1)] = 'w'
    cells[(13, 2)] = 'w'; cells[(14, 2)] = 'l'
    for r in range(3, 10):
        cells[(15 - r, r)] = 'w'; cells[(16 - r, r)] = 'l'; cells[(17 - r, r)] = 'm'
    # 코등이 — 날과 직각(왼쪽 위 → 오른쪽 아래), 두 칸 두께.
    for i, (x, y) in enumerate([(3, 8), (4, 9), (5, 10), (6, 11), (7, 12)]):
        cells[(x, y)] = 'g'
        cells[(x + 1, y)] = 'G' if (x + 1, y) not in cells else cells[(x + 1, y)]
    # 손잡이와 폼멜.
    for x, y in [(4, 12), (3, 13)]:
        cells[(x, y)] = 'b'
    cells[(2, 14)] = 'g'; cells[(1, 14)] = 'G'; cells[(2, 15)] = 'G'
    return drawn({'w': '#ffffff', 'l': '#d9dde3', 'm': '#9aa3ad', 'g': '#f2c14e', 'G': '#b4812a', 'b': '#8a5a2e'}, cells)


def pickaxe():
    import math
    cells = {}
    cx, cy = 5.0, 11.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dy, dx))  # 화면 좌표 — 오른쪽 위가 -45
            if -103 <= ang <= 13 and 7.4 <= r <= 9.6:
                cells[(x, y)] = 'i' if r >= 8.9 else ('I' if r >= 8.1 else 'D')
    # 자루 — x + y = 15 줄, 한쪽에 그늘.
    for t in range(1, 10):
        x, y = t, 15 - t
        if (x, y) not in cells:
            cells[(x, y)] = 'b'
        if t <= 8 and (x + 1, y) not in cells:
            cells[(x + 1, y)] = 'B'
    return drawn({'i': '#eef1f4', 'I': '#b3bbc5', 'D': '#6f7883', 'b': '#9a6a3a', 'B': '#6b4524'}, cells)


ICONS["weapon"] = sword()
ICONS["tool"] = pickaxe()

ORDER = ["weapon", "armor", "tool", "consumable", "accessory", "talisman", "relic", "backpack", "material", "gem", "block", "misc"]


def grid(name):
    palette, rows = ICONS[name]
    assert len(rows) == 16, (name, len(rows))
    for i, r in enumerate(rows):
        assert len(r) == 16, (name, i, len(r), r)
        for ch in r:
            assert ch == '.' or ch in palette, (name, i, ch)
    px = [[None if ch == '.' else hexc(palette[ch]) for ch in r] for r in rows]
    # 테두리 — 칠한 칸의 네 이웃 중 빈 칸.
    out = [row[:] for row in px]
    for y in range(16):
        for x in range(16):
            if px[y][x] is not None:
                continue
            if any(0 <= y + dy < 16 and 0 <= x + dx < 16 and px[y + dy][x + dx] is not None for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = OUTLINE
    return [[c if c is not None else (0, 0, 0, 0) for c in row] for row in out]


def png(w, h, rows):
    raw = b''.join(b'\x00' + bytes([v for px in row for v in px]) for row in rows)

    def chunk(t, data):
        return struct.pack('>I', len(data)) + t + data + struct.pack('>I', zlib.crc32(t + data) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    grids = {}
    for name in ORDER:
        g = grid(name)
        grids[name] = g
        with open(OUT + name + ".png", "wb") as f:
            f.write(png(16, 16, g))
    # 미리보기: 4x3, 한 칸 16px x 10배, 인벤토리 칸 색 위에.
    scale, pad, cols = 10, 16, 4
    cell = 16 * scale + pad * 2
    W, H = cols * cell, 3 * cell
    bg, slot = (0xc6, 0xc6, 0xc6, 255), (0x8b, 0x8b, 0x8b, 255)
    img = [[bg for _ in range(W)] for _ in range(H)]
    for i, name in enumerate(ORDER):
        ox, oy = (i % cols) * cell + pad, (i // cols) * cell + pad
        for y in range(16 * scale):
            for x in range(16 * scale):
                c = grids[name][y // scale][x // scale]
                img[oy + y][ox + x] = c if c[3] else slot
    with open(PREVIEW, "wb") as f:
        f.write(png(W, H, img))
    print("ok", len(ORDER))


main()

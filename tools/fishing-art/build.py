"""낚시 아이템 그림(16x16)을 다시 그린다.

    pip install pillow
    python3 tools/fishing-art/build.py

결과는 src/main/resources/role-appearance/fishing/ 아래 fish · fillet · bait · rod 에 쓰인다.
항목·등급 목록은 src/main/resources/role-appearance.yml (그림 경로가 여기와 같아야 한다 — RoleAppearanceTest).
"""
import os
from extras import BAITS, RODS, fillet
from species import SPECIES

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'role-appearance', 'fishing')


def save(img, *path):
    target = os.path.join(OUT, *path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    img.save(target)


for fid, build in SPECIES.items():
    save(build().render(), 'fish', fid + '.png')
for grade in 'fedcbas':
    for trophy in ('none', 'trophy', 'rare'):
        save(fillet(grade, trophy).render(), 'fillet', f'{grade}_{trophy}.png')
for bid, build in BAITS.items():
    save(build().render(), 'bait', bid + '.png')
for rid, build in RODS.items():
    save(build(False), 'rod', rid + '.png')
    save(build(True), 'rod', rid + '_cast.png')
print('done:', OUT)

#!/usr/bin/env python3
"""Original pixel art: an inked railway with a station crossing a river on a folded paper map."""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
LAND = ("#7fb238", "#6da136", "#89bd3f")
WATER = ("#4040ff", "#3838e0")
INK = "#24160f"
PAPER = "#ddcfa2"
BORDER = "#8a6f47"
size = 16
image = Image.new("RGB", (size, size))


def rgb(colour):
    return tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5))


for y in range(size):
    for x in range(size):
        river = 10 + (y // 5) - (1 if y > 11 else 0)
        if x in (0, size - 1) or y in (0, size - 1):
            colour = BORDER
        elif river <= x <= river + 2:
            colour = WATER[(x + y) % 2]
        else:
            colour = LAND[(x * 7 + y * 13 + (x * y) % 5) % 3]
        image.putpixel((x, y), rgb(colour))
# The line: diagonal from the bottom-left, then east across the river.
line = [(2, 13), (3, 12), (4, 11), (5, 10), (6, 9), (7, 8)] + [(x, 7) for x in range(8, 15)]
for index, (x, y) in enumerate(line):
    image.putpixel((x, y), rgb(INK))
    bridge = 10 <= x <= 13 and y == 7
    if bridge:
        image.putpixel((x, y - 1), rgb(PAPER))
        image.putpixel((x, y + 1), rgb(PAPER))
    elif index % 3 == 1:
        ticks = ((x - 1, y - 1), (x + 1, y + 1)) if y > 7 else ((x, y - 1), (x, y + 1))
        for tick in ticks:
            image.putpixel(tick, rgb(INK))
# Station: ink square with a paper centre.
for dx in (-1, 0, 1):
    for dy in (-1, 0, 1):
        image.putpixel((5 + dx, 10 + dy), rgb(PAPER if dx == 0 and dy == 0 else INK))
for path, scale in ((root / "src/main/resources/assets/immersive_train_lines/icon.png", 128), (root / "docs/icon.png", 512)):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.resize((scale, scale), Image.Resampling.NEAREST).save(path)

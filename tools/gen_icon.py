#!/usr/bin/env python3
"""Generate the Matrix-style launcher icon.

Writes, for each variant, an Android VectorDrawable (adaptive-icon foreground, 108x108 viewport)
and an SVG for previewing. The rain is made of the same 5x7 pixel digits as the app's clock.

Run:  python3 tools/gen_icon.py            -> tools/out/icon_<variant>.{xml,svg}
      python3 tools/gen_icon.py --install face   -> also copies that variant into res/drawable
"""
import os
import random
import sys

W = 108.0
CX = CY = 54.0
BG = "#030806"          # adaptive background layer colour (set in colors.xml)
GREEN = "#39FF14"
HEAD = "#D8FFE0"

PATTERNS = {
    "0": [" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "],
    "1": ["  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "],
    "2": [" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"],
    "3": ["#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### "],
    "4": ["   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "],
    "5": ["#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "],
    "6": ["  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### "],
    "7": ["#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   "],
    "8": [" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "],
    "9": [" ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  "],
    ":": [" ", " ", "#", " ", "#", " ", " "],
}


class Shapes:
    """Collects primitives once, emits them as VectorDrawable paths or SVG elements."""

    def __init__(self):
        self.items = []

    def rect(self, x, y, w, h, color, alpha=1.0, r=0.0):
        self.items.append(("rect", dict(x=x, y=y, w=w, h=h, r=r, fill=color, alpha=alpha)))

    def circle(self, cx, cy, r, fill=None, stroke=None, width=0.0, alpha=1.0):
        self.items.append(("circle", dict(cx=cx, cy=cy, r=r, fill=fill, stroke=stroke, width=width, alpha=alpha)))

    def line(self, x1, y1, x2, y2, color, width, alpha=1.0):
        self.items.append(("line", dict(x1=x1, y1=y1, x2=x2, y2=y2, stroke=color, width=width, alpha=alpha)))

    # ---- emitters -------------------------------------------------------------------------

    @staticmethod
    def _rect_path(d):
        x, y, w, h, r = d["x"], d["y"], d["w"], d["h"], d["r"]
        if r <= 0:
            return f"M{x:.2f},{y:.2f}h{w:.2f}v{h:.2f}h{-w:.2f}z"
        return (f"M{x + r:.2f},{y:.2f}h{w - 2 * r:.2f}a{r},{r} 0 0 1 {r},{r}v{h - 2 * r:.2f}"
                f"a{r},{r} 0 0 1 {-r},{r}h{-(w - 2 * r):.2f}a{r},{r} 0 0 1 {-r},{-r}v{-(h - 2 * r):.2f}"
                f"a{r},{r} 0 0 1 {r},{-r}z")

    @staticmethod
    def _circle_path(d):
        cx, cy, r = d["cx"], d["cy"], d["r"]
        return f"M{cx - r:.2f},{cy:.2f}a{r},{r} 0 1 0 {2 * r},0a{r},{r} 0 1 0 {-2 * r},0z"

    def merged(self):
        """Consecutive rects with the same fill and alpha collapse into one item (one path)."""
        out, groups = [], {}
        for kind, d in self.items:
            if kind == "rect":
                key = (d["fill"], round(d["alpha"], 2))
                if key in groups:
                    groups[key]["paths"].append(self._rect_path(d))
                    continue
                g = dict(fill=d["fill"], alpha=d["alpha"], paths=[self._rect_path(d)])
                groups[key] = g
                out.append(("rects", g))
            else:
                out.append((kind, d))
        return out

    def vector_xml(self):
        out = ['<?xml version="1.0" encoding="utf-8"?>',
               '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
               '    android:width="108dp"', '    android:height="108dp"',
               '    android:viewportWidth="108"', '    android:viewportHeight="108">']
        for kind, d in self.merged():
            attrs = []
            if kind == "rects":
                attrs += [f'android:pathData="{"".join(d["paths"])}"', f'android:fillColor="{d["fill"]}"']
            elif kind == "circle":
                attrs += [f'android:pathData="{self._circle_path(d)}"']
                if d["fill"]:
                    attrs.append(f'android:fillColor="{d["fill"]}"')
                if d["stroke"]:
                    attrs += [f'android:strokeColor="{d["stroke"]}"', f'android:strokeWidth="{d["width"]}"']
            elif kind == "line":
                attrs += [f'android:pathData="M{d["x1"]:.2f},{d["y1"]:.2f}L{d["x2"]:.2f},{d["y2"]:.2f}"',
                          f'android:strokeColor="{d["stroke"]}"', f'android:strokeWidth="{d["width"]}"',
                          'android:strokeLineCap="round"']
            if d["alpha"] < 1.0:
                key = "android:strokeAlpha" if (kind == "line" or (kind == "circle" and not d["fill"])) else "android:fillAlpha"
                attrs.append(f'{key}="{d["alpha"]:.2f}"')
            out.append("    <path\n        " + "\n        ".join(attrs) + " />")
        out.append("</vector>")
        return "\n".join(out) + "\n"

    def svg(self):
        out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">',
               f'<rect width="108" height="108" fill="{BG}"/>']
        for kind, d in self.merged():
            a = f' opacity="{d["alpha"]:.2f}"' if d["alpha"] < 1.0 else ""
            if kind == "rects":
                out.append(f'<path d="{"".join(d["paths"])}" fill="{d["fill"]}"{a}/>')
            elif kind == "circle":
                fill = d["fill"] or "none"
                stroke = f' stroke="{d["stroke"]}" stroke-width="{d["width"]}"' if d["stroke"] else ""
                out.append(f'<circle cx="{d["cx"]}" cy="{d["cy"]}" r="{d["r"]}" fill="{fill}"{stroke}{a}/>')
            elif kind == "line":
                out.append(f'<line x1="{d["x1"]:.2f}" y1="{d["y1"]:.2f}" x2="{d["x2"]:.2f}" y2="{d["y2"]:.2f}" '
                           f'stroke="{d["stroke"]}" stroke-width="{d["width"]}" stroke-linecap="round"{a}/>')
        out.append("</svg>")
        return "\n".join(out) + "\n"


def glyph(s, ch, x, y, cell, color, alpha):
    """Draw one 5x7 pixel glyph with its top-left at (x, y)."""
    gap = cell * 0.28
    pitch = cell + gap
    for r, line in enumerate(PATTERNS[ch]):
        for c, px in enumerate(line):
            if px == "#":
                s.rect(x + c * pitch, y + r * pitch, cell, cell, color, alpha, r=cell * 0.18)


def rain(s, rng, cell=1.15, avoid=None):
    """Columns of falling digits: a bright head and a fading green trail."""
    gap = cell * 0.28
    gw = 5 * cell + 4 * gap
    gh = 7 * cell + 6 * gap
    col_pitch = gw + 3.2
    row_pitch = gh + 2.4
    x = 2.0
    while x + gw < W - 1:
        head_row = rng.randint(1, 9)
        trail = rng.randint(3, 7)
        for k in range(trail + 1):
            row = head_row - k
            y = row * row_pitch - rng.uniform(0, 4)
            if y + gh < 0 or y > W:
                continue
            if avoid and (x + gw / 2 - CX) ** 2 + (y + gh / 2 - CY) ** 2 < avoid ** 2:
                continue
            ch = rng.choice("0123456789")
            if k == 0:
                glyph(s, ch, x, y, cell, HEAD, 1.0)
            else:
                glyph(s, ch, x, y, cell, GREEN, max(0.12, 0.85 - k * 0.13))
        x += col_pitch


def variant_face():
    """Rain behind a glowing analog clock face reading 10:10."""
    s = Shapes()
    rain(s, random.Random(7), cell=1.15)
    r = 25.0
    s.circle(CX, CY, r + 4, fill="#000000", alpha=0.72)
    s.circle(CX, CY, r, stroke=GREEN, width=7.0, alpha=0.22)   # glow
    s.circle(CX, CY, r, stroke=GREEN, width=2.6)
    import math
    for deg in (0, 90, 180, 270):
        a = math.radians(deg)
        s.line(CX + (r - 6) * math.sin(a), CY - (r - 6) * math.cos(a),
               CX + (r - 2.5) * math.sin(a), CY - (r - 2.5) * math.cos(a), GREEN, 2.2)
    for deg, length, width in ((300, 12.0, 3.2), (60, 17.0, 2.6)):
        a = math.radians(deg)
        s.line(CX, CY, CX + length * math.sin(a), CY - length * math.cos(a), HEAD, width)
    s.circle(CX, CY, 2.6, fill=HEAD)
    return s


def variant_digits():
    """Rain with a big bright pixel time in the middle."""
    s = Shapes()
    rain(s, random.Random(11), cell=1.15, avoid=30)
    cell = 3.4
    gap = cell * 0.28
    pitch = cell + gap
    text = "10:10"
    cols = sum(len(PATTERNS[c][0]) for c in text) + (len(text) - 1)
    total_w = cols * pitch - gap
    total_h = 7 * pitch - gap
    x0, y0 = CX - total_w / 2, CY - total_h / 2
    s.rect(x0 - 5, y0 - 5, total_w + 10, total_h + 10, "#000000", 0.72, r=4)
    x = x0
    for ch in text:
        glyph(s, ch, x, y0, cell, GREEN, 1.0)
        x += (len(PATTERNS[ch][0]) + 1) * pitch
    return s


VARIANTS = {"face": variant_face, "digits": variant_digits}


def main():
    root = os.path.join(os.path.dirname(__file__), "..")
    out_dir = os.path.join(root, "tools", "out")
    os.makedirs(out_dir, exist_ok=True)
    for name, fn in VARIANTS.items():
        s = fn()
        with open(os.path.join(out_dir, f"icon_{name}.xml"), "w") as f:
            f.write(s.vector_xml())
        with open(os.path.join(out_dir, f"icon_{name}.svg"), "w") as f:
            f.write(s.svg())
        print("wrote", name, len(s.items), "shapes")

    if len(sys.argv) >= 3 and sys.argv[1] == "--install":
        name = sys.argv[2]
        src = os.path.join(out_dir, f"icon_{name}.xml")
        dst = os.path.join(root, "app", "src", "main", "res", "drawable", "ic_launcher_foreground.xml")
        with open(src) as f, open(dst, "w") as g:
            g.write(f.read())
        print("installed", name, "->", os.path.normpath(dst))


if __name__ == "__main__":
    main()

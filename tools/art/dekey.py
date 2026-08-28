"""Strip the painted transparency checkerboard from a portrait and emit a real alpha channel.

The generator answers "transparent background" with an opaque RGB image that has a neutral
grey checkerboard painted into it, at two levels that differ per image (measured, never
assumed).  An earlier version keyed by colour proximity to those two levels; that punched
holes through cigar smoke, because the smoke's own pixels land on exactly the same values
(205/255 on the Appraiser and the Auditor).  No colour rule can separate them.

Topology can.  The checkerboard and the smoke both touch the image border; the subject's
white shirt does not.  So a bright neutral pixel is background when it is REACHABLE from
the border through other bright neutrals, and foreground when the figure encloses it.
"""
import os
import sys
from collections import Counter, deque

from PIL import Image, ImageChops, ImageFilter


def checker_levels(im):
    """The two neutral levels painted along the border, low first."""
    w, h = im.size
    px = im.load()
    c = Counter()
    for x in range(0, w, 3):
        for y in (1, 2, 3, h - 2, h - 3, h - 4):
            v = px[x, y]
            if max(v) - min(v) <= 4:
                c[v[0]] += 1
    return sorted(k for k, _ in c.most_common(4))


def _flood(mask, size):
    """Every mask pixel reachable from the image border, 4-connected."""
    mp = mask.load()
    out = Image.new("L", (size, size), 0)
    op = out.load()
    q = deque()
    edge = [(x, y) for x in range(size) for y in (0, size - 1)]
    edge += [(x, y) for y in range(size) for x in (0, size - 1)]
    for x, y in edge:
        if mp[x, y] and not op[x, y]:
            op[x, y] = 255
            q.append((x, y))
    while q:
        x, y = q.popleft()
        for nx, ny in ((x+1, y), (x-1, y), (x, y+1), (x, y-1)):
            if 0 <= nx < size and 0 <= ny < size and mp[nx, ny] and not op[nx, ny]:
                op[nx, ny] = 255
                q.append((nx, ny))
    return out


def _largest_component(alpha, size):
    """The figure is one solid mass; anything opaque but detached from it is a remnant."""
    ap = alpha.load()
    seen = [[0] * size for _ in range(size)]
    best = []
    for sy in range(size):
        for sx in range(size):
            if ap[sx, sy] > 127 and not seen[sy][sx]:
                comp, q = [], deque([(sx, sy)])
                seen[sy][sx] = 1
                while q:
                    x, y = q.popleft()
                    comp.append((x, y))
                    for nx, ny in ((x+1, y), (x-1, y), (x, y+1), (x, y-1)):
                        if 0 <= nx < size and 0 <= ny < size and ap[nx, ny] > 127 and not seen[ny][nx]:
                            seen[ny][nx] = 1
                            q.append((nx, ny))
                if len(comp) > len(best):
                    best = comp
    keep = Image.new("L", (size, size), 0)
    kp = keep.load()
    for x, y in best:
        kp[x, y] = 255
    return keep, len(best)


# Board trapped inside a cigar-smoke wisp: the wisp's own outline encloses it, so the
# flood cannot reach it, and no colour rule can find it either -- this art is greyscale,
# so "neutral" is true across every face.  These two blobs sit in empty space beside the
# figure, measured off a coordinate grid, and are erased outright.  The clean smoke that
# rises from the cigar is below them and is left alone.
ERASE = {
    "enemy_appraiser": (312, 18, 420, 118),
    "enemy_auditor": (296, 118, 400, 200),
}


def key_portrait(src, out, size=512, spread=10, slack=12):
    im = Image.open(src).convert("RGB")
    levels = checker_levels(im)
    floor = min(levels) - slack
    r, g, b = im.split()
    mx = ImageChops.lighter(ImageChops.lighter(r, g), b)
    mn = ImageChops.darker(ImageChops.darker(r, g), b)
    flat = ImageChops.subtract(mx, mn).point(lambda v: 255 if v <= spread else 0)
    bright = im.convert("L").point(lambda v: 255 if v >= floor else 0)
    cand = ImageChops.multiply(flat, bright).resize((size, size), Image.BOX) \
                                            .point(lambda v: 255 if v > 150 else 0)
    alpha = _flood(cand, size).point(lambda v: 0 if v else 255).filter(ImageFilter.MinFilter(3))
    keep, n = _largest_component(alpha, size)
    box = ERASE.get(os.path.basename(src)[:-4])
    if box:
        alpha.paste(0, box)
    alpha = ImageChops.multiply(alpha, keep).filter(ImageFilter.GaussianBlur(0.6))
    rgb = im.resize((size, size), Image.LANCZOS)
    rgb.putalpha(alpha)
    rgb.save(out)
    print("%-13s floor=%3d opaque=%4.1f%%" % (os.path.basename(src)[6:-4], floor, 100 * n / (size * size)))


if __name__ == "__main__":
    key_portrait(sys.argv[1], sys.argv[2])

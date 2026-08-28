"""Cover-crop a generated still to the 1280x720 design space and flatten it.

Opaque on purpose: an alpha channel here is a bug, not a feature -- drawBackground never
blends these, and a stray channel just costs texture memory.  Unlike every other asset in
this project these need no keying, because they are full-bleed and were never asked for a
transparent background.
"""
import sys

from PIL import Image

TW, TH = 1280, 720


def frame(src, out):
    im = Image.open(src).convert("RGB")
    w, h = im.size
    scale = max(TW / w, TH / h)
    im = im.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
    x, y = (im.width - TW) // 2, (im.height - TH) // 2
    im.crop((x, y, x + TW, y + TH)).save(out)


if __name__ == "__main__":
    frame(sys.argv[1], sys.argv[2])

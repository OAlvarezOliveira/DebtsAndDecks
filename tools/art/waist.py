"""Re-frame a full-body portrait to waist-up.

CombatRenderer fits the art to w=180 and the art is square, so a full-body 512 renders
as a 180px figure whose head is about 22px: a smudge.  Cropping to a square whose side
is the figure's OWN width is self-tuning -- a narrow standing figure yields head-to-hip,
a broad one yields more torso -- and in both cases the subject fills the frame.

The rule reads the alpha bounding box as "the body", so it breaks on a pose where a prop
is wider than the person.  Those get an explicit override rather than a fudged constant,
so the next person can see which assets are exceptions and why.
"""
import sys
from PIL import Image

# side (px in the 512 source) and horizontal centre, for figures whose bbox is not the body
OVERRIDE = {
    # Seated in an armchair: the chair widens the bbox by ~100px, so the rule barely crops
    # and leaves him small and off-centre.  Framed on the head band instead, which also
    # drops most of the chair -- the prompt asked for no furniture in the first place.
    "underwriter": (300, None),
}


def head_centre(alpha, bbox, frac=0.30):
    ap, lo, hi = alpha.load(), 10**9, -1
    for y in range(bbox[1], bbox[1] + int((bbox[3]-bbox[1]) * frac)):
        for x in range(bbox[0], bbox[2]):
            if ap[x, y] > 40:
                lo = min(lo, x); break
        for x in range(bbox[2]-1, bbox[0]-1, -1):
            if ap[x, y] > 40:
                hi = max(hi, x); break
    return (lo + hi) // 2


def waist_up(path, out, key, margin=0.06):
    im = Image.open(path).convert("RGBA")
    a = im.getchannel("A")
    b = a.getbbox()
    fw, fh = b[2]-b[0], b[3]-b[1]
    if key in OVERRIDE:
        side, cx = OVERRIDE[key]
        cx = cx if cx is not None else head_centre(a, b)
        top = b[1] - int(side * margin)
    else:
        side = int(fw * (1 + 2*margin))
        cx, top = (b[0] + b[2]) // 2, b[1] - int(fw * margin)
    left = cx - side // 2
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(im.crop((left, top, left+side, top+side)), (0, 0))
    canvas.resize((512, 512), Image.LANCZOS).save(out)
    print("%-13s side=%3d (%d%% of the figure) centre=%d%s"
          % (key, side, round(100*side/fh), cx, "  [override]" if key in OVERRIDE else ""))


if __name__ == "__main__":
    waist_up(sys.argv[1], sys.argv[2], sys.argv[3])

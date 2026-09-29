#!/usr/bin/env python3
"""Generates the Wood mat's Hardwood texture (composeResources/drawable/mat_hardwood.webp): flat-sawn
oak floorboards. Each board's grain is its log's growth rings sliced by the board face - the pith
dipping towards the face and away along the board opens them into long cathedral arches - with the
rings pushed aside round knots, which have their own tight rings, dark ragged edges and sometimes a
check; oak's open pores strung along the grain; a tone of its own per board; end joints staggered
between rows; and long grooves between boards that vary in width and depth.

Needs numpy, scipy and pillow. Deterministic for a given seed; the shipped texture is seed 3:
    python3 scripts/art/generate_hardwood_mat.py 3 mat_hardwood.png
    magick mat_hardwood.png -quality 88 -define webp:method=6 \
        app/shared/src/commonMain/composeResources/drawable/mat_hardwood.webp
"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage

W, H = 1080, 640
SEED = int(sys.argv[1]) if len(sys.argv) > 1 else 3
OUT = sys.argv[2] if len(sys.argv) > 2 else "mat_hardwood.png"
BOARDS = 5
rng = np.random.default_rng(SEED)


def value_noise(shape, cells_x, cells_y):
    h, w = shape
    grid = rng.random((cells_y + 4, cells_x + 4)).astype(np.float32)
    big = ndimage.zoom(grid, ((h + 4 * h / cells_y) / grid.shape[0], (w + 4 * w / cells_x) / grid.shape[1]), order=3)
    oy, ox = int(2 * h / cells_y), int(2 * w / cells_x)
    return big[oy:oy + h, ox:ox + w]


def fbm(shape, cx, cy, octaves, gain=0.5):
    total = np.zeros(shape, np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        total += amp * value_noise(shape, max(1, int(cx * 2 ** o)), max(1, int(cy * 2 ** o)))
        norm += amp
        amp *= gain
    return total / norm


def board(w, h, tone):
    """One board, w x h, grain running along x. Returns RGB float array and a knot-darkness mask."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    shape = (h, w)
    # The log's pith runs along x, somewhere off to one side and below the face; its depth wanders
    # along the board, so the rings cut by the face bunch and spread into arches.
    pith_y = rng.uniform(-0.6, 1.6) * h
    # Along the board the pith dips towards the face and away again, over a board-length or so:
    # where it comes close, the rings open into the long arches of flat-sawn oak.
    cycles = rng.uniform(0.35, 0.9)
    depth = h * (0.08 + 0.32 * (0.5 + 0.5 * np.sin(xx / w * cycles * 2 * np.pi + rng.uniform(0, 6.28))))
    depth += (fbm(shape, 2, 1, 2) - 0.5) * h * 0.3
    lateral = (yy - pith_y) * 1.0 + (fbm(shape, 3, 2, 3) - 0.5) * h * 0.35

    # Knots: the rings are pushed aside round each one.
    knots = []
    for _ in range(rng.choice([0, 1, 2], p=[0.45, 0.4, 0.15])):
        kx = rng.uniform(0.08, 0.92) * w
        ky = rng.uniform(0.2, 0.8) * h
        kr = rng.uniform(0.04, 0.1) * h
        knots.append((kx, ky, kr))
    for kx, ky, kr in knots:
        dx = (xx - kx) / (kr * 5.0)
        dy = (yy - ky) / (kr * 1.8)
        infl = np.exp(-(dx * dx + dy * dy))
        push = np.sign(yy - ky + 1e-3) * infl * kr * 3.2
        lateral += push

    r = np.sqrt(lateral ** 2 + depth ** 2)
    spacing = rng.uniform(5.5, 8.0)
    ring = r / spacing + (fbm(shape, 4, 5, 3) - 0.5) * 0.9
    phase = ring - np.floor(ring)
    # Earlywood light, latewood a sharp dark line at the end of each year.
    late = np.clip((phase - 0.72) / 0.28, 0, 1) ** 2.2
    late = np.maximum(late, np.clip(1 - phase / 0.08, 0, 1) * 0.6)
    # Year lines come and go in strength along their length, rather than being drawn with a pen.
    late *= np.clip(0.35 + 0.9 * fbm(shape, 16, 4, 2), 0, 1)

    # Around each knot: tight concentric rings of its own, and a dark core with a darker rim.
    knot_mask = np.zeros(shape, np.float32)
    for kx, ky, kr in knots:
        ex = (xx - kx) / 2.1
        ey = (yy - ky)
        kd = np.sqrt(ex * ex + ey * ey) + (fbm(shape, 12, 4, 2) - 0.5) * kr * 0.5
        near = np.clip(1 - (kd - kr) / (kr * 2.2), 0, 1)
        kphase = (kd / max(2.2, kr * 0.28)) % 1.0
        kring = np.clip((kphase - 0.65) / 0.35, 0, 1) ** 1.5
        late = late * (1 - near) + np.maximum(late, kring) * near
        # The knot itself: dark, darkest at its edge and a shade lighter in the heart, its outline ragged.
        body = np.clip(1 - (kd - kr) / (kr * 0.35), 0, 1)
        heart = np.clip(1 - kd / (kr * 0.6), 0, 1)
        rim = np.exp(-((kd - kr * 0.95) / (kr * 0.25)) ** 2)
        knot_mask = np.maximum(knot_mask, np.clip(body * (0.62 - heart * 0.2) + rim * 0.25, 0, 0.85))
        # A halo of darker wood round it, where the grain is wild.
        knot_mask = np.maximum(knot_mask, np.clip(1 - (kd - kr) / (kr * 1.6), 0, 1) * 0.18)
        # A small check (crack) across the heart of the bigger knots.
        if kr > 0.09 * h:
            ang = rng.uniform(0, np.pi)
            dist = np.abs((xx - kx) * np.sin(ang) - (yy - ky) * np.cos(ang))
            along = np.abs((xx - kx) * np.cos(ang) + (yy - ky) * np.sin(ang))
            crack = np.clip(1 - dist / 1.0, 0, 1) * 0.8 * np.clip(1 - along / (kr * 0.9), 0, 1)
            knot_mask = np.maximum(knot_mask, crack)

    # Oak's open pores: short dark dashes strung along the grain, thickest in the latewood.
    pores = fbm(shape, w // 5, h // 2, 1)
    pores = np.clip((pores - 0.62) / 0.2, 0, 1) * (0.35 + 0.65 * late)
    # Colour drifts gently along the board, and a faint streak or two.
    drift = fbm(shape, 3, 1, 3) - 0.5
    streak = np.clip((fbm(shape, 2, 8, 2) - 0.6) / 0.2, 0, 1)
    fibre = fbm(shape, max(2, w // 120), h // 3, 2) - 0.5

    base = np.array(tone, np.float32)
    light = base * 1.06
    dark = base * np.array([0.55, 0.50, 0.46])
    img = light * (1 - late[..., None] * 0.6) + dark * (late[..., None] * 0.6)
    img *= (1 + drift[..., None] * 0.16 + fibre[..., None] * 0.10)
    img = img * (1 - pores[..., None] * 0.35) + dark * 0.8 * pores[..., None] * 0.35
    img = img * (1 - streak[..., None] * 0.08) + np.array([235, 205, 160]) * streak[..., None] * 0.08
    knot_col = base * np.array([0.30, 0.24, 0.20])
    img = img * (1 - knot_mask[..., None]) + knot_col * knot_mask[..., None]
    return img


img = np.zeros((H, W, 3), np.float32)
groove = np.zeros((H, W), np.float32)
ho = H / BOARDS
base_tone = np.array([168, 114, 70], np.float32)
xx_full = np.arange(W, dtype=np.float32)
prev_joints = []
for row in range(BOARDS + 1):
    top = int(round(row * ho - ho * 0.5))
    bottom = int(round((row + 1) * ho - ho * 0.5))
    y0, y1 = max(top, 0), min(bottom, H)
    if y1 <= y0:
        continue
    # End joints: one or two, never near the ones in the row above.
    joints = []
    for _ in range(rng.integers(1, 3)):
        for _attempt in range(30):
            j = rng.uniform(0.1, 0.9) * W
            if all(abs(j - p) > W * 0.18 for p in prev_joints + joints):
                joints.append(j)
                break
    joints.sort()
    edges = [-(ho * 0.2)] + joints + [W + ho * 0.2]
    for a, b in zip(edges[:-1], edges[1:]):
        xa, xb = int(max(a, 0)), int(min(b, W))
        if xb <= xa:
            continue
        tone = base_tone * rng.uniform(0.84, 1.08) * np.array([1, rng.uniform(0.985, 1.015), rng.uniform(0.95, 1.04)])
        piece = board(int(b - a) + 2, bottom - top, tone)
        ox = xa - int(a)
        oy = y0 - top
        img[y0:y1, xa:xb] = piece[oy:oy + (y1 - y0), ox:ox + (xb - xa)]
        if b < W:
            # The end joint: a thin, slightly wandering dark line.
            jx = b + (value_noise((y1 - y0, 1), 1, 3)[:, 0] - 0.5) * 1.5
            dist = np.abs(xx_full[None, :] - jx[:, None])
            groove[y0:y1] = np.maximum(groove[y0:y1], np.clip(1 - dist / 1.3, 0, 1) * 0.85)
    prev_joints = joints
    # The long groove along the board's top edge: uneven depth and width, with the odd chip.
    if top > 0:
        width = 1.0 + 2.4 * value_noise((1, W), 12, 1)[0] ** 1.5 + 1.8 * np.clip((value_noise((1, W), 30, 1)[0] - 0.75) / 0.25, 0, 1)
        centre = top + (value_noise((1, W), 8, 1)[0] - 0.5) * 1.6
        ys = np.arange(max(top - 8, 0), min(top + 8, H), dtype=np.float32)
        dist = np.abs(ys[:, None] - centre[None, :])
        depth = 0.7 + 0.3 * value_noise((1, W), 20, 1)[0]
        groove[ys.astype(int)] = np.maximum(groove[ys.astype(int)], np.clip(1 - dist / width[None, :], 0, 1) * depth[None, :])

# Each groove: a dark crack with a soft shadow on its lower lip and a catch of light on its upper edge.
shadow = ndimage.gaussian_filter(groove, 2.2)
lift = np.roll(shadow, -2, axis=0) - np.roll(shadow, 2, axis=0)
img *= (1 - np.clip(shadow * 0.5, 0, 0.6))[..., None]
img += np.clip(lift, 0, 1)[..., None] * 18
img = img * (1 - groove[..., None]) + np.array([34, 20, 12]) * groove[..., None]
# A finish: the faintest broad sheen and fine noise.
sheen = ndimage.gaussian_filter(rng.random((H // 40, W // 40)), 1)
sheen = ndimage.zoom(sheen, (H / sheen.shape[0], W / sheen.shape[1]), order=3)[:H, :W]
img *= (0.97 + 0.06 * (sheen - sheen.min()) / (np.ptp(sheen) + 1e-9))[..., None]
img += (rng.random((H, W, 1)) - 0.5) * 4
Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(OUT)
print("wrote", OUT)

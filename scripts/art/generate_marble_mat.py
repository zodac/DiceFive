#!/usr/bin/env python3
"""Generates the Marble mat's texture (composeResources/drawable/mat_marble.webp): white Statuario-style
marble - faint grey clouds, long dark streaks running diagonally that branch and rejoin at shallow
angles (the edges of Voronoi cells stretched along the diagonal and domain-warped), a sparse web of
hairline cracks, and soft grey ghost veins behind them. Every vein has a sharp core, varies in
thickness along its length and bleeds a grey halo into the stone.

Needs numpy, scipy and pillow. Deterministic for a given seed; the shipped texture is seed 11:
    python3 scripts/art/generate_marble_mat.py 11 mat_marble.png
    magick mat_marble.png -quality 88 -define webp:method=6 \
        app/shared/src/commonMain/composeResources/drawable/mat_marble.webp
"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage
from scipy.spatial import cKDTree

W, H = 1080, 640
SEED = int(sys.argv[1]) if len(sys.argv) > 1 else 11
OUT = sys.argv[2] if len(sys.argv) > 2 else "mat_marble.png"
rng = np.random.default_rng(SEED)

yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)


def value_noise(cells_x, cells_y):
    """Smooth noise in 0..1 with roughly cells_x x cells_y features across the image."""
    grid = rng.random((cells_y + 4, cells_x + 4)).astype(np.float32)
    big = ndimage.zoom(grid, ((H + 4 * H / cells_y) / grid.shape[0], (W + 4 * W / cells_x) / grid.shape[1]), order=3)
    oy = int(2 * H / cells_y)
    ox = int(2 * W / cells_x)
    return big[oy:oy + H, ox:ox + W]


def fbm(base, octaves, gain=0.5):
    total = np.zeros((H, W), np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        n = 2 ** o
        total += amp * value_noise(int(base * n * W / H), base * n)
        norm += amp
        amp *= gain
    return total / norm


def normalise(a):
    return (a - a.min()) / (a.max() - a.min() + 1e-9)


# Domain warp shared by everything, so the veins and clouds bend together like one stone.
warp_x = (fbm(2, 5) - 0.5) * 140
warp_y = (fbm(2, 5) - 0.5) * 140
wx = xx + warp_x
wy = yy + warp_y

# --- Clouds: soft grey patches, some fairly strong, most very faint.
cloud = normalise(fbm(2, 6, 0.55))
cloud = np.clip((cloud - 0.5) / 0.5, 0, 1) ** 1.8
haze = normalise(fbm(4, 5, 0.6))

# --- Fracture network: edges of warped Voronoi cells (F2 - F1 small).
n_cells = 26
pts = np.column_stack([rng.random(n_cells) * (W + 200) - 100, rng.random(n_cells) * (H + 200) - 100])
# Stretch along the diagonal so the cells, and so the cracks, lean the same way as the main veins.
angle = np.deg2rad(-28)
ca, sa = np.cos(angle), np.sin(angle)
def stretch(x, y):
    u = x * ca - y * sa
    v = x * sa + y * ca
    return np.column_stack([u * 0.32, v * 1.0])
tree = cKDTree(stretch(pts[:, 0], pts[:, 1]))
fine_wx = wx + (fbm(8, 3) - 0.5) * 36
fine_wy = wy + (fbm(8, 3) - 0.5) * 36
d, _ = tree.query(stretch(fine_wx.ravel(), fine_wy.ravel()), k=2)
edge = (d[:, 1] - d[:, 0]).reshape(H, W)
# Crack width varies along its length; many edges fade out entirely so it's a web, not a grid.
thick = normalise(fbm(3, 4)) ** 2.2
width = 1.6 + 7.5 * thick
ragged = 0.75 + 0.5 * fbm(14, 2)
crack = np.clip(1 - edge / (width * ragged), 0, 1) ** 0.8
presence = np.clip((fbm(3, 4) - 0.36) / 0.2, 0, 1)
crack *= presence

# Second, finer network of hairline cracks.
pts2 = np.column_stack([rng.random(120) * (W + 100) - 50, rng.random(120) * (H + 100) - 50])
tree2 = cKDTree(stretch(pts2[:, 0], pts2[:, 1]))
d2, _ = tree2.query(stretch(fine_wx.ravel() * 1.0, fine_wy.ravel()), k=2)
edge2 = (d2[:, 1] - d2[:, 0]).reshape(H, W)
hair = np.clip(1 - edge2 / (1.5 * ragged), 0, 1) * np.clip((fbm(4, 4) - 0.55) / 0.2, 0, 1)

# --- Ghost veins: a second, looser network, blurred into soft grey drifts behind the sharp ones.
pts3 = np.column_stack([rng.random(14) * (W + 300) - 150, rng.random(14) * (H + 300) - 150])
tree3 = cKDTree(stretch(pts3[:, 0], pts3[:, 1]))
d3, _ = tree3.query(stretch(wx.ravel(), wy.ravel()), k=2)
edge3 = (d3[:, 1] - d3[:, 0]).reshape(H, W)
ghost = ndimage.gaussian_filter(np.clip(1 - edge3 / 5, 0, 1), 5) * np.clip((fbm(2, 3) - 0.3) / 0.3, 0, 1)

hair = np.clip(ndimage.gaussian_filter(hair, 0.9) * 1.6, 0, 1)
veins = np.clip(np.maximum(crack, hair * 0.5), 0, 1)

# Halo: veins bleed grey into the stone around them.
halo = ndimage.gaussian_filter(veins, 4) * 1.6 + ndimage.gaussian_filter(veins, 14) * 1.6
halo = np.clip(halo, 0, 1)
core = ndimage.gaussian_filter(veins, 0.9)

# --- Compose.
white = np.array([247, 246, 243], np.float32)
cloud_grey = np.array([216, 215, 214], np.float32)
halo_grey = np.array([172, 172, 174], np.float32)
vein_dark = np.array([50, 50, 54], np.float32)

img = np.broadcast_to(white, (H, W, 3)).copy()
t = (cloud * 0.75 + haze * 0.18)[..., None]
img = img * (1 - t) + cloud_grey * t
t = np.clip(halo * 0.55 + ghost * 0.9, 0, 1)[..., None]
img = img * (1 - t) + halo_grey * t
t = (core * (0.5 + 0.3 * fbm(6, 3) + 0.35 * fbm(22, 2)))[..., None].clip(0, 1)
img = img * (1 - t) + vein_dark * t
# Crystalline grain, barely there.
img += (rng.random((H, W, 1)) - 0.5) * 5
Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(OUT)
print("wrote", OUT)

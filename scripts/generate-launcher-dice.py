#!/usr/bin/env python3
"""Regenerates the launcher icon's five cube dice in its foreground and themed layers.

Run it after changing the pose below. Each cube is projected as TossedCube (RollingDie.kt) draws one. Everything else
in the two files (the cup, the zoom, and the comments) is left alone, and it fails rather than write a file it can't
place the dice in. Coordinates are the layers' own, inside their 0.8 zoom.
"""
import math
import pathlib
import re
import sys

DRAWABLES = pathlib.Path(__file__).resolve().parent.parent / "app/android/src/main/res/drawable"

# Value, centre x, centre y, side, and spin in degrees, in the order the foreground draws them.
DICE = [(2, 23.0, 67.5, 14, -52), (4, 30.5, 25.9, 15, 28), (5, 56.0, 20.5, 14, -5), (3, 77.6, 29.9, 14, 18),
        (6, 87.0, 54.2, 14, -77)]
# Each die's front looks this many degrees away from the cup's mouth.
TILT_DEGREES = 20
CUP_MOUTH = (54, 45.5)

EYE_DISTANCE = 4.0  # Face widths from the front plane, as CUBE_EYE_DISTANCE in RollingDie.kt.
CORNER = 0.25
CORNER_POINTS = 7
PIP_RADIUS = 1.0 / 14
PIP_POINTS = 16
NEAR, FAR = 4.83 / 14, 9.18 / 14
SIX_TOP, SIX_BOTTOM = 4.46 / 14, 9.54 / 14
PIPS = {
    1: [(.5, .5)],
    2: [(NEAR, NEAR), (FAR, FAR)],
    3: [(NEAR, NEAR), (.5, .5), (FAR, FAR)],
    4: [(NEAR, NEAR), (FAR, NEAR), (NEAR, FAR), (FAR, FAR)],
    5: [(NEAR, NEAR), (FAR, NEAR), (.5, .5), (NEAR, FAR), (FAR, FAR)],
    6: [(NEAR, SIX_TOP), (NEAR, .5), (NEAR, SIX_BOTTOM), (FAR, SIX_TOP), (FAR, .5), (FAR, SIX_BOTTOM)],
}
# A real die's neighbours for each front value (opposite faces add up to 7): right, left, down, and up.
NEIGHBOURS = {1: (3, 4, 5, 2), 2: (3, 4, 1, 6), 3: (6, 1, 5, 2), 4: (1, 6, 5, 2), 5: (3, 4, 6, 1), 6: (3, 4, 2, 5)}

PIP_COLOUR = (0x2B, 0x21, 0x18)
BODY_COLOUR = (0xB4, 0xA0, 0x74)
FACE_TOP = (0xFF, 0xFF, 0xFA)
FACE_BOTTOM = (0xCD, 0xB9, 0x8C)
LIGHT = (-0.45, -0.6, 0.66)  # Towards the top left, and the viewer.
SHADOW_OFFSET = (1.0, 1.5)
THEMED_GAP = 0.03  # Parts the themed icon's faces, in face widths, since it has no colours to tell them apart.


def apply(matrix, vector):
    return tuple(sum(matrix[row][col] * vector[col] for col in range(3)) for row in range(3))


def multiply(a, b):
    return [[sum(a[row][k] * b[k][col] for k in range(3)) for col in range(3)] for row in range(3)]


def spun(degrees):
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def leaning(heading, degrees):
    """The rotation that leans +z over by [degrees] towards a screen heading (0 is right, 90 is down)."""
    h, t = math.radians(heading), math.radians(degrees)
    ux, uy = -math.sin(h), math.cos(h)
    c, s = math.cos(t), math.sin(t)
    return [[c + ux * ux * (1 - c), ux * uy * (1 - c), uy * s],
            [uy * ux * (1 - c), c + uy * uy * (1 - c), -ux * s],
            [-uy * s, ux * s, c]]


def faces_of(value):
    """Each face of a unit cube about the origin: its value, a corner, its two axes, and its normal."""
    right, left, down, up = NEIGHBOURS[value]
    return [(value, (-.5, -.5, .5), (1, 0, 0), (0, 1, 0), (0, 0, 1)),
            (right, (.5, -.5, .5), (0, 0, -1), (0, 1, 0), (1, 0, 0)),
            (left, (-.5, -.5, -.5), (0, 0, 1), (0, 1, 0), (-1, 0, 0)),
            (down, (-.5, .5, .5), (1, 0, 0), (0, 0, -1), (0, 1, 0)),
            (up, (-.5, -.5, -.5), (1, 0, 0), (0, 0, 1), (0, -1, 0))]


def rounded_square():
    points = []
    for cx, cy, start in [(1 - CORNER, CORNER, -90), (1 - CORNER, 1 - CORNER, 0), (CORNER, 1 - CORNER, 90),
                          (CORNER, CORNER, 180)]:
        for i in range(CORNER_POINTS + 1):
            angle = math.radians(start + 90 * i / CORNER_POINTS)
            points.append((cx + CORNER * math.cos(angle), cy + CORNER * math.sin(angle)))
    return points


def convex_hull(points):
    ordered = sorted(set(points))

    def half(sequence):
        hull = []
        for p in sequence:
            while len(hull) > 1 and ((hull[-1][0] - hull[-2][0]) * (p[1] - hull[-2][1])
                                     - (hull[-1][1] - hull[-2][1]) * (p[0] - hull[-2][0])) <= 0:
                hull.pop()
            hull.append(p)
        return hull[:-1]

    return half(ordered) + half(reversed(ordered))


def path_data(polygon):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in polygon) + " Z"


def colour(rgb, shade=1.0):
    return "#%02X%02X%02X" % tuple(min(255, int(channel * shade)) for channel in rgb)


def pip_ring(face_point, place, x, y):
    return [place(face_point(x + PIP_RADIUS * math.cos(i * math.pi / 8), y + PIP_RADIUS * math.sin(i * math.pi / 8)))
            for i in range(PIP_POINTS)]


def project(die):
    """The die's visible faces, farthest first, and how to place a projected point in the layer."""
    value, cx, cy, side, spin = die
    heading = (math.degrees(math.atan2(CUP_MOUTH[1] - cy, CUP_MOUTH[0] - cx)) + 180) % 360
    rotation = multiply(leaning(heading, TILT_DEGREES), spun(spin))

    def to_screen(point):
        x, y, z = apply(rotation, point)
        scale = EYE_DISTANCE / (EYE_DISTANCE - (z - .5))  # The front plane, untilted, keeps its size.
        return x * scale * side, y * scale * side

    visible, outline = [], []
    for face_value, corner, u, v, normal in faces_of(value):
        turned = apply(rotation, normal)
        centre = apply(rotation, tuple(n * .5 for n in normal))
        towards_eye = (turned[0] * -centre[0] + turned[1] * -centre[1]
                       + turned[2] * (EYE_DISTANCE + .5 - centre[2]))
        if towards_eye <= 0.001:
            continue

        def face_point(a, b, corner=corner, u=u, v=v):
            return to_screen(tuple(corner[i] + u[i] * a + v[i] * b for i in range(3)))

        outline += [face_point(a, b) for a, b in rounded_square()]
        visible.append((centre[2], face_value, face_point, turned))

    # The whole outline is centred on the die's centre, so a tilt doesn't move the die.
    hull = convex_hull(outline)
    middle_x = (min(p[0] for p in hull) + max(p[0] for p in hull)) / 2
    middle_y = (min(p[1] for p in hull) + max(p[1] for p in hull)) / 2

    def place(point):
        return point[0] - middle_x + cx, point[1] - middle_y + cy

    return sorted(visible, key=lambda face: face[0]), place, [place(p) for p in hull]


def solid_path(data, fill, stroke=""):
    return f'    <path\n        android:pathData="{data}"\n        android:fillColor="{fill}"{stroke} />'


def foreground_die(die):
    value = die[0]
    faces, place, hull = project(die)
    shadow = path_data([(x + SHADOW_OFFSET[0], y + SHADOW_OFFSET[1]) for x, y in hull])
    paths = [solid_path(shadow, "#40000000",
                        '\n        android:strokeColor="#26000000"\n        android:strokeWidth="0.9"'),
             solid_path(path_data(hull), colour(BODY_COLOUR))]
    for _, face_value, face_point, normal in faces:
        polygon = [place(face_point(a, b)) for a, b in rounded_square()]
        lit = max(0.0, sum(normal[i] * LIGHT[i] for i in range(3)))
        is_front = face_value == value
        shade = 1.0 if is_front else 0.62 + 0.42 * lit
        xs, ys = [p[0] for p in polygon], [p[1] for p in polygon]
        paths.append(
            f'    <path\n        android:pathData="{path_data(polygon)}">\n'
            '        <aapt:attr name="android:fillColor">\n            <gradient\n'
            '                android:type="linear"\n'
            f'                android:startX="{min(xs):.2f}"\n                android:startY="{min(ys):.2f}"\n'
            f'                android:endX="{max(xs):.2f}"\n                android:endY="{max(ys):.2f}"\n'
            f'                android:startColor="{colour(FACE_TOP, shade)}"\n'
            f'                android:endColor="{colour(FACE_BOTTOM, shade)}" />\n'
            '        </aapt:attr>\n    </path>')
        pip = colour(PIP_COLOUR, 1.0 if is_front else 1.15)
        for x, y in PIPS[face_value]:
            paths.append(solid_path(path_data(pip_ring(face_point, place, x, y)), pip))
    return "\n".join(paths)


def themed_die(die):
    """One even-odd path a face, with its pips as holes: a single path a die is too long for lint's VectorPath."""
    faces, place, _ = project(die)
    # The themed faces don't overlap, so they go in the order faces_of lists them rather than by depth.
    order = [face[0] for face in faces_of(die[0])]
    paths = []
    for _, face_value, face_point, _ in sorted(faces, key=lambda face: order.index(face[1])):
        inset = THEMED_GAP
        parts = [path_data([place(face_point(inset + a * (1 - 2 * inset), inset + b * (1 - 2 * inset)))
                            for a, b in rounded_square()])]
        parts += [path_data(pip_ring(face_point, place, x, y)) for x, y in PIPS[face_value]]
        paths.append('        <path\n            android:fillType="evenOdd"\n            android:fillColor="#000000"\n'
                     f'            android:pathData="{" ".join(parts)}" />')
    return "\n".join(paths)


def with_foreground_dice(text):
    """Each die's paths run from the comment that names its value to the next comment, or the zoom's end."""
    pattern = re.compile(r'(<!-- (?:(?!-->).)*?Showing (\d)(?:(?!-->).)*?-->\n)(.*?)(?=\n\n    (?:<!--|</group>))',
                         re.DOTALL)
    dice = {die[0]: die for die in DICE}
    seen = []

    def replace(match):
        seen.append(int(match[2]))
        return match[1] + foreground_die(dice[int(match[2])])

    result = pattern.sub(replace, text)
    if seen != [die[0] for die in DICE]:
        sys.exit(f"Foreground dice not found in order: {seen}")
    return result


def with_themed_dice(text):
    """The dice are the run of even-odd paths made only of straight lines; the cup's body has curves."""
    one = r'[ \t]*<path\s+android:fillType="evenOdd"\s+android:fillColor="#000000"\s+android:pathData="M[^"C]*" />'
    run = re.compile(rf'{one}(?:\s*{one})*')
    matches = run.findall(text)
    if len(matches) != 1:
        sys.exit(f"Expected one run of themed dice, found {len(matches)}")
    return run.sub(lambda _: "\n\n".join(themed_die(die) for die in DICE), text)


def main():
    # Both are worked out before either is written, so a failure can't leave the two layers out of step.
    layers = [("ic_launcher_foreground.xml", with_foreground_dice), ("ic_launcher_monochrome.xml", with_themed_dice)]
    results = [(DRAWABLES / name, regenerate((DRAWABLES / name).read_text(encoding="utf-8")))
               for name, regenerate in layers]
    for path, text in results:
        path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()

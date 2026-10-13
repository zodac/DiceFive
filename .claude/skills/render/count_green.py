# Counts pure-green pixels in a PNG render (the debug-colour gap check). zlib only - there's no PIL in the sandbox.
# Usage: python3 -I count_green.py render.png
import struct
import sys
import zlib

d = open(sys.argv[1], 'rb').read()
i = 8
idat = b''
while i < len(d):
    n, t = struct.unpack('>I4s', d[i:i + 8])
    c = d[i + 8:i + 8 + n]
    if t == b'IHDR':
        w, h, _, ct = struct.unpack('>IIBB', c[:10])
    if t == b'IDAT':
        idat += c
    i += 12 + n
raw = zlib.decompress(idat)
bpp = 4 if ct == 6 else 3
stride = w * bpp
prev = bytearray(stride)
p = 0
green = 0
for y in range(h):
    f = raw[p]
    line = bytearray(raw[p + 1:p + 1 + stride])
    p += 1 + stride
    for x in range(stride):
        a = line[x - bpp] if x >= bpp else 0
        b = prev[x]
        cc = prev[x - bpp] if x >= bpp else 0
        if f == 1:
            line[x] = (line[x] + a) & 255
        elif f == 2:
            line[x] = (line[x] + b) & 255
        elif f == 3:
            line[x] = (line[x] + (a + b) // 2) & 255
        elif f == 4:
            pa, pb, pc = abs(b - cc), abs(a - cc), abs(a + b - 2 * cc)
            line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else cc)) & 255
    green += sum(1 for x in range(0, stride, bpp) if line[x + 1] > 180 and line[x] < 90 and line[x + 2] < 90)
    prev = line
print("green pixels:", green, "of", w * h)

# prévias dos trajes vestidos num manequim (pele genérica de jogador)
import sys, copy, json, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, "/home/claude/lore3d"); sys.path.insert(0, "/home/claude/especies"); sys.path.insert(0, "/home/claude/unif2")
from core import render
from pose import posed
from traje import TEX

def skin():
    m = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); d = ImageDraw.Draw(m)
    pele, cab, cam, calca, sap = (198, 140, 104), (64, 42, 26), (60, 110, 150), (52, 58, 96), (60, 60, 64)
    d.rectangle([0, 0, 31, 15], fill=pele + (255,))
    d.rectangle([8, 0, 15, 7], fill=cab + (255,)); d.rectangle([0, 8, 31, 9], fill=cab + (255,)); d.rectangle([24, 8, 31, 15], fill=cab + (255,))
    d.rectangle([8, 8, 15, 9], fill=cab + (255,))
    for x, c in ((9, (255, 255, 255)), (10, (50, 80, 140)), (13, (50, 80, 140)), (14, (255, 255, 255))): d.point((x, 12), fill=c + (255,))
    d.line([10, 14, 13, 14], fill=(150, 90, 70, 255))
    for box, c in (((16, 16, 39, 31), cam), ((40, 16, 55, 31), cam), ((32, 48, 47, 63), cam), ((0, 16, 15, 31), calca), ((16, 48, 31, 63), calca)):
        d.rectangle(box, fill=c + (255,))
    for box in ((44, 28, 55, 31), (36, 60, 47, 63)): d.rectangle(box, fill=pele + (255,))
    for box in ((0, 28, 15, 31), (16, 60, 31, 63)): d.rectangle(box, fill=sap + (255,))
    return m

def box_uv(u, v, sx, sy, sz, ox, oy):
    r = {"east": (u, v + sz, sz, sy), "north": (u + sz, v + sz, sx, sy), "west": (u + sz + sx, v + sz, sz, sy),
         "south": (u + 2 * sz + sx, v + sz, sx, sy), "up": (u + sz, v, sx, sz), "down": (u + sz + sx, v, sx, sz)}
    return {k: {"uv": [a + ox, b + oy], "uv_size": [w, h]} for k, (a, b, w, h) in r.items()}

MANNEQUIN = [("armorHead", (-4, 24, -4), (8, 8, 8), (0, 0)), ("armorBody", (-4, 12, -2), (8, 12, 4), (16, 16)),
             ("armorRightArm", (-8, 12, -2), (4, 12, 4), (40, 16)), ("armorLeftArm", (4, 12, -2), (4, 12, 4), (32, 48)),
             ("armorRightLeg", (-3.9, 0, -2), (4, 12, 4), (0, 16)), ("armorLeftLeg", (-0.1, 0, -2), (4, 12, 4), (16, 48))]

def vestido(geo, tex, gl, pose=None, sem_manequim=False):
    """geo + textura combinada (traje à esquerda, pele à direita)"""
    g = copy.deepcopy(geo)
    T = Image.new("RGBA", (TEX * 2, TEX * 2), (0, 0, 0, 0)); T.paste(tex, (0, 0)); T.paste(skin(), (TEX, 0))
    G = Image.new("RGBA", (TEX * 2, TEX * 2), (0, 0, 0, 0)); G.paste(gl, (0, 0))
    G.putpixel((TEX - 1, TEX - 1), (0, 0, 0, 0))
    bones = {b["name"]: b for b in g["minecraft:geometry"][0]["bones"]}
    if not sem_manequim:
        for bn, o, s, uv in MANNEQUIN:
            bones[bn].setdefault("cubes", []).insert(0, {"origin": list(o), "size": list(s), "uv": box_uv(uv[0], uv[1], *s, TEX, 0)})
    if pose: g = posed(g, pose, 0)
    return g, T, G

POSE_A = {"armorRightArm": {"rotation": [4, 0, 7]}, "armorLeftArm": {"rotation": [-4, 0, -7]},
          "armorRightLeg": {"rotation": [-3, 0, 2]}, "armorLeftLeg": {"rotation": [3, 0, -2]},
          "armorRightBoot": {"rotation": [-3, 0, 2]}, "armorLeftBoot": {"rotation": [3, 0, -2]}}

def vistas(geo, tex, gl, size=(330, 560)):
    out = []
    for yaw in (-25, 155, 65):
        g, T, G = vestido(geo, tex, gl, POSE_A)
        out.append(render(g, T, G, yaw=yaw, pitch=8, size=size, margin=0.04))
    return out

def font(s): return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", s)

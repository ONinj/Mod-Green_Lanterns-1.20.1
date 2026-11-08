import sys
sys.path.insert(0, "/home/claude/aneis"); sys.path.insert(0, "/home/claude/lore3d")
from PIL import Image
from core import render
def para_geo(model, tex):
    W = 64; at = Image.new("RGBA", (128, 128), (0, 0, 0, 0)); gl = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    at.paste(tex, (0, 0)); at.paste(tex, (64, 0)); gl.paste(tex, (64, 0))
    cubes = []
    for e in model["elements"]:
        f, t = e["from"], e["to"]; luz = "forge_data" in e
        c = {"origin": [8 - t[0], f[1], f[2] - 8], "size": [t[0] - f[0], t[1] - f[1], t[2] - f[2]], "uv": {}}
        for face in ("north", "south", "east", "west", "up", "down"):
            gf = {"east": "west", "west": "east"}.get(face, face)
            u1, v1, u2, v2 = e["faces"][face]["uv"]; x0 = 64 if luz else 0
            c["uv"][gf] = {"uv": [x0 + u1 * 4, v1 * 4], "uv_size": [(u2 - u1) * 4, (v2 - v1) * 4]}
        if "rotation" in e:
            r = e["rotation"]; o = r["origin"]; th = r["angle"]
            c["pivot"] = [8 - o[0], o[1], o[2] - 8]
            c["rotation"] = {"x": [-th, 0, 0], "y": [0, -th, 0], "z": [0, 0, th]}[r["axis"]]
        cubes.append(c)
    return {"minecraft:geometry": [{"bones": [{"name": "b", "pivot": [0, 0, 0], "cubes": cubes}]}]}, at, gl
def vistas(model, tex, size=420):
    g, at, gl = para_geo(model, tex)
    return [render(g, at, gl, yaw=y, pitch=p, size=(size, size), margin=0.1) for y, p in ((-35, 25), (0, 89), (90, 5), (180, 20))]

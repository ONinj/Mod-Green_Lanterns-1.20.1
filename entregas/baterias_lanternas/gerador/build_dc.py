import os, sys, json, math, shutil, copy
sys.path.insert(0, "/home/claude/baterias"); sys.path.insert(0, "/home/claude/lore3d"); sys.path.insert(0, "/home/claude/especies")
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import nbtlib
from nbtlib.tag import Compound, List, Int, String
from core import render, rotmat
from bbmodel import geo_to_bbmodel
import centrais_dc as centrais

OUT = "/home/claude/Baterias_DC/centrais"
MOD = "lanterna"
if os.path.exists(OUT): shutil.rmtree(OUT)
A_ = f"{OUT}/src/main/resources/assets/{MOD}"; D_ = f"{OUT}/src/main/resources/data/{MOD}"
for d in ("geo/block", "animations/block", "textures/block", "blockstates", "models/block", "models/item", "lang"): os.makedirs(f"{A_}/{d}", exist_ok=True)
for d in ("structures", "dimension", "dimension_type"): os.makedirs(f"{D_}/{d}", exist_ok=True)
for d in ("previas", "blockbench"): os.makedirs(f"{OUT}/{d}", exist_ok=True)
font = lambda s: ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", s)
SEM_COLISAO = ("campo", "anel", "nucleo_brilho")

def anims(bones):
    idle = {"loop": True, "animation_length": 8.0, "bones": {}}
    ativo = {"loop": True, "animation_length": 2.0, "bones": {}}
    if "anel_1" in bones:
        idle["bones"]["anel_1"] = {"rotation": [0, "query.anim_time * 45", 0], "position": [0, "math.sin(query.anim_time * 45) * 3", 0]}
        ativo["bones"]["anel_1"] = {"rotation": [0, "query.anim_time * 180", 0], "position": [0, "math.sin(query.anim_time * 180) * 6", 0]}
    if "anel_2" in bones:
        idle["bones"]["anel_2"] = {"rotation": [0, "-query.anim_time * 30", 0], "position": [0, "math.cos(query.anim_time * 45) * 3", 0]}
        ativo["bones"]["anel_2"] = {"rotation": [0, "-query.anim_time * 140", 0]}
    idle["bones"]["nucleo_brilho"] = {"scale": ["1 + math.sin(query.anim_time * 90) * 0.04", "1 + math.sin(query.anim_time * 90) * 0.02", "1 + math.sin(query.anim_time * 90) * 0.04"]}
    ativo["bones"]["nucleo_brilho"] = {"scale": ["1.12 + math.sin(query.anim_time * 540) * 0.06", "1.05", "1.12 + math.sin(query.anim_time * 540) * 0.06"]}
    idle["bones"]["campo_brilho"] = {"scale": ["0.9 + math.sin(query.anim_time * 180) * 0.1", 1, "0.9 + math.sin(query.anim_time * 180) * 0.1"]}
    ativo["bones"]["campo_brilho"] = {"scale": ["1.4 + math.sin(query.anim_time * 720) * 0.15", 1, "1.4 + math.sin(query.anim_time * 720) * 0.15"]}
    return {"format_version": "1.8.0", "animations": {"misc.idle": idle, "misc.ativo": ativo}}

def voxel(geo):
    """células de 1 bloco (centradas no núcleo) cobertas por pelo menos 40% de geometria sólida"""
    cubes = []
    for b in geo["minecraft:geometry"][0]["bones"]:
        if b["name"].startswith(SEM_COLISAO): continue
        for c in b.get("cubes", []):
            o = np.array(c["origin"], float); s = np.array(c["size"], float)
            R = rotmat(-c["rotation"][0], c["rotation"][1], -c["rotation"][2]) if "rotation" in c else np.eye(3)
            pv = np.array(c.get("pivot", [0, 0, 0]), float)
            corners = np.array([[o[0] + s[0] * i, o[1] + s[1] * j, o[2] + s[2] * k] for i in (0, 1) for j in (0, 1) for k in (0, 1)])
            wc = (corners - pv) @ R.T + pv
            cubes.append((o, s, R, pv, wc.min(0), wc.max(0)))
    cnt = {}
    g = (np.arange(4) + 0.5) / 4
    for (o, s, R, pv, lo, hi) in cubes:
        i0, i1 = int(math.floor((lo[0] + 8) / 16)), int(math.floor((hi[0] + 8) / 16))
        j0, j1 = int(math.floor(lo[1] / 16)), int(math.floor(hi[1] / 16))
        k0, k1 = int(math.floor((lo[2] + 8) / 16)), int(math.floor((hi[2] + 8) / 16))
        for i in range(i0, i1 + 1):
            for j in range(j0, j1 + 1):
                for k in range(k0, k1 + 1):
                    P = np.array([[i * 16 - 8 + 16 * a, j * 16 + 16 * b_, k * 16 - 8 + 16 * c_] for a in g for b_ in g for c_ in g])
                    L = (P - pv) @ R + pv            # volta para o espaço do cubo (R ortogonal)
                    inside = np.all((L >= o - 1e-6) & (L <= o + s + 1e-6), axis=1)
                    if inside.any():
                        key = (i, j, k); prev = cnt.get(key, np.zeros(64, bool))
                        cnt[key] = prev | inside
    return {k for k, v in cnt.items() if v.mean() >= 0.4}

def estrutura(cor, sol):
    """monta o .nbt: casca invisível, portal, núcleo e ar na câmara/entrada"""
    cells = {}
    for c in sol: cells[c] = ("casca", {})
    for i in range(-2, 3):
        for k in range(-2, 3):
            for j in range(1, 7): cells[(i, j, k)] = ("air", {})
    for i in range(-1, 2):
        for k in range(-9, -2):
            for j in range(1, 5): cells[(i, j, k)] = ("air", {})
    for i in range(-1, 2):
        for k in range(-1, 2):
            for j in range(1, 5): cells[(i, j, k)] = ("portal", {"tropa": cor, "retorno": "false"})
    cells[(0, 0, 0)] = ("nucleo", {"tropa": cor})
    xs = [c[0] for c in cells]; ys = [c[1] for c in cells]; zs = [c[2] for c in cells]
    x0, y0, z0 = min(xs), min(ys), min(zs)
    size = (max(xs) - x0 + 1, max(ys) - y0 + 1, max(zs) - z0 + 1)
    pal, idx, blocks = [], {}, []
    for (i, j, k), (n, p) in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        name = "minecraft:air" if n == "air" else f"{MOD}:bateria_{n}"
        key = (name, tuple(sorted(p.items())))
        if key not in idx:
            idx[key] = len(pal); st = Compound({"Name": String(name)})
            if p: st["Properties"] = Compound({a: String(b) for a, b in p.items()})
            pal.append(st)
        blocks.append(Compound({"pos": List[Int]([Int(i - x0), Int(j - y0), Int(k - z0)]), "state": Int(idx[key])}))
    root = Compound({"DataVersion": Int(3465), "size": List[Int]([Int(v) for v in size]), "palette": List[Compound](pal),
                     "blocks": List[Compound](blocks), "entities": List[Compound]([])})
    nbtlib.File(root).save(f"{D_}/structures/bateria_central_{cor}.nbt", gzipped=True)
    return cells, size, (-x0, -y0, -z0)

CHEGADA = {  # piso, detalhe, luz (blocos do vanilla para a plataforma de chegada na dimensão)
    "verde": ("minecraft:green_concrete", "minecraft:lime_concrete", "minecraft:verdant_froglight"),
    "amarelo": ("minecraft:black_concrete", "minecraft:yellow_concrete", "minecraft:ochre_froglight"),
    "vermelho": ("minecraft:red_nether_bricks", "minecraft:magma_block", "minecraft:shroomlight"),
    "laranja": ("minecraft:orange_concrete", "minecraft:gold_block", "minecraft:shroomlight"),
    "azul": ("minecraft:white_concrete", "minecraft:light_blue_concrete", "minecraft:sea_lantern"),
    "indigo": ("minecraft:blue_concrete", "minecraft:purple_concrete", "minecraft:pearlescent_froglight"),
    "violeta": ("minecraft:purple_concrete", "minecraft:magenta_concrete", "minecraft:pearlescent_froglight"),
    "preto": ("minecraft:black_concrete", "minecraft:bone_block", "minecraft:crying_obsidian"),
    "branco": ("minecraft:white_concrete", "minecraft:gold_block", "minecraft:glowstone"),
}
def chegada(cor):
    piso, det, luz = CHEGADA[cor]; R = 6
    cells = {}
    for i in range(-R, R + 1):
        for k in range(-R, R + 1):
            d = math.hypot(i, k)
            if d <= R + 0.3:
                cells[(i, 0, k)] = luz if R - 1 < d <= R + 0.3 and (i + k) % 2 == 0 else (det if (abs(i) == abs(k) or i == 0 or k == 0) and d > 2 else piso)
    for i in range(-1, 2):
        for k in range(-1, 2): cells[(i, 0, k)] = luz; cells[(i, 1, k)] = ("portal", {"tropa": cor, "retorno": "true"})
    for (i, k) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        cells[(i, 1, k)] = det; cells[(i, 2, k)] = luz
    pal, idx, blocks = [], {}, []
    for (i, j, k), v in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        if isinstance(v, tuple): name, p = f"{MOD}:bateria_{v[0]}", v[1]
        else: name, p = v, {}
        key = (name, tuple(sorted(p.items())))
        if key not in idx:
            idx[key] = len(pal); st = Compound({"Name": String(name)})
            if p: st["Properties"] = Compound({a: String(b) for a, b in p.items()})
            pal.append(st)
        blocks.append(Compound({"pos": List[Int]([Int(i + R), Int(j), Int(k + R)]), "state": Int(idx[key])}))
    root = Compound({"DataVersion": Int(3465), "size": List[Int]([Int(2 * R + 1), Int(3), Int(2 * R + 1)]), "palette": List[Compound](pal),
                     "blocks": List[Compound](blocks), "entities": List[Compound]([])})
    nbtlib.File(root).save(f"{D_}/structures/chegada_{cor}.nbt", gzipped=True)

def jogador(geo, A):
    g = copy.deepcopy(geo); b = g["minecraft:geometry"][0]["bones"][0]
    u, v = A.mats["faixa_luz"][1]
    b.setdefault("cubes", []).append({"origin": [-4, 16, -150], "size": [8, 30, 5], "uv": {f: {"uv": [u + 10, v + 10], "uv_size": [4, 4]} for f in ("north", "south", "east", "west", "up", "down")}})
    return g

def previa_hitbox(cells, A, path, titulo):
    """renderiza as células da colisão (cinza), do portal (cor) e o núcleo"""
    tex = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); d = ImageDraw.Draw(tex)
    d.rectangle([0, 0, 15, 15], fill=(120, 126, 136, 255)); d.rectangle([0, 0, 15, 15], outline=(70, 74, 82, 255))
    d.rectangle([16, 0, 31, 15], fill=(60, 230, 120, 200)); d.rectangle([32, 0, 47, 15], fill=(255, 210, 60, 255))
    gl = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); ImageDraw.Draw(gl).rectangle([16, 0, 31, 15], fill=(60, 230, 120, 255))
    cubes = []
    for (i, j, k), (n, p) in cells.items():
        if n == "air": continue
        if n == "casca" and k < -1 and j < 7 and abs(i) < 9: continue        # corte na frente para ver dentro
        u = {"casca": 0, "portal": 16, "nucleo": 32}[n]
        cubes.append({"origin": [i * 16 - 8, j * 16, k * 16 - 8], "size": [16, 16, 16], "uv": {f: {"uv": [u, 0], "uv_size": [16, 16]} for f in ("north", "south", "east", "west", "up", "down")}})
    geo = {"minecraft:geometry": [{"bones": [{"name": "b", "pivot": [0, 0, 0], "cubes": cubes}]}]}
    im = render(geo, tex, gl, yaw=-30, pitch=25, size=(560, 760), margin=0.04)
    W = Image.new("RGBA", (560, 800), (16, 18, 24, 255)); W.alpha_composite(im, (0, 30))
    ImageDraw.Draw(W).text((280, 18), titulo, font=font(16), fill=(230, 230, 240), anchor="mm")
    return W

meta = {}; cards = []
dim_type = {"ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "has_skylight": False, "has_ceiling": False, "ambient_light": 0.5,
            "fixed_time": 18000, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0, "piglin_safe": False, "bed_works": False,
            "respawn_anchor_works": False, "has_raids": False, "logical_height": 256, "min_y": 0, "height": 256,
            "infiniburn": "#minecraft:infiniburn_overworld", "effects": "minecraft:the_end"}
json.dump(dim_type, open(f"{D_}/dimension_type/fonte.json", "w"), indent=1)
for cor, fn in centrais.BATERIAS.items():
    g, A = fn(); geo = g.export()
    n = f"bateria_central_{cor}"
    names = [b["name"] for b in geo["minecraft:geometry"][0]["bones"]]
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []): assert min(c["size"]) > 0
    json.dump(geo, open(f"{A_}/geo/block/{n}.geo.json", "w"), indent=1)
    an = anims(names); json.dump(an, open(f"{A_}/animations/block/{n}.animation.json", "w"), indent=1)
    for a_ in an["animations"].values():
        for bn in a_["bones"]: assert bn in names, (n, bn)
    A.img.save(f"{A_}/textures/block/{n}.png"); A.glow.save(f"{A_}/textures/block/{n}_glowmask.png"); assert A.glow.getbbox()
    geo_to_bbmodel(geo, [(f"{n}.png", A.img)], n, f"{OUT}/blockbench/{n}.bbmodel")
    sol = voxel(geo)
    cells, size, origem = estrutura(cor, sol)
    chegada(cor)
    json.dump({"type": f"{MOD}:fonte", "generator": {"type": "minecraft:flat", "settings": {"biome": "minecraft:the_void",
               "layers": [{"block": "minecraft:air", "height": 1}], "lakes": False, "features": False, "structure_overrides": []}}},
              open(f"{D_}/dimension/fonte_{cor}.json", "w"), indent=1)
    alt = max(c[1] for c in cells) + 1
    meta[cor] = dict(cubos=g.count(), estrutura=size, origem=origem, altura=alt, casca=sum(1 for v in cells.values() if v[0] == "casca"))
    # prévias
    views = [render(jogador(geo, A), A.img, A.glow, yaw=y, pitch=p, size=(520, 760), margin=0.04) for y, p in ((-30, 14), (150, 14), (0, 3))]
    W = Image.new("RGBA", (520 * 3 + 560, 800), (16, 18, 24, 255)); dr = ImageDraw.Draw(W)
    for i, im in enumerate(views): W.alpha_composite(im, (i * 520, 30))
    W.alpha_composite(previa_hitbox(cells, A, None, "hitbox: cinza = colisão · verde = portal · amarelo = núcleo"), (1560, 0))
    dr.text((780, 18), f"{centrais.NOMES[cor]} (bateria_central_{cor}) — {size[0]} x {alt} x {size[2]} blocos · o boneco à frente da porta tem a altura de um jogador",
            font=font(18), fill=(235, 235, 245), anchor="mm")
    W.convert("RGB").save(f"{OUT}/previas/{n}.png")
    cards.append((cor, views[0]))
    print(n, meta[cor], flush=True)
G = Image.new("RGBA", (7 * 300, 520), (16, 18, 24, 255)); dr = ImageDraw.Draw(G)
dr.text((G.width / 2, 26), "Baterias Centrais — lanternas", font=font(28), fill=(235, 235, 245), anchor="mm")
for i, (cor, im) in enumerate(cards):
    G.alpha_composite(im.resize((300, 438), Image.LANCZOS), (i * 300, 50))
    dr.text((i * 300 + 150, 500), centrais.NOMES[cor], font=font(13), fill=(220, 220, 230), anchor="mm")
G.convert("RGB").save(f"{OUT}/previas/galeria.png")
json.dump(meta, open("/home/claude/baterias/_meta_dc.json", "w"), indent=1)
print("ok")

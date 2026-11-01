# Corpos base (padrão, grande, pequeno): ossos obrigatórios, ilhas de UV fixas na metade de cima (256x128)
import sys, math, json, random
sys.path.insert(0, "/home/claude/lore3d")
from core import Geo, Atlas, hx, mix, lt, dk

D = 2.0          # pixels por unidade
TEX = 256

# ---------------------------------------------------------------- medidas de cada tipo de corpo (unidades; 16 = 1 bloco)
TYPES = {
 "padrao": dict(r=0.45, hip=13.0, hipx=2.0, leg=(3.8, 3.8), knee=7.0, foot=(4.0, 1.4, 4.8, -2.9),
                pelvis=(7.6, 3.0, 4.2), abd=(7.0, 3.6, 3.8), chest=(8.6, 6.4, 4.6), neck=(2.6, 1.6, 2.6),
                arm=3.2, upper=6.2, fore=5.4, hand=(2.8, 3.0, 2.8), head=(7.0, 6.8, 7.0), hitbox=(0.6, 2.0)),
 "grande": dict(r=0.65, hip=17.0, hipx=3.0, leg=(5.4, 5.4), knee=9.0, foot=(5.8, 2.0, 7.6, -4.4),
                pelvis=(11.0, 4.0, 6.0), abd=(10.0, 5.0, 5.6), chest=(13.6, 8.6, 7.0), neck=(3.6, 1.8, 3.6),
                arm=4.6, upper=8.4, fore=7.4, hand=(4.2, 4.2, 4.2), head=(8.6, 8.2, 8.4), hitbox=(1.1, 2.6)),
 "pequeno": dict(r=0.3, hip=8.4, hipx=1.45, leg=(2.8, 2.8), knee=4.4, foot=(3.0, 1.0, 3.8, -2.4),
                 pelvis=(5.4, 2.2, 3.2), abd=(5.0, 2.4, 3.0), chest=(6.0, 4.0, 3.4), neck=(1.8, 1.0, 1.8),
                 arm=2.2, upper=4.2, fore=3.6, hand=(2.0, 2.0, 2.0), head=(5.6, 4.8, 5.4), hitbox=(0.5, 1.3)),
}

def layout_points(t):
    """alturas de cada parte a partir das medidas"""
    T = TYPES[t]; P = {}
    pw, ph, pd = T["pelvis"]; aw, ah, ad = T["abd"]; cw, ch, cd = T["chest"]; nw, nh, nd = T["neck"]
    hip = T["hip"]
    P["pelvis"] = (hip - ph * 0.55, hip + ph * 0.45)
    P["abd"] = (P["pelvis"][1], P["pelvis"][1] + ah)
    P["chest"] = (P["abd"][1], P["abd"][1] + ch)
    P["neck"] = (P["chest"][1] - 0.4, P["chest"][1] - 0.4 + nh)
    P["head_pivot"] = P["chest"][1] + 0.3
    P["head"] = (P["neck"][1] - 0.4, P["neck"][1] - 0.4 + T["head"][1])
    ax = cw / 2 + T["arm"] / 2
    P["arm_x"] = ax
    P["shoulder"] = P["chest"][1] - T["arm"] * 0.45
    P["upper"] = (P["chest"][1] - T["upper"], P["chest"][1] + 0.2)
    P["elbow"] = P["upper"][0] + 0.2
    P["fore"] = (P["upper"][0] - T["fore"] + 0.2, P["upper"][0] + 0.2)
    P["wrist"] = P["fore"][0] + 0.2
    P["hand"] = (P["fore"][0] - T["hand"][1] + 0.2, P["fore"][0] + 0.2)
    P["thigh"] = (T["knee"], hip + 0.4)
    P["shin"] = (T["foot"][1] - 0.1, T["knee"] + 0.2)
    P["foot"] = (0.0, T["foot"][1])
    return P

# ---------------------------------------------------------------- ilhas de UV (box-UV por parte)
def island_size(w, h, d, dens=D):
    W = math.ceil(2 * (w + d) * dens); H = math.ceil((d + h) * dens)
    return W, H

def island_faces(x0, y0, w, h, d, dens=D):
    """regiões de cada face dentro da ilha: up, down, east, north, west, south"""
    W_, D_, H_ = w * dens, d * dens, h * dens
    return {"up": (x0 + D_, y0, W_, D_), "down": (x0 + D_ + W_, y0, W_, D_),
            "east": (x0, y0 + D_, D_, H_), "north": (x0 + D_, y0 + D_, W_, H_),
            "west": (x0 + D_ + W_, y0 + D_, D_, H_), "south": (x0 + 2 * D_ + W_, y0 + D_, W_, H_)}

def parts_for(t):
    """(nome, osso, caixa (x0,y0,z0)-(x1,y1,z1), densidade, grupo) — o mesmo para todas as espécies do tipo"""
    T = TYPES[t]; P = layout_points(t); out = []
    pw, ph, pd = T["pelvis"]; aw, ah, ad = T["abd"]; cw, ch, cd = T["chest"]; nw, nh, nd = T["neck"]
    out.append(("pelvis", "body", (-pw / 2, P["pelvis"][0], -pd / 2), (pw / 2, P["pelvis"][1], pd / 2), D, "corpo"))
    out.append(("abdomen", "body", (-aw / 2, P["abd"][0], -ad / 2), (aw / 2, P["abd"][1], ad / 2), D, "corpo"))
    out.append(("peito", "body", (-cw / 2, P["chest"][0], -cd / 2), (cw / 2, P["chest"][1], cd / 2), D, "corpo"))
    out.append(("pescoco", "body", (-nw / 2, P["neck"][0], -nd / 2), (nw / 2, P["neck"][1], nd / 2), D, "corpo"))
    A = T["arm"]; hw, hh, hd = T["hand"]; lw, ld = T["leg"]; fw, fh, fd, fz = T["foot"]
    for s, sd in ((-1, "r"), (1, "l")):
        x = s * P["arm_x"]
        out.append((f"braco_{sd}", f"arm_{sd}", (x - A / 2, P["upper"][0], -A / 2), (x + A / 2, P["upper"][1], A / 2), D, "corpo"))
        a2 = A * 0.94
        out.append((f"antebraco_{sd}", f"forearm_{sd}", (x - a2 / 2, P["fore"][0], -a2 / 2), (x + a2 / 2, P["fore"][1], a2 / 2), D, "corpo"))
        out.append((f"mao_{sd}", f"hand_{sd}", (x - hw / 2, P["hand"][0], -hd / 2), (x + hw / 2, P["hand"][1], hd / 2), D, "corpo"))
        lx = s * T["hipx"]
        out.append((f"coxa_{sd}", f"leg_{sd}", (lx - lw / 2, P["thigh"][0], -ld / 2), (lx + lw / 2, P["thigh"][1], ld / 2), D, "corpo"))
        l2 = lw * 0.92
        out.append((f"canela_{sd}", f"shin_{sd}", (lx - l2 / 2, P["shin"][0], -l2 / 2), (lx + l2 / 2, P["shin"][1], l2 / 2), D, "corpo"))
        out.append((f"pe_{sd}", f"shin_{sd}", (lx - fw / 2, 0.0, fz), (lx + fw / 2, fh, fz + fd), D, "corpo"))
    # partes de roupa (ossos roupa_*, escondidos por padrão: a pele deixa essa área transparente)
    sh = P["chest"][1]
    for s, sd in ((-1, "r"), (1, "l")):
        x = s * P["arm_x"]; ow = A * 1.55
        out.append((f"ombreira_{sd}", f"roupa_ombreira_{sd}", (x - ow / 2, sh - A * 0.9, -ow / 2), (x + ow / 2, sh + A * 0.45, ow / 2), D, "roupa"))
    out.append(("gola", "roupa_gola", (-cw * 0.42, sh - 0.2, -cd * 0.62), (cw * 0.42, sh + nh * 1.3, cd * 0.62), D, "roupa"))
    hw_, hh_, hd_ = T["head"]
    out.append(("capuz", "roupa_capuz", (-hw_ / 2 - 0.6, P["head"][0] + hh_ * 0.25, -hd_ / 2 - 0.3), (hw_ / 2 + 0.6, P["head"][1] + 0.7, hd_ / 2 + 0.8), 1.4, "roupa"))
    out.append(("mochila", "roupa_mochila", (-cw * 0.38, P["abd"][0] + 0.4, cd / 2), (cw * 0.38, sh - 0.6, cd / 2 + cd * 0.55), D, "roupa"))
    out.append(("casaco", "roupa_casaco", (-pw / 2 - 0.5, P["thigh"][0] + (P["thigh"][1] - P["thigh"][0]) * 0.1, -pd / 2 - 0.5),
                (pw / 2 + 0.5, P["pelvis"][1], pd / 2 + 0.5), 1.4, "roupa"))
    out.append(("capa", "capa", (-cw * 0.48, P["thigh"][0] - (P["thigh"][1] - P["thigh"][0]) * 0.4, cd / 2 + 0.4), (cw * 0.48, sh + 0.4, cd / 2 + 0.9), 1.0, "roupa"))
    return out

def pack(t):
    """empacota as ilhas na metade de cima (256 x 128). Determinístico."""
    items = []
    for name, bone, a, b, dens, grp in parts_for(t):
        w, h, d = (b[0] - a[0]), (b[1] - a[1]), (b[2] - a[2])
        W, H = island_size(w, h, d, dens)
        items.append([name, W, H, (w, h, d), dens])
    order = sorted(items, key=lambda it: (-it[2], -it[1]))
    x = y = rowh = 0; pos = {}
    for name, W, H, dims, dens in order:
        if x + W > TEX: x, y, rowh = 0, y + rowh, 0
        pos[name] = (x, y, W, H, dims, dens)
        x += W; rowh = max(rowh, H)
    if y + rowh > 128: raise RuntimeError(f"{t}: não coube ({y + rowh} > 128)")
    return pos

BONES = [  # (nome, pai, função do pivô)
 ("root", None), ("body", "root"), ("head", "body"),
 ("arm_r", "body"), ("forearm_r", "arm_r"), ("hand_r", "forearm_r"), ("anel", "hand_r"), ("emissor", "hand_r"),
 ("arm_l", "body"), ("forearm_l", "arm_l"), ("hand_l", "forearm_l"),
 ("capa", "body"),
 ("leg_r", "body"), ("shin_r", "leg_r"), ("leg_l", "body"), ("shin_l", "leg_l"),
 ("roupa_gola", "body"), ("roupa_mochila", "body"), ("roupa_casaco", "body"),
 ("roupa_ombreira_r", "arm_r"), ("roupa_ombreira_l", "arm_l"), ("roupa_capuz", "head"),
]

def pivots(t):
    T = TYPES[t]; P = layout_points(t); ax = P["arm_x"]
    pv = {"root": (0, 0, 0), "body": (0, T["hip"], 0), "head": (0, P["head_pivot"], 0), "capa": (0, P["chest"][1], TYPES[t]["chest"][2] / 2 + 0.4),
          "roupa_gola": (0, P["chest"][1], 0), "roupa_mochila": (0, P["chest"][1], 0), "roupa_casaco": (0, T["hip"], 0), "roupa_capuz": (0, P["head_pivot"], 0)}
    for s, sd in ((-1, "r"), (1, "l")):
        pv[f"arm_{sd}"] = (s * ax, P["shoulder"], 0); pv[f"roupa_ombreira_{sd}"] = (s * ax, P["shoulder"], 0)
        pv[f"forearm_{sd}"] = (s * ax, P["elbow"], 0); pv[f"hand_{sd}"] = (s * ax, P["wrist"], 0)
        pv[f"leg_{sd}"] = (s * T["hipx"], T["hip"], 0); pv[f"shin_{sd}"] = (s * T["hipx"], T["knee"], 0)
    hw, hh, hd = T["hand"]; x = -ax
    pv["anel"] = (x + hw * 0.32, P["hand"][0] + hh * 0.35, -hd / 2 - 0.05)       # no dedo, à frente da mão direita
    pv["emissor"] = (x, P["hand"][0] + hh * 0.5, -hd / 2 - 1.6)                 # à frente do punho fechado
    return pv

def build_body(t, name, atlas_seed=1, skin_atlas=None):
    """cria a Geo com o corpo do tipo t; a cabeça/partes extras são adicionadas depois pela espécie"""
    T = TYPES[t]
    A = skin_atlas or Atlas(TEX, 32, seed=atlas_seed)
    A.n = max(A.n, 32)                         # metade de baixo: slots a partir da linha 4
    g = Geo(name, A, density=D)
    g.uvrng = random.Random(atlas_seed * 7 + 3)
    pv = pivots(t)
    for b, parent in BONES:
        g.bone(b, pv[b], parent)
    pos = pack(t)
    r = T["r"]
    for pname, bone, a, b, dens, grp in parts_for(t):
        x0, y0, W, H, (w, h, d), dn = pos[pname]
        faces = island_faces(x0, y0, w, h, d, dn)
        uv = {f: {"uv": [round(fx, 3), round(fy, 3)], "uv_size": [round(fw, 3), round(fh, 3)]} for f, (fx, fy, fw, fh) in faces.items()}
        rr = r if (grp == "corpo" and min(w, h, d) >= 2 * r + 1.0) else 0
        if rr:
            # arestas arredondadas: 3 cubos cruzados, cada um usa as faces da mesma ilha
            subs = [((a[0] + rr, a[1], a[2] + rr), (b[0] - rr, b[1], b[2] - rr), ("up", "down")),
                    ((a[0], a[1] + rr, a[2] + rr), (b[0], b[1] - rr, b[2] - rr), ("east", "west")),
                    ((a[0] + rr, a[1] + rr, a[2]), (b[0] - rr, b[1] - rr, b[2]), ("north", "south"))]
            for sa, sb, _vis in subs:
                c = {"origin": [round(v, 4) for v in sa], "size": [round(sb[i] - sa[i], 4) for i in range(3)], "uv": uv, "_mat": "corpo", "_part": pname}
                g.bones[bone]["cubes"].append(c)
        else:
            c = {"origin": [round(v, 4) for v in a], "size": [round(b[i] - a[i], 4) for i in range(3)], "uv": uv, "_mat": grp, "_part": pname}
            g.bones[bone]["cubes"].append(c)
    # ossos-marcador com um cubo minúsculo (anel e emissor)
    for m in ("anel", "emissor"):
        x, y, z = pv[m]
        g.bones[m]["cubes"].append({"origin": [round(x - 0.05, 4), round(y - 0.05, 4), round(z - 0.05, 4)], "size": [0.1, 0.1, 0.1],
                                    "uv": {f: {"uv": [0, 0], "uv_size": [0.1, 0.1]} for f in ("north", "south", "east", "west", "up", "down")}, "_mat": "marcador"})
    g.pos = pos; g.ttype = t
    return g

# ---------------------------------------------------------------- pintura das ilhas (pele, roupas)
def paint_islands(img, glow, pos, fn, groups=("corpo",), parts=None):
    """fn(parte, face, fx, fy, px, py) -> None | (cor, alpha, brilha)"""
    p = img.load(); gp = glow.load() if glow else None
    allp = {n: grp for n, _, _, _, _, grp in parts}
    for name, (x0, y0, W, H, (w, h, d), dn) in pos.items():
        if allp.get(name) not in groups: continue
        for face, (fx0, fy0, fw, fh) in island_faces(x0, y0, w, h, d, dn).items():
            for yy in range(int(round(fy0)), int(round(fy0 + fh))):
                for xx in range(int(round(fx0)), int(round(fx0 + fw))):
                    u = (xx - fx0 + 0.5) / max(1e-6, fw); v = (yy - fy0 + 0.5) / max(1e-6, fh)
                    res = fn(name, face, u, v, xx - int(round(fx0)), yy - int(round(fy0)))
                    if res is None: continue
                    c, a, gl = res
                    c = hx(c) if isinstance(c, str) else c
                    p[xx, yy] = tuple(max(0, min(255, int(v_))) for v_ in c[:3]) + (a,)
                    if gp is not None and gl: gp[xx, yy] = p[xx, yy]

def free_pixel(g):
    """um pixel que nenhuma face usa (para o glowmask nunca ficar 100% transparente)"""
    used = set()
    for b in g.bones.values():
        for c in b["cubes"]:
            for f in c["uv"].values():
                u, v = f["uv"]; w, h = f["uv_size"]
                for x in range(int(u), int(math.ceil(u + abs(w))) + 1):
                    for y in range(int(v), int(math.ceil(v + abs(h))) + 1): used.add((x, y))
    for y in range(127, -1, -1):
        for x in range(TEX - 1, -1, -1):
            if (x, y) not in used: return x, y
    return TEX - 1, 127

def uv_template(t, path):
    """molde de UV com o nome de cada ilha e de cada face (para desenhar roupas)"""
    from PIL import Image, ImageDraw, ImageFont
    pos = pack(t); S = 4
    im = Image.new("RGBA", (TEX * S, 128 * S), (24, 26, 32, 255)); d = ImageDraw.Draw(im)
    f1 = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 15)
    f2 = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10)
    cols = {"up": (90, 120, 160), "down": (70, 80, 100), "north": (80, 150, 90), "south": (150, 110, 70), "east": (140, 90, 140), "west": (140, 90, 140)}
    names = {"up": "topo", "down": "base", "north": "frente", "south": "costas", "east": "lado", "west": "lado"}
    allp = {n: grp for n, _, _, _, _, grp in parts_for(t)}
    for name, (x0, y0, W, H, (w, h, dd), dn) in pos.items():
        for face, (fx, fy, fw, fh) in island_faces(x0, y0, w, h, dd, dn).items():
            c = cols[face] if allp[name] == "corpo" else tuple(int(v * 0.7) for v in cols[face])
            d.rectangle([fx * S, fy * S, (fx + fw) * S - 1, (fy + fh) * S - 1], fill=c + (255,), outline=(20, 20, 24, 255))
            if fw * S > 34 and fh * S > 14: d.text((fx * S + 3, fy * S + 2), names[face], font=f2, fill=(235, 235, 240))
        d.text((x0 * S + 3, (y0 + H) * S - 18), name, font=f1, fill=(255, 255, 255))
    im.save(path)

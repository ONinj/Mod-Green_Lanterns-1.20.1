# Espécies (cabeça + partes extra_ na metade de baixo; pele do corpo na metade de cima) e roupas
import math, random, sys
sys.path.insert(0, "/home/claude/lore3d")
from PIL import Image
from core import Atlas, hx, mix, lt, dk
from body import build_body, paint_islands, parts_for, TYPES, layout_points, TEX
from races import frect, fdisc, fbg

# emblemas das Tropas (os mesmos do pacote dos anéis/uniformes)
_E = {}
exec("E = {" + open("/home/claude/gerar.py", encoding="utf-8").read().split("E = {")[1].split("\n}")[0] + "\n}", {}, _E)
EMBLEMAS = _E["E"]

def side_s(sd): return -1 if sd == "r" else 1

# ======================================================================== pele do corpo (metade de cima)
def skin_painter(base, shade=0.22, seam=None, pattern=None, seed=1, veins=None, plates=None, spots=None, alpha=255, scars=None):
    rnd = random.Random(seed); cache = {}
    def fn(part, face, u, v, px, py):
        c = mix(lt(base, 0.08), dk(base, shade), v) if face in ("north", "south", "east", "west") else (lt(base, 0.1) if face == "up" else dk(base, 0.3))
        if face in ("east", "west"): c = dk(c, 0.06)
        key = (part, face, px, py)
        n = (hash(key) % 9) - 4
        c = tuple(x + n for x in c)
        gl = False
        if plates and face != "down":
            step = plates[0]
            if px % step == 0 or (py + (step // 2 if (px // step) % 2 else 0)) % step == 0: c = dk(c, plates[1])
        if veins:
            col, dens = veins
            h = (hash((part, face, px // 2)) % 97)
            if h < dens and (py + h) % 11 in (0, 1): c, gl = hx(col), True
            if (hash((part, face, py // 3)) % 89) < dens // 2 and (px + py) % 13 == 0: c, gl = lt(col, 0.3), True
        if spots:
            col, dens, glowing = spots
            if hash((part, face, px // 2, py // 2)) % 100 < dens: c, gl = hx(col), glowing
        if scars and face == "north" and (hash((part, px // 3)) % 100) < scars[1] and (px + py) % 9 == 0: c = hx(scars[0])
        if seam and part.startswith(("mao", "pe_")) and face == "down": c = dk(c, 0.2)
        return c, (255 if gl else alpha), gl
    return fn

def body_skin(g, img, glow, painter):
    paint_islands(img, glow, g.pos, painter, groups=("corpo",), parts=parts_for(g.ttype))

# ======================================================================== uniforme de Tropa (roupa_tropa_<cor>)
TROPA_COR = {
 "green":  dict(suit="#16191C", plate="#22C24E", plate2="#13893A", line="#9DFFB4", emb="verde"),
 "yellow": dict(suit="#121212", plate="#F2C800", plate2="#8A7000", line="#FFF27A", emb="amarelo"),
 "red":    dict(suit="#2A0E0C", plate="#C81E16", plate2="#6E0A06", line="#FF7A50", emb="vermelho"),
 "blue":   dict(suit="#DCE8F7", plate="#1E7BFF", plate2="#0A3A8A", line="#9CE0FF", emb="azul"),
 "violet": dict(suit="#2E0838", plate="#B03AD8", plate2="#6A1A88", line="#FF9CF2", emb="violeta"),
 "indigo": dict(suit="#140A36", plate="#4B2BBF", plate2="#2A1680", line="#CFC4FF", emb="indigo"),
 "orange": dict(suit="#2A1406", plate="#E07800", plate2="#8A4400", line="#FFD27A", emb="laranja"),
 "black":  dict(suit="#0A0A0E", plate="#26262E", plate2="#16161C", line="#E8E8FF", emb="preto"),
}

def uniform_painter(cor, small=False):
    C = TROPA_COR[cor]; S, P, P2, L = hx(C["suit"]), hx(C["plate"]), hx(C["plate2"]), hx(C["line"])
    grid = EMBLEMAS[C["emb"]]
    if small:   # corpo pequeno: o peito tem poucos pixels, então o emblema usa uma grade 5x5 (amostrada da 9x9)
        grid = ["".join(grid[r][c] for c in (0, 2, 4, 6, 8)) for r in (0, 2, 4, 6, 8)]
    N = len(grid)
    def shade(c, v, k=0.18): return mix(lt(c, 0.06), dk(c, k), v)
    def fn(part, face, u, v, px, py):
        if face == "down": return dk(S, 0.3), 255, False
        if face == "up":
            if part in ("braco_r", "braco_l", "peito", "pescoco"): return lt(P, 0.1), 255, False
            return dk(S, 0.1), 255, False
        side = face in ("east", "west")
        if part == "peito":
            if face == "north":
                # medalhão com o emblema
                ex, ey, er = 0.5, 0.42, 0.26
                if abs(u - ex) < er and abs(v - ey) < er * 1.15:
                    gx = int((u - (ex - er)) / (2 * er) * N); gy = int((v - (ey - er * 1.15)) / (2 * er * 1.15) * N)
                    if 0 <= gx < N and 0 <= gy < N and grid[gy][gx] == "#": return lt(L, 0.3), 255, True
                    edge = abs(u - ex) > er - 0.04 or abs(v - ey) > er * 1.15 - 0.05
                    return (L if edge else dk(S, 0.4)), 255, edge
                if v < 0.18: return shade(P, v), 255, False                       # ombros
                if u < 0.16 or u > 0.84: return shade(P, v), 255, False           # placas laterais
                if abs(u - 0.16) < 0.03 or abs(u - 0.84) < 0.03: return L, 255, True
                if 0.18 <= v < 0.21: return L, 255, True
                return shade(S, v, 0.1), 255, False
            if face == "south":
                if v < 0.18: return shade(P, v), 255, False
                if abs(u - 0.5) < 0.05: return L, 255, True                       # espinha luminosa
                if abs(u - 0.5) < 0.18 and v > 0.25: return shade(P2, v), 255, False
                return shade(S, v, 0.1), 255, False
            if side:
                if 0.25 < u < 0.75: return shade(P, v), 255, False
                return shade(S, v), 255, False
        if part == "abdomen":
            if face == "north" and abs(u - 0.5) < 0.05: return L, 255, True
            if face in ("north", "south") and (int(v * 4) % 2 == 0) and 0.2 < u < 0.8: return shade(P2, v), 255, False
            if side and 0.3 < u < 0.7: return shade(P, v), 255, False
            return shade(S, v, 0.1), 255, False
        if part == "pelvis":
            if v < 0.42:                                                        # cinto
                if face == "north" and abs(u - 0.5) < 0.12: return (lt(L, 0.2) if 0.08 < v < 0.34 else P), 255, 0.08 < v < 0.34
                return (dk(P2, 0.2) if v > 0.06 else P), 255, False
            return shade(S, v, 0.1), 255, False
        if part == "pescoco": return shade(dk(S, 0.1), v), 255, False
        if part.startswith("braco_"):
            if v < 0.38: return shade(P, v / 0.38), 255, False                  # ombro
            if v < 0.43: return L, 255, True
            outer = (face == "east" and part.endswith("_r")) or (face == "west" and part.endswith("_l"))
            if outer and 0.35 < u < 0.65: return shade(P2, v), 255, False
            return shade(S, v), 255, False
        if part.startswith("antebraco_"):
            if v > 0.42: return shade(P, (v - 0.42) / 0.58), 255, False         # manopla
            if v > 0.36: return L, 255, True
            return shade(S, v), 255, False
        if part.startswith("mao_"):
            if face == "north" and v < 0.35: return shade(P2, v), 255, False   # nós dos dedos
            return shade(dk(S, 0.15), v), 255, False
        if part.startswith("coxa_"):
            outer = (face == "east" and part.endswith("_r")) or (face == "west" and part.endswith("_l"))
            if outer and 0.38 < u < 0.62: return (L if abs(u - 0.5) < 0.05 else shade(P, v)), 255, abs(u - 0.5) < 0.05
            if face == "north" and v > 0.7 and 0.15 < u < 0.85: return shade(P, (v - 0.7) / 0.3), 255, False   # joelheira
            return shade(S, v), 255, False
        if part.startswith("canela_"):
            if v > 0.34: return shade(P, (v - 0.34) / 0.66), 255, False         # bota
            if v > 0.28: return L, 255, True
            if face == "north" and v < 0.28 and 0.2 < u < 0.8: return shade(P2, v), 255, False
            return shade(S, v), 255, False
        if part.startswith("pe_"):
            if v > 0.8: return dk(S, 0.2), 255, False                           # sola
            return shade(P, v), 255, False
        if part.startswith("ombreira_"):
            if face in ("north", "south", "east", "west") and v > 0.82: return L, 255, True
            return shade(P, v), 255, False
        if part == "gola":
            if v < 0.2: return L, 255, True
            return shade(P2, v), 255, False
        return None
    return fn

def uniform_texture(g, cor):
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0)); glow = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    fn = uniform_painter(cor, small=(g.ttype == "pequeno"))
    paint_islands(img, glow, g.pos, fn, groups=("corpo",), parts=parts_for(g.ttype))
    def roupa(part, face, u, v, px, py):
        if part.startswith("ombreira_") or part == "gola": return fn(part, face, u, v, px, py)
        return None
    paint_islands(img, glow, g.pos, roupa, groups=("roupa",), parts=parts_for(g.ttype))
    return img, glow

# ======================================================================== ILITH (corpo padrão · Tropa Verde)
ILITH_PELE = {"v1": "#BFD8EE", "v2": "#C9C2EE", "v3": "#B6E2DA"}

def ilith(var="v1", g=None):
    base = ILITH_PELE[var]
    A = Atlas(TEX, 32, seed=41); A.n = 32; A.eyes = []
    A.material("cranio", "skin", base, spots=dk(base, 0.12), n_spots=4)
    A.material("cranio2", "skin", dk(base, 0.1))
    A.material("dedo", "skin", lt(base, 0.05))
    A.material("unha", "solid", lt(base, 0.35), noise=2)
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, base, 3)
        for cx, sgn in ((0.28, -1), (0.72, 1)):                                    # olhos grandes, pretos, amendoados
            for y in range(S):
                for x in range(S):
                    dx = (x + 0.5) / S - cx; dy = (y + 0.5) / S - 0.48
                    rx, ry = 0.17, 0.11
                    tilt = dy - sgn * dx * 0.35
                    if (dx / rx) ** 2 + (tilt / ry) ** 2 <= 1: A.put(x0 + x, y0 + y, (8, 10, 16))
            fdisc(A, x0, y0, S, cx - sgn * 0.06, 0.44, 0.03, "#E8F2FF")
            A.eyes.append((x0, y0, S, cx, 0.48, 0.12))
        frect(A, x0, y0, S, 0.46, 0.64, 0.48, 0.70, dk(base, 0.4)); frect(A, x0, y0, S, 0.52, 0.64, 0.54, 0.70, dk(base, 0.4))   # narinas em fenda
        frect(A, x0, y0, S, 0.40, 0.80, 0.60, 0.82, dk(base, 0.35))
    A.decal("rosto", face)
    new = g is None
    if new: g = build_body("padrao", "especie_ilith", atlas_seed=41, skin_atlas=A)
    else: g.A = A
    if new:
        P = layout_points("padrao"); hb = P["head"][0]; hw, hh, hd = TYPES["padrao"]["head"]
        g.soft = {"cranio": 0.8, "cranio2": 0.6}
        g.box("head", (-hw / 2 + 0.3, hb, -hd / 2), (hw / 2 - 0.3, hb + hh, hd / 2), "cranio", decals={"north": "rosto"})
        g.box("head", (-hw / 2 + 0.6, hb - 0.4, -hd / 2 + 0.4), (hw / 2 - 0.6, hb + 1.6, hd / 2 - 1.0), "cranio2")      # queixo fino
        g.bone("extra_cabeca_lobulos", (0, hb + hh * 0.6, hd / 2 - 1), "head")
        L = "extra_cabeca_lobulos"; py, pz = hb + hh * 0.66, hd / 2 - 2.6
        for s in (-1, 1):                                                         # dois lóbulos alongados para trás e para cima
            x = s * 1.72
            g.box(L, (x - 1.62, py - 1.9, pz), (x + 1.62, py + 2.3, pz + 7.4), "cranio", rot=(-12, s * 7, 0), pivot=(x, py, pz))
            g.box(L, (x - 1.25, py - 1.3, pz + 7.0), (x + 1.25, py + 1.8, pz + 9.6), "cranio2", rot=(-12, s * 7, 0), pivot=(x, py, pz))
            g.box(L, (x - 0.8, py - 0.8, pz + 9.4), (x + 0.8, py + 1.2, pz + 10.6), "cranio2", rot=(-12, s * 7, 0), pivot=(x, py, pz))
        for s, sd in ((-1, "r"), (1, "l")):                                        # dedos longos
            hx_ = s * P["arm_x"]; y0 = P["hand"][0]; hw2, hh2, hd2 = TYPES["padrao"]["hand"]
            nm = g.bone(f"extra_corpo_dedos_{sd}", (hx_, y0, 0), f"hand_{sd}")
            for i in range(3):
                z = -hd2 / 2 + 0.45 + i * 0.95
                g.box(nm, (hx_ - 0.32 + s * 0.3, y0 - 2.6, z - 0.3), (hx_ + 0.32 + s * 0.3, y0 + 0.1, z + 0.3), "dedo")
                g.box(nm, (hx_ - 0.28 + s * 0.3, y0 - 3.0, z - 0.26), (hx_ + 0.28 + s * 0.3, y0 - 2.6, z + 0.26), "unha")
            g.box(nm, (hx_ - s * 1.5 - 0.3, y0 - 1.4, -0.8), (hx_ - s * 1.5 + 0.3, y0 + 0.6, -0.2), "dedo")
    img = A.img; glow = A.glow
    body_skin(g, img, glow, skin_painter(base, seed=41))
    extra = {"misc.extra": {"loop": True, "animation_length": 3, "bones": {
        "extra_corpo_dedos_r": {"rotation": ["math.sin(query.anim_time * 120) * 4", 0, 0]},
        "extra_corpo_dedos_l": {"rotation": ["math.sin(query.anim_time * 120 + 90) * 4", 0, 0]}}}}
    return g, img, glow, extra

# ======================================================================== KORRVAAL (corpo grande · Tropa Verde, instrutores)
KORR_PELE = {"v1": "#6E7C8C", "v2": "#56606C", "v3": "#857F72"}
KORR_CRISTAL = "#9DFFB4"

def korrvaal(var="v1", g=None):
    base = KORR_PELE[var]
    A = Atlas(TEX, 32, seed=42); A.n = 32; A.eyes = []
    A.material("rocha", "stone", base, dk(base, 0.35))
    A.material("rocha2", "plates", dk(base, 0.12), dk(base, 0.45), step=8)
    A.material("cristal", "crystal", KORR_CRISTAL, alpha=255, glowing=True)
    A.material("cristal2", "glow", "#39FF6A", "#E0FFE8")
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, base, 4)
        for cx in (0.3, 0.7):                                                     # olhos pequenos e brilhantes
            fdisc(A, x0, y0, S, cx, 0.46, 0.065, dk(base, 0.5)); fdisc(A, x0, y0, S, cx, 0.46, 0.04, "#C8FFD8", True)
            A.eyes.append((x0, y0, S, cx, 0.46, 0.07))
        frect(A, x0, y0, S, 0.30, 0.74, 0.70, 0.76, dk(base, 0.45))                # boca larga e serena (sem nariz)
        frect(A, x0, y0, S, 0.27, 0.72, 0.30, 0.74, dk(base, 0.45)); frect(A, x0, y0, S, 0.70, 0.72, 0.73, 0.74, dk(base, 0.45))
        for x in (0.12, 0.88): frect(A, x0, y0, S, x - 0.015, 0.2, x + 0.015, 0.62, KORR_CRISTAL, True)   # veios nas têmporas
    A.decal("rosto", face)
    new = g is None
    if new: g = build_body("grande", "especie_korrvaal", atlas_seed=42, skin_atlas=A)
    else: g.A = A
    if new:
        P = layout_points("grande"); hb = P["head"][0]; hw, hh, hd = TYPES["grande"]["head"]
        g.soft = {"rocha": 0.9, "rocha2": 0.5}
        g.box("head", (-hw / 2 - 0.6, hb, -hd / 2), (hw / 2 + 0.6, hb + hh, hd / 2), "rocha", decals={"north": "rosto"})   # rosto largo
        g.box("head", (-hw / 2 - 0.2, hb - 0.6, -hd / 2 + 0.6), (hw / 2 + 0.2, hb + 1.4, hd / 2 - 0.6), "rocha2")          # mandíbula larga
        g.bone("extra_cabeca_placas", (0, hb + hh, 0), "head")
        X = "extra_cabeca_placas"
        g.box(X, (-hw / 2 - 0.8, hb + hh * 0.66, -hd / 2 - 0.7), (hw / 2 + 0.8, hb + hh * 0.8, -hd / 2 + 1.2), "rocha2")    # sobrancelha de placa
        for i, (x0, x1, h, z0, z1) in enumerate(((-4.2, -1.4, 1.2, -3.6, 1.2), (-1.3, 1.3, 1.8, -3.2, 2.6), (1.4, 4.2, 1.2, -3.6, 1.2), (-3.4, 3.4, 1.0, 1.2, 4.6))):
            g.box(X, (x0, hb + hh - 0.2, z0), (x1, hb + hh + h, z1), "rocha2")                                              # placas da coroa
        for s in (-1, 1):
            g.box(X, (s * (hw / 2 + 0.6) - 0.5, hb + 1.6, -hd / 2 + 0.8), (s * (hw / 2 + 0.6) + 0.5, hb + hh * 0.62, hd / 2 - 1.6), "rocha2")   # placas das bochechas
            g.cbox(X, (s * 2.6, hb + hh + 1.0, 2.2), (0.9, 2.2, 0.9), "cristal", rot=(18 * s, 45, 0))
        # cristais nas costas e ombros
        g.bone("extra_corpo_cristais", (0, P["chest"][1], 0), "body")
        Y = "extra_corpo_cristais"; cd = TYPES["grande"]["chest"][2]
        for (x, y, z, h, rx, rz) in ((-4.4, P["chest"][1] - 1.0, cd / 2, 4.4, 25, -15), (-2.0, P["chest"][1] - 2.5, cd / 2, 3.2, 35, -5), (3.0, P["chest"][1] - 1.6, cd / 2, 5.0, 30, 12),
                                     (5.0, P["chest"][1] - 4.0, cd / 2, 2.8, 40, 20), (0.8, P["chest"][1] - 5.2, cd / 2, 2.4, 45, 0)):
            g.box(Y, (x - 0.55, y, z - 0.55), (x + 0.55, y + h, z + 0.55), "cristal", rot=(rx, 0, rz), pivot=(x, y, z))
            g.box(Y, (x - 0.28, y + h, z - 0.28), (x + 0.28, y + h + 0.9, z + 0.28), "cristal2", rot=(rx, 0, rz), pivot=(x, y, z))
        for s in (-1, 1):
            x = s * (P["arm_x"] - 0.6)
            for j, (dx, h) in enumerate(((-0.8, 2.6), (0.5, 3.4), (1.4, 2.0))):
                g.box(Y, (x + dx * s - 0.45, P["chest"][1] + 0.4, -0.45 + j * 0.6), (x + dx * s + 0.45, P["chest"][1] + 0.4 + h, 0.45 + j * 0.6), "cristal",
                      rot=(0, 0, -s * (10 + j * 12)), pivot=(x + dx * s, P["chest"][1] + 0.4, j * 0.6))
    img = A.img; glow = A.glow
    body_skin(g, img, glow, skin_painter(base, shade=0.25, seed=42, plates=(10, 0.28), veins=(KORR_CRISTAL, 9)))
    extra = {"misc.extra": {"loop": True, "animation_length": 4, "bones": {
        "extra_corpo_cristais": {"scale": ["1 + math.max(0, math.sin(query.anim_time * 90)) * 0.04"] * 3}}}}
    return g, img, glow, extra

SPECIES = {"ilith": ("padrao", ilith, ILITH_PELE), "korrvaal": ("grande", korrvaal, KORR_PELE)}

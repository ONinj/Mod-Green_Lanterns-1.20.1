import json, uuid, base64, io, math, os, random
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = "EspectroEmocional"
RES = 64  # textura 64x64, 16 amostras de 16x16

# ---------------------------------------------------------------- paletas
P = {
 "verde":    dict(tropa="Tropa da Vontade",   aspecto="Vontade",   main="#1FCB4A", dark="#0B4F1E", light="#8CFF9E", glow="#39FF6A", bright="#D9FFE0", accent="#15191D", gem="#7DFF9A", mark="#0A2E14", ring="ridges",  top="dome"),
 "vermelho": dict(tropa="Tropa da Fúria",     aspecto="Raiva",     main="#E0201B", dark="#5E0907", light="#FF7A6B", glow="#FF2A1A", bright="#FFD2CC", accent="#2A0000", gem="#FF5A3C", mark="#3A0504", ring="spikes",  top="spikes"),
 "laranja":  dict(tropa="Tropa da Avareza",   aspecto="Avareza",   main="#FF8A00", dark="#6E3300", light="#FFC870", glow="#FFA21F", bright="#FFE9C4", accent="#C9A227", gem="#FFD84A", mark="#4A2200", ring="gems",    top="coin"),
 "amarelo":  dict(tropa="Tropa do Medo",      aspecto="Medo",      main="#FFD60A", dark="#5E4D00", light="#FFF59A", glow="#FFE53B", bright="#FFFBD6", accent="#1A1A1A", gem="#FFF27A", mark="#2E2600", ring="blades",  top="crown"),
 "azul":     dict(tropa="Tropa da Esperança", aspecto="Esperança", main="#1E7BFF", dark="#0A2A66", light="#9CCBFF", glow="#3EA0FF", bright="#DDEEFF", accent="#E8F2FF", gem="#7FC4FF", mark="#06193D", ring="wings",   top="wings"),
 "indigo":   dict(tropa="Tropa da Compaixão", aspecto="Compaixão", main="#4B2BBF", dark="#1C0E52", light="#9C87FF", glow="#6A4BFF", bright="#E0D9FF", accent="#2C2C3A", gem="#B19CFF", mark="#120836", ring="wings",   top="staff"),
 "violeta":  dict(tropa="Tropa do Amor",      aspecto="Amor",      main="#C02BE0", dark="#4E0B63", light="#F29CFF", glow="#E04BFF", bright="#FFE0FF", accent="#FFD6F5", gem="#FF7AE8", mark="#33063F", ring="crystals",top="crystal"),
 "preto":    dict(tropa="Tropa da Morte",     aspecto="Morte",     main="#22222C", dark="#08080C", light="#55556A", glow="#3C3C55", bright="#D8D8F0", accent="#8A8AA8", gem="#9A9AC8", mark="#D8D8F0", ring="spikes",  top="obelisk"),
 "branco":   dict(tropa="Tropa da Vida",      aspecto="Vida",      main="#F2F2F2", dark="#9AA3AD", light="#FFFFFF", glow="#FFFFFF", bright="#FFFFFF", accent="#E8C670", gem="#FFE9A8", mark="#B8902F", ring="halo",    top="halo"),
}

# ---------------------------------------------------------------- emblemas originais (9x9)
E = {
 "verde": [  # dupla seta para cima: força de vontade que avança
  "....#....","...###...","..##.##..",".##...##.","##..#..##","...###...","..##.##..",".##...##.","##.....##"],
 "vermelho": [  # chama de três línguas
  "....#....",".#..#..#.",".#.###.#.",".#######.","#########","####.####",".###.###.","..#####..","...###..."],
 "laranja": [  # gema lapidada / tesouro
  "...###...","..#####..",".#.#.#.#.","#########",".#######.","..#####..","...###...","....#....","........."],
 "amarelo": [  # olho de pupila em fenda
  ".........","...###...",".##...##.","#...#...#","#..###..#","#...#...#",".##...##.","...###...","........."],
 "azul": [  # estrela-guia
  "....#....","....#....",".#.###.#.","..#####..","#########","..#####..",".#.###.#.","....#....","....#...."],
 "indigo": [  # mãos que acolhem uma chama
  "....#....","...###...","...###...","....#....","#.......#","##.....##",".##...##.","..#####..","...###..."],
 "violeta": [  # coração com gema
  ".##...##.","#########","####.####","###...###","####.####",".#######.","..#####..","...###...","....#...."],
 "preto": [  # crânio estilizado
  "..#####..",".#######.","##..#..##","##..#..##","#########",".###.###.","..#.#.#..","..#####..","........."],
 "branco": [  # ankh / símbolo da vida
  "...###...","..#...#..","..#...#..","...###...","#########","....#....","....#....","....#....","...###..."],
}

SW = ["main", "dark", "light", "glow", "bright", "accent", "gem", "mark"]
KIND = dict(main="metal", dark="metal", light="metal", glow="glow", bright="glow", accent="metal", gem="glow", mark="flat")

def hx(c):
    c = c.lstrip("#"); return tuple(int(c[i:i+2], 16) for i in (0, 2, 4))

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

# ---------------------------------------------------------------- textura
def make_texture(pal, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (RES, RES), (0, 0, 0, 0))
    px = img.load()
    for i, name in enumerate(SW):
        ox, oy = (i % 4) * 16, (i // 4) * 16
        base = hx(pal[name]); kind = KIND[name]
        for y in range(16):
            for x in range(16):
                if kind == "glow":
                    d = math.hypot(x - 7.5, y - 7.5) / 10.6
                    c = mix((255, 255, 255), base, min(1, 0.25 + d * 0.95))
                else:
                    t = y / 15
                    c = mix(mix(base, (255, 255, 255), 0.22), mix(base, (0, 0, 0), 0.30), t)
                    if kind == "metal" and abs((x + y) - 13) <= 1:
                        c = mix(c, (255, 255, 255), 0.18)  # brilho diagonal
                    n = rnd.randint(-6, 6); c = tuple(max(0, min(255, v + n)) for v in c)
                    if x == 0 or y == 0: c = mix(c, (255, 255, 255), 0.30)
                    if x == 15 or y == 15: c = mix(c, (0, 0, 0), 0.35)
                px[ox + x, oy + y] = c + (255,)
    return img

def swatch_uv(name):
    i = SW.index(name); ox, oy = (i % 4) * 16, (i // 4) * 16
    return [ox, oy, ox + 16, oy + 16]

def swatch_avg(img, name):
    i = SW.index(name); ox, oy = (i % 4) * 16, (i // 4) * 16
    return img.crop((ox, oy, ox + 16, oy + 16)).resize((1, 1), Image.BOX).getpixel((0, 0))[:3]

# ---------------------------------------------------------------- elementos
def U(): return str(uuid.uuid4())

class Model:
    def __init__(self): self.els = []; self.groups = []
    def group(self, name, origin=(8, 8, 8)):
        g = dict(name=name, origin=list(origin), color=0, uuid=U(), export=True, mirror_uv=False,
                 isOpen=True, locked=False, visibility=True, autouv=0, children=[])
        self.groups.append(g); return g
    def cube(self, g, name, frm, to, sw, rot=None, origin=None, glow=False):
        r = [0, 0, 0]
        if rot: r["xyz".index(rot[0])] = rot[1]
        if origin is None: origin = [(frm[i] + to[i]) / 2 for i in range(3)]
        e = dict(name=name, box_uv=False, rescale=False, locked=False, light_emission=15 if glow else 0,
                 render_order="default", allow_mirror_modeling=True,
                 **{"from": [round(v, 4) for v in frm]}, to=[round(v, 4) for v in to], autouv=0, color=0,
                 origin=[round(v, 4) for v in origin], rotation=r,
                 faces={f: dict(uv=swatch_uv(sw), texture=0) for f in ["north", "east", "south", "west", "up", "down"]},
                 type="cube", uuid=U(), _sw=sw, _glow=glow)
        self.els.append(e); g["children"].append(e["uuid"]); return e

def emblem_runs(grid):
    """junta '#' consecutivos de cada linha num só cubo"""
    out = []
    for r, row in enumerate(grid):
        c = 0
        while c < 9:
            if row[c] == "#":
                s = c
                while c < 9 and row[c] == "#": c += 1
                out.append((r, s, c))
            else: c += 1
    return out

# ---------------------------------------------------------------- anel
def build_ring(key, pal):
    m = Model()
    z0, z1, ri, t, s = 6.75, 9.25, 2.5, 1.0, 2.6
    aro = m.group("aro")
    top = ([8 - s/2, 8 + ri, z0], [8 + s/2, 8 + ri + t, z1])
    bot = ([8 - s/2, 8 - ri - t, z0], [8 + s/2, 8 - ri, z1])
    m.cube(aro, "aro_base", *bot, "main")
    m.cube(aro, "aro_esq", [8 - ri - t, 8 - s/2, z0], [8 - ri, 8 + s/2, z1], "main")
    m.cube(aro, "aro_dir", [8 + ri, 8 - s/2, z0], [8 + ri + t, 8 + s/2, z1], "main")
    for a in (45, -45):
        m.cube(aro, f"aro_diag_inf_{a}", *bot, "main", ("z", a), [8, 8, 8])
        m.cube(aro, f"aro_diag_sup_{a}", *top, "main", ("z", a), [8, 8, 8])
    # friso luminoso no meio do aro
    fr = m.group("friso")
    fb = ([8 - s/2, 8 - ri - t - 0.2, 7.75], [8 + s/2, 8 - ri - t + 0.01, 8.25])
    m.cube(fr, "friso_base", *fb, "glow", glow=True)
    for a in (45, -45):
        m.cube(fr, f"friso_{a}", *fb, "glow", ("z", a), [8, 8, 8], glow=True)
    m.cube(fr, "friso_esq", [8 - ri - t - 0.2, 8 - s/2, 7.75], [8 - ri - t + 0.01, 8 + s/2, 8.25], "glow", glow=True)
    m.cube(fr, "friso_dir", [8 + ri + t - 0.01, 8 - s/2, 7.75], [8 + ri + t + 0.2, 8 + s/2, 8.25], "glow", glow=True)
    # ombros
    om = m.group("ombros")
    m.cube(om, "ombro_esq", [5.9, 10.4, 6.4], [7.0, 11.6, 9.6], "light")
    m.cube(om, "ombro_dir", [9.0, 10.4, 6.4], [10.1, 11.6, 9.6], "light")
    # sinete
    si = m.group("sinete", (8, 12, 8))
    m.cube(si, "base", [5, 11.5, 5], [11, 12.2, 11], "accent")
    m.cube(si, "borda_n", [5, 12.2, 5], [11, 12.9, 5.6], "main")
    m.cube(si, "borda_s", [5, 12.2, 10.4], [11, 12.9, 11], "main")
    m.cube(si, "borda_o", [5, 12.2, 5.6], [5.6, 12.9, 10.4], "main")
    m.cube(si, "borda_l", [10.4, 12.2, 5.6], [11, 12.9, 10.4], "main")
    m.cube(si, "placa", [5.6, 12.2, 5.6], [10.4, 12.45, 10.4], "dark")
    em = m.group("emblema", (8, 12.6, 8))
    cell = 0.5
    for r, a, b in emblem_runs(E[key]):
        z_hi = 10.25 - r * cell
        m.cube(em, f"emb_{r}_{a}", [10.25 - b * cell, 12.45, z_hi - cell], [10.25 - a * cell, 12.75, z_hi], "bright", glow=True)
    # decoração por cor
    d = m.group("detalhes")
    st = pal["ring"]
    if st == "ridges":
        for x0 in (8 - ri - t - 0.15, 8 + ri + t - 0.1):
            for zz in (z0 - 0.15, z1 - 0.1):
                m.cube(d, "trilho", [x0, 8 - s/2, zz], [x0 + 0.25, 8 + s/2, zz + 0.25], "light")
        m.cube(d, "trilho_b1", [8 - s/2, 8 - ri - t - 0.15, z0 - 0.15], [8 + s/2, 8 - ri - t + 0.1, z0 + 0.1], "light")
        m.cube(d, "trilho_b2", [8 - s/2, 8 - ri - t - 0.15, z1 - 0.1], [8 + s/2, 8 - ri - t + 0.1, z1 + 0.15], "light")
    if st == "spikes":
        for x in (4.7, 10.3):
            for z in (4.7, 10.3):
                m.cube(d, "espinho", [x - 0.45, 11.5, z - 0.45], [x + 0.45, 13.4, z + 0.45], "light", ("y", 45))
        for x0, x1 in ((8 - ri - t - 1.1, 8 - ri - t + 0.2), (8 + ri + t - 0.2, 8 + ri + t + 1.1)):
            m.cube(d, "garra", [x0, 7.45, 7.45], [x1, 8.55, 8.55], "light", ("x", 45))
    if st == "gems":
        for x in (5.3, 10.7):
            for z in (5.3, 10.7):
                m.cube(d, "gema", [x - 0.55, 12.4, z - 0.55], [x + 0.55, 13.5, z + 0.55], "gem", ("y", 45), glow=True)
    if st == "blades":
        m.cube(d, "lamina_l", [10.6, 11.2, 7.2], [13.4, 11.9, 8.8], "light", ("z", 22.5), [10.6, 11.5, 8])
        m.cube(d, "lamina_o", [2.6, 11.2, 7.2], [5.4, 11.9, 8.8], "light", ("z", -22.5), [5.4, 11.5, 8])
        m.cube(d, "ponta_n", [7.4, 11.6, 3.6], [8.6, 12.4, 5.0], "light", ("y", 45))
        m.cube(d, "ponta_s", [7.4, 11.6, 11.0], [8.6, 12.4, 12.4], "light", ("y", 45))
    if st == "wings":
        for sgn in (-1, 1):
            xa = 11 if sgn > 0 else 5
            for k, (ln, zz) in enumerate(((2.4, 6.3), (1.8, 7.5), (1.2, 8.7))):
                x0, x1 = (xa, xa + ln) if sgn > 0 else (xa - ln, xa)
                m.cube(d, f"asa_{k}", [x0, 11.6, zz], [x1, 12.0, zz + 1.0], "light", ("z", 22.5 * sgn), [xa, 11.8, 8])
    if st == "crystals":
        for x, z in ((5.0, 8.0), (11.0, 8.0), (8.0, 5.0), (8.0, 11.0)):
            m.cube(d, "cristal", [x - 0.6, 12.0, z - 0.6], [x + 0.6, 13.8, z + 0.6], "gem", ("y", 45), glow=True)
    if st == "halo":
        hb = [(4.4, 4.4, 11.6, 4.7), (4.4, 11.3, 11.6, 11.6), (4.4, 4.7, 4.7, 11.3), (11.3, 4.7, 11.6, 11.3)]
        for x0, z0_, x1, z1_ in hb:
            m.cube(d, "aureola", [x0, 12.3, z0_], [x1, 12.55, z1_], "gem", glow=True)
    return m

# ---------------------------------------------------------------- bateria
def build_battery(key, pal):
    m = Model()
    b = m.group("base", (8, 0, 8))
    m.cube(b, "pedestal", [1, 0, 1], [15, 1.5, 15], "dark")
    m.cube(b, "degrau", [2, 1.5, 2], [14, 3, 14], "main")
    for x in (1, 14):
        for z in (1, 14):
            m.cube(b, "pe", [x - 0.25, 0, z - 0.25], [x + 1.25, 2, z + 1.25], "light")
    c = m.group("nucleo")
    m.cube(c, "nucleo", [4, 3, 4], [12, 11.5, 12], "glow", glow=True)
    m.cube(c, "anel_inf", [3.8, 3, 3.8], [12.2, 3.5, 12.2], "accent")
    m.cube(c, "anel_sup", [3.8, 11, 3.8], [12.2, 11.5, 12.2], "accent")
    p = m.group("pilares")
    for x in (2.5, 12):
        for z in (2.5, 12):
            m.cube(p, "pilar", [x, 3, z], [x + 1.5, 11.5, z + 1.5], "main")
            m.cube(p, "friso", [x - 0.1, 6.9, z - 0.1], [x + 1.6, 7.6, z + 1.6], "light")
    t = m.group("tampa")
    m.cube(t, "tampa", [2, 11.5, 2], [14, 12.5, 14], "main")
    m.cube(t, "tampa2", [3, 12.5, 3], [13, 13.5, 13], "dark")
    em = m.group("emblema")
    cell = 0.75
    ytop = 10.55
    for r, a, bb in emblem_runs(E[key]):
        y1 = ytop - r * cell
        # frente (norte): x espelhado para ler corretamente
        m.cube(em, f"emb_n_{r}_{a}", [11.375 - bb * cell, y1 - cell, 3.6], [11.375 - a * cell, y1, 4.0], "mark")
        m.cube(em, f"emb_s_{r}_{a}", [4.625 + a * cell, y1 - cell, 12.0], [4.625 + bb * cell, y1, 12.4], "mark")
    T = 13.5
    d = m.group("topo", (8, T, 8))
    st = pal["top"]
    if st == "dome":
        m.cube(d, "cupula1", [4, T, 4], [12, T + 1, 12], "main")
        m.cube(d, "cupula2", [5.5, T + 1, 5.5], [10.5, T + 2, 10.5], "light")
        m.cube(d, "alca_o", [6, T + 2, 7.5], [7, T + 3.5, 8.5], "main")
        m.cube(d, "alca_l", [9, T + 2, 7.5], [10, T + 3.5, 8.5], "main")
        m.cube(d, "alca", [6, T + 3.5, 7.5], [10, T + 4.5, 8.5], "main")
    if st == "spikes":
        for x in (3.2, 12.8):
            for z in (3.2, 12.8):
                m.cube(d, "espinho", [x - 0.6, T, z - 0.6], [x + 0.6, T + 3.5, z + 0.6], "light", ("y", 45))
        for k, (w, h) in enumerate(((3.0, 1.5), (2.2, 1.5), (1.4, 1.5), (0.7, 1.2))):
            y0 = T + sum(hh for _, hh in ((3.0, 1.5), (2.2, 1.5), (1.4, 1.5))[:k])
            m.cube(d, f"chama_{k}", [8 - w, y0, 8 - w], [8 + w, y0 + h, 8 + w], "glow", ("y", 45 if k % 2 else 0), glow=True)
    if st == "coin":
        y = T
        for k, w in enumerate((4.5, 3.8, 3.0)):
            for rot in (0, 45):
                m.cube(d, f"moeda_{k}", [8 - w, y, 8 - w], [8 + w, y + 0.8, 8 + w], "accent" if k % 2 else "main", ("y", rot) if rot else None)
            y += 0.8
        m.cube(d, "gema", [6.8, y, 6.8], [9.2, y + 2.4, 9.2], "gem", ("y", 45), glow=True)
    if st == "crown":
        m.cube(d, "aro", [4, T, 4], [12, T + 0.8, 12], "main")
        for k in range(8):
            ang = k * 45
            rad = math.radians(ang)
            cx, cz = 8 + 3.6 * math.sin(rad), 8 + 3.6 * math.cos(rad)
            sx, cz_ = math.sin(rad), math.cos(rad)
            if abs(cz_) >= abs(sx): axis, a = "x", 22.5 if cz_ > 0 else -22.5
            else: axis, a = "z", -22.5 if sx > 0 else 22.5
            m.cube(d, f"ponta_{k}", [cx - 0.5, T + 0.8, cz - 0.5], [cx + 0.5, T + 3.6, cz + 0.5], "light", (axis, a) if a else None, [cx, T + 0.8, cz])
        m.cube(d, "joia", [7, T + 0.8, 7], [9, T + 2.8, 9], "gem", ("y", 45), glow=True)
    if st == "wings":
        m.cube(d, "cupula", [5, T, 5], [11, T + 1.5, 11], "main")
        m.cube(d, "orbe", [6.8, T + 1.5, 6.8], [9.2, T + 3.9, 9.2], "gem", glow=True)
        for sgn in (-1, 1):
            xa = 11 if sgn > 0 else 5
            for k, (ln, yy) in enumerate(((4.5, 0.2), (3.6, 1.2), (2.6, 2.2))):
                x0, x1 = (xa, xa + ln) if sgn > 0 else (xa - ln, xa)
                m.cube(d, f"asa_{k}", [x0, T + yy, 7.4], [x1, T + yy + 0.8, 8.6], "light", ("z", 22.5 * sgn), [xa, T, 8])
    if st == "staff":
        m.cube(d, "cupula", [5, T, 5], [11, T + 1, 11], "main")
        m.cube(d, "haste", [7.3, T + 1, 7.3], [8.7, T + 5, 8.7], "light")
        m.cube(d, "orbe", [6.4, T + 5, 6.4], [9.6, T + 8.2, 9.6], "gem", ("y", 45), glow=True)
        for sgn in (-1, 1):
            m.cube(d, "lua", [8 + sgn * 1.7 - 0.4, T + 4.5, 7.6], [8 + sgn * 1.7 + 0.4, T + 7.5, 8.4], "main", ("z", -22.5 * sgn), [8 + sgn * 1.7, T + 4.5, 8])
    if st == "crystal":
        m.cube(d, "base_cristal", [5, T, 5], [11, T + 0.8, 11], "main")
        m.cube(d, "cristal1", [5.5, T + 0.8, 5.5], [10.5, T + 3.8, 10.5], "gem", ("y", 45), glow=True)
        m.cube(d, "cristal2", [6.5, T + 3.8, 6.5], [9.5, T + 6.3, 9.5], "gem", ("y", 45), glow=True)
        m.cube(d, "cristal3", [7.3, T + 6.3, 7.3], [8.7, T + 7.8, 8.7], "bright", ("y", 45), glow=True)
        for x, z in ((4.6, 8), (11.4, 8), (8, 4.6), (8, 11.4)):
            m.cube(d, "lasca", [x - 0.5, T + 0.8, z - 0.5], [x + 0.5, T + 2.6, z + 0.5], "gem", ("y", 45), glow=True)
    if st == "obelisk":
        y = T
        for k, (w, h) in enumerate(((3.5, 1.2), (2.6, 2.4), (1.9, 2.4), (1.2, 1.6))):
            m.cube(d, f"obelisco_{k}", [8 - w, y, 8 - w], [8 + w, y + h, 8 + w], "main" if k % 2 == 0 else "dark")
            y += h
        m.cube(d, "ponta", [7.3, y, 7.3], [8.7, y + 1.4, 8.7], "bright", ("y", 45), glow=True)
    if st == "halo":
        m.cube(d, "cupula", [5, T, 5], [11, T + 1.2, 11], "main")
        m.cube(d, "orbe", [6.9, T + 1.2, 6.9], [9.1, T + 3.4, 9.1], "bright", glow=True)
        r, w, y = 4.2, 0.4, T + 4.4
        sideln = 2 * r * math.tan(math.radians(22.5)) + 0.1
        for rot in (0, 45):
            m.cube(d, "aureola", [8 - sideln/2, y, 8 - r - w/2], [8 + sideln/2, y + 0.35, 8 - r + w/2], "gem", ("y", rot) if rot else None, [8, y, 8], glow=True)
            m.cube(d, "aureola", [8 - sideln/2, y, 8 + r - w/2], [8 + sideln/2, y + 0.35, 8 + r + w/2], "gem", ("y", rot) if rot else None, [8, y, 8], glow=True)
            m.cube(d, "aureola", [8 - r - w/2, y, 8 - sideln/2], [8 - r + w/2, y + 0.35, 8 + sideln/2], "gem", ("y", rot) if rot else None, [8, y, 8], glow=True)
            m.cube(d, "aureola", [8 + r - w/2, y, 8 - sideln/2], [8 + r + w/2, y + 0.35, 8 + sideln/2], "gem", ("y", rot) if rot else None, [8, y, 8], glow=True)
    return m

# ---------------------------------------------------------------- export bbmodel
RING_DISPLAY = {
 "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 1], "scale": [0.45, 0.45, 0.45]},
 "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 1], "scale": [0.45, 0.45, 0.45]},
 "firstperson_righthand": {"rotation": [10, -30, 0], "translation": [2, 3, 0], "scale": [0.5, 0.5, 0.5]},
 "firstperson_lefthand": {"rotation": [10, -30, 0], "translation": [2, 3, 0], "scale": [0.5, 0.5, 0.5]},
 "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
 "gui": {"rotation": [35, 30, 0], "translation": [0, -1.5, 0], "scale": [1.2, 1.2, 1.2]},
 "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -3], "scale": [1, 1, 1]},
 "head": {"translation": [0, 6, 0]},
}
BAT_DISPLAY = {
 "gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
 "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
 "fixed": {"scale": [0.5, 0.5, 0.5]},
 "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
 "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.35, 0.35, 0.35]},
 "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.35, 0.35, 0.35]},
}

def save_bbmodel(m, name, tex_img, tex_name, path, display):
    buf = io.BytesIO(); tex_img.save(buf, "PNG")
    els = [{k: v for k, v in e.items() if not k.startswith("_")} for e in m.els]
    data = {
        "meta": {"format_version": "4.5", "model_format": "java_block", "box_uv": False},
        "name": name, "parent": "", "ambientocclusion": True, "front_gui_light": False,
        "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "unhandled_root_fields": {},
        "resolution": {"width": RES, "height": RES},
        "elements": els,
        "outliner": m.groups,
        "textures": [{
            "path": "", "name": tex_name, "folder": "", "namespace": "", "id": "0",
            "width": RES, "height": RES, "uv_width": RES, "uv_height": RES,
            "particle": True, "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
            "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
            "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True,
            "saved": False, "uuid": U(), "relative_path": "../texturas/" + tex_name,
            "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
        "display": display,
    }
    with open(path, "w", encoding="utf-8") as f: json.dump(data, f, ensure_ascii=False, indent=1)

# ---------------------------------------------------------------- renderizador de prévia
def rot_point(p, axis, ang, o):
    if not ang: return p
    a = math.radians(ang); c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    if axis == 0: y, z = y * c - z * s, y * s + z * c
    if axis == 1: x, z = x * c + z * s, -x * s + z * c
    if axis == 2: x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])

def render(m, tex, size=420, yaw=30, pitch=28):
    yw, pt = math.radians(yaw), math.radians(pitch)
    dvec = (math.cos(pt) * math.sin(yw), math.sin(pt), -math.cos(pt) * math.cos(yw))
    fwd = tuple(-v for v in dvec)
    def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
    def norm(a): l = math.sqrt(sum(v*v for v in a)); return tuple(v/l for v in a)
    right = norm(cross(fwd, (0, 1, 0))); up = cross(right, fwd)
    dot = lambda a, b: sum(a[i]*b[i] for i in range(3))
    light = norm((0.4, 0.9, -0.5))
    polys = []
    FACES = [((0,0,0),(0,1,0),(1,1,0),(1,0,0)), ((0,0,1),(1,0,1),(1,1,1),(0,1,1)),
             ((0,0,0),(0,0,1),(0,1,1),(0,1,0)), ((1,0,0),(1,1,0),(1,1,1),(1,0,1)),
             ((0,1,0),(0,1,1),(1,1,1),(1,1,0)), ((0,0,0),(1,0,0),(1,0,1),(0,0,1))]
    cols = {n: swatch_avg(tex, n) for n in SW}
    for e in m.els:
        f, t = e["from"], e["to"]; ax = next((i for i in range(3) if e["rotation"][i]), 0)
        ang = e["rotation"][ax]
        for face in FACES:
            pts = [rot_point(tuple(t[i] if c[i] else f[i] for i in range(3)), ax, ang, e["origin"]) for c in face]
            n = norm(cross(tuple(pts[1][i]-pts[0][i] for i in range(3)), tuple(pts[2][i]-pts[0][i] for i in range(3))))
            cen = tuple(sum(p[i] for p in pts) / 4 for i in range(3))
            if dot(n, tuple(cen[i]-8 for i in range(3))) < 0: n = tuple(-v for v in n)
            if dot(n, fwd) >= 0: continue
            base = cols[e["_sw"]]
            k = 1.0 if e["_glow"] else 0.45 + 0.6 * max(0, dot(n, light))
            col = tuple(min(255, int(v * k)) for v in base)
            polys.append((dot(cen, fwd), [(dot(p, right), -dot(p, up)) for p in pts], col, e["_glow"]))
    xs = [x for _, pp, _, _ in polys for x, _ in pp]; ys = [y for _, pp, _, _ in polys for _, y in pp]
    sc = (size * 0.84) / max(max(xs) - min(xs), max(ys) - min(ys))
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glowl = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dr, dg = ImageDraw.Draw(img), ImageDraw.Draw(glowl)
    for _, pp, col, gl in sorted(polys, key=lambda q: -q[0]):
        sp = [((x - cx) * sc + size / 2, (y - cy) * sc + size / 2) for x, y in pp]
        dr.polygon(sp, fill=col + (255,), outline=tuple(int(v * 0.6) for v in col) + (255,))
        if gl: dg.polygon(sp, fill=col + (200,))
    glowl = glowl.filter(ImageFilter.GaussianBlur(size / 30))
    out = Image.alpha_composite(glowl, img)
    return out

# ---------------------------------------------------------------- logos
def font(sz):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"]:
        if os.path.exists(p): return ImageFont.truetype(p, sz)
    return ImageFont.load_default()

def logo_svg(key, pal, with_text=True):
    h = 640 if with_text else 512
    cell, x0, y0 = 30, 256 - 135, 256 - 135
    rects = "".join(f'<rect x="{x0 + c*cell}" y="{y0 + r*cell}" width="{cell+0.5}" height="{cell+0.5}"/>'
                    for r, row in enumerate(E[key]) for c, ch in enumerate(row) if ch == "#")
    ticks = "".join(f'<rect x="250" y="20" width="12" height="34" rx="3" transform="rotate({a} 256 256)"/>' for a in range(0, 360, 45))
    txt = (f'<text x="256" y="580" text-anchor="middle" font-family="Arial Black, Arial, sans-serif" font-weight="900" font-size="40" fill="{pal["main"] if key != "preto" else pal["bright"]}" stroke="{pal["dark"]}" stroke-width="1.5">{pal["tropa"].upper()}</text>'
           f'<text x="256" y="622" text-anchor="middle" font-family="Arial, sans-serif" font-size="24" letter-spacing="6" fill="{pal["light"]}">ASPECTO: {pal["aspecto"].upper()}</text>') if with_text else ""
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 {h}" width="512" height="{h}">
<defs>
 <filter id="brilho" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="9" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
 <radialGradient id="fundo" cx="50%" cy="45%" r="60%"><stop offset="0" stop-color="{pal["dark"]}"/><stop offset="1" stop-color="#05060a"/></radialGradient>
</defs>
<g id="moldura">
 <circle cx="256" cy="256" r="226" fill="url(#fundo)" stroke="{pal["main"]}" stroke-width="22"/>
 <circle cx="256" cy="256" r="200" fill="none" stroke="{pal["light"]}" stroke-width="4" opacity="0.8"/>
 <g fill="{pal["main"]}">{ticks}</g>
</g>
<g id="simbolo" fill="{pal["bright"]}" filter="url(#brilho)">{rects}</g>
{txt}
</svg>'''

def logo_png(key, pal, size=512):
    S = size / 512
    img = Image.new("RGBA", (size, int(640 * S)), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    c = 256 * S
    d.ellipse([c - 237 * S, c - 237 * S, c + 237 * S, c + 237 * S], fill=hx(pal["main"]))
    for r in range(215, 0, -3):
        d.ellipse([c - r * S, c - r * S, c + r * S, c + r * S], fill=mix(hx(pal["dark"]), (5, 6, 10), 1 - r / 215))
    d.ellipse([c - 202 * S, c - 202 * S, c + 202 * S, c + 202 * S], outline=hx(pal["light"]), width=max(1, int(4 * S)))
    for a in range(0, 360, 45):
        ra = math.radians(a)
        for rr in range(202, 236):
            x, y = c + rr * S * math.sin(ra), c - rr * S * math.cos(ra)
            d.ellipse([x - 6 * S, y - 6 * S, x + 6 * S, y + 6 * S], fill=hx(pal["main"]))
    sym = Image.new("RGBA", img.size, (0, 0, 0, 0)); ds = ImageDraw.Draw(sym)
    cell, x0 = 30 * S, c - 135 * S
    for r, row in enumerate(E[key]):
        for cc, ch in enumerate(row):
            if ch == "#": ds.rectangle([x0 + cc * cell, x0 + r * cell, x0 + (cc + 1) * cell, x0 + (r + 1) * cell], fill=hx(pal["bright"]))
    glow = Image.new("RGBA", img.size, hx(pal["glow"]) + (0,)); glow.putalpha(sym.split()[3].filter(ImageFilter.GaussianBlur(12 * S)))
    img = Image.alpha_composite(Image.alpha_composite(img, glow), sym); d = ImageDraw.Draw(img)
    tc = hx(pal["main"] if key != "preto" else pal["bright"])
    d.text((c, 580 * S), pal["tropa"].upper(), font=font(int(38 * S)), fill=tc, anchor="mm", stroke_width=max(1, int(2 * S)), stroke_fill=hx(pal["dark"]))
    d.text((c, 622 * S), "ASPECTO: " + pal["aspecto"].upper(), font=font(int(22 * S)), fill=hx(pal["light"]), anchor="mm")
    return img

# ---------------------------------------------------------------- main
for sub in ["aneis", "baterias", "texturas", "logos/svg", "logos/png", "previas"]:
    os.makedirs(f"{OUT}/{sub}", exist_ok=True)

ring_imgs, bat_imgs, logo_imgs, counts = [], [], [], {}
for i, (key, pal) in enumerate(P.items()):
    asp = pal["aspecto"].lower().replace("ç", "c").replace("ã", "a").replace("ú", "u")
    tex = make_texture(pal, i); tname = f"espectro_{key}.png"
    tex.save(f"{OUT}/texturas/{tname}")
    tex.resize((512, 512), Image.NEAREST).save(f"{OUT}/texturas/{tname[:-4]}_ampliada.png")
    r = build_ring(key, pal); b = build_battery(key, pal)
    save_bbmodel(r, f"anel_{asp}_{key}", tex, tname, f"{OUT}/aneis/anel_{asp}_{key}.bbmodel", RING_DISPLAY)
    save_bbmodel(b, f"bateria_{asp}_{key}", tex, tname, f"{OUT}/baterias/bateria_{asp}_{key}.bbmodel", BAT_DISPLAY)
    counts[key] = (len(r.els), len(b.els))
    with open(f"{OUT}/logos/svg/logo_{asp}_{key}.svg", "w", encoding="utf-8") as f: f.write(logo_svg(key, pal))
    with open(f"{OUT}/logos/svg/simbolo_{asp}_{key}.svg", "w", encoding="utf-8") as f: f.write(logo_svg(key, pal, False))
    lp = logo_png(key, pal, 1024); lp.save(f"{OUT}/logos/png/logo_{asp}_{key}.png")
    ring_imgs.append((pal, render(r, tex, 420, 25, 38))); bat_imgs.append((pal, render(b, tex, 420, 32, 18)))
    logo_imgs.append(logo_png(key, pal, 300))

def sheet(items, title, cw=420, ch=470):
    W = Image.new("RGBA", (cw * 3, ch * 3 + 70), (16, 18, 24, 255)); d = ImageDraw.Draw(W)
    d.text((cw * 1.5, 36), title, font=font(34), fill=(235, 235, 245), anchor="mm")
    for i, (pal, im) in enumerate(items):
        x, y = (i % 3) * cw, (i // 3) * ch + 70
        W.alpha_composite(im, (x, y))
        d.text((x + cw / 2, y + ch - 30), f'{pal["tropa"]} · {pal["aspecto"]}', font=font(22), fill=hx(pal["light"]) if pal["main"] != "#22222C" else (200, 200, 220), anchor="mm")
    return W

sheet(ring_imgs, "Anéis do Espectro Emocional").convert("RGB").save(f"{OUT}/previas/aneis.png")
sheet(bat_imgs, "Baterias do Espectro Emocional").convert("RGB").save(f"{OUT}/previas/baterias.png")
L = Image.new("RGBA", (300 * 3 + 40, 375 * 3 + 40), (16, 18, 24, 255))
for i, im in enumerate(logo_imgs): L.alpha_composite(im, (20 + (i % 3) * 300, 20 + (i // 3) * 375))
L.convert("RGB").save(f"{OUT}/previas/logos.png")
print(counts)

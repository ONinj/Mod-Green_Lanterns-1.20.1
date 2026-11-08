# Anéis das Tropas em alta definição (modelo de item Java, elementos pequenos).
# Mantém os símbolos EXATOS das texturas do mod (16x16), agora em relevo com microcubos de 0,25 px.
import sys, json, math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, "/home/claude/aneis"); sys.path.insert(0, "/home/claude/lore3d")
from simbolos import extrair, CORES

C = 8.0
LUZ = {"block_light": 15, "sky_light": 15}
ARQ = dict(verde="anel_vontade_verde", amarelo="anel_medo_amarelo", vermelho="anel_raiva_vermelho", laranja="anel_avareza_laranja",
           azul="anel_esperanca_azul", indigo="anel_compaixao_indigo", violeta="anel_amor_violeta", preto="anel_morte_preto",
           branco="anel_vida_branco")
METAL = dict(verde="#2E9C4C", amarelo="#D8B11E", vermelho="#B02424", laranja="#DE6A14", azul="#2C6CCB", indigo="#4A3A98",
             violeta="#A23CBA", preto="#36363D", branco="#DCDEE3")
ENERGIA = dict(verde="#7CFF8C", amarelo="#FFF07A", vermelho="#FF6A50", laranja="#FFB060", azul="#8CC8FF", indigo="#B8A0FF",
               violeta="#FF9CF4", preto="#E8ECFF", branco="#FFFFFF")
ACENTO = dict(verde="#1C5E30", amarelo="#F4F0E0", vermelho="#3A0808", laranja="#FFE07A", azul="#E8F2FF", indigo="#D8CCFF",
              violeta="#FFC8F6", preto="#C9C9D2", branco="#FFE7A0")

def hx(c): c = c.lstrip("#"); return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))
def mix(a, b, t): return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))
def lt(c, k): return mix(c, (255, 255, 255), k)
def dk(c, k): return mix(c, (0, 0, 0), k)

# ------------------------------------------------------------------ textura 64x64: 16 amostras de 16x16 (uv 4x4 cada)
SW = {"metal": 0, "escuro": 1, "polido": 2, "energia": 3, "fundo": 4, "simbolo": 5, "acento": 6, "interno": 7, "brilho": 8}
def textura(cor, sym, bg):
    rng = np.random.default_rng(3)
    T = np.zeros((64, 64, 4), np.uint8)
    def tile(i, f):
        x0, y0 = (i % 4) * 16, (i // 4) * 16
        for y in range(16):
            for x in range(16):
                T[y0 + y, x0 + x] = (*[max(0, min(255, int(v))) for v in f(x, y)], 255)
    m = hx(METAL[cor])
    def escovado(base, hi=0.3, lo=0.4):
        def f(x, y):
            t = y / 15.0
            k = 1 + hi * math.exp(-((t - 0.28) / 0.13) ** 2) - lo * t * t     # faixa de brilho em cima, escurece embaixo
            k += (((x * 7 + y * 3) % 11) / 11.0 - 0.5) * 0.05 + rng.uniform(-0.025, 0.025)
            k *= 0.88 if (x in (0, 15) or y in (0, 15)) else 1.0
            return [c * k + (k - 1) * 22 * (k > 1) for c in base]
        return f
    tile(SW["metal"], escovado(m))
    tile(SW["escuro"], escovado(dk(m, 0.45), 0.2, 0.25))
    tile(SW["polido"], escovado(lt(m, 0.16), 0.45, 0.25))
    e = hx(ENERGIA[cor])
    tile(SW["energia"], lambda x, y: lt(e, 0.25 * math.exp(-((x - 7.5) ** 2 + (y - 7.5) ** 2) / 30)))
    b = tuple(int(v) for v in bg)
    tile(SW["fundo"], lambda x, y: [c * (0.82 + 0.25 * math.exp(-((x - 7.5) ** 2 + (y - 7.5) ** 2) / 60)) for c in b])
    s = tuple(int(v) for v in sym)
    tile(SW["simbolo"], lambda x, y: [c * (1.0 - 0.12 * (y / 15)) + (25 if y < 3 else 0) for c in s])
    a = hx(ACENTO[cor])
    tile(SW["acento"], lambda x, y: lt(a, 0.35) if (x + y) % 9 == 0 else (dk(a, 0.12) if (x - y) % 7 == 0 else a))
    tile(SW["interno"], escovado(dk(m, 0.2), 0.6, 0.1))
    tile(SW["brilho"], lambda x, y: lt(e, 0.6))
    return Image.fromarray(T, "RGBA")

def uv(name):
    i = SW[name]; u, v = (i % 4) * 4, (i // 4) * 4
    return [u + 0.25, v + 0.25, u + 3.75, v + 3.75]

# ------------------------------------------------------------------ elementos
class Anel:
    def __init__(self): self.el = []; self.grupos = {}
    def box(self, grupo, nome, a, b, tex="metal", rot=None, luz=False, faces=None):
        f = [min(a[i], b[i]) for i in range(3)]; t = [max(a[i], b[i]) for i in range(3)]
        assert all(t[i] - f[i] > 1e-4 for i in range(3)), (nome, a, b)
        e = {"name": nome, "from": [round(v, 4) for v in f], "to": [round(v, 4) for v in t], "faces": {}}
        for fc in ("north", "east", "south", "west", "up", "down"):
            tn = (faces or {}).get(fc, tex)
            e["faces"][fc] = {"uv": uv(tn), "texture": "#0"}
        if rot:
            ax, ang, org = rot
            assert ang in (-45, -22.5, 0, 22.5, 45), ang
            e["rotation"] = {"angle": ang, "axis": ax, "origin": [round(v, 4) for v in org]}
        if luz: e["forge_data"] = dict(LUZ)
        self.grupos.setdefault(grupo, []).append(len(self.el)); self.el.append(e)

# posições angulares do aro: (base, ângulo de rotação no eixo z)
#  base "b" = embaixo (-90°), "d" = direita (0°, x+), "e" = esquerda (180°), "c" = em cima (+90°)
POS = [("b", -45), ("b", -22.5), ("b", 0), ("b", 22.5), ("b", 45), ("d", -22.5), ("d", 0), ("d", 22.5),
       ("e", -22.5), ("e", 0), ("e", 22.5), ("c", -45), ("c", -22.5), ("c", 0), ("c", 22.5), ("c", 45)]
def ang_real(base, r): return {"b": -90, "d": 0, "e": 180, "c": 90}[base] + r

def segmento(A, base, r, r0, r1, z0, z1, L, tex, grupo="aro", nome="aro", luz=False, faces_io=True):
    """caixa de raio r0..r1, comprimento tangencial L, entre z0 e z1, levada à posição pela rotação no eixo z"""
    h = L / 2
    if base == "b": a, b = (C - h, C - r1, z0), (C + h, C - r0, z1); fin, fout = "up", "down"
    elif base == "c": a, b = (C - h, C + r0, z0), (C + h, C + r1, z1); fin, fout = "down", "up"
    elif base == "d": a, b = (C + r0, C - h, z0), (C + r1, C + h, z1); fin, fout = "west", "east"
    else: a, b = (C - r1, C - h, z0), (C - r0, C + h, z1); fin, fout = "east", "west"
    faces = {fin: "interno"} if faces_io else None
    A.box(grupo, nome, a, b, tex, rot=("z", r, (C, C, C)) if r else None, luz=luz, faces=faces)

def largura(base, r):
    th = math.radians(ang_real(base, r) + 90)            # 0 embaixo, pi em cima
    k = (1 - math.cos(th)) / 2                            # 0 embaixo .. 1 em cima
    return 1.7 + 1.3 * k ** 2

def aro(A, Rin=2.15, Rout=2.85):
    L = 2 * Rout * math.tan(math.radians(11.25)) + 0.06
    for base, r in POS:
        w = largura(base, r); z0, z1 = C - w / 2, C + w / 2
        segmento(A, base, r, Rin, Rout, z0 + 0.14, z1 - 0.14, L, "metal")                       # núcleo
        segmento(A, base, r, Rin + 0.1, Rout - 0.1, z0, z0 + 0.14, L, "polido", nome="chanfro")  # chanfros das bordas
        segmento(A, base, r, Rin + 0.1, Rout - 0.1, z1 - 0.14, z1, L, "polido", nome="chanfro")
        segmento(A, base, r, Rout, Rout + 0.07, C - 0.13, C + 0.13, L, "energia", grupo="friso", nome="friso", luz=True, faces_io=False)
        for zz in (C - 0.55, C + 0.42):                                                          # frisos gravados
            segmento(A, base, r, Rout, Rout + 0.04, zz, zz + 0.13, L, "escuro", grupo="friso", nome="gravura", faces_io=False)

def ombros(A, w=3.0):
    """ombros: o aro engrossa e sobe até o sinete, em 3 degraus de cada lado"""
    for sg in (-1, 1):
        for i, (x0, x1, y0, y1, zz) in enumerate(((2.2, 3.0, 1.6, 2.75, 1.5), (1.9, 2.75, 2.55, 3.05, 1.35), (1.7, 2.45, 2.95, 3.25, 1.15))):
            a = (C + sg * x0, C + y0, C - zz); b = (C + sg * x1, C + y1, C + zz)
            A.box("ombros", f"ombro_{i}", a, b, ["metal", "metal", "polido"][i], faces={"up": "polido"})
        # voluta (rolo) na lateral do ombro
        A.box("ombros", "voluta", (C + sg * 2.85, C + 1.85, C - 0.45), (C + sg * 3.1, C + 2.5, C + 0.45), "polido", rot=("z", 22.5 * sg, (C + sg * 2.97, C + 2.17, C)))
        A.box("ombros", "voluta_luz", (C + sg * 3.05, C + 2.0, C - 0.12), (C + sg * 3.13, C + 2.35, C + 0.12), "energia", luz=True, rot=("z", 22.5 * sg, (C + sg * 2.97, C + 2.17, C)))

Y0 = C + 2.75          # base do sinete
def sinete(A, cor, m, sym, bg):
    S = 2.4            # meia largura do corpo do sinete (4,8 px)
    # galeria embaixo (mais estreita) e corpo com cantos chanfrados
    A.box("sinete", "galeria", (C - 1.8, Y0 - 0.45, C - 1.8), (C + 1.8, Y0 + 0.1, C + 1.8), "escuro")
    A.box("sinete", "galeria_45", (C - 1.6, Y0 - 0.45, C - 1.6), (C + 1.6, Y0 + 0.1, C + 1.6), "escuro", rot=("y", 45, (C, C, C)))
    for nm, (dx, dz) in (("corpo_x", (S, S - 0.4)), ("corpo_z", (S - 0.4, S))):
        A.box("sinete", nm, (C - dx, Y0, C - dz), (C + dx, Y0 + 0.65, C + dz), "metal", faces={"up": "polido"})
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = C + sx * (S - 0.4), C + sz * (S - 0.4)
            A.box("sinete", "canto", (cx - 0.28, Y0, cz - 0.28), (cx + 0.28, Y0 + 0.65, cz + 0.28), "metal", rot=("y", 45, (cx, Y0, cz)))
    # degrau polido e moldura em volta da face
    A.box("sinete", "degrau", (C - 2.25, Y0 + 0.65, C - 2.25), (C + 2.25, Y0 + 0.8, C + 2.25), "polido")
    R0, R1 = 2.0, 2.22
    for nm, a, b in (("moldura_n", (C - R1, Y0 + 0.8, C - R1), (C + R1, Y0 + 1.12, C - R0)),
                     ("moldura_s", (C - R1, Y0 + 0.8, C + R0), (C + R1, Y0 + 1.12, C + R1)),
                     ("moldura_o", (C - R1, Y0 + 0.8, C - R0), (C - R0, Y0 + 1.12, C + R0)),
                     ("moldura_l", (C + R0, Y0 + 0.8, C - R0), (C + R1, Y0 + 1.12, C + R0))):
        A.box("sinete", nm, a, b, "polido", faces={"up": "brilho"} if False else None)
    for sx in (-1, 1):                                       # rebites nos cantos da moldura
        for sz in (-1, 1):
            cx, cz = C + sx * 2.11, C + sz * 2.11
            A.box("sinete", "rebite", (cx - 0.1, Y0 + 1.12, cz - 0.1), (cx + 0.1, Y0 + 1.2, cz + 0.1), "polido")
    # face (fundo do símbolo) e o símbolo em microcubos de 0,25 px
    lum = lambda c: sum(c)
    sim_luz = lum(sym) >= lum(bg)
    A.box("sinete", "face", (C - R0, Y0 + 0.8, C - R0), (C + R0, Y0 + 0.92, C + R0), "fundo", luz=not sim_luz)
    p = 0.25
    used = np.zeros_like(m)
    for r in range(16):
        c = 0
        while c < 16:
            if m[r, c] and not used[r, c]:
                c1 = c
                while c1 < 16 and m[r, c1] and not used[r, c1]: c1 += 1
                r1 = r + 1
                while r1 < 16 and m[r1, c:c1].all() and not used[r1, c:c1].any(): r1 += 1
                used[r:r1, c:c1] = True
                A.box("simbolo", "simbolo", (C - 2 + c * p, Y0 + 0.92, C - 2 + r * p), (C - 2 + c1 * p, Y0 + 1.1, C - 2 + r1 * p),
                      "simbolo", luz=sim_luz, faces={"down": "fundo"})
                c = c1
            else: c += 1
    return sim_luz

# ------------------------------------------------------------------ detalhes de cada Tropa (os mesmos do modelo original, refinados)
def detalhes(A, cor):
    top = Y0 + 0.8
    if cor in ("vermelho", "preto"):         # garras nas laterais do aro, curvando para cima (3 dedos de cada lado)
        for sg in (-1, 1):
            x = C + sg * 2.8
            A.box("detalhes", "garra_base", (x, C - 1.0, C - 1.0), (x + sg * 0.45, C + 0.7, C + 1.0), "escuro")
            A.box("detalhes", "garra_veia", (x + sg * 0.45, C - 0.8, C - 0.07), (x + sg * 0.5, C + 0.5, C + 0.07), "energia", luz=True)
            for k, dz in enumerate((-0.62, 0.0, 0.62)):
                L = 1.0 if k == 1 else 0.8
                A.box("detalhes", "garra_1", (x + sg * 0.3, C - 0.25, C + dz - 0.2), (x + sg * (0.3 + L), C + 0.25, C + dz + 0.2), "metal",
                      rot=("z", 22.5 * sg, (x + sg * 0.3, C, C)))
                A.box("detalhes", "garra_2", (x + sg * (0.2 + L), C - 0.17, C + dz - 0.15), (x + sg * (0.75 + L), C + 0.17, C + dz + 0.15), "polido",
                      rot=("z", 45 * sg, (x + sg * (0.2 + L), C, C)))
                A.box("detalhes", "garra_ponta", (x + sg * (0.7 + L), C - 0.1, C + dz - 0.09), (x + sg * (1.05 + L), C + 0.1, C + dz + 0.09), "acento",
                      rot=("z", 45 * sg, (x + sg * (0.2 + L), C, C)))
    elif cor == "amarelo":                     # lâminas laterais e pontas na frente e atrás do sinete
        for sg in (-1, 1):
            x = C + sg * 2.25
            for i, (L, th, w) in enumerate(((1.3, 0.5, 1.4), (1.0, 0.36, 1.05), (0.75, 0.24, 0.7), (0.5, 0.14, 0.36))):
                x0 = x + sg * sum(v[0] for v in ((1.3, 0, 0), (1.0, 0, 0), (0.75, 0, 0))[:i]) * 0.8
                A.box("detalhes", f"lamina_{i}", (x0, top - 0.6 - th / 2, C - w / 2), (x0 + sg * L, top - 0.6 + th / 2, C + w / 2),
                      ["metal", "metal", "polido", "acento"][i], rot=("z", -22.5 * sg, (x, top - 0.6, C)))
            A.box("detalhes", "lamina_fio", (x, top - 0.6 + 0.24, C - 0.06), (x + sg * 2.6, top - 0.6 + 0.3, C + 0.06), "energia", luz=True, rot=("z", -22.5 * sg, (x, top - 0.6, C)))
        for sz in (-1, 1):
            z = C + sz * 2.25
            for i, (s, L) in enumerate(((0.75, 0.55), (0.5, 0.45), (0.26, 0.4))):
                z0 = z + sz * (0, 0.55, 1.0)[i]
                A.box("detalhes", f"ponta_{i}", (C - s / 2, top - 0.45 - s / 2, z0), (C + s / 2, top - 0.45 + s / 2, z0 + sz * L),
                      ["metal", "polido", "acento"][i], rot=("z", 45, (C, top - 0.45, z0)))
    elif cor in ("azul", "indigo"):             # asas: 5 penas em leque de cada lado
        for sg in (-1, 1):
            x = C + sg * 2.2
            for i in range(5):
                z = C - 1.5 + i * 0.68; L = 2.3 - abs(i - 1.2) * 0.45; y = top - 0.35 - i * 0.05
                A.box("detalhes", f"pena_{i}", (x, y - 0.1, z - 0.3), (x + sg * L, y + 0.1, z + 0.3), "polido" if i % 2 == 0 else "metal",
                      rot=("z", 22.5 * sg, (x, y, z)), faces={"up": "acento"})
                A.box("detalhes", f"raque_{i}", (x, y + 0.1, z - 0.05), (x + sg * (L - 0.25), y + 0.15, z + 0.05), "energia", luz=True,
                      rot=("z", 22.5 * sg, (x, y, z)))
            A.box("detalhes", "asa_raiz", (x - sg * 0.1, top - 0.75, C - 1.75), (x + sg * 0.45, top - 0.1, C + 1.75), "escuro")
    elif cor == "violeta":                      # 4 cristais em volta do sinete
        for (dx, dz) in ((-2.75, 0), (2.75, 0), (0, -2.75), (0, 2.75)):
            cx, cz = C + dx, C + dz
            A.box("detalhes", "engaste", (cx - 0.5, Y0 + 0.1, cz - 0.5), (cx + 0.5, Y0 + 0.5, cz + 0.5), "escuro")
            for i, (s, y0, y1) in enumerate(((0.8, Y0 + 0.4, Y0 + 1.7), (0.55, Y0 + 1.7, Y0 + 2.2), (0.28, Y0 + 2.2, Y0 + 2.5))):
                A.box("detalhes", f"cristal_{i}", (cx - s / 2, y0, cz - s / 2), (cx + s / 2, y1, cz + s / 2), "acento", luz=True,
                      rot=("y", 45, (cx, y0, cz)))
            for (px, pz) in ((-0.45, 0), (0.45, 0), (0, -0.45), (0, 0.45)):
                A.box("detalhes", "garra_cristal", (cx + px - 0.07, Y0 + 0.4, cz + pz - 0.07), (cx + px + 0.07, Y0 + 0.85, cz + pz + 0.07), "polido")
    elif cor == "laranja":                      # 4 gemas lapidadas nos cantos, com garras
        for sx in (-1, 1):
            for sz in (-1, 1):
                cx, cz = C + sx * 2.45, C + sz * 2.45
                A.box("detalhes", "base_gema", (cx - 0.45, Y0 + 0.2, cz - 0.45), (cx + 0.45, Y0 + 0.6, cz + 0.45), "escuro", rot=("y", 45, (cx, Y0, cz)))
                for i, (s, y0, y1) in enumerate(((0.55, Y0 + 0.6, Y0 + 0.8), (0.85, Y0 + 0.8, Y0 + 1.05), (0.6, Y0 + 1.05, Y0 + 1.25), (0.32, Y0 + 1.25, Y0 + 1.32))):
                    A.box("detalhes", f"gema_{i}", (cx - s / 2, y0, cz - s / 2), (cx + s / 2, y1, cz + s / 2), "acento", luz=True,
                          rot=("y", 45, (cx, y0, cz)))
                for (px, pz) in ((-0.42, 0), (0.42, 0), (0, -0.42), (0, 0.42)):
                    A.box("detalhes", "garra_gema", (cx + px - 0.06, Y0 + 0.6, cz + pz - 0.06), (cx + px + 0.06, Y0 + 1.12, cz + pz + 0.06), "polido")
    elif cor == "branco":                       # auréola flutuando sobre o sinete
        yh = Y0 + 1.55; R = 2.75; t = 0.18
        for nm, a, b in (("aureola_n", (C - R, yh, C - R), (C + R, yh + 0.2, C - R + t)), ("aureola_s", (C - R, yh, C + R - t), (C + R, yh + 0.2, C + R)),
                         ("aureola_o", (C - R, yh, C - R + t), (C - R + t, yh + 0.2, C + R - t)), ("aureola_l", (C + R - t, yh, C - R + t), (C + R, yh + 0.2, C + R - t))):
            A.box("detalhes", nm, a, b, "acento", luz=True)
        for sx in (-1, 1):
            for sz in (-1, 1):
                cx, cz = C + sx * (R - 0.09), C + sz * (R - 0.09)
                A.box("detalhes", "haste", (cx - 0.06, Y0 + 0.5, cz - 0.06), (cx + 0.06, yh, cz + 0.06), "polido")
                A.box("detalhes", "estrela", (cx - 0.16, yh - 0.05, cz - 0.16), (cx + 0.16, yh + 0.25, cz + 0.16), "brilho", luz=True, rot=("y", 45, (cx, yh, cz)))
        for (dx, dz) in ((0, -R + 0.09), (0, R - 0.09), (-R + 0.09, 0), (R - 0.09, 0)):
            A.box("detalhes", "raio", (C + dx - 0.12, yh + 0.2, C + dz - 0.12), (C + dx + 0.12, yh + 0.45, C + dz + 0.12), "brilho", luz=True, rot=("y", 45, (C + dx, yh, C + dz)))
    elif cor == "verde":                        # anel da Vontade: lanterna estilizada nos ombros (faixas de energia) + base do sinete
        for sg in (-1, 1):
            for i in range(3):
                A.box("detalhes", "friso_ombro", (C + sg * (2.2 + i * 0.22), C + 1.6 + i * 0.4, C - 1.5 + i * 0.15),
                      (C + sg * (2.3 + i * 0.22), C + 1.95 + i * 0.4, C + 1.5 - i * 0.15), "energia", luz=True)

DISPLAY = {"thirdperson_righthand": {"translation": [0, 1.5, 1], "scale": [0.4, 0.4, 0.4]},
           "thirdperson_lefthand": {"translation": [0, 1.5, 1], "scale": [0.4, 0.4, 0.4]},
           "firstperson_righthand": {"rotation": [10, -30, 0], "translation": [2, 3, 0], "scale": [0.45, 0.45, 0.45]},
           "firstperson_lefthand": {"rotation": [10, -30, 0], "translation": [2, 3, 0], "scale": [0.45, 0.45, 0.45]},
           "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
           "gui": {"rotation": [35, 30, 0], "translation": [0, -1.0, 0], "scale": [1.45, 1.45, 1.45]},
           "head": {"translation": [0, 6, 0]},
           "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}}

def construir(cor):
    m, sym, bg = extrair(cor)
    A = Anel()
    aro(A); ombros(A)
    sinete(A, cor, m, sym, bg)
    detalhes(A, cor)
    groups = []
    for g in ("aro", "friso", "ombros", "sinete", "simbolo", "detalhes"):
        if g in A.grupos: groups.append({"name": g, "origin": [8, 8, 8], "color": 0, "children": A.grupos[g]})
    model = {"credit": "Anel em alta definição (símbolo original em microcubos)", "texture_size": [64, 64],
             "textures": {"0": f"lanterna:item/ring/{ARQ[cor]}", "particle": f"lanterna:item/ring/{ARQ[cor]}"},
             "elements": A.el, "display": DISPLAY, "groups": groups}
    return model, textura(cor, sym, bg)

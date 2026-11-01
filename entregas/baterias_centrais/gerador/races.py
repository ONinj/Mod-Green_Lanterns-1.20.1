# Raças do Vazio Cromático (entidades GeckoLib)
import math
from core import *

def frect(A, x0, y0, S, a, b, c, d, col, glow=False, alpha=255):
    """retângulo em frações do slot"""
    for y in range(int(round(b * S)), int(round(d * S))):
        for x in range(int(round(a * S)), int(round(c * S))):
            if 0 <= x < S and 0 <= y < S: A.put(x0 + x, y0 + y, hx(col), alpha, glow)

def fdisc(A, x0, y0, S, cx, cy, r, col, glow=False, alpha=255):
    for y in range(S):
        for x in range(S):
            if math.hypot((x + 0.5) / S - cx, (y + 0.5) / S - cy) <= r: A.put(x0 + x, y0 + y, hx(col), alpha, glow)

def fbg(A, x0, y0, S, col, noise=4, alpha=255, grad=0.12):
    for y in range(S):
        for x in range(S):
            c = mix(lt(col, grad / 2), dk(col, grad), y / (S - 1)); k = A.r.randint(-noise, noise)
            A.put(x0 + x, y0 + y, tuple(v + k for v in hx(c)), alpha)

def sides():
    return ((-1, "r"), (1, "l"))

# ======================================================================== BRAKKARI (Kharuun · Fúria)
def brakkari():
    A = Atlas(256, 32, seed=11)
    SK, CR = "#2B2523", "#FF3B1A"
    A.material("pele", "cracks", SK, CR, density=2)
    A.material("pele2", "cracks", dk(SK, 0.15), "#C81E0A", density=1)
    A.material("chifre", "bone", "#3E322B")
    A.material("chifre_ponta", "glow", "#FF6A2A", "#FFD27A")
    A.material("couro", "fabric", "#4A2A1A", trim="#2A140A")
    A.material("metal", "metal", "#5A4A44")
    A.material("osso", "bone", "#D9CDB8")
    A.material("garra", "bone", "#1A1412")
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, SK, 5)
        frect(A, x0, y0, S, 0.05, 0.12, 0.95, 0.30, dk(SK, 0.45))                  # sobrancelha pesada
        for cx, cy in ((0.30, 0.40), (0.70, 0.40), (0.38, 0.56), (0.62, 0.56)):    # quatro olhos pequenos
            fdisc(A, x0, y0, S, cx, cy, 0.075, "#1A0A06"); fdisc(A, x0, y0, S, cx, cy, 0.05, "#FF8A2A", True)
            fdisc(A, x0, y0, S, cx - 0.015, cy - 0.015, 0.018, "#FFE6A0", True)
        for x in (0.2, 0.5, 0.78):                                                 # rachaduras do rosto
            frect(A, x0, y0, S, x, 0.62, x + 0.03, 0.9, CR, True)
        frect(A, x0, y0, S, 0.42, 0.62, 0.58, 0.72, dk(SK, 0.35))                   # narinas
    A.decal("rosto", face)
    g = Geo("brakkari", A); g.soft = {"pele": 1.2, "pele2": 0.9}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 14, 0), "root", "body")
    # pélvis, barriga, peito largo e corcunda
    g.box("body", (-7, 12, -4), (7, 17, 4), "pele")
    g.box("body", (-7.5, 17, -5), (7.5, 23, 4.5), "pele")
    g.box("body", (-6, 18, -5.6), (6, 22.5, -5), "pele2")                        # abdômen
    for i in range(3):
        g.box("body", (-4.5, 18.4 + i * 1.4, -5.9), (-0.4, 19.5 + i * 1.4, -5.5), "pele")
        g.box("body", (0.4, 18.4 + i * 1.4, -5.9), (4.5, 19.5 + i * 1.4, -5.5), "pele")
    g.box("body", (-10, 23, -6.5), (10, 32, 5), "pele")                           # peito
    g.box("body", (-9, 24.5, -7.2), (-0.4, 30.5, -6.5), "pele2")                 # peitorais
    g.box("body", (0.4, 24.5, -7.2), (9, 30.5, -6.5), "pele2")
    g.box("body", (-12, 30, -4.5), (12, 35, 4.5), "pele")                         # ombros/trapézio
    g.box("body", (-8, 33, -2), (8, 37, 5), "pele2")                              # corcunda
    for i, x in enumerate((-5, -2, 1.5, 4.5)):                                     # placas ósseas nas costas
        g.box("body", (x - 1, 30 + i % 2, 5), (x + 1, 34 + i % 2, 6.4), "chifre")
    # tanga de couro com troféus
    g.box("body", (-7.3, 11, -4.6), (7.3, 13.5, 4.6), "couro")
    g.box("body", (-4, 5.5, -4.8), (4, 11, -4.2), "couro"); g.box("body", (-4.5, 6.5, 4.2), (4.5, 11, 4.8), "couro")
    for i, x in enumerate((-5.5, -2.5, 2.5, 5.5)):
        g.box("body", (x - 0.6, 8.5 - i % 2, -5.2), (x + 0.6, 11, -4.6), "osso")    # dentes pendurados
    g.box("body", (-1.2, 7.4, -5.4), (1.2, 10, -4.8), "osso")
    # cabeça baixa e projetada
    g.bone("head", (0, 31, -5), "body", "head")
    g.box("head", (-4.2, 29.5, -12), (4.2, 37, -4), "pele", decals={"north": "rosto"})
    g.box("head", (-4.8, 35, -12.6), (4.8, 36.6, -9), "pele2")                   # sobrancelha
    g.box("head", (-4.6, 28, -12.5), (4.6, 30.5, -5), "pele")                     # mandíbula
    for s, _ in sides():
        g.box("head", (s * 3.0 - 0.6, 29.6, -13.3), (s * 3.0 + 0.6, 32.2, -12.4), "osso")  # presas
        g.box("head", (s * 3.0 - 0.35, 32.2, -13.1), (s * 3.0 + 0.35, 32.8, -12.6), "osso")
    # chifres: seis pares possíveis, cada um num osso próprio (o código mostra quantos quiser)
    horns = [(2.6, 36.5, -9.5), (3.6, 36.2, -7.0), (1.4, 36.8, -6.0)]
    hi = 0
    for (hxp, hy, hz) in horns:
        for s, sd in sides():
            hi += 1; nm = f"chifre_{hi}"
            g.bone(nm, (s * hxp, hy, hz), "head")
            g.limb(nm, (s * hxp, hy, hz), (s * (hxp + 2.2), hy + 2.5, hz + 1.0), 1.6, "chifre", taper_to=1.2)
            g.limb(nm, (s * (hxp + 2.2), hy + 2.5, hz + 1.0), (s * (hxp + 2.4), hy + 5.0, hz - 0.6), 1.1, "chifre", taper_to=0.8)
            g.limb(nm, (s * (hxp + 2.4), hy + 5.0, hz - 0.6), (s * (hxp + 2.0), hy + 6.4, hz - 1.6), 0.7, "chifre_ponta", taper_to=0.5)
    # braços enormes até quase o chão
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 11, 32, 0), "body", f"arm:{sd}")
        g.blob(b, (s * 12.5, 31, 0), 4.2, 3.6, 4.6, "pele", layers=4)
        g.box(b, (s * 12 - 3, 21, -3.4), (s * 12 + 3, 30, 3.4), "pele")
        g.box(b, (s * 12 - 3.6, 12, -4), (s * 12 + 3.6, 21, 4), "pele")
        g.box(b, (s * 12 - 3.9, 14, -4.3), (s * 12 + 3.9, 17.5, 4.3), "metal")   # bracelete
        for z in (-3, 0, 3): g.box(b, (s * 12 - 4.3, 15.2, z - 0.4), (s * 12 + 4.3, 16.2, z + 0.4), "osso")
        g.box(b, (s * 12 - 4, 6, -4.5), (s * 12 + 4, 12, 4.2), "pele2")            # punho
        for i in range(4):
            z = -3.6 + i * 2.1
            g.box(b, (s * 12 - 3.8, 5.0, z - 0.8), (s * 12 + 3.4, 6.2, z + 0.8), "pele")   # dedos
            g.box(b, (s * 12 - 3.4, 4.2, z - 0.4), (s * 12 + 1.5, 5.0, z + 0.4), "garra")
    # pernas grossas e curtas
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 4.5, 13, 0), "root", f"leg:{sd}")
        g.box(b, (s * 4.5 - 3.6, 6, -3.6), (s * 4.5 + 3.6, 13, 3.6), "pele")
        g.box(b, (s * 4.5 - 3.0, 1.6, -3.0), (s * 4.5 + 3.0, 6.5, 3.0), "pele")
        g.box(b, (s * 4.5 - 2.4, 5.0, -3.9), (s * 4.5 + 2.4, 7.4, -3.0), "chifre")  # joelheira óssea
        g.box(b, (s * 4.5 - 4.0, 0, -5.6), (s * 4.5 + 4.0, 2.0, 3.4), "pele2")      # pé
        for i in range(3):
            x = s * 4.5 - 2.6 + i * 2.6
            g.box(b, (x - 0.6, 0, -6.6), (x + 0.6, 1.0, -5.6), "garra")
    return g, dict(scale=1.0, height=2.5, notes="Chifres em ossos chifre_1..chifre_6: esconda/mostre conforme as lutas vencidas.")

# ======================================================================== GHALLOS (Aurumvex · Ganância)
def ghallos():
    A = Atlas(256, 32, seed=12)
    A.material("pele", "skin", "#C98A4A", spots="#A86A30", n_spots=5)
    A.material("manto", "fabric", "#7A3E12", trim="#D4A82A")
    A.material("manto2", "fabric", "#5A2A0A")
    A.material("bolso", "fabric", "#A0571C", trim="#6A3410")
    A.material("ouro", "metal", "#D4A82A", rivets=False)
    A.material("gema", "crystal", "#FF8A00", alpha=255, glowing=True)
    A.material("couro", "solid", "#4A2A14")
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, "#C98A4A", 4)
        for cy in (0.32, 0.52):                                                    # seis olhos: 2 fileiras de 3
            for cx in (0.24, 0.5, 0.76):
                r = 0.085 if cy < 0.4 else 0.07
                fdisc(A, x0, y0, S, cx, cy, r + 0.025, "#5A2A0A"); fdisc(A, x0, y0, S, cx, cy, r, "#FFE58A", True)
                fdisc(A, x0, y0, S, cx, cy + 0.01, r * 0.5, "#2A1000")
        frect(A, x0, y0, S, 0.3, 0.72, 0.7, 0.78, "#5A2A0A"); frect(A, x0, y0, S, 0.36, 0.78, 0.64, 0.82, "#8A4A20")  # sorriso
        frect(A, x0, y0, S, 0.62, 0.70, 0.68, 0.76, "#FFE58A", True)               # dente de ouro
    A.decal("rosto", face)
    g = Geo("ghallos", A); g.soft = {"pele": 1.0, "manto": 0.6, "manto2": 0.6}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 4, 0), "root", "body")
    g.blob("body", (0, 7.5, -0.6), 5.2, 4.4, 5.0, "pele", layers=5)                # barriga
    g.box("body", (-4.8, 9.5, -4.2), (4.8, 12.2, 3.8), "manto")                     # manto sobre os ombros
    g.box("body", (-5.2, 4.0, 1.0), (5.2, 11.5, 4.6), "manto2")                     # manto nas costas
    g.box("body", (-5.5, 3.2, 3.6), (5.5, 4.4, 4.8), "manto")
    for (x, y) in ((-3.6, 6.2), (-1.2, 5.0), (1.2, 5.0), (3.6, 6.2), (-3.2, 8.6), (3.2, 8.6)):   # bolsos
        g.box("body", (x - 1.1, y - 1.2, -5.8), (x + 1.1, y + 1.0, -4.9), "bolso")
        g.box("body", (x - 1.1, y + 0.6, -6.0), (x + 1.1, y + 1.0, -5.8), "manto")
    g.box("body", (-1.4, 9.2, -4.8), (1.4, 10.4, -4.2), "ouro"); g.box("body", (-0.5, 9.4, -5.1), (0.5, 10.2, -4.8), "gema")  # broche
    # bugigangas penduradas (balançam e tilintam)
    for i, (x, z, kind) in enumerate(((-4.6, -2.0, "moeda"), (4.6, -2.0, "chave"), (-4.2, 2.2, "saco"), (4.2, 2.4, "moeda"), (0, 4.7, "lanterna"))):
        nm = f"bugiganga_{i + 1}"; g.bone(nm, (x, 9.5, z), "body", f"sway:r:{i * 60}")
        g.box(nm, (x - 0.12, 7.0, z - 0.12), (x + 0.12, 9.5, z + 0.12), "couro")
        if kind == "moeda":
            g.box(nm, (x - 0.9, 5.6, z - 0.15), (x + 0.9, 7.2, z + 0.15), "ouro"); g.box(nm, (x - 0.5, 5.9, z - 0.25), (x + 0.5, 6.9, z + 0.25), "ouro")
        elif kind == "chave":
            g.box(nm, (x - 0.25, 4.6, z - 0.2), (x + 0.25, 7.0, z + 0.2), "ouro"); g.box(nm, (x - 0.7, 6.4, z - 0.25), (x + 0.7, 7.3, z + 0.25), "ouro")
            g.box(nm, (x, 4.6, z - 0.2), (x + 0.7, 5.2, z + 0.2), "ouro")
        elif kind == "saco":
            g.box(nm, (x - 1.1, 4.8, z - 1.0), (x + 1.1, 6.8, z + 1.0), "couro"); g.box(nm, (x - 0.5, 6.8, z - 0.5), (x + 0.5, 7.2, z + 0.5), "ouro")
        else:
            g.box(nm, (x - 0.8, 4.6, z - 0.8), (x + 0.8, 6.6, z + 0.8), "ouro"); g.box(nm, (x - 0.5, 5.0, z - 0.5), (x + 0.5, 6.2, z + 0.5), "gema")
    # cabeça grande com seis olhos, orelhas e capuz
    g.bone("head", (0, 12, 0), "body", "head")
    g.box("head", (-3.8, 12, -4.2), (3.8, 17.6, 3.2), "pele", decals={"north": "rosto"})
    g.box("head", (-4.2, 15.6, -3.0), (4.2, 18.4, 3.8), "manto")                  # capuz
    g.box("head", (-3.6, 18.4, -1.8), (3.6, 19.0, 3.2), "manto2")
    for s, _ in sides():
        g.limb("head", (s * 3.8, 15.2, -0.5), (s * 6.6, 16.4, 0.4), 1.2, "pele", taper_to=0.6)   # orelhas
        g.box("head", (s * 4.0 - 0.3, 13.6, -0.6), (s * 4.0 + 0.3, 14.4, 0.0), "ouro")           # brinco
    # braços compridos demais (até o chão)
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 4.6, 11, 0), "body", f"arm:{sd}")
        g.box(b, (s * 4.9 - 1.4, 8.5, -1.4), (s * 4.9 + 1.4, 11.8, 1.4), "manto")
        g.box(b, (s * 5.2 - 0.9, 4.0, -0.9), (s * 5.2 + 0.9, 8.6, 0.9), "pele")
        g.box(b, (s * 5.4 - 0.85, 1.2, -0.85), (s * 5.4 + 0.85, 4.2, 0.85), "pele")
        g.box(b, (s * 5.4 - 1.05, 3.6, -1.05), (s * 5.4 + 1.05, 4.4, 1.05), "ouro")   # pulseira
        for i, z in enumerate((-0.9, 0.0, 0.9)):                                      # três dedos longos
            g.box(b, (s * 5.4 - 0.3, -0.2, z - 0.3), (s * 5.4 + 0.3, 1.3, z + 0.3), "pele")
        g.box(b, (s * 5.4 + s * 0.6 - 0.25, 1.4, -0.8), (s * 5.4 + s * 0.6 + 0.25, 1.9, -0.3), "ouro")  # anel
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 2, 3.5, 0), "root", f"leg:{sd}")
        g.box(b, (s * 2 - 1.2, 1, -1.2), (s * 2 + 1.2, 3.6, 1.2), "pele")
        g.box(b, (s * 2 - 1.4, 0, -2.6), (s * 2 + 1.4, 1.2, 1.2), "couro")
    return g, dict(scale=1.0, height=1.0, notes="Bugigangas em ossos bugiganga_1..5 com balanço (tilintar).")

# ======================================================================== SKRELL'VATH (Nyxkor · Medo)
def skrellvath():
    A = Atlas(256, 32, seed=13)
    A.material("carapaca", "carapace", "#141414", "#E8C200")
    A.material("carapaca2", "carapace", "#0A0A0A", "#8A7000", stripe=9)
    A.material("articulacao", "solid", "#2A2A22")
    A.material("amarelo", "glow", "#F2C800", "#FFF7B0")
    A.material("mandibula", "plates", "#1E1E1A", "#0A0A08", step=4)
    A.material("garra", "bone", "#E8C200")
    def eye(A, x0, y0, S):                                                         # olho composto (hexágonos)
        for y in range(S):
            for x in range(S):
                cx, cy = (x // 3) * 3 + (1.5 if (y // 3) % 2 else 0), (y // 3) * 3
                d = math.hypot(x - cx - 1, y - cy - 1)
                c = mix("#FFF27A", "#8A6A00", min(1, d / 2.2))
                if (x % 3 == 0) or (y % 3 == 0): c = hx("#3A2E00")
                spec = math.hypot(x - S * 0.3, y - S * 0.3) < S * 0.12
                A.put(x0 + x, y0 + y, lt(c, 0.4) if spec else c, 255, True)
    A.decal("olho", eye)
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, "#141414", 3)
        for i in range(4): frect(A, x0, y0, S, 0.1 + i * 0.2, 0.15, 0.16 + i * 0.2, 0.55, "#E8C200", True)
        frect(A, x0, y0, S, 0.3, 0.7, 0.7, 0.76, "#E8C200", True)
    A.decal("rosto", face)
    g = Geo("skrell_vath", A); g.soft = {"carapaca": 0.5}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 22, 0), "root", "body")
    # abdômen segmentado, cintura de vespa, tórax
    g.taper("body", (0, 19, 1.0), 4.5, 4.0, 3.0, 2.6, 4.5, 3, "carapaca2")
    g.box("body", (-1.4, 23.5, -1.1), (1.4, 26.5, 1.3), "articulacao")             # cintura fina
    g.taper("body", (0, 26.5, 0), 4.0, 3.2, 6.6, 4.2, 9.5, 5, "carapaca")         # tórax alargando
    for i in range(4):                                                             # placas do peito
        y = 27.5 + i * 2.0
        g.box("body", (-2.6 + i * 0.3, y, -2.5 - i * 0.1), (2.6 - i * 0.3, y + 1.5, -1.8), "carapaca2")
    for s, _ in sides():                                                           # espinhos nos ombros
        g.limb("body", (s * 3.4, 36, 0.5), (s * 5.4, 40.5, 2.2), 1.2, "carapaca", taper_to=0.4)
        g.limb("body", (s * 2.2, 35.4, 1.8), (s * 3.0, 38.8, 4.2), 0.9, "carapaca2", taper_to=0.3)
    for i in range(5):                                                             # marcas amarelas nas costas
        g.box("body", (-0.4, 27.2 + i * 1.8, 1.9 + i * 0.02), (0.4, 28.2 + i * 1.8, 2.25), "amarelo")
    # pescoço longo e cabeça alongada
    g.bone("head", (0, 36, -0.5), "body", "head")
    g.box("head", (-0.9, 35.6, -1.4), (0.9, 39.4, 0.4), "articulacao")
    g.taper("head", (0, 39, -1.6), 3.6, 4.4, 2.2, 3.4, 6.0, 4, "carapaca", decals=None)
    g.box("head", (-2.0, 40.0, -4.3), (2.0, 43.2, -3.5), "carapaca", decals={"north": "rosto"})
    for s, _ in sides():
        g.box("head", (s * 2.3 - 1.0 * (s > 0) - 0.0, 41.0, -3.6), (s * 2.3 + 1.0 * (s < 0) + 0.0, 43.8, -1.2), "carapaca",
              decals={"east" if s > 0 else "west": "olho", "north": "olho"})          # olhos compostos laterais
        g.limb("head", (s * 1.0, 45, -2.0), (s * 3.2, 51, 0.5), 0.45, "carapaca2", steps=2, taper_to=0.2)   # antenas
        jb = g.bone(f"mandibula_{'l' if s > 0 else 'r'}", (s * 1.3, 40.5, -4.0), "head", "jaw")
        g.limb(jb, (s * 1.3, 40.5, -4.0), (s * 1.9, 38.5, -6.2), 1.0, "mandibula", taper_to=0.7)
        g.limb(jb, (s * 1.9, 38.5, -6.2), (s * 0.5, 37.6, -7.4), 0.7, "garra", taper_to=0.35)
    # braços finos de três segmentos com lâmina
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 3.6, 34.5, 0), "body", f"arm:{sd}")
        g.limb(b, (s * 3.8, 35, 0), (s * 5.4, 27, -0.6), 1.5, "carapaca", taper_to=1.1)
        g.box(b, (s * 5.4 - 0.8, 26.2, -1.4), (s * 5.4 + 0.8, 27.8, 0.2), "articulacao")
        g.limb(b, (s * 5.4, 27, -0.6), (s * 5.9, 17.5, -2.4), 1.1, "carapaca2", taper_to=0.8)
        g.limb(b, (s * 5.9, 17.5, -2.4), (s * 5.6, 12.5, -4.8), 0.7, "garra", taper_to=0.25)
        for i in range(3): g.box(b, (s * 5.4 - 0.3, 21 + i * 2, -2.0), (s * 5.4 + 0.3, 22 + i * 2, -1.5), "amarelo")
    # pernas digitígradas
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 2.2, 21, 0.5), "root", f"leg:{sd}")
        g.limb(b, (s * 2.2, 21, 0.5), (s * 3.2, 12.5, -3.0), 1.8, "carapaca", taper_to=1.3)
        g.box(b, (s * 3.2 - 0.9, 11.6, -3.9), (s * 3.2 + 0.9, 13.4, -2.1), "articulacao")
        g.limb(b, (s * 3.2, 12.5, -3.0), (s * 3.0, 4.0, 2.2), 1.2, "carapaca2", taper_to=0.9)
        g.limb(b, (s * 3.0, 4.0, 2.2), (s * 3.0, 0.4, -0.2), 0.9, "carapaca", taper_to=0.7)
        for dx in (-0.8, 0.8):
            g.limb(b, (s * 3.0 + dx * 0.5, 0.6, -0.2), (s * 3.0 + dx, 0.0, -2.8), 0.45, "garra", taper_to=0.2)
    # máscara de líder (feita de carapaça de rival) — escondida por padrão no código
    g.bone("mascara_lider", (0, 41, -4.5), "head")
    g.box("mascara_lider", (-2.6, 39.6, -5.2), (2.6, 44.4, -4.4), "carapaca2")
    for x in (-2.0, 0.0, 2.0): g.limb("mascara_lider", (x, 44.2, -4.8), (x * 1.4, 47.0, -4.4), 0.6, "garra", taper_to=0.2)
    g.box("mascara_lider", (-1.8, 41.4, -5.4), (-0.6, 42.4, -5.2), "amarelo"); g.box("mascara_lider", (0.6, 41.4, -5.4), (1.8, 42.4, -5.2), "amarelo")
    return g, dict(scale=1.0, height=3.0, notes="Osso mascara_lider: mostrar só nos líderes de colmeia. Mandíbulas animadas (estalo).")

# ======================================================================== VELTARI (Veridia · Vontade)
def veltari():
    A = Atlas(256, 32, seed=14)
    ST = "#7C8A80"
    A.material("pedra", "stone", ST, "#5A665E")
    A.material("pedra2", "stone", dk(ST, 0.18), "#4A544C")
    A.material("luz", "glow", "#39FF6A", "#E0FFE8")
    A.material("manto", "fabric", "#1F5A34", trim="#C9D6CC")
    A.material("manto2", "fabric", "#163F25")
    A.material("prata", "metal", "#B8C4BC", rivets=False)
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, ST, 3, grad=0.08)
        for cx in (0.3, 0.7):
            frect(A, x0, y0, S, cx - 0.12, 0.40, cx + 0.12, 0.48, "#39FF6A", True)
            frect(A, x0, y0, S, cx - 0.08, 0.42, cx + 0.08, 0.46, "#E0FFE8", True)
        frect(A, x0, y0, S, 0.49, 0.15, 0.51, 0.36, "#39FF6A", True)               # fissura na testa (sem boca)
        frect(A, x0, y0, S, 0.45, 0.55, 0.55, 0.62, dk(ST, 0.15))                    # nariz liso
    A.decal("rosto", face)
    g = Geo("veltari", A); g.soft = {"pedra": 0.5, "pedra2": 0.4}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 20, 0), "root", "body")
    g.box("body", (-4.2, 18, -2.4), (4.2, 22, 2.4), "pedra2")
    g.box("body", (-5.2, 22, -2.8), (5.2, 33, 2.8), "pedra")
    g.box("body", (-1.0, 21.6, -2.6), (1.0, 22.4, 2.6), "luz")                     # junta da cintura
    for y in (24.5, 27.0, 29.5): g.box("body", (-4.6, y, -3.1), (4.6, y + 1.8, -2.8), "pedra2")   # placas do peito
    # manto de juiz (ombros + faixa + cauda)
    g.box("body", (-6.4, 31, -3.4), (6.4, 34, 3.4), "manto")
    g.box("body", (-5.6, 34, -2.6), (5.6, 34.8, 2.6), "prata")
    g.limb("body", (-3.6, 33.6, -3.2), (3.4, 20.6, -3.2), 1.6, "manto2", steps=3)  # faixa diagonal
    g.box("body", (-5.4, 10, 2.6), (5.4, 31, 3.6), "manto")                         # manto nas costas até a canela
    g.box("body", (-5.6, 9.0, 2.8), (5.6, 10.2, 3.8), "prata")
    g.bone("head", (0, 35, 0), "body", "head")
    g.box("head", (-1.4, 34.4, -1.4), (1.4, 36, 1.4), "luz")                        # pescoço de luz
    g.box("head", (-3.4, 36, -3.6), (3.4, 44, 3.2), "pedra", decals={"north": "rosto"})
    g.box("head", (-3.0, 44, -3.0), (3.0, 45.2, 2.6), "pedra2")
    g.box("head", (-3.6, 39.0, 3.2), (3.6, 43.6, 4.0), "pedra2")                   # nuca alta
    for s, _ in sides():
        g.box("head", (s * 3.4 - (0.6 if s > 0 else 0), 37.5, -1.0), (s * 3.4 + (0 if s > 0 else 0.6), 42.5, 1.5), "pedra2")
        g.box("head", (s * 3.75 - 0.08, 38.4, -0.2), (s * 3.75 + 0.08, 41.6, 0.4), "luz")
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 5.8, 32.5, 0), "body", f"arm:{sd}")
        g.box(b, (s * 6.4 - 1.7, 26, -1.7), (s * 6.4 + 1.7, 33, 1.7), "pedra")
        g.box(b, (s * 6.4 - 1.2, 25.2, -1.2), (s * 6.4 + 1.2, 26.2, 1.2), "luz")   # cotovelo
        g.box(b, (s * 6.4 - 1.5, 18.6, -1.5), (s * 6.4 + 1.5, 25.4, 1.5), "pedra2")
        g.box(b, (s * 6.4 - 1.0, 17.8, -1.0), (s * 6.4 + 1.0, 18.8, 1.0), "luz")   # pulso
        g.box(b, (s * 6.4 - 1.4, 14.8, -1.4), (s * 6.4 + 1.4, 18.0, 1.0), "pedra")  # mão
        for i in range(3): g.box(b, (s * 6.4 - 0.25 + (i - 1) * 0.9, 13.6, -1.3), (s * 6.4 + 0.25 + (i - 1) * 0.9, 14.9, -0.7), "pedra2")
        g.box(b, (s * 6.4 - 1.9, 30.5, -1.9), (s * 6.4 + 1.9, 33.6, 1.9), "manto")  # ombreira de tecido
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 2.3, 18.5, 0), "root", f"leg:{sd}")
        g.box(b, (s * 2.3 - 1.9, 10.5, -1.9), (s * 2.3 + 1.9, 18.5, 1.9), "pedra")
        g.box(b, (s * 2.3 - 1.3, 9.6, -2.0), (s * 2.3 + 1.3, 10.6, 1.3), "luz")    # joelho
        g.box(b, (s * 2.3 - 1.7, 2.0, -1.7), (s * 2.3 + 1.7, 9.8, 1.7), "pedra2")
        g.box(b, (s * 2.3 - 1.9, 0, -3.4), (s * 2.3 + 1.9, 2.0, 1.9), "pedra")
        g.box(b, (s * 2.3 - 1.2, 1.9, -1.2), (s * 2.3 + 1.2, 2.3, 1.2), "luz")      # tornozelo
    return g, dict(scale=1.0, height=2.8, notes="Sem boca; juntas de luz verde brilham (glowmask).")

# ======================================================================== AELÍNOS (Lumeris · Esperança)
def aelinos():
    A = Atlas(256, 32, seed=15)
    SK = "#9CCBFF"
    A.material("pele", "skin", SK, spots="#5FB8FF", spot_glow=True, alpha=200, n_spots=3, translucent=True)
    A.material("pele2", "skin", dk(SK, 0.15), spots="#3EA0FF", spot_glow=True, alpha=215, n_spots=2, translucent=True)
    A.material("pena", "feathers", "#5AA8FF", "#DDF2FF", alpha=200, translucent=True)
    A.material("pena2", "feathers", "#2E7BE0", "#9CE0FF", alpha=210, translucent=True)
    A.material("barriga", "skin", "#DDEEFF", alpha=225, translucent=True)
    A.material("luz", "glow", "#3EA0FF", "#E0F4FF")
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, SK, 3, alpha=215)
        for cx in (0.27, 0.73):                                                    # olhos grandes e brilhantes
            fdisc(A, x0, y0, S, cx, 0.45, 0.17, "#0A2A66"); fdisc(A, x0, y0, S, cx, 0.45, 0.14, "#3EA0FF", True)
            fdisc(A, x0, y0, S, cx, 0.47, 0.07, "#E0F4FF", True); fdisc(A, x0, y0, S, cx - 0.05, 0.40, 0.03, "#FFFFFF", True)
        frect(A, x0, y0, S, 0.42, 0.74, 0.58, 0.77, "#5A8AC8")                     # boca pequena
        for cx in (0.47, 0.53): fdisc(A, x0, y0, S, cx, 0.64, 0.015, "#5A8AC8")
    A.decal("rosto", face)
    g = Geo("aelinos", A); g.soft = {"pele": 0.9, "pele2": 0.5}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 16, 0), "root", "body")
    g.box("body", (-3.0, 14.6, -1.8), (3.0, 17.6, 1.8), "pele2")                 # quadril
    g.box("body", (-2.2, 17.6, -1.5), (2.2, 20.0, 1.5), "pele2")                 # cintura fina
    g.taper("body", (0, 20.0, 0), 4.6, 3.0, 7.4, 3.8, 6.0, 4, "pele")           # peito em V
    g.box("body", (-1.8, 17.8, -1.8), (1.8, 24.0, -1.5), "barriga")
    for i in range(6):                                                             # barbatana dorsal em penas
        y = 17.6 + i * 1.5
        g.limb("body", (0, y, 2.0), (0, y + 1.2, 4.6 - i * 0.2), 0.5 + (0.3 if i % 2 else 0), "pena", taper_to=0.3)
    g.bone("head", (0, 26.5, 0), "body", "head")
    g.box("head", (-1.0, 26, -0.9), (1.0, 27.6, 0.9), "pele2")
    g.box("head", (-3.2, 27.4, -3.6), (3.2, 32.4, 2.6), "pele", decals={"north": "rosto"})
    g.box("head", (-2.8, 32.4, -3.0), (2.8, 33.2, 2.2), "pele2")
    g.box("head", (-2.6, 27.0, -3.9), (2.6, 28.0, -3.4), "pele2")                 # queixo
    for i in range(5):                                                             # crista de penas
        g.limb("head", (0, 33.0, -2.4 + i * 1.2), (0, 35.6 - i * 0.2, -1.0 + i * 1.5), 0.6 if i % 2 else 0.9, "pena2", taper_to=0.3)
    for s, _ in sides():                                                           # barbatanas-orelha
        for i in range(3):
            g.limb("head", (s * 3.2, 31.2 - i * 1.1, 0.4), (s * (5.4 - i * 0.5), 32.6 - i * 1.2, 2.2), 0.7, "pena", taper_to=0.3)
        for i in range(3):                                                         # guelras luminosas
            g.box("head", (s * 3.25 - 0.05, 28.2 + i * 0.7, -1.4), (s * 3.25 + 0.05, 28.6 + i * 0.7, 0.6), "luz")
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 3.8, 25, 0), "body", f"arm:{sd}")
        g.box(b, (s * 4.4 - 1.0, 18.4, -1.0), (s * 4.4 + 1.0, 25.4, 1.0), "pele")
        g.box(b, (s * 4.6 - 0.85, 12.6, -0.85), (s * 4.6 + 0.85, 18.6, 0.85), "pele2")
        for i in range(4):                                                         # barbatana do antebraço
            g.limb(b, (s * (4.6 + 0.85), 17.6 - i * 1.3, 0), (s * (6.8 - i * 0.3), 16.6 - i * 1.5, 1.0), 0.5, "pena", taper_to=0.25)
        g.box(b, (s * 4.6 - 1.0, 10.6, -1.1), (s * 4.6 + 1.0, 12.8, 0.9), "pele")  # mão com membrana
        for i in range(3): g.box(b, (s * 4.6 - 0.2 + (i - 1) * 0.6, 9.2, -0.9), (s * 4.6 + 0.2 + (i - 1) * 0.6, 10.8, -0.5), "pele2")
        g.box(b, (s * 4.6 - 0.9, 9.6, -0.75), (s * 4.6 + 0.9, 10.6, -0.65), "pena")
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 1.8, 15, 0), "root", f"leg:{sd}")
        g.box(b, (s * 1.8 - 1.2, 8.0, -1.2), (s * 1.8 + 1.2, 15, 1.2), "pele")
        g.box(b, (s * 1.8 - 1.0, 2.0, -1.0), (s * 1.8 + 1.0, 8.2, 1.0), "pele2")
        for i in range(3):                                                         # barbatana da panturrilha
            g.limb(b, (s * 1.8, 7.4 - i * 1.6, 1.0), (s * 2.4, 6.6 - i * 1.6, 3.0), 0.5, "pena", taper_to=0.25)
        g.box(b, (s * 1.8 - 1.6, 0, -3.8), (s * 1.8 + 1.6, 2.0, 1.0), "pele")      # pé de nadadeira
        g.box(b, (s * 1.8 - 1.8, 0, -4.6), (s * 1.8 + 1.8, 0.6, -3.4), "pena2")
    return g, dict(scale=1.0, height=2.1, translucent=True, notes="Pele translúcida: use RenderType translúcido.")

# ======================================================================== VES'KAI (Tal'Eun · Compaixão)
def veskai():
    A = Atlas(256, 32, seed=16)
    A.material("pele", "tattoo", "#2A2236", "#8A6BFF")
    A.material("veste", "fabric", "#2E1F5A", trim="#6A4BFF")
    A.material("veste2", "fabric", "#1C1238")
    A.material("veu", "fabric", "#4B3A8A", folds=False)
    A.material("faixa", "fabric", "#D9CCB8")
    A.material("madeira", "plates", "#3A2C24", "#24180F", step=6)
    A.material("orbe", "glow", "#6A4BFF", "#E0D9FF")
    A.material("prata", "metal", "#A9A2C8", rivets=False)
    def veil(A, x0, y0, S):                                                         # rosto coberto pelo véu
        fbg(A, x0, y0, S, "#4B3A8A", 3)
        frect(A, x0, y0, S, 0.0, 0.0, 1.0, 0.28, "#2E1F5A")
        for cx in (0.32, 0.68): frect(A, x0, y0, S, cx - 0.1, 0.32, cx + 0.1, 0.38, "#B19CFF", True)   # brilho dos olhos sob o véu
        for x in range(4): frect(A, x0, y0, S, 0.12 + x * 0.22, 0.45, 0.14 + x * 0.22, 1.0, dk("#4B3A8A", 0.2))  # dobras
        for i in range(3): frect(A, x0, y0, S, 0.3 + i * 0.15, 0.6, 0.35 + i * 0.15, 0.65, "#8A6BFF", True)    # símbolos bordados
    A.decal("veu_rosto", veil)
    g = Geo("ves_kai", A); g.soft = {"pele": 0.5, "veu": 0.5}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 14, 0), "root", "body")
    g.box("body", (-3.0, 13, -1.7), (3.0, 16, 1.7), "veste")
    g.box("body", (-3.4, 16, -1.9), (3.4, 25, 1.9), "pele")
    g.limb("body", (-3.0, 25, -2.1), (2.6, 15.6, -2.1), 1.4, "veste", steps=3)    # tecido cruzado
    g.limb("body", (3.0, 25, 2.1), (-2.6, 15.6, 2.1), 1.4, "veste", steps=3)
    g.box("body", (-3.6, 15.0, -2.2), (3.6, 16.4, 2.2), "faixa")                    # faixa da cintura
    g.box("body", (-3.8, 3.0, -2.0), (3.8, 15.2, -1.6), "veste2")                  # saia de tiras
    g.box("body", (-3.8, 4.0, 1.6), (3.8, 15.2, 2.0), "veste2")
    for x in (-2.6, 0.0, 2.6): g.box("body", (x - 0.9, 2.4, -2.2), (x + 0.9, 13, -1.9), "veste")
    g.box("body", (-3.8, 23.4, -2.4), (3.8, 25.6, 2.4), "veste")                    # gola/capuz caído
    g.bone("head", (0, 25.5, 0), "body", "head")
    g.box("head", (-0.9, 25, -0.9), (0.9, 26.6, 0.9), "pele")
    g.box("head", (-2.8, 26.4, -3.0), (2.8, 31.6, 2.6), "pele", decals={"north": "veu_rosto"})
    g.box("head", (-3.2, 30.0, -3.3), (3.2, 32.6, 3.0), "veu")                    # capuz/turbante
    g.box("head", (-2.6, 32.6, -2.6), (2.6, 33.4, 2.4), "veu")
    g.box("head", (-3.0, 25.6, -3.25), (3.0, 30.0, -3.05), "veu", decals={"north": "veu_rosto"})   # véu na frente
    g.box("head", (-2.9, 24.4, -3.15), (2.9, 25.6, -2.95), "veu")
    g.box("head", (-0.4, 30.8, -3.5), (0.4, 31.6, -3.3), "orbe")                   # joia na testa
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 3.6, 24.5, 0), "body", f"arm:{sd}")
        g.box(b, (s * 4.1 - 1.0, 18.0, -1.0), (s * 4.1 + 1.0, 25.0, 1.0), "pele")
        g.box(b, (s * 4.2 - 0.85, 12.0, -0.85), (s * 4.2 + 0.85, 18.2, 0.85), "pele")
        for y in (13.0, 14.2, 15.4, 16.6): g.box(b, (s * 4.2 - 1.0, y, -1.0), (s * 4.2 + 1.0, y + 0.7, 1.0), "faixa")   # ataduras
        g.box(b, (s * 4.2 - 0.9, 10.2, -0.9), (s * 4.2 + 0.9, 12.2, 0.9), "pele")
        g.box(b, (s * 4.4 - 1.4, 22.0, -1.4), (s * 4.4 + 1.4, 25.4, 1.4), "veste")   # manga curta
    # cajado na mão direita (osso próprio para o código esconder/trocar)
    st = g.bone("cajado", (-4.2, 11, 0), "arm_r")
    for i in range(8): g.box(st, (-4.65, -1 + i * 3.2, -0.45), (-3.75, 2.2 + i * 3.2, 0.45), "madeira")
    for y in (8.4, 9.6, 10.8): g.box(st, (-4.8, y, -0.6), (-3.6, y + 0.6, 0.6), "faixa")
    g.box(st, (-4.9, 24.6, -0.6), (-3.5, 25.4, 0.6), "prata")
    pts = [(-4.2, 25.2), (-5.6, 26.2), (-6.2, 28.0), (-5.8, 29.8), (-4.2, 30.8), (-2.6, 29.8), (-2.2, 28.0), (-2.8, 26.2), (-4.2, 25.2)]
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        for k in range(3):
            x = xa + (xb - xa) * k / 3; y = ya + (yb - ya) * k / 3
            g.box(st, (x - 0.3, y - 0.3, -0.3), (x + 0.3, y + 0.3, 0.3), "prata")
    g.cbox(st, (-4.2, 28.0, 0), (1.8, 1.8, 1.8), "orbe")
    g.cbox(st, (-4.2, 28.0, 0), (1.8, 1.8, 1.8), "orbe", rot=(0, 45, 0))
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 1.7, 13, 0), "root", f"leg:{sd}")
        g.box(b, (s * 1.7 - 1.1, 2, -1.1), (s * 1.7 + 1.1, 13, 1.1), "pele")
        for y in (2.2, 3.4, 4.6, 5.8): g.box(b, (s * 1.7 - 1.25, y, -1.25), (s * 1.7 + 1.25, y + 0.7, 1.25), "faixa")
        g.box(b, (s * 1.7 - 1.3, 0, -2.8), (s * 1.7 + 1.3, 2.0, 1.3), "faixa")
    return g, dict(scale=1.0, height=2.1, notes="Osso cajado (mão direita) com orbe luminoso. Véu cobre o rosto.")

# ======================================================================== ZAËLITH (Selenaar · Amor)
def zaelith():
    A = Atlas(256, 32, seed=17)
    A.material("cristal", "crystal", "#E7A8FF", alpha=175, translucent=True)
    A.material("cristal2", "crystal", "#C02BE0", alpha=200, translucent=True)
    A.material("cabelo", "glow", "#E04BFF", "#FFE0FF", alpha=230)
    A.material("cabelo2", "glow", "#B03AD8", "#FF9CF2", alpha=210)
    A.material("nucleo", "glow", "#FF5CC8", "#FFFFFF")
    A.material("fio", "glow", "#FFB0E8", "#FFFFFF")
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, "#E7A8FF", 3, alpha=200)
        for cx in (0.3, 0.7):
            frect(A, x0, y0, S, cx - 0.11, 0.44, cx + 0.11, 0.50, "#FFFFFF", True)
            frect(A, x0, y0, S, cx - 0.13, 0.40, cx + 0.13, 0.42, "#9A2ABF")       # sobrancelha fina
        frect(A, x0, y0, S, 0.44, 0.70, 0.56, 0.73, "#C02BE0")
        for i in range(3): frect(A, x0, y0, S, 0.47, 0.12 + i * 0.07, 0.53, 0.16 + i * 0.07, "#FF5CC8", True)   # gema na testa
    A.decal("rosto", face)
    g = Geo("zaelith", A); g.soft = {"cristal": 0.4}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 16, 0), "root", "body")
    g.taper("body", (0, 14.5, 0), 5.2, 3.0, 3.2, 2.2, 4.0, 3, "cristal2")           # quadril
    g.box("body", (-1.6, 18.4, -1.2), (1.6, 19.6, 1.2), "cristal")                  # cintura fina
    g.taper("body", (0, 19.6, 0), 3.4, 2.4, 5.6, 3.0, 6.4, 4, "cristal")
    g.cbox("body", (0, 22.6, -1.4), (1.6, 1.6, 1.6), "nucleo", rot=(0, 45, 45))    # coração de cristal
    for s, _ in sides():                                                            # ombros facetados
        g.cbox("body", (s * 3.0, 25.4, 0), (1.8, 1.8, 1.8), "cristal2", rot=(45, 0, 45))
    # vestido de facetas em camadas
    for i, (w, y) in enumerate(((5.6, 12.0), (6.6, 9.0), (7.6, 6.0), (8.4, 3.0), (9.0, 0.4))):
        g.box("body", (-w / 2, y, -w / 2 * 0.6), (w / 2, y + 3.2, w / 2 * 0.6), "cristal2" if i % 2 else "cristal")
    g.bone("head", (0, 26.5, 0), "body", "head")
    g.box("head", (-0.7, 26, -0.7), (0.7, 27.4, 0.7), "cristal")
    g.box("head", (-2.6, 27.2, -2.8), (2.6, 32.4, 2.2), "cristal", decals={"north": "rosto"})
    g.box("head", (-2.2, 32.4, -2.2), (2.2, 33.2, 1.8), "cristal2")
    # cabelos de luz: mechas que balançam
    for i, (x, z, ln) in enumerate(((-2.2, 1.4, 10), (-1.0, 2.2, 13), (0.3, 2.4, 14), (1.5, 2.1, 12), (2.4, 1.2, 9), (-2.6, -0.8, 7), (2.7, -0.6, 7))):
        nm = f"cabelo_{i + 1}"; g.bone(nm, (x, 32.6, z), "head", f"sway:r:{i * 50}")
        g.limb(nm, (x, 33.2, z - 0.6), (x * 1.3, 31.0, z + 1.6), 0.9, "cabelo", taper_to=0.8)
        g.limb(nm, (x * 1.3, 31.0, z + 1.6), (x * 1.5, 31.0 - ln * 0.6, z + 2.6), 0.8, "cabelo2", taper_to=0.5)
        g.limb(nm, (x * 1.5, 31.0 - ln * 0.6, z + 2.6), (x * 1.4, 31.0 - ln, z + 2.4), 0.5, "cabelo", taper_to=0.2)
    for s, sd in sides():                                                           # tiara de cristal
        g.limb("head", (s * 1.6, 33.0, -2.0), (s * 2.6, 35.4, -1.6), 0.6, "cristal2", taper_to=0.2)
    g.limb("head", (0, 33.0, -2.2), (0, 36.0, -1.8), 0.7, "nucleo", taper_to=0.25)
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 3.4, 25.5, 0), "body", f"arm:{sd}")
        g.limb(b, (s * 3.6, 25.5, 0), (s * 4.6, 19.0, -0.4), 1.2, "cristal", taper_to=1.0)
        g.limb(b, (s * 4.6, 19.0, -0.4), (s * 5.0, 13.0, -1.2), 1.0, "cristal2", taper_to=0.8)
        for i in range(3): g.limb(b, (s * 5.0, 13.0, -1.2), (s * (5.1 + (i - 1) * 0.3), 11.2, -1.6 - i * 0.2), 0.3, "cristal", taper_to=0.15)
        g.cbox(b, (s * 4.7, 17.2, -0.6), (0.9, 0.9, 0.9), "nucleo", rot=(45, 0, 45))   # bracelete
    # fio de luz que liga o par (sai da mão; o código estica/aponta para o par)
    g.bone("fio_de_luz", (5.0, 12.4, -1.4), "arm_l")
    g.box("fio_de_luz", (4.85, 12.25, -17.4), (5.15, 12.55, -1.4), "fio")
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 1.6, 14.5, 0), "root", f"leg:{sd}")
        g.limb(b, (s * 1.6, 14, 0), (s * 1.8, 7, 0), 1.3, "cristal", taper_to=1.0)
        g.limb(b, (s * 1.8, 7, 0), (s * 1.8, 0.8, 0.4), 1.0, "cristal2", taper_to=0.7)
        g.box(b, (s * 1.8 - 0.7, 0, -2.0), (s * 1.8 + 0.7, 1.0, 0.8), "cristal")
    return g, dict(scale=1.0, height=2.2, translucent=True,
                   notes="Corpo translúcido. Osso fio_de_luz: estique no eixo z / aponte para o par (16 unidades por padrão).")

# ======================================================================== SILENTES (Umbrall · Morte)
def silente(coletor=False):
    A = Atlas(256, 32, seed=18 + (5 if coletor else 0))
    A.material("osso", "bone", "#CFCABC")
    A.material("osso2", "bone", "#9A968A")
    A.material("pele", "skin", "#3A3640", spots="#24222A", n_spots=8)
    A.material("trapo1", "fabric", "#5A2A1A")                                      # restos de roupas de outros planetas
    A.material("trapo2", "fabric", "#1F5A34")
    A.material("trapo3", "fabric", "#2E1F5A")
    A.material("capuz", "fabric", "#0E0E14", trim="#2A2A38")
    A.material("alma", "glow", "#9A9AFF", "#FFFFFF")
    A.material("ferro", "metal", "#4A4A52")
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, "#CFCABC", 4)
        for cx in (0.3, 0.7):
            fdisc(A, x0, y0, S, cx, 0.42, 0.14, "#1A1A1E"); fdisc(A, x0, y0, S, cx, 0.42, 0.08, "#FFFFFF", True)   # olhos brancos sem pupila
        frect(A, x0, y0, S, 0.45, 0.55, 0.55, 0.66, "#2A2A2E")
        for i in range(6): frect(A, x0, y0, S, 0.22 + i * 0.1, 0.74, 0.28 + i * 0.1, 0.86, "#E8E4D8")
        frect(A, x0, y0, S, 0.2, 0.72, 0.8, 0.74, "#2A2A2E"); frect(A, x0, y0, S, 0.2, 0.86, 0.8, 0.88, "#2A2A2E")
        frect(A, x0, y0, S, 0.66, 0.12, 0.68, 0.34, "#5A5850")                     # rachadura
    A.decal("rosto", face)
    def hood_face(A, x0, y0, S):
        fbg(A, x0, y0, S, "#050508", 2)
        for cx in (0.34, 0.66): fdisc(A, x0, y0, S, cx, 0.5, 0.07, "#FFFFFF", True)
    A.decal("capuz_rosto", hood_face)
    name = "coletor" if coletor else "silente"
    g = Geo(name, A); g.soft = {"osso": 0.0, "capuz": 0.6, "pele": 0.4}
    H = 1.25 if coletor else 1.0
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 13 * H, 0), "root", "body")
    # coluna, bacia e costelas expostas
    g.box("body", (-3.2, 12 * H, -1.6), (3.2, 14.5 * H, 1.6), "osso2")
    for i in range(6): g.box("body", (-0.6, (14.5 + i * 1.5) * H, 0.2), (0.6, (15.7 + i * 1.5) * H, 1.4), "osso")
    for i in range(5):
        y = (17 + i * 1.5) * H; w = 3.6 - abs(i - 2) * 0.3
        for s, _ in sides():
            if not coletor and s > 0 and i == 3: continue                           # costela quebrada
            g.box("body", (s * 0.6 if s > 0 else -w, y, -2.0), (w if s > 0 else -0.6, y + 0.6 * H, -1.4), "osso")
            g.box("body", (s * w - 0.3, y, -1.4), (s * w + 0.3, y + 0.6 * H, 1.2), "osso2")
    g.box("body", (-3.4, 24.5 * H, -1.8), (3.4, 26 * H, 1.8), "osso")              # clavículas
    g.box("body", (-2.6, 14.5 * H, 0.6), (2.6, 22 * H, 1.6), "pele")               # pele seca nas costas
    if coletor:
        g.box("body", (-4.6, 2.0, -3.2), (4.6, 32.0, 3.0), "capuz")                 # manto longo
        g.box("body", (-5.0, 0.2, -3.6), (5.0, 2.4, 3.4), "capuz")
        g.box("body", (-1.0, 4.0, -3.4), (1.0, 31.0, -3.2), "trapo3")
    else:
        g.box("body", (-3.6, 9.0, -1.9), (3.6, 14.0, 1.9), "trapo1")                # restos de roupa
        g.box("body", (-3.8, 19.5, -2.3), (0.4, 25.0, 2.0), "trapo2")              # metade de um colete
        for x in (-3.2, -1.6, 0.0): g.box("body", (x, 7.0 - (x % 2), -2.0), (x + 1.0, 9.2, -1.8), "trapo1")
    g.bone("head", (0, 26 * H, 0), "body", "head")
    g.box("head", (-0.7, 25.6 * H, -0.7), (0.7, 27.2 * H, 0.7), "osso2")
    if coletor:
        g.box("head", (-3.2, 27.0 * H, -3.6), (3.2, 34.2 * H, 3.0), "capuz", decals={"north": "capuz_rosto"})
        g.limb("head", (0, 34.2 * H, -2.0), (0, 36.6 * H, 2.4), 2.4, "capuz", taper_to=0.8)   # ponta do capuz
    else:
        g.box("head", (-3.0, 27.2, -3.2), (3.0, 33.2, 2.8), "osso", decals={"north": "rosto"})
        g.box("head", (-2.6, 26.0, -3.4), (2.6, 27.4, 0.0), "osso2")               # mandíbula
        g.box("head", (-3.2, 31.2, -1.0), (-1.0, 33.6, 3.0), "pele")                # resto de couro cabeludo
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 3.8, 25 * H, 0), "body", f"arm:{sd}")
        broken = (not coletor) and s > 0
        g.box(b, (s * 4.2 - 0.55, 18.5 * H, -0.55), (s * 4.2 + 0.55, 25.0 * H, 0.55), "osso")
        g.box(b, (s * 4.2 - 0.8, 17.9 * H, -0.8), (s * 4.2 + 0.8, 18.9 * H, 0.8), "osso2")
        if not broken:
            g.box(b, (s * 4.0 - 0.45, 12.0 * H, -0.7), (s * 4.0 + 0.0, 18.2 * H, 0.0), "osso")
            g.box(b, (s * 4.4 - 0.0, 12.0 * H, -0.2), (s * 4.4 + 0.4, 18.2 * H, 0.5), "osso2")
            for i in range(4): g.box(b, (s * 4.2 - 0.15 + (i - 1.5) * 0.35, 9.8 * H, -0.3), (s * 4.2 + 0.15 + (i - 1.5) * 0.35, 12.1 * H, 0.0), "osso")
        else:
            g.box(b, (s * 4.2 - 0.45, 15.2, -0.45), (s * 4.2 + 0.45, 18.0, 0.45), "osso2")   # braço partido
        if coletor:
            g.box(b, (s * 4.4 - 1.6, 17.0 * H, -1.6), (s * 4.4 + 1.6, 25.6 * H, 1.6), "capuz")   # manga
    if coletor:                                                                     # jarro de almas na mão esquerda
        j = g.bone("jarro_almas", (5.0, 11.5, -0.5), "arm_l")
        g.box(j, (4.0, 7.2, -1.4), (6.0, 11.0, 0.6), "ferro")
        g.box(j, (4.2, 7.6, -1.6), (5.8, 10.6, 0.8), "alma")
        g.box(j, (4.4, 11.0, -1.0), (5.6, 11.6, 0.2), "ferro")
        g.box(j, (4.9, 11.6, -0.6), (5.1, 12.6, -0.2), "ferro")
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 1.8, 12 * H, 0), "root", f"leg:{sd}")
        g.box(b, (s * 1.8 - 0.55, 6.5 * H, -0.55), (s * 1.8 + 0.55, 12.2 * H, 0.55), "osso")
        g.box(b, (s * 1.8 - 0.8, 5.9 * H, -0.9), (s * 1.8 + 0.8, 6.9 * H, 0.7), "osso2")
        g.box(b, (s * 1.8 - 0.5, 1.0, -0.5), (s * 1.8 + 0.5, 6.2 * H, 0.5), "osso")
        g.box(b, (s * 1.8 - 1.0, 0, -2.4), (s * 1.8 + 1.0, 1.0, 0.8), "osso2")
        if not coletor and s < 0: g.box(b, (s * 1.8 - 1.2, 0, -2.6), (s * 1.8 + 1.2, 3.2, 1.0), "trapo3")   # bota de outro planeta
    return g, dict(scale=1.0, height=2.5 if coletor else 2.0,
                   notes=("O Coletor: encapuzado, carrega jarro de almas (osso jarro_almas)." if coletor else
                          "Corpo assimétrico (braço partido, costela quebrada) e restos de roupas."))

# ======================================================================== BROTOS (Gênese · Vida)
def broto():
    A = Atlas(128, 32, seed=19)
    A.material("semente", "glow", "#F4F8FF", "#FFFFFF", alpha=255)
    A.material("casca", "skin", "#E8EEF4", spots="#FFFFFF", spot_glow=True, n_spots=5)
    A.material("folha", "leaf", "#E8FFE8", "#FFFFFF")
    A.material("folha2", "leaf", "#C8F4D0", "#F0FFF4")
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, "#F4F8FF", 2, grad=0.05)
        for cx in (0.33, 0.67): fdisc(A, x0, y0, S, cx, 0.5, 0.09, "#6A8AAA")
        frect(A, x0, y0, S, 0.42, 0.68, 0.58, 0.72, "#8AAAC8")
    A.decal("rosto", face)
    g = Geo("broto", A); g.soft = {"casca": 0.6}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 2, 0), "root", "body")
    g.blob("body", (0, 3.6, 0), 2.4, 2.8, 2.4, "casca", layers=5)
    g.box("body", (-1.6, 2.4, -2.5), (1.6, 5.0, -2.2), "semente", decals={"north": "rosto"})
    g.bone("head", (0, 6, 0), "body", "head")
    g.box("head", (-0.3, 6.2, -0.3), (0.3, 8.0, 0.3), "folha2")                     # broto central
    for i, (s, ang) in enumerate(((-1, 0), (1, 0), (0, 1))):
        nm = f"folha_{i + 1}"; g.bone(nm, (0, 7.6, 0), "head", f"sway:r:{i * 120}")
        if ang == 0:
            g.limb(nm, (0, 7.6, 0), (s * 2.6, 9.4, 0.4), 1.4, "folha", taper_to=0.6)
            g.limb(nm, (s * 2.6, 9.4, 0.4), (s * 3.6, 9.0, 0.6), 0.8, "folha2", taper_to=0.3)
        else:
            g.limb(nm, (0, 7.6, 0), (0, 10.0, 1.6), 1.2, "folha", taper_to=0.5)
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 1.0, 1.4, 0), "root", f"leg:{sd}")
        g.box(b, (s * 1.0 - 0.4, 0, -0.6), (s * 1.0 + 0.4, 1.4, 0.4), "casca")
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 2.2, 4.0, 0), "body", f"arm:{sd}")
        g.limb(b, (s * 2.2, 4.0, 0), (s * 3.2, 2.8, -0.4), 0.5, "casca", taper_to=0.35)
    return g, dict(scale=1.0, height=0.6, notes="Folhas em ossos folha_1..3 (abrem e fecham). Brilho total.")

# ======================================================================== ECOS CINZENTOS (do Nulo)
def eco_cinzento():
    A = Atlas(256, 32, seed=20)
    A.material("cinza", "gray", "#8A8A8E", dissolve=0.0)
    A.material("cinza2", "gray", "#6E6E72", dissolve=0.0)
    A.material("fumaca", "gray", "#9A9A9E", dissolve=0.85, alpha=200, translucent=True)
    A.material("vazio", "solid", "#2A2A2E", noise=2, edges=False)
    def face(A, x0, y0, S):
        fbg(A, x0, y0, S, "#8A8A8E", 1, grad=0.04)
        fdisc(A, x0, y0, S, 0.5, 0.52, 0.22, "#2A2A2E")                             # só um buraco sem expressão
        fdisc(A, x0, y0, S, 0.5, 0.52, 0.12, "#121214")
    A.decal("rosto", face)
    g = Geo("eco_cinzento", A); g.soft = {"cinza": 0.8, "cinza2": 0.6}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 12, 0), "root", "float")
    g.taper("body", (0, 3.0, 0), 2.0, 1.6, 5.0, 3.2, 9.0, 5, "fumaca")             # cauda de fumaça (sem pernas)
    g.box("body", (-3.0, 12, -1.8), (3.0, 20, 1.8), "cinza")
    g.box("body", (-3.6, 18.5, -2.0), (3.6, 21.5, 2.2), "cinza2")                   # ombros caídos
    for i in range(4): g.box("body", (-3.2 + i * 2.0, 10.4 - i % 2, -1.9), (-2.2 + i * 2.0, 12.2, -1.6), "fumaca")
    g.bone("head", (0, 21, -1.0), "body", "head")
    g.box("head", (-2.6, 20.4, -5.0), (2.6, 25.6, -0.2), "cinza", decals={"north": "rosto"})
    g.box("head", (-2.2, 25.6, -4.4), (2.2, 26.4, -0.8), "cinza2")
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 3.4, 20.5, 0), "body", f"arm:{sd}")
        g.limb(b, (s * 3.6, 20.8, -0.2), (s * 5.2, 13.0, -1.6), 1.3, "cinza", taper_to=1.0)
        g.limb(b, (s * 5.2, 13.0, -1.6), (s * 5.6, 5.0, -3.4), 1.0, "cinza2", taper_to=0.7)
        for i in range(3):                                                          # dedos que se desfazem
            g.limb(b, (s * 5.6, 5.0, -3.4), (s * (5.6 + (i - 1) * 0.5), 2.2, -4.2), 0.35, "fumaca", taper_to=0.15)
    return g, dict(scale=1.0, height=1.7, translucent=True, notes="Flutua (sem pernas). Bordas de fumaça translúcidas.")

RACES = [("brakkari", brakkari), ("ghallos", ghallos), ("skrell_vath", skrellvath), ("veltari", veltari),
         ("aelinos", aelinos), ("ves_kai", veskai), ("zaelith", zaelith), ("silente", lambda: silente(False)),
         ("coletor", lambda: silente(True)), ("broto", broto), ("eco_cinzento", eco_cinzento)]

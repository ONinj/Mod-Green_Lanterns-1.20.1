# Bosses das Tropas — 8 criaturas ORIGINAIS do mod (uma por emoção), GeckoLib, textura 512x512.
# Frente = -z, esquerda do boss = +x. Ossos com papéis para as animações automáticas (core.animations) + extras.
import math, sys
sys.path.insert(0, "/home/claude/lore3d"); sys.path.insert(0, "/home/claude/especies")
from core import *
from races import frect, fdisc, fbg, sides
from species import EMBLEMAS

def kf(d): return {"rotation": d}

def emblema_decal(A, nome, tropa, cor, fundo=None, borda=None):
    """emblema da Tropa (o do mod, 9x9) ampliado 6x num slot, aceso"""
    def f(A, x0, y0, S):
        if fundo: fbg(A, x0, y0, S, fundo, 3)
        else:
            for y in range(S):
                for x in range(S): A.put(x0 + x, y0 + y, (0, 0, 0), 0)
        grid = EMBLEMAS[tropa]; n = len(grid); c = 6; off = (S - n * c) // 2
        if borda: fdisc(A, x0, y0, S, 0.5, 0.5, 0.49, borda); fdisc(A, x0, y0, S, 0.5, 0.5, 0.44, fundo or "#000000")
        for r, row in enumerate(grid):
            for k, ch in enumerate(row):
                if ch == "#":
                    for yy in range(c):
                        for xx in range(c):
                            e = yy in (0, c - 1) or xx in (0, c - 1)
                            A.put(x0 + off + k * c + xx, y0 + off + r * c + yy, lt(cor, 0.0 if e else 0.35), 255, True)
    A.decal(nome, f)

def morte(extra, ossos_corpo="body"):
    extra["misc.death"] = {"animation_length": 2.0, "bones": {
        "root": {"rotation": {"0.0": [0, 0, 0], "0.6": [-10, 0, 0], "1.6": [80, 0, 0], "2.0": [90, 0, 0]},
                 "position": {"0.0": [0, 0, 0], "1.6": [0, -2, 0], "2.0": [0, -4, 0]}}}}

def mao(g, b, p, s, mat, garra, dedos=4, L=3.0, w=0.9, dz=-1):
    """mão com dedos curvados; p = pulso"""
    x, y, z = p
    g.cbox(b, (x, y - 1.6, z), (2.6, 3.2, 3.0), mat)
    for i in range(dedos):
        zz = z - 1.2 + i * (2.4 / max(1, dedos - 1))
        g.limb(b, (x, y - 3.0, zz), (x + s * 0.3, y - 3.0 - L * 0.6, zz + dz * 0.6), w, mat, taper_to=w * 0.8)
        g.limb(b, (x + s * 0.3, y - 3.0 - L * 0.6, zz + dz * 0.6), (x + s * 0.1, y - 3.0 - L, zz + dz * 1.6), w * 0.8, garra, taper_to=0.25)
    g.limb(b, (x - s * 1.0, y - 2.0, z - 1.4), (x - s * 1.4, y - 4.0, z - 2.6), w, garra, taper_to=0.3)

# ======================================================================== 1. KARVHUL, O FORJADOR RUBRO (Fúria)
def karvhul():
    A = Atlas(512, 64, seed=101)
    A.material("crosta", "magma", "#1C1210", "#FF3A0A")
    A.material("crosta2", "magma", "#2A1612", "#FF6A12")
    A.material("basalto", "cracks", "#161212", "#FF2A08", density=2)
    A.material("lava", "glow", "#FF4A0A", "#FFE29A")
    A.material("ferro", "metal", "#2C2626")
    A.material("chifre", "bone", "#2A201C")
    A.material("brasa", "glow", "#FF1A06", "#FFB070")
    def boca(A, x0, y0, S):   # grade de forja no lugar da boca
        fbg(A, x0, y0, S, "#140C0A", 3)
        frect(A, x0, y0, S, 0.08, 0.15, 0.92, 0.85, "#FF5A10", True); frect(A, x0, y0, S, 0.2, 0.3, 0.8, 0.7, "#FFE08A", True)
        for i in range(6): frect(A, x0, y0, S, 0.06 + i * 0.16, 0.1, 0.14 + i * 0.16, 0.9, "#1A1210")
        frect(A, x0, y0, S, 0.06, 0.45, 0.94, 0.55, "#1A1210")
    A.decal("boca", boca)
    def olho(A, x0, y0, S):
        fbg(A, x0, y0, S, "#140C0A", 3); fdisc(A, x0, y0, S, 0.5, 0.5, 0.4, "#FF4A0A", True); fdisc(A, x0, y0, S, 0.5, 0.5, 0.18, "#FFF0C0", True)
    A.decal("olho", olho)
    emblema_decal(A, "emb", "vermelho", "#FF4A1A", fundo="#1A0A08", borda="#3A1A14")
    g = Geo("karvhul_forjador_rubro", A); g.soft = {"crosta": 1.6, "crosta2": 1.4}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 34, 0), "root", "body")
    # tronco curvado para a frente: peito enorme, cintura, fornalha no peito
    g.taper("body", (0, 30, 2), 18, 14, 30, 20, 22, 5, "crosta")
    g.box("body", (-12, 24, -6), (12, 32, 9), "crosta2")                          # abdômen
    g.box("body", (-7, 36, -10.6), (7, 48, -9), "ferro")                           # porta da fornalha
    g.box("body", (-6, 37, -11.2), (6, 47, -10.4), "brasa", decals={"north": "emb"})
    for i in range(4): g.box("body", (-7.5, 37 + i * 3, -11.6), (7.5, 37.6 + i * 3, -10.6), "ferro")
    for s, _ in sides():                                                              # placas peitorais rachadas
        g.box("body", (s * 7.5 - 4.5, 40, -10), (s * 7.5 + 4.5, 50, -6), "basalto", rot=(0, -s * 12, 0))
        for i in range(3): g.box("body", (s * 12 - 1.5, 26 + i * 3, -6 + i), (s * 12 + 1.5, 28 + i * 3, 6 + i), "basalto")
    for i in range(7):                                                                # cristas de basalto nas costas
        z = -2 + i * 2.2; y = 52 - i * 2.6
        g.limb("body", (0, y, 8 + i * 0.8), (0, y + 8 - i * 0.6, 14 + i * 1.2), 3.4 - i * 0.3, "basalto", taper_to=0.6)
        for s, _ in sides(): g.limb("body", (s * 6, y - 1, 7 + i * 0.6), (s * 9, y + 5 - i * 0.5, 12 + i), 2.4 - i * 0.2, "chifre", taper_to=0.5)
    g.box("body", (-11, 22, -7), (11, 25, 9), "ferro")                                # cinturão de correntes
    for i in range(6): g.box("body", (-11.5 + i * 4, 18, -7.6), (-9.5 + i * 4, 22.4, -6.8), "ferro")
    # cabeça afundada entre os ombros
    g.bone("head", (0, 50, -6), "body", "head")
    g.box("head", (-6, 46, -16), (6, 56, -5), "crosta")
    g.box("head", (-6.6, 52, -16.6), (6.6, 56.6, -11), "basalto")                    # testa blindada
    g.box("head", (-4.5, 46.4, -16.8), (4.5, 50.4, -16), "lava", decals={"north": "boca"})
    for s, _ in sides():
        g.box("head", (s * 3 - 1.4, 51.2, -16.9), (s * 3 + 1.4, 52.8, -16.2), "lava", decals={"north": "olho"})
        g.limb("head", (s * 5, 55, -10), (s * 12, 60, -4), 3.2, "chifre", steps=2, taper_to=2.2)     # chifres para trás
        g.limb("head", (s * 12, 60, -4), (s * 14, 58, 4), 2.2, "chifre", steps=2, taper_to=0.6)
        g.box("head", (s * 14 - 0.5, 57.5, 3.5), (s * 14 + 0.5, 58.5, 4.5), "lava")
    jb = g.bone("mandibula", (0, 47, -8), "head", "jaw")
    g.box(jb, (-5.5, 42, -15.5), (5.5, 47, -6), "crosta2")
    for i in range(5): g.limb(jb, (-4 + i * 2, 47, -15.2), (-4 + i * 2, 49.4, -15.6), 0.9, "chifre", taper_to=0.3)
    # 4 braços: par de cima com punhos-martelo, par de baixo com ganchos em correntes
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 15, 50, -1), "body", f"arm:{sd}")
        g.blob(b, (s * 16, 50, -1), 6.5, 5.0, 6.0, "crosta2", layers=4)                    # ombro
        g.limb(b, (s * 17, 48, -1), (s * 21, 34, -3), 7.0, "crosta", taper_to=6.0)
        g.limb(b, (s * 21, 34, -3), (s * 22, 20, -6), 6.0, "crosta2", taper_to=6.0)
        g.cbox(b, (s * 22, 16, -6.5), (10, 9, 10), "basalto")                               # punho de escória
        g.cbox(b, (s * 22, 16, -6.5), (10.4, 1.6, 8), "lava"); g.cbox(b, (s * 22, 16, -6.5), (1.6, 7, 10.4), "lava")
        for k in range(3): g.limb(b, (s * 22 - 3 + k * 3, 12, -10), (s * 22 - 3 + k * 3, 9.6, -12), 1.6, "chifre", taper_to=0.5)
        for k in range(4): g.box(b, (s * 21 - 3, 28 + k * 3, -6.5), (s * 21 + 3, 29 + k * 3, -0.5), "ferro")   # argolas
        b2 = g.bone(f"braco_baixo_{sd}", (s * 12, 34, -4), "body", f"arm:{'l' if sd == 'r' else 'r'}")
        g.limb(b2, (s * 12, 34, -4), (s * 16, 26, -9), 4.0, "crosta", taper_to=3.4)
        g.limb(b2, (s * 16, 26, -9), (s * 17, 17, -13), 3.4, "crosta2", taper_to=3.0)
        mao(g, b2, (s * 17, 17, -13), s, "crosta2", "chifre", dedos=3, L=3.4, w=1.2)
        for k in range(5): g.cbox(b2, (s * 17.5, 12 - k * 2.2, -13), (1.2, 2.0, 2.4 if k % 2 else 1.2), "ferro")
        g.limb(b2, (s * 17.5, 1.5, -13), (s * 19, -1, -15), 2.0, "lava", taper_to=0.6)        # gancho em brasa
    # pernas grossas
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 8, 26, 3), "root", f"leg:{sd}")
        g.blob(b, (s * 8.5, 20, 3), 6, 7, 6, "crosta", layers=4)
        g.limb(b, (s * 9, 16, 3), (s * 10, 5, 1), 6.0, "crosta2", taper_to=5.5)
        g.cbox(b, (s * 10, 2.5, -1), (8, 5, 11), "basalto")
        for k in range(3): g.limb(b, (s * 10 - 2.6 + k * 2.6, 2, -6.2), (s * 10 - 2.6 + k * 2.6, 0.2, -8.4), 1.6, "chifre", taper_to=0.5)
        g.box(b, (s * 10 - 3.2, 9, -2.6), (s * 10 + 3.2, 10, 4.6), "ferro")
    extra = {"attack.smash": {"animation_length": 1.4, "bones": {
                "arm_r": kf({"0.0": [0, 0, 0], "0.5": [-160, 0, -10], "0.8": [20, 0, 0], "1.4": [0, 0, 0]}),
                "arm_l": kf({"0.0": [0, 0, 0], "0.5": [-160, 0, 10], "0.8": [20, 0, 0], "1.4": [0, 0, 0]}),
                "body": kf({"0.0": [0, 0, 0], "0.5": [-12, 0, 0], "0.8": [22, 0, 0], "1.4": [0, 0, 0]})}},
             "attack.chains": {"animation_length": 1.0, "bones": {
                "braco_baixo_r": kf({"0.0": [0, 0, 0], "0.3": [-40, 0, -60], "0.6": [-70, 0, 40], "1.0": [0, 0, 0]}),
                "braco_baixo_l": kf({"0.0": [0, 0, 0], "0.3": [-40, 0, 60], "0.6": [-70, 0, -40], "1.0": [0, 0, 0]})}},
             "misc.rage": {"loop": True, "animation_length": 0.3, "bones": {
                "body": {"position": ["math.sin(query.anim_time * 2400) * 0.3", 0, 0]}, "mandibula": kf({"0.0": [20, 0, 0], "0.15": [28, 0, 0], "0.3": [20, 0, 0]})}}}
    morte(extra)
    return g, dict(scale=1.6, label="Karvhul, o Forjador Rubro (Fúria)", extra=extra,
                   notes="Titã de forja com 4 braços. attack.smash (punhos de escória no chão), attack.chains (ganchos em brasa), "
                         "misc.rage (fase 2, treme e ruge). Fornalha do peito com o emblema da Fúria no glowmask.")

# ======================================================================== 2. VESK MORRATH, O CARCEREIRO (Medo)
def vesk():
    A = Atlas(512, 64, seed=102)
    A.material("manto", "fabric", "#121014", trim="#B89A10")
    A.material("manto2", "fabric", "#1C1A10", trim=None)
    A.material("pele", "skin", "#2A2622", spots="#3A3420")
    A.material("ferro", "metal", "#24221E")
    A.material("ouro", "metal", "#C8A010", rivets=False)
    A.material("chama", "glow", "#FFD80A", "#FFFFFF")
    A.material("osso", "bone", "#D8CCA0")
    A.material("quitina", "carapace", "#0E0E0E", "#8A7400", stripe=8)
    A.material("vidro", "crystal", "#FFE860", alpha=90, translucent=True)
    emblema_decal(A, "emb", "amarelo", "#FFD80A", fundo="#141210", borda="#5A4A08")
    g = Geo("vesk_morrath_carcereiro", A); g.soft = {"manto": 0.6, "pele": 0.6}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 30, 0), "root", "body")
    # corpo magro e alto, curvado; manto em camadas rasgadas
    g.taper("body", (0, 28, 0), 9, 5, 12, 6, 16, 4, "manto")
    g.box("body", (-3, 26, -2.5), (3, 30, 2.5), "pele")
    g.cbox("body", (0, 38, -3.2), (6, 6, 0.6), "manto2", decals={"north": "emb"})          # brasão no peito
    for s, _ in sides():                                                                    # costelas de metal na frente
        for i in range(4): g.limb("body", (s * 0.8, 35 - i * 2, -3.3), (s * 5.2, 33.5 - i * 2, -2.6), 0.6, "ferro", taper_to=0.5)
    for i, (w, y0, y1, d) in enumerate(((13, 6, 28, 7), (15, 2, 18, 8.4), (17, 0, 8, 9.6))):        # saia do manto (3 camadas)
        g.box("body", (-w / 2, y0, -d / 2), (w / 2, y1, d / 2), "manto" if i % 2 == 0 else "manto2")
    for k in range(9):                                                                      # tiras rasgadas na barra
        x = -7.5 + k * 1.9; h = 2 + (k * 7) % 4
        g.box("body", (x, -h + 0.4, -4.9 + (k % 2) * 0.3), (x + 1.4, 0.6, -4.6 + (k % 2) * 0.3), "manto")
        g.box("body", (x, -h + 0.4, 4.6), (x + 1.4, 0.6, 4.9), "manto")
    # gola alta e o molho de chaves na cintura
    for s, _ in sides(): g.box("body", (s * 6.5 - 1, 40, -4), (s * 6.5 + 1, 47, 4), "manto2", rot=(0, 0, -s * 14))
    g.box("body", (-6.6, 26, -3.8), (6.6, 27.4, 3.8), "ouro")
    g.bone("chaves", (4.5, 26, -3.8), "body", "sway")
    g.cbox("chaves", (4.5, 24.5, -4.2), (2.4, 2.4, 0.5), "ouro")
    for k in range(5): g.limb("chaves", (3.8 + k * 0.4, 23.4, -4.2), (3.4 + k * 0.6, 18.8 - k * 0.4, -4.6), 0.5, "ferro", taper_to=0.5)
    # cabeça: GAIOLA-LANTERNA com chama, sem rosto
    g.bone("head", (0, 44, -1), "body", "head")
    g.box("head", (-1.5, 43, -2.5), (1.5, 46, 0.5), "pele")
    g.box("head", (-4.5, 45.5, -5), (4.5, 46.5, 4), "ferro")                                # base
    g.box("head", (-4.5, 55.5, -5), (4.5, 56.5, 4), "ferro")                                # tampa
    for k in range(4):                                                                      # grades
        for s in (-1, 1):
            g.box("head", (-4.3 + k * 2.6, 46.5, s * 4.4 - 0.6 - 0.25), (-3.7 + k * 2.6, 55.5, s * 4.4 - 0.25), "ferro") if False else None
    for x in (-4.4, -1.5, 1.5, 4.4):
        for z in (-4.9, 3.9):
            g.box("head", (x - 0.35, 46.5, z), (x + 0.35, 55.5, z + 0.7), "ferro")
    for z in (-1.5, 1.5):
        for x in (-4.9, 4.2):
            g.box("head", (x, 46.5, z - 0.35), (x + 0.7, 55.5, z + 0.35), "ferro")
    g.box("head", (-4.2, 46.5, -4.7), (4.2, 55.5, 3.7), "vidro")
    g.bone("chama", (0, 48, -0.5), "head", "pulse")
    g.blob("chama", (0, 50, -0.5), 2.4, 3.2, 2.4, "chama", layers=4)
    g.limb("chama", (0, 52.5, -0.5), (0.6, 55, -0.2), 1.6, "chama", taper_to=0.4)
    g.taper("head", (0, 56.5, -0.5), 6, 6, 1.2, 1.2, 3.5, 3, "ferro")                     # cúpula
    g.box("head", (-0.4, 60, -0.9), (0.4, 62, -0.1), "ouro")
    g.limb("head", (0, 62, -0.5), (0, 64, -0.5), 0.4, "ferro")
    # braços longos e finos com garras compridas
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 6, 42, 0), "body", f"arm:{sd}")
        g.cbox(b, (s * 6.8, 42, 0), (3.4, 3.0, 3.4), "manto2")
        g.limb(b, (s * 7, 41, 0), (s * 9, 30, -2), 2.0, "manto", taper_to=1.8)
        g.box(b, (s * 9 - 2.2, 22, -4.4), (s * 9 + 2.2, 30.5, 0.4), "manto", rot=(0, 0, s * 6), pivot=(s * 9, 30, -2))   # manga larga
        g.limb(b, (s * 9, 30, -2), (s * 10, 19, -5), 1.4, "pele", taper_to=1.2)
        mao(g, b, (s * 10, 19, -5), s, "pele", "osso", dedos=4, L=5.0, w=0.6)
    # pernas finas (escondidas pelo manto)
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 2.5, 22, 0), "root", f"leg:{sd}")
        g.limb(b, (s * 2.5, 22, 0), (s * 2.8, 3, 0), 1.8, "pele", taper_to=1.4)
        g.box(b, (s * 2.8 - 1.2, 0, -3.0), (s * 2.8 + 1.2, 2, 1), "ferro")
    # quatro patas de aranha saindo das costas (por baixo do manto)
    for s, sd in sides():
        for k, (ang, ph) in enumerate(((35, 0), (-5, 120))):
            nm = f"pata_{sd}_{k + 1}"; py = 40 - k * 4
            g.bone(nm, (s * 3, py, 3), "body", f"sway:{sd}:{ph}")
            a = math.radians(ang)
            p1 = (s * 13, py + 9, 3 + 9 * math.sin(a)); p2 = (s * 20, py - 6 - k * 4, 3 + 14 * math.sin(a)); p3 = (s * 22, py - 22 - k * 3, 3 + 16 * math.sin(a))
            g.limb(nm, (s * 3, py, 3), p1, 1.8, "quitina", taper_to=1.5)
            g.cbox(nm, p1, (1.8, 1.8, 1.8), "ouro")
            g.limb(nm, p1, p2, 1.5, "quitina", steps=2, taper_to=1.0)
            g.limb(nm, p2, p3, 1.0, "quitina", steps=2, taper_to=0.3)
    extra = {"attack.grab": {"animation_length": 1.2, "bones": {
                "arm_r": kf({"0.0": [0, 0, 0], "0.4": [-90, 0, 20], "0.7": [-60, 0, -10], "1.2": [0, 0, 0]}),
                "arm_l": kf({"0.0": [0, 0, 0], "0.4": [-90, 0, -20], "0.7": [-60, 0, 10], "1.2": [0, 0, 0]})}},
             "attack.legs": {"animation_length": 1.0, "bones": {
                f"pata_{sd}_1": kf({"0.0": [0, 0, 0], "0.3": [-70, 0, 0], "0.5": [20, 0, 0], "1.0": [0, 0, 0]}) for sd in "rl"}},
             "misc.phase2": {"loop": True, "animation_length": 0.5, "bones": {
                "chama": {"scale": {"0.0": [1, 1, 1], "0.25": [1.6, 1.8, 1.6], "0.5": [1, 1, 1]}}}}}
    morte(extra)
    return g, dict(scale=1.3, label="Vesk Morrath, o Carcereiro (Medo)", translucent=True, extra=extra,
                   notes="Cabeça é uma gaiola-lanterna com chama (vidro translúcido, use RenderType translúcido). "
                         "4 patas de aranha nas costas (attack.legs), garras longas (attack.grab), misc.phase2 = chama cresce.")

# ======================================================================== 3. GHOR'MADDU, A ARCA VIVA (Avareza)
def ghormaddu():
    A = Atlas(512, 64, seed=103)
    A.material("casco", "coins", "#5A3A10", "#E8B840")
    A.material("carapaca", "carapace", "#3A1E0A", "#E07A10", stripe=7)
    A.material("carapaca2", "carapace", "#2A1408", "#B05A08", stripe=10)
    A.material("ouro", "metal", "#D8A830", rivets=False)
    A.material("gema", "crystal", "#FF8A10", alpha=230, glowing=True)
    A.material("gema2", "crystal", "#3AD0FF", alpha=230, glowing=True)
    A.material("olho", "glow", "#FF8A10", "#FFF0C0")
    A.material("osso", "bone", "#C8B48A")
    A.material("aco", "metal", "#9AA0A8")
    emblema_decal(A, "emb", "laranja", "#FF9A20", fundo="#3A1E0A", borda="#D8A830")
    g = Geo("ghormaddu_arca_viva", A); g.soft = {"carapaca": 1.0, "casco": 1.4}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 14, 0), "root", "body")
    g.blob("body", (0, 14, 2), 16, 6, 14, "carapaca", layers=4)                       # corpo achatado
    g.box("body", (-12, 8, -12), (12, 12, 14), "carapaca2")                            # barriga
    # casco = monte de tesouro
    g.blob("body", (0, 22, 4), 15, 8, 13, "casco", layers=5)
    g.blob("body", (-2, 29, 6), 9, 5, 8, "casco", layers=4)
    for (x, y, z, r) in ((-9, 26, -2, 2.2), (8, 27, 0, 2.6), (2, 33, 4, 2.0), (-5, 30, 12, 1.8), (10, 23, 12, 2.2)):   # gemas engastadas
        g.cbox("body", (x, y, z), (r, r, r), "gema" if x < 3 else "gema2", rot=(45, 45, 0))
    # coroa espetada no topo, espada e taça enterradas no casco
    g.box("body", (-4, 33, 3), (2, 35, 9), "ouro")
    for k in range(5): g.limb("body", (-3.6 + k * 1.3, 35, 3.4 + (k % 2) * 4.4), (-3.6 + k * 1.3, 37.5, 3.4 + (k % 2) * 4.4), 0.8, "ouro", taper_to=0.3)
    g.limb("body", (9, 22, 6), (14, 36, 10), 1.2, "aco", taper_to=0.5)                  # espada
    g.box("body", (8.2, 24.6, 3.2), (12.2, 25.4, 8.8), "ouro", rot=(0, 0, -20), pivot=(10, 25, 6))
    g.box("body", (-14, 24, 6), (-10, 25, 10), "ouro"); g.box("body", (-12.6, 25, 7.4), (-11.4, 28, 8.6), "ouro"); g.box("body", (-14, 28, 6), (-10, 31, 10), "ouro")  # taça
    g.cbox("body", (0, 14, -14.6), (8, 7, 1), "carapaca2", decals={"north": "emb"})       # brasão na frente da carapaça
    # cabeça baixa com pedúnculos oculares e mandíbulas
    g.bone("head", (0, 13, -12), "body", "head")
    g.box("head", (-6, 9, -18), (6, 16, -11), "carapaca")
    for s, sd in sides():
        st = g.bone(f"olho_{sd}", (s * 3.5, 15.5, -15), "head", f"sway:{sd}:{90 if s > 0 else 0}")
        g.limb(st, (s * 3.5, 15.5, -15), (s * 5, 23, -17), 1.2, "carapaca2", steps=2, taper_to=0.9)
        g.cbox(st, (s * 5, 23.6, -17), (2.4, 2.4, 2.4), "olho")
        jb = g.bone(f"mandibula_{sd}", (s * 3, 10.5, -18), "head", "jaw")
        g.limb(jb, (s * 3, 10.5, -18), (s * 1.2, 8, -22), 1.6, "osso", taper_to=0.5)
    for k in range(4): g.limb("head", (-3 + k * 2, 10, -18), (-3 + k * 2, 6.5, -19.5), 0.6, "carapaca2", taper_to=0.3)   # palpos
    # duas pinças desiguais (a direita é enorme e cheia de ouro)
    for s, sd in sides():
        big = s < 0; k = 1.5 if big else 1.0
        b = g.bone(f"arm_{sd}", (s * 12, 14, -8), "body", f"arm:{sd}")
        g.limb(b, (s * 12, 14, -8), (s * 18, 17, -16), 3.2 * k, "carapaca", taper_to=2.6 * k)
        g.limb(b, (s * 18, 17, -16), (s * 17, 15, -24), 2.6 * k, "carapaca2", taper_to=2.6 * k)
        g.blob(b, (s * 16.5, 15, -28), 3.4 * k, 3.0 * k, 4.6 * k, "carapaca", layers=4)
        pc = g.bone(f"pinca_{sd}", (s * 16.5, 15 + 2 * k, -31), b, "jaw")
        g.limb(pc, (s * 16.5, 15 + 1.5 * k, -31), (s * 16, 15 + 1.0 * k, -31 - 7 * k), 2.2 * k, "carapaca2", steps=2, taper_to=0.6)
        g.limb(b, (s * 16.5, 15 - 1.5 * k, -31), (s * 16, 15 - 0.8 * k, -31 - 6 * k), 2.0 * k, "carapaca", steps=2, taper_to=0.5)
        if big:
            for kk in range(4): g.cbox(b, (s * 16.5 + (kk - 1.5) * 1.6, 15 + 3.2 * k, -28 + kk * 0.8), (1.4, 0.6, 1.4), "ouro")   # anéis de ouro
    # seis patas
    for s, sd in sides():
        for k in range(3):
            z = -4 + k * 8; ph = k * 120
            b = g.bone(f"perna_{sd}_{k + 1}", (s * 13, 12, z), "root", f"leg:{sd}:{ph}")
            g.limb(b, (s * 13, 12, z), (s * 22, 18, z + (k - 1) * 3), 2.4, "carapaca", taper_to=2.0)
            g.cbox(b, (s * 22, 18, z + (k - 1) * 3), (2.4, 2.4, 2.4), "ouro")
            g.limb(b, (s * 22, 18, z + (k - 1) * 3), (s * 26, 0, z + (k - 1) * 5), 2.0, "carapaca2", steps=2, taper_to=0.6)
    extra = {"attack.pinch": {"animation_length": 1.0, "bones": {
                "arm_r": kf({"0.0": [0, 0, 0], "0.35": [-30, -30, 0], "0.55": [10, 20, 0], "1.0": [0, 0, 0]}),
                "pinca_r": kf({"0.0": [0, 0, 0], "0.35": [-35, 0, 0], "0.55": [5, 0, 0], "1.0": [0, 0, 0]})}},
             "attack.coins": {"animation_length": 1.6, "bones": {
                "body": {"rotation": {"0.0": [0, 0, 0], "1.6": [0, 360, 0]}, "position": {"0.0": [0, 0, 0], "0.4": [0, 4, 0], "1.2": [0, 4, 0], "1.6": [0, 0, 0]}}}},
             "misc.hoard": {"animation_length": 1.0, "bones": {"head": kf({"0.0": [0, 0, 0], "0.4": [30, 0, 0], "1.0": [0, 0, 0]}),
                                                               "mandibula_r": kf({"0.0": [0, 0, 0], "0.3": [0, 30, 0], "0.6": [0, 0, 0]}),
                                                               "mandibula_l": kf({"0.0": [0, 0, 0], "0.3": [0, -30, 0], "0.6": [0, 0, 0]})}}}
    morte(extra)
    return g, dict(scale=1.6, label="Ghor'Maddu, a Arca Viva (Avareza)", extra=extra,
                   notes="Casco de tesouro. attack.pinch (pinça grande), attack.coins (gira e solta um tornado de moedas), "
                         "misc.hoard (engole itens do chão: aumente a escala do boss a cada item, por código).")

# ======================================================================== 4. SERAPHEL DO VITRAL (Esperança)
def seraphel():
    A = Atlas(512, 64, seed=104)
    A.material("tunica", "fabric", "#E8EEF6", trim="#2A7AD8")
    A.material("tunica2", "fabric", "#B8CCE4", trim=None)
    A.material("porcelana", "solid", "#F2F4F8", noise=3)
    A.material("faixa", "fabric", "#1E5AB0", trim="#BDEBFF")
    A.material("vitral", "crystal", "#5AB8FF", alpha=120, glowing=True, translucent=True)
    A.material("vitral2", "crystal", "#A8E0FF", alpha=110, glowing=True, translucent=True)
    A.material("chumbo", "metal", "#4A5A6E", rivets=False)
    A.material("luz", "glow", "#6AC8FF", "#FFFFFF")
    A.material("prata", "metal", "#C8D4E4", rivets=False)
    def rosto(A, x0, y0, S):
        fbg(A, x0, y0, S, "#F2F4F8", 2, grad=0.18)
        for cx in (0.3, 0.7): frect(A, x0, y0, S, cx - 0.14, 0.42, cx + 0.14, 0.5, "#6AC8FF", True); frect(A, x0, y0, S, cx - 0.06, 0.43, cx + 0.06, 0.49, "#FFFFFF", True)
        frect(A, x0, y0, S, 0.48, 0.1, 0.52, 0.36, "#6AC8FF", True)                    # risco de luz na testa
        frect(A, x0, y0, S, 0.42, 0.72, 0.58, 0.74, "#C8D0DC")
    A.decal("rosto", rosto)
    emblema_decal(A, "emb", "azul", "#8CD4FF", fundo="#E8EEF6", borda="#2A7AD8")
    g = Geo("seraphel_do_vitral", A); g.soft = {"tunica": 0.8, "porcelana": 0.6}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 26, 0), "root", "float")
    # túnica longa que termina em feixe de luz (flutua)
    for i, (w, d, y0, y1) in enumerate(((7, 4.6, 10, 20), (8.6, 5.6, 4, 12), (10, 6.6, 0, 6))):
        g.box("body", (-w / 2, y0, -d / 2), (w / 2, y1, d / 2), "tunica" if i % 2 == 0 else "tunica2")
    for k in range(5): g.box("body", (-4.6 + k * 2.2, -3 - (k % 2) * 1.5, -3.4), (-3.4 + k * 2.2, 0.4, 3.4), "luz")     # raios de luz embaixo
    g.taper("body", (0, 19, 0), 7, 4.2, 9.6, 5, 11, 4, "tunica")
    g.box("body", (-4.6, 18.6, -2.8), (4.6, 20.2, 2.8), "faixa")                                                       # faixa na cintura
    g.box("body", (-1.2, 6, -3.0), (1.2, 18.8, -2.6), "faixa")                                                         # pala que cai na frente
    g.cbox("body", (0, 25.5, -2.75), (4.6, 4.6, 0.4), "tunica", decals={"north": "emb"})
    for s, _ in sides(): g.box("body", (s * 2.2 - 1.2, 20, -2.9), (s * 2.2 + 1.2, 30.2, -2.5), "faixa", rot=(0, 0, s * 18), pivot=(s * 2.2, 30, -2.7))   # estola
    # cabeça de porcelana, alongada, com coroa de cristais flutuando
    g.bone("head", (0, 30, 0), "body", "head")
    g.box("head", (-1.2, 29.6, -1.2), (1.2, 31.4, 1.2), "porcelana")
    g.box("head", (-3, 31, -3.2), (3, 38.5, 2.6), "porcelana", decals={"north": "rosto"})
    g.box("head", (-2.2, 38.4, -2.6), (2.2, 40.6, 2.0), "porcelana")
    g.box("head", (-3.2, 33.8, -0.6), (3.2, 39, 3.0), "tunica2")                                 # capuz baixo atrás
    g.bone("coroa", (0, 44, 0), "head", "spin")
    for k in range(7):
        a = 2 * math.pi * k / 7; h = 3.5 + (k % 2) * 2
        g.box("coroa", (5 * math.sin(a) - 0.5, 42, 5 * math.cos(a) - 0.5), (5 * math.sin(a) + 0.5, 42 + h, 5 * math.cos(a) + 0.5), "vitral2", rot=(0, math.degrees(a) + 45, 12), pivot=(5 * math.sin(a), 42, 5 * math.cos(a)))
    # braços com mangas largas, mãos abertas
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 4.8, 28.5, 0), "body", f"arm:{sd}")
        g.cbox(b, (s * 5.2, 28.3, 0), (2.8, 2.8, 2.8), "tunica")
        g.limb(b, (s * 5.4, 27.5, 0), (s * 6.4, 20, -1.5), 2.2, "tunica", taper_to=2.0)
        g.box(b, (s * 6.4 - 2.2, 12.6, -4.0), (s * 6.4 + 2.2, 20.4, 1.0), "tunica2", rot=(14, 0, s * 4), pivot=(s * 6.4, 20, -1.5))
        g.limb(b, (s * 6.4, 18, -2), (s * 6.6, 13, -4), 1.2, "porcelana", taper_to=1.0)
        mao(g, b, (s * 6.6, 13, -4), s, "porcelana", "porcelana", dedos=4, L=2.2, w=0.45)
        g.cbox(b, (s * 6.6, 9.5, -5.6), (1.6, 1.6, 1.6), "luz", rot=(45, 45, 0))                 # luz na palma
    # asas de vitral: 3 painéis grandes de cada lado com caixilhos de chumbo
    for s, sd in sides():
        nm = f"asa_{sd}"; g.bone(nm, (s * 2.5, 30, 3), "body", f"wing:{sd}")
        for k, (ang, ln, hh, yy) in enumerate(((10, 20, 9, 33), (-12, 24, 8, 27), (-34, 17, 6, 21))):
            x0, x1 = s * 3, s * (3 + ln)
            rot = (0, -s * 30, s * ang)
            g.box(nm, (min(x0, x1), yy - hh / 2, 3.2), (max(x0, x1), yy + hh / 2, 3.6), "vitral" if k != 1 else "vitral2", rot=rot, pivot=(s * 3, yy, 3.4))
            for v in range(4):
                xv = 3 + (v + 1) * ln / 5
                g.box(nm, (min(s * xv, s * (xv + 0.4)), yy - hh / 2, 3.1), (max(s * xv, s * (xv + 0.4)), yy + hh / 2, 3.7), "chumbo", rot=rot, pivot=(s * 3, yy, 3.4))
            g.box(nm, (min(x0, x1), yy + hh / 2 - 0.4, 3.05), (max(x0, x1), yy + hh / 2, 3.75), "prata", rot=rot, pivot=(s * 3, yy, 3.4))
            g.box(nm, (min(x0, x1), yy - hh / 2, 3.05), (max(x0, x1), yy - hh / 2 + 0.4, 3.75), "chumbo", rot=rot, pivot=(s * 3, yy, 3.4))
    extra = {"attack.light": {"animation_length": 1.4, "bones": {
                "arm_r": kf({"0.0": [0, 0, 0], "0.5": [-100, 0, 30], "1.0": [-100, 0, 30], "1.4": [0, 0, 0]}),
                "arm_l": kf({"0.0": [0, 0, 0], "0.5": [-100, 0, -30], "1.0": [-100, 0, -30], "1.4": [0, 0, 0]}),
                "asa_r": kf({"0.0": [0, 0, 0], "0.5": [0, 40, 0], "1.4": [0, 0, 0]}), "asa_l": kf({"0.0": [0, 0, 0], "0.5": [0, -40, 0], "1.4": [0, 0, 0]})}},
             "misc.heal": {"loop": True, "animation_length": 2.0, "bones": {
                "coroa": {"rotation": [0, "query.anim_time * 180", 0], "scale": ["1 + math.sin(query.anim_time * 360) * 0.15"] * 3},
                "arm_r": kf({"0.0": [-60, 0, 20], "2.0": [-60, 0, 20]}), "arm_l": kf({"0.0": [-60, 0, -20], "2.0": [-60, 0, -20]})}},
             "misc.vulnerable": {"loop": True, "animation_length": 1.0, "bones": {
                "asa_r": kf({"0.0": [0, 70, 0], "1.0": [0, 70, 0]}), "asa_l": kf({"0.0": [0, -70, 0], "1.0": [0, -70, 0]})}}}
    morte(extra)
    return g, dict(scale=1.4, label="Seraphel do Vitral (Esperança)", translucent=True, extra=extra,
                   notes="Flutua (sem pernas). Asas de vitral translúcidas e acesas. attack.light (raio das mãos), misc.heal (cura aliados), "
                         "misc.vulnerable (asas abertas = ponto fraco, a janela de dano).")

# ======================================================================== 5. AUUN E IEL, OS GÊMEOS VELADOS (Compaixão)
def gemeos():
    A = Atlas(512, 64, seed=105)
    A.material("pele", "skin", "#5A4A8A", spots="#3A2E6A")
    A.material("faixas", "fabric", "#D9CCB8", trim="#8A7A68")
    A.material("veste", "fabric", "#2A1E5A", trim="#9A88E8")
    A.material("veu", "crystal", "#8A6AFF", alpha=85, translucent=True)
    A.material("luz", "glow", "#8A6AFF", "#FFFFFF")
    A.material("madeira", "solid", "#3A2A1C", noise=6)
    A.material("osso", "bone", "#E0D6C0")
    def rosto(A, x0, y0, S):    # véu de faixas com um olho aceso
        fbg(A, x0, y0, S, "#D9CCB8", 4)
        for i in range(5): frect(A, x0, y0, S, 0, 0.12 + i * 0.2, 1, 0.15 + i * 0.2, "#8A7A68")
        fdisc(A, x0, y0, S, 0.5, 0.42, 0.14, "#2A1E5A"); fdisc(A, x0, y0, S, 0.5, 0.42, 0.09, "#8A6AFF", True); fdisc(A, x0, y0, S, 0.5, 0.42, 0.04, "#FFFFFF", True)
    A.decal("rosto", rosto)
    emblema_decal(A, "emb", "indigo", "#B8A0FF", fundo="#2A1E5A", borda="#D9CCB8")
    g = Geo("gemeos_velados", A); g.soft = {"veste": 0.5}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 22, 0), "root", "body")
    for s, sd in sides():
        cx = s * 7; nb = g.bone(f"gemeo_{sd}", (cx, 22, 0), "body", "float")
        # corpo esguio enfaixado
        g.taper(nb, (cx, 13, 0), 4.6, 3, 6.4, 3.6, 13, 4, "veste")
        for i in range(5): g.box(nb, (cx - 3.4 + i * 0.1, 15 + i * 2.2, -2.0), (cx + 3.4 - i * 0.1, 15.7 + i * 2.2, 2.0), "faixas", rot=(0, 0, (-1) ** i * 8))
        g.cbox(nb, (cx, 23, -1.95), (3.4, 3.4, 0.3), "veste", decals={"north": "emb"})
        g.box(nb, (cx - 3.0, 4, -2.4), (cx + 3.0, 14, 2.4), "veste")                           # saia de tiras
        for k in range(4): g.box(nb, (cx - 2.8 + k * 1.5, 1.5 + (k % 2), -2.6), (cx - 1.8 + k * 1.5, 6, -2.3), "faixas")
        # cabeça velada
        hb = g.bone(f"cabeca_{sd}", (cx, 26.5, 0), nb, "head")
        g.box(hb, (cx - 2.4, 26.4, -2.6), (cx + 2.4, 32.4, 2.2), "faixas", decals={"north": "rosto"})
        g.box(hb, (cx - 2.8, 30.6, -2.9), (cx + 2.8, 33.2, 2.6), "veste")                      # capuz curto
        g.limb(hb, (cx, 33, 2), (cx, 31, 6), 1.6, "veste", taper_to=0.6)
        for k in range(3): g.cbox(hb, (cx + (k - 1) * 1.6, 33.6, 0), (0.6, 0.6, 0.6), "osso")
        # braços: o de fora livre, o de dentro segura o cajado
        for t, sgn in (("fora", s), ("dentro", -s)):
            b = g.bone(f"arm_{sd}_{t}", (cx + sgn * 3.4, 25, 0), nb, f"arm:{'l' if sgn > 0 else 'r'}")
            g.limb(b, (cx + sgn * 3.6, 25, 0), (cx + sgn * 4.4, 18, -1.6), 1.4, "pele", taper_to=1.2)
            for i in range(3): g.cbox(b, (cx + sgn * 3.9, 23.6 - i * 2.2, -0.6), (1.7, 0.7, 1.7), "faixas")
            g.limb(b, (cx + sgn * 4.4, 18, -1.6), (cx + sgn * 4.0, 12, -3.0), 1.2, "pele", taper_to=1.0)
            mao(g, b, (cx + sgn * 4.0, 12, -3.0), sgn, "pele", "pele", dedos=3, L=1.6, w=0.45)
        # pernas finas
        for t, sgn in (("a", -1), ("b", 1)):
            b = g.bone(f"leg_{sd}_{t}", (cx + sgn * 1.4, 13, 0), nb, f"leg:{'l' if sgn > 0 else 'r'}:{0 if s < 0 else 180}")
            g.limb(b, (cx + sgn * 1.4, 13, 0), (cx + sgn * 1.5, 1.2, 0), 1.3, "pele", taper_to=1.0)
            g.box(b, (cx + sgn * 1.5 - 0.8, 0, -1.6), (cx + sgn * 1.5 + 0.8, 1.2, 0.8), "faixas")
    # o véu que une os dois pelos ombros (sofrem juntos) e o cajado-anzol que os dois seguram
    g.bone("veu", (0, 26, 1), "body", "sway")
    g.box("veu", (-6, 14, 1.0), (6, 26, 1.3), "veu", rot=(6, 0, 0), pivot=(0, 26, 1))
    for k in range(3): g.box("veu", (-6, 25.5 - k * 4, 0.9), (6, 26 - k * 4, 1.4), "luz", rot=(6, 0, 0), pivot=(0, 26, 1))
    g.bone("cajado", (0, 14, -4), "body", "float")
    g.box("cajado", (-0.5, 0, -4.5), (0.5, 34, -3.5), "madeira")
    for k in range(5): g.cbox("cajado", (0, 8 + k * 5, -4), (1.3, 0.6, 1.3), "faixas")
    g.limb("cajado", (0, 34, -4), (3.5, 38, -4), 1.0, "madeira", taper_to=0.9)                # gancho
    g.limb("cajado", (3.5, 38, -4), (5.5, 34.5, -4), 0.9, "madeira", taper_to=0.8)
    g.limb("cajado", (5.5, 34.5, -4), (3.8, 31.5, -4), 0.8, "madeira", taper_to=0.4)
    g.cbox("cajado", (2.6, 35.2, -4), (2.0, 2.0, 2.0), "luz", rot=(0, 0, 45))
    extra = {"attack.hook": {"animation_length": 1.2, "bones": {
                "cajado": {"rotation": {"0.0": [0, 0, 0], "0.4": [-50, 0, 0], "0.7": [40, 0, 0], "1.2": [0, 0, 0]}},
                "arm_r_dentro": kf({"0.0": [0, 0, 0], "0.4": [-80, 0, 0], "0.7": [-20, 0, 0], "1.2": [0, 0, 0]}),
                "arm_l_dentro": kf({"0.0": [0, 0, 0], "0.4": [-80, 0, 0], "0.7": [-20, 0, 0], "1.2": [0, 0, 0]})}},
             "misc.share_pain": {"animation_length": 0.8, "bones": {
                "gemeo_r": kf({"0.0": [0, 0, 0], "0.2": [-12, 0, -8], "0.8": [0, 0, 0]}), "gemeo_l": kf({"0.0": [0, 0, 0], "0.2": [-12, 0, 8], "0.8": [0, 0, 0]}),
                "veu": {"scale": {"0.0": [1, 1, 1], "0.2": [1.1, 1.2, 1], "0.8": [1, 1, 1]}}}},
             "misc.split": {"animation_length": 1.0, "bones": {
                "gemeo_r": {"position": {"0.0": [0, 0, 0], "1.0": [-10, 0, 0]}}, "gemeo_l": {"position": {"0.0": [0, 0, 0], "1.0": [10, 0, 0]}},
                "veu": {"scale": {"0.0": [1, 1, 1], "1.0": [2.6, 1, 1]}}}}}
    morte(extra)
    return g, dict(scale=1.4, label="Auun e Iel, os Gêmeos Velados (Compaixão)", translucent=True, extra=extra,
                   notes="Dois corpos num só boss (ossos gemeo_r / gemeo_l). Dano em um = misc.share_pain nos dois. "
                         "misc.split separa os gêmeos (fase 2; o véu estica entre eles). attack.hook = cajado-anzol puxa o jogador.")

# ======================================================================== 6. RAINHA CALITHE DO ESPELHO (Amor)
def calithe():
    A = Atlas(512, 64, seed=106)
    A.material("cristal", "crystal", "#B048D0", alpha=235, glowing=False)
    A.material("cristal2", "crystal", "#7A2A9A", alpha=240, glowing=False)
    A.material("placas", "plates", "#3A1446", "#C870E8", step=9, rivet="#FFD0FF")
    A.material("prata", "metal", "#D8D0E8", rivets=False)
    A.material("espelho", "crystal", "#F0E8FF", alpha=200, glowing=True, translucent=True)
    A.material("capa", "fabric", "#5A1A6A", trim="#E8B8FF")
    A.material("luz", "glow", "#E070FF", "#FFFFFF")
    A.material("malha", "metal", "#2A1A30")
    def visor(A, x0, y0, S):
        fbg(A, x0, y0, S, "#2A0A34", 3)
        frect(A, x0, y0, S, 0.12, 0.38, 0.88, 0.5, "#E070FF", True); frect(A, x0, y0, S, 0.2, 0.41, 0.8, 0.47, "#FFE0FF", True)
        for i in range(5): frect(A, x0, y0, S, 0.24 + i * 0.12, 0.6, 0.28 + i * 0.12, 0.85, "#7A2A9A")
        frect(A, x0, y0, S, 0.48, 0.05, 0.52, 0.36, "#C870E8")
    A.decal("visor", visor)
    emblema_decal(A, "emb", "violeta", "#FF9CF4", fundo="#3A1446", borda="#D8D0E8")
    g = Geo("calithe_rainha_do_espelho", A); g.soft = {"placas": 0.5}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 22, 0), "root", "body")
    # torso de armadura facetada, cintura fina, saia de placas
    g.taper("body", (0, 21, 0), 6, 3.8, 9.4, 5.2, 11, 4, "placas")
    g.box("body", (-3.6, 19.6, -2.2), (3.6, 21.4, 2.2), "prata")
    for s, _ in sides():
        g.box("body", (s * 2.4 - 2.3, 26, -3.3), (s * 2.4 + 2.3, 30.6, -2.3), "cristal", rot=(0, -s * 14, 0))                       # peito facetado
    g.cbox("body", (0, 25, -2.7), (2.8, 2.8, 0.6), "luz", rot=(0, 0, 45))                                                          # gema do coração
    g.cbox("body", (0, 28.6, 2.7), (4.4, 4.4, 0.4), "placas", decals={"south": "emb"})
    for i, (w, d, y0, y1) in enumerate(((8, 5.2, 13, 20), (10, 6.4, 7, 14), (12, 7.6, 1, 8))):                                      # saia longa de lâminas
        g.box("body", (-w / 2, y0, -d / 2), (w / 2, y1, d / 2), "malha")
        for k in range(8):
            a = 2 * math.pi * k / 8; r = w / 2 + 0.2
            g.box("body", (r * math.sin(a) - 1.1, y0 - 0.6, r * math.cos(a) * d / w - 0.2), (r * math.sin(a) + 1.1, y1, r * math.cos(a) * d / w + 0.2), "cristal2" if (k + i) % 2 else "cristal", rot=(0, math.degrees(a), 0), pivot=(r * math.sin(a), y0, r * math.cos(a) * d / w))
    for s, _ in sides():                                                                    # ombreiras de cristal
        for k in range(3): g.box("body", (s * 4.8 - 1.4, 29.6 - k * 1.3, -2.6 + k * 0.2), (s * 4.8 + (2.6 + k * 0.6) * s, 31.2 - k * 1.3, 2.6 - k * 0.2), "cristal", rot=(0, 0, -s * (14 + k * 8)), pivot=(s * 4.8, 30, 0))
    # capa
    g.bone("capa", (0, 31, 2.8), "body", "sway")
    g.box("capa", (-5.2, 2, 3.4), (5.2, 31, 4.0), "capa", rot=(-8, 0, 0), pivot=(0, 31, 3.6))
    g.box("capa", (-5.4, 30, 2.4), (5.4, 31.6, 3.4), "prata")
    # cabeça: elmo fechado com coroa de agulhas de cristal
    g.bone("head", (0, 31.5, 0), "body", "head")
    g.box("head", (-1.3, 31, -1.3), (1.3, 32.8, 1.3), "malha")
    g.box("head", (-3, 32.5, -3.2), (3, 39, 2.8), "placas")
    g.box("head", (-2.8, 33, -3.5), (2.8, 38.4, -3.1), "placas", decals={"north": "visor"})
    for k in range(7):
        x = -3 + k; h = 4.5 - abs(k - 3) * 0.9
        g.box("head", (x - 0.4, 38.6, -1.6 + abs(k - 3) * 0.6), (x + 0.4, 38.6 + h, -0.8 + abs(k - 3) * 0.6), "cristal", rot=(-8, 0, (k - 3) * 6), pivot=(x, 38.6, 0))
        g.box("head", (x - 0.25, 38.6 + h - 0.6, -1.7 + abs(k - 3) * 0.6), (x + 0.25, 38.6 + h + 0.3, -0.7 + abs(k - 3) * 0.6), "luz", rot=(-8, 0, (k - 3) * 6), pivot=(x, 38.6, 0))
    for s, _ in sides(): g.box("head", (s * 3.2 - 0.4, 34, -2.6), (s * 3.2 + 0.4, 38, 2.0), "prata")
    # braço direito com a lança-agulha, esquerdo com o ESCUDO-ESPELHO
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 5, 29.5, 0), "body", f"arm:{sd}")
        g.limb(b, (s * 5.4, 29, 0), (s * 6.2, 22, -1), 2.0, "placas", taper_to=1.8)
        g.cbox(b, (s * 6.2, 22, -1), (2.2, 2.2, 2.2), "prata")
        g.limb(b, (s * 6.2, 22, -1), (s * 6.4, 15.5, -3.0), 1.8, "cristal2", taper_to=1.8)
        mao(g, b, (s * 6.4, 15.5, -3.0), s, "placas", "prata", dedos=4, L=1.8, w=0.5)
        if s < 0:   # lança de cristal
            g.box(b, (s * 6.4 - 0.4, 0, -4.2), (s * 6.4 + 0.4, 30, -3.4), "prata", rot=(-12, 0, 0), pivot=(s * 6.4, 13, -3.8))
            g.box(b, (s * 6.4 - 1.0, 30, -4.8), (s * 6.4 + 1.0, 38, -2.8), "cristal", rot=(-12, 0, 0), pivot=(s * 6.4, 13, -3.8))
            g.box(b, (s * 6.4 - 0.4, 37.5, -4.2), (s * 6.4 + 0.4, 41, -3.4), "luz", rot=(-12, 0, 0), pivot=(s * 6.4, 13, -3.8))
        else:       # escudo-espelho oval
            sh = g.bone("escudo", (s * 8.6, 19, -1), b)
            for (w, h) in ((1.0, 15), (6, 13), (9, 10), (11, 6)):
                g.box(sh, (s * 8.4, 19 - h / 2, -1 - w / 2 - 0.0), (s * 9.2, 19 + h / 2, -1 + w / 2), "prata")
            for (w, h) in ((5, 12), (8, 9), (10, 5)):
                g.box(sh, (s * 9.2, 19 - h / 2, -1 - w / 2), (s * 9.5, 19 + h / 2, -1 + w / 2), "espelho")
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 2, 13, 0), "root", f"leg:{sd}")
        g.limb(b, (s * 2, 13, 0), (s * 2.2, 2, 0), 2.2, "placas", taper_to=1.8)
        g.box(b, (s * 2.2 - 1.3, 0, -3.2), (s * 2.2 + 1.3, 2.4, 1.2), "cristal2")
    extra = {"attack.thrust": {"animation_length": 0.9, "bones": {
                "arm_r": kf({"0.0": [0, 0, 0], "0.3": [-30, 0, 0], "0.5": [-95, 0, 0], "0.9": [0, 0, 0]}),
                "body": kf({"0.0": [0, 0, 0], "0.5": [8, -15, 0], "0.9": [0, 0, 0]})}},
             "misc.reflect": {"loop": True, "animation_length": 1.0, "bones": {
                "arm_l": kf({"0.0": [-80, -30, 0], "1.0": [-80, -30, 0]}), "escudo": {"scale": ["1 + math.sin(query.anim_time * 720) * 0.05"] * 3}}},
             "attack.shards": {"animation_length": 1.2, "bones": {
                "head": kf({"0.0": [0, 0, 0], "0.4": [-25, 0, 0], "0.7": [10, 0, 0], "1.2": [0, 0, 0]}),
                "arm_r": kf({"0.0": [0, 0, 0], "0.4": [-150, 0, 0], "0.7": [-40, 0, 0], "1.2": [0, 0, 0]})}}}
    morte(extra)
    return g, dict(scale=1.4, label="Rainha Calithe do Espelho (Amor)", translucent=True, extra=extra,
                   notes="Armadura de cristal facetado, elmo fechado com coroa de agulhas, lança e escudo-espelho (translúcido). "
                         "misc.reflect = postura de escudo (devolve projéteis enquanto toca), attack.thrust, attack.shards.")

# ======================================================================== 7. MORTHAAL, O COLETOR DE LANTERNAS (Morte)
def morthaal():
    A = Atlas(512, 64, seed=107)
    A.material("osso", "bone", "#D6CEB8")
    A.material("osso2", "bone", "#8A8270")
    A.material("mortalha", "fabric", "#0E0E12", trim="#2A2A34")
    A.material("ferro", "metal", "#1A1A20")
    A.material("alma", "glow", "#BFD8FF", "#FFFFFF")
    A.material("alma2", "crystal", "#9AB8FF", alpha=110, glowing=True, translucent=True)
    A.material("vidro_morto", "crystal", "#3A3A44", alpha=150, translucent=True)
    A.material("lamina", "metal", "#4A4C58", rivets=False)
    def cranio(A, x0, y0, S):
        fbg(A, x0, y0, S, "#D6CEB8", 3, grad=0.2)
        for cx in (0.3, 0.7): fdisc(A, x0, y0, S, cx, 0.42, 0.15, "#0A0A0E"); fdisc(A, x0, y0, S, cx, 0.44, 0.06, "#BFD8FF", True)
        frect(A, x0, y0, S, 0.45, 0.56, 0.55, 0.66, "#0A0A0E")
        for i in range(7): frect(A, x0, y0, S, 0.2 + i * 0.09, 0.76, 0.26 + i * 0.09, 0.9, "#F0EAD8"); frect(A, x0, y0, S, 0.26 + i * 0.09, 0.76, 0.29 + i * 0.09, 0.9, "#2A2620")
    A.decal("cranio", cranio)
    emblema_decal(A, "emb", "preto", "#DDE6FF", fundo="#0E0E12", borda="#8A8270")
    g = Geo("morthaal_coletor_de_lanternas", A); g.soft = {"mortalha": 0.6}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 30, 0), "root", "body")
    # coluna e caixa torácica ABERTA com uma alma presa dentro
    for i in range(7): g.cbox("body", (0, 22 + i * 2.6, 2.4), (2.2, 2.0, 2.2), "osso2")
    g.box("body", (-6, 40, -1), (6, 42.5, 4), "osso")                                       # clavículas
    for i in range(5):
        y = 38 - i * 2.4; w = 7 - abs(i - 1.5) * 0.6
        for s, _ in sides():
            g.limb("body", (s * 0.6, y, 2.6), (s * w, y - 0.8, 0.8), 1.0, "osso", taper_to=0.9)
            g.limb("body", (s * w, y - 0.8, 0.8), (s * (w - 2.2), y - 1.6, -3.4), 0.9, "osso", taper_to=0.6)
    g.bone("alma", (0, 33, -0.5), "body", "pulse")
    g.blob("alma", (0, 33, -0.5), 3.0, 3.6, 3.0, "alma", layers=4)
    g.blob("alma", (0, 33, -0.5), 4.4, 5.0, 4.4, "alma2", layers=3)
    g.cbox("body", (0, 40.5, 4.2), (4, 4, 0.4), "osso", decals={"south": "emb"})
    g.box("body", (-6.5, 19, -3.5), (6.5, 23, 4.5), "osso2")                               # pelve
    # mortalha rasgada presa nos ombros e na cintura
    g.bone("mortalha", (0, 41, 3), "body", "sway")
    g.box("mortalha", (-7.5, 4, 3.6), (7.5, 41, 4.2), "mortalha", rot=(8, 0, 0), pivot=(0, 41, 3.8))
    for k in range(8): g.box("mortalha", (-7.5 + k * 1.9, 0, 3.5), (-6.2 + k * 1.9, 5 + (k % 3), 4.1), "mortalha", rot=(8, 0, 0), pivot=(0, 41, 3.8))
    for k in range(6): g.box("body", (-6.4 + k * 2.2, 8 + (k % 2) * 2, -3.8), (-4.8 + k * 2.2, 20, 4.8), "mortalha")
    # cabeça: crânio alongado de ossos fundidos com galhada de ossos
    g.bone("head", (0, 43, 1), "body", "head")
    g.box("head", (-1, 42, 0), (1, 45, 2), "osso2")
    g.box("head", (-3.4, 44.5, -3.6), (3.4, 51.5, 3.0), "osso", decals={"north": "cranio"})
    g.box("head", (-2.6, 51.4, -2.6), (2.6, 53.2, 2.4), "osso")
    for s, _ in sides():
        g.limb("head", (s * 2.4, 52.5, 0), (s * 6, 58, 1), 1.1, "osso2", steps=2, taper_to=0.7)
        g.limb("head", (s * 6, 58, 1), (s * 10, 61, 2), 0.8, "osso2", taper_to=0.3)
        g.limb("head", (s * 5, 57, 1), (s * 5.5, 62, -1), 0.7, "osso2", taper_to=0.3)
        g.limb("head", (s * 7.5, 59.5, 1.5), (s * 8.5, 64, 0), 0.6, "osso2", taper_to=0.2)
    jb = g.bone("mandibula", (0, 45, -1), "head", "jaw")
    g.box(jb, (-2.8, 42.8, -3.6), (2.8, 45, 0.5), "osso")
    # braços longos de ossos; a mão direita segura a GADANHA, a esquerda uma lanterna apagada
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 7, 41, 1), "body", f"arm:{sd}")
        g.cbox(b, (s * 7.5, 41, 1), (3.0, 3.0, 3.0), "osso2")
        g.limb(b, (s * 8, 40, 1), (s * 10, 29, -1), 1.3, "osso", taper_to=1.1)
        g.cbox(b, (s * 10, 29, -1), (2.0, 2.0, 2.0), "osso2")
        for dx in (-0.6, 0.6): g.limb(b, (s * 10 + dx, 29, -1), (s * 10.5 + dx, 18, -4), 0.8, "osso", taper_to=0.7)
        mao(g, b, (s * 10.5, 18, -4), s, "osso2", "osso", dedos=4, L=3.6, w=0.5)
        if s < 0:
            g.box(b, (s * 10.5 - 0.5, 2, -6.2), (s * 10.5 + 0.5, 46, -5.2), "ferro")
            g.box(b, (s * 10.5 - 0.3, 44, -6.0), (s * 10.5 + 0.3, 46.4, -5.4), "osso")
            g.box(b, (s * 10.5 - 0.25, 40, -22), (s * 10.5 + 0.25, 44.5, -5.2), "lamina", rot=(-18, 0, 0), pivot=(s * 10.5, 44, -5.7))
            g.box(b, (s * 10.5 - 0.3, 43.4, -22.4), (s * 10.5 + 0.3, 44.6, -5.2), "alma", rot=(-18, 0, 0), pivot=(s * 10.5, 44, -5.7))
        else:
            for k in range(3): g.cbox(b, (s * 10.5, 13.5 - k * 1.2, -5), (0.4, 1.0, 0.4), "ferro")
            g.box(b, (s * 10.5 - 1.8, 6, -6.8), (s * 10.5 + 1.8, 10, -3.2), "vidro_morto")
            g.box(b, (s * 10.5 - 2.2, 10, -7.2), (s * 10.5 + 2.2, 11, -2.8), "ferro"); g.box(b, (s * 10.5 - 2.2, 5, -7.2), (s * 10.5 + 2.2, 6, -2.8), "ferro")
    # lanternas apagadas penduradas no cinto (almas coletadas)
    for k, x in enumerate((-6, -2, 2, 6)):
        nm = f"lanterna_{k + 1}"; g.bone(nm, (x, 20, -4), "body", f"sway:r:{k * 70}")
        g.box(nm, (x - 0.15, 16, -4.2), (x + 0.15, 20, -3.9), "ferro")
        g.box(nm, (x - 1.2, 12.5, -5.3), (x + 1.2, 15.5, -2.9), "vidro_morto")
        g.box(nm, (x - 1.4, 15.5, -5.5), (x + 1.4, 16.2, -2.7), "ferro"); g.box(nm, (x - 1.4, 11.8, -5.5), (x + 1.4, 12.5, -2.7), "ferro")
        g.cbox(nm, (x, 14, -4.1), (0.6, 0.6, 0.6), "alma")
    # pernas de ossos
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 3.5, 20, 0.5), "root", f"leg:{sd}")
        for dx in (-0.6, 0.6): g.limb(b, (s * 3.5 + dx, 20, 0.5), (s * 3.8 + dx, 10, -0.5), 1.0, "osso", taper_to=0.8)
        g.cbox(b, (s * 3.8, 10, -0.5), (2.0, 2.0, 2.0), "osso2")
        g.limb(b, (s * 3.8, 10, -0.5), (s * 4, 1.6, 0.5), 1.1, "osso", taper_to=0.9)
        g.box(b, (s * 4 - 1.2, 0, -3.0), (s * 4 + 1.2, 1.6, 1.4), "osso2")
    # almas soltas girando em volta
    g.bone("almas", (0, 30, 0), "root", "spin")
    for i in range(5):
        a = 2 * math.pi * i / 5
        g.cbox("almas", (14 * math.sin(a), 28 + 8 * math.sin(a * 2), 14 * math.cos(a)), (1.4, 2.2, 1.4), "alma2")
        g.cbox("almas", (14 * math.sin(a), 28 + 8 * math.sin(a * 2), 14 * math.cos(a)), (0.6, 1.0, 0.6), "alma")
    extra = {"attack.reap": {"animation_length": 1.3, "bones": {
                "arm_r": kf({"0.0": [0, 0, 0], "0.5": [-30, 70, -40], "0.8": [-40, -60, 30], "1.3": [0, 0, 0]}),
                "body": kf({"0.0": [0, 0, 0], "0.5": [0, 25, 0], "0.8": [0, -30, 0], "1.3": [0, 0, 0]})}},
             "misc.release_souls": {"animation_length": 1.5, "bones": {
                "almas": {"scale": {"0.0": [1, 1, 1], "1.0": [2.2, 1.4, 2.2], "1.5": [1, 1, 1]}},
                "alma": {"scale": {"0.0": [1, 1, 1], "0.4": [1.6, 1.6, 1.6], "1.5": [1, 1, 1]}},
                "head": kf({"0.0": [0, 0, 0], "0.4": [-30, 0, 0], "1.5": [0, 0, 0]}), "mandibula": kf({"0.0": [0, 0, 0], "0.4": [35, 0, 0], "1.5": [0, 0, 0]})}}}
    morte(extra)
    return g, dict(scale=1.5, label="Morthaal, o Coletor de Lanternas (Morte)", translucent=True, extra=extra,
                   notes="Golem de ossos com a caixa torácica aberta e uma alma presa (pulsa). Gadanha na mão direita, lanterna apagada na esquerda, "
                         "4 lanternas de almas no cinto (balançam). attack.reap, misc.release_souls (solta as almas que giram em volta).")

# ======================================================================== 8. VERDAN, O ARQUITETO (Vontade)
def verdan():
    A = Atlas(512, 64, seed=108)
    A.material("armadura", "plates", "#1E3A2A", "#4ACB7A", step=8, rivet="#B4FFC9")
    A.material("armadura2", "plates", "#2A4A36", "#1E2E24", step=12)
    A.material("aco", "metal", "#5A6A62")
    A.material("malha", "metal", "#1A221E")
    A.material("luz", "glow", "#3AFF7A", "#FFFFFF")
    A.material("construto", "crystal", "#5AFF9A", alpha=120, glowing=True, translucent=True)
    A.material("cabo", "solid", "#0E1410", noise=3)
    def visor(A, x0, y0, S):
        fbg(A, x0, y0, S, "#1E3A2A", 3)
        frect(A, x0, y0, S, 0.06, 0.4, 0.94, 0.52, "#0A120E"); frect(A, x0, y0, S, 0.1, 0.43, 0.9, 0.49, "#3AFF7A", True)
        for i in range(4): frect(A, x0, y0, S, 0.3 + i * 0.12, 0.66, 0.34 + i * 0.12, 0.86, "#0A120E")
    A.decal("visor", visor)
    emblema_decal(A, "emb", "verde", "#7CFF9C", fundo="#14201A", borda="#5A6A62")
    g = Geo("verdan_o_arquiteto", A); g.soft = {"armadura": 0.6}
    g.bone("root", (0, 0, 0))
    g.bone("body", (0, 26, 0), "root", "body")
    # tronco largo e blindado, cintura articulada
    g.taper("body", (0, 25, 0), 11, 7, 17, 9, 15, 4, "armadura")
    g.box("body", (-5, 20, -3.4), (5, 25.4, 3.4), "malha")
    for i in range(3): g.box("body", (-5.4 + i * 0.3, 20.4 + i * 1.6, -3.8), (5.4 - i * 0.3, 21.6 + i * 1.6, 3.8), "armadura2")
    g.cbox("body", (0, 34, -4.8), (6, 6, 0.6), "armadura", decals={"north": "emb"})
    for s, _ in sides():
        g.box("body", (s * 4.5 - 3, 30, -5.0), (s * 4.5 + 3, 38, -4.0), "armadura2", rot=(0, -s * 10, 0))
        g.box("body", (s * 7.6 - 0.4, 26, -4.4), (s * 7.6 + 0.4, 36, -3.8), "luz")
    g.box("body", (-6, 27, 4.4), (6, 38, 7.4), "aco")                                          # mochila-gerador
    g.box("body", (-8, 18.6, -4.6), (8, 20.4, 4.6), "aco")                                      # cinturão
    for k in range(4): g.box("body", (-7 + k * 4, 13, -4.9), (-4 + k * 4, 19, -4.3), "armadura2")   # tassets
    g.box("body", (-1.2, 18.8, -5.0), (1.2, 20.2, -4.6), "luz")
    for k in range(3): g.box("body", (-4.5 + k * 3, 28, 7.4), (-3.0 + k * 3, 37, 7.9), "luz")
    for s, _ in sides(): g.limb("body", (s * 4, 38, 6), (s * 6, 46, 8), 1.4, "aco", taper_to=0.8); g.cbox("body", (s * 6, 46.6, 8), (1.6, 1.6, 1.6), "luz")
    # cabeça: elmo com fenda de visor única
    g.bone("head", (0, 40, -0.5), "body", "head")
    g.box("head", (-2, 39.5, -2.5), (2, 41.5, 1.5), "malha")
    g.box("head", (-3.6, 41, -4.4), (3.6, 48.6, 3.0), "armadura2")
    g.box("head", (-3.4, 41.6, -4.8), (3.4, 47.6, -4.2), "armadura", decals={"north": "visor"})
    g.box("head", (-0.6, 46, -5.0), (0.6, 50, 2.4), "armadura")                              # crista
    for s, _ in sides(): g.box("head", (s * 3.6 - 0.6, 43, -2), (s * 3.6 + 0.6, 47, 2), "aco"); g.cbox("head", (s * 4.3, 45, 0), (0.6, 0.6, 2.6), "luz")
    # ombros com pilões emissores
    for s, sd in sides():
        b = g.bone(f"arm_{sd}", (s * 9, 37, 0), "body", f"arm:{sd}")
        g.box(b, (s * 8 - 3.6, 35, -4), (s * 8 + 3.6, 40.4, 4), "armadura")
        g.box(b, (s * 9 - 0.8, 40.4, -1.6), (s * 9 + 0.8, 44, 1.6), "aco"); g.box(b, (s * 9 - 0.5, 44, -1.2), (s * 9 + 0.5, 45.2, 1.2), "luz")
        g.limb(b, (s * 9.5, 35, 0), (s * 10.5, 26, -1), 3.2, "malha", taper_to=3.0)
        g.cbox(b, (s * 10.5, 26, -1), (3.6, 3.0, 3.6), "armadura2")
        g.limb(b, (s * 10.5, 26, -1), (s * 11, 17, -3), 3.4, "armadura", taper_to=3.6)
        for k in range(3): g.box(b, (s * 10.7 - 2.1, 19 + k * 2.2, -4.9), (s * 10.7 + 2.1, 19.6 + k * 2.2, -1.1), "aco")   # aletas da manopla
        for k in range(3): g.box(b, (s * 8 - 3.8, 34 - k * 1.2, -4.2), (s * 8 + 3.8, 34.8 - k * 1.2, 4.2), "armadura2")    # lamelas do ombro
        if s < 0:   # mão direita vira um MARTELO DE CONSTRUTO
            g.limb(b, (s * 11, 17, -3), (s * 11.2, 10, -5), 1.6, "construto", taper_to=1.6)
            g.cbox(b, (s * 11.2, 7, -5.6), (6.5, 6, 8), "construto")
            g.cbox(b, (s * 11.2, 7, -5.6), (4.5, 4, 8.6), "luz")
        else:       # esquerda: projetor de antebraço + mão
            g.box(b, (s * 11 - 2.2, 18, -6.2), (s * 11 + 2.2, 22, -4.2), "aco")
            g.box(b, (s * 11 - 1.4, 18.6, -6.6), (s * 11 + 1.4, 21.4, -6.2), "luz")
            mao(g, b, (s * 11, 17, -3), s, "armadura2", "aco", dedos=4, L=2.4, w=0.8)
    for s, sd in sides():
        b = g.bone(f"leg_{sd}", (s * 3.5, 20, 0), "root", f"leg:{sd}")
        g.limb(b, (s * 3.6, 20, 0), (s * 4, 11, -1), 3.6, "armadura2", taper_to=3.2)
        g.cbox(b, (s * 4, 11, -2.4), (3.6, 3.0, 2.0), "armadura")
        g.limb(b, (s * 4, 11, -1), (s * 4.2, 2.5, 0), 3.2, "armadura", taper_to=3.0)
        g.box(b, (s * 4.2 - 2.4, 0, -4.4), (s * 4.2 + 2.4, 2.8, 2.4), "aco")
        g.box(b, (s * 4.2 - 0.3, 4, -1.9), (s * 4.2 + 0.3, 10, -1.5), "luz")
        g.box(b, (s * 4.2 - 2.0, 5, -2.2), (s * 4.2 + 2.0, 8, 2.0), "armadura2")                    # caneleira
        for k in range(3): g.box(b, (s * 3.6 - 2.2, 17 - k * 2, -2.6), (s * 3.6 + 2.2, 18 - k * 2, 2.6), "armadura")   # coxote em lamelas
    # construtos flutuando em volta (placas, vigas e um cubo de contenção)
    g.bone("construtos", (0, 30, 0), "root", "spin")
    for i in range(6):
        a = 2 * math.pi * i / 6; r = 17
        x, z = r * math.sin(a), r * math.cos(a); y = 24 + (i % 3) * 7
        if i % 3 == 0: g.cbox("construtos", (x, y, z), (5, 7, 0.6), "construto", rot=(0, math.degrees(a), 0))
        elif i % 3 == 1: g.cbox("construtos", (x, y, z), (1.2, 1.2, 9), "construto", rot=(30, math.degrees(a), 0))
        else:
            g.cbox("construtos", (x, y, z), (4, 4, 4), "construto", rot=(45, 45, 0)); g.cbox("construtos", (x, y, z), (1.6, 1.6, 1.6), "luz")
    extra = {"attack.hammer": {"animation_length": 1.2, "bones": {
                "arm_r": kf({"0.0": [0, 0, 0], "0.45": [-170, 0, 0], "0.7": [10, 0, 0], "1.2": [0, 0, 0]}),
                "body": kf({"0.0": [0, 0, 0], "0.45": [-10, 0, 0], "0.7": [15, 0, 0], "1.2": [0, 0, 0]})}},
             "attack.build": {"animation_length": 1.5, "bones": {
                "arm_l": kf({"0.0": [0, 0, 0], "0.4": [-90, 0, 0], "1.1": [-90, 0, 0], "1.5": [0, 0, 0]}),
                "construtos": {"position": {"0.0": [0, 0, 0], "0.6": [0, 0, -12], "1.1": [0, 0, -12], "1.5": [0, 0, 0]},
                               "scale": {"0.0": [1, 1, 1], "0.6": [0.5, 1, 0.5], "1.5": [1, 1, 1]}}}},
             "misc.phase2": {"loop": True, "animation_length": 1.0, "bones": {"construtos": {"rotation": [0, "query.anim_time * 360", 0]}}}}
    morte(extra)
    return g, dict(scale=1.5, label="Verdan, o Arquiteto (Vontade)", translucent=True, extra=extra,
                   notes="Armadura pesada com fenda de visor, gerador nas costas. Mão direita = martelo de construto (translúcido). "
                         "attack.hammer, attack.build (o osso 'construtos' se junta à frente: use para gerar paredes/prisões), misc.phase2.")

BOSSES = [("karvhul_forjador_rubro", karvhul), ("vesk_morrath_carcereiro", vesk), ("ghormaddu_arca_viva", ghormaddu),
          ("seraphel_do_vitral", seraphel), ("gemeos_velados", gemeos), ("calithe_rainha_do_espelho", calithe),
          ("morthaal_coletor_de_lanternas", morthaal), ("verdan_o_arquiteto", verdan)]

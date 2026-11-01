# Parte 4: nave_patrulha (8 Tropas), nave_cargueiro (2 pinturas), nave_pirata_capitania (2 pinturas, 1024x1024), destroços
import sys, math, random
sys.path.insert(0, "/home/claude/naves"); sys.path.insert(0, "/home/claude/lore3d"); sys.path.insert(0, "/home/claude/especies")
from naves import *
from naves import _sh
from core import Atlas, hx, mix, lt, dk, PATTERNS

def pat_container(A, x0, y0, S, kind, col, step=6):
    """chapa corrugada de contêiner"""
    for y in range(S):
        for x in range(S):
            c = _sh(col, kind, y / (S - 1))
            if kind == 0:
                k = x % step
                if k == 0: c = dk(c, 0.3)
                elif k == 1: c = lt(c, 0.12)
                elif k == step - 1: c = dk(c, 0.12)
            if y < 2 or y >= S - 2: c = dk(col, 0.45)
            n = A.r.randint(-3, 3); A.put(x0 + x, y0 + y, tuple(v + n for v in hx(c)))

def pat_burnt(A, x0, y0, S, kind, col, seed=0):
    """casco queimado: fuligem, bordas derretidas, chapas"""
    r = random.Random(seed * 17 + kind)
    blobs = [(r.randrange(S), r.randrange(S), r.uniform(4, S / 3)) for _ in range(6)]
    for y in range(S):
        for x in range(S):
            c = _sh(col, kind, y / (S - 1))
            if x % (S // 2) == 0 or y % (S // 2) == 0: c = dk(c, 0.35)
            soot = max([max(0, 1 - math.hypot(x - bx, y - by) / br) for bx, by, br in blobs] + [0])
            c = mix(c, "#141210", min(0.9, soot * 1.2))
            if 0.55 < soot < 0.6: c = mix(c, "#6A4020", 0.6)
            n = r.randint(-5, 5); A.put(x0 + x, y0 + y, tuple(v + n for v in hx(c)))

def pat_embers(A, x0, y0, S, kind, base="#1A1410", hot="#FF7A20"):
    """metal ainda em brasa (vai no glowmask)"""
    r = random.Random(99 + kind)
    for y in range(S):
        for x in range(S):
            d = (math.sin(x * 0.7) + math.cos(y * 0.5 + x * 0.2)) * 0.5
            if d > 0.45 + r.random() * 0.2: A.put(x0 + x, y0 + y, mix(hot, "#FFE080", r.random() * 0.5), 255, True)
            else: A.put(x0 + x, y0 + y, mix(base, "#2A2018", r.random()))

PATTERNS.update(container=pat_container, burnt=pat_burnt, embers=pat_embers)

def X2(s, a, b): return (min(s * a, s * b), max(s * a, s * b))

def torre(N, i, cx, cy, cz, k=1.0, parent="casco", mats=("metal_esc", "casco2", "metal", "faixa"), barrels=2):
    """torre giratória: osso canhao_i na base (eixo vertical) e boca_i na ponta do cano"""
    B, M = N.B, N.M
    me, c2, mt, fx = mats
    N.bone(f"canhao_{i}", (cx, cy, cz), parent)
    B(f"canhao_{i}", cx - 9 * k, cx + 9 * k, cy, cy + 3 * k, cz - 9 * k, cz + 9 * k, me)
    B(f"canhao_{i}", cx - 7 * k, cx + 7 * k, cy + 3 * k, cy + 9 * k, cz - 7 * k, cz + 7 * k, c2)
    B(f"canhao_{i}", cx - 5 * k, cx + 5 * k, cy + 9 * k, cy + 11 * k, cz - 5 * k, cz + 4 * k, c2)
    B(f"canhao_{i}", cx - 7.5 * k, cx + 7.5 * k, cy + 4 * k, cy + 6 * k, cz - 7.5 * k, cz + 7.5 * k, fx)
    by = cy + 6.5 * k; L = 22 * k
    offs = (-3.5 * k, 3.5 * k) if barrels == 2 else (0,)
    for ox in offs: B(f"canhao_{i}", cx + ox - 1.5 * k, cx + ox + 1.5 * k, by - 1.5 * k, by + 1.5 * k, cz - 6 * k - L, cz - 6 * k, mt)
    N.bone(f"boca_{i}", (cx, by, cz - 6 * k - L), f"canhao_{i}")
    for ox in offs:
        B(f"boca_{i}", cx + ox - 2 * k, cx + ox + 2 * k, by - 2 * k, by + 2 * k, cz - 10 * k - L, cz - 6 * k - L, me)
        B(f"boca_{i}", cx + ox - 1 * k, cx + ox + 1 * k, by - 1 * k, by + 1 * k, cz - 10.4 * k - L, cz - 9.9 * k - L, "motor")

def motor(N, i, cx, cy, cz0, cz1, hw, hh, mats=("casco2", "metal"), parent="casco", ring="luz"):
    """nacele com bocal, brilho e osso chama_i (o fogo)"""
    B = N.B; m0, m1 = mats
    N.bone(f"propulsor_{i}", (cx, cy, cz1), parent)
    B(f"propulsor_{i}", cx - hw, cx + hw, cy - hh, cy + hh, cz0, cz1, m0)
    B(f"propulsor_{i}", cx - hw - 1, cx + hw + 1, cy - hh - 1, cy + hh + 1, cz1, cz1 + 4, m1)
    B(f"propulsor_{i}", cx - hw + 2, cx + hw - 2, cy - hh + 2, cy + hh - 2, cz1 + 3.6, cz1 + 4.2, "motor")
    if ring: B(f"propulsor_{i}", cx - hw - 0.5, cx + hw + 0.5, cy - 1, cy + 1, cz0 + 4, cz1 - 4, ring)
    N.bone(f"chama_{i}", (cx, cy, cz1 + 4), f"propulsor_{i}")
    B(f"chama_{i}", cx - hw + 3, cx + hw - 3, cy - hh + 3, cy + hh - 3, cz1 + 4, cz1 + 4 + max(6, hh), "chama")

def colisoes(N, cols):
    for i, (a, b, c, d, e, f) in enumerate(cols, 1):
        N.bone(f"col_{i}", (0, 0, 0), "root")
        N.B(f"col_{i}", a, b, c, d, e, f, "colisao")

def seats(N, lst, y, mat="assento", back=14):
    for nm, x, z in lst:
        N.bone(nm, (x, y, z), "casco")
        N.B(nm, x - 5, x + 5, y - 3, y, z - 5, z + 4, mat)
        N.B(nm, x - 5, x + 5, y, y + back, z + 4, z + 7, mat)

# ======================================================================== NAVE PATRULHA (10 x 4 x 14 blocos)
def patrulha(cor):
    C = LANCEIRA_COR[cor]
    A = Atlas(TEX, SLOT, seed=31)
    A.material("casco", "hull", C["casco"], dk(C["casco"], 0.4), step=32, seed=11)
    A.material("casco2", "hull", C["casco2"], dk(C["casco2"], 0.5) if cor != "blue" else lt(C["casco2"], 0.3), step=32, seed=12)
    A.material("metal", "metal", "#5C6168")
    A.material("metal_esc", "metal", "#2E3238", rivets=False)
    A.material("faixa", "stripe", C["casco2"], C["casco"], width=8)
    A.material("vidro", "glass", mix("#9FE8FF", C["luz"], 0.35), alpha=110, translucent=True)
    A.material("luz", "strip", C["luz"], C["casco2"])
    A.material("motor", "glow", C["luz"], core="#FFFFFF")
    A.material("chama", "flame", C["luz"], translucent=True)
    A.material("interior", "solid", "#2A2D33", noise=4)
    A.material("assento", "fabric", "#1E2126", trim=C["casco"])
    A.material("ventil", "vent", "#3A3E45", glowcol=C["luz"])
    A.material("colisao", "invisible")
    A.decal("emblema", emblem_decal, EMBLEMAS[C["emb"]], C["casco2"], C["luz"], ring=C["luz"], scale=5)
    A.decal("emblema_topo", emblem_decal, EMBLEMAS[C["emb"]], C["casco2"], C["luz"], ring=C["luz"], scale=5, flip=True)
    A.decal("painel", panel_decal, "#24272C", [C["luz"], "#FF5050", "#FFD050", "#60C0FF"])
    A.decal("numero", text_decal, C["casco"], C["casco2"] if cor != "blue" else "#1E3A6A", "P-21")
    N = Nave("nave_patrulha", A); B, M = N.B, N.M
    N.bone("root", (0, 0, 0)); N.bone("casco", (0, 30, 0), "root")
    # nariz
    N.loft("casco", [(-112, 8, 18, 26), (-104, 14, 15, 31), (-96, 20, 12, 35), (-88, 26, 10, 38)], "casco")
    M("casco", 13, 14, 20, 23, -104, -96, "luz"); B("casco", -2, 2, 20, 23, -116, -112, "metal")
    # cabine (aberta por cima, coberta pela canopy)
    B("casco", -28, 28, 10, 16, -88, -38, "casco2")
    M("casco", 24, 28, 16, 34, -88, -38, "casco")
    M("casco", 28, 28.4, 24, 27, -88, -38, "luz")
    B("casco", -24, 24, 16, 26, -88, -82, "interior", decals={"south": "painel"})
    N.bone("cabine_vidro", (0, 34, -60), "casco")
    B("cabine_vidro", -24, 24, 34, 40, -88, -78, "vidro"); B("cabine_vidro", -24, 24, 34, 48, -78, -40, "vidro")
    for z in (-79, -60, -41): B("cabine_vidro", -24, 24, 48, 49, z, z + 2, "metal_esc")
    M("cabine_vidro", 24, 25, 34, 48, -78, -40, "metal_esc")
    seats(N, [("assento_piloto", -9, -72), ("assento_1", 9, -72), ("assento_2", -9, -52), ("assento_3", 9, -52)], 19)
    # compartimento da porta (z -38..-10)
    B("casco", -28, 28, 10, 12, -38, -10, "casco2"); B("casco", -28, 28, 46, 50, -38, -10, "casco")
    B("casco", -28, -24, 12, 46, -38, -10, "casco"); B("casco", -24, -22, 12, 46, -38, -10, "interior")
    B("casco", 24, 28, 12, 46, -38, -35, "casco"); B("casco", 24, 28, 12, 46, -13, -10, "casco")
    B("casco", 24, 28, 44, 46, -35, -13, "casco")
    B("casco", -24, 24, 34, 46, -40, -38, "casco2")
    N.bone("porta", (27, 44, -24), "casco")
    B("porta", 26, 28, 12, 44, -35, -13, "casco")
    B("porta", 28, 28.3, 15, 29, -31, -17, "casco", decals={"west": "numero"})
    B("porta", 28, 28.5, 34, 36, -33, -15, "luz")
    # corpo central
    B("casco", -28, 28, 10, 48, -10, 30, "casco"); B("casco", -28, 28, 10, 44, 30, 70, "casco")
    B("casco", -18, 18, 48, 52, -10, 34, "casco2"); B("casco", -14, 14, 52, 52.4, -6, 26, "casco2", decals={"up": "emblema_topo"})
    B("casco", -10, 10, 44, 46, 34, 68, "ventil")
    M("casco", 28, 28.4, 20, 23, -10, 70, "luz")
    M("casco", 28, 28.3, 26, 40, 32, 64, "faixa")
    B("casco", -22, 22, 8, 10, -30, 60, "casco2")
    # motor central
    motor(N, 1, 0, 28, 70, 104, 16, 14)
    # asas e pods
    for s in (1, -1):
        x0, x1 = X2(s, 28, 44); B("casco", x0, x1, 18, 26, -10, 60, "casco")
        x0, x1 = X2(s, 28, 44); B("casco", x0, x1, 26, 26.4, 4, 40, "casco2", decals={"up": "emblema_topo"})
        x0, x1 = X2(s, 28, 44); B("casco", x0, x1, 19, 25, -12, -10, "luz")
    for i, s in ((2, -1), (3, 1)):
        cx = 54 * s
        motor(N, i, cx, 24, -4, 96, 10, 12, mats=("casco2", "metal"))
        B(f"propulsor_{i}", cx - 7, cx + 7, 15, 33, -16, -4, "casco")
        B(f"propulsor_{i}", cx - 4, cx + 4, 18, 30, -18, -16, "metal_esc")
        B(f"propulsor_{i}", cx - 10.5, cx + 10.5, 14, 34, 0, 40, "casco")
        x0, x1 = X2(s, 64, 64.3); B(f"propulsor_{i}", x0, x1, 18, 32, 2, 38, "casco", decals={"west": "emblema", "east": "emblema"})
        B(f"propulsor_{i}", cx - 1.5, cx + 1.5, 36, 54, 70, 96, "casco2")          # deriva
        B(f"propulsor_{i}", cx - 1.5, cx + 1.5, 54, 56, 80, 98, "luz")
    # torres sobre os pods
    torre(N, 1, -54, 36, 10); torre(N, 2, 54, 36, 10)
    # trem de pouso
    N.bone("trem_pouso", (0, 10, 0), "casco")
    for (x, z, y0) in ((0, -96, 10), (-54, 6, 12), (54, 6, 12), (-54, 80, 12), (54, 80, 12)):
        B("trem_pouso", x - 1.5, x + 1.5, 2, y0, z - 1.5, z + 1.5, "metal")
        B("trem_pouso", x - 2.5, x + 2.5, 6, 8, z - 2.5, z + 2.5, "metal_esc")
        B("trem_pouso", x - 4, x + 4, 0, 2, z - 5, z + 5, "metal_esc")
    colisoes(N, [(-28, 28, 10, 40, -112, -88), (-28, 28, 10, 49, -88, -10), (-28, 28, 8, 52, -10, 70), (-16, 16, 14, 42, 70, 108),
                 (-64, -44, 12, 36, -18, 100), (44, 64, 12, 36, -18, 100), (-44, 44, 18, 26, -12, 60), (-63, 63, 36, 56, -8, 98)])
    free_pixel(A)
    return N.g, A

# ======================================================================== NAVE CARGUEIRO (12 x 6 x 20 blocos)
CARGUEIRO_PINTURA = {
    1: dict(casco="#D8D4CC", casco2="#3A3C40", faixa="#E07020", luz="#FFC060", vidro="#A0E0FF", nome="rota",
            conts=("#C8501E", "#2E5E8A", "#6A7A3A")),
    2: dict(casco="#3A5878", casco2="#1E2630", faixa="#E8C030", luz="#80E0FF", vidro="#B0F0FF", nome="frota",
            conts=("#8A2A2A", "#D0A020", "#4A7A6A")),
}

def cargueiro(var):
    P = CARGUEIRO_PINTURA[var]
    A = Atlas(TEX, SLOT, seed=41)
    A.material("casco", "hull", P["casco"], dk(P["casco"], 0.35), step=32, grime=0.6, seed=21)
    A.material("casco2", "hull", P["casco2"], dk(P["casco2"], 0.5), step=32, seed=22)
    A.material("metal", "metal", "#5C6168")
    A.material("metal_esc", "metal", "#2E3238", rivets=False)
    A.material("faixa", "hazard", P["faixa"], "#1A1A1A")
    A.material("vidro", "glass", P["vidro"], alpha=110, translucent=True)
    A.material("luz", "strip", P["luz"], P["casco2"])
    A.material("motor", "glow", P["luz"], core="#FFFFFF")
    A.material("chama", "flame", P["luz"], translucent=True)
    A.material("interior", "solid", "#2A2D33", noise=4)
    A.material("assento", "fabric", "#3A3028", trim=P["faixa"])
    A.material("ventil", "vent", "#3A3E45", glowcol=P["luz"])
    for i, c in enumerate(P["conts"]): A.material(f"cont{i}", "container", c)
    A.material("colisao", "invisible")
    A.decal("painel", panel_decal, "#24272C", [P["luz"], "#FF5050", "#50FF80"])
    A.decal("numero", text_decal, P["casco"], P["casco2"], "C-220")
    A.decal("numero_d", text_decal, P["casco"], P["casco2"], "C-220")
    N = Nave("nave_cargueiro", A); B, M = N.B, N.M
    N.bone("root", (0, 0, 0)); N.bone("casco", (0, 48, 0), "root")
    # proa e cabine elevada
    N.loft("casco", [(-160, 20, 18, 34), (-150, 26, 14, 40), (-140, 30, 12, 44), (-128, 32, 12, 46)], "casco")
    B("casco", -32, 32, 12, 44, -128, -110, "casco")
    B("casco", -32, 32, 40, 46, -152, -110, "casco2")                               # piso da cabine
    M("casco", 28, 32, 46, 60, -152, -112, "casco")
    B("casco", -28, 28, 46, 54, -152, -146, "interior", decals={"south": "painel"})
    B("casco", -32, 32, 78, 82, -152, -110, "casco")                                # teto
    B("casco", -32, 32, 46, 78, -114, -110, "casco2")
    M("casco", 28, 32, 60, 78, -116, -112, "casco")
    N.bone("cabine_vidro", (0, 68, -132), "casco")
    B("cabine_vidro", -28, 28, 54, 78, -154, -152, "vidro")
    M("cabine_vidro", 30, 32, 60, 78, -152, -116, "vidro")
    B("cabine_vidro", -1, 1, 54, 78, -155, -152, "metal_esc")
    B("casco", -6, 6, 82, 86, -140, -124, "metal"); B("casco", -1, 1, 86, 96, -132, -130, "metal")   # antena
    M("casco", 32, 32.4, 48, 51, -152, -110, "luz")
    seats(N, [("assento_piloto", 0, -136), ("assento_1", -14, -124), ("assento_2", 14, -124)], 49, back=12)
    # porão (z -110..90), com vão livre atrás da rampa traseira
    for z0, z1, m in ((-110, -50, "casco"), (-50, 10, "casco"), (10, 70, "casco")):
        B("casco", -48, 48, 12, 76, z0, z1, m)
    B("casco", -48, -24, 12, 76, 70, 90, "casco"); B("casco", 24, 48, 12, 76, 70, 90, "casco")
    B("casco", -24, 24, 62, 76, 70, 90, "casco"); B("casco", -24, 24, 12, 14, 70, 90, "metal_esc")
    B("casco", -24, 24, 14, 62, 69, 70, "interior")
    for z in (-100, -60, -20, 20, 60):
        B("casco", -50, 50, 74, 78, z, z + 4, "metal")                               # nervuras
    M("casco", 48, 48.4, 64, 68, -110, 90, "faixa")
    B("casco", -48, 48, 76, 76.4, -60, 40, "casco2")
    M("casco", 32, 32.3, 44, 52, -146, -118, "casco", decals={"west": "numero", "east": "numero_d"})
    # contêineres nas laterais
    for s in (1, -1):
        for k, (z0, z1) in enumerate(((-104, -54), (-50, 0), (4, 54))):
            x0, x1 = X2(s, 50, 90); m = f"cont{(k + (s > 0)) % 3}"
            B("casco", x0, x1, 16, 56, z0, z1, m)
            x0, x1 = X2(s, 48, 92); B("casco", x0, x1, 56, 58, z0 - 1, z1 + 1, "metal_esc")
        x0, x1 = X2(s, 48, 92); B("casco", x0, x1, 14, 16, -106, 56, "metal_esc")
        x0, x1 = X2(s, 50, 90); B("casco", x0, x1, 58, 60, -80, -30, f"cont{2 - (s > 0)}")
    # motores
    for i, s in ((1, -1), (2, 1)):
        motor(N, i, 64 * s, 40, 60, 146, 16, 20)
        x0, x1 = X2(s, 48, 50); B(f"propulsor_{i}", x0, x1, 30, 50, 70, 120, "metal_esc")
    motor(N, 3, 0, 84, 70, 150, 10, 8, mats=("casco2", "metal"))
    B("casco", -8, 8, 76, 78, 70, 140, "metal_esc")
    # rampa traseira (dobradiça embaixo, desce para trás)
    N.bone("porta", (0, 14, 91), "casco")
    B("porta", -24, 24, 14, 62, 90, 92, "casco2")
    B("porta", -24, 24, 14, 62, 92, 92.4, "faixa")
    for k in range(7): B("porta", -22, 22, 17 + k * 6, 18 + k * 6, 89, 90, "metal")
    # trem
    N.bone("trem_pouso", (0, 12, 0), "casco")
    for (x, z) in ((-30, -120), (30, -120), (-36, -30), (36, -30), (-36, 60), (36, 60)):
        B("trem_pouso", x - 2, x + 2, 2, 12, z - 2, z + 2, "metal")
        B("trem_pouso", x - 3, x + 3, 7, 9, z - 3, z + 3, "metal_esc")
        B("trem_pouso", x - 5, x + 5, 0, 2, z - 6, z + 6, "metal_esc")
    colisoes(N, [(-32, 32, 12, 46, -160, -110), (-32, 32, 40, 96, -155, -110), (-48, 48, 12, 78, -110, 92), (-92, -48, 14, 60, -106, 56),
                 (48, 92, 14, 60, -106, 56), (-82, -46, 18, 62, 58, 152), (46, 82, 18, 62, 58, 152), (-10, 10, 74, 94, 68, 156)])
    free_pixel(A)
    return N.g, A

# ======================================================================== CAPITÂNIA PIRATA (24 x 10 x 36 blocos)
CAPITANIA_PINTURA = {
    1: dict(casco="#6A2A1E", casco2="#1C1A1A", faixa="#D9A400", luz="#FF4020", vidro="#FFB080", nome="sangue"),
    2: dict(casco="#2E3A44", casco2="#141618", faixa="#C8C8C0", luz="#40FFA0", vidro="#A0FFD0", nome="abismo"),
}

def capitania(var):
    P = CAPITANIA_PINTURA[var]
    A = Atlas(1024, 128, seed=51)
    A.material("casco", "hull", P["casco"], dk(P["casco"], 0.45), step=64, rust=0.9, grime=1.0, seed=31)
    A.material("casco2", "hull", P["casco2"], dk(P["casco2"], 0.5), step=32, rust=0.4, grime=0.6, seed=32)
    A.material("remendo", "hull", mix(P["casco"], "#7A7A70", 0.5), "#3A3632", step=32, rust=1.0, seed=33)
    A.material("metal", "metal", "#5A5650")
    A.material("metal_esc", "metal", "#2A2826", rivets=False)
    A.material("faixa", "hazard", P["faixa"], "#1A1816", w=12)
    A.material("vidro", "glass", P["vidro"], alpha=120, translucent=True)
    A.material("luz", "strip", P["luz"], P["casco2"])
    A.material("motor", "glow", P["luz"], core="#FFF0C0")
    A.material("chama", "flame", P["luz"], core="#FFF0C0", translucent=True)
    A.material("interior", "solid", "#262420", noise=5)
    A.material("assento", "fabric", "#3A2A20", trim="#5A4030")
    A.material("ventil", "vent", "#3A3632", glowcol=P["luz"])
    A.material("dentes", "metal", "#8A8478")
    A.material("hangar_piso", "hazard", "#2A2826", P["faixa"], w=16)
    A.material("colisao", "invisible")
    A.decal("emblema", emblem_decal, EMB_PIRATA, P["casco2"], P["luz"], ring=P["luz"], scale=11)
    A.decal("emblema_topo", emblem_decal, EMB_PIRATA, P["casco2"], P["luz"], ring=P["luz"], scale=11, flip=True)
    A.decal("painel", panel_decal, "#22201C", [P["luz"], "#FFD050", "#50FF80"])
    A.decal("numero", text_decal, P["casco"], "#E8E0D0", "PX-01")
    A.decal("numero_d", text_decal, P["casco"], "#E8E0D0", "PX-01")
    N = Nave("nave_pirata_capitania", A); B, M = N.B, N.M
    rng = random.Random(5)
    N.bone("root", (0, 0, 0)); N.bone("casco", (0, 80, 0), "root")
    HG = (-30, 124, 18, 76, -64, 64)        # vão do hangar (x0, x1, y0, y1, z0, z1)
    def grid(x0, x1, y0, y1, z0, z1, mats=("casco", "casco", "casco2", "remendo"), n=64, skip=None):
        nx, ny, nz = max(1, round((x1 - x0) / n)), max(1, round((y1 - y0) / n)), max(1, round((z1 - z0) / n))
        for i in range(nx):
            for j in range(ny):
                for k in range(nz):
                    a = (x0 + (x1 - x0) * i / nx, x0 + (x1 - x0) * (i + 1) / nx); b = (y0 + (y1 - y0) * j / ny, y0 + (y1 - y0) * (j + 1) / ny)
                    c = (z0 + (z1 - z0) * k / nz, z0 + (z1 - z0) * (k + 1) / nz)
                    if skip and a[0] >= skip[0] - 1 and a[1] <= skip[1] + 6 and b[0] >= skip[2] - 1 and b[1] <= skip[3] + 1 and c[0] >= skip[4] - 1 and c[1] <= skip[5] + 1: continue
                    B("casco", a[0], a[1], b[0], b[1], c[0], c[1], rng.choice(mats))
    # casco inferior (com o vão do hangar à esquerda)
    grid(-120, -30, 16, 80, -200, 224)
    grid(-30, 124, 16, 18, -64, 64, mats=("hangar_piso",))                     # piso do hangar
    grid(-30, 124, 76, 80, -64, 64, mats=("casco2",))                          # teto do hangar
    grid(-30, 124, 16, 80, -200, -64); grid(-30, 124, 16, 80, 64, 224)
    B("casco", -32, -30, 18, 76, -64, 64, "interior")                          # fundo do hangar
    for z in (-62, 62):
        B("casco", -30, 120, 74, 76, z - 1, z + 1, "luz")                       # luzes do hangar
    for x in (10, 50, 90): B("casco", x - 1, x + 1, 18, 18.3, -50, 50, "faixa")
    B("casco", 120, 124, 16, 80, -68, -64, "faixa"); B("casco", 120, 124, 16, 80, 64, 68, "faixa")
    # convés superior e torre de comando
    grid(-88, 88, 80, 120, -150, 180)
    B("casco", -60, 60, 120, 122, -140, 90, "casco2")
    B("casco", -40, 40, 122, 122.4, -60, 20, "casco2", decals={"up": "emblema_topo"})
    # ponte (oca, envidraçada na frente)
    B("casco", -48, 48, 116, 122, 96, 172, "casco2")
    M("casco", 44, 48, 122, 134, 96, 172, "casco")
    B("casco", -48, 48, 148, 152, 96, 172, "casco")
    B("casco", -48, 48, 122, 148, 168, 172, "casco")
    M("casco", 44, 48, 134, 148, 156, 168, "casco")
    B("casco", -44, 44, 122, 130, 96, 100, "interior", decals={"south": "painel"})
    N.bone("cabine_vidro", (0, 140, 98), "casco")
    B("cabine_vidro", -44, 44, 130, 148, 94, 96, "vidro")
    M("cabine_vidro", 44, 46, 134, 148, 96, 156, "vidro")
    for x in (-22, 0, 22): B("cabine_vidro", x - 1, x + 1, 130, 148, 93, 96, "metal_esc")
    B("casco", -36, 36, 152, 156, 110, 160, "metal_esc"); B("casco", -2, 2, 156, 160, 130, 134, "metal")
    seats(N, [("assento_piloto", 0, 116), ("assento_1", -22, 128), ("assento_2", 22, 128), ("assento_3", -22, 146), ("assento_4", 22, 146), ("assento_5", 0, 152)], 125, back=12)
    # proa / aríete
    for s in (1, -1):
        for k, (z0, z1, w0, y0, y1) in enumerate(((-232, -200, 110, 22, 76), (-260, -232, 92, 28, 68), (-282, -260, 70, 34, 60))):
            x0, x1 = X2(s, 0, w0); B("casco", x0, x1, y0, y1, z0, z1, rng.choice(("casco", "casco2", "remendo")))
        x0, x1 = X2(s, 30, 46); B("casco", x0, x1, 40, 54, -288, -282, "dentes")
        x0, x1 = X2(s, 120, 126); B("casco", x0, x1, 40, 46, -200, 200, "luz")
    for k in range(7):                                                           # dentes do aríete
        z = -282 + k * 12
        B("casco", -6, 6, 16 + (k % 2) * 4, 24, z, z + 6, "dentes")
    B("casco", -70, 70, 60, 80, -232, -200, "casco2")
    # costelas e espinhos (pirata)
    for z in (-180, -120, -60, 0, 60, 120, 180):
        for s in (1, -1):
            if s > 0 and -64 <= z <= 64: continue
            x0, x1 = X2(s, 120, 130); B("casco", x0, x1, 20, 76, z - 4, z + 4, "metal_esc")
            x0, x1 = X2(s, 130, 150); B("casco", x0, x1, 70, 76, z - 2, z + 2, "dentes", rot=(0, 0, -20 * s), pivot=(130 * s, 73, z))
    for z in (-130, -40, 50, 140):
        for s in (1, -1):
            x0, x1 = X2(s, 88, 104); B("casco", x0, x1, 80, 116, z - 6, z + 6, "casco2")
    # radiadores e remendos do lado direito
    for k in range(8):
        z = -170 + k * 18
        B("casco", -150, -120, 26, 70, z, z + 6, "metal")
    B("casco", -121, -120, 24, 36, -200, 224, "faixa")
    B("casco", 120.5, 121, 24, 36, -200, -70, "faixa"); B("casco", 120.5, 121, 24, 36, 70, 224, "faixa")
    B("casco", 120, 121, 40, 76, 100, 180, "casco", decals={"west": "numero"})
    B("casco", -121, -120, 40, 76, 100, 180, "casco", decals={"east": "numero_d"})
    B("casco", -89, -88, 84, 116, -100, -20, "casco", decals={"east": "emblema"})
    B("casco", 88, 89, 84, 116, -100, -20, "casco", decals={"west": "emblema"})
    for (x0, x1, y0, y1, z0, z1) in ((-60, -20, 120, 121, -120, -80), (20, 70, 120, 121, 40, 80), (-121, -120, 44, 70, 0, 40),
                                      (-88.6, -88, 86, 110, 60, 100), (88, 88.6, 90, 112, 120, 160)):
        B("casco", x0, x1, y0, y1, z0, z1, "remendo")
    B("casco", -30, 30, 120, 122, 140, 170, "ventil")
    # popa e motores
    grid(-110, 110, 20, 76, 224, 248, mats=("casco2",))
    for i, (cx, cy, hw, hh) in enumerate(((-56, 50, 30, 26), (56, 50, 30, 26), (-150, 44, 14, 14), (150, 44, 14, 14)), 1):
        motor(N, i, cx, cy, 200 if i < 3 else 120, 276, hw, hh, mats=("casco2" if i < 3 else "remendo", "metal"))
    B("casco", -96, 96, 100, 104, 150, 210, "metal_esc")
    B("casco", -130, -110, 30, 60, 120, 240, "metal_esc"); B("casco", 110, 130, 30, 60, 120, 240, "metal_esc")
    # torres (4)
    for i, (x, z) in enumerate(((-64, -110), (64, -110), (-70, 40), (70, 40)), 1):
        torre(N, i, x, 120, z, k=2.0)
    # hangar (porta grande, dobradiça embaixo, desce para fora à esquerda) e porta da tripulação (direita)
    N.bone("hangar", (122, 16, 0), "casco")
    B("hangar", 120, 124, 16, 80, -64, 64, "casco2")
    B("hangar", 124, 124.5, 18, 78, -62, -54, "faixa"); B("hangar", 124, 124.5, 18, 78, 54, 62, "faixa")
    B("hangar", 124, 124.4, 30, 66, -40, 40, "casco", decals={"west": "emblema"})
    for k in range(8): B("hangar", 118, 120, 20 + k * 7, 21 + k * 7, -60, 60, "metal")
    N.bone("porta", (-122, 18, -150), "casco")
    B("porta", -124, -120, 18, 58, -166, -134, "casco")
    B("porta", -124.5, -124, 20, 56, -166, -162, "faixa"); B("porta", -124.5, -124, 20, 56, -138, -134, "faixa")
    for k in range(6): B("porta", -120, -119, 22 + k * 6, 23 + k * 6, -164, -136, "metal_esc")
    # trem de pouso
    N.bone("trem_pouso", (0, 16, 0), "casco")
    for (x, z) in ((-80, -180), (80, -180), (-90, 0), (-90, 160), (90, 160), (0, -240), (90, -100)):
        B("trem_pouso", x - 3, x + 3, 3, 16, z - 3, z + 3, "metal")
        B("trem_pouso", x - 5, x + 5, 9, 12, z - 5, z + 5, "faixa")
        B("trem_pouso", x - 8, x + 8, 0, 3, z - 10, z + 10, "metal_esc")
    colisoes(N, [(-120, -30, 16, 80, -200, 248), (-30, 124, 16, 80, -200, -64), (-30, 124, 16, 80, 64, 248), (-110, 110, 16, 76, -288, -200),
                 (-88, 104, 80, 122, -150, 180), (-48, 48, 116, 160, 96, 172), (-180, -120, 20, 76, -180, 290), (120, 180, 20, 76, 120, 290)])
    free_pixel(A)
    return N.g, A

# ======================================================================== DESTROÇOS
def _destroco_atlas(seed):
    A = Atlas(TEX, SLOT, seed=seed)
    A.material("queimado", "burnt", "#5A6068", seed=seed)
    A.material("queimado2", "burnt", "#3A3E44", seed=seed + 1)
    A.material("verde", "burnt", "#2E6A40", seed=seed + 2)            # resto de pintura de Tropa
    A.material("metal", "metal", "#4A4C50")
    A.material("metal_esc", "metal", "#24262A", rivets=False)
    A.material("brasa", "embers")
    A.material("vidro", "glass", "#7A9AA8", alpha=90, translucent=True)
    A.material("interior", "solid", "#1E2024", noise=6)
    A.material("cont", "container", "#7A3A20")
    A.material("cont2", "container", "#2E4A6A")
    A.material("fios", "vent", "#2A2A2A", glowcol="#FFB040")
    A.material("faisca", "glow", "#FFD080", core="#FFFFFF")
    A.material("colisao", "invisible")
    return A

def destrocos(n):
    A = _destroco_atlas(60 + n)
    N = Nave(f"destrocos_{n}", A); B, M = N.B, N.M
    N.bone("root", (0, 0, 0)); N.bone("casco", (0, 0, 0), "root")
    sparks = []
    if n == 1:      # caça partido: fuselagem inclinada enterrada, asa arrancada ao lado, canopy quebrada (6 x 3 x 10)
        N.bone("fuselagem", (0, 0, 0), "casco")
        R = dict(rot=(8, 12, -14), pivot=(0, 0, 0))
        B("fuselagem", -16, 16, -4, 24, -60, 30, "queimado", **R)
        B("fuselagem", -12, 12, 0, 20, -76, -60, "verde", **R)
        B("fuselagem", -6, 6, 4, 14, -84, -76, "queimado2", **R)
        B("fuselagem", -12, 12, 24, 30, -50, -20, "vidro", **R)
        B("fuselagem", -12, 12, 24, 25, -20, 10, "metal_esc", **R)
        B("fuselagem", -10, 10, 2, 22, 30, 52, "queimado2", **R)
        B("fuselagem", -8, 8, 4, 20, 52, 56, "brasa", **R)
        B("fuselagem", 16, 34, 6, 10, -10, 26, "verde", **R)                        # toco da asa
        B("fuselagem", 16, 17, 2, 14, 0, 20, "fios", **R)
        N.bone("asa_solta", (-58, 0, 30), "casco")
        B("asa_solta", -80, -40, 0, 4, 10, 54, "verde", rot=(0, 35, 12), pivot=(-58, 0, 30))
        B("asa_solta", -76, -70, 4, 7, 20, 60, "metal_esc", rot=(0, 35, 12), pivot=(-58, 0, 30))
        N.bone("motor_solto", (40, 0, 60), "casco")
        B("motor_solto", 30, 48, 0, 14, 46, 78, "queimado2", rot=(0, -20, 90), pivot=(40, 7, 60))
        B("motor_solto", 32, 46, 2, 12, 78, 80, "brasa", rot=(0, -20, 90), pivot=(40, 7, 60))
        for (x, z, w, rr) in ((-20, -90, 10, 20), (26, -60, 8, -30), (-40, -20, 12, 50), (50, 10, 6, 10), (-10, 70, 9, -15)):
            B("casco", x - w, x + w, -16, -14, z - w / 2, z + w / 2, "queimado", rot=(0, rr, 0), pivot=(x, -15, z))
        sparks = [(0, 22, 40), (18, 12, 0), (-58, 4, 30)]
        cols = [(-18, 18, -16, 14, -84, 56), (-80, -36, -16, -8, 6, 60), (26, 52, -16, 2, 40, 82)]
    elif n == 2:    # seção de cargueiro partida ao meio, costelas expostas e contêineres espalhados (12 x 6 x 16)
        N.bone("secao", (0, 0, 0), "casco")
        R = dict(rot=(0, 0, 16), pivot=(0, -16, 0))
        B("secao", -48, 48, -16, -10, -100, 60, "queimado2", **R)                    # piso
        B("secao", -48, -42, -10, 60, -100, 60, "queimado", **R)                     # parede direita
        B("secao", -48, 48, 54, 60, -100, -40, "queimado", **R)                      # teto (só um pedaço)
        for z in (-90, -60, -30, 0, 30):
            B("secao", 42, 48, -10, 40 - (z % 3) * 10, z, z + 6, "metal_esc", **R)   # costelas sem chapa
            B("secao", -42, 48, 54, 60, z, z + 6, "metal_esc", **R) if z > -40 else None
        B("secao", -48, 48, -10, -6, 56, 60, "brasa", **R)                           # borda rompida (em brasa)
        B("secao", -48, -42, -6, 40, 58, 61, "brasa", **R)
        B("secao", -10, 30, 54, 58, -44, -40, "brasa", **R)
        B("secao", -42, -41, 10, 22, -60, 20, "fios", **R)
        for (x, z, ry, m, tip) in ((70, -20, 25, "cont", 0), (60, 60, -40, "cont2", 90), (-80, 80, 10, "cont", 0), (20, 100, 70, "cont2", 0)):
            if tip: B("casco", x - 20, x + 20, -16, 4, z - 25, z + 25, m, rot=(0, ry, 0), pivot=(x, -6, z))
            else: B("casco", x - 20, x + 20, -16, 24, z - 25, z + 25, m, rot=(0, ry, 0), pivot=(x, 4, z))
        B("casco", -60, -20, -16, -10, -128, -100, "queimado", rot=(0, 30, 0), pivot=(-40, -13, -114))
        B("casco", 40, 90, -16, -12, -120, -90, "queimado2", rot=(0, -15, 0), pivot=(65, -14, -105))
        sparks = [(-30, 30, 58), (40, 10, -60), (0, 50, -80)]
        cols = [(-60, 60, -16, 60, -100, 62), (46, 96, -16, 24, -48, 10), (36, 84, -16, 4, 30, 90), (-100, -60, -16, 24, 52, 108), (-4, 44, -16, 24, 76, 124)]
    else:           # campo de destroços: torre arrancada, motor, vigas e placas (12 x 4 x 14)
        N.bone("torre_solta", (40, -16, -40), "casco")
        B("torre_solta", 22, 58, -16, -6, -58, -22, "metal_esc", rot=(0, 20, 18), pivot=(40, -16, -40))
        B("torre_solta", 26, 54, -6, 8, -54, -26, "queimado2", rot=(0, 20, 18), pivot=(40, -16, -40))
        B("torre_solta", 34, 40, 0, 6, -100, -54, "metal", rot=(0, 20, 18), pivot=(40, -16, -40))
        B("torre_solta", 42, 48, 0, 6, -90, -54, "metal", rot=(0, 20, 18), pivot=(40, -16, -40))
        N.bone("motor_solto", (-40, -16, 40), "casco")
        B("motor_solto", -66, -14, -16, 20, 10, 70, "queimado", rot=(0, -30, 0), pivot=(-40, 2, 40))
        B("motor_solto", -62, -18, -12, 16, 70, 76, "metal", rot=(0, -30, 0), pivot=(-40, 2, 40))
        B("motor_solto", -58, -22, -8, 12, 75.6, 76.2, "brasa", rot=(0, -30, 0), pivot=(-40, 2, 40))
        for (x, z, ry, L) in ((-10, -60, 40, 110), (30, 40, -20, 90), (-70, -40, 75, 70)):
            B("casco", x - 3, x + 3, -16, -10, z - L / 2, z + L / 2, "metal_esc", rot=(0, ry, 0), pivot=(x, -13, z))
        for (x, z, w, d, ry, rz, m) in ((0, 0, 30, 24, 15, 6, "queimado"), (60, 30, 24, 30, -35, -8, "verde"), (-80, 90, 26, 20, 60, 10, "queimado2"),
                                        (70, 100, 20, 16, 10, 0, "queimado"), (-20, 90, 18, 14, -50, 12, "verde"), (-90, -90, 22, 18, 30, -10, "queimado")):
            B("casco", x - w / 2, x + w / 2, -16, -12, z - d / 2, z + d / 2, m, rot=(0, ry, rz), pivot=(x, -14, z))
        B("casco", -6, 6, -16, 30, 90, 96, "metal_esc", rot=(18, 0, 10), pivot=(0, -16, 93))     # viga espetada no chão
        B("casco", -2, 2, 0, 30, 92, 94, "fios", rot=(18, 0, 10), pivot=(0, -16, 93))
        sparks = [(40, 6, -40), (-40, 20, 76), (0, 30, 96)]
        cols = [(18, 62, -16, 8, -62, -18), (-70, -10, -16, 20, 6, 80), (-20, 20, -16, -10, -16, 16), (40, 84, -16, -8, 10, 50)]
    for i, (x, y, z) in enumerate(sparks, 1):
        N.bone(f"faisca_{i}", (x, y, z), "casco")
        B(f"faisca_{i}", x - 0.6, x + 0.6, y - 0.6, y + 0.6, z - 0.6, z + 0.6, "faisca")
        B(f"faisca_{i}", x + 1, x + 1.8, y + 1, y + 1.8, z - 0.4, z + 0.4, "faisca")
    N.g.shift("casco", 0, 16, 0); N.g.bones["casco"]["pivot"] = [0, 0, 0]
    cols = [(a, b, c + 16, d + 16, e, f) for (a, b, c, d, e, f) in cols]
    colisoes(N, cols)
    free_pixel(A)
    return N.g, A

# Naves (GeckoLib) — Parte 3: nave_lanceira (8 pinturas de Tropa) e nave_pirata (2 pinturas)
# Coordenadas bedrock: frente = -z, direita = -x, esquerda = +x, chão em y = 0. 1 bloco = 16 unidades.
import sys, math, random
sys.path.insert(0, "/home/claude/lore3d"); sys.path.insert(0, "/home/claude/especies")
from core import Atlas, Geo, hx, mix, lt, dk, PATTERNS, pat_solid
from species import EMBLEMAS

TEX = 512; SLOT = 64

# ======================================================================== padrões próprios das naves
def pat_hull(A, x0, y0, S, kind, col, line=None, step=32, rust=0.0, grime=0.0, rivets=True, seed=0):
    """chapas de casco: painéis grandes (step px), algumas subdivididas, rebites espaçados, ferrugem e escorridos"""
    r = random.Random(seed * 131 + kind)
    line = line or dk(col, 0.35)
    rustspots = [(r.randrange(S), r.randrange(S), r.uniform(2, 6)) for _ in range(int(rust * 14))]
    streaks = [r.randrange(S) for _ in range(int(grime * 10))]
    n = S // step
    split = {(i, j): r.choice((None, None, "h", "v")) for i in range(n) for j in range(n)}
    tone = {(i, j): r.uniform(-0.05, 0.05) for i in range(n) for j in range(n)}
    for y in range(S):
        for x in range(S):
            i, j = x // step, y // step; lx, ly = x % step, y % step
            if kind == 0: c = mix(lt(col, 0.07), dk(col, 0.14), y / (S - 1))
            elif kind == 1: c = lt(col, 0.10)
            else: c = dk(col, 0.32)
            t = tone[(i, j)]; c = lt(c, t) if t > 0 else dk(c, -t)
            k = r.randint(-3, 3); c = tuple(v + k for v in hx(c))
            sp = split[(i, j)]
            if lx == 0 or ly == 0 or (sp == "h" and ly == step // 2) or (sp == "v" and lx == step // 2): c = mix(c, line, 0.75)
            elif lx == 1 or ly == 1 or (sp == "h" and ly == step // 2 + 1) or (sp == "v" and lx == step // 2 + 1): c = lt(c, 0.08)
            elif rivets and (ly in (3, step - 3) or lx in (3, step - 3)) and ((lx + ly) % 6 == 0): c = lt(c, 0.22)
            for (sx, sy, rr) in rustspots:
                d = math.hypot(x - sx, (y - sy) * 1.4)
                if d < rr: c = mix(c, "#6B3A1E" if d < rr * 0.6 else "#8A5530", 0.55 + 0.3 * (d < rr * 0.5))
            if kind == 0 and x in streaks and y > 4: c = dk(c, 0.08 + 0.12 * (y / S))
            A.put(x0 + x, y0 + y, c)

def pat_stripe(A, x0, y0, S, kind, col, stripe, width=8):
    """faixa de casco: metade de cima cor do casco, faixa colorida no meio (de pé) ou listras (topo)"""
    for y in range(S):
        for x in range(S):
            c = hx(col) if (y // width) % 2 else hx(stripe)
            if kind == 2: c = dk(c, 0.3)
            k = A.r.randint(-3, 3); A.put(x0 + x, y0 + y, tuple(v + k for v in c))

def pat_hazard(A, x0, y0, S, kind, a, b, w=6):
    for y in range(S):
        for x in range(S):
            c = hx(a) if ((x + y) // w) % 2 else hx(b)
            if kind == 2: c = dk(c, 0.35)
            if (x * 7 + y * 13) % 23 == 0: c = dk(c, 0.25)   # desgaste
            k = A.r.randint(-4, 4); A.put(x0 + x, y0 + y, tuple(v + k for v in c))

def pat_glass(A, x0, y0, S, kind, col, alpha=120):
    for y in range(S):
        for x in range(S):
            c = hx(col); a = alpha
            d = (x + y) % S
            if 6 < d < 10 or 16 < d < 18: c = lt(c, 0.55); a = alpha + 50
            if x == 0 or y == 0 or x == S - 1 or y == S - 1: c = dk(c, 0.4); a = 230
            A.put(x0 + x, y0 + y, c, a)

def pat_vent(A, x0, y0, S, kind, col, glowcol=None):
    for y in range(S):
        for x in range(S):
            c = hx(col)
            if y % 4 in (2, 3): c = dk(c, 0.55)
            gl = glowcol is not None and y % 4 == 3 and 4 < x < S - 4
            if gl: c = hx(glowcol)
            if x < 2 or x >= S - 2: c = dk(col, 0.2)
            A.put(x0 + x, y0 + y, c, 255, gl)

def pat_strip(A, x0, y0, S, kind, col, base):
    """friso de luz: lado = linha brilhante no meio; topo/base = metal"""
    for y in range(S):
        for x in range(S):
            if kind == 0:
                d = abs(y - S / 2) / (S / 2)
                if d < 0.55:
                    A.put(x0 + x, y0 + y, mix("#FFFFFF", col, min(1, 0.3 + d * 1.5)), 255, True); continue
            c = _sh(base, kind, y / (S - 1)); A.put(x0 + x, y0 + y, c)

def pat_flame(A, x0, y0, S, kind, col, core="#FFFFFF"):
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - (S - 1) / 2, y - (S - 1) / 2) / (S * 0.55)
            c = mix(core, col, min(1, d * 1.4)); a = int(max(60, 230 - d * 150))
            A.put(x0 + x, y0 + y, c, a, True)

def pat_invisible(A, x0, y0, S, kind):
    for y in range(S):
        for x in range(S): A.p[x0 + x, y0 + y] = (0, 0, 0, 0)

def _sh(c, kind, t):
    if kind == 1: return lt(c, 0.12)
    if kind == 2: return dk(c, 0.35)
    return mix(lt(c, 0.06), dk(c, 0.2), t)

PATTERNS.update(hull=pat_hull, stripe=pat_stripe, hazard=pat_hazard, glass=pat_glass, vent=pat_vent, strip=pat_strip,
                flame=pat_flame, invisible=pat_invisible)

def emblem_decal(A, x0, y0, S, grid, bg, fg, ring=None, scale=5, flip=False):
    if flip: grid = grid[::-1]
    for y in range(S):
        for x in range(S):
            c = hx(bg); k = A.r.randint(-3, 3)
            d = math.hypot(x - (S - 1) / 2, y - (S - 1) / 2)
            if ring and S / 2 - 5 < d < S / 2 - 2: A.put(x0 + x, y0 + y, hx(ring), 255, True); continue
            A.put(x0 + x, y0 + y, tuple(v + k for v in c))
    h, w = len(grid), len(grid[0]); ox, oy = (S - w * scale) // 2, (S - h * scale) // 2
    for gy, row in enumerate(grid):
        for gx, ch in enumerate(row):
            if ch == "#":
                for dy in range(scale):
                    for dx in range(scale):
                        A.put(x0 + ox + gx * scale + dx, y0 + oy + gy * scale + dy, lt(fg, 0.25) if dx == 0 or dy == 0 else fg, 255, True)

def panel_decal(A, x0, y0, S, col, lights):
    """painel com tela/luzes (laterais da cabine, consoles vistos de fora)"""
    for y in range(S):
        for x in range(S):
            c = _sh(col, 0, y / S)
            if x % 16 == 0 or y % 16 == 0: c = dk(c, 0.3)
            A.put(x0 + x, y0 + y, c)
    r = A.r
    for i in range(10):
        lx, ly = r.randrange(4, S - 6), r.randrange(4, S - 6); lc = r.choice(lights)
        for dx in range(3):
            for dy in range(2): A.put(x0 + lx + dx, y0 + ly + dy, lc, 255, True)

def text_decal(A, x0, y0, S, bg, fg, code, mirror=False):
    """número de casco (pixel font 3x5)"""
    F = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111", "4": "101101111001001",
         "5": "111100111001111", "6": "111100111101111", "7": "111001010010010", "8": "111101111101111", "9": "111101111001111",
         "-": "000000111000000", "L": "100100100100111", "P": "111101111100100", "X": "101101010101101", "K": "101110100110101", "C": "111100100100111", "T": "111010010010010", "A": "010101111101101", "R": "110101110101101"}
    for y in range(S):
        for x in range(S): A.put(x0 + x, y0 + y, tuple(v + A.r.randint(-3, 3) for v in hx(bg)))
    sc = max(3, min(S // 21, (S - 4) // (len(code) * 4))); wtot = len(code) * 4 * sc; ox = (S - wtot) // 2; oy = (S - 5 * sc) // 2
    for i, ch in enumerate(code):
        m = F.get(ch, "0" * 15)
        for j, bit in enumerate(m):
            if bit == "1":
                for dy in range(sc):
                    for dx in range(sc):
                        px = ox + i * 4 * sc + (j % 3) * sc + dx
                        A.put(x0 + (S - 1 - px if mirror else px), y0 + oy + (j // 3) * sc + dy, fg)

# ======================================================================== utilidades de geometria
class Nave:
    def __init__(self, name, A):
        self.g = Geo(name, A, density=2.0)
        self.g.uvrng = random.Random(4242)
    def B(self, bone, x0, x1, y0, y1, z0, z1, mat, **kw):
        kw.setdefault("soft", False)
        return self.g.box(bone, (x0, y0, z0), (x1, y1, z1), mat, **kw)
    def M(self, bone, x0, x1, y0, y1, z0, z1, mat, **kw):
        """espelhado (lado direito -x e esquerdo +x)"""
        self.B(bone, x0, x1, y0, y1, z0, z1, mat, **kw)
        self.B(bone, -x1, -x0, y0, y1, z0, z1, mat, **kw)
    def loft(self, bone, secs, mat, **kw):
        """secs: [(z, meia_largura, y_base, y_topo)] — caixas em degraus entre seções"""
        for (za, wa, ba, ta), (zb, wb, bb, tb) in zip(secs, secs[1:]):
            w, b, t = (wa + wb) / 2, (ba + bb) / 2, (ta + tb) / 2
            self.B(bone, -w, w, b, t, min(za, zb), max(za, zb), mat, **kw)
    def bone(self, name, pivot, parent=None):
        return self.g.bone(name, pivot, parent)

def free_pixel(A):
    """um pixel opaco no glowmask num slot que nenhum cubo usa (o GeckoLib não aceita glowmask vazio)"""
    per = A.size // A.slot
    assert A.n < per * per, "sem slot livre"
    x, y = (per * per - 1) % per * A.slot, (per * per - 1) // per * A.slot
    A.g[x + A.slot - 1, y + A.slot - 1] = (255, 255, 255, 255)

# ======================================================================== NAVE LANCEIRA (6 x 3 x 9 blocos)
LANCEIRA_COR = {
    "green":  dict(casco="#1F9A45", casco2="#15191D", luz="#7DFF9E", emb="verde"),
    "yellow": dict(casco="#D4AF00", casco2="#151515", luz="#FFF27A", emb="amarelo"),
    "red":    dict(casco="#A81A14", casco2="#22100E", luz="#FF6A3A", emb="vermelho"),
    "blue":   dict(casco="#D6E2F0", casco2="#1E5FC8", luz="#8CD8FF", emb="azul"),
    "violet": dict(casco="#8E2DB0", casco2="#22082C", luz="#FF9CF2", emb="violeta"),
    "indigo": dict(casco="#3B2398", casco2="#120A30", luz="#CFC4FF", emb="indigo"),
    "orange": dict(casco="#C86A00", casco2="#22120A", luz="#FFC85A", emb="laranja"),
    "black":  dict(casco="#26262E", casco2="#0C0C10", luz="#E8E8FF", emb="preto"),
}

def lanceira(cor):
    C = LANCEIRA_COR[cor]
    A = Atlas(TEX, SLOT, seed=11)
    # ordem fixa dos materiais (UVs iguais em todas as pinturas)
    A.material("casco", "hull", C["casco"], dk(C["casco"], 0.4), step=32, seed=1)
    A.material("casco2", "hull", C["casco2"], dk(C["casco2"], 0.5) if cor != "blue" else lt(C["casco2"], 0.3), step=32, seed=2)
    A.material("metal", "metal", "#5C6168")
    A.material("metal_esc", "metal", "#2E3238", rivets=False)
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
    A.decal("numero", text_decal, C["casco"], C["casco2"] if cor != "blue" else "#1E3A6A", "L-07")
    N = Nave("nave_lanceira", A); B, M = N.B, N.M
    N.bone("root", (0, 0, 0))
    N.bone("casco", (0, 24, 0), "root")
    # --- nariz (z -72..-40), afinando
    N.loft("casco", [(-72, 4, 13, 19), (-64, 8, 11, 22), (-56, 11, 10, 25), (-48, 14, 9, 27), (-40, 16, 8, 28)], "casco")
    B("casco", -2, 2, 15, 18, -76, -72, "metal")                                   # sonda
    B("casco", -15, 15, 8, 9, -56, -40, "casco2")                                  # quilha
    # --- cabine aberta (z -40..6): piso, laterais, painel de instrumentos
    B("casco", -16, 16, 8, 12, -40, 6, "casco2")                                   # piso/quilha
    M("casco", 12, 16, 12, 26, -40, 6, "casco")                                    # paredes laterais
    B("casco", -12, 12, 12, 16, -40, -36, "interior")                              # pé da cabine
    B("casco", -12, 12, 16, 24, -40, -34, "interior", decals={"south": "painel"}) # painel de instrumentos
    M("casco", 16, 17, 18, 20, -40, 6, "luz")                                      # friso de luz
    M("casco", 16, 16.4, 8, 14, -40, 6, "casco2")                                  # faixa escura inferior
    for (z0, z1, w, y) in ((-72, -64, 6, 19), (-64, -56, 9.5, 22), (-56, -48, 12.5, 25), (-48, -40, 15, 27)):
        M("casco", w - 0.6, w, y - 7, y - 4, z0, z1, "casco2")
    B("casco", -1, 1, 27, 27.5, -48, -40, "luz"); B("casco", -1, 1, 24.6, 25.1, -56, -48, "luz")
    M("casco", 16, 22, 10, 22, -30, 6, "casco2")                                   # entradas de ar
    M("casco", 22, 22.5, 12, 20, -28, 4, "ventil")
    # --- canopy (cabine_vidro)
    N.bone("cabine_vidro", (0, 26, 6), "casco")
    B("cabine_vidro", -12, 12, 26, 32, -40, -32, "vidro")
    B("cabine_vidro", -12, 12, 26, 38, -32, -22, "vidro")
    B("cabine_vidro", -12, 12, 26, 42, -22, 4, "vidro")
    M("cabine_vidro", 12, 13, 26, 42, -22, 4, "metal_esc")                        # moldura
    B("cabine_vidro", -12, 12, 42, 43, -12, -10, "metal_esc")
    B("cabine_vidro", -12, 12, 42, 43, 2, 4, "metal_esc")
    # --- assentos (pivô = ponto onde o passageiro senta)
    for nm, z in (("assento_piloto", -26), ("assento_1", -6)):
        N.bone(nm, (0, 14, z), "casco")
        B(nm, -6, 6, 12, 14, z - 6, z + 5, "assento")
        B(nm, -6, 6, 14, 30, z + 5, z + 8, "assento")
        B(nm, -4, 4, 30, 34, z + 6, z + 8, "assento")
        M(nm, 6, 7, 14, 19, z - 5, z + 5, "metal_esc")
    B("casco", -4, 4, 16, 20, -18, -16, "interior")                                # manche
    # --- compartimento da porta (z 6..26): vão livre de 1 x 2 blocos atrás da porta
    B("casco", -18, 18, 8, 10, 6, 26, "casco2")                                    # piso
    B("casco", -18, 18, 42, 46, 6, 26, "casco")                                    # teto
    B("casco", -18, -14, 10, 42, 6, 26, "casco")                                   # parede direita
    B("casco", -14, -12, 10, 42, 6, 26, "interior")
    B("casco", 14, 18, 10, 42, 6, 7, "casco"); B("casco", 14, 18, 10, 42, 25, 26, "casco")   # batentes
    B("casco", -12, 14, 10, 42, 25, 26, "interior")                                # parede do fundo
    B("casco", -18, 18, 38, 42, 6, 8, "casco")                                     # verga (atrás da canopy)
    B("casco", -12, 12, 26, 38, 4, 6, "casco2")
    M("casco", 18, 18.5, 30, 34, 6, 26, "luz")
    # porta (asa de gaivota, dobradiça no alto)
    N.bone("porta", (17, 42, 16), "casco")
    B("porta", 16, 18, 10, 42, 7, 25, "casco")
    B("porta", 18, 18.3, 13, 27, 9, 23, "casco", decals={"west": "numero"})
    B("porta", 18, 18.5, 32, 34, 9, 23, "luz")
    B("porta", 15, 16, 12, 40, 9, 23, "interior")
    # --- traseira (z 26..72)
    N.loft("casco", [(26, 18, 8, 40), (40, 17, 9, 34), (56, 14, 11, 30), (66, 10, 13, 26)], "casco")
    B("casco", -10, 10, 30, 36, 26, 50, "casco2")                                  # espinha dorsal
    B("casco", -6, 6, 36, 37, 28, 46, "ventil")
    # deriva (cauda vertical com emblema)
    B("casco", -1.5, 1.5, 34, 40, 44, 66, "casco2")
    B("casco", -1.5, 1.5, 40, 46, 52, 68, "casco2", decals={"west": "emblema", "east": "emblema"})
    B("casco", -1.5, 1.5, 46, 48, 60, 70, "luz")
    # --- asas
    for s in (1, -1):
        def X(a, b): return (min(s * a, s * b), max(s * a, s * b))
        x0, x1 = X(18, 28); B("casco", x0, x1, 14, 19, 10, 56, "casco")
        x0, x1 = X(28, 38); B("casco", x0, x1, 14, 18, 20, 56, "casco")
        x0, x1 = X(38, 46); B("casco", x0, x1, 14.5, 17.5, 30, 54, "casco")
        x0, x1 = X(24, 40); B("casco", x0, x1, 18, 18.4, 30, 46, "casco2", decals={"up": "emblema_topo"})
        x0, x1 = X(18, 38); B("casco", x0, x1, 18, 18.3, 50, 56, "casco2")                 # faixa escura no bordo de fuga
        x0, x1 = X(18, 38); B("casco", x0, x1, 15, 17, 8, 10, "luz")                # bordo de ataque iluminado
        x0, x1 = X(38, 46); B("casco", x0, x1, 15, 17, 28, 30, "luz")
        x0, x1 = X(46, 47); B("casco", x0, x1, 12, 20, 34, 52, "casco2")             # ponta da asa
    # --- canhões fixos nas pontas das asas
    for i, s in ((1, -1), (2, 1)):
        cx = 44 * s
        N.bone(f"canhao_{i}", (cx, 13, 30), "casco")
        B(f"canhao_{i}", cx - 3, cx + 3, 10, 14, 18, 40, "metal_esc")
        B(f"canhao_{i}", cx - 1.5, cx + 1.5, 10.5, 13.5, -14, 18, "metal")
        B(f"canhao_{i}", cx - 2, cx + 2, 10, 14, -6, -2, "metal_esc")
        M_ = (f"boca_{i}", (cx, 12, -16))
        N.bone(M_[0], M_[1], f"canhao_{i}")
        B(M_[0], cx - 2, cx + 2, 10, 14, -18, -14, "metal_esc")
        B(M_[0], cx - 1, cx + 1, 11, 13, -18.5, -17.9, "motor")
    # --- propulsores
    for i, s in ((1, -1), (2, 1)):
        cx = 14 * s
        N.bone(f"propulsor_{i}", (cx, 21, 66), "casco")
        B(f"propulsor_{i}", cx - 8, cx + 8, 14, 28, 34, 66, "casco2")
        B(f"propulsor_{i}", cx - 6, cx + 6, 12, 30, 34, 66, "casco2")
        B(f"propulsor_{i}", cx - 7, cx + 7, 13, 29, 66, 70, "metal")
        B(f"propulsor_{i}", cx - 5, cx + 5, 15, 27, 69.6, 70.2, "motor")
        N.bone(f"chama_{i}", (cx, 21, 70), f"propulsor_{i}")
        B(f"chama_{i}", cx - 4, cx + 4, 16, 26, 70, 78, "chama")
        B(f"propulsor_{i}", cx - 8.5, cx + 8.5, 20, 22, 40, 60, "luz")
        B(f"propulsor_{i}", cx - 6, cx + 6, 28, 32, 30, 40, "casco")            # entrada de ar
        B(f"propulsor_{i}", cx - 5, cx + 5, 29, 31, 29.6, 30, "ventil")
    # --- trem de pouso (encolhe para dentro do casco no voo)
    N.bone("trem_pouso", (0, 8, 0), "casco")
    for (x, z) in ((0, -50), (-22, 40), (22, 40)):
        B("trem_pouso", x - 1, x + 1, 2, 8, z - 1, z + 1, "metal")
        B("trem_pouso", x - 2, x + 2, 5, 7, z - 2, z + 2, "metal_esc")
        B("trem_pouso", x - 3, x + 3, 0, 2, z - 4, z + 4, "metal_esc")
    # --- caixas de colisão (simples, sem rotação, invisíveis)
    cols = [(-16, 16, 8, 28, -72, -40), (-16, 16, 8, 43, -40, 6), (-18, 18, 8, 46, 6, 26), (-18, 18, 8, 40, 26, 72),
            (-47, -18, 10, 20, 8, 56), (18, 47, 10, 20, 8, 56), (-2, 2, 34, 48, 44, 70), (-3, 3, 0, 8, -54, 44)]
    for i, (a, b, c, d, e, f) in enumerate(cols, 1):
        N.bone(f"col_{i}", (0, 0, 0), "root")
        B(f"col_{i}", a, b, c, d, e, f, "colisao")
    free_pixel(A)
    return N.g, A

# ======================================================================== NAVE PIRATA (10 x 4 x 16 blocos)
PIRATA_PINTURA = {
    1: dict(casco="#6E2A1E", casco2="#1C1A1A", faixa="#D9A400", luz="#FF4A2A", vidro="#FFB080", nome="ferrugem"),
    2: dict(casco="#4C5638", casco2="#22241E", faixa="#E06A10", luz="#FFB030", vidro="#C8FF90", nome="sucata"),
}
EMB_PIRATA = ["#.......#", ".#.###.#.", "..#...#..", ".#.#.#.#.", ".#..#..#.", ".#.#.#.#.", "..#...#..", ".#.###.#.", "#.......#"]

def pirata(var):
    P = PIRATA_PINTURA[var]
    A = Atlas(TEX, SLOT, seed=21)
    A.material("casco", "hull", P["casco"], dk(P["casco"], 0.45), step=32, rust=0.8, grime=0.9, seed=3)
    A.material("casco2", "hull", P["casco2"], dk(P["casco2"], 0.5), step=16, rust=0.4, grime=0.5, seed=4)
    A.material("remendo", "hull", mix(P["casco"], "#7A7A70", 0.5), "#3A3632", step=16, rust=1.0, seed=5)
    A.material("metal", "metal", "#5A5650")
    A.material("metal_esc", "metal", "#2A2826", rivets=False)
    A.material("faixa", "hazard", P["faixa"], "#1A1816")
    A.material("vidro", "glass", P["vidro"], alpha=120, translucent=True)
    A.material("luz", "strip", P["luz"], P["casco2"])
    A.material("motor", "glow", P["luz"], core="#FFF0C0")
    A.material("chama", "flame", P["luz"], core="#FFF0C0", translucent=True)
    A.material("interior", "solid", "#262420", noise=5)
    A.material("assento", "fabric", "#3A2A20", trim="#5A4030")
    A.material("ventil", "vent", "#3A3632", glowcol=P["luz"])
    A.material("dentes", "metal", "#8A8478")
    A.material("colisao", "invisible")
    A.decal("emblema", emblem_decal, EMB_PIRATA, P["casco2"], P["luz"], ring=None, scale=5)
    A.decal("painel", panel_decal, "#22201C", [P["luz"], "#FFD050", "#50FF80"])
    A.decal("numero", text_decal, P["casco"], "#E8E0D0", "PX-13")
    N = Nave("nave_pirata", A); B, M = N.B, N.M
    N.bone("root", (0, 0, 0))
    N.bone("casco", (0, 32, 0), "root")
    # --- casco principal (x -36..36, z -60..92)
    B("casco", -20, 36, 10, 46, -60, -28, "casco"); B("casco", -20, 36, 10, 46, -28, 4, "casco2")
    B("casco", -36, -20, 10, 46, -60, 6, "casco")                                  # lado direito, à frente da porta
    B("casco", -36, -20, 10, 46, 42, 92, "casco")                                  # lado direito, atrás da porta
    B("casco", -20, 36, 10, 46, 4, 42, "casco")
    B("casco", -20, 36, 10, 46, 42, 92, "casco2")
    B("casco", -36, -20, 46, 50, 6, 42, "casco")                                   # verga da porta
    B("casco", -36, -20, 8, 10, 6, 42, "metal_esc")                                # soleira
    B("casco", -20, -19, 10, 46, 6, 42, "interior")                                # fundo do vão
    B("casco", -30, 30, 46, 50, -50, 84, "casco2")                                 # teto
    B("casco", -24, 24, 6, 10, -40, 80, "casco2")                                  # barriga
    M("casco", 36, 36.5, 30, 33, -56, 88, "luz")
    B("casco", -37, -36, 18, 22, -58, 4, "faixa"); B("casco", -37, -36, 18, 22, 44, 90, "faixa")
    # remendos e placas soltas (assimetria de nave pirata)
    for (x0, x1, y0, y1, z0, z1) in ((-37, -36, 26, 40, -48, -30), (-37.5, -36, 30, 44, 56, 76), (36, 37.5, 14, 28, -40, -18),
                                      (36, 37, 34, 44, 20, 40), (-10, 14, 50, 51, 10, 34), (-30, -8, 50, 51, 60, 80)):
        B("casco", x0, x1, y0, y1, z0, z1, "remendo")
    B("casco", 36, 37, 16, 40, 48, 80, "ventil")
    B("casco", -1, 1, 50, 51, -40, 80, "metal_esc")
    # módulo de carga (lado esquerdo)
    B("casco", 36, 60, 10, 38, -20, 28, "casco2"); B("casco", 36, 60, 10, 38, 28, 70, "casco")
    B("casco", 60, 61, 12, 36, -16, 24, "faixa"); B("casco", 60, 61, 14, 34, 32, 66, "remendo")
    B("casco", 40, 56, 38, 40, -14, 64, "metal_esc")
    B("casco", 60, 61, 22, 26, 28, 70, "luz")
    # radiadores do lado direito
    for k in range(5):
        z = -40 + k * 10
        B("casco", -48, -36, 14, 40, z, z + 3, "metal")
    B("casco", -44, -36, 12, 14, -42, 2, "metal_esc")
    # emblema e número
    B("casco", -24, 8, 50, 50.5, -30, 2, "casco2", decals={"up": "emblema"})
    B("casco", 61, 61.4, 12, 36, -16, 24, "faixa", decals={"west": "numero"})
    # --- garras (pontas de abalroamento)
    for s in (1, -1):
        def X(a, b): return (min(s * a, s * b), max(s * a, s * b))
        x0, x1 = X(22, 40); B("casco", x0, x1, 12, 36, -88, -60, "casco")
        x0, x1 = X(24, 38); B("casco", x0, x1, 14, 32, -108, -88, "casco")
        x0, x1 = X(27, 35); B("casco", x0, x1, 17, 28, -122, -108, "casco2")
        x0, x1 = X(29, 33); B("casco", x0, x1, 19, 25, -128, -122, "dentes")
        for k in range(4):
            z = -114 + k * 12
            x0, x1 = X(20, 24 if k % 2 else 22); B("casco", x0, x1, 18, 24, z, z + 5, "dentes")
        x0, x1 = X(40, 41); B("casco", x0, x1, 20, 24, -86, -62, "luz")
    # --- ponte de comando (cabine elevada à frente), oca para mostrar a tripulação
    B("casco", -26, 26, 22, 28, -92, -56, "casco2")                                # piso
    M("casco", 22, 26, 28, 38, -92, -56, "casco")                                  # paredes laterais baixas
    B("casco", -22, 22, 28, 36, -92, -88, "interior", decals={"south": "painel"})   # console frontal
    B("casco", -26, 26, 22, 60, -56, -52, "casco")                                 # anteparo traseiro
    B("casco", -26, 26, 56, 60, -92, -52, "casco")                                 # teto
    M("casco", 22, 26, 38, 56, -60, -52, "casco")
    B("casco", -24, 24, 60, 62, -82, -60, "metal_esc")
    B("casco", -4, 4, 62, 64, -72, -64, "metal")                                   # antena
    M("casco", 24, 24.5, 30, 33, -92, -56, "luz")
    N.bone("cabine_vidro", (0, 47, -74), "casco")
    B("cabine_vidro", -22, 22, 36, 56, -94, -92, "vidro")
    M("cabine_vidro", 22, 24, 38, 56, -92, -60, "vidro")
    B("cabine_vidro", -1, 1, 36, 56, -95, -92, "metal_esc")
    B("cabine_vidro", -22, 22, 46, 47, -95, -92, "metal_esc")
    seats = [("assento_piloto", 0, -78), ("assento_1", -12, -66), ("assento_2", 12, -66), ("assento_3", 0, -62)]
    for nm, x, z in seats:
        N.bone(nm, (x, 32, z), "casco")
        B(nm, x - 5, x + 5, 28, 32, z - 5, z + 4, "assento")
        B(nm, x - 5, x + 5, 32, 46, z + 4, z + 7, "assento")
    # --- canhões: 2 torres no teto (giram no eixo vertical) + 1 fixo sob a ponte
    for i, z in ((1, -24), (2, 62)):
        N.bone(f"canhao_{i}", (0, 50, z), "casco")
        B(f"canhao_{i}", -9, 9, 50, 53, z - 9, z + 9, "metal_esc")
        B(f"canhao_{i}", -7, 7, 53, 59, z - 7, z + 7, "casco2")
        B(f"canhao_{i}", -5, 5, 59, 61, z - 5, z + 5, "casco2")
        B(f"canhao_{i}", -8, 8, 54, 56, z - 8, z + 8, "faixa")
        M(f"canhao_{i}", 2, 5, 55, 58, z - 22, z - 6, "metal")
        B(f"canhao_{i}", -2, 2, 59, 61, z + 4, z + 8, "vidro")
        N.bone(f"boca_{i}", (0, 56.5, z - 22), f"canhao_{i}")
        M(f"boca_{i}", 1.5, 5.5, 54.5, 58.5, z - 26, z - 22, "metal_esc")
        M(f"boca_{i}", 2.5, 4.5, 55.5, 57.5, z - 26.4, z - 25.9, "motor")
    N.bone("canhao_3", (0, 18, -60), "casco")
    B("canhao_3", -8, 8, 12, 22, -70, -56, "metal_esc")
    B("canhao_3", -4, 4, 14, 20, -110, -70, "metal")
    B("canhao_3", -6, 6, 13, 21, -96, -92, "faixa")
    N.bone("boca_3", (0, 17, -110), "canhao_3")
    B("boca_3", -5, 5, 12, 22, -116, -110, "metal_esc")
    B("boca_3", -3, 3, 14, 20, -116.5, -115.9, "motor")
    # --- propulsores (desiguais)
    N.bone("propulsor_1", (0, 30, 92), "casco")
    B("propulsor_1", -18, 18, 14, 46, 92, 118, "casco2")
    B("propulsor_1", -20, 20, 12, 48, 100, 106, "faixa")
    B("propulsor_1", -16, 16, 16, 44, 118, 124, "metal")
    B("propulsor_1", -13, 13, 19, 41, 123.6, 124.2, "motor")
    N.bone("chama_1", (0, 30, 124), "propulsor_1")
    B("chama_1", -11, 11, 21, 39, 124, 128, "chama")
    for i, cx, w, y0, y1, zz in ((2, -44, 7, 18, 32, 70), (3, 48, 6, 40, 52, 72)):
        N.bone(f"propulsor_{i}", (cx, (y0 + y1) / 2, zz + 40), "casco")
        B(f"propulsor_{i}", cx - w, cx + w, y0, y1, zz, zz + 36, "casco2" if i == 2 else "remendo")
        B(f"propulsor_{i}", cx - w - 1, cx + w + 1, y0 - 1, y1 + 1, zz + 36, zz + 40, "metal")
        B(f"propulsor_{i}", cx - w + 2, cx + w - 2, y0 + 2, y1 - 2, zz + 39.6, zz + 40.2, "motor")
        N.bone(f"chama_{i}", (cx, (y0 + y1) / 2, zz + 40), f"propulsor_{i}")
        B(f"chama_{i}", cx - w + 3, cx + w - 3, y0 + 3, y1 - 3, zz + 40, zz + 46, "chama")
    B("casco", 38, 58, 38, 40, 70, 106, "metal_esc")                               # suporte do motor de cima
    B("casco", -48, -36, 22, 28, 60, 72, "metal_esc")
    # --- rampa lateral (porta), dobradiça embaixo, abre para a direita
    N.bone("porta", (-37, 10, 24), "casco")
    B("porta", -38, -36, 10, 48, 8, 40, "casco", decals={"east": "numero"})
    for k in range(6):
        B("porta", -36, -35, 13 + k * 6, 14 + k * 6, 10, 38, "metal_esc")       # degraus (lado de dentro)
    B("porta", -38.5, -38, 12, 46, 8, 10, "faixa"); B("porta", -38.5, -38, 12, 46, 38, 40, "faixa")
    # --- trem de pouso
    N.bone("trem_pouso", (0, 10, 0), "casco")
    for (x, z) in ((-26, -40), (26, -40), (-26, 70), (26, 70), (0, -100)):
        B("trem_pouso", x - 2, x + 2, 2, 10, z - 2, z + 2, "metal")
        B("trem_pouso", x - 3, x + 3, 6, 8, z - 3, z + 3, "faixa")
        B("trem_pouso", x - 4, x + 4, 0, 2, z - 6, z + 6, "metal_esc")
    B("trem_pouso", -2, 2, 10, 14, -102, -98, "metal")
    # --- colisão
    cols = [(-36, 36, 8, 50, -60, 92), (-26, 26, 22, 64, -94, -52), (36, 61, 10, 40, -20, 70), (-20, 20, 12, 48, 92, 128),
            (-40, -22, 12, 36, -128, -60), (22, 40, 12, 36, -128, -60), (-48, -36, 14, 40, -42, 2), (-10, 10, 50, 61, -46, 72)]
    for i, (a, b, c, d, e, f) in enumerate(cols, 1):
        N.bone(f"col_{i}", (0, 0, 0), "root")
        B(f"col_{i}", a, b, c, d, e, f, "colisao")
    free_pixel(A)
    return N.g, A

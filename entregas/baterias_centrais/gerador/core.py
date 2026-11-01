# Núcleo de modelagem para os modelos GeckoLib da lore (entidades e bosses).
# Gera .geo.json (bedrock 1.12), textura HD com padrões por material, glowmask e animações.
import json, math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

def hx(c):
    if isinstance(c, (tuple, list)): return tuple(c[:3])
    c = c.lstrip("#"); return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))
def mix(a, b, t): a, b = hx(a), hx(b); return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))
def lt(c, k): return mix(c, (255, 255, 255), k)
def dk(c, k): return mix(c, (0, 0, 0), k)
def clamp(v): return max(0, min(255, int(v)))

# ======================================================================== TEXTURA
class Atlas:
    """textura em 'slots' quadrados; cada material ocupa 3 slots (lado, topo, base); decalques ocupam 1 slot"""
    def __init__(self, size=256, slot=32, seed=1):
        self.size, self.slot = size, slot
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0)); self.p = self.img.load()
        self.glow = Image.new("RGBA", (size, size), (0, 0, 0, 0)); self.g = self.glow.load()
        self.n = 0; self.mats = {}; self.decals = {}; self.r = random.Random(seed)
        self.translucent = set()
    def _slot(self):
        per = self.size // self.slot
        i = self.n; self.n += 1
        if i >= per * per: raise RuntimeError("atlas cheio")
        return (i % per) * self.slot, (i // per) * self.slot
    def put(self, x, y, c, a=255, glow=False):
        c = tuple(clamp(v) for v in hx(c)) if isinstance(c, str) else tuple(clamp(v) for v in c[:3])
        self.p[x, y] = c + (a,)
        if glow: self.g[x, y] = c + (a,)
    def material(self, name, pattern, *args, translucent=False, **kw):
        slots = [self._slot() for _ in range(3)]
        for kind, (x0, y0) in enumerate(slots):
            PATTERNS[pattern](self, x0, y0, self.slot, kind, *args, **kw)
        self.mats[name] = slots
        if translucent: self.translucent.add(name)
        return name
    def decal(self, name, fn, *args, **kw):
        x0, y0 = self._slot(); fn(self, x0, y0, self.slot, *args, **kw); self.decals[name] = (x0, y0); return name

# ---------------------------------------------------------------- padrões (kind 0 = lado, 1 = topo, 2 = base)
def _shade(c, kind, t, edge):
    if kind == 1: c = lt(c, 0.14)
    elif kind == 2: c = dk(c, 0.35)
    else: c = mix(lt(c, 0.10), dk(c, 0.22), t)
    if edge == 1: c = lt(c, 0.18)
    elif edge == -1: c = dk(c, 0.25)
    return c

def pat_solid(A, x0, y0, S, kind, col, noise=6, edges=True, glow=False, alpha=255):
    for y in range(S):
        for x in range(S):
            edge = 0
            if edges and (y == 0 or x == 0): edge = 1
            if edges and (y == S - 1 or x == S - 1): edge = -1
            c = _shade(col, kind, y / (S - 1), edge)
            k = A.r.randint(-noise, noise); c = tuple(v + k for v in hx(c))
            A.put(x0 + x, y0 + y, c, alpha, glow)

def pat_glow(A, x0, y0, S, kind, col, core="#FFFFFF", alpha=255):
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - (S - 1) / 2, y - (S - 1) / 2) / (S * 0.7)
            c = mix(core, col, min(1, 0.25 + d * 1.3))
            A.put(x0 + x, y0 + y, c, alpha, True)

def pat_cracks(A, x0, y0, S, kind, base, crack, density=3, width=1, glow=True, noise=7):
    """rocha/pele escura com rachaduras luminosas"""
    pat_solid(A, x0, y0, S, kind, base, noise)
    r = A.r
    for _ in range(density):
        x, y = r.randrange(S), 0 if r.random() < 0.6 else r.randrange(S)
        for _ in range(S + r.randrange(S)):
            for w in range(width):
                xx = min(S - 1, max(0, x + w))
                core = r.random() < 0.12
                A.put(x0 + xx, y0 + y, lt(crack, 0.35) if core else crack, 255, glow)
            if r.random() < 0.15 and x + 1 < S: A.put(x0 + x + 1, y0 + y, dk(crack, 0.25), 255, glow)
            x = max(0, min(S - 1, x + r.choice((-1, 0, 0, 1)))); y += 1
            if y >= S: break

def pat_magma(A, x0, y0, S, kind, crust, lava):
    """crosta de magma em placas com lava entre elas"""
    r = A.r; cells = [(r.randrange(S), r.randrange(S)) for _ in range(max(5, S // 4))]
    for y in range(S):
        for x in range(S):
            ds = sorted(math.hypot(x - cx, y - cy) for cx, cy in cells)
            gap = ds[1] - ds[0]
            if gap < 0.8:
                c = mix(lt(lava, 0.3), lava, gap / 0.8); A.put(x0 + x, y0 + y, c, 255, True)
            elif gap < 1.6:
                A.put(x0 + x, y0 + y, mix(dk(lava, 0.45), crust, (gap - 0.8) / 0.8))
            else:
                c = _shade(crust, kind, y / (S - 1), 0); k = r.randint(-8, 8)
                c = mix(c, (60, 60, 60), 0.05)
                A.put(x0 + x, y0 + y, tuple(v + k for v in hx(c)))

def pat_plates(A, x0, y0, S, kind, col, line, step=8, rivet=None, noise=5):
    """placas (carapaça, armadura, pedra polida em blocos)"""
    pat_solid(A, x0, y0, S, kind, col, noise, edges=False)
    for y in range(S):
        for x in range(S):
            if y % step == 0 or (x + (step // 2 if (y // step) % 2 else 0)) % step == 0:
                A.put(x0 + x, y0 + y, line)
            elif y % step == 1: A.put(x0 + x, y0 + y, lt(col, 0.18))
    if rivet:
        for y in range(step // 2, S, step):
            for x in range(step // 2, S, step): A.put(x0 + x, y0 + y, rivet)

def pat_carapace(A, x0, y0, S, kind, col, mark, stripe=6):
    """quitina brilhante com faixas de marcação"""
    for y in range(S):
        for x in range(S):
            t = y / (S - 1)
            c = mix(lt(col, 0.10), dk(col, 0.3), t)
            spec = math.exp(-((x - S * 0.3) ** 2) / (S * 1.2)) * 0.22
            c = lt(c, spec)
            if kind == 0 and y % (stripe * 2) == 0 and S * 0.15 < x < S * 0.85: c = mark
            if kind == 0 and y % (stripe * 2) == 1 and S * 0.3 < x < S * 0.7: c = dk(mark, 0.35)
            if kind == 1 and x in (S // 2 - 1, S // 2) and y % 6 < 3: c = mark
            if kind == 2: c = dk(c, 0.3)
            k = A.r.randint(-3, 3); A.put(x0 + x, y0 + y, tuple(v + k for v in hx(c)))

def pat_stone(A, x0, y0, S, kind, col, vein, vein_glow=False, n_veins=2):
    """pedra polida com veios (Veltari)"""
    pat_solid(A, x0, y0, S, kind, col, 4, edges=True)
    r = A.r
    for _ in range(n_veins):
        y = r.randrange(S); x = 0
        while x < S:
            A.put(x0 + x, y0 + y, vein, 255, vein_glow)
            x += 1
            if r.random() < 0.3: y = max(0, min(S - 1, y + r.choice((-1, 1))))

def pat_skin(A, x0, y0, S, kind, col, spots=None, spot_glow=False, alpha=255, n_spots=6):
    for y in range(S):
        for x in range(S):
            c = _shade(col, kind, y / (S - 1), 0)
            k = A.r.randint(-3, 3); A.put(x0 + x, y0 + y, tuple(v + k for v in hx(c)), alpha)
    if spots:
        for _ in range(n_spots):
            cx, cy, rr = A.r.randrange(S), A.r.randrange(S), A.r.choice((1, 1, 2))
            for y in range(cy - rr, cy + rr + 1):
                for x in range(cx - rr, cx + rr + 1):
                    if 0 <= x < S and 0 <= y < S and math.hypot(x - cx, y - cy) <= rr:
                        A.put(x0 + x, y0 + y, spots, 255 if spot_glow else alpha, spot_glow)

def pat_crystal(A, x0, y0, S, kind, col, alpha=190, glowing=False):
    """facetas de cristal (translúcido)"""
    r = A.r; lines = [(r.uniform(-1, 1), r.randrange(S)) for _ in range(3)]
    for y in range(S):
        for x in range(S):
            facet = sum(1 for m, b in lines if y > m * x + b) % 3
            c = [lt(col, 0.35), col, dk(col, 0.18)][facet]
            if kind == 1: c = lt(c, 0.2)
            if any(abs(y - (m * x + b)) < 0.7 for m, b in lines): c = lt(col, 0.6)
            if x == 0 or y == 0: c = lt(col, 0.5)
            A.put(x0 + x, y0 + y, c, alpha, glowing)

def pat_bone(A, x0, y0, S, kind, col):
    pat_solid(A, x0, y0, S, kind, col, 5, edges=True)
    r = A.r
    for _ in range(S // 3):
        A.put(x0 + r.randrange(S), y0 + r.randrange(S), dk(col, 0.3))
    for _ in range(2):
        x, y = r.randrange(S), r.randrange(S)
        for _ in range(S // 3):
            A.put(x0 + x, y0 + y, dk(col, 0.45)); x = max(0, min(S - 1, x + r.choice((-1, 0, 1)))); y = min(S - 1, y + 1)

def pat_fabric(A, x0, y0, S, kind, col, trim=None, folds=True):
    for y in range(S):
        for x in range(S):
            c = _shade(col, kind, y / (S - 1), 0)
            if (x + y) % 2 == 0: c = lt(c, 0.04)
            if folds and kind == 0 and x % 7 == 0: c = dk(c, 0.18)
            if folds and kind == 0 and x % 7 == 1: c = lt(c, 0.08)
            if trim and kind == 0 and y >= S - 3: c = trim if y != S - 3 else lt(trim, 0.25)
            A.put(x0 + x, y0 + y, c)

def pat_metal(A, x0, y0, S, kind, col, rivets=True):
    for y in range(S):
        for x in range(S):
            c = _shade(col, kind, y / (S - 1), 1 if y == 0 else (-1 if y == S - 1 else 0))
            if x % 3 == 0: c = lt(c, 0.05)
            k = A.r.randint(-4, 4); A.put(x0 + x, y0 + y, tuple(v + k for v in hx(c)))
    if rivets:
        for x in (3, S - 4):
            for y in (3, S - 4): A.put(x0 + x, y0 + y, lt(col, 0.5)); A.put(x0 + x + 1, y0 + y + 1, dk(col, 0.4))

def pat_coins(A, x0, y0, S, kind, base, gold):
    pat_solid(A, x0, y0, S, kind, base, 6, edges=False)
    r = A.r
    for _ in range(S // 2):
        cx, cy = r.randrange(S), r.randrange(S)
        for y in range(cy - 2, cy + 3):
            for x in range(cx - 2, cx + 3):
                if 0 <= x < S and 0 <= y < S and math.hypot(x - cx, y - cy) <= 2.2:
                    c = lt(gold, 0.35) if (x - cx + y - cy) < 0 else gold
                    if abs(math.hypot(x - cx, y - cy) - 2) < 0.6: c = dk(gold, 0.3)
                    A.put(x0 + x, y0 + y, c)

def pat_sand(A, x0, y0, S, kind, col, sparkle="#FFE58A"):
    for y in range(S):
        for x in range(S):
            c = _shade(col, kind, y / (S - 1), 0); k = A.r.randint(-14, 14); c = tuple(v + k for v in hx(c))
            sp = A.r.random() < 0.03
            A.put(x0 + x, y0 + y, hx(sparkle) if sp else c, 255, sp)

def pat_gray(A, x0, y0, S, kind, col, dissolve=0.0, alpha=255):
    """cinza liso (Nulo) ou cinza que se desfaz nas bordas (Ecos)"""
    for y in range(S):
        for x in range(S):
            c = hx(col) if kind == 0 else (lt(col, 0.05) if kind == 1 else dk(col, 0.1))
            a = alpha
            if dissolve:
                e = min(x, y, S - 1 - x, S - 1 - y) / (S / 2)
                if A.r.random() > e + (1 - dissolve): a = 0
            A.put(x0 + x, y0 + y, c, a)

def pat_obsidian(A, x0, y0, S, kind, col, sheen):
    for y in range(S):
        for x in range(S):
            c = _shade(col, kind, y / (S - 1), 0)
            s = math.sin((x * 0.7 + y * 0.35)) * 0.5 + 0.5
            if s > 0.85: c = mix(c, sheen, 0.55)
            k = A.r.randint(-3, 3); A.put(x0 + x, y0 + y, tuple(v + k for v in hx(c)))

def pat_leaf(A, x0, y0, S, kind, col, vein):
    for y in range(S):
        for x in range(S):
            c = _shade(col, kind, y / (S - 1), 0)
            if x == S // 2 or abs(x - S // 2) == (y % 6): c = vein
            A.put(x0 + x, y0 + y, c, 255, True)

def pat_tattoo(A, x0, y0, S, kind, skin, ink):
    pat_skin(A, x0, y0, S, kind, skin)
    if kind != 0: return
    for y in range(S):
        for x in range(S):
            band = (y // 10) % 3
            if band == 0 and (x + y) % 12 == 0 and y % 10 < 6: A.put(x0 + x, y0 + y, ink, 255, True)
            if band == 0 and (x - y) % 12 == 0 and y % 10 < 6: A.put(x0 + x, y0 + y, ink, 255, True)
            if y % 20 == 8 and x % 4 != 3 and S * 0.2 < x < S * 0.8: A.put(x0 + x, y0 + y, ink, 255, True)

def pat_feathers(A, x0, y0, S, kind, col, edge, alpha=210):
    """barbatanas em forma de pena (Aelínos)"""
    for y in range(S):
        for x in range(S):
            row = y // 4; off = (row % 2) * 2
            t = ((y % 4) / 3)
            c = mix(lt(col, 0.25), col, t)
            if (x + off) % 4 == 0: c = edge
            if y % 4 == 3: c = dk(col, 0.15)
            A.put(x0 + x, y0 + y, c, alpha, (x + off) % 4 == 0)

PATTERNS = dict(solid=pat_solid, glow=pat_glow, cracks=pat_cracks, magma=pat_magma, plates=pat_plates,
                carapace=pat_carapace, stone=pat_stone, skin=pat_skin, crystal=pat_crystal, bone=pat_bone,
                fabric=pat_fabric, metal=pat_metal, coins=pat_coins, sand=pat_sand, gray=pat_gray,
                obsidian=pat_obsidian, leaf=pat_leaf, tattoo=pat_tattoo, feathers=pat_feathers)

# ---------------------------------------------------------------- decalques (rostos, olhos etc.) desenhados num slot
def draw_grid(A, x0, y0, S, rows, cmap, glow_keys="", bg=None):
    """desenha um pixel-art (lista de strings) centralizado no slot; cada caractere = cor do cmap"""
    h, w = len(rows), len(rows[0])
    sx, sy = (S - w) // 2, (S - h) // 2
    if bg is not None:
        for y in range(S):
            for x in range(S): A.put(x0 + x, y0 + y, hx(bg) if isinstance(bg, str) else bg)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in cmap and cmap[ch] is not None:
                A.put(x0 + sx + x, y0 + sy + y, hx(cmap[ch]), 255, ch in glow_keys)
    return sx, sy, w, h

# ======================================================================== GEOMETRIA
class Geo:
    def __init__(self, name, atlas, density=2.0):
        self.name, self.A, self.D = name, atlas, density
        self.bones = {}; self.order = []; self.roles = {}
        self.soft = {}          # material -> raio de arredondamento das arestas (formas orgânicas)
    def bone(self, name, pivot, parent=None, role=None):
        if name not in self.bones:
            b = {"name": name, "pivot": [round(v, 4) for v in pivot], "cubes": []}
            if parent: b["parent"] = parent
            self.bones[name] = b; self.order.append(name)
        if role: self.roles[name] = role
        return name
    def _uv(self, mat, kind, w, h, decal=None):
        S = self.A.slot
        if decal:
            x0, y0 = self.A.decals[decal]
            return {"uv": [x0, y0], "uv_size": [S, S]}
        x0, y0 = self.A.mats[mat][kind]
        uw, uh = min(S, max(1, w * self.D)), min(S, max(1, h * self.D))
        rng = getattr(self, "uvrng", None) or self.A.r
        ox = rng.uniform(0, S - uw) if uw < S else 0
        oy = rng.uniform(0, S - uh) if uh < S else 0
        return {"uv": [round(x0 + ox, 3), round(y0 + oy, 3)], "uv_size": [round(uw, 3), round(uh, 3)]}
    def box(self, bone, a, b, mat, rot=None, pivot=None, decals=None, inflate=0.0, mirror_decal=False, soft=True):
        x0, y0, z0 = [min(a[i], b[i]) for i in range(3)]; x1, y1, z1 = [max(a[i], b[i]) for i in range(3)]
        sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
        decals = decals or {}
        r = self.soft.get(mat, 0) if soft else 0
        if r and min(sx, sy, sz) >= 2 * r + 1.0:
            # arestas arredondadas: três caixas cruzadas (chanfro nas 12 arestas)
            pv = pivot if pivot else [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2]
            kw = dict(rot=rot, pivot=pv, inflate=inflate, soft=False)
            d = decals
            self.box(bone, (x0 + r, y0, z0 + r), (x1 - r, y1, z1 - r), mat, decals={k: v for k, v in d.items() if k in ("up", "down")}, **kw)
            self.box(bone, (x0, y0 + r, z0 + r), (x1, y1 - r, z1 - r), mat, decals={k: v for k, v in d.items() if k in ("east", "west")}, **kw)
            return self.box(bone, (x0 + r, y0 + r, z0), (x1 - r, y1 - r, z1), mat, decals={k: v for k, v in d.items() if k in ("north", "south")}, **kw)
        uv = {"north": self._uv(mat, 0, sx, sy, decals.get("north")), "south": self._uv(mat, 0, sx, sy, decals.get("south")),
              "east": self._uv(mat, 0, sz, sy, decals.get("east")), "west": self._uv(mat, 0, sz, sy, decals.get("west")),
              "up": self._uv(mat, 1, sx, sz, decals.get("up")), "down": self._uv(mat, 2, sx, sz, decals.get("down"))}
        c = {"origin": [round(x0, 4), round(y0, 4), round(z0, 4)], "size": [round(sx, 4), round(sy, 4), round(sz, 4)], "uv": uv, "_mat": mat}
        if inflate: c["inflate"] = inflate
        if rot and any(rot):
            # intenção = rotação padrão (Rz·Ry·Rx) no espaço bedrock; Blockbench/GeckoLib guardam (-x, y, -z)
            c["rotation"] = [round(-rot[0], 3), round(rot[1], 3), round(-rot[2], 3)]
            c["pivot"] = [round(v, 4) for v in (pivot if pivot else [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2])]
        self.bones[bone]["cubes"].append(c)
        return c
    def cbox(self, bone, center, size, mat, **kw):
        """caixa por centro e tamanho"""
        cx, cy, cz = center; sx, sy, sz = size
        return self.box(bone, (cx - sx / 2, cy - sy / 2, cz - sz / 2), (cx + sx / 2, cy + sy / 2, cz + sz / 2), mat, **kw)
    def taper(self, bone, center_bottom, w0, d0, w1, d1, h, steps, mat, **kw):
        """tronco de pirâmide em degraus (forma orgânica de cubos pequenos)"""
        cx, y, cz = center_bottom
        for i in range(steps):
            t = i / max(1, steps - 1)
            w, d = w0 + (w1 - w0) * t, d0 + (d1 - d0) * t
            self.box(bone, (cx - w / 2, y + h * i / steps, cz - d / 2), (cx + w / 2, y + h * (i + 1) / steps, cz + d / 2), mat, **kw)
    def blob(self, bone, center, rx, ry, rz, mat, layers=5, **kw):
        """elipsoide aproximado com camadas de caixas (cruz em cada camada)"""
        cx, cy, cz = center
        for i in range(layers):
            t = -1 + (2 * i + 1) / layers
            k = math.sqrt(max(0.0, 1 - t * t))
            y0 = cy + ry * (-1 + 2 * i / layers); y1 = cy + ry * (-1 + 2 * (i + 1) / layers)
            self.box(bone, (cx - rx * k, y0, cz - rz * k * 0.62), (cx + rx * k, y1, cz + rz * k * 0.62), mat, **kw)
            self.box(bone, (cx - rx * k * 0.62, y0, cz - rz * k), (cx + rx * k * 0.62, y1, cz + rz * k), mat, **kw)
    def limb(self, bone, p0, p1, w, mat, steps=1, taper_to=None, **kw):
        """segmento entre dois pontos, como cubo girado (só no plano yz ou xy)"""
        (x0, y0, z0), (x1, y1, z1) = p0, p1
        L = math.dist(p0, p1); taper_to = taper_to if taper_to is not None else w
        dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
        rx = math.degrees(math.asin(max(-1, min(1, -dz / L)))) if L > 0 else 0
        rz = math.degrees(math.atan2(dx, -dy)) if L > 0 else 0
        for i in range(steps):
            t0, t1 = i / steps, (i + 1) / steps
            ww = w + (taper_to - w) * (t0 + t1) / 2
            self.box(bone, (x0 - ww / 2, y0 - L * t1, z0 - ww / 2), (x0 + ww / 2, y0 - L * t0, z0 + ww / 2), mat,
                     rot=(rx, 0, rz), pivot=(x0, y0, z0), **kw)
    def shift(self, bone, dx=0.0, dy=0.0, dz=0.0):
        """move um osso e todos os filhos (cubos e pivôs)"""
        names = [bone]; i = 0
        while i < len(names):
            names += [n for n in self.order if self.bones[n].get("parent") == names[i]]; i += 1
        d = (dx, dy, dz)
        for n in names:
            b = self.bones[n]; b["pivot"] = [round(b["pivot"][k] + d[k], 4) for k in range(3)]
            for c in b["cubes"]:
                c["origin"] = [round(c["origin"][k] + d[k], 4) for k in range(3)]
                if "pivot" in c: c["pivot"] = [round(c["pivot"][k] + d[k], 4) for k in range(3)]

    def export(self):
        bones = []
        for n in self.order:
            b = self.bones[n]; out = {k: v for k, v in b.items() if k != "cubes"}
            cubes = [{k: v for k, v in c.items() if not k.startswith("_")} for c in b["cubes"]]
            if cubes: out["cubes"] = cubes
            bones.append(out)
        allc = [c for b in self.bones.values() for c in b["cubes"]]
        mn = [min(c["origin"][i] for c in allc) for i in range(3)]; mx = [max(c["origin"][i] + c["size"][i] for c in allc) for i in range(3)]
        vw = max(mx[0] - mn[0], mx[2] - mn[2]) / 16 + 1; vh = (mx[1] - mn[1]) / 16 + 1
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.name}", "texture_width": self.A.size, "texture_height": self.A.size,
                            "visible_bounds_width": round(vw, 1), "visible_bounds_height": round(vh, 1),
                            "visible_bounds_offset": [0, round(vh / 2, 2), 0]},
            "bones": bones}]}
    def count(self): return sum(len(b["cubes"]) for b in self.bones.values())

# ======================================================================== ANIMAÇÕES
def _rot(x="0", y="0", z="0"): return {"rotation": [x, y, z]}

def animations(geo, extra=None):
    """gera misc.idle, move.walk e attack.strike a partir dos papéis dos ossos"""
    idle, walk, attack = {}, {}, {}
    for b, role in geo.roles.items():
        kind, *arg = role.split(":")
        sgn = -1 if arg and arg[0] == "l" else 1
        ph = float(arg[1]) if len(arg) > 1 else 0.0
        if kind == "body":
            idle[b] = {"position": [0, "math.sin(query.anim_time * 120) * 0.25", 0]}
            walk[b] = {"rotation": ["math.sin(query.anim_time * 720) * 1.5", 0, "math.sin(query.anim_time * 360) * 1.5"]}
            attack[b] = {"rotation": {"0.0": [0, 0, 0], "0.2": [-6, -12, 0], "0.45": [10, 18, 0], "0.8": [0, 0, 0]}}
        elif kind == "head":
            idle[b] = _rot(f"math.sin(query.anim_time * 90) * 2", f"math.sin(query.anim_time * 45) * 4", 0)
            walk[b] = _rot(f"math.sin(query.anim_time * 720) * 2", 0, 0)
        elif kind == "leg":
            walk[b] = _rot(f"math.sin(query.anim_time * 360 + {90 + 90 * sgn + ph}) * 30", 0, 0)
        elif kind == "arm":
            idle[b] = _rot(0, 0, f"{sgn} * (2 + math.sin(query.anim_time * 120) * 2)")
            walk[b] = _rot(f"math.sin(query.anim_time * 360 + {90 - 90 * sgn + ph}) * 28", 0, 0)
            if sgn > 0: attack[b] = {"rotation": {"0.0": [0, 0, 0], "0.2": [-110, 10, 0], "0.45": [20, -10, 0], "0.8": [0, 0, 0]}}
        elif kind == "wing":
            idle[b] = _rot(0, f"{sgn} * math.sin(query.anim_time * 900) * 18", 0)
            walk[b] = idle[b]
        elif kind == "tail":
            idle[b] = _rot(0, f"math.sin(query.anim_time * 120 + {ph}) * 8", 0)
            walk[b] = _rot(0, f"math.sin(query.anim_time * 360 + {ph}) * 14", 0)
        elif kind == "sway":
            idle[b] = _rot(f"math.sin(query.anim_time * 100 + {ph}) * 5", 0, f"math.cos(query.anim_time * 80 + {ph}) * 4")
            walk[b] = _rot(f"math.sin(query.anim_time * 300 + {ph}) * 10", 0, 0)
        elif kind == "jaw":
            idle[b] = _rot(f"(math.sin(query.anim_time * 140) + 1) * 4", 0, 0)
            attack[b] = {"rotation": {"0.0": [0, 0, 0], "0.25": [30, 0, 0], "0.5": [0, 0, 0]}}
        elif kind == "pulse":
            idle[b] = {"scale": ["1 + math.max(0, math.sin(query.anim_time * 400)) * 0.12"] * 3}
            walk[b] = idle[b]
        elif kind == "spin":
            idle[b] = _rot(0, "query.anim_time * 45", 0); walk[b] = idle[b]
        elif kind == "float":
            idle[b] = {"position": [0, f"math.sin(query.anim_time * 90 + {ph}) * 1.2", 0]}; walk[b] = idle[b]
        elif kind == "seg":
            idle[b] = _rot(f"math.sin(query.anim_time * 120 + {ph}) * 4", f"math.sin(query.anim_time * 90 + {ph}) * 5", 0)
            walk[b] = _rot(f"math.sin(query.anim_time * 300 + {ph}) * 6", f"math.sin(query.anim_time * 240 + {ph}) * 8", 0)
    anims = {
        "misc.idle": {"loop": True, "animation_length": 4, "bones": idle},
        "move.walk": {"loop": True, "animation_length": 1, "bones": walk},
        "attack.strike": {"animation_length": 0.8, "bones": attack},
    }
    if extra: anims.update(extra)
    return {"format_version": "1.8.0", "animations": anims}

# ======================================================================== PRÉVIA (z-buffer, ortográfica, com brilho)
FACES = {  # mapeamento igual ao do GeckoLib (BakedModelFactory/GeoQuad): canto da origem da UV, +u, +v, em coordenadas do cubo (x do .geo)
         "north": [(0, 1, 0), (1, 1, 0), (0, 0, 0)], "south": [(1, 1, 1), (0, 1, 1), (1, 0, 1)],
         "east": [(0, 1, 1), (0, 1, 0), (0, 0, 1)], "west": [(1, 1, 0), (1, 1, 1), (1, 0, 0)],
         "up": [(0, 1, 1), (1, 1, 1), (0, 1, 0)], "down": [(0, 0, 0), (1, 0, 0), (0, 0, 1)]}

def rotmat(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    Rx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Ry = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Rz = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

def render(geo_json, tex, glow, yaw=30, pitch=12, size=(520, 520), margin=0.08, bg=None):
    yw, pt = math.radians(yaw), math.radians(pitch)
    dvec = np.array([math.cos(pt) * math.sin(yw), math.sin(pt), -math.cos(pt) * math.cos(yw)])
    fwd = -dvec; right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right); up = np.cross(right, fwd)
    light = np.array([0.4, 0.85, -0.5]); light /= np.linalg.norm(light)
    TW = tex.size[0]; K = max(1, 2048 // TW)
    T = tex.resize((TW * K, TW * K), Image.NEAREST); G = glow.resize((TW * K, TW * K), Image.NEAREST)
    faces = []
    for b in geo_json["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            inf = c.get("inflate", 0)
            o = np.array(c["origin"], float) - inf; s = np.array(c["size"], float) + 2 * inf
            R = rotmat(-c["rotation"][0], c["rotation"][1], -c["rotation"][2]) if "rotation" in c else None; pv = np.array(c.get("pivot", [0, 0, 0]), float)
            for face, corners in FACES.items():
                if s[0] == 0 and face in ("east", "west"): pass
                uvd = c["uv"][face]; u0, v0 = uvd["uv"]; w, h = uvd["uv_size"]
                pts = []
                for cc in corners:
                    p = o + s * np.array(cc, float)
                    if R is not None: p = R @ (p - pv) + pv
                    pts.append(np.array([-p[0], p[1], p[2]]))
                n = np.cross(pts[1] - pts[0], pts[2] - pts[0]); nn = np.linalg.norm(n)
                if nn < 1e-9: continue
                n /= nn
                cb = o + s / 2
                if R is not None: cb = R @ (cb - pv) + pv
                cb = np.array([-cb[0], cb[1], cb[2]])
                if np.dot(n, (pts[1] + pts[2]) / 2 - cb) < 0: n = -n
                if np.dot(n, fwd) >= 0: continue
                faces.append((pts, n, (u0, v0, w, h), inf))
    allp = np.array([p for f in faces for p in f[0]] + [f[0][1] + f[0][2] - f[0][0] for f in faces])
    sx = allp @ right; sy = -(allp @ up)
    W, H = size
    sc = min(W * (1 - 2 * margin) / max(1e-6, sx.max() - sx.min()), H * (1 - 2 * margin) / max(1e-6, sy.max() - sy.min()))
    ox = W / 2 - (sx.max() + sx.min()) / 2 * sc; oy = H / 2 - (sy.max() + sy.min()) / 2 * sc
    proj = lambda q: np.array([float(q @ right) * sc + ox, -float(q @ up) * sc + oy])
    zbuf = np.full((H, W), np.inf); col = np.zeros((H, W, 3), float); alpha = np.zeros((H, W)); gl = np.zeros((H, W, 3), float)
    yy, xx = np.mgrid[0:H, 0:W]
    trans = []
    for pass_ in (0, 1):
        items = faces if pass_ == 0 else sorted(trans, key=lambda f: -float(((f[0][1] + f[0][2]) / 2) @ fwd))
        for pts, n, (u0, v0, w, h), inf in items:
            s0, s1, s2 = [proj(q) for q in pts]
            A_ = np.array([[s0[0], s0[1], 1], [s1[0], s1[1], 1], [s2[0], s2[1], 1]])
            try:
                inv = np.linalg.solve(A_, np.array([[u0 * K, v0 * K], [(u0 + w) * K, v0 * K], [u0 * K, (v0 + h) * K]]))
                dpl = np.linalg.solve(A_, np.array([float(p @ fwd) for p in pts[:3]]))
            except np.linalg.LinAlgError: continue
            quad = [tuple(s0), tuple(s1), tuple(s1 + s2 - s0), tuple(s2)]
            x0 = int(max(0, min(q[0] for q in quad) - 1)); x1 = int(min(W, max(q[0] for q in quad) + 2))
            y0 = int(max(0, min(q[1] for q in quad) - 1)); y1 = int(min(H, max(q[1] for q in quad) + 2))
            if x1 <= x0 or y1 <= y0: continue
            coeffs = (inv[0, 0], inv[1, 0], inv[2, 0], inv[0, 1], inv[1, 1], inv[2, 1])
            box = (x0, y0, x1, y1)
            wimg = np.array(T.transform(size, Image.AFFINE, coeffs, resample=Image.NEAREST).crop(box), float)
            gimg = np.array(G.transform(size, Image.AFFINE, coeffs, resample=Image.NEAREST).crop(box), float)
            mask = Image.new("L", size, 0); ImageDraw.Draw(mask).polygon(quad, fill=255)
            m = np.array(mask.crop(box)) > 0
            depth = dpl[0] * xx[y0:y1, x0:x1] + dpl[1] * yy[y0:y1, x0:x1] + dpl[2] - inf * 0.05
            a = wimg[:, :, 3]
            sh = 0.6 + 0.48 * max(0, float(n @ light))
            isglow = gimg[:, :, 3] > 0
            rgb = wimg[:, :, :3] * np.where(isglow[..., None], 1.0, sh)
            zb = zbuf[y0:y1, x0:x1]
            if pass_ == 0:
                opaque = m & (a >= 250) & (depth < zb)
                semi = m & (a > 20) & (a < 250)
                if semi.any(): trans.append((pts, n, (u0, v0, w, h), inf))
                ok = opaque
                zb[ok] = depth[ok]
                c_ = col[y0:y1, x0:x1]; c_[ok] = rgb[ok]
                al = alpha[y0:y1, x0:x1]; al[ok] = 255
                g_ = gl[y0:y1, x0:x1]; g_[ok] = np.where(isglow[ok][..., None], rgb[ok], 0)
            else:
                ok = m & (a > 20) & (a < 250) & (depth < zb)
                k = (a[ok] / 255.0)[..., None]
                c_ = col[y0:y1, x0:x1]; base = np.where((alpha[y0:y1, x0:x1][ok] > 0)[..., None], c_[ok], np.array([18, 20, 26]))
                c_[ok] = base * (1 - k) + rgb[ok] * k
                al = alpha[y0:y1, x0:x1]; al[ok] = np.maximum(al[ok], a[ok] * 0.9 + 25)
                g_ = gl[y0:y1, x0:x1]; g_[ok] = g_[ok] + np.where(isglow[ok][..., None], rgb[ok] * k, 0)
    img = Image.fromarray(np.dstack([np.clip(col, 0, 255), np.clip(alpha, 0, 255)]).astype(np.uint8), "RGBA")
    bloom = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8), "RGB").filter(ImageFilter.GaussianBlur(max(3, W // 70)))
    bl = np.array(bloom, float)
    out = np.array(img, float)
    out[:, :, :3] = np.clip(out[:, :, :3] + bl * 0.8, 0, 255)
    out[:, :, 3] = np.clip(np.maximum(out[:, :, 3], bl.max(axis=2) * 1.6), 0, 255)
    return Image.fromarray(out.astype(np.uint8), "RGBA")

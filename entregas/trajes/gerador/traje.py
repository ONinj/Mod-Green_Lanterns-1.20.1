# Motor dos trajes "segunda pele" (GeckoLib armor, Forge 1.20.1)
#  - camada de base colada ao corpo, textura em 2 px por unidade (HD 2x), pintada face a face
#  - um mapa de relevo pintado junto com a cor vira cubos finos (0,1 a 0,6 px) que reaproveitam a própria pintura
#  - acessórios 3D (cubos com materiais) por cima
import sys, math, random, json
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, "/home/claude/lore3d")
from core import rotmat

D = 4                    # pixels por unidade (HD 4x)
TEX = 256

def hx(c):
    if not isinstance(c, str): return tuple(int(v) for v in c[:3])
    c = c.lstrip("#"); return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))
def mix(a, b, t): a, b = hx(a), hx(b); return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
def lt(c, k): return mix(c, (255, 255, 255), k)
def dk(c, k): return mix(c, (0, 0, 0), k)

PARTS = {
    "head":  dict(bone="armorHead",      o=(-4, 24, -4),   s=(8, 8, 8),  inf=0.42),
    "body":  dict(bone="armorBody",      o=(-4, 12, -2),   s=(8, 12, 4), inf=0.24),
    "rarm":  dict(bone="armorRightArm",  o=(-8, 12, -2),   s=(4, 12, 4), inf=0.24),
    "larm":  dict(bone="armorLeftArm",   o=(4, 12, -2),    s=(4, 12, 4), inf=0.24),
    "rleg":  dict(bone="armorRightLeg",  o=(-3.9, 0, -2),  s=(4, 12, 4), inf=0.22),
    "lleg":  dict(bone="armorLeftLeg",   o=(-0.1, 0, -2),  s=(4, 12, 4), inf=0.22),
    "rboot": dict(bone="armorRightBoot", o=(-3.9, 0, -2),  s=(4, 5, 4),  inf=0.36),
    "lboot": dict(bone="armorLeftBoot",  o=(-0.1, 0, -2),  s=(4, 5, 4),  inf=0.36),
}
PIVOTS = {"armorHead": (0, 24, 0), "armorBody": (0, 24, 0), "armorRightArm": (-5, 22, 0), "armorLeftArm": (5, 22, 0),
          "armorRightLeg": (-1.9, 12, 0), "armorLeftLeg": (1.9, 12, 0), "armorRightBoot": (-1.9, 12, 0), "armorLeftBoot": (1.9, 12, 0)}
FACES6 = ("north", "south", "east", "west", "up", "down")

def face_size(part, face):
    sx, sy, sz = PARTS[part]["s"]
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]

# semântica: nome lógico da face e se a coordenada "a" lógica é espelhada em relação à textura
#  membros: front/back medem "a" a partir da borda de FORA; outer/inner a partir da FRENTE
#  tronco/cabeça: front/back a partir do lado direito do jogador (vista de fora); sides a partir da frente
def sem_map(part):
    if part in ("rarm", "rleg", "rboot"):
        return {"front": ("north", False), "back": ("south", True), "outer": ("east", True), "inner": ("west", False), "top": ("up", False), "bottom": ("down", False)}
    if part in ("larm", "lleg", "lboot"):
        return {"front": ("north", True), "back": ("south", False), "outer": ("west", False), "inner": ("east", True), "top": ("up", True), "bottom": ("down", True)}
    return {"front": ("north", False), "back": ("south", False), "right": ("east", True), "left": ("west", False), "top": ("up", False), "bottom": ("down", False)}

# ======================================================================== tela de uma face
class Face:
    def __init__(self, part, face, base_col):
        self.part, self.face = part, face
        self.w, self.h = face_size(part, face)
        W, H = int(self.w * D), int(self.h * D)
        self.W, self.H = W, H
        self.col = np.zeros((H, W, 3), float); self.col[:] = hx(base_col)
        self.alpha = np.full((H, W), 255, float)
        self.glow = np.zeros((H, W), bool)
        self.rz = np.zeros((H, W), float)          # relevo (unidades)
        self.tag = np.zeros((H, W), np.int16)      # 0 = tecido de base; >0 = painel (para textura/sombra)
        self.sh = np.ones((H, W), float)           # sombreado suave (anatomia, dobras)
        self.pat = np.zeros((H, W), np.int16)      # padrão de textura por pixel (0 = o do tecido)
        self.flip = False
    def mask_poly(self, pts):
        m = Image.new("L", (self.W, self.H), 0); ImageDraw.Draw(m).polygon([(x * D, y * D) for x, y in pts], fill=255)
        return np.array(m) > 0
    def mask_rect(self, a0, b0, a1, b1):
        m = np.zeros((self.H, self.W), bool)
        y0, y1 = int(round(b0 * D)), int(round(b1 * D)); x0, x1 = int(round(a0 * D)), int(round(a1 * D))
        m[max(0, y0):max(0, y1), max(0, x0):max(0, x1)] = True
        return m
    def mask_line(self, pts, width):
        m = Image.new("L", (self.W, self.H), 0); ImageDraw.Draw(m).line([(x * D, y * D) for x, y in pts], fill=255, width=max(1, int(round(width * D))))
        return np.array(m) > 0
    def mask_ellipse(self, a0, b0, a1, b1):
        m = Image.new("L", (self.W, self.H), 0); ImageDraw.Draw(m).ellipse([a0 * D, b0 * D, a1 * D - 1, b1 * D - 1], fill=255)
        return np.array(m) > 0
    def apply(self, m, col=None, rz=None, glow=None, alpha=None, tag=None, rz_mode="set", shade=None, blur=0.6, pat=None, grad=None):
        if shade is not None:      # multiplica o sombreado com borda suave
            mm = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(blur * D))
            w = np.array(mm, float) / 255.0
            self.sh *= 1 - (1 - shade) * w
            return
        if pat is not None: self.pat[m] = pat
        if col is not None and grad is not None:      # degradê vertical dentro da máscara (claro em cima)
            ys = np.where(m.any(axis=1))[0]
            if len(ys):
                y0, y1 = ys.min(), ys.max() + 1
                t = (np.arange(self.H)[:, None] - y0) / max(1, y1 - y0)
                c0, c1 = np.array(lt(col, grad)), np.array(dk(col, grad))
                full = c0[None, None, :] * (1 - t[..., None]) + c1[None, None, :] * t[..., None]
                full = np.broadcast_to(full, (self.H, self.W, 3))
                self.col[m] = full[m]
            col = None
        if col is not None: self.col[m] = hx(col)
        if rz is not None:
            if rz_mode == "max": self.rz[m] = np.maximum(self.rz[m], rz)
            elif rz_mode == "add": self.rz[m] = self.rz[m] + rz
            else: self.rz[m] = rz
        if glow is not None: self.glow[m] = glow
        if alpha is not None: self.alpha[m] = alpha
        if tag is not None: self.tag[m] = tag
    def mirror(self):
        """espelha a pintura no eixo vertical do meio (para desenhar só um lado em faces simétricas)"""
        pass

# ======================================================================== traje
class Traje:
    def __init__(self, nome, pal, seed=1):
        self.nome, self.pal, self.r = nome, pal, random.Random(seed)
        self.faces = {}
        for p in PARTS:
            for f in FACES6:
                self.faces[(p, f)] = Face(p, f, pal["base"])
        self.acc = []          # acessórios: (bone, a, b, material, rot, pivot)
        self.mats = {}         # material -> cores
        self.hidden_head = True
    # ---- acesso por face lógica (cuida do espelhamento entre direita/esquerda)
    def F(self, part, sface):
        real, flip = sem_map(part)[sface]
        f = self.faces[(part, real)]; f._sflip = flip
        return f
    def draw(self, part, sface, kind, *args, **kw):
        """kind: rect/poly/line/ellipse em coordenadas lógicas (a, b) em unidades"""
        f = self.F(part, sface)
        flip = f._sflip; w = f.w
        fx = (lambda a: w - a) if flip else (lambda a: a)
        if kind == "rect":
            a0, b0, a1, b1 = args[:4]; A0, A1 = sorted((fx(a0), fx(a1))); m = f.mask_rect(A0, b0, A1, b1)
        elif kind == "poly":
            m = f.mask_poly([(fx(a), b) for a, b in args[0]])
        elif kind == "line":
            m = f.mask_line([(fx(a), b) for a, b in args[0]], args[1])
        elif kind == "ellipse":
            a0, b0, a1, b1 = args[:4]; A0, A1 = sorted((fx(a0), fx(a1))); m = f.mask_ellipse(A0, b0, A1, b1)
        elif kind == "all":
            m = np.ones((f.H, f.W), bool)
        else: raise ValueError(kind)
        if "where" in kw:
            wf = kw.pop("where"); m = m & wf(f)
        f.apply(m, **kw)
        return m
    def both(self, base, sface, kind, *args, **kw):
        """desenha nos dois lados (direito e esquerdo) do mesmo membro"""
        for side in ("r", "l"): self.draw(side + base, sface, kind, *args, **kw)
    def sym(self, part, sface, kind, pts_or_rect, *rest, **kw):
        """desenha a forma e o espelho dela no eixo central da face (tronco/cabeça)"""
        f = self.F(part, sface); w = f.w
        self.draw(part, sface, kind, pts_or_rect, *rest, **kw)
        if kind == "poly": self.draw(part, sface, "poly", [(w - a, b) for a, b in pts_or_rect], *rest, **kw)
        elif kind == "line": self.draw(part, sface, "line", [(w - a, b) for a, b in pts_or_rect], *rest, **kw)
        elif kind in ("rect", "ellipse"):
            a0, b0, a1, b1 = pts_or_rect, *rest[:3]; self.draw(part, sface, kind, w - a1, b0, w - a0, b1, **kw)
    def symrect(self, part, sface, a0, b0, a1, b1, **kw):
        f = self.F(part, sface); w = f.w
        self.draw(part, sface, "rect", a0, b0, a1, b1, **kw); self.draw(part, sface, "rect", w - a1, b0, w - a0, b1, **kw)
    # ---- acessórios
    def mat(self, name, col, kind="solid", glow=False, alpha=255):
        self.mats[name] = dict(col=hx(col), kind=kind, glow=glow, alpha=alpha)
    def box(self, bone, a, b, mat, rot=None, pivot=None):
        x0, y0, z0 = [min(a[i], b[i]) for i in range(3)]; x1, y1, z1 = [max(a[i], b[i]) for i in range(3)]
        assert min(x1 - x0, y1 - y0, z1 - z0) > 0.0001, (bone, a, b)
        self.acc.append(dict(bone=bone, o=(x0, y0, z0), s=(x1 - x0, y1 - y0, z1 - z0), mat=mat, rot=rot,
                             pivot=pivot if pivot else ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)))

# ======================================================================== padrões de tecido (variação de brilho por pixel)
PATS = {"liso": 0, "colmeia": 1, "nervura": 2, "carbono": 3, "escamas": 4, "fibra": 5, "acolchoado": 6, "atadura": 7, "malha": 8,
        "placa": 9, "couro": 10, "cristal": 11, "osso": 12, "seda": 13, "pena": 14, "energia": 15, "pele": 16}
def pat(name, x, y):
    if name == "liso": return 0.0
    if name == "colmeia":   # hexágonos pequenos (diagonais alternadas), sutil
        r = y % 6; c = (x + (3 if (y // 6) % 2 else 0)) % 6
        edge = (r == 0 and c in (2, 3)) or (r in (1, 2) and c in (0, 5)) or (r == 3 and c in (2, 3) and False)
        return -0.07 if edge else (0.025 if r == 1 and c in (2, 3) else 0.0)
    if name == "nervura": return -0.08 if x % 4 == 0 else (0.04 if x % 4 == 1 else 0)
    if name == "carbono":
        k = ((x // 2) + (y // 2)) % 2; d = ((x // 2) - (y // 2)) % 4
        return (0.045 if k else -0.045) + (0.02 if d == 0 else 0)
    if name == "escamas":
        cy = y % 6; cx = (x + (3 if (y // 6) % 2 else 0)) % 6
        return -0.13 if cy == 5 and cx in (0, 5) else (-0.05 if cy == 5 else (0.07 if cy == 0 and cx in (2, 3) else 0.0))
    if name == "fibra":
        return -0.07 if (x * 3 + y) % 9 == 0 else (0.045 if (x * 3 + y) % 9 == 1 else 0)
    if name == "acolchoado":
        return -0.11 if ((x + y) % 12 == 0 or (x - y) % 12 == 0) else (0.03 if ((x + y) % 12 == 1 or (x - y) % 12 == 1) else 0)
    if name == "atadura":
        return -0.13 if (y + x // 4) % 6 == 0 else (0.05 if (y + x // 4) % 6 == 1 else 0)
    if name == "malha":
        return -0.06 if (x % 2 == 0 and y % 2 == 0) else 0.02
    if name == "placa":   # metal escovado
        return 0.03 if (y * 7 + x) % 13 == 0 else (-0.02 if x % 5 == 0 else 0)
    if name == "couro":
        return -0.06 if ((x * 13 + y * 7) % 17) < 2 else (0.03 if ((x * 5 + y * 11) % 19) == 0 else 0)
    if name == "cristal":
        return 0.12 if (x - y) % 9 == 0 else (-0.06 if (x + 2 * y) % 11 == 0 else 0)
    if name == "osso":
        return -0.06 if (x * 7 + y * 3) % 23 < 2 else (0.04 if y % 7 == 0 else 0)
    if name == "seda":
        return 0.06 if (x + y // 2) % 10 < 2 else 0
    if name == "energia":   # malha de energia do filme: microgomos em losango com um ponto mais claro no centro
        u = (x + y) % 6; v = (x - y) % 6
        return -0.10 if (u == 0 or v == 0) else (0.07 if (u == 3 and v == 3) else (0.025 if u in (2, 3, 4) and v in (2, 3, 4) else 0))
    if name == "pele":      # tecido escuro de baixo: estrias finas no sentido do músculo
        return -0.06 if (y * 2 + (x % 3)) % 7 == 0 else (0.03 if x % 3 == 1 else 0)
    if name == "pena":
        return -0.1 if (x + (y // 3)) % 5 == 0 else 0.02
    return 0.0
PATN = {v: k for k, v in PATS.items()}

# ======================================================================== finalização: sombreado, relevo -> cubos, atlas
LEVELS = (0.1, 0.22, 0.38, 0.6)

def quantize(rz):
    q = np.zeros_like(rz)
    for L in LEVELS: q[rz >= L - 1e-6] = L
    return q

def snap(rz):
    """relevo alinhado em blocos de 2x2 px (0,5 unidade): borda pintada e borda do cubo coincidem"""
    H, W = rz.shape; q = quantize(rz)
    qb = q.reshape(H // 2, 2, W // 2, 2)
    # nível de cada bloco = o que cobre pelo menos 2 dos 4 pixels (mediana)
    vals = np.sort(qb.transpose(0, 2, 1, 3).reshape(H // 2, W // 2, 4), axis=2)
    lvl = vals[:, :, 2]
    return np.repeat(np.repeat(lvl, 2, axis=0), 2, axis=1)

def finish_face(f, fabric, panel_pat, rng):
    H, W = f.H, f.W
    q = snap(f.rz)
    col = f.col.copy()
    yy, xx = np.mgrid[0:H, 0:W]
    tex = np.zeros((H, W))
    for y in range(H):
        for x in range(W):
            pid = f.pat[y, x]
            name = PATN[pid] if pid > 0 else (panel_pat if f.tag[y, x] > 0 else fabric)
            tex[y, x] = pat(name, x, y) + rng.uniform(-0.02, 0.02)
    k = (1 + tex) * f.sh
    if f.face not in ("up", "down"): k *= 1.07 - 0.14 * (yy / max(1, H - 1))
    up = np.vstack([q[:1], q[:-1]]); dn = np.vstack([q[1:], q[-1:]]); lf = np.hstack([q[:, :1], q[:, :-1]]); rt = np.hstack([q[:, 1:], q[:, -1:]])
    up2 = np.vstack([q[:2], q[:-2]]); lf2 = np.hstack([q[:, :2], q[:, :-2]])
    k = np.where(q > up, k * 1.28, k); k = np.where(q > dn, k * 0.66, k)
    k = np.where(q > lf, k * 1.12, k); k = np.where(q > rt, k * 0.80, k)
    k = np.where((q > up2) & (q <= up), k * 1.08, k)                               # chanfro de 2 px
    k = np.where((up > q), k * 0.70, k); k = np.where((up2 > q) & (up <= q), k * 0.85, k)   # sombra projetada abaixo do relevo
    k = np.where((lf > q), k * 0.84, k)
    # brilho especular diagonal nas placas mais altas
    plate = (q >= 0.22) & (~f.glow)
    k = np.where(plate & (((xx - yy * 0.6) % 26) < 2.2), k * 1.12, k)
    # desgaste nas bordas das placas
    edge_pl = plate & ((q > up) | (q > lf) | (q > rt) | (q > dn))
    wear = np.array([[rng.random() < 0.08 for _ in range(W)] for _ in range(H)])
    k = np.where(edge_pl & wear, k * 1.25, k)
    edge = np.minimum.reduce([xx, W - 1 - xx, yy, H - 1 - yy])
    k = np.where(edge == 0, k * 0.9, k)
    out = np.clip(col * k[..., None] + np.clip(k - 1, 0, None)[..., None] * 70, 0, 255)
    # halo das linhas de energia (tinge o que está em volta)
    if f.glow.any():
        g = Image.fromarray((f.glow * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
        gw = np.array(g, float) / 255.0
        gc = Image.fromarray(np.where(f.glow[..., None], f.col, 0).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
        gcol = np.array(gc, float) / np.maximum(gw[..., None], 1e-3)
        halo = (~f.glow) & (gw > 0.05)
        t = np.clip(gw * 0.9, 0, 0.55)[..., None]
        out = np.where(halo[..., None], out * (1 - t) + np.clip(gcol, 0, 255) * t, out)
        out[f.glow] = np.clip(f.col[f.glow] * (1 + tex[f.glow] * 0.25)[:, None], 0, 255)
    f.out = out; f.q = q

def rects_from_mask(m):
    """decompõe uma máscara em retângulos (gulosos: faixas horizontais unidas na vertical)"""
    H, W = m.shape; used = np.zeros_like(m); rects = []
    for y in range(H):
        x = 0
        while x < W:
            if m[y, x] and not used[y, x]:
                x1 = x
                while x1 < W and m[y, x1] and not used[y, x1]: x1 += 1
                y1 = y + 1
                while y1 < H and m[y1, x:x1].all() and not used[y1, x:x1].any(): y1 += 1
                used[y:y1, x:x1] = True; rects.append((x, y, x1, y1)); x = x1
            else: x += 1
    return rects

def face_point(part, face, a, b):
    """coordenada (a, b) da face real (unidades da textura) -> ponto 3D na superfície inflada (x, y, z do .geo)"""
    o, s, inf = PARTS[part]["o"], PARTS[part]["s"], PARTS[part]["inf"]
    X0, Y0, Z0 = o; X1, Y1, Z1 = o[0] + s[0], o[1] + s[1], o[2] + s[2]
    w, h = face_size(part, face)
    def L(lo, hi, t, n): return lo - inf + t * (hi - lo + 2 * inf) / n
    if face == "north": return (L(X0, X1, a, w), L(Y0, Y1, h - b, h), Z0 - inf), (0, 0, -1)
    if face == "south": return (L(X0, X1, w - a, w), L(Y0, Y1, h - b, h), Z1 + inf), (0, 0, 1)
    if face == "east":  return (X0 - inf, L(Y0, Y1, h - b, h), L(Z0, Z1, w - a, w)), (-1, 0, 0)
    if face == "west":  return (X1 + inf, L(Y0, Y1, h - b, h), L(Z0, Z1, a, w)), (1, 0, 0)
    if face == "up":    return (L(X0, X1, a, w), Y1 + inf, L(Z0, Z1, h - b, h)), (0, 1, 0)
    if face == "down":  return (L(X0, X1, a, w), Y0 - inf, L(Z0, Z1, b, h)), (0, -1, 0)

class Atlas2:
    def __init__(self):
        self.img = np.zeros((TEX, TEX, 4), np.uint8); self.gl = np.zeros((TEX, TEX, 4), np.uint8)
        self.shelf_x = 0; self.shelf_y = 0; self.shelf_h = 0
    def alloc(self, w, h):
        if self.shelf_x + w > TEX: self.shelf_x = 0; self.shelf_y += self.shelf_h; self.shelf_h = 0
        x, y = self.shelf_x, self.shelf_y
        assert y + h <= TEX, "atlas cheio"
        self.shelf_x += w; self.shelf_h = max(self.shelf_h, h)
        return x, y

def build(T, fabric="liso", panel_pat="liso"):
    rng = random.Random(7)
    A = Atlas2()
    # 1) faces da camada de base
    order = sorted(T.faces.items(), key=lambda kv: -kv[1].H)
    for (p, f), F_ in order:
        finish_face(F_, fabric, panel_pat, rng)
        if (F_.alpha == 0).all(): F_.ax = F_.ay = None; continue
        x, y = A.alloc(F_.W, F_.H); F_.ax, F_.ay = x, y
        A.img[y:y + F_.H, x:x + F_.W, :3] = F_.out.astype(np.uint8); A.img[y:y + F_.H, x:x + F_.W, 3] = F_.alpha.astype(np.uint8)
        g = F_.glow & (F_.alpha > 0)
        A.gl[y:y + F_.H, x:x + F_.W][g] = np.dstack([F_.out.astype(np.uint8), F_.alpha.astype(np.uint8)])[g]
    # 2) amostras de material dos acessórios (16x16 por tipo: lado, topo, base)
    mat_uv = {}
    for name, M in T.mats.items():
        slots = []
        for kind in range(3):
            x, y = A.alloc(16, 16); slots.append((x, y))
            for j in range(16):
                for i in range(16):
                    c = M["col"]
                    if M["glow"]:
                        d = math.hypot(i - 7.5, j - 7.5) / 9; cc = mix((255, 255, 255), c, min(1, 0.3 + d))
                    else:
                        if kind == 0: cc = mix(lt(c, 0.12), dk(c, 0.25), j / 15)
                        elif kind == 1: cc = lt(c, 0.16)
                        else: cc = dk(c, 0.38)
                        if M["kind"] == "metal" and i % 4 == 0: cc = lt(cc, 0.06)
                        if M["kind"] == "cristal" and (i + j) % 7 == 0: cc = lt(cc, 0.35)
                        if M["kind"] == "osso" and (i * 3 + j) % 11 == 0: cc = dk(cc, 0.12)
                        if (i in (0, 15) or j in (0, 15)) and kind == 0: cc = dk(cc, 0.12)
                        if j == 0 and kind == 0: cc = lt(cc, 0.2)
                    n = rng.randint(-3, 3)
                    px = tuple(int(max(0, min(255, v + n))) for v in cc)
                    A.img[y + j, x + i] = px + (M["alpha"],)
                    if M["glow"]: A.gl[y + j, x + i] = px + (255,)
        mat_uv[name] = slots
    # pixel livre opaco no glowmask (o GeckoLib não aceita glowmask vazio)
    A.gl[TEX - 1, TEX - 1] = (255, 255, 255, 255); A.img[TEX - 1, TEX - 1] = (0, 0, 0, 0)
    # 3) geometria
    bones = {b: {"name": b, "pivot": list(PIVOTS[b]), "cubes": []} for b in PIVOTS}
    for p, P in PARTS.items():
        cube = {"origin": list(P["o"]), "size": list(P["s"]), "inflate": P["inf"], "uv": {}}
        for f in FACES6:
            F_ = T.faces[(p, f)]
            if (F_.alpha == 0).all(): cube["uv"][f] = {"uv": [TEX - 1, TEX - 1], "uv_size": [0.001, 0.001]}   # face invisível (pixel transparente)
            else: cube["uv"][f] = {"uv": [F_.ax, F_.ay], "uv_size": [F_.W, F_.H]}
        if any(v["uv_size"][0] > 0.01 for v in cube["uv"].values()):
            bones[P["bone"]]["cubes"].append(cube)
        # relevo: para cada nível, retângulos da máscara viram cubos empilhados
        for f in FACES6:
            F_ = T.faces[(p, f)]; prev = 0.0
            if F_.ax is None: continue
            for L in LEVELS:
                m = (F_.q >= L - 1e-6) & (F_.alpha > 0)
                if not m.any(): prev = L; continue
                mb = m[::2, ::2]
                for (x0, y0, x1, y1) in [(a * 2, b * 2, c * 2, d * 2) for (a, b, c, d) in rects_from_mask(mb)]:
                    a0, b0, a1, b1 = x0 / D, y0 / D, x1 / D, y1 / D
                    p00, n = face_point(p, f, a0, b0); p11, _ = face_point(p, f, a1, b1)
                    lo = [min(p00[i], p11[i]) for i in range(3)]; hi = [max(p00[i], p11[i]) for i in range(3)]
                    ax = [i for i in range(3) if n[i] != 0][0]; sgn = n[ax]
                    base = p00[ax]
                    lo[ax] = base + sgn * prev - sgn * 0.005 if sgn > 0 else base + sgn * L
                    hi[ax] = base + sgn * L if sgn > 0 else base + sgn * prev + 0.005
                    lo[ax], hi[ax] = min(lo[ax], hi[ax]), max(lo[ax], hi[ax])
                    size = [hi[i] - lo[i] for i in range(3)]
                    U, V = F_.ax + x0, F_.ay + y0; Wd, Hd = x1 - x0, y1 - y0
                    uv = {f: {"uv": [U, V], "uv_size": [Wd, Hd]}}
                    # laterais do degrau: faixa de 1 px da borda do painel (já sombreada)
                    others = [g for g in FACES6 if g != f and g != OPP[f]]
                    for g in others:
                        uv[g] = edge_strip(f, g, U, V, Wd, Hd)
                    uv[OPP[f]] = {"uv": [U, V], "uv_size": [1, 1]}
                    bones[P["bone"]]["cubes"].append({"origin": [round(v, 4) for v in lo], "size": [round(v, 4) for v in size], "uv": uv})
                prev = L
    # acessórios
    for c in T.acc:
        sl = mat_uv[c["mat"]]
        sx, sy, sz = c["s"]
        def side(w, h, k=0):
            x, y = sl[k]; uw, uh = min(16, max(1, w * D)), min(16, max(1, h * D))
            return {"uv": [x + (16 - uw) / 2, y + (16 - uh) / 2 if k else y], "uv_size": [uw, uh]}
        uv = {"north": side(sx, sy), "south": side(sx, sy), "east": side(sz, sy), "west": side(sz, sy), "up": side(sx, sz, 1), "down": side(sx, sz, 2)}
        cube = {"origin": [round(v, 4) for v in c["o"]], "size": [round(v, 4) for v in c["s"]], "uv": uv}
        if c["rot"] and any(c["rot"]):
            r = c["rot"]; cube["rotation"] = [round(-r[0], 3), round(r[1], 3), round(-r[2], 3)]; cube["pivot"] = [round(v, 4) for v in c["pivot"]]
        bones[c["bone"]]["cubes"].append(cube)
    out_bones = []
    for b in PIVOTS:
        bb = dict(bones[b])
        if not bb["cubes"]: del bb["cubes"]
        out_bones.append(bb)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{T.nome}", "texture_width": TEX, "texture_height": TEX,
                        "visible_bounds_width": 3, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.25, 0]},
        "bones": out_bones}]}
    img = Image.fromarray(A.img, "RGBA"); gl = Image.fromarray(A.gl, "RGBA")
    return geo, img, gl

OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}

def edge_strip(f, g, U, V, W, H):
    # qual borda do painel corresponde à face lateral g do degrau
    if f in ("north", "south", "east", "west"):
        if g == "up": return {"uv": [U, V], "uv_size": [W, 1]}
        if g == "down": return {"uv": [U, V + H - 1], "uv_size": [W, 1]}
        return {"uv": [U if g in ("east",) else U + W - 1, V], "uv_size": [1, H]}
    return {"uv": [U, V], "uv_size": [W, 1]} if g in ("north", "south") else {"uv": [U, V], "uv_size": [1, H]}

def count(geo): return sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])

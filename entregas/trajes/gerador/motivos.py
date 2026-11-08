# Motivos reutilizáveis (anatomia do traje). Coordenadas lógicas em unidades (1 unidade = 1 pixel da skin).
#  tronco: front/back w=8 h=12 · right/left w=4 · top/bottom 8x4
#  membros (arm/leg sem r/l): front/back/outer/inner w=4 h=12 ; boot h=5
from traje import hx, mix, lt, dk

LADOS = ("front", "back", "right", "left")
MEMBRO = ("front", "back", "outer", "inner")

def faixa_corpo(T, b0, b1, col, rz=None, glow=None, tag=1):
    for f in LADOS: T.draw("body", f, "rect", 0, b0, 8 if f in ("front", "back") else 4, b1, col=col, rz=rz, glow=glow, tag=tag)

def faixa_membro(T, base, b0, b1, col, rz=None, glow=None, tag=1, faces=MEMBRO, lado="rl"):
    for s in lado:
        for f in faces: T.draw(s + base, f, "rect", 0, b0, 4, b1, col=col, rz=rz, glow=glow, tag=tag)

def gola(T, col, rz=0.22, alto=0.7, glow=None):
    faixa_corpo(T, 0, alto, col, rz)
    T.draw("body", "top", "rect", 0, 0, 8, 4, col=col)
    T.draw("body", "top", "rect", 2.5, 1, 5.5, 3, col=dk(col, 0.5))            # abertura do pescoço
    if glow: T.draw("body", "front", "rect", 3, alto - 0.5, 5, alto, col=glow, glow=True)

def peitoral(T, col, rz=0.1, borda=None, estilo="curvo"):
    """placas do peito (duas), com borda opcional"""
    if estilo == "curvo":
        pts = [(0.5, 1.0), (3.75, 1.0), (3.75, 4.2), (3.0, 4.8), (1.5, 4.8), (0.5, 3.9)]
    elif estilo == "angular":
        pts = [(0.3, 0.9), (3.8, 1.6), (3.8, 4.6), (2.2, 5.0), (0.3, 3.6)]
    else:
        pts = [(0.6, 1.2), (3.7, 1.2), (3.7, 4.4), (0.6, 4.4)]
    T.sym("body", "front", "poly", pts, col=col, rz=rz, tag=2)
    if borda: T.sym("body", "front", "line", pts + [pts[0]], 0.5, col=borda, rz=rz, tag=2)

def abdomen(T, col, rz=0.1, linhas=None, rows=3, top=5.4, alto=1.25, gap=0.25, larg=1.55):
    for i in range(rows):
        b0 = top + i * (alto + gap)
        T.sym("body", "front", "rect", 4 - larg - 0.15, b0, 3.85, b0 + alto, col=col, rz=rz, tag=3)
        if linhas: T.sym("body", "front", "rect", 4 - larg - 0.15, b0 + alto - 0.5, 3.85, b0 + alto, col=linhas, rz=rz, tag=3)

def flancos(T, col, rz=0.1, b0=4.5, b1=10.2, glow=None):
    for f in ("right", "left"):
        T.draw("body", f, "poly", [(0.6, b0), (3.4, b0 + 0.8), (3.4, b1), (0.6, b1)], col=col, rz=rz, tag=4)
        if glow: T.draw("body", f, "rect", 0.6, b0 + 0.6, 1.1, b1, col=glow, glow=True, rz=rz)

def cinto(T, col, fivela=None, rz=0.22, b0=10.3, b1=11.4, fivela_glow=False):
    faixa_corpo(T, b0, b1, col, rz, tag=5)
    if fivela:
        T.draw("body", "front", "rect", 3.0, b0 - 0.15, 5.0, b1 + 0.15, col=fivela, rz=rz + 0.16, glow=fivela_glow, tag=5)
        T.draw("body", "front", "rect", 3.5, b0 + 0.25, 4.5, b1 - 0.25, col=dk(fivela, 0.45), rz=rz + 0.16, glow=fivela_glow, tag=5)

def coluna(T, col, rz=0.22, b0=0.6, b1=10.2, vertebras=None):
    T.draw("body", "back", "rect", 3.5, b0, 4.5, b1, col=col, rz=rz, tag=6)
    if vertebras:
        b = b0 + 0.3
        while b < b1 - 0.4:
            T.draw("body", "back", "rect", 3.25, b, 4.75, b + 0.5, col=vertebras, rz=rz + 0.16, tag=6); b += 1.2

def omoplatas(T, col, rz=0.1, glow=None):
    pts = [(0.8, 1.2), (3.2, 1.6), (3.2, 5.6), (2.0, 6.4), (0.8, 5.4)]
    T.sym("body", "back", "poly", pts, col=col, rz=rz, tag=7)
    if glow: T.sym("body", "back", "line", [(1.2, 2.0), (2.8, 2.3), (2.8, 5.2)], 0.5, col=glow, glow=True, rz=rz)

def ombreira(T, col, rz=0.22, alto=2.6, borda=None, camadas=1, glow=None, lado="rl"):
    """capa de ombro: topo + faces externas, com camadas em degrau"""
    for s in lado:
        p = s + "arm"
        T.draw(p, "top", "rect", 0, 0, 4, 4, col=col)
        for k in range(camadas):
            b1 = alto - k * 0.9
            for f in ("outer", "front", "back"):
                T.draw(p, f, "rect", 0, 0, 4, b1, col=col if k == 0 else lt(col, 0.08 * k), rz=rz + 0.12 * k, tag=8, rz_mode="max")
            T.draw(p, "inner", "rect", 0, 0, 4, min(1.0, b1), col=col, rz=rz, tag=8, rz_mode="max")
            if borda:
                for f in ("outer", "front", "back"):
                    T.draw(p, f, "rect", 0, b1 - 0.5, 4, b1, col=borda, rz=rz + 0.12 * k, tag=8, rz_mode="max")
        if glow:
            T.draw(p, "outer", "rect", 0.5, alto - 1.3, 3.5, alto - 0.8, col=glow, glow=True)

def bracadeira(T, col, rz=0.22, b0=6.8, b1=10.3, borda=None, glow=None, lado="rl", faces=("outer", "front", "back")):
    for s in lado:
        p = s + "arm"
        for f in faces:
            T.draw(p, f, "rect", 0, b0, 4, b1, col=col, rz=rz, tag=9, rz_mode="max")
            if borda:
                T.draw(p, f, "rect", 0, b0, 4, b0 + 0.5, col=borda, rz=rz + 0.1, tag=9, rz_mode="max")
                T.draw(p, f, "rect", 0, b1 - 0.5, 4, b1, col=borda, rz=rz + 0.1, tag=9, rz_mode="max")
        if glow:
            T.draw(p, "outer", "rect", 1.0, b0 + 1.0, 3.0, b0 + 1.5, col=glow, glow=True, rz=rz)
            T.draw(p, "outer", "rect", 1.0, b0 + 2.0, 3.0, b0 + 2.5, col=glow, glow=True, rz=rz)

def luva(T, col, rz=0.1, b0=10.2, junta=None, lado="rl"):
    for s in lado:
        p = s + "arm"
        for f in MEMBRO: T.draw(p, f, "rect", 0, b0, 4, 12, col=col, rz=rz, tag=10, rz_mode="max")
        T.draw(p, "bottom", "all", col=dk(col, 0.15))
        if junta:   # nós dos dedos
            for a in (0.4, 1.4, 2.4):
                T.draw(p, "front", "rect", a, 10.8, a + 0.8, 11.3, col=junta, rz=rz + 0.12, tag=10, rz_mode="max")

def coxa(T, col, rz=0.1, b0=0.8, b1=5.0, faces=("front", "outer"), lado="rl"):
    for s in lado:
        for f in faces:
            T.draw(s + "leg", f, "poly", [(0.4, b0), (3.6, b0), (3.6, b1 - 0.6), (2.0, b1), (0.4, b1 - 0.6)], col=col, rz=rz, tag=11, rz_mode="max")

def joelheira(T, col, rz=0.3, b0=5.2, b1=7.4, borda=None, lado="rl"):
    for s in lado:
        p = s + "leg"
        T.draw(p, "front", "poly", [(0.5, b0), (3.5, b0), (3.8, b0 + 1.0), (3.2, b1), (0.8, b1), (0.2, b0 + 1.0)], col=col, rz=rz, tag=12, rz_mode="max")
        if borda: T.draw(p, "front", "rect", 1.2, b0 + 0.9, 2.8, b0 + 1.4, col=borda, rz=rz + 0.1, tag=12, rz_mode="max")

def bota(T, col, sola, rz=0.1, cano=None, biqueira=None, lado="rl"):
    for s in lado:
        p = s + "boot"
        for f in MEMBRO: T.draw(p, f, "rect", 0, 0, 4, 5, col=col, tag=13)
        for f in MEMBRO: T.draw(p, f, "rect", 0, 4.3, 4, 5, col=sola, rz=0.22, tag=13, rz_mode="max")
        T.draw(p, "bottom", "all", col=dk(sola, 0.2)); T.draw(p, "top", "all", col=col, alpha=0)
        if cano:
            for f in MEMBRO: T.draw(p, f, "rect", 0, 0, 4, 0.7, col=cano, rz=0.22, tag=13, rz_mode="max")
        if biqueira:
            T.draw(p, "front", "rect", 0.3, 2.8, 3.7, 4.3, col=biqueira, rz=0.22, tag=13, rz_mode="max")
            T.draw(p, "back", "rect", 0.6, 3.0, 3.4, 4.3, col=biqueira, rz=0.1, tag=13, rz_mode="max")

# ---------------------------------------------------------------- máscara esculpida (dominó realista)
# metade direita do rosto (a de 0 a 4); a outra metade é o espelho. Pontos de cima (testa) e de baixo (maçãs do rosto).
MASC = {
    "classico": dict(top=[(0, 2.5), (1.0, 2.3), (2.2, 2.25), (3.2, 2.45), (4, 2.85)],
                     bot=[(4, 4.55), (3.45, 4.75), (2.7, 5.25), (1.5, 5.35), (0.5, 5.05), (0, 4.8)]),
    "angular":  dict(top=[(0, 2.0), (1.4, 2.35), (2.9, 2.8), (3.6, 3.05), (4, 2.9)],
                     bot=[(4, 4.5), (3.4, 4.7), (2.4, 5.3), (1.0, 5.25), (0, 5.5)]),
    "baile":    dict(top=[(0, 1.1), (0.7, 1.5), (1.7, 2.3), (3.0, 2.4), (4, 2.8)],
                     bot=[(4, 4.55), (3.4, 4.75), (2.6, 5.2), (1.4, 5.25), (0.4, 4.7), (0, 4.3)]),
}
OLHO = {
    "classico": [(1.0, 3.95), (1.5, 3.6), (2.4, 3.55), (3.05, 3.8), (3.1, 4.1), (2.45, 4.35), (1.5, 4.3)],
    "angular":  [(0.9, 3.6), (2.2, 3.75), (3.1, 4.05), (3.0, 4.35), (2.0, 4.4), (1.2, 4.25)],
}
def _esp(pts): return [(8 - a, b) for a, b in pts]

def mascara_forma(T, col, estilo="classico", b0=None, b1=None, rz=0.1, borda=None, abas=True, grad=0.18, pat=None):
    """desenha só a máscara esculpida (não limpa a cabeça): contorno anatômico, sombreado, vinco e brilho"""
    M = MASC[estilo]
    dt = 0 if b0 is None else b0 - min(b for a, b in M["top"])
    db = 0 if b1 is None else b1 - max(b for a, b in M["bot"])
    top = [(a, b + dt) for a, b in M["top"]]; bot = [(a, b + db) for a, b in M["bot"]]
    cont = top + _esp(list(reversed(top)))[1:] + _esp(list(reversed(bot))) + bot[1:]
    T.draw("head", "front", "poly", cont, col=col, alpha=255, rz=rz, grad=grad, pat=pat)
    T.draw("head", "front", "line", cont + [cont[0]], 0.25, col=dk(col, 0.45), alpha=255)            # chanfro da borda
    for lado in (top, _esp(top)): T.draw("head", "front", "line", lado, 0.25, col=lt(col, 0.28), alpha=255)  # luz na borda de cima
    if borda:
        for lado in (top, _esp(top)): T.draw("head", "front", "line", lado, 0.25, col=borda, alpha=255)
    for sg in (0, 1):                                  # arco da sobrancelha, vinco da têmpora e sombra da maçã do rosto
        f = (lambda p: p) if sg == 0 else _esp
        T.draw("head", "front", "line", f([(1.1, 2.95 + dt * 0.5), (2.0, 2.8 + dt * 0.5), (3.0, 2.95 + dt * 0.5)]), 0.25, col=lt(col, 0.16), alpha=255)
        T.draw("head", "front", "line", f([(0.1, 3.5), (0.75, 3.85)]), 0.25, col=dk(col, 0.3), alpha=255)
        T.draw("head", "front", "line", f([(1.4, 4.85 + db * 0.5), (2.6, 4.85 + db * 0.5)]), 0.25, col=dk(col, 0.22), alpha=255)
        T.draw("head", "front", "rect", *(lambda r: r if sg == 0 else (8 - r[2], r[1], 8 - r[0], r[3]))((1.6, 2.55 + dt * 0.5, 2.3, 2.8 + dt * 0.5)), col=lt(col, 0.42), alpha=255)
    T.draw("head", "front", "rect", 3.75, 3.1, 4.25, 4.3, col=lt(col, 0.2), alpha=255)                 # ponte do nariz
    for a0 in (3.5, 4.25): T.draw("head", "front", "rect", a0, 3.3, a0 + 0.25, 4.5, col=dk(col, 0.18), alpha=255)
    if abas:   # a máscara contorna as têmporas e some nas laterais
        t0, t1 = top[0][1], bot[-1][1]
        for f in ("right", "left"):
            T.draw("head", f, "poly", [(0, t0), (2.8, t0 + 0.7), (3.2, (t0 + t1) / 2), (2.8, t1 - 0.6), (0, t1)], col=dk(col, 0.08), alpha=255, rz=rz, grad=grad)
            T.draw("head", f, "line", [(0, t0), (2.8, t0 + 0.7), (3.2, (t0 + t1) / 2), (2.8, t1 - 0.6), (0, t1)], 0.25, col=dk(col, 0.45), alpha=255)
            T.draw("head", f, "line", [(0, t0 + 0.1), (2.8, t0 + 0.8)], 0.25, col=lt(col, 0.2), alpha=255)

def lentes(T, col, b0=3.5, b1=4.5, glow=True, forma="reta", olhos="rl", aro=None, rz=0.0):
    """olhos amendoados recuados na máscara: aro escuro (fundo), lente acesa com borda suave"""
    pts = OLHO["angular" if forma == "angular" else "classico"]
    dy = (b0 + b1) / 2 - 3.95
    pts = [(a, b + dy) for a, b in pts]
    for lado in olhos:
        pp = pts if lado == "r" else _esp(pts)
        T.draw("head", "front", "line", pp + [pp[0]], 0.75, col=aro if aro else "#050506", alpha=255, rz=rz)
        T.draw("head", "front", "poly", pp, col=col, glow=glow, alpha=255, rz=rz)
        T.draw("head", "front", "line", pp + [pp[0]], 0.25, col=mix(col, (40, 40, 48), 0.3) if not glow else mix(col, (255, 255, 255), 0.0), glow=glow, alpha=255, rz=rz)

def mascara(T, col, lente=None, lente_glow=True, rz=0.0, b0=None, b1=None, abas=True, estilo="classico", borda=None, forma=None, pat=None):
    """meia-máscara esculpida: só a máscara é opaca; o resto da cabeça fica transparente"""
    for f in ("front", "back", "right", "left", "top", "bottom"): T.draw("head", f, "all", alpha=0)
    mascara_forma(T, col, estilo, b0, b1, rz=rz, borda=borda, abas=abas, pat=pat)
    if lente:
        lentes(T, lente, glow=lente_glow, forma=forma or ("angular" if estilo == "angular" else "reta"), aro=dk(col, 0.6))

def emblema3d(T, grid, col, fundo=None, a_c=4.0, b_c=2.9, cel=0.5, rz_fundo=0.22, rz=0.38, glow=True, aro=None):
    """emblema da Tropa em relevo no peito (grade 9x9 dos anéis; 1 célula = cel unidades)"""
    n = len(grid); L = n * cel
    a0, b0 = a_c - L / 2, b_c - L / 2
    if fundo:
        T.draw("body", "front", "ellipse", a0 - 0.1, b0 - 0.1, a0 + L + 0.1, b0 + L + 0.1, col=fundo, rz=0.1, tag=14, rz_mode="max")
    if aro:   # aro fino em volta (sem cobrir o peito)
        f = T.F("body", "front")
        ring = f.mask_ellipse(a0 - 0.6, b0 - 0.6, a0 + L + 0.6, b0 + L + 0.6) & ~f.mask_ellipse(a0 - 0.1, b0 - 0.1, a0 + L + 0.1, b0 + L + 0.1)
        f.apply(ring, col=aro, rz=rz_fundo, tag=14, rz_mode="max")
    for j, row in enumerate(grid):
        for i, ch in enumerate(row):
            if ch == "#":
                T.draw("body", "front", "rect", a0 + i * cel, b0 + j * cel, a0 + (i + 1) * cel, b0 + (j + 1) * cel, col=col, rz=rz, glow=glow, tag=14)

def circuito(T, part, face, pts, col, w=0.5, rz=None):
    T.draw(part, face, "line", pts, w, col=col, glow=True, rz=rz, rz_mode="max" if rz else "set")

# ======================================================================== anatomia (sombreado suave por baixo do traje) e costuras
def anatomia(T, k=1.0, magro=False):
    S = lambda v: 1 - (1 - v) * k
    # tronco frente: sulco central, sombra sob o peitoral, gomos do abdômen, clavículas, oblíquos
    T.draw("body", "front", "rect", 3.85, 1.2, 4.15, 10.0, shade=S(0.80), blur=0.35)
    T.sym("body", "front", "ellipse", 0.6, 3.9, 3.9, 5.3, shade=S(0.78), blur=0.5)
    T.sym("body", "front", "ellipse", 0.9, 1.3, 3.6, 3.6, shade=S(1.12), blur=0.6)
    for b in (6.6, 8.0, 9.3): T.draw("body", "front", "rect", 2.4, b, 5.6, b + 0.2, shade=S(0.84), blur=0.3)
    T.sym("body", "front", "rect", 0.0, 4.5, 0.9, 10.5, shade=S(0.82), blur=0.6)
    T.sym("body", "front", "line", [(0.8, 0.9), (3.5, 1.3)], 0.3, shade=S(1.15), blur=0.3)
    # costas: coluna, omoplatas, lombar
    T.draw("body", "back", "rect", 3.8, 0.8, 4.2, 10.0, shade=S(0.8), blur=0.35)
    T.sym("body", "back", "ellipse", 0.8, 1.2, 3.4, 5.6, shade=S(1.1), blur=0.7)
    T.sym("body", "back", "line", [(1.0, 5.6), (3.3, 6.4)], 0.35, shade=S(0.82), blur=0.4)
    T.draw("body", "back", "rect", 2.0, 8.4, 6.0, 10.2, shade=S(0.9), blur=0.8)
    for side in ("right", "left"): T.draw("body", side, "rect", 0, 0, 4, 12, shade=S(0.93), blur=0.1)
    # braços: deltoide, bíceps, cotovelo, antebraço
    for s in "rl":
        p = s + "arm"
        for f in ("outer", "front", "back"):
            T.draw(p, f, "ellipse", 0.2, -0.6, 3.8, 3.2, shade=S(1.12), blur=0.6)
            T.draw(p, f, "rect", 0, 3.1, 4, 3.5, shade=S(0.8), blur=0.45)
        T.draw(p, "front", "ellipse", 0.6, 3.6, 3.4, 6.6, shade=S(1.1), blur=0.6)
        T.draw(p, "front", "rect", 0, 6.5, 4, 6.9, shade=S(0.82), blur=0.35)
        T.draw(p, "back", "rect", 1.4, 6.0, 2.6, 7.0, shade=S(1.12), blur=0.4)
        for f in MEMBRO: T.draw(p, f, "rect", 0, 9.0, 4, 10.4, shade=S(0.92), blur=0.6)
        T.draw(p, "inner", "rect", 0, 0, 4, 12, shade=S(0.9), blur=0.1)
    # pernas: quadríceps, joelho, panturrilha, parte interna
    for s in "rl":
        p = s + "leg"
        T.draw(p, "front", "ellipse", 0.3, 0.6, 3.7, 5.2, shade=S(1.1), blur=0.7)
        T.draw(p, "front", "rect", 0, 5.0, 4, 5.4, shade=S(0.82), blur=0.4)
        T.draw(p, "front", "ellipse", 1.0, 5.4, 3.0, 7.2, shade=S(1.12), blur=0.4)
        T.draw(p, "front", "rect", 0, 7.4, 4, 7.7, shade=S(0.85), blur=0.3)
        T.draw(p, "back", "ellipse", 0.4, 7.0, 3.6, 10.6, shade=S(1.12), blur=0.7)
        T.draw(p, "back", "rect", 0, 5.6, 4, 6.2, shade=S(0.8), blur=0.4)
        T.draw(p, "inner", "rect", 0, 0, 4, 12, shade=S(0.88), blur=0.1)
        T.draw(p, "outer", "rect", 1.6, 0.4, 2.4, 11.0, shade=S(0.92), blur=0.5)

def costura(T, part, face, pts, col="#000000", ponto=None, w=0.25):
    """costura: vinco escuro com pontos claros alternados"""
    T.draw(part, face, "line", pts, w, col=col)
    if ponto:
        for (a0, b0), (a1, b1) in zip(pts, pts[1:]):
            L = ((a1 - a0) ** 2 + (b1 - b0) ** 2) ** 0.5; n = int(L / 0.75)
            for i in range(n):
                t = (i + 0.3) / max(1, n); a, b = a0 + (a1 - a0) * t, b0 + (b1 - b0) * t
                T.draw(part, face, "rect", a - 0.12, b - 0.12, a + 0.13, b + 0.13, col=ponto)

def costuras_padrao(T, col, ponto=None):
    """costuras laterais do macacão (encontro das faces) e do gancho"""
    for s in "rl":
        for p in (s + "arm", s + "leg"):
            costura(T, p, "inner", [(2.0, 0.2), (2.0, 11.8)], col, ponto)
    for side in ("right", "left"): costura(T, "body", side, [(2.0, 0.2), (2.0, 11.8)], col, ponto)

def linhas_painel(T, col, luz=None, w=0.25):
    """linhas ergonômicas do traje (recortes de painel), com fio de luz opcional ao lado"""
    def L(part, face, pts):
        T.draw(part, face, "line", pts, w, col=col)
        if luz: T.draw(part, face, "line", [(a + 0.25, b + 0.25) for a, b in pts], w, col=luz)
    # tronco: do sovaco descendo em curva até o centro do quadril; da clavícula ao ombro
    T.sym("body", "front", "line", [(0.0, 2.2), (1.2, 5.2), (2.3, 8.2), (3.2, 10.3)], w, col=col)
    T.sym("body", "front", "line", [(0.2, 0.9), (2.4, 0.9), (3.4, 1.6)], w, col=col)
    T.sym("body", "back", "line", [(0.0, 2.0), (1.6, 6.5), (3.0, 10.3)], w, col=col)
    for s in "rl":
        L(s + "arm", "outer", [(0.0, 3.3), (4.0, 3.3)])
        L(s + "arm", "front", [(0.0, 3.3), (1.8, 6.6), (4.0, 7.2)])
        L(s + "arm", "back", [(0.0, 3.3), (2.0, 6.0), (4.0, 7.0)])
        L(s + "leg", "front", [(0.0, 0.6), (2.6, 4.0), (4.0, 5.2)])
        L(s + "leg", "outer", [(0.0, 7.0), (2.0, 5.0), (4.0, 2.5)])
        L(s + "leg", "back", [(0.0, 6.0), (2.0, 8.0), (4.0, 6.5)])

def ombro_fino(T, col, borda=None, rz=0.1, alto=2.3, lado="rl", glow=None, grad=0.12):
    """capa de ombro rente (segunda pele): topo + 3 faces, com aro elevado"""
    for s in lado:
        p = s + "arm"
        T.draw(p, "top", "all", col=col)
        for f in ("outer", "front", "back"):
            T.draw(p, f, "poly", [(0, 0), (4, 0), (4, alto - 0.6), (2, alto), (0, alto - 0.6)] if f == "outer" else [(0, 0), (4, 0), (4, alto - 0.9), (0, alto)] if f == "front" else [(0, 0), (4, 0), (4, alto - 0.9), (0, alto)],
                   col=col, grad=grad, rz=rz, tag=8, rz_mode="max")
        T.draw(p, "inner", "rect", 0, 0, 4, 0.9, col=col, rz=rz, tag=8, rz_mode="max")
        if borda:
            T.draw(p, "outer", "line", [(0, alto - 0.6), (2, alto), (4, alto - 0.6)], 0.5, col=borda, rz=rz + 0.12, rz_mode="max", tag=8)
        if glow: T.draw(p, "outer", "rect", 1.5, 0.6, 2.5, alto - 0.9, col=glow, glow=True)

# ======================================================================== cabeça
def cabeca_vazia(T):
    for f in ("front", "back", "right", "left", "top", "bottom"): T.draw("head", f, "all", alpha=0)

def capuz(T, col, borda=None, rosto=(1.0, 1.6, 7.0, 8.0), grad=0.15, rz_borda=0.22, pat=None):
    """capuz/cogula: topo, costas e laterais cobertos; abre o rosto (rosto = a0, b0, a1, b1 da abertura)"""
    for f in ("back", "right", "left", "top"):
        T.draw("head", f, "all", col=col, alpha=255, grad=grad if f != "top" else None, pat=pat)
    T.draw("head", "bottom", "all", alpha=0)
    T.draw("head", "front", "all", col=col, alpha=255, grad=grad)
    a0, b0, a1, b1 = rosto
    T.draw("head", "front", "rect", a0, b0, a1, b1, alpha=0)
    if borda:
        T.draw("head", "front", "rect", a0 - 0.5, b0 - 0.5, a1 + 0.5, b0, col=borda, rz=rz_borda, alpha=255)
        T.draw("head", "front", "rect", a0 - 0.5, b0, a0, b1, col=borda, rz=rz_borda, alpha=255)
        T.draw("head", "front", "rect", a1, b0, a1 + 0.5, b1, col=borda, rz=rz_borda, alpha=255)

# ======================================================================== acessórios 3D
def espinho(T, bone, base, eixo, L, w, mat, passos=3, rot=None):
    """espinho em degraus a partir de base, ao longo do eixo ('+x','-x','+y','-y','+z','-z')"""
    x, y, z = base; ax = "xyz".index(eixo[1]); sg = 1 if eixo[0] == "+" else -1
    seg = L / passos
    for i in range(passos):
        ww = w * (1 - i / passos * 0.75)
        lo = [x - ww / 2, y - ww / 2, z - ww / 2]; hi = [x + ww / 2, y + ww / 2, z + ww / 2]
        p0 = [x, y, z][ax] + sg * seg * i; p1 = p0 + sg * seg
        lo[ax], hi[ax] = min(p0, p1), max(p0, p1)
        T.box(bone, lo, hi, mat, rot=rot, pivot=base if rot else None)

def cristal(T, bone, base, h, w, mat, rot=(0, 0, 0)):
    x, y, z = base
    T.box(bone, (x - w / 2, y, z - w / 2), (x + w / 2, y + h, z + w / 2), mat, rot=rot, pivot=base)
    T.box(bone, (x - w * 0.3, y + h, z - w * 0.3), (x + w * 0.3, y + h + w * 0.6, z + w * 0.3), mat, rot=rot, pivot=base)

def tiras(T, bone, pts, mat, larg=0.8, esp=0.15, eixo="z"):
    """tiras penduradas (pano rasgado): pts = [(x, y_topo, z, comprimento, inclinação)]"""
    for (x, y, z, L, inc) in pts:
        if eixo == "z":
            T.box(bone, (x - larg / 2, y - L, z - esp / 2), (x + larg / 2, y, z + esp / 2), mat, rot=(inc, 0, 0) if inc else None, pivot=(x, y, z))
        else:
            T.box(bone, (x - esp / 2, y - L, z - larg / 2), (x + esp / 2, y, z + larg / 2), mat, rot=(0, 0, inc) if inc else None, pivot=(x, y, z))

def aureola(T, raio, y, mat, n=16, esp=0.35, cz=0.0):
    for i in range(n):
        a = 2 * math.pi * i / n
        x, z = raio * math.cos(a), cz + raio * math.sin(a)
        T.box("armorHead", (x - esp, y, z - esp), (x + esp, y + esp, z + esp), mat, rot=(0, -math.degrees(a), 0), pivot=(x, y, z))

import math

# Baterias de mão (bloco/item Java) no formato clássico de lanterna: pé, boca larga, globo de vidro com o núcleo,
# boca larga, tampa, haste e alça. O símbolo é o mesmo do anel (16x16) em microcubos na frente e atrás do globo.
import sys, math, json
import numpy as np
from PIL import Image
sys.path.insert(0, "/home/claude/aneis")
from simbolos import extrair

C = 8.0
LUZ = {"block_light": 15, "sky_light": 15}
ARQ = dict(verde="bateria_vontade_verde", amarelo="bateria_medo_amarelo", vermelho="bateria_raiva_vermelho", laranja="bateria_avareza_laranja",
           azul="bateria_esperanca_azul", violeta="bateria_amor_violeta", preto="bateria_morte_preto")
PAL = {  # corpo (escuro), corpo2 (cor da Tropa), borda (metal claro), vidro, núcleo, acento
    "verde":    ("#1C4A2C", "#2A8A48", "#9AA8A0", "#5AFF9A", "#B4FFC8", "#2A8A48"),
    "amarelo":  ("#1A1A1C", "#C8A400", "#3A3A3E", "#FFE84A", "#FFF6B0", "#F4F0E0"),
    "vermelho": ("#3A0E0E", "#8A1414", "#3A2626", "#E0281A", "#FFB090", "#FF3A10"),
    "laranja":  ("#6A3A18", "#C88A20", "#E8BC50", "#FF9A2A", "#FFE0A8", "#FF7A10"),
    "azul":     ("#1E4E9A", "#D8E2EE", "#C8D4E4", "#6AC8FF", "#E0F4FF", "#E8F2FF"),
    "violeta":  ("#4A1A5A", "#9A3AB8", "#E0C4EE", "#E070FF", "#FFD8FF", "#F070FF"),
    "preto":    ("#101012", "#2A2A30", "#5A5A64", "#9AA0B8", "#F0F2FF", "#E0DCCB"),
}
def hx(c): c = c.lstrip("#"); return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))
def mix(a, b, t): return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))
def lt(c, k): return mix(c, (255, 255, 255), k)
def dk(c, k): return mix(c, (0, 0, 0), k)

SW = {"corpo": 0, "corpo2": 1, "borda": 2, "energia": 3, "vidro": 4, "nucleo": 5, "simbolo": 6, "placa": 7, "acento": 8, "interno": 9}
def textura(cor, sym):
    corpo, corpo2, borda, vidro, nuc, ac = [hx(c) for c in PAL[cor]]
    rng = np.random.default_rng(5)
    T = np.zeros((64, 64, 4), np.uint8)
    def tile(i, f):
        x0, y0 = (i % 4) * 16, (i // 4) * 16
        for y in range(16):
            for x in range(16):
                r = f(x, y); a = r[3] if len(r) > 3 else 255
                T[y0 + y, x0 + x] = (*[max(0, min(255, int(v))) for v in r[:3]], a)
    def metal(base, hi=0.3):
        def f(x, y):
            t = y / 15.0; k = 1 + hi * math.exp(-((t - 0.3) / 0.14) ** 2) - 0.35 * t * t + rng.uniform(-0.03, 0.03)
            k *= 0.86 if x in (0, 15) or y in (0, 15) else 1
            return [c * k for c in base]
        return f
    tile(SW["corpo"], metal(corpo)); tile(SW["corpo2"], metal(corpo2)); tile(SW["borda"], metal(borda, 0.45))
    tile(SW["energia"], lambda x, y: lt(vidro, 0.35))
    tile(SW["vidro"], lambda x, y: (*lt(vidro, 0.25 if (x + y) % 11 == 0 else 0.0), 150 if (x - y) % 13 == 0 else 105))
    tile(SW["nucleo"], lambda x, y: lt(nuc, 0.4 * math.exp(-((x - 7.5) ** 2 + (y - 7.5) ** 2) / 40)))
    s = hx("#FFFFFF") if sum(sym) < 150 else tuple(int(v) for v in sym)
    tile(SW["simbolo"], lambda x, y: lt(mix(s, nuc, 0.3), 0.2))
    tile(SW["placa"], lambda x, y: [c * (0.85 + 0.1 * (y / 15)) for c in dk(corpo, 0.5)])
    tile(SW["acento"], lambda x, y: lt(hx(PAL[cor][5]), 0.3) if (x + y) % 7 == 0 else hx(PAL[cor][5]))
    tile(SW["interno"], metal(dk(corpo2, 0.3), 0.5))
    return Image.fromarray(T, "RGBA")
def uv(n): i = SW[n]; u, v = (i % 4) * 4, (i // 4) * 4; return [u + 0.25, v + 0.25, u + 3.75, v + 3.75]

class M:
    def __init__(self): self.el = []; self.grupos = {}
    def box(self, g, nome, a, b, tex, rot=None, luz=False, faces=None):
        f = [min(a[i], b[i]) for i in range(3)]; t = [max(a[i], b[i]) for i in range(3)]
        assert all(t[i] - f[i] > 1e-4 for i in range(3)), (nome, a, b)
        e = {"name": nome, "from": [round(v, 4) for v in f], "to": [round(v, 4) for v in t],
             "faces": {fc: {"uv": uv((faces or {}).get(fc, tex)), "texture": "#0"} for fc in ("north", "east", "south", "west", "up", "down")}}
        if rot:
            ax, ang, org = rot; assert ang in (-45, -22.5, 0, 22.5, 45), ang
            if ang: e["rotation"] = {"angle": ang, "axis": ax, "origin": [round(v, 4) for v in org]}
        if luz: e["forge_data"] = dict(LUZ)
        self.grupos.setdefault(g, []).append(len(self.el)); self.el.append(e)
    def disco(self, g, nome, r, y0, y1, tex, lados=16, luz=False):
        s = r * math.tan(math.radians(180 / lados)) * 2
        for ang in ((0, 22.5, 45, -22.5) if lados == 16 else (0, 45)):
            self.box(g, nome, (C - r, y0, C - s / 2), (C + r, y1, C + s / 2), tex, rot=("y", ang, (C, y0, C)), luz=luz)
            self.box(g, nome, (C - s / 2, y0, C - r), (C + s / 2, y1, C + r), tex, rot=("y", ang, (C, y0, C)), luz=luz)
    def perfil(self, g, nome, pts, tex, lados=16, luz=False):
        for r, y0, y1 in pts: self.disco(g, nome, r, y0, y1, tex, lados, luz)
    def radial(self, g, nome, k, r, y0, y1, w, d, tex, luz=False, extra=0.0):
        """caixa na posição angular k*22,5 graus (0 = norte/-z), a 'r' do centro"""
        base = round(k) % 16
        # escolhe a base (N/E/S/W) e a rotação permitida mais próxima
        for b4, rot in ((0, 0), (0, -22.5), (0, 22.5), (0, -45), (4, -22.5), (4, 0), (4, 22.5), (4, -45), (8, -22.5), (8, 0), (8, 22.5), (8, -45), (12, -22.5), (12, 0), (12, 22.5), (12, -45)):
            if (b4 - rot / 22.5) % 16 == base: break
        ang = math.radians(b4 * 22.5)
        cx, cz = C + r * math.sin(ang), C - r * math.cos(ang)
        if b4 in (0, 8): a, b = (cx - w / 2, y0, cz - d / 2), (cx + w / 2, y1, cz + d / 2)
        else: a, b = (cx - d / 2, y0, cz - w / 2), (cx + d / 2, y1, cz + w / 2)
        self.box(g, nome, a, b, tex, rot=("y", rot, (C, y0, C)), luz=luz)

def simbolo(m_, M_, cor, cy, r, px):
    mk, sym, bg = extrair(cor)
    L = 16 * px
    for sinal in (-1, 1):
        z = C + sinal * r
        M_.box("emblema", "placa", (C - L / 2 - 0.2, cy - L / 2 - 0.2, min(z, z + sinal * 0.12)), (C + L / 2 + 0.2, cy + L / 2 + 0.2, max(z, z + sinal * 0.12)), "placa",
               faces={"north" if sinal < 0 else "south": "placa"})
        for (x0, x1, y0, y1) in ((-L / 2 - 0.35, L / 2 + 0.35, L / 2 + 0.2, L / 2 + 0.35), (-L / 2 - 0.35, L / 2 + 0.35, -L / 2 - 0.35, -L / 2 - 0.2),
                                 (-L / 2 - 0.35, -L / 2 - 0.2, -L / 2 - 0.2, L / 2 + 0.2), (L / 2 + 0.2, L / 2 + 0.35, -L / 2 - 0.2, L / 2 + 0.2)):
            M_.box("emblema", "moldura", (C + x0, cy + y0, min(z, z + sinal * 0.22)), (C + x1, cy + y1, max(z, z + sinal * 0.22)), "borda")
        used = np.zeros_like(mk)
        for rr in range(16):
            c = 0
            while c < 16:
                if mk[rr, c] and not used[rr, c]:
                    c1 = c
                    while c1 < 16 and mk[rr, c1] and not used[rr, c1]: c1 += 1
                    r1 = rr + 1
                    while r1 < 16 and mk[r1, c:c1].all() and not used[r1, c:c1].any(): r1 += 1
                    used[rr:r1, c:c1] = True
                    # olhando de fora: na frente (norte) a coluna 0 fica no +x... (lado esquerdo de quem olha é -x? norte olha para +z)
                    xa, xb = (C + L / 2 - c * px, C + L / 2 - c1 * px) if sinal < 0 else (C - L / 2 + c * px, C - L / 2 + c1 * px)
                    ya, yb = cy + L / 2 - rr * px, cy + L / 2 - r1 * px
                    z0, z1 = z + sinal * 0.12, z + sinal * 0.24
                    M_.box("emblema", "simbolo", (min(xa, xb), min(ya, yb), min(z0, z1)), (max(xa, xb), max(ya, yb), max(z0, z1)), "simbolo", luz=True)
                    c = c1
                else: c += 1
    return sym

def arco(M_, g, cx, cy, R, w, d, tex, luz=False, passos=range(0, 9)):
    """alça: arco no plano x-y, segmentos centrados em 0, 22,5 ... 180 graus (rotações permitidas)"""
    seg = 2 * R * math.tan(math.radians(11.25)) + w * 0.4
    for k in passos:
        th = k * 22.5; t = math.radians(th)
        px, py = cx + R * math.cos(t), cy + R * math.sin(t)
        if th <= 45 or th >= 135:   # trecho mais vertical: barra em pé girada
            rot = th if th <= 45 else th - 180
            a, b = (px - w / 2, py - seg / 2, C - d / 2), (px + w / 2, py + seg / 2, C + d / 2)
        else:                       # trecho mais horizontal
            rot = th - 90
            a, b = (px - seg / 2, py - w / 2, C - d / 2), (px + seg / 2, py + w / 2, C + d / 2)
        M_.box(g, "alca", a, b, tex, rot=("z", rot, (px, py, C)), luz=luz)

def construir(cor):
    m = M()
    # pé
    m.perfil("pe", "pe", [(5.4, 0, 0.7)], "corpo"); m.perfil("pe", "degrau", [(4.8, 0.7, 1.4)], "borda"); m.perfil("pe", "pescoco", [(3.4, 1.4, 2.3)], "corpo2")
    m.perfil("pe", "colar", [(3.8, 1.7, 1.95)], "borda")
    for k in range(0, 16, 2): m.radial("pe", "pezinho", k + 1, 5.05, 0, 0.35, 0.7, 0.7, "borda")
    # boca larga de baixo
    m.perfil("boca", "boca_baixo", [(4.3, 2.3, 2.7), (6.0, 2.7, 3.25)], "corpo2"); m.perfil("boca", "aro_baixo", [(6.2, 2.9, 3.1)], "borda")
    m.perfil("boca", "aro_baixo2", [(4.6, 3.25, 3.55)], "corpo")
    # globo de vidro e núcleo
    m.perfil("globo", "vidro", [(3.3, 3.55, 4.0), (3.9, 4.0, 4.5), (4.4, 4.5, 5.1), (4.8, 5.1, 8.2), (4.4, 8.2, 8.8), (3.9, 8.8, 9.3), (3.3, 9.3, 9.75)], "vidro")
    m.perfil("nucleo", "nucleo", [(2.2, 4.4, 8.9)], "nucleo", luz=True); m.perfil("nucleo", "nucleo2", [(1.4, 3.6, 9.6)], "nucleo", luz=True)
    # gaiola: 8 costelas
    for k in range(1, 16, 2):
        for (r, y0, y1) in ((3.5, 3.55, 4.1), (4.1, 4.0, 4.6), (4.6, 4.5, 5.2), (4.95, 5.1, 8.3), (4.6, 8.2, 8.9), (4.1, 8.8, 9.4), (3.5, 9.3, 9.75)):
            m.radial("gaiola", "costela", k, r, y0, y1, 0.45, 0.35, "corpo2")
        m.radial("gaiola", "costela_luz", k, 5.15, 5.3, 8.1, 0.12, 0.1, "energia", luz=True)
    # emblema
    sym = simbolo(None, m, cor, 6.65, 4.85, 0.19)
    # boca larga de cima, pescoço, tampa, haste
    m.perfil("boca", "boca_cima", [(4.6, 9.75, 10.05), (6.0, 10.05, 10.6), (4.3, 10.6, 11.0)], "corpo2"); m.perfil("boca", "aro_cima", [(6.2, 10.2, 10.4)], "borda")
    m.perfil("tampa", "pescoco2", [(3.4, 11.0, 11.6)], "corpo"); m.perfil("tampa", "tampa", [(4.3, 11.6, 12.0), (3.5, 12.0, 12.4), (2.6, 12.4, 12.8), (1.6, 12.8, 13.2)], "corpo2")
    m.perfil("tampa", "friso", [(4.45, 11.72, 11.86)], "energia", luz=True)
    for k in range(1, 16, 2): m.radial("tampa", "respiro", k, 3.62, 12.08, 12.3, 0.6, 0.1, "energia", luz=True)
    m.perfil("haste", "haste", [(0.6, 13.2, 14.2)], "corpo", 8); m.perfil("haste", "colar", [(0.9, 13.6, 13.8)], "borda", 8)
    # alça (presa nos lados da tampa)
    for sg in (-1, 1):
        m.box("alca", "dobradica", (C + sg * 4.0, 11.5, C - 0.5), (C + sg * 4.6, 12.3, C + 0.5), "borda")
    arco(m, "alca", C, 11.9, 4.35, 0.45, 0.6, "corpo2")
    arco(m, "alca", C, 11.9, 4.35, 0.12, 0.66, "energia", luz=True, passos=range(1, 8))
    # remate e detalhes de cada Tropa
    if cor == "verde":
        m.perfil("remate", "remate", [(0.9, 14.2, 14.7)], "corpo2", 8); m.perfil("remate", "luz", [(0.45, 14.7, 15.3)], "nucleo", 8, luz=True)
    elif cor == "amarelo":
        for i, (r, y0, y1) in enumerate(((0.8, 14.2, 14.6), (0.55, 14.6, 15.1), (0.3, 15.1, 15.6), (0.12, 15.6, 15.95))):
            m.perfil("remate", "espinho", [(r, y0, y1)], "corpo2" if i < 3 else "nucleo", 8, luz=i == 3)
        for k in range(1, 16, 2):
            for y in (3.0, 10.3):
                m.radial("detalhes", "ponta", k, 5.95, y - 0.15, y + 0.15, 0.3, 0.6, "acento")
                m.radial("detalhes", "ponta", k, 6.4, y - 0.08, y + 0.08, 0.15, 0.4, "acento")
    elif cor == "vermelho":
        m.perfil("remate", "remate", [(0.9, 14.2, 14.5)], "corpo2", 8)
        for k in range(0, 16, 4): m.radial("remate", "chifre", k, 0.7, 14.5, 15.3, 0.25, 0.25, "corpo2")
        m.perfil("remate", "luz", [(0.35, 14.5, 15.1)], "nucleo", 8, luz=True)
        for k in range(0, 16, 2):
            m.radial("detalhes", "chifre", k + 1, 5.3, 10.6, 11.3, 0.35, 0.35, "corpo2")
            m.radial("detalhes", "pinga", k, 5.2, 2.2 - (k % 3) * 0.3, 2.75, 0.18, 0.18, "acento", luz=True)
    elif cor == "laranja":
        m.perfil("remate", "cebola", [(0.8, 14.2, 14.45), (1.05, 14.45, 14.85), (0.7, 14.85, 15.15), (0.35, 15.15, 15.45)], "corpo2", 8)
        m.box("remate", "gema", (C - 0.25, 15.45, C - 0.25), (C + 0.25, 15.95, C + 0.25), "acento", rot=("y", 45, (C, 15.45, C)), luz=True)
        for k in range(16):
            for y in (3.05, 10.25):
                if k % 2 == 0: m.radial("detalhes", "gema", k, 5.75, y - 0.18, y + 0.18, 0.36, 0.25, "acento", luz=True)
    elif cor == "azul":
        m.perfil("remate", "remate", [(0.8, 14.2, 14.5)], "corpo2", 8)
        m.box("remate", "estrela", (C - 0.15, 14.5, C - 0.15), (C + 0.15, 16.0, C + 0.15), "nucleo", luz=True, rot=("y", 45, (C, 14.5, C)))
        m.box("remate", "estrela", (C - 0.9, 15.05, C - 0.12), (C + 0.9, 15.3, C + 0.12), "nucleo", luz=True)
        m.box("remate", "estrela", (C - 0.12, 15.05, C - 0.9), (C + 0.12, 15.3, C + 0.9), "nucleo", luz=True)
        for sg in (-1, 1):   # asinhas na boca de cima
            for i in range(3):
                L = 1.8 - i * 0.4
                m.box("detalhes", "pena", (C + sg * 5.4, 10.4 + i * 0.25, C - 0.7 + i * 0.1), (C + sg * (5.4 + L), 10.62 + i * 0.25, C + 0.7 - i * 0.1),
                      "borda" if i % 2 == 0 else "corpo2", rot=("z", 22.5 * sg, (C + sg * 5.4, 10.5, C)))
    elif cor == "violeta":
        m.perfil("remate", "remate", [(0.8, 14.2, 14.45)], "corpo2", 8)
        for sg in (-1, 1):
            m.box("remate", "coracao", (C + sg * 0.12 - 0.42, 14.5, C - 0.25), (C + sg * 0.12 + 0.42, 15.4, C + 0.25), "acento", luz=True, rot=("z", 45 * -sg, (C, 14.5, C)))
        for k in range(0, 16, 2):
            m.radial("detalhes", "petala", k + 1, 5.6, 1.9, 3.0, 0.8, 0.22, "acento", luz=True)
            m.radial("detalhes", "petala", k + 1, 5.6, 10.6, 11.7, 0.8, 0.22, "acento", luz=True)
    elif cor == "preto":   # lanterna negra: ossos na gaiola e pequenas caveiras nas laterais
        m.perfil("remate", "remate", [(0.8, 14.2, 14.5)], "corpo2", 8); m.perfil("remate", "luz", [(0.4, 14.5, 15.0)], "nucleo", 8, luz=True)
        for k in range(1, 16, 2):
            for y in (3.6, 9.5):
                m.radial("detalhes", "osso", k, 5.0, y - 0.1, y + 0.3, 0.7, 0.45, "acento")
        for sg in (-1, 1):
            x = C + sg * 5.9
            m.box("detalhes", "caveira", (x - 0.45, 6.1, C - 0.5), (x + 0.45, 7.1, C + 0.5), "acento")
            m.box("detalhes", "queixo", (x - 0.3, 5.75, C - 0.35), (x + 0.3, 6.1, C + 0.35), "acento")
            for dz in (-0.25, 0.25):
                m.box("detalhes", "olho", (x + sg * 0.42 - 0.03, 6.5, C + dz - 0.13), (x + sg * 0.42 + 0.03, 6.75, C + dz + 0.13), "nucleo", luz=True)
            m.box("detalhes", "suporte", (C + sg * 4.9, 6.4, C - 0.15), (x - sg * 0.4, 6.8, C + 0.15), "corpo2")
    groups = [{"name": g, "origin": [8, 8, 8], "color": 0, "children": v} for g, v in m.grupos.items()]
    model = {"credit": "Bateria de mão no formato de lanterna (alta definição)", "render_type": "minecraft:translucent", "texture_size": [64, 64],
             "textures": {"0": f"lanterna:block/battery/{ARQ[cor]}", "particle": f"lanterna:block/battery/{ARQ[cor]}"},
             "elements": m.el, "groups": groups,
             "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -1.0, 0], "scale": [0.55, 0.55, 0.55]},
                         "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]}, "fixed": {"scale": [0.5, 0.5, 0.5]},
                         "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                         "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
                         "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]}}}
    return model, textura(cor, sym)

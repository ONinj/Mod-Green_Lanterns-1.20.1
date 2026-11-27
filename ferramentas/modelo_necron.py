"""
O Necron em alta definição (0.58.1): gera geo/entity/necron.geo.json, textures/entity/necron.png e o _glowmask.

Diferente do modelos_saga.py (box UV, 1 pixel por unidade), aqui cada face de cada cubo tem a sua área na textura,
com 3 a 6 pixels por unidade, e a pintura é feita em 3D: cada pixel sabe em que ponto do corpo ele está. Assim as
rachaduras do crânio, os restos de pele e as dobras da túnica continuam de um cubo para o outro, como num modelo de
verdade. O modelo é montado com muitos cubos pequenos: crânio arredondado em camadas, arcos das órbitas, maçãs do
rosto, cavidade do nariz, dentes um a um, mandíbula com os ramos, vértebras do pescoço, costelas, falanges, a foice
com a lâmina chanfrada e a pérola presa por garras.

Os nomes e os pivôs dos ossos são os mesmos de antes (a cena e a entidade mexem neles pelo código):
root, corpo, saia, farrapo_1..12, cabeca, mandibula, capuz, ombro_d/e → braco_d/e → antebraco_d/e → mao_d/e (foice, caveira) → dedos_d/e,
 perna_d/e → canela_d/e → pe_d/e, perola.

Rodar na raiz do projeto:  python3 ferramentas/modelo_necron.py
"""
import json
import math
import os
import random

from PIL import Image

RAIZ = os.path.join("src", "main", "resources", "assets", "lanterna")
ANG_SINAL = -1


# ============================== ruído ==============================

def _h(*v):
    r = random.Random(hash(v) & 0xFFFFFFFF)
    return r.random()


def _hash3(x, y, z, s):
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (s * 2654435761)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xFFFFFF) / float(0xFFFFFF)


def ruido3(x, y, z, esc, s=0):
    x, y, z = x / esc, y / esc, z / esc
    x0, y0, z0 = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - x0, y - y0, z - z0
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    def c(i, j, k):
        return _hash3(x0 + i, y0 + j, z0 + k, s)
    a = c(0, 0, 0) * (1 - fx) + c(1, 0, 0) * fx
    b = c(0, 1, 0) * (1 - fx) + c(1, 1, 0) * fx
    cc = c(0, 0, 1) * (1 - fx) + c(1, 0, 1) * fx
    d = c(0, 1, 1) * (1 - fx) + c(1, 1, 1) * fx
    return (a * (1 - fy) + b * fy) * (1 - fz) + (cc * (1 - fy) + d * fy) * fz


def fbm(x, y, z, esc, s=0, oct=3):
    t, amp, tot = 0.0, 1.0, 0.0
    for o in range(oct):
        t += ruido3(x, y, z, esc / (2 ** o), s + o * 17) * amp
        tot += amp
        amp *= 0.5
    return t / tot


def mistura(c1, c2, k):
    k = max(0.0, min(1.0, k))
    return tuple(int(c1[i] + (c2[i] - c1[i]) * k) for i in range(3))


def rgba(c, a=255):
    return (max(0, min(255, int(c[0]))), max(0, min(255, int(c[1]))), max(0, min(255, int(c[2]))), a)


def esc(c, k):
    return tuple(int(x * k) for x in c)


def dist_seg(p, a, b):
    ax, ay, az = a
    bx, by, bz = b
    px, py, pz = p
    vx, vy, vz = bx - ax, by - ay, bz - az
    wx, wy, wz = px - ax, py - ay, pz - az
    t = max(0.0, min(1.0, (wx * vx + wy * vy + wz * vz) / max(1e-9, vx * vx + vy * vy + vz * vz)))
    dx, dy, dz = ax + vx * t - px, ay + vy * t - py, az + vz * t - pz
    return math.sqrt(dx * dx + dy * dy + dz * dz)


# ============================== modelo com UV por face ==============================

FACES = ("north", "south", "east", "west", "up", "down")
# o que brilharia com o _glowmask. Vazio: o brilho automático do GeckoLib tira esses pixels da textura comum e, no
# teste, desenhava preto no lugar (os olhos e a pérola ficavam pretos). As cores claras ficam na textura comum.
BRILHAM = ()
DEBUG = os.environ.get("NEC_DEBUG") == "1"


class Modelo:
    def __init__(self, nome):
        self.nome = nome
        self.ossos = []
        self.por_nome = {}
        self.cubos = []   # (dict json, origem, tam, material, densidade)

    def osso(self, nome, pai=None, pivo=(0, 0, 0), rot=None):
        o = {"name": nome, "pivot": [round(float(x), 4) for x in pivo]}
        if pai:
            o["parent"] = pai
        if rot:
            o["rotation"] = [float(x) for x in rot]
        self.ossos.append(o)
        self.por_nome[nome] = o
        return nome

    def cubo(self, osso, origem, tam, mat, rot=None, pivo=None, inflar=0, D=4):
        c = {"origin": [round(float(x), 4) for x in origem], "size": [round(float(x), 4) for x in tam]}
        if inflar:
            c["inflate"] = inflar
        if rot:
            c["rotation"] = [round(float(x), 3) for x in rot]
            c["pivot"] = [round(float(x), 4) for x in (pivo or origem)]
        self.por_nome[osso].setdefault("cubes", []).append(c)
        self.cubos.append((c, tuple(origem), tuple(tam), mat, D))
        # o que brilha vira duas peças: a de baixo sem brilho (sempre aparece) e uma casca fina por cima só com o
        # brilho (o GeckoLib tira da textura comum os pixels que brilham; sem a casca, onde o brilho não é desenhado
        # o lugar ficava preto)
        if mat in BRILHAM:
            g = {"origin": c["origin"], "size": c["size"], "inflate": round(inflar + 0.015, 3)}
            if rot:
                g["rotation"], g["pivot"] = c["rotation"], c["pivot"]
            self.por_nome[osso]["cubes"].append(g)
            self.cubos.append((g, tuple(origem), tuple(tam), mat + "_g", D))
        return c

    # ---------- empacotar e pintar ----------
    def _rects(self):
        rs = []
        for i, (c, o, t, mat, D) in enumerate(self.cubos):
            w, h, d = t
            dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
            for f in FACES:
                fw, fh = dims[f]
                rs.append((i, f, max(1, int(math.ceil(fw * D - 1e-6))), max(1, int(math.ceil(fh * D - 1e-6)))))
        return rs

    def _empacotar(self, rects, tam):
        ordem = sorted(rects, key=lambda r: (-r[3], -r[2]))
        x = y = linha = 0
        pos = {}
        for (i, f, w, h) in ordem:
            if x + w > tam:
                x = 0
                y += linha
                linha = 0
            if y + h > tam:
                return None
            pos[(i, f)] = (x, y, w, h)
            x += w
            linha = max(linha, h)
        return pos

    @staticmethod
    def _ponto(o, t, f, uu, vv):
        """O ponto 3D (sem a rotação do cubo) de um pixel da face (uu, vv em 0..1; v para baixo)."""
        ox, oy, oz = o
        w, h, d = t
        if f == "north":
            return (ox + w * (1 - uu), oy + h * (1 - vv), oz), (0, 0, -1)
        if f == "south":
            return (ox + w * uu, oy + h * (1 - vv), oz + d), (0, 0, 1)
        if f == "west":
            return (ox, oy + h * (1 - vv), oz + d * uu), (-1, 0, 0)
        if f == "east":
            return (ox + w, oy + h * (1 - vv), oz + d * (1 - uu)), (1, 0, 0)
        if f == "up":
            return (ox + w * uu, oy + h, oz + d * vv), (0, 1, 0)
        return (ox + w * uu, oy, oz + d * (1 - vv)), (0, -1, 0)

    def salvar(self, pintores, brilhos, vis_w=5, vis_h=6):
        rects = self._rects()
        for tam in (1024, 2048, 4096):
            pos = self._empacotar(rects, tam)
            if pos:
                break
        else:
            raise RuntimeError("textura grande demais")
        # altura usada (para não deixar a textura cheia de vazio)
        altura = max(y + h for (x, y, w, h) in pos.values())
        # quadrada: o GeckoLib lê as UVs por face como se a textura fosse quadrada
        tw = th = tam
        for i, (c, o, t, mat, D) in enumerate(self.cubos):
            uv = {}
            for f in FACES:
                x, y, w, h = pos[(i, f)]
                uv[f] = {"uv": [x, y], "uv_size": [w, h]}
            c["uv"] = uv
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.nome, "texture_width": tw, "texture_height": th,
                            "visible_bounds_width": vis_w, "visible_bounds_height": vis_h, "visible_bounds_offset": [0, vis_h / 2, 0]},
            "bones": self.ossos}]}
        with open(os.path.join(RAIZ, "geo", "entity", self.nome + ".geo.json"), "w") as f:
            json.dump(geo, f, separators=(",", ":"))
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        px, gx = img.load(), glow.load()
        for i, (c, o, t, mat, D) in enumerate(self.cubos):
            for f in FACES:
                x, y, w, h = pos[(i, f)]
                for yy in range(h):
                    for xx in range(w):
                        P, N = self._ponto(o, t, f, (xx + 0.5) / w, (yy + 0.5) / h)
                        cor = pintores[mat](P, N, f, (xx + 0.5) / w, (yy + 0.5) / h, w, h)
                        if cor is not None:
                            px[x + xx, y + yy] = cor
                        if mat in brilhos:
                            g = brilhos[mat](P, N, f, (xx + 0.5) / w, (yy + 0.5) / h, w, h)
                            if g is not None:
                                gx[x + xx, y + yy] = g
        # o GeckoLib não aceita uma máscara de brilho vazia: um pixel num canto que nenhuma face usa
        if glow.getbbox() is None:
            glow.putpixel((tw - 1, th - 1), (1, 1, 1, 255))
        img.save(os.path.join(RAIZ, "textures", "entity", self.nome + ".png"), optimize=True)
        glow.save(os.path.join(RAIZ, "textures", "entity", self.nome + "_glowmask.png"), optimize=True)
        print("necron: %d cubos, textura %dx%d" % (len(self.cubos), tw, th))


# ============================== cores e pintores ==============================

OSSO = (222, 212, 188)
OSSO_MEIO = (176, 162, 136)
OSSO_ESC = (104, 90, 74)
CARNE = (104, 34, 34)
CARNE_ESC = (52, 14, 18)
PELE = (122, 96, 80)
PELE_ESC = (66, 46, 40)
PANO = (24, 22, 30)
PANO_CLARO = (54, 48, 66)
METAL = (62, 64, 74)
FIO = (210, 214, 226)
HASTE = (36, 28, 24)

# as rachaduras do crânio (segmentos em 3D, sobre a superfície)
RACHAS = [
    ((-0.6, 38.2, -3.3), (-1.4, 36.6, -3.8)), ((-1.4, 36.6, -3.8), (-1.1, 35.4, -3.9)),
    ((-0.6, 38.2, -3.3), (0.4, 38.6, -1.2)), ((0.4, 38.6, -1.2), (0.0, 38.7, 1.4)), ((0.0, 38.7, 1.4), (-0.8, 38.4, 3.0)),
    ((0.4, 38.6, -1.2), (2.0, 38.2, -0.4)), ((2.0, 38.2, -0.4), (3.3, 37.0, 0.2)), ((3.3, 37.0, 0.2), (3.6, 35.6, -0.8)),
    ((-1.4, 36.6, -3.8), (-2.6, 36.9, -3.2)), ((-2.6, 36.9, -3.2), (-3.5, 36.0, -1.8)),
    ((-0.8, 38.4, 3.0), (-2.4, 37.2, 3.4)), ((2.6, 33.0, -3.2), (3.4, 34.4, -2.2)),
]
# onde ainda há pele grudada (centro, raio)
PELES = [((-3.0, 33.4, -2.4), 1.7), ((-2.2, 37.6, 1.8), 1.9), ((2.9, 31.6, -2.0), 1.2), ((-2.6, 30.4, -1.8), 1.1), ((1.0, 38.6, 2.6), 1.2)]


def p_osso(P, N, f, u, v, w, h):
    x, y, z = P
    n = fbm(x, y, z, 1.6, 3)
    c = mistura(OSSO_MEIO, OSSO, 0.35 + n * 0.75)
    # poros e manchas
    if ruido3(x, y, z, 0.22, 9) > 0.86:
        c = esc(c, 0.78)
    if fbm(x, y, z, 1.1, 5) > 0.66:
        c = mistura(c, (150, 128, 96), 0.45)   # amarelado de velho
    if N[1] < 0:
        c = esc(c, 0.72)
    elif N[1] == 0:
        c = esc(c, 0.92)
    # bordas mais escuras
    if u < 0.08 or u > 0.92 or v < 0.08 or v > 0.92:
        c = esc(c, 0.88)
    return rgba(c)


def _racha(P):
    x, y, z = P
    jx = (ruido3(x, y, z, 0.35, 41) - 0.5) * 0.35
    jy = (ruido3(x, y, z, 0.35, 42) - 0.5) * 0.35
    q = (x + jx, y + jy, z)
    return min(dist_seg(q, a, b) for a, b in RACHAS)


def _pele(P):
    x, y, z = P
    k = 0.0
    for (c, r) in PELES:
        d = math.dist(P, c) / r
        k = max(k, 1 - d)
    return k + (fbm(x, y, z, 0.7, 51) - 0.5) * 0.6


def p_cranio(P, N, f, u, v, w, h):
    x, y, z = P
    d = _racha(P)
    if d < 0.07:
        return rgba((34, 24, 20))
    if d < 0.15:
        return rgba(esc(p_osso(P, N, f, u, v, w, h)[:3], 0.6))
    pk = _pele(P)
    if pk > 0.32:
        # pele seca, repuxada, com rasgos e veias
        c = mistura(PELE_ESC, PELE, fbm(x, y, z, 0.5, 52))
        if pk < 0.4:
            c = mistura(c, CARNE_ESC, 0.6)   # borda rasgada da pele
        if ruido3(x, y, z, 0.18, 53) > 0.82:
            c = mistura(c, CARNE, 0.6)
        return rgba(c)
    if pk > 0.22:
        return rgba(mistura(CARNE_ESC, CARNE, fbm(x, y, z, 0.4, 54)))
    c = p_osso(P, N, f, u, v, w, h)[:3]
    # a sombra funda das têmporas
    if abs(x) > 2.6 and 32.0 < y < 35.0 and z < 0:
        c = esc(c, 0.8)
    return rgba(c)


def p_cranio_glow(P, N, f, u, v, w, h):
    return None


def p_orbita(P, N, f, u, v, w, h):
    return rgba((200, 0, 0)) if DEBUG else rgba((6, 3, 8))


def g_orbita(P, N, f, u, v, w, h):
    if f != "north":
        return None
    d = math.hypot(u - 0.5, v - 0.55) * 2
    k = max(0.0, 1 - d)
    return rgba((int(110 * k + 14), int(160 * k + 12), int(235 * k + 20)))


def p_pupila(P, N, f, u, v, w, h):
    # a brasa fria no fundo da órbita: clara no meio, azulada na borda
    d = math.hypot(u - 0.5, v - 0.5) * 2
    return rgba(mistura((236, 248, 255), (70, 150, 230), d))


def g_pupila(P, N, f, u, v, w, h):
    return rgba((200, 235, 255))


def p_buraco(P, N, f, u, v, w, h):
    return rgba((14, 8, 10))


def p_dente(P, N, f, u, v, w, h):
    x, y, z = P
    c = mistura((176, 150, 104), (236, 228, 204), fbm(x, y, z, 0.3, 61) * 0.6 + 0.45)
    if f in ("east", "west", "south"):
        c = esc(c, 0.7)
    if v < 0.15:
        c = mistura(c, (90, 40, 36), 0.6)   # gengiva seca
    if ruido3(x, y, z, 0.15, 62) > 0.9:
        c = (100, 76, 50)                  # cárie
    return rgba(c)


def p_carne(P, N, f, u, v, w, h):
    x, y, z = P
    c = mistura(CARNE_ESC, CARNE, fbm(x, y, z, 0.5, 71))
    # fibras do músculo (no comprimento)
    if (int(u * w) + int(ruido3(x, y, z, 0.4, 72) * 3)) % 3 == 0:
        c = esc(c, 0.75)
    if ruido3(x, y, z, 0.25, 73) > 0.85:
        c = mistura(c, (150, 80, 66), 0.6)   # tendão claro
    return rgba(c)


def p_pele(P, N, f, u, v, w, h):
    x, y, z = P
    c = mistura(PELE_ESC, PELE, fbm(x, y, z, 0.4, 81))
    if u < 0.12 or u > 0.88 or v > 0.85:
        c = mistura(c, CARNE_ESC, 0.5)
    return rgba(c)


def p_cabelo(P, N, f, u, v, w, h):
    x, y, z = P
    return rgba(mistura((24, 22, 24), (70, 64, 66), ruido3(x, y, z, 0.15, 91)))


def p_pano(P, N, f, u, v, w, h):
    x, y, z = P
    n = fbm(x, y, z, 1.4, 101)
    c = mistura(PANO, PANO_CLARO, n * 0.75)
    # trama do tecido
    if (int(u * w) + int(v * h)) % 2 == 0:
        c = esc(c, 0.93)
    # dobras verticais (seguem o corpo: dependem de x e z)
    dobra = math.sin((x * 1.6 + z * 1.1) + fbm(x, y * 0.2, z, 1.0, 102) * 4)
    if dobra > 0.75:
        c = mistura(c, PANO_CLARO, 0.35)
    elif dobra < -0.7:
        c = esc(c, 0.7)
    # gasto e sujo embaixo
    if y < 6:
        c = mistura(c, (42, 34, 30), (6 - y) / 8)
    # rasgos de verdade (buracos) e fios roxos
    if f in ("north", "south", "east", "west") and fbm(x, y, z, 0.8, 103) > 0.74 and 0.1 < v < 0.9:
        return (0, 0, 0, 0)
    if ruido3(x, y, z, 0.2, 104) > 0.94:
        c = mistura(c, (110, 70, 140), 0.5)
    return rgba(c)


def p_farrapo(P, N, f, u, v, w, h):
    x, y, z = P
    # a ponta esfiapada: cada fio termina numa altura
    corte = 0.72 + ruido3(x * 3, 0, z * 3, 0.5, 111) * 0.26
    if v > corte:
        return (0, 0, 0, 0)
    c = p_pano(P, N, f, u, v * 0.7, w, h)
    if c[3] == 0:
        c = rgba(PANO)
    return c


def p_corda(P, N, f, u, v, w, h):
    x, y, z = P
    t = (u * w + v * h * 0.7) % 3
    return rgba(mistura((40, 30, 24), (92, 74, 54), t / 3))


def p_metal(P, N, f, u, v, w, h):
    x, y, z = P
    c = mistura(esc(METAL, 0.6), METAL, fbm(x, y, z, 0.8, 121))
    # riscos finos ao longo da lâmina
    if ruido3(x * 4, y, z * 0.3, 0.3, 122) > 0.86:
        c = mistura(c, (120, 124, 136), 0.6)
    if fbm(x, y, z, 0.5, 123) > 0.72:
        c = mistura(c, (80, 22, 24), 0.55)   # sangue seco
    return rgba(c)


def p_fio(P, N, f, u, v, w, h):
    x, y, z = P
    return rgba(mistura((150, 156, 170), FIO, ruido3(x, y, z, 0.3, 131)))


def p_haste(P, N, f, u, v, w, h):
    x, y, z = P
    # veio da madeira escura ao longo do comprimento
    veio = math.sin(x * 9 + z * 7 + fbm(x, y * 0.15, z, 1.0, 141) * 6)
    c = mistura(HASTE, (72, 56, 46), (veio + 1) / 2 * 0.6)
    return rgba(c)


def p_perola(P, N, f, u, v, w, h):
    d = math.hypot(u - 0.5, v - 0.5) * 2
    c = mistura((80, 44, 124), (14, 8, 22), d)
    if math.hypot(u - 0.32, v - 0.3) < 0.12:
        c = (236, 226, 255)
    return rgba(c)


def g_perola(P, N, f, u, v, w, h):
    d = math.hypot(u - 0.5, v - 0.5) * 2
    k = max(0.0, 1 - d)
    return rgba((int(140 * k + 30), int(30 * k + 6), int(200 * k + 44)))


def p_coracao(P, N, f, u, v, w, h):
    x, y, z = P
    c = mistura((20, 6, 28), (90, 30, 130), fbm(x, y, z, 0.4, 151))
    return rgba(c)


def g_coracao(P, N, f, u, v, w, h):
    x, y, z = P
    k = fbm(x, y, z, 0.35, 152)
    if k < 0.5:
        return None
    k = (k - 0.5) * 2
    return rgba((int(150 * k), int(40 * k), int(220 * k)))


def p_ferro(P, N, f, u, v, w, h):
    x, y, z = P
    c = mistura((34, 34, 40), (84, 84, 94), fbm(x, y, z, 0.4, 161))
    if fbm(x, y, z, 0.6, 162) > 0.65:
        c = mistura(c, (96, 54, 30), 0.6)   # ferrugem
    return rgba(c)


# ============================== a montagem ==============================

def necron():
    m = Modelo("necron")
    m.osso("root")
    m.osso("corpo", "root", (0, 16, 0))

    # ---------------- coluna, costelas, esterno, clavículas, escápulas e o coração da Morte ----------------
    for i in range(11):
        y = 16.2 + i * 1.18
        m.cubo("corpo", (-0.75, y, 0.55), (1.5, 0.85, 1.5), "osso")                      # vértebra
        m.cubo("corpo", (-0.25, y + 0.1, 2.0), (0.5, 0.55, 1.0), "osso")                 # processo espinhoso
        m.cubo("corpo", (-1.45, y + 0.15, 1.0), (0.7, 0.45, 0.6), "osso")                # processos laterais
        m.cubo("corpo", (0.75, y + 0.15, 1.0), (0.7, 0.45, 0.6), "osso")
    for i in range(7):
        y = 27.3 - i * 1.32
        rx, rz = 4.0 - 0.16 * i - (0.5 if i > 4 else 0), 2.8 - 0.08 * i
        for sinal in (-1, 1):
            n = 9 if i < 5 else 6
            for k in range(n):
                fi = math.radians(18 + 140 * k / (n - 1) * (1 if i < 5 else 0.6))
                x = sinal * rx * math.sin(fi)
                z = rz * math.cos(fi)
                y2 = y - 1.0 * (k / (n - 1))
                m.cubo("corpo", (x - 0.34, y2 - 0.22, z - 0.34), (0.68, 0.48, 0.68), "osso", D=5)
            if i < 5:  # cartilagem até o esterno
                m.cubo("corpo", (sinal * 1.3 - 0.45, y - 1.25, -2.95), (0.9, 0.4, 0.4), "osso", D=5)
    m.cubo("corpo", (-0.55, 20.6, -3.05), (1.1, 6.6, 0.55), "osso", D=5)                 # esterno
    m.cubo("corpo", (-0.75, 26.6, -3.1), (1.5, 1.2, 0.6), "osso", D=5)                   # manúbrio
    m.cubo("corpo", (-0.3, 19.6, -3.0), (0.6, 1.1, 0.45), "osso", D=5)                   # xifoide
    for sinal in (-1, 1):
        m.cubo("corpo", (min(0.7 * sinal, 4.8 * sinal), 27.7, -1.7), (4.1, 0.62, 0.62), "osso",
               rot=(0, 0, -9 * sinal), pivo=(0.7 * sinal, 28, -1.4), D=5)                # clavículas
        m.cubo("corpo", (sinal * 3.2 - 1.4, 22.5, 1.7), (2.8, 4.6, 0.5), "osso", rot=(0, 12 * sinal, 0),
               pivo=(sinal * 3.2, 25, 1.9))                                               # escápulas
    # o coração da Morte, aceso dentro do peito
    m.cubo("corpo", (-1.0, 22.6, -1.3), (2.0, 2.2, 1.8), "coracao", D=5)
    m.cubo("corpo", (-0.6, 22.2, -1.0), (1.2, 3.0, 1.2), "coracao", D=5)
    # restos de carne pendurados nas costelas
    for (x, y, z, comp) in [(-2.8, 23.0, -2.4, 2.4), (2.3, 24.4, -2.2, 1.7), (-1.7, 20.0, -2.6, 2.0),
                            (3.1, 21.0, -1.6, 1.5), (-3.4, 25.8, -1.4, 1.2), (1.2, 26.2, -2.7, 1.0)]:
        m.cubo("corpo", (x, y - comp, z), (0.5, comp, 0.3), "carne", D=5)
        m.cubo("corpo", (x + 0.1, y - comp - 0.7, z + 0.05), (0.25, 0.7, 0.2), "carne", D=5)

    # ---------------- a túnica: costas e lados em camadas, lapelas, manto nos ombros, ombreira de osso ----------------
    for k, (z0, esp) in enumerate([(2.5, 0.6), (3.0, 0.5)]):
        m.cubo("corpo", (-5.1 + k * 0.2, 16, z0), (10.2 - k * 0.4, 12.6 - k * 0.6, esp), "pano", D=3)
    for sinal in (-1, 1):
        x0 = -5.1 if sinal < 0 else 4.0
        m.cubo("corpo", (x0, 16, -2.5), (1.1, 12.6, 5.0), "pano", D=3)
        for k in range(3):   # dobras verticais da lateral
            m.cubo("corpo", (x0 + (0.9 if sinal > 0 else -0.25), 16.5 + k * 0.4, -2.0 + k * 1.6), (0.35, 11.0 - k * 1.2, 0.7), "pano", D=3)
        m.cubo("corpo", (sinal * 3.8 - 1.2, 25.6, -3.4), (2.4, 2.9, 6.8), "pano", D=3)  # lapela
        m.cubo("corpo", (sinal * 4.2 - 0.8, 18.4, -3.55), (1.6, 7.4, 0.6), "farrapo", D=3)
    m.cubo("corpo", (-6.3, 26.4, -3.6), (12.6, 2.3, 7.6), "pano", D=3)                   # manto nos ombros
    m.cubo("corpo", (-6.6, 27.9, -3.2), (13.2, 0.9, 6.8), "pano", D=3)
    for k in range(9):   # a borda do manto, rasgada
        x = -6.2 + k * 1.45
        comp = 0.8 + _h(k, 3) * 1.6
        m.cubo("corpo", (x, 26.4 - comp, -3.65), (1.3, comp, 0.3), "farrapo", D=3)
        m.cubo("corpo", (x, 26.4 - comp, 3.85), (1.3, comp, 0.3), "farrapo", D=3)
    # ombreira de osso no ombro esquerdo: um crânio pequeno e garras
    m.cubo("corpo", (5.0, 27.6, -1.8), (2.4, 2.2, 3.6), "osso", D=5)
    m.cubo("corpo", (5.4, 29.6, -1.4), (1.8, 0.6, 2.8), "osso", D=5)
    m.cubo("corpo", (5.6, 28.0, -2.2), (1.6, 1.4, 0.5), "cranio", D=6)
    for k in range(3):
        m.cubo("corpo", (5.6 + k * 0.5, 27.9, -2.35), (0.25, 0.3, 0.2), "buraco", D=6)
        m.cubo("corpo", (6.6 + k * 0.3, 27.2 + k * 0.5, -1.4 + k * 1.1), (0.4, 1.6, 0.4), "osso",
               rot=(0, 0, -30), pivo=(6.8, 28.4, -1.2 + k), D=5)
    # cinto de corda com correntes e um crânio pendurado
    m.cubo("corpo", (-5.6, 15.4, -3.4), (11.2, 1.1, 6.9), "corda", D=4)
    m.cubo("corpo", (-5.7, 16.4, -3.5), (11.4, 0.4, 7.1), "corda", D=4)
    for k in range(6):
        m.cubo("corpo", (-3.6 + k * 0.45, 14.9 - k * 0.55, -3.7), (0.45, 0.6, 0.25), "ferro", D=5)
    m.cubo("corpo", (-1.2, 11.2, -4.1), (1.8, 1.8, 1.6), "cranio", D=6)                 # crânio pendurado
    m.cubo("corpo", (-0.95, 11.9, -4.2), (0.5, 0.45, 0.2), "buraco", D=6)
    m.cubo("corpo", (-0.15, 11.9, -4.2), (0.5, 0.45, 0.2), "buraco", D=6)
    m.cubo("corpo", (-1.0, 10.9, -4.05), (1.4, 0.4, 1.2), "dente", D=6)
    m.cubo("corpo", (1.2, 12.6, -3.7), (0.9, 3.0, 0.8), "corda", D=4)
    m.cubo("corpo", (1.05, 11.8, -3.75), (1.2, 0.9, 1.0), "ferro", D=5)

    # ---------------- a saia longa em camadas e os farrapos ----------------
    m.osso("saia", "corpo", (0, 16, 0))
    m.cubo("saia", (-5.4, 10, -3.5), (10.8, 6, 7.3), "pano", D=3)
    # 0.59.3: a túnica se abre na frente (as pernas aparecem quando ele anda): costas, lados e duas abas na frente
    m.cubo("saia", (-6.0, 4.5, 0.6), (12.0, 5.7, 3.7), "pano", D=3)                # costas (a barra acima das canelas)
    m.cubo("saia", (-6.4, 3.0, 2.2), (12.8, 1.8, 2.4), "pano", D=3)                # barra de trás, mais comprida
    m.cubo("saia", (-6.0, 6.0, -4.0), (1.6, 4.2, 4.6), "pano", D=3)                # lados
    m.cubo("saia", (4.4, 6.0, -4.0), (1.6, 4.2, 4.6), "pano", D=3)
    m.cubo("saia", (-5.2, 5.4, -4.2), (1.9, 4.7, 0.9), "pano", D=3)                # abas da frente, rasgadas
    m.cubo("saia", (3.3, 5.0, -4.2), (1.9, 5.1, 0.9), "pano", D=3)
    m.cubo("saia", (-4.6, 4.2, -4.0), (0.8, 1.2, 0.6), "farrapo", D=4)
    m.cubo("saia", (3.9, 3.9, -4.0), (0.8, 1.1, 0.6), "farrapo", D=4)
    m.cubo("saia", (-6.1, 4.6, -2.6), (0.6, 1.4, 1.0), "farrapo", D=4)
    m.cubo("saia", (5.5, 4.9, -1.4), (0.6, 1.1, 1.0), "farrapo", D=4)
    for k in range(10):   # pregas da saia (atrás)
        x = -5.8 + k * 1.25
        m.cubo("saia", (x, 3.2, 4.6), (0.5, 7.0 - ((k + 1) % 3) * 0.8, 0.4), "pano", D=3)
    pos = [(-5.8, -4.6), (-3.2, -4.7), (-0.4, -4.8), (2.4, -4.7), (4.6, -4.6),
           (-5.6, 4.7), (-2.6, 4.8), (0.6, 4.8), (3.6, 4.7), (-6.7, -0.8), (6.0, -0.8), (6.0, 1.6)]
    for i, (x, z) in enumerate(pos, 1):
        nome = "farrapo_%d" % i
        # 0.59.3: presos na barra (mais alta) e sem passar do chão
        y0 = 6.0 if z < 0 else 4.0
        m.osso(nome, "saia", (x + 1, y0, z + 0.4))
        comp = 2.4 + (i * 7) % 4
        larg = (1.0 if z < -3 else 1.6) + (i % 3) * 0.4
        m.cubo(nome, (x, y0 - comp, z), (larg, comp, 0.5), "farrapo", D=4)
        m.cubo(nome, (x + 0.3, max(0.1, y0 - comp - 1.2), z + 0.1), (larg * 0.45, 1.2, 0.3), "farrapo", D=4)
    # 0.59.3: pernas de osso articuladas: bacia (no root) → coxa → canela → pé
    m.cubo("root", (-3.6, 13.6, -1.6), (7.2, 2.2, 3.2), "osso", D=5)                # bacia
    m.cubo("root", (-1.0, 12.8, -1.2), (2.0, 1.2, 2.2), "osso", D=5)               # sacro
    for sinal, lado in ((-1, "d"), (1, "e")):
        x = 2.6 * sinal
        m.osso("perna_" + lado, "root", (x, 14.6, 0))
        m.cubo("perna_" + lado, (x - 1.1, 13.6, -1.1), (2.2, 1.8, 2.2), "osso", D=5)        # cabeça do fêmur
        m.cubo("perna_" + lado, (x - 0.7, 8.4, -0.75), (1.4, 5.6, 1.4), "osso", D=5)        # fêmur
        m.cubo("perna_" + lado, (x - 0.95, 12.0, -1.3), (1.9, 1.5, 0.6), "farrapo", D=4)    # tiras de pano
        m.cubo("perna_" + lado, (x - 0.35, 9.6, -0.95), (0.3, 2.6, 0.3), "carne", D=6)      # tendão seco
        m.osso("canela_" + lado, "perna_" + lado, (x, 8.2, 0))
        m.cubo("canela_" + lado, (x - 1.05, 7.4, -1.15), (2.1, 1.5, 2.1), "osso", D=5)     # joelho
        m.cubo("canela_" + lado, (x - 0.55, 7.9, -1.4), (1.1, 0.9, 0.5), "osso", D=6)      # patela
        m.cubo("canela_" + lado, (x - 0.65, 2.0, -0.7), (1.0, 5.6, 1.0), "osso", D=5)      # tíbia
        m.cubo("canela_" + lado, (x + 0.35, 2.3, -0.3), (0.55, 5.0, 0.55), "osso", D=6)    # fíbula
        m.cubo("canela_" + lado, (x - 0.5, 4.0, -0.95), (0.25, 2.4, 0.25), "carne", D=6)
        m.cubo("canela_" + lado, (x - 0.9, 5.4, -1.0), (1.8, 0.8, 1.8), "corda", D=5)      # trapo amarrado
        m.osso("pe_" + lado, "canela_" + lado, (x, 1.6, 0))
        m.cubo("pe_" + lado, (x - 0.85, 0.8, -1.0), (1.7, 1.3, 1.9), "osso", D=5)          # tarso
        m.cubo("pe_" + lado, (x - 0.5, 0.6, 0.6), (1.0, 1.0, 1.0), "osso", D=5)            # calcanhar
        for k in range(4):
            fx = x - 1.0 + k * 0.55
            m.cubo("pe_" + lado, (fx, 0, -2.5), (0.4, 0.55, 1.6), "osso", D=6)             # metatarsos
            m.cubo("pe_" + lado, (fx + 0.02, 0, -3.4), (0.36, 0.42, 0.95), "osso", D=6)    # falanges

    # ---------------- a CABEÇA ----------------
    m.osso("cabeca", "corpo", (0, 28.5, 0))
    # pescoço: três vértebras com processos e um tendão seco
    for k in range(3):
        y = 28.4 + k * 0.85
        m.cubo("cabeca", (-0.75, y, -0.2), (1.5, 0.65, 1.5), "osso", D=6)
        m.cubo("cabeca", (-0.25, y + 0.1, 1.3), (0.5, 0.45, 0.8), "osso", D=6)
    m.cubo("cabeca", (0.7, 28.8, -0.6), (0.3, 2.4, 0.3), "carne", D=6)
    m.cubo("cabeca", (-1.0, 28.9, -0.4), (0.25, 2.1, 0.25), "carne", D=6)

    # crânio em camadas arredondadas (cada camada: uma placa larga e uma comprida, cantos chanfrados)
    yb, ytop = 31.2, 39.0
    camadas = 16
    for i in range(camadas):
        y0 = yb + (ytop - yb) * i / camadas
        hh = (ytop - yb) / camadas + 0.001
        t = (y0 + hh / 2 - 33.9) / (ytop - 33.9)    # 0 na altura das têmporas, 1 no topo
        f = math.sqrt(max(0.0, 1 - max(0.0, t) ** 2)) if t > 0 else 1 - 0.08 * (-t) ** 1.5
        rx = 3.55 * f
        rzf, rzb = 3.55 * f, 3.9 * f                 # a nuca é mais funda
        if rx < 0.4:
            continue
        # a frente do crânio, na altura do rosto, fica para a grade do rosto (atrás dela)
        fr = min(rzf, 3.2) if 30.9 < y0 < 36.6 else rzf
        m.cubo("cabeca", (-rx, y0, -min(rzf * 0.78, fr)), (2 * rx, hh, min(rzf * 0.78, fr) + rzb), "cranio", D=6)
        m.cubo("cabeca", (-rx * 0.8, y0, -fr), (2 * rx * 0.8, hh, fr + rzb * 1.0), "cranio", D=6)
    # o ROSTO esculpido em relevo: uma grade de 0,4 x 0,4; cada letra é uma profundidade/material
    #   R osso saltado (sobrancelha, maçãs)  B osso   E órbita funda   P a brasa no fundo da órbita
    #   N cavidade do nariz   S pele seca grudada   X osso quebrado (lasca que falta)   . nada
    rosto = [
        "...RRRRRRRRRR...",
        "..RRRRRRRRXXRR..",
        ".RRRRRRBBRXXRRR.",
        "BRREEEEBBEEEERRB",
        "BREEEEEBBEEEEERB",
        "SREEPPEBBEPPEERB",
        "SREEPPEBBEPPEERB",
        "SREEEEEBBEEEEERB",
        "SBREEEBNNBEEERBB",
        "RRRBBBNNNNBBBRRR",
        "DRRBBBNBBNBBBRRD",
        ".DBBBBBBBBBBBBD.",
        "..BBBBBBBBBBBB..",
        "...BBBBBBBBBB...",
    ]
    prof = {"R": (-4.3, "cranio"), "B": (-4.0, "cranio"), "E": (-3.25, "orbita"), "P": (-3.4, "pupila"),
            "N": (-3.3, "buraco"), "S": (-4.08, "pele"), "X": (-3.5, "buraco"), "D": (-3.75, "cranio")}
    cel = 0.4
    x0, ytopo = -3.2, 36.6
    for r, linha in enumerate(rosto):
        y = ytopo - (r + 1) * cel
        c = 0
        while c < len(linha):
            ch = linha[c]
            n = 1
            while c + n < len(linha) and linha[c + n] == ch:
                n += 1
            if ch != ".":
                zf, mat = prof[ch]
                m.cubo("cabeca", (x0 + c * cel, y, zf), (n * cel, cel, -3.15 - zf), mat, D=10 if mat in ("orbita", "pupila") else 8)
                if ch == "P":   # a brasa também acende a borda da órbita em volta
                    m.cubo("cabeca", (x0 + c * cel + 0.08, y + 0.08, zf - 0.02), (cel - 0.16, cel - 0.16, 0.04), "pupila", D=10)
            c += n
    # o arco zigomático indo para trás e a cavidade funda das têmporas
    for sinal in (-1, 1):
        m.cubo("cabeca", (sinal * 3.3 - 0.3, 32.6, -3.2), (0.6, 0.9, 2.8), "cranio", D=6)
        m.cubo("cabeca", (sinal * 3.5 - (0.1 if sinal > 0 else 0.2), 33.0, -0.9), (0.3, 1.8, 1.8), "buraco", D=6)
    # a lasca solta do osso quebrado da sobrancelha, caída por cima
    m.cubo("cabeca", (0.95, 35.95, -4.45), (0.7, 0.25, 0.5), "cranio", D=8, rot=(0, 0, -25), pivo=(1.3, 36.1, -4.2))
    # a maxila e os dentes de cima, um a um (dois faltando)
    m.cubo("cabeca", (-2.35, 30.6, -3.9), (4.7, 0.6, 2.0), "cranio", D=6)
    for k in range(10):
        if k in (2, 7):
            continue
        x = -2.15 + k * 0.44
        hk = 0.72 + (0.15 if k in (3, 6) else 0) - (0.12 if k in (0, 9) else 0)
        m.cubo("cabeca", (x, 30.6 - hk, -4.0 + abs(k - 4.5) * 0.07), (0.38, hk, 0.42), "dente", D=8)

    # restos de pele: placas finas grudadas e tiras penduradas, tendões e fios de cabelo
    m.cubo("cabeca", (-3.65, 32.0, -2.9), (0.15, 2.4, 2.2), "pele", D=6)                # bochecha esquerda
    m.cubo("cabeca", (-3.0, 30.0, -3.55), (0.9, 1.6, 0.12), "pele", D=6,
           rot=(0, 0, 12), pivo=(-2.6, 31.6, -3.5))                                          # tira pendurada
    m.cubo("cabeca", (-2.5, 29.4, -3.5), (0.35, 1.3, 0.1), "pele", D=6)
    m.cubo("cabeca", (2.75, 30.7, -2.6), (0.6, 1.4, 0.12), "pele", D=6, rot=(0, 0, -10), pivo=(3.0, 32, -2.6))
    m.cubo("cabeca", (-3.0, 30.4, -2.2), (0.22, 1.9, 0.22), "carne", D=6)              # tendão até a mandíbula
    m.cubo("cabeca", (2.95, 30.6, -2.0), (0.22, 1.6, 0.22), "carne", D=6)
    m.cubo("cabeca", (-2.6, 38.1, 0.4), (2.6, 0.18, 3.2), "pele", D=6)                 # couro cabeludo podre
    for k in range(9):
        x = -2.4 + (k % 5) * 0.45
        z = 1.0 + (k // 5) * 1.2 + _h(k, 7) * 0.5
        comp = 1.4 + _h(k, 8) * 2.4
        m.cubo("cabeca", (x, 38.2 - comp, 3.6 + _h(k, 9) * 0.3), (0.12, comp, 0.12), "cabelo", D=8)
    # o osso lascado do alto da cabeça e um buraco no parietal
    m.cubo("cabeca", (1.6, 38.6, -0.6), (1.1, 0.35, 0.8), "cranio", D=6, rot=(0, 0, 18), pivo=(2.0, 38.6, -0.2))
    m.cubo("cabeca", (-2.7, 37.0, 1.6), (0.15, 0.8, 1.0), "buraco", D=6)

    # a mandíbula: corpo, queixo, ramos subindo até a articulação e os dentes de baixo
    m.osso("mandibula", "cabeca", (0, 30.6, 0.8), (12, 0, 0))
    m.cubo("mandibula", (-2.3, 28.7, -3.75), (4.6, 0.9, 1.2), "cranio", D=6)
    m.cubo("mandibula", (-1.0, 28.2, -3.95), (2.0, 0.7, 0.9), "cranio", D=6)           # queixo
    m.cubo("mandibula", (-0.5, 28.05, -4.05), (1.0, 0.4, 0.5), "cranio", D=6)
    for sinal in (-1, 1):
        m.cubo("mandibula", (sinal * 2.35 - 0.3, 28.8, -3.2), (0.6, 0.9, 3.0), "cranio", D=6)
        m.cubo("mandibula", (sinal * 2.45 - 0.3, 29.4, -0.6), (0.6, 1.9, 0.9), "cranio", D=6)  # ramo
    for k in range(9):
        if k == 5:
            continue
        x = -1.95 + k * 0.44
        m.cubo("mandibula", (x, 29.6, -3.85 + abs(k - 4) * 0.06), (0.38, 0.6, 0.4), "dente", D=8)
    m.cubo("mandibula", (1.6, 27.4, -3.0), (0.4, 1.3, 0.12), "pele", D=6)              # pele pendurada no queixo
    m.cubo("mandibula", (-1.9, 27.8, -3.4), (0.2, 0.9, 0.2), "carne", D=6)

    # o capuz: fundo, em camadas, a borda grossa em volta do rosto e a ponta caída atrás
    m.osso("capuz", "cabeca", (0, 33, 0))
    m.cubo("capuz", (-4.9, 39.4, -5.0), (9.8, 1.2, 9.6), "pano", D=3)
    m.cubo("capuz", (-4.3, 40.5, -4.2), (8.6, 0.8, 8.0), "pano", D=3)
    m.cubo("capuz", (-4.9, 29.0, 4.2), (9.8, 10.6, 1.0), "pano", D=3)
    for sinal in (-1, 1):
        x0 = -5.4 if sinal < 0 else 4.2
        m.cubo("capuz", (x0, 29.0, -5.0), (1.2, 10.6, 9.4), "pano", D=3)
        m.cubo("capuz", (x0 + (0.9 if sinal > 0 else -0.3), 30.5, -3.0), (0.4, 8.0, 5.0), "pano", D=3)   # dobra
    # a borda do rosto: um aro grosso de pano dobrado
    for k in range(12):
        a = math.pi * k / 11
        x = -4.6 * math.cos(a)
        y = 30.2 + 9.2 * math.sin(a)
        m.cubo("capuz", (x - 0.75, y - 0.6, -5.6), (1.5, 1.3, 1.0), "pano", D=3)
    for k, (x, comp) in enumerate([(-4.6, 2.6), (-3.0, 1.4), (-1.2, 2.2), (0.6, 1.1), (2.4, 2.4), (3.8, 1.6)]):
        m.cubo("capuz", (x, 39.2 - comp, -5.85), (1.2, comp, 0.3), "farrapo", D=4)
    m.cubo("capuz", (-1.6, 39.8, 3.0), (3.2, 2.0, 3.6), "pano", D=3, rot=(-28, 0, 0), pivo=(0, 40.3, 4.2))
    m.cubo("capuz", (-1.0, 40.2, 5.6), (2.0, 1.4, 2.6), "pano", D=3, rot=(-48, 0, 0), pivo=(0, 40.6, 6.0))
    m.cubo("capuz", (-2.6, 20.5, 5.0), (5.2, 9.0, 0.6), "farrapo", D=3)                # capa nas costas
    m.cubo("capuz", (-3.8, 22.0, 4.8), (1.4, 7.0, 0.5), "farrapo", D=3)
    m.cubo("capuz", (2.4, 21.5, 4.8), (1.4, 7.5, 0.5), "farrapo", D=3)

    # ---------------- braços: manga rasgada em camadas, úmero, cotovelo, rádio e ulna, mão com falanges ----------------
    # 0.59.3: braço articulado: ombro (clavícula e ombreira) → braço (bíceps, úmero) → antebraço → mão → dedos
    for sinal, lado in ((-1, "d"), (1, "e")):
        nome = "braco_" + lado
        px = 6.3 * sinal
        m.osso("ombro_" + lado, "corpo", (3.6 * sinal, 28.4, 0))
        m.cubo("ombro_" + lado, (min(1.2 * sinal, 5.6 * sinal), 27.9, -0.5), (4.4, 0.8, 1.0), "osso", D=5)   # clavícula
        m.cubo("ombro_" + lado, (px - 2.4, 27.2, -2.6), (4.8, 1.6, 5.2), "pano", D=3)                      # ombreira
        m.cubo("ombro_" + lado, (px - 2.0, 28.6, -2.2), (4.0, 0.7, 4.4), "farrapo", D=4)
        m.osso(nome, "ombro_" + lado, (px, 27.5, 0))
        m.cubo(nome, (px - 1.05, 26.4, -1.05), (2.1, 2.1, 2.1), "osso", D=5)              # cabeça do úmero
        m.cubo(nome, (px - 2.1, 22.0, -2.3), (4.2, 5.0, 4.6), "pano", D=3)               # manga
        for k in range(5):
            comp = 1.6 + _h(k, sinal, 11) * 3.2
            ang = k * 72
            dx, dz = 1.9 * math.cos(math.radians(ang)), 2.0 * math.sin(math.radians(ang))
            m.cubo(nome, (px + dx - 0.6, 22.0 - comp, dz - 0.15), (1.2, comp, 0.3), "farrapo", D=4)
        m.cubo(nome, (px - 0.65, 19.8, -0.65), (1.3, 6.8, 1.3), "osso", D=5)             # úmero
        m.cubo(nome, (px - 0.4, 21.5, -0.95), (0.3, 3.4, 0.3), "carne", D=6)             # bíceps seco
        ante = "antebraco_" + lado
        m.osso(ante, nome, (px, 19.5, 0))
        m.cubo(ante, (px - 0.95, 18.9, -0.95), (1.9, 1.3, 1.9), "osso", D=5)             # cotovelo
        m.cubo(ante, (px - 0.35, 18.4, 0.55), (0.7, 0.8, 0.6), "osso", D=5)              # olécrano
        m.cubo(ante, (px - 0.78, 13.6, -0.62), (0.62, 5.6, 0.62), "osso", D=5)           # rádio
        m.cubo(ante, (px + 0.1, 13.6, -0.1), (0.6, 5.6, 0.6), "osso", D=5)               # ulna
        m.cubo(ante, (px - 0.62, 14.2, -0.86), (0.3, 2.6, 0.3), "carne", D=6)            # tendões secos
        m.cubo(ante, (px + 0.35, 15.0, 0.45), (0.25, 2.0, 0.25), "carne", D=6)
        m.cubo(ante, (px - 0.9, 16.4, -0.9), (1.8, 0.7, 1.8), "corda", D=5)              # trapo no pulso
        mao = "mao_" + lado
        m.osso(mao, ante, (px, 13.4, 0))
        m.cubo(mao, (px - 1.05, 12.6, -1.05), (2.1, 1.1, 2.1), "osso", D=5)              # carpo
        m.cubo(mao, (px - 1.15, 11.1, -0.95), (2.3, 1.5, 0.95), "osso", D=5)             # metacarpos
        m.cubo(mao, (px - 0.4 * sinal - 0.25, 10.4, -1.8), (0.48, 1.3, 0.48), "osso", D=6)  # polegar
        m.cubo(mao, (px - 0.4 * sinal - 0.22, 9.4, -2.1), (0.42, 1.0, 0.42), "osso", D=6)
        dedos = "dedos_" + lado
        m.osso(dedos, mao, (px, 11.1, -0.7))
        for k in range(4):
            fx = px - 1.1 + k * 0.6
            comp = (1.3, 1.5, 1.4, 1.1)[k]
            m.cubo(dedos, (fx, 11.1 - comp, -0.95), (0.42, comp, 0.42), "osso", D=6)      # falange 1
            m.cubo(dedos, (fx + 0.02, 11.1 - comp - 1.05, -1.2), (0.38, 1.1, 0.38), "osso", D=6)   # falange 2
            m.cubo(dedos, (fx + 0.05, 11.1 - comp - 1.75, -1.5), (0.32, 0.75, 0.32), "osso", D=6)  # ponta em garra

    # ---------------- 0.59.1: a caveira que ele segura para se alimentar (escondida; aparece na animação) ----------------
    m.osso("caveira", "mao_e", (6.3, 8.6, -1.4))
    cx, cy, cz = 6.3, 6.4, -1.4
    m.cubo("caveira", (cx - 2.2, cy - 1.2, cz - 2.2), (4.4, 3.6, 4.4), "cranio", D=6)          # calota
    m.cubo("caveira", (cx - 1.8, cy + 2.4, cz - 1.8), (3.6, 0.8, 3.6), "cranio", D=6)          # topo
    m.cubo("caveira", (cx - 1.6, cy - 2.4, cz - 2.5), (3.2, 1.3, 2.6), "cranio", D=6)          # maxila
    m.cubo("caveira", (cx - 2.35, cy - 1.6, cz - 1.0), (0.5, 1.4, 1.6), "osso", D=6)           # zigomáticos
    m.cubo("caveira", (cx + 1.85, cy - 1.6, cz - 1.0), (0.5, 1.4, 1.6), "osso", D=6)
    m.cubo("caveira", (cx - 1.55, cy - 0.5, cz - 2.35), (1.2, 1.1, 0.3), "orbita", D=8)       # órbitas
    m.cubo("caveira", (cx + 0.35, cy - 0.5, cz - 2.35), (1.2, 1.1, 0.3), "orbita", D=8)
    m.cubo("caveira", (cx - 0.3, cy - 1.45, cz - 2.6), (0.6, 0.8, 0.3), "buraco", D=8)        # nariz
    m.cubo("caveira", (cx - 1.3, cy - 2.75, cz - 2.65), (2.6, 0.5, 0.3), "dente", D=8)        # dentes
    m.cubo("caveira", (cx - 1.4, cy - 3.5, cz - 2.3), (2.8, 0.8, 2.1), "osso", D=6)           # mandíbula
    m.cubo("caveira", (cx - 0.9, cy + 1.2, cz - 2.3), (0.25, 1.5, 0.2), "buraco", D=8)        # rachadura

    # ---------------- a foice: haste amarrada, lâmina chanfrada e serrilhada, crânio na base, a pérola ----------------
    m.osso("foice", "mao_d", (-6.3, 10.5, -0.4))
    m.cubo("foice", (-6.8, -3, -0.9), (1, 56, 1), "haste", D=4)
    for y in (2, 9, 20, 31, 42):
        m.cubo("foice", (-7.05, y, -1.15), (1.5, 0.7, 1.5), "osso", D=5)                  # anéis de osso
    for y in range(11, 16):                                                                 # o punho, enrolado em couro
        m.cubo("foice", (-6.95, y + 0.1, -1.05), (1.3, 0.8, 1.3), "corda", D=5, rot=(0, 20 * (y % 2), 0), pivo=(-6.3, y, -0.4))
    for k in range(3):                                                                      # contas e um amuleto pendurado
        m.cubo("foice", (-7.6 - k * 0.2, 41.5 - k * 0.8, -0.6), (0.5, 0.5, 0.5), "ferro", D=6)
    m.cubo("foice", (-8.3, 38.6, -0.75), (0.9, 1.0, 0.8), "cranio", D=6)
    m.cubo("foice", (-7.3, -5, -1.4), (2, 2, 2), "ferro", D=5)                             # ponteira
    m.cubo("foice", (-6.9, -6.4, -1.0), (1.2, 1.4, 1.2), "ferro", D=5)
    m.cubo("foice", (-7.4, 50, -1.5), (2.2, 3.2, 2.2), "ferro", D=5)                       # colar da lâmina
    m.cubo("foice", (-7.55, 50.8, -1.65), (2.5, 0.6, 2.5), "ferro", D=5)
    # um crânio cravado na base da lâmina
    m.cubo("foice", (-7.2, 52.6, -2.0), (1.8, 1.8, 1.6), "cranio", D=6)
    m.cubo("foice", (-6.95, 53.3, -2.1), (0.5, 0.45, 0.2), "orbita", D=8)
    m.cubo("foice", (-6.15, 53.3, -2.1), (0.5, 0.45, 0.2), "orbita", D=8)
    m.cubo("foice", (-6.95, 52.4, -1.95), (1.3, 0.4, 1.0), "dente", D=8)
    pts = []
    n = 26
    for i in range(n + 1):
        u = i / n
        pts.append((53.0 + 4.0 * math.sin(u * math.pi * 0.85) - 8.0 * u * u, -1.4 - 19.0 * u))
    for i in range(n):
        (y0, z0), (y1, z1) = pts[i], pts[i + 1]
        cy, cz = (y0 + y1) / 2, (z0 + z1) / 2
        comp = math.hypot(y1 - y0, z1 - z0) + 0.3
        ang = math.degrees(math.atan2(y1 - y0, -(z1 - z0)))
        alt = 3.6 - 2.9 * (i / n)
        rot = (ANG_SINAL * ang, 0, 0)
        m.cubo("foice", (-6.6, cy - alt * 0.25, cz - comp / 2), (0.6, alt * 0.72, comp), "metal", rot=rot, pivo=(-6.3, cy, cz), D=5)
        m.cubo("foice", (-6.45, cy - alt * 0.25 - alt * 0.3, cz - comp / 2), (0.3, alt * 0.32, comp), "fio", rot=rot, pivo=(-6.3, cy, cz), D=5)
        if i % 2 == 0 and i < n - 3:   # serrilhado no dorso
            m.cubo("foice", (-6.5, cy + alt * 0.45, cz - 0.25), (0.4, 0.7 - 0.3 * (i / n), 0.5), "metal",
                   rot=(ANG_SINAL * ang - 20, 0, 0), pivo=(-6.3, cy, cz), D=5)
        if i % 5 == 2:                  # rebites
            m.cubo("foice", (-6.75, cy + alt * 0.1, cz - 0.15), (0.9, 0.3, 0.3), "ferro", rot=rot, pivo=(-6.3, cy, cz), D=6)
    m.cubo("foice", (-6.8, 51, 0.6), (1, 3.6, 2.8), "metal", rot=(30, 0, 0), pivo=(-6.3, 52, 1.2), D=5)   # esporão de trás
    m.cubo("foice", (-6.7, 53.6, 2.3), (0.8, 2.0, 1.2), "metal", rot=(50, 0, 0), pivo=(-6.3, 54, 2.6), D=5)
    # a pérola negra (a bateria da Morte) presa por garras de osso
    m.osso("perola", "foice", (-6.3, 51.2, -2.6))
    m.cubo("perola", (-7.8, 50.2, -3.8), (3, 2, 2.4), "perola", D=6)
    m.cubo("perola", (-7.3, 49.7, -3.8), (2, 3, 2.4), "perola", D=6)
    m.cubo("perola", (-7.3, 50.2, -4.3), (2, 2, 3.4), "perola", D=6)
    for (dx, dy, rz) in ((-1.7, 1.3, 30), (1.2, 1.3, -30), (-1.7, -1.7, -30), (1.2, -1.7, 30)):
        m.cubo("perola", (-6.55 + dx, 51.2 + dy, -3.1), (0.5, 0.7, 1.8), "osso", rot=(0, 0, rz), pivo=(-6.3 + dx, 51.2 + dy, -2.6), D=6)
        m.cubo("perola", (-6.5 + dx * 1.15, 51.4 + dy * 1.1, -4.4), (0.35, 0.35, 0.9), "osso", D=6)

    pintores = {"osso": p_osso, "cranio": p_cranio, "orbita": p_orbita, "pupila": p_pupila, "buraco": p_buraco,
                "dente": p_dente, "carne": p_carne, "pele": p_pele, "cabelo": p_cabelo, "pano": p_pano,
                "farrapo": p_farrapo, "corda": p_corda, "metal": p_metal, "fio": p_fio, "haste": p_haste,
                "perola": p_perola, "coracao": p_coracao, "ferro": p_ferro}
    brilhos = {}
    for k, g in ():
        # a casca: a mesma cor do brilho, e o brilho; fora do brilho ela é transparente
        pintores[k + "_g"] = (lambda gg: lambda *a: gg(*a) if gg(*a) else (0, 0, 0, 0))(g)
        brilhos[k + "_g"] = g
    m.salvar(pintores, brilhos)


if __name__ == "__main__":
    necron()

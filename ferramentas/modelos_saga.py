"""
Gera os modelos 3D (formato Bedrock/GeckoLib, abrem no Blockbench) e as texturas da saga do Necron:

  geo/entity/necron.geo.json          + textures/entity/necron.png (+ _glowmask)
  geo/entity/embriao_vida.geo.json    + textures/entity/embriao_vida.png (+ _glowmask)
  animations/entity/<nome>.animation.json (vazias: a cutscene mexe nos ossos pelo código)

Rodar na raiz do projeto:  python3 ferramentas/modelos_saga.py

Se o dono refizer um modelo no Blockbench, é só salvar por cima com o MESMO nome de arquivo e os MESMOS nomes de
ossos usados pela cena (necron: root, corpo, cabeca, mandibula, capuz, braco_d, braco_e, foice, perola, saia,
farrapo_1..10 · embriao: root, corpo, cabeca, cordao_1..8) e NÃO rodar este script de novo.
"""
import json
import math
import os
import random

from PIL import Image

ANG_SINAL = -1
RAIZ = os.path.join("src", "main", "resources", "assets", "lanterna")


class Modelo:
    def __init__(self, nome, tw, th):
        self.nome, self.tw, self.th = nome, tw, th
        self.ossos = []
        self.por_nome = {}
        self.pintar = []          # (cubo, material, u, v)
        self.cx = self.cy = 0     # empacotador
        self.linha = 0

    def osso(self, nome, pai=None, pivo=(0, 0, 0), rot=None):
        o = {"name": nome, "pivot": [float(x) for x in pivo]}
        if pai:
            o["parent"] = pai
        if rot:
            o["rotation"] = [float(x) for x in rot]
        self.ossos.append(o)
        self.por_nome[nome] = o
        return nome

    def _uv(self, w, h, d):
        # tamanho da área do "box UV" (arredondado para cima)
        lw = int(math.ceil(2 * (d + w))) + 1
        lh = int(math.ceil(d + h)) + 1
        if self.cx + lw > self.tw:
            self.cx = 0
            self.cy += self.linha
            self.linha = 0
        if self.cy + lh > self.th:
            raise RuntimeError(self.nome + ": textura pequena demais")
        u, v = self.cx, self.cy
        self.cx += lw
        self.linha = max(self.linha, lh)
        return u, v

    def cubo(self, osso, origem, tam, material, rot=None, pivo=None, inflar=0):
        w, h, d = tam
        u, v = self._uv(w, h, d)
        c = {"origin": [float(x) for x in origem], "size": [float(x) for x in tam], "uv": [u, v]}
        if inflar:
            c["inflate"] = inflar
        if rot:
            c["rotation"] = [float(x) for x in rot]
            c["pivot"] = [float(x) for x in (pivo or origem)]
        self.por_nome[osso].setdefault("cubes", []).append(c)
        self.pintar.append((tam, material, u, v))
        return c

    def salvar(self, vis_w, vis_h, pintores, brilhos):
        geo = {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.nome,
                    "texture_width": self.tw, "texture_height": self.th,
                    "visible_bounds_width": vis_w, "visible_bounds_height": vis_h,
                    "visible_bounds_offset": [0, vis_h / 2, 0],
                },
                "bones": self.ossos,
            }],
        }
        with open(os.path.join(RAIZ, "geo", "entity", self.nome + ".geo.json"), "w") as f:
            json.dump(geo, f, indent=1)
        with open(os.path.join(RAIZ, "animations", "entity", self.nome + ".animation.json"), "w") as f:
            json.dump({"format_version": "1.8.0", "animations": {}}, f, indent=1)

        img = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        px, gx = img.load(), glow.load()
        for (w, h, d), mat, u, v in self.pintar:
            w, h, d = int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))
            faces = {
                "cima": (u + d, v, w, d), "baixo": (u + d + w, v, w, d),
                "direita": (u, v + d, d, h), "frente": (u + d, v + d, w, h),
                "esquerda": (u + d + w, v + d, d, h), "tras": (u + 2 * d + w, v + d, w, h),
            }
            for nome, (fx, fy, fw, fh) in faces.items():
                for yy in range(max(1, fh)):
                    for xx in range(max(1, fw)):
                        X, Y = fx + xx, fy + yy
                        if X >= self.tw or Y >= self.th:
                            continue
                        cor = pintores[mat](nome, xx, yy, max(1, fw), max(1, fh), X, Y)
                        if cor is not None:
                            px[X, Y] = cor
                        if mat in brilhos:
                            g = brilhos[mat](nome, xx, yy, max(1, fw), max(1, fh), X, Y)
                            if g is not None:
                                gx[X, Y] = g
        img.save(os.path.join(RAIZ, "textures", "entity", self.nome + ".png"))
        glow.save(os.path.join(RAIZ, "textures", "entity", self.nome + "_glowmask.png"))


# ============================== ruído para pintar ==============================

def ruido(X, Y, s=0):
    r = random.Random((X * 73856093) ^ (Y * 19349663) ^ (s * 83492791))
    return r.random()


def suave(X, Y, esc, s=0):
    # ruído de valor interpolado
    x, y = X / esc, Y / esc
    x0, y0 = int(math.floor(x)), int(math.floor(y))
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = ruido(x0, y0, s), ruido(x0 + 1, y0, s)
    c, d = ruido(x0, y0 + 1, s), ruido(x0 + 1, y0 + 1, s)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def mistura(c1, c2, k):
    k = max(0.0, min(1.0, k))
    return tuple(int(c1[i] + (c2[i] - c1[i]) * k) for i in range(3))


def rgba(c, a=255):
    return (max(0, min(255, c[0])), max(0, min(255, c[1])), max(0, min(255, c[2])), a)


def escurece(c, k):
    return tuple(int(x * k) for x in c)


# ============================== NECRON ==============================

OSSO = (214, 204, 182)
OSSO_ESC = (120, 108, 92)
CARNE = (92, 30, 32)
CARNE_ESC = (52, 14, 18)
PANO = (30, 28, 36)
PANO_CLARO = (52, 48, 62)
METAL = (58, 60, 70)
FIO = (196, 200, 214)
HASTE = (34, 28, 26)
PEROLA = (18, 10, 26)


def p_osso(face, x, y, w, h, X, Y):
    n = suave(X, Y, 3, 1) * 0.6 + ruido(X, Y, 2) * 0.4
    c = mistura(OSSO_ESC, OSSO, 0.55 + n * 0.45)
    # restos de carne grudados no osso
    if suave(X, Y, 4, 7) > 0.68:
        c = mistura(CARNE_ESC, CARNE, ruido(X, Y, 9))
    # sombra nas bordas
    if x == 0 or y == h - 1:
        c = escurece(c, 0.8)
    return rgba(c)


def p_cranio(face, x, y, w, h, X, Y):
    base = p_osso(face, x, y, w, h, X, Y)
    if face != "frente":
        if face == "cima" and suave(X, Y, 2, 4) > 0.6:
            return rgba(mistura(CARNE_ESC, CARNE, 0.6))
        return base
    # rosto de caveira (7x7): órbitas fundas, nariz, dentes; carne pendurada nas maçãs do rosto
    cx = (w - 1) / 2.0
    olho = (y in (2, 3)) and (x in (1, 2) or x in (w - 3, w - 2))
    if olho:
        return rgba((8, 4, 8))
    if y == 4 and abs(x - cx) < 0.6:
        return rgba((20, 12, 12))  # nariz
    if y >= 5:
        # dentes alternados
        if y == 5:
            return rgba((230, 224, 206)) if x % 2 == 0 else rgba((70, 60, 52))
        return rgba(mistura(CARNE_ESC, CARNE, 0.5)) if x in (0, w - 1) else rgba((60, 50, 44))
    if (x == 0 or x == w - 1) and y >= 3:
        return rgba(mistura(CARNE_ESC, CARNE, ruido(X, Y, 3)))
    if y == 1 and x in (1, 2, w - 3, w - 2):
        return rgba(escurece(OSSO, 0.7))  # sobrancelha funda
    return base


def g_cranio(face, x, y, w, h, X, Y):
    # o brilho frio dentro das órbitas
    if face == "frente" and y in (2, 3) and (x in (1, 2) or x in (w - 3, w - 2)):
        k = 1.0 if (y == 3 and x in (2, w - 3)) else 0.55
        return rgba((int(200 * k), int(230 * k), int(255 * k)))
    return None


def p_mandibula(face, x, y, w, h, X, Y):
    if face == "frente" and y == 0:
        return rgba((230, 224, 206)) if x % 2 == 1 else rgba((60, 50, 44))
    return p_osso(face, x, y, w, h, X, Y)


def p_pano(face, x, y, w, h, X, Y):
    n = suave(X, Y, 3, 11)
    c = mistura(PANO, PANO_CLARO, n * 0.8)
    # dobras verticais
    if (X + int(suave(X, Y, 5, 3) * 3)) % 4 == 0:
        c = escurece(c, 0.75)
    # rasgos: buracos de verdade (transparentes) e fiapos
    if suave(X, Y, 2, 21) > 0.80 and face in ("frente", "tras", "direita", "esquerda") and 0 < y < h - 1:
        return (0, 0, 0, 0)
    if ruido(X, Y, 5) > 0.93:
        c = mistura(c, (90, 70, 110), 0.5)  # um fio arroxeado (resto de luz negra)
    return rgba(c)


def p_pano_torso(face, x, y, w, h, X, Y):
    # rasgo grande na frente do peito: aparecem as costelas
    if face == "frente" and 2 <= y <= 6 and 1 <= x <= w - 2:
        if y % 2 == 0 and x not in (w // 2,):
            return p_osso(face, x, y, w, h, X, Y)
        if x == w // 2:
            return rgba(escurece(OSSO, 0.85))  # esterno
        return rgba(mistura(CARNE_ESC, (10, 4, 6), 0.4))
    return p_pano(face, x, y, w, h, X, Y)


def p_farrapo(face, x, y, w, h, X, Y):
    # ponta esfiapada: cada coluna termina numa altura diferente
    corte = h - 1 - int(ruido(X, 0, 31) * 2.5)
    if y > corte:
        return (0, 0, 0, 0)
    return p_pano(face, x, y, w, h, X, Y)


def p_corda(face, x, y, w, h, X, Y):
    return rgba(mistura((40, 30, 24), (78, 62, 46), (X + Y) % 3 / 2.0))


def p_metal(face, x, y, w, h, X, Y):
    n = suave(X, Y, 2, 14)
    c = mistura(escurece(METAL, 0.7), METAL, n)
    if face == "baixo" or (face in ("frente", "tras") and y == h - 1):
        c = FIO  # o fio da lâmina
    if ruido(X, Y, 16) > 0.9:
        c = (70, 18, 22)  # ferrugem/sangue seco
    return rgba(c)


def p_haste(face, x, y, w, h, X, Y):
    c = mistura(HASTE, (60, 50, 44), suave(X, Y, 2, 17))
    if Y % 6 == 0:
        c = mistura(c, OSSO_ESC, 0.6)  # anéis de osso amarrados na haste
    return rgba(c)


def p_perola(face, x, y, w, h, X, Y):
    # pérola negra com reflexo violeta-branco
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    dist = math.hypot((x - cx) / max(1, w / 2), (y - cy) / max(1, h / 2))
    c = mistura((70, 40, 110), PEROLA, dist)
    if x == int(cx * 0.6) and y == int(cy * 0.6):
        c = (235, 225, 255)
    return rgba(c)


def g_perola(face, x, y, w, h, X, Y):
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    dist = math.hypot((x - cx) / max(1, w / 2), (y - cy) / max(1, h / 2))
    k = max(0.0, 1 - dist)
    return rgba((int(130 * k + 30), int(25 * k + 5), int(190 * k + 45)))


def p_carne(face, x, y, w, h, X, Y):
    n = ruido(X, Y, 61)
    c = mistura(CARNE_ESC, CARNE, n)
    if suave(X, Y, 2, 62) > 0.7:
        c = mistura(c, (130, 60, 52), 0.5)   # tendões mais claros
    return rgba(c)


def p_dentes(face, x, y, w, h, X, Y):
    if face == "frente":
        return rgba((228, 220, 196)) if x % 2 == 0 else rgba((88, 70, 58))
    return p_osso(face, x, y, w, h, X, Y)


def p_cranio2(face, x, y, w, h, X, Y):
    base = p_osso(face, x, y, w, h, X, Y)
    if face == "frente":
        # testa com uma rachadura e restos de pele
        if x == w // 2 + 1 and y < h // 2:
            return rgba((70, 56, 48))
        if suave(X, Y, 2, 64) > 0.62:
            return rgba(mistura(CARNE_ESC, CARNE, 0.6))
    if face == "cima" and suave(X, Y, 3, 65) > 0.55:
        return rgba(mistura(CARNE_ESC, (60, 30, 30), 0.5))   # couro cabeludo podre
    return base


def p_orbita(face, x, y, w, h, X, Y):
    return rgba((10, 5, 10))


def g_orbita(face, x, y, w, h, X, Y):
    if face != "frente":
        return None
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    d = math.hypot(x - cx, y - cy)
    k = max(0.0, 1 - d / max(1.0, w / 1.6))
    return rgba((int(150 * k + 30), int(200 * k + 20), int(255 * k + 30)))


def necron():
    m = Modelo("necron", 512, 512)
    m.osso("root")
    m.osso("corpo", "root", (0, 16, 0))

    # ---------- tronco: coluna, costelas, esterno e clavículas (o peito aberto) ----------
    for i in range(10):
        y = 16.5 + i * 1.3
        m.cubo("corpo", (-0.7, y, 0.6), (1.4, 1.0, 1.4), "osso")
        m.cubo("corpo", (-0.3, y + 0.2, 1.9), (0.6, 0.6, 0.9), "osso")          # processo espinhoso
    for i in range(6):
        y = 27.0 - i * 1.45
        rx, rz = 3.9 - 0.18 * i, 2.7 - 0.08 * i
        for sinal in (-1, 1):
            n = 7
            for k in range(n):
                fi = math.radians(25 + 125 * k / (n - 1))
                x = sinal * rx * math.sin(fi)
                z = rz * math.cos(fi) - 0.1
                y2 = y - 0.9 * (k / (n - 1))          # a costela desce indo para a frente
                m.cubo("corpo", (x - 0.45, y2 - 0.3, z - 0.45), (0.9, 0.6, 0.9), "osso")
    m.cubo("corpo", (-0.6, 21, -2.9), (1.2, 6.2, 0.6), "osso")                     # esterno
    for sinal in (-1, 1):                                                          # clavículas
        m.cubo("corpo", (min(0.6 * sinal, 4.6 * sinal), 27.6, -1.6), (4.0, 0.7, 0.7), "osso",
               rot=(0, 0, -10 * sinal), pivo=(0.6 * sinal, 28, -1.2))
    # restos de carne pendurados nas costelas
    for (x, y, z, c) in [(-2.6, 22.5, -2.2, 2.2), (2.2, 24.0, -2.0, 1.6), (-1.6, 19.5, -2.4, 1.8), (3.0, 20.5, -1.4, 1.4)]:
        m.cubo("corpo", (x, y - c, z), (0.6, c, 0.4), "carne")

    # ---------- a túnica: costas, lados, ombreiras rasgadas; a frente aberta ----------
    m.cubo("corpo", (-5, 16, 2.4), (10, 12.5, 1.2), "pano")
    m.cubo("corpo", (-5, 16, -2.4), (1.1, 12.5, 4.9), "pano")
    m.cubo("corpo", (3.9, 16, -2.4), (1.1, 12.5, 4.9), "pano")
    m.cubo("corpo", (-5, 26, -3.2), (2.4, 2.6, 6.6), "pano")                       # lapelas rasgadas
    m.cubo("corpo", (2.6, 26, -3.2), (2.4, 2.6, 6.6), "pano")
    m.cubo("corpo", (-4.9, 18.5, -3.4), (1.6, 7.5, 0.7), "farrapo")
    m.cubo("corpo", (3.3, 17.5, -3.4), (1.6, 8.5, 0.7), "farrapo")
    m.cubo("corpo", (-6.2, 26.5, -3.5), (12.4, 2.2, 7.4), "pano")                  # manto nos ombros
    m.cubo("corpo", (-5.5, 15.5, -3.3), (11, 1.1, 6.8), "corda")                   # cinto de corda
    m.cubo("corpo", (-1.3, 12.5, -3.6), (1.0, 3.2, 0.9), "corda")
    m.cubo("corpo", (0.4, 13.2, -3.6), (1.0, 2.5, 0.9), "corda")

    # ---------- a saia longa e os farrapos ----------
    m.osso("saia", "corpo", (0, 16, 0))
    m.cubo("saia", (-5.3, 10, -3.4), (10.6, 6, 7.2), "pano")
    m.cubo("saia", (-5.9, 3, -3.9), (11.8, 7, 8.2), "pano")
    pos = [(-5.8, -3.9), (-3.2, -4.0), (-0.4, -4.1), (2.4, -4.0), (4.6, -3.9),
           (-5.6, 3.3), (-2.6, 3.4), (0.6, 3.4), (3.6, 3.3), (-6.3, -0.8), (5.3, -0.8), (5.3, 1.6)]
    for i, (x, z) in enumerate(pos, 1):
        nome = "farrapo_%d" % i
        m.osso(nome, "saia", (x + 1, 3, z + 0.4))
        comp = 3 + (i * 7) % 5
        larg = 1.6 + (i % 3) * 0.5
        m.cubo(nome, (x, 3 - comp, z), (larg, comp, 0.8), "farrapo")
    # pés de osso (saem por baixo da saia)
    for sinal in (-1, 1):
        x = 2.4 * sinal
        m.cubo("root", (x - 1.1, 0, -2.4), (2.2, 1.0, 3.2), "osso")
        for k in range(3):
            m.cubo("root", (x - 1.1 + k * 0.8, 0, -3.6), (0.6, 0.6, 1.4), "osso")   # dedos
        m.cubo("root", (x - 0.5, 1.0, -0.5), (1.0, 2.2, 1.0), "osso")               # canela

    # ---------- cabeça: crânio com maçãs do rosto, órbitas fundas, dentes; mandíbula solta ----------
    m.osso("cabeca", "corpo", (0, 28.5, 0))
    m.cubo("cabeca", (-0.7, 28.5, -0.2), (1.4, 2.2, 1.4), "osso")                  # pescoço
    m.cubo("cabeca", (-3.4, 31.5, -3.2), (6.8, 6.0, 7.0), "cranio2")               # caixa do crânio
    m.cubo("cabeca", (-3.0, 37.3, -2.6), (6.0, 0.8, 5.8), "cranio2")               # topo arredondado
    m.cubo("cabeca", (-3.8, 32.5, -2.4), (0.6, 4.2, 5.2), "osso")                  # lados
    m.cubo("cabeca", (3.2, 32.5, -2.4), (0.6, 4.2, 5.2), "osso")
    m.cubo("cabeca", (-3.3, 35.0, -3.9), (6.6, 1.1, 1.0), "osso")                  # sobrancelha
    for sinal in (-1, 1):
        x = 1.55 * sinal
        m.cubo("cabeca", (x - 1.05, 32.9, -3.45), (2.1, 2.0, 0.4), "orbita")        # órbitas (fundas e escuras)
        m.cubo("cabeca", (2.7 * sinal - 0.7, 31.7, -3.6), (1.4, 1.3, 2.4), "osso")  # maçãs do rosto
        m.cubo("cabeca", (3.1 * sinal - 0.4, 30.6, -2.0), (0.8, 1.6, 0.6), "carne") # carne pendurada
    m.cubo("cabeca", (-0.55, 31.6, -3.7), (1.1, 1.2, 0.4), "orbita")               # nariz
    m.cubo("cabeca", (-2.4, 30.3, -3.6), (4.8, 1.3, 2.0), "osso")                  # maxila
    m.cubo("cabeca", (-2.2, 29.8, -3.75), (4.4, 0.8, 0.4), "dentes")               # dentes de cima
    m.osso("mandibula", "cabeca", (0, 30.6, 0.8), (12, 0, 0))
    m.cubo("mandibula", (-2.6, 28.3, -3.5), (5.2, 1.2, 4.2), "osso")
    m.cubo("mandibula", (-2.2, 29.4, -3.6), (4.4, 0.7, 0.4), "dentes")             # dentes de baixo
    m.cubo("mandibula", (-0.9, 27.9, -3.7), (1.8, 0.8, 1.0), "osso")               # queixo
    m.cubo("mandibula", (1.6, 27.2, -2.8), (0.5, 1.4, 0.4), "carne")
    # o capuz: fundo, deixando o rosto na sombra, com a borda rasgada
    m.osso("capuz", "cabeca", (0, 33, 0))
    m.cubo("capuz", (-4.8, 38.0, -5.0), (9.6, 1.4, 9.6), "pano")
    m.cubo("capuz", (-4.8, 29.0, 3.6), (9.6, 9.2, 1.2), "pano")
    m.cubo("capuz", (-5.2, 29.0, -5.0), (1.2, 9.2, 8.8), "pano")
    m.cubo("capuz", (4.0, 29.0, -5.0), (1.2, 9.2, 8.8), "pano")
    m.cubo("capuz", (-4.6, 37.2, -5.6), (9.2, 1.0, 1.2), "farrapo")
    for k, (x, comp) in enumerate([(-4.6, 2.5), (-2.4, 1.5), (0.4, 2.2), (2.6, 1.2)]):
        m.cubo("capuz", (x, 37.2 - comp, -5.7), (1.8, comp, 0.5), "farrapo")
    m.cubo("capuz", (-1.6, 38.5, 3.0), (3.2, 2.0, 3.5), "pano", rot=(-28, 0, 0), pivo=(0, 39, 4.2))
    m.cubo("capuz", (-2.4, 21.5, 4.6), (4.8, 8.0, 0.8), "farrapo")                 # capa caindo nas costas

    # ---------- braços de osso: manga rasgada, úmero, rádio e ulna, mão com dedos ----------
    for sinal, nome in ((-1, "braco_d"), (1, "braco_e")):
        px = 6.3 * sinal
        m.osso(nome, "corpo", (px, 27.5, 0))
        m.cubo(nome, (px - 1.0, 26.5, -1.0), (2.0, 2.0, 2.0), "osso")                # ombro
        m.cubo(nome, (px - 2.0, 21.5, -2.2), (4.0, 6.0, 4.4), "farrapo")            # manga rasgada
        m.cubo(nome, (px - 1.9, 18.0, -2.0), (1.6, 3.5, 0.7), "farrapo")
        m.cubo(nome, (px - 0.7, 20.0, -0.7), (1.4, 6.5, 1.4), "osso")                # úmero
        m.cubo(nome, (px - 0.9, 19.0, -0.9), (1.8, 1.4, 1.8), "osso")                # cotovelo
        m.cubo(nome, (px - 0.75, 13.5, -0.6), (0.7, 5.8, 0.7), "osso")               # rádio
        m.cubo(nome, (px + 0.05, 13.5, -0.1), (0.7, 5.8, 0.7), "osso")               # ulna
        m.cubo(nome, (px - 0.6, 13.6, -0.8), (0.4, 2.2, 0.4), "carne")
        m.cubo(nome, (px - 1.1, 12.5, -1.1), (2.2, 1.2, 2.2), "osso")                # pulso
        m.cubo(nome, (px - 1.2, 11.0, -1.0), (2.4, 1.6, 1.0), "osso")                # palma
        for k in range(4):
            fx = px - 1.15 + k * 0.62
            m.cubo(nome, (fx, 9.5, -1.0), (0.45, 1.6, 0.45), "osso")
            m.cubo(nome, (fx, 8.2, -1.25), (0.4, 1.4, 0.4), "osso")
        m.cubo(nome, (px - 0.4 * sinal - 0.25, 10.2, -1.9), (0.5, 1.4, 0.5), "osso")  # polegar

    # ---------- a foice, na mão direita, com a pérola negra (a bateria da Morte) presa por garras ----------
    m.osso("foice", "braco_d", (-6.3, 10.5, -0.4))
    m.cubo("foice", (-6.8, -3, -0.9), (1, 56, 1), "haste")
    for y in (4, 16, 30, 44):
        m.cubo("foice", (-7.05, y, -1.15), (1.5, 0.8, 1.5), "osso")                  # anéis de osso amarrados
    m.cubo("foice", (-7.3, -5, -1.4), (2, 2, 2), "metal")                           # ponteira
    m.cubo("foice", (-7.3, 50, -1.4), (2, 3, 2), "metal")                           # colar da lâmina
    pts = []
    n = 18
    for i in range(n + 1):
        u = i / n
        pts.append((52.5 + 3.8 * math.sin(u * math.pi * 0.85) - 7.5 * u * u, -1.4 - 18.0 * u))
    for i in range(n):
        (y0, z0), (y1, z1) = pts[i], pts[i + 1]
        cy, cz = (y0 + y1) / 2, (z0 + z1) / 2
        comp = math.hypot(y1 - y0, z1 - z0) + 0.35
        ang = math.degrees(math.atan2(y1 - y0, -(z1 - z0)))
        alt = 3.4 - 2.7 * (i / n)
        m.cubo("foice", (-6.55, cy - alt * 0.35, cz - comp / 2), (0.5, alt, comp), "metal", rot=(ANG_SINAL * ang, 0, 0), pivo=(-6.3, cy, cz))
        if i % 3 == 1:   # serrilhado no dorso
            m.cubo("foice", (-6.45, cy + alt * 0.6, cz - 0.3), (0.3, 0.8, 0.6), "metal", rot=(ANG_SINAL * ang, 0, 0), pivo=(-6.3, cy, cz))
    m.cubo("foice", (-6.8, 51, 0.6), (1, 3.4, 2.8), "metal", rot=(30, 0, 0), pivo=(-6.3, 52, 1.2))   # esporão de trás
    m.osso("perola", "foice", (-6.3, 51.2, -2.6))
    m.cubo("perola", (-7.8, 50.2, -3.7), (3, 2, 2.4), "perola")
    m.cubo("perola", (-7.3, 49.7, -3.7), (2, 3, 2.4), "perola")
    m.cubo("perola", (-7.3, 50.2, -4.2), (2, 2, 3.4), "perola")
    for (dx, dy) in ((-1.6, 1.4), (1.1, 1.4), (-1.6, -1.6), (1.1, -1.6)):           # garras segurando a pérola
        m.cubo("perola", (-6.55 + dx, 51.2 + dy, -3.0), (0.5, 0.6, 1.6), "osso")

    pintores = {"osso": p_osso, "cranio2": p_cranio2, "orbita": p_orbita, "dentes": p_dentes, "carne": p_carne,
                "pano": p_pano, "farrapo": p_farrapo, "corda": p_corda, "metal": p_metal, "haste": p_haste, "perola": p_perola}
    m.salvar(4, 5, pintores, {"orbita": g_orbita, "perola": g_perola})


# ============================== EMBRIÃO DA VIDA ==============================

PELE = (250, 248, 246)
PELE_ROSA = (240, 222, 226)
VEIA = (214, 200, 230)


def p_pele(face, x, y, w, h, X, Y):
    n = suave(X, Y, 3, 41)
    c = mistura(PELE_ROSA, PELE, 0.4 + n * 0.6)
    # veias finas (luz por baixo da pele)
    if abs(suave(X, Y, 4, 43) - 0.5) < 0.04:
        c = mistura(c, VEIA, 0.7)
    return rgba(c)


def p_rosto(face, x, y, w, h, X, Y):
    if face == "frente":
        # olhos fechados (duas linhas), narizinho, boca
        cx = (w - 1) / 2.0
        if h // 2 - 2 <= y <= h // 2 - 1 and 0.8 <= abs(x - cx) <= 2.2:
            return rgba((150, 128, 158))
        if y == h // 2 + 1 and x == w // 2:
            return rgba((222, 200, 208))
        if y == h // 2 + 2 and abs(x - (w - 1) / 2) < 1:
            return rgba((200, 170, 182))
    return p_pele(face, x, y, w, h, X, Y)


def g_pele(face, x, y, w, h, X, Y):
    k = 0.55 + suave(X, Y, 3, 44) * 0.45
    return rgba((int(255 * k), int(255 * k), int(255 * k)))


def p_cordao(face, x, y, w, h, X, Y):
    return rgba(mistura((226, 214, 236), (255, 255, 255), ((X + Y) % 3) / 2.0))


def conta(m, osso, c, r, mat, frente=None):
    """Uma "conta" arredondada (3 cubos cruzados + miolo), para formas orgânicas."""
    x, y, z = c
    if r < 0.75:
        m.cubo(osso, (x - r, y - r, z - r), (2 * r, 2 * r, 2 * r), mat)
        return
    a, b = 2 * r, 2 * r * 0.7
    m.cubo(osso, (x - a / 2, y - b / 2, z - b / 2), (a, b, b), mat)
    m.cubo(osso, (x - b / 2, y - a / 2, z - b / 2), (b, a, b), mat)
    m.cubo(osso, (x - b / 2, y - b / 2, z - a / 2), (b, b, a), frente or mat)
    k = 2 * r * 0.86
    m.cubo(osso, (x - k / 2, y - k / 2, z - k / 2), (k, k, k), mat)


def embriao():
    m = Modelo("embriao_vida", 256, 256)
    m.osso("root")
    m.osso("corpo", "root", (0, 7, 0))
    # o corpo é um "C": as contas descem pelas costas (+z) e a ponta enrola para a frente, por baixo
    R = 5.2
    n = 11
    for i in range(n):
        u = i / (n - 1)
        phi = math.radians(-5 + 215 * u)
        y = 7 + R * math.cos(phi)
        z = R * math.sin(phi) * 0.95
        r = 3.3 - 2.4 * u ** 1.3
        conta(m, "corpo", (0, y, z), r, "pele")
    # barriga (preenche o meio do C)
    conta(m, "corpo", (0, 6.5, -0.3), 2.8, "pele")
    # brotos dos braços (dobrados para perto do rosto) e das pernas (encolhidas)
    for lado in (-1, 1):
        conta(m, "corpo", (2.9 * lado, 9.2, -1.2), 1.1, "pele")
        conta(m, "corpo", (2.6 * lado, 8.6, -2.8), 0.95, "pele")
        conta(m, "corpo", (2.0 * lado, 9.2, -4.0), 0.75, "pele")
        conta(m, "corpo", (2.6 * lado, 3.4, 0.4), 1.3, "pele")
        conta(m, "corpo", (2.4 * lado, 3.0, -1.6), 1.1, "pele")
        conta(m, "corpo", (2.1 * lado, 3.8, -3.2), 0.85, "pele")
    # cabeça grande, curvada sobre o peito
    m.osso("cabeca", "corpo", (0, 11, -0.5))
    conta(m, "cabeca", (0, 12.8, -3.2), 4.3, "pele", frente="rosto")
    conta(m, "cabeca", (0, 14.0, -1.4), 3.6, "pele")
    # cordão umbilical: sai da barriga e desce torcendo
    pai, pv = "corpo", (0, 5.0, -3.0)
    for i in range(1, 9):
        nome = "cordao_%d" % i
        m.osso(nome, pai, pv)
        prox = (pv[0] + 0.6 * math.sin(i * 1.3), pv[1] - 1.5, pv[2] - 0.5 + 0.4 * math.cos(i))
        for k in range(3):
            q = k / 3
            conta(m, nome, (pv[0] + (prox[0] - pv[0]) * q, pv[1] + (prox[1] - pv[1]) * q, pv[2] + (prox[2] - pv[2]) * q), 0.45, "cordao")
        pai, pv = nome, prox
    m.salvar(2, 2, {"pele": p_pele, "rosto": p_rosto, "cordao": p_cordao},
             {"pele": g_pele, "rosto": g_pele, "cordao": g_pele})


def animacoes_necron():
    """Animações do Necron no mundo (a cutscene mexe nos ossos pelo código)."""
    def sen(vel, amp, fase=0, base=0):
        return "%s + math.sin(query.anim_time * %s + %s) * %s" % (base, vel, fase, amp)
    ossos = {
        "corpo": {"rotation": [sen(60, 1.5), 0, sen(45, 1.2)]},
        "saia": {"rotation": [sen(80, 3), 0, sen(55, 2)]},
        "cabeca": {"rotation": [sen(40, 3), sen(30, 6), 0]},
        "mandibula": {"rotation": [sen(150, 6, 0, 6), 0, 0]},
        "capuz": {"rotation": [sen(70, 2.5), 0, 0]},
        "braco_d": {"rotation": [sen(50, 3, 0, -18), 0, sen(40, 2, 0, 6)]},
        "braco_e": {"rotation": [sen(50, 4, 1, 8), 0, sen(40, 3, 1, -10)]},
        "foice": {"rotation": [sen(50, 2, 0, 16), 0, 0]},
    }
    for i in range(1, 13):
        ossos["farrapo_%d" % i] = {"rotation": [sen(90 + i * 7, 9, i * 1.3, 4), 0, sen(70, 3, i)]}
    golpe = {
        "braco_d": {"rotation": {"0.0": [-18, 0, 6], "0.25": [-150, -20, 10], "0.5": [40, 30, 0], "0.9": [-18, 0, 6]}},
        "foice": {"rotation": {"0.0": [16, 0, 0], "0.25": [-30, 0, 0], "0.5": [60, 0, 0], "0.9": [16, 0, 0]}},
        "corpo": {"rotation": {"0.0": [0, 0, 0], "0.25": [-8, 20, 0], "0.5": [12, -25, 0], "0.9": [0, 0, 0]}},
    }
    anim = {"format_version": "1.8.0", "animations": {
        "misc.idle": {"loop": True, "animation_length": 6, "bones": ossos},
        "attack.swing": {"animation_length": 0.9, "bones": golpe},
    }}
    with open(os.path.join(RAIZ, "animations", "entity", "necron.animation.json"), "w") as f:
        json.dump(anim, f, indent=1)


if __name__ == "__main__":
    necron()
    animacoes_necron()
    embriao()
    print("ok: necron e embriao_vida")

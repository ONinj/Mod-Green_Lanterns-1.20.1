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
    return rgba((int(150 * k + 40), int(90 * k + 10), int(220 * k + 40)))


def necron():
    m = Modelo("necron", 256, 256)
    m.osso("root")
    m.osso("corpo", "root", (0, 13, 0))
    # tronco (túnica com o peito rasgado), ombros com manto, corda na cintura
    m.cubo("corpo", (-4, 13, -2.5), (8, 9, 5), "pano_torso")
    m.cubo("corpo", (-5, 19.5, -3), (10, 3, 6), "pano")
    m.cubo("corpo", (-4.5, 12.5, -3), (9, 1, 6), "corda")
    m.cubo("corpo", (-1, 10, -3.2), (1, 3, 1), "corda")
    # espinha saindo pelas costas rasgadas
    for i in range(4):
        m.cubo("corpo", (-0.5, 14 + i * 2, 2.4), (1, 1, 1), "osso")
    # saia longa da túnica
    m.osso("saia", "corpo", (0, 13, 0))
    m.cubo("saia", (-4.5, 8, -3), (9, 5, 6), "pano")
    m.cubo("saia", (-5, 3, -3.5), (10, 5, 7), "pano")
    # farrapos pendurados na barra (balançam na cena)
    pos = [(-5, -3.5), (-2.5, -3.6), (0.5, -3.6), (3, -3.5), (-5, 2.5), (-1.5, 2.6), (2, 2.6), (4, 2.5), (-5.2, -0.5), (4.2, -0.5)]
    for i, (x, z) in enumerate(pos, 1):
        n = "farrapo_%d" % i
        m.osso(n, "saia", (x + 1, 3, z + 0.5))
        comp = 3 + (i * 7) % 4
        m.cubo(n, (x, 3 - comp, z), (2, comp, 1), "farrapo")
    # pés de osso
    m.cubo("root", (-3.5, 0, -3.5), (2, 1.5, 3), "osso")
    m.cubo("root", (1.5, 0, -3.5), (2, 1.5, 3), "osso")
    m.cubo("root", (-3, 1.5, -1), (1, 1.5, 1), "osso")
    m.cubo("root", (2, 1.5, -1), (1, 1.5, 1), "osso")

    # cabeça: crânio com restos de carne, mandíbula solta e capuz rasgado
    m.osso("cabeca", "corpo", (0, 22, 0))
    m.cubo("cabeca", (-3.5, 23, -3.5), (7, 7, 7), "cranio")
    m.cubo("cabeca", (-4, 24.5, -3), (1, 2, 4), "osso")     # maçãs do rosto
    m.cubo("cabeca", (3, 24.5, -3), (1, 2, 4), "osso")
    m.cubo("cabeca", (-1, 22, -1), (2, 1, 2), "osso")       # pescoço
    m.osso("mandibula", "cabeca", (0, 24, 1), (14, 0, 0))
    m.cubo("mandibula", (-3, 21.5, -3.6), (6, 2, 5), "mandibula")
    m.osso("capuz", "cabeca", (0, 26, 0))
    m.cubo("capuz", (-4.5, 29.5, -4.5), (9, 1.5, 9), "pano")
    m.cubo("capuz", (-4.5, 22.5, 3.5), (9, 7, 1.5), "pano")
    m.cubo("capuz", (-4.8, 22.5, -4.5), (1.3, 7, 8), "pano")
    m.cubo("capuz", (3.5, 22.5, -4.5), (1.3, 7, 8), "pano")
    m.cubo("capuz", (-1.5, 30, 3), (3, 2, 3), "pano", rot=(-25, 0, 0), pivo=(0, 30, 4))   # ponta do capuz
    m.cubo("capuz", (-2, 17, 4), (4, 6, 1), "farrapo")                                    # capa nas costas

    # braços magros: manga rasgada, antebraço de osso, mão ossuda
    for lado, nome in ((-1, "braco_d"), (1, "braco_e")):
        px = 5.5 * lado
        m.osso(nome, "corpo", (px, 21, 0))
        x0 = px - 1.5 if lado < 0 else px - 1.5
        m.cubo(nome, (x0, 15, -2), (3, 6, 4), "farrapo")
        m.cubo(nome, (px - 1, 9, -1), (2, 6.5, 2), "osso")
        m.cubo(nome, (px - 1.5, 7, -1.5), (3, 2, 3), "osso")
        for k in range(3):   # dedos
            m.cubo(nome, (px - 1.5 + k, 5.5, -1.5), (1, 1.5, 1), "osso")

    # a foice, na mão direita, com a pérola negra (a bateria da Morte) na junção da lâmina
    m.osso("foice", "braco_d", (-5.5, 8, 0))
    m.cubo("foice", (-6, -10, -0.5), (1, 50, 1), "haste")
    m.cubo("foice", (-6.5, -12, -1), (2, 2, 2), "metal")                          # ponteira
    m.cubo("foice", (-6.5, 37, -1), (2, 3, 2), "metal")                           # colar da lâmina
    # lâmina curva para a frente (-z) e para baixo
    seg = 8
    for i in range(seg):
        s = (i + 0.5) / seg
        z = -1.5 - i * 2.1
        y = 39.5 + 3.2 * math.sin(s * math.pi * 0.9) - 7.5 * s * s
        alt = 3.2 - 2.2 * s
        ang = math.degrees(math.atan2(3.2 * math.pi * 0.9 * math.cos(s * math.pi * 0.9) / seg - 15 * s / seg, 2.1))
        m.cubo("foice", (-5.75, y - alt / 2, z - 1.2), (0.5, alt, 2.4), "metal", rot=(ang, 0, 0), pivo=(-5.5, y, z))
    m.cubo("foice", (-6, 38, 1), (1, 3, 2.5), "metal", rot=(30, 0, 0), pivo=(-5.5, 39, 1.5))  # esporão de trás
    m.osso("perola", "foice", (-5.5, 38.5, -1.8))
    m.cubo("perola", (-7, 37.5, -3), (3, 2, 2.4), "perola")
    m.cubo("perola", (-6.5, 37, -3), (2, 3, 2.4), "perola")
    m.cubo("perola", (-6.5, 37.5, -3.5), (2, 2, 3.4), "perola")

    pintores = {"osso": p_osso, "cranio": p_cranio, "mandibula": p_mandibula, "pano": p_pano, "pano_torso": p_pano_torso,
                "farrapo": p_farrapo, "corda": p_corda, "metal": p_metal, "haste": p_haste, "perola": p_perola}
    m.salvar(4, 4, pintores, {"cranio": g_cranio, "perola": g_perola})


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
        if y == h // 2 - 1 and (x in (1, 2) or x in (w - 3, w - 2)):
            return rgba((168, 150, 170))
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


def embriao():
    m = Modelo("embriao_vida", 128, 128)
    m.osso("root")
    m.osso("corpo", "root", (0, 6, 0), (-12, 0, 0))
    # tronco curvado: costas arredondadas (+z), barriga (-z)
    m.cubo("corpo", (-2.5, 4, -2), (5, 5, 4.5), "pele")
    m.cubo("corpo", (-2.2, 4.5, -2.4), (4.4, 4, 5.2), "pele")
    m.cubo("corpo", (-2.5, 1, -1.5), (5, 4, 4.5), "pele", rot=(25, 0, 0), pivo=(0, 4, 0))
    m.cubo("corpo", (-2, 0.5, -0.5), (4, 3, 3.5), "pele", rot=(50, 0, 0), pivo=(0, 2, 0))
    m.cubo("corpo", (-1.5, 3.5, 2.2), (3, 6, 1), "pele", rot=(-8, 0, 0), pivo=(0, 6, 2.5))       # espinha nas costas
    # braços dobrados com as mãozinhas perto do rosto
    for lado in (-1, 1):
        x = 2.6 * lado
        m.cubo("corpo", (x - 0.8, 5.5, -1.5), (1.6, 3.5, 1.6), "pele", rot=(-50, 0, -10 * lado), pivo=(x, 8.5, -0.5))
        m.cubo("corpo", (x * 0.7 - 0.8, 6.5, -4.4), (1.6, 3, 1.6), "pele", rot=(-15, 0, 15 * lado), pivo=(x * 0.7, 6.8, -3.6))
        m.cubo("corpo", (x * 0.55 - 0.9, 9, -4.8), (1.8, 1.4, 1.2), "pele")                         # mãozinha
    # perninhas encolhidas (coxas para a frente, canelas para cima)
    for lado in (-1, 1):
        x = 1.6 * lado
        m.cubo("corpo", (x - 1.1, 0.5, -5.2), (2.2, 2.2, 5), "pele", rot=(15, 0, 0), pivo=(x, 1.5, 0))
        m.cubo("corpo", (x - 0.9, 0.8, -6.4), (1.8, 4.2, 1.8), "pele", rot=(20, 0, 0), pivo=(x, 1.5, -5.5))
        m.cubo("corpo", (x - 0.9, 4.6, -7.2), (1.8, 1, 2.4), "pele")                                 # pezinho
    # cabeça grande, inclinada para o peito
    m.osso("cabeca", "corpo", (0, 9, -0.5), (32, 0, 0))
    m.cubo("cabeca", (-3.5, 9.5, -4), (7, 6, 7), "rosto")
    m.cubo("cabeca", (-3, 9, -3.5), (6, 7, 6), "pele")
    m.cubo("cabeca", (-3, 10, -4.5), (6, 5, 8), "pele")
    m.cubo("cabeca", (-2.5, 15.5, -3), (5, 0.8, 5), "pele")
    # cordão umbilical: sai da barriga e desce torcendo
    pai, pv = "corpo", (0, 3, -2.3)
    for i in range(1, 9):
        n = "cordao_%d" % i
        m.osso(n, pai, pv, (18 + (i % 3) * 6, 12 * math.sin(i), 0))
        m.cubo(n, (pv[0] - 0.5, pv[1] - 2.2, pv[2] - 0.5), (1, 2.4, 1), "cordao")
        pai, pv = n, (pv[0], pv[1] - 2.2, pv[2])
    m.salvar(2, 2, {"pele": p_pele, "rosto": p_rosto, "cordao": p_cordao},
             {"pele": g_pele, "rosto": g_pele, "cordao": g_pele})


if __name__ == "__main__":
    necron()
    embriao()
    print("ok: necron e embriao_vida")

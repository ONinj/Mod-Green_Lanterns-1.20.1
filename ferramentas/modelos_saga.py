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
    m.cubo("foice", (-6, -4, -0.5), (1, 44, 1), "haste")
    m.cubo("foice", (-6.5, -6, -1), (2, 2, 2), "metal")                           # ponteira
    m.cubo("foice", (-6.5, 37, -1), (2, 3, 2), "metal")                           # colar da lâmina
    # lâmina curva para a frente (-z) e para baixo: segmentos que se encostam seguindo a curva
    pts = []
    n = 16
    for i in range(n + 1):
        u = i / n
        pts.append((39.5 + 3.4 * math.sin(u * math.pi * 0.85) - 6.5 * u * u, -1.0 - 16.0 * u))
    for i in range(n):
        (y0, z0), (y1, z1) = pts[i], pts[i + 1]
        cy, cz = (y0 + y1) / 2, (z0 + z1) / 2
        comp = math.hypot(y1 - y0, z1 - z0) + 0.35
        ang = math.degrees(math.atan2(y1 - y0, -(z1 - z0)))
        alt = 3.0 - 2.3 * (i / n)
        m.cubo("foice", (-5.75, cy - alt * 0.35, cz - comp / 2), (0.5, alt, comp), "metal", rot=(ANG_SINAL * ang, 0, 0), pivo=(-5.5, cy, cz))
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


if __name__ == "__main__":
    necron()
    embriao()
    print("ok: necron e embriao_vida")

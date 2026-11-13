"""Espécies dos Lanternas lendários (todas originais do mod):
  humano   humanos (três tons de pele e cabelo; e a versão corrompida, cinza com veias negras)
  vekthar  Amarelos: pele cinza-cinza, crista óssea alta, olhos dourados brilhando
  brakhul  Vermelhos (corpo grande): pele carvão com rachaduras incandescentes, chifres para frente e presas
  gobrin   Laranjas (corpo pequeno): pele oliva, orelhas enormes, nariz comprido e olhos amarelos
  serenim  Azuis: pele azul-clara, olhos brancos brilhando, duas barbatanas para trás e uma gema na testa
  nhai     Índigo: pele escura com tatuagens índigo brilhando e um coque alto

Para cada uma: geo/entity/especie_<id>.geo.json (o corpo do tipo + peças extras na cabeça, com UV próprio),
textures/entity/especie_<id>_v<n>.png (+ _glowmask) e animations/entity/especie_<id>.animation.json.
Rodar da raiz do mod:  python3 tools/gerar_especies_lendarias.py
"""
import copy, json, math, random
from PIL import Image

A = "src/main/resources/assets/lanterna/"


class Atlas:
    """Reserva retângulos livres na textura (da linha 168 para baixo)."""

    def __init__(self):
        self.x, self.y, self.h = 0, 168, 0

    def pega(self, w, h):
        w, h = int(math.ceil(w)), int(math.ceil(h))
        if self.x + w > 256:
            self.x, self.y, self.h = 0, self.y + self.h + 1, 0
        r = (self.x, self.y)
        self.x += w + 1
        self.h = max(self.h, h)
        assert self.y + h <= 256, "textura cheia"
        return r


def faces_box(atlas, size, px=2):
    """UV por face para um cubo, cada face no seu retângulo."""
    w, h, d = size
    dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    out = {}
    for f, (a, b) in dims.items():
        aw, bh = max(1, a * px), max(1, b * px)
        x, y = atlas.pega(aw, bh)
        out[f] = {"uv": [x, y], "uv_size": [aw, bh]}
    return out


def ruido(seed):
    r = random.Random(seed)
    return lambda: r.uniform(-1, 1)


def cor_mais(c, k):
    return tuple(max(0, min(255, int(v + k))) for v in c[:3])


def pinta(px, uv, fn):
    x0, y0 = int(math.floor(uv["uv"][0])), int(math.floor(uv["uv"][1]))
    w, h = int(math.ceil(uv["uv_size"][0])), int(math.ceil(uv["uv_size"][1]))
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if 0 <= x < 256 and 0 <= y < 256:
                c = fn((x - x0 + 0.5) / w, (y - y0 + 0.5) / h)
                if c is not None:
                    px[x, y] = c if len(c) == 4 else c + (255,)


LUZ = {"up": 22, "north": 6, "south": -4, "east": 0, "west": 0, "down": -26}

# ------------------------------------------------------------------ desenho do rosto (32x32, face da frente)


def olhos(px, gx, x0, y0, cor, iris, brilha, largura=4, linha=13, sclera=(240, 236, 228)):
    for lado in (-1, 1):
        cx = x0 + 16 + lado * 6
        for dx in range(-largura // 2, largura - largura // 2):
            for dy in range(0, 2):
                x, y = cx + dx, y0 + linha + dy
                px[x, y] = (sclera + (255,)) if sclera else (iris + (255,))
        for dx in range(0, 2):
            for dy in range(0, 2):
                x, y = cx - 1 + dx + (1 if lado < 0 else 0), y0 + linha + dy
                px[x, y] = iris + (255,)
                if brilha:
                    gx[x, y] = cor_mais(iris, 40) + (255,)


def rosto(px, gx, uv, especie, pele, cabelo, olho, var):
    x0, y0 = int(uv["uv"][0]), int(uv["uv"][1])
    n = ruido(hash((especie, var)) & 0xFFFF)
    for y in range(32):
        for x in range(32):
            px[x0 + x, y0 + y] = cor_mais(pele, n() * 6 - (6 if y > 24 else 0)) + (255,)
    escuro = cor_mais(pele, -45)
    if especie == "humano":
        # cabelo: franja e laterais
        for y in range(0, 8):
            for x in range(32):
                if y < 5 or (x < 4 or x > 27) or (y < 8 and (x * 7 + var) % 5 == 0):
                    px[x0 + x, y0 + y] = cor_mais(cabelo, n() * 8) + (255,)
        if var == 2:  # cabelo comprido: desce pelas laterais
            for y in range(8, 26):
                for x in list(range(0, 4)) + list(range(28, 32)):
                    px[x0 + x, y0 + y] = cor_mais(cabelo, n() * 8) + (255,)
        # sobrancelhas
        for lado in (-1, 1):
            for dx in range(-2, 3):
                px[x0 + 16 + lado * 6 + dx, y0 + 11] = cor_mais(cabelo, -10) + (255,)
        olhos(px, gx, x0, y0, None, olho, False)
        # nariz e boca
        for dy in range(15, 20):
            px[x0 + 16, y0 + dy] = cor_mais(pele, -18) + (255,)
        px[x0 + 15, y0 + 20] = px[x0 + 17, y0 + 20] = cor_mais(pele, -28) + (255,)
        for dx in range(-3, 4):
            px[x0 + 16 + dx, y0 + 24] = (150, 80, 76, 255) if var != 3 else (60, 40, 50, 255)
        if var == 3:  # corrompido: veias escuras e olhos sem cor
            r = random.Random(7)
            for _ in range(10):
                x, y = r.randrange(32), r.randrange(8, 32)
                for k in range(5):
                    if 0 <= x < 32 and 0 <= y < 32:
                        px[x0 + x, y0 + y] = (20, 20, 26, 255)
                    x += r.choice((-1, 0, 1)); y += 1
            for lado in (-1, 1):
                for dx in range(-2, 2):
                    for dy in range(2):
                        px[x0 + 16 + lado * 6 + dx, y0 + 13 + dy] = (230, 230, 240, 255)
                        gx[x0 + 16 + lado * 6 + dx, y0 + 13 + dy] = (200, 210, 230, 255)
    elif especie == "vekthar":
        for x in range(32):  # sobrancelha óssea pesada
            for y in (10, 11):
                px[x0 + x, y0 + y] = escuro + (255,)
        for lado in (-1, 1):  # olhos estreitos dourados
            for dx in range(-3, 3):
                x, y = x0 + 16 + lado * 6 + dx, y0 + 13
                px[x, y] = (255, 214, 60, 255); gx[x, y] = (255, 214, 60, 255)
        for dx in range(-4, 5):  # boca fina
            px[x0 + 16 + dx, y0 + 24] = cor_mais(pele, -60) + (255,)
        for y in range(16, 30):  # placas no queixo
            for x in (9, 22):
                px[x0 + x, y0 + y] = escuro + (255,)
    elif especie == "brakhul":
        r = random.Random(3)
        for _ in range(14):  # rachaduras incandescentes
            x, y = r.randrange(32), r.randrange(32)
            for k in range(7):
                if 0 <= x < 32 and 0 <= y < 32:
                    px[x0 + x, y0 + y] = (255, 70, 20, 255); gx[x0 + x, y0 + y] = (255, 90, 30, 255)
                x += r.choice((-1, 0, 1)); y += r.choice((0, 1))
        for x in range(4, 28):
            for y in (9, 10, 11):
                px[x0 + x, y0 + y] = (14, 10, 10, 255)
        for lado in (-1, 1):
            for dx in range(-2, 2):
                for dy in range(2):
                    x, y = x0 + 16 + lado * 6 + dx, y0 + 12 + dy
                    px[x, y] = (255, 40, 20, 255); gx[x, y] = (255, 60, 30, 255)
        for x in range(6, 26):  # bocarra com dentes
            for y in range(21, 27):
                dente = (y in (21, 26)) and (x % 3 != 0)
                px[x0 + x, y0 + y] = (235, 220, 190, 255) if dente else (40, 6, 6, 255)
    elif especie == "gobrin":
        for lado in (-1, 1):
            for dx in range(-3, 3):
                for dy in range(-1, 3):
                    x, y = x0 + 16 + lado * 7 + dx, y0 + 12 + dy
                    px[x, y] = (250, 210, 40, 255)
            px[x0 + 16 + lado * 7, y0 + 12] = px[x0 + 16 + lado * 7 - 1, y0 + 12] = (20, 14, 6, 255)
        for dx in range(-7, 8):  # sorriso largo cheio de dentes
            px[x0 + 16 + dx, y0 + 22] = (60, 20, 10, 255)
            px[x0 + 16 + dx, y0 + 23] = (240, 230, 190, 255) if dx % 2 else (60, 20, 10, 255)
    elif especie == "serenim":
        for lado in (-1, 1):
            for dx in range(-2, 3):
                for dy in range(0, 3):
                    x, y = x0 + 16 + lado * 6 + dx, y0 + 12 + dy
                    px[x, y] = (240, 252, 255, 255); gx[x, y] = (210, 240, 255, 255)
        for dx in range(-2, 3):
            px[x0 + 16 + dx, y0 + 23] = cor_mais(pele, -40) + (255,)
        for dy in range(4, 8):  # gema na testa
            for dx in range(-1, 2):
                px[x0 + 16 + dx, y0 + dy] = (150, 220, 255, 255); gx[x0 + 16 + dx, y0 + dy] = (170, 230, 255, 255)
    elif especie == "nhai":
        for y in range(0, 7):  # cabelo
            for x in range(32):
                px[x0 + x, y0 + y] = cor_mais(cabelo, n() * 6) + (255,)
        olhos(px, gx, x0, y0, None, (60, 40, 30), False)
        for y in range(16, 30):  # tatuagens verticais
            for x in (8, 23):
                px[x0 + x, y0 + y] = (130, 90, 255, 255); gx[x0 + x, y0 + y] = (150, 110, 255, 255)
        for x in range(10, 22):
            px[x0 + x, y0 + 9] = (130, 90, 255, 255); gx[x0 + x, y0 + 9] = (150, 110, 255, 255)
        for dx in range(-2, 3):
            px[x0 + 16 + dx, y0 + 24] = cor_mais(pele, -40) + (255,)


# ------------------------------------------------------------------ espécies

ESPECIES = {
    # id: corpo, peles [(pele, cabelo, olho)], extras
    "humano": ("padrao", [((228, 186, 152), (92, 60, 34), (70, 110, 160)), ((176, 120, 84), (30, 20, 16), (60, 40, 26)),
                          ((236, 200, 170), (210, 160, 90), (60, 120, 70))], "humano"),
    "vekthar": ("padrao", [((142, 142, 150), None, None)], "crista"),
    "brakhul": ("grande", [((48, 40, 40), None, None)], "chifres"),
    "gobrin": ("pequeno", [((128, 120, 58), None, None)], "orelhas"),
    "serenim": ("padrao", [((150, 196, 226), None, None)], "barbatanas"),
    "nhai": ("padrao", [((96, 62, 42), (24, 16, 14), None)], "coque"),
}


def extras(tipo, head_top, head_front, s):
    """Cubos extras (origin absoluto, size) presos na cabeça. s = escala do corpo."""
    t, f = head_top, head_front
    if tipo == "humano":
        return [("cabelo", [-3.1, t - 2.2, 1.0], [6.2, 2.4, 2.6])]  # nuca
    if tipo == "crista":
        return [("crista", [-0.5, t, f + 1.0], [1.0, 3.0, 6.0]), ("crista_alto", [-0.4, t + 3.0, f + 3.0], [0.8, 1.6, 4.5]),
                ("espinho_r", [-3.6, t - 1.5, f + 4.5], [0.8, 0.8, 3.0]), ("espinho_l", [2.8, t - 1.5, f + 4.5], [0.8, 0.8, 3.0])]
    if tipo == "chifres":
        return [("chifre_r", [-5.6, t - 1.5, f + 1.0], [1.6, 1.6, 2.5]), ("chifre_r2", [-6.0, t - 0.2, f - 1.2], [1.2, 1.2, 2.4]),
                ("chifre_r3", [-6.2, t + 1.0, f - 2.8], [0.9, 0.9, 1.8]),
                ("chifre_l", [4.0, t - 1.5, f + 1.0], [1.6, 1.6, 2.5]), ("chifre_l2", [4.8, t - 0.2, f - 1.2], [1.2, 1.2, 2.4]),
                ("chifre_l3", [5.3, t + 1.0, f - 2.8], [0.9, 0.9, 1.8]),
                ("presa_r", [-2.6, t - 7.0, f - 0.4], [0.7, 1.6, 0.7]), ("presa_l", [1.9, t - 7.0, f - 0.4], [0.7, 1.6, 0.7])]
    if tipo == "orelhas":
        return [("orelha_r", [-6.4, t - 3.0, f + 1.6], [3.6, 2.6, 0.6]), ("orelha_l", [2.8, t - 3.0, f + 1.6], [3.6, 2.6, 0.6]),
                ("nariz", [-0.5, t - 3.4, f - 1.6], [1.0, 1.0, 1.8])]
    if tipo == "barbatanas":
        return [("barbatana_r", [-2.4, t - 1.0, f + 5.5], [0.6, 2.4, 4.5]), ("barbatana_l", [1.8, t - 1.0, f + 5.5], [0.6, 2.4, 4.5]),
                ("barbatana_m", [-0.3, t, f + 3.0], [0.6, 1.4, 5.0])]
    if tipo == "coque":
        return [("coque", [-1.2, t, f + 3.2], [2.4, 2.6, 2.4]), ("coque_ponta", [-0.7, t + 2.6, f + 3.7], [1.4, 1.4, 1.4])]
    return []


def cor_extra(tipo, nome, pele, cabelo):
    if tipo == "humano" or nome.startswith("coque"):
        return cabelo
    if tipo == "crista":
        return (96, 92, 100)
    if tipo == "chifres":
        return (220, 200, 160) if nome.startswith("presa") else (30, 22, 20)
    if tipo == "barbatanas":
        return (120, 170, 220)
    return pele


def gerar(eid, corpo, peles, tipo):
    base = json.load(open(A + f"geo/entity/corpo_{corpo}.geo.json"))
    geo = copy.deepcopy(base)
    g = geo["minecraft:geometry"][0]
    g["description"]["identifier"] = f"geometry.especie_{eid}"
    atlas = Atlas()
    head = next(b for b in g["bones"] if b["name"] == "head")
    # rosto: a face da frente do cubo mais à frente da cabeça vai para um quadro 32x32 próprio
    frente = min(head["cubes"], key=lambda c: c["origin"][2])
    rosto_uv = {"uv": [224, 224], "uv_size": [32, 32]}
    head_faces = []
    for c in head["cubes"]:
        c["uv"] = faces_box(atlas, c["size"])
        if c is frente:
            c["uv"]["north"] = copy.deepcopy(rosto_uv)
        head_faces.append(c)
    top = max(c["origin"][1] + c["size"][1] for c in head["cubes"])
    front = min(c["origin"][2] for c in head["cubes"])
    novos = []
    for nome, origin, size in extras(tipo, top, front, 1):
        cube = {"origin": origin, "size": size, "uv": faces_box(atlas, size)}
        novos.append((nome, cube))
        g["bones"].append({"name": f"extra_cabeca_{nome}", "parent": "head", "pivot": head["pivot"], "cubes": [cube]})
    json.dump(geo, open(A + f"geo/entity/especie_{eid}.geo.json", "w"), indent=1)
    json.dump({"format_version": "1.8.0", "animations": {"misc.extra": {"loop": True, "animation_length": 4, "bones": {}}}},
              open(A + f"animations/entity/especie_{eid}.animation.json", "w"), indent=1)

    variantes = list(enumerate(peles, 1))
    if eid == "humano":
        variantes.append(("corrompido", ((150, 150, 158), (16, 16, 20), (230, 230, 240))))
    for v, (pele, cabelo, olho) in variantes:
        cabelo = cabelo or cor_mais(pele, -40)
        im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        gl = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        px, gx = im.load(), gl.load()
        n = ruido(hash((eid, v)) & 0xFFFF)
        # corpo, braços e pernas: pele (a roupa cobre quase tudo; mãos e pescoço aparecem)
        for b in g["bones"]:
            if b["name"] == "head" or b["name"].startswith(("extra_", "roupa_", "capa")):
                continue
            for c in b.get("cubes", []):
                if not isinstance(c.get("uv"), dict):
                    continue
                for f, uv in c["uv"].items():
                    pinta(px, uv, lambda a, bb, f=f: cor_mais(pele, LUZ[f] + n() * 5))
        # cabeça
        humano = eid == "humano"
        for c in head_faces:
            for f, uv in c["uv"].items():
                if c is frente and f == "north":
                    continue
                def fn(a, bb, f=f):
                    base_c = cor_mais(pele, LUZ[f] + n() * 5)
                    if humano or eid == "nhai":
                        if f == "up" or f == "south" or (f in ("east", "west") and bb < 0.45):
                            return cor_mais(cabelo, LUZ[f] * 0.5 + n() * 8)
                    if eid == "brakhul" and n() > 0.86:
                        return (255, 70, 20)
                    return base_c
                pinta(px, uv, fn)
        rosto(px, gx, frente["uv"]["north"], eid, pele, cabelo, olho or (60, 60, 60), v if v != "corrompido" else 3)
        # extras
        for nome, cube in novos:
            ce = cor_extra(tipo, nome, pele, cabelo)
            for f, uv in cube["uv"].items():
                pinta(px, uv, lambda a, bb, f=f: cor_mais(ce, LUZ[f] + n() * 6 + (25 if bb < 0.2 else 0)))
                if eid == "serenim" and nome.startswith("barbatana"):
                    pinta(gx, uv, lambda a, bb: (90, 160, 230, 255) if bb < 0.15 else None)
        # corpo do brakhul: rachaduras incandescentes também
        if eid == "brakhul":
            r = random.Random(11)
            for _ in range(500):
                x, y = r.randrange(256), r.randrange(0, 128)
                if px[x, y][3] > 0:
                    px[x, y] = (255, 80, 20, 255); gx[x, y] = (255, 90, 30, 255)
        # o GeckoLib exige pelo menos um pixel no glowmask: um quase invisível no canto
        if gx[0, 255][3] == 0:
            gx[0, 255] = (0, 0, 0, 1)
        nome = f"corrupcao_{eid}" if v == "corrompido" else f"especie_{eid}_v{v}"
        im.save(A + f"textures/entity/{nome}.png")
        gl.save(A + f"textures/entity/{nome}_glowmask.png")
    # espécies com uma pele só: as variantes 2 e 3 repetem a 1 (o jogo sorteia de 1 a 3)
    if len(peles) == 1:
        for v in (2, 3):
            for suf in ("", "_glowmask"):
                Image.open(A + f"textures/entity/especie_{eid}_v1{suf}.png").save(A + f"textures/entity/especie_{eid}_v{v}{suf}.png")
    if eid != "humano":
        for suf in ("", "_glowmask"):
            Image.open(A + f"textures/entity/especie_{eid}_v1{suf}.png").save(A + f"textures/entity/corrupcao_{eid}{suf}.png")
    print("ok", eid)


if __name__ == "__main__":
    for eid, (corpo, peles, tipo) in ESPECIES.items():
        gerar(eid, corpo, peles, tipo)

"""Texturas dos Lanternas lendários (todas originais):
  - trajes: roupa_lendario_<id>_<corpo>.png (+ _glowmask), a partir do traje da Tropa, com capa, gola e ombreiras próprias;
  - itens: insignia_<id>.png e fragmento_lendario.png (16x16);
  - planetas no espaço: lend_<planeta>.png e hanks.png (com o símbolo amarelo do próprio mod).
Rodar da raiz do mod:  python3 tools/gerar_lendarios.py
"""
import json, math, random, sys
import numpy as np
from PIL import Image, ImageDraw

A = "src/main/resources/assets/lanterna/"
sys.path.insert(0, "tools")
import gerar_planetas as gp  # ruído esférico e utilidades

LENDAS = {
    # id: (tropa, corpo, desenho)
    "hal_jordan": ("green", "padrao"),
    "larfleeze": ("orange", "pequeno"),
    "atrocitus": ("red", "grande"),
    "santo_andarilho": ("blue", "padrao"),
    "indigo_1": ("indigo", "padrao"),
    "carol_ferris": ("violet", "padrao"),
    "mao_negra": ("black", "padrao"),
    "sinestro": ("yellow", "padrao"),
}
COR = {"green": (43, 255, 74), "yellow": (255, 224, 26), "red": (224, 16, 16), "blue": (42, 123, 255), "violet": (194, 59, 255),
       "indigo": (91, 59, 216), "orange": (255, 138, 26), "black": (58, 58, 68)}


def uvs(corpo):
    d = json.load(open(A + f"geo/entity/corpo_{corpo}.geo.json"))
    out = {}
    for b in d["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            if isinstance(c.get("uv"), dict):
                out[b["name"]] = {k: (f["uv"][0], f["uv"][1], f["uv_size"][0], f["uv_size"][1]) for k, f in c["uv"].items()}
    return out


def rect(f):
    x, y, w, h = f
    return int(math.floor(x)), int(math.floor(y)), int(math.ceil(x + w)), int(math.ceil(y + h))


def pinta(px, f, fn):
    """fn(u, v) -> cor RGBA ou None, com u e v de 0 a 1 dentro da face."""
    x0, y0, x1, y1 = rect(f)
    for y in range(y0, y1):
        for x in range(x0, x1):
            u = (x - x0 + 0.5) / max(1, x1 - x0)
            v = (y - y0 + 0.5) / max(1, y1 - y0)
            c = fn(u, v)
            if c is not None and 0 <= x < 256 and 0 <= y < 256:
                px[x, y] = c


def escuro(c, k):
    return tuple(int(v * k) for v in c[:3]) + (255,)


def traje(lid, tropa, corpo):
    base = Image.open(A + f"textures/entity/roupa_tropa_{tropa}_{corpo}.png").convert("RGBA")
    glow = Image.open(A + f"textures/entity/roupa_tropa_{tropa}_{corpo}_glowmask.png").convert("RGBA")
    px, gx = base.load(), glow.load()
    u = uvs(corpo)
    cor = COR[tropa]
    rnd = random.Random(lid)
    preto = (18, 18, 22, 255)

    def capa(fora, dentro, borda, rasgada=False, listras=None, brilho=None):
        cp = u["capa"]
        # a face "south" é a de fora (a que aparece nas costas); a "north" fica virada para o corpo
        for face, c in (("south", fora), ("north", dentro)):
            def fn(a, b, c=c):
                if rasgada and b > 0.82 and rnd.random() < (b - 0.82) * 4:
                    return (0, 0, 0, 0)
                if a < 0.08 or a > 0.92:
                    return borda
                if listras and int(b * listras) % 2 == 0 and 0.3 < a < 0.7:
                    return escuro(c, 0.6)
                return escuro(c, 1 - b * 0.25)
            pinta(px, cp[face], fn)
            if brilho:
                pinta(gx, cp[face], lambda a, b: brilho if (a < 0.08 or a > 0.92) and not (rasgada and b > 0.85) else None)
        for face in ("east", "west", "up", "down"):
            pinta(px, cp[face], lambda a, b: borda)

    def ombreiras(c, borda, brilho=None):
        for n in ("roupa_ombreira_r", "roupa_ombreira_l"):
            for face, f in u[n].items():
                pinta(px, f, lambda a, b: borda if (a < 0.15 or a > 0.85 or b < 0.15) else c)
                if brilho:
                    pinta(gx, f, lambda a, b: brilho if b < 0.15 else None)

    def gola(c, borda, brilho=None):
        for face, f in u["roupa_gola"].items():
            pinta(px, f, lambda a, b: borda if b < 0.25 else c)
            if brilho:
                pinta(gx, f, lambda a, b: brilho if b < 0.25 else None)

    luz = cor + (255,)
    if lid == "hal_jordan":
        ombreiras((20, 70, 30, 255), luz, luz)
        gola(preto, luz, luz)
    elif lid == "larfleeze":
        capa((200, 90, 10, 255), (90, 40, 6, 255), (255, 200, 60, 255), rasgada=True, brilho=(255, 170, 40, 255))
        gola((214, 168, 40, 255), (255, 220, 90, 255))
    elif lid == "atrocitus":
        capa((120, 6, 8, 255), (40, 4, 4, 255), (20, 10, 10, 255), listras=7, brilho=None)
        ombreiras((60, 8, 8, 255), (230, 30, 20, 255), (255, 60, 30, 255))
    elif lid == "santo_andarilho":
        capa((30, 80, 190, 255), (14, 30, 80, 255), (230, 240, 255, 255), brilho=(150, 210, 255, 255))
        gola((230, 240, 255, 255), (120, 190, 255, 255), (150, 210, 255, 255))
    elif lid == "indigo_1":
        capa((60, 36, 150, 255), (24, 14, 60, 255), (150, 110, 255, 255), brilho=(170, 130, 255, 255))
        # glifos brilhando na capa
        cp = u["capa"]
        pinta(gx, cp["south"], lambda a, b: (180, 140, 255, 255) if (int(a * 6) + int(b * 9)) % 5 == 0 and 0.15 < a < 0.85 else None)
    elif lid == "carol_ferris":
        ombreiras((200, 60, 200, 255), (255, 170, 255, 255), (255, 150, 255, 255))
        gola((120, 30, 140, 255), (255, 170, 255, 255), (255, 150, 255, 255))
    elif lid == "mao_negra":
        capa((14, 14, 16, 255), (4, 4, 6, 255), (90, 90, 96, 255), listras=10)
        ombreiras((30, 30, 34, 255), (150, 150, 160, 255))
    elif lid == "sinestro":
        capa((20, 18, 14, 255), (220, 180, 20, 255), (255, 214, 40, 255), brilho=(255, 210, 40, 255))
        gola((24, 22, 18, 255), (255, 214, 40, 255), (255, 214, 40, 255))
        ombreiras((40, 34, 18, 255), (255, 214, 40, 255), (255, 214, 40, 255))
    base.save(A + f"textures/entity/roupa_lendario_{lid}_{corpo}.png")
    glow.save(A + f"textures/entity/roupa_lendario_{lid}_{corpo}_glowmask.png")
    print("traje", lid)


# ------------------------------------------------------------------ itens

def insignia(lid, tropa):
    """Insígnia: um medalhão com um desenho geométrico diferente para cada lendário."""
    S = 16
    im = Image.new("RGBA", (S * 8, S * 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = COR[tropa]
    cl = tuple(min(255, int(v * 0.6 + 110)) for v in c)
    ce = tuple(int(v * 0.45) for v in c)
    d.ellipse((10, 10, 118, 118), fill=ce + (255,), outline=(30, 30, 34, 255), width=8)
    d.ellipse((22, 22, 106, 106), outline=c + (255,), width=8)
    m = 64
    if lid == "hal_jordan":
        d.rectangle((44, 30, 84, 38), fill=cl); d.rectangle((44, 90, 84, 98), fill=cl); d.ellipse((40, 40, 88, 88), outline=cl, width=10)
    elif lid == "larfleeze":
        d.polygon([(m, 26), (96, m), (m, 102), (32, m)], fill=cl); d.polygon([(m, 44), (80, m), (m, 84), (48, m)], fill=ce)
    elif lid == "atrocitus":
        d.polygon([(m, 26), (90, 60), (78, 100), (50, 100), (38, 60)], fill=cl); d.line((m, 40, m, 92), fill=ce, width=8)
    elif lid == "santo_andarilho":
        for k in range(3):
            d.arc((30 + k * 10, 30 + k * 10, 98 - k * 10, 98 - k * 10), 200, 340, fill=cl, width=6)
        d.ellipse((54, 70, 74, 90), fill=cl)
    elif lid == "indigo_1":
        d.polygon([(m, 28), (92, 96), (36, 96)], outline=cl, width=8); d.ellipse((56, 64, 72, 80), fill=cl)
    elif lid == "carol_ferris":
        d.polygon([(m, 28), (74, 54), (100, m), (74, 74), (m, 100), (54, 74), (28, m), (54, 54)], fill=cl)
    elif lid == "mao_negra":
        d.rectangle((40, 34, 88, 94), outline=(170, 170, 180, 255), width=8); d.line((40, 64, 88, 64), fill=(170, 170, 180, 255), width=8)
    elif lid == "sinestro":
        d.polygon([(m, 26), (100, 50), (m, 102), (28, 50)], outline=cl, width=8); d.rectangle((52, 46, 76, 82), fill=cl)
    im = im.resize((S, S), Image.LANCZOS)
    im.save(A + f"textures/item/insignia_{lid}.png")


def fragmento():
    im = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cores = [COR[k] for k in ("green", "yellow", "red", "blue", "violet", "indigo", "orange")]
    pts = [(64, 8), (100, 40), (90, 112), (40, 120), (24, 60)]
    d.polygon(pts, fill=(235, 235, 245, 255), outline=(60, 60, 70, 255))
    for i, c in enumerate(cores):
        a = i / 7 * math.pi * 2
        d.line((64, 64, 64 + math.cos(a) * 40, 64 + math.sin(a) * 40), fill=c + (255,), width=9)
    d.ellipse((50, 50, 78, 78), fill=(255, 255, 255, 255))
    im.resize((16, 16), Image.LANCZOS).save(A + "textures/item/fragmento_lendario.png")


def modelos():
    for lid in list(LENDAS) + ["fragmento_lendario"]:
        nome = lid if lid == "fragmento_lendario" else "insignia_" + lid
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "lanterna:item/" + nome}},
                  open(A + f"models/item/{nome}.json", "w"), indent=1)


# ------------------------------------------------------------------ planetas

PLANETAS = {
    # planeta: (rampa de cores, mar, nuvens)
    "vigilia": ([(0, (10, 50, 40)), (0.45, (20, 110, 80)), (0.5, (60, 140, 70)), (0.75, (40, 100, 50)), (1, (210, 230, 210))], 0.48, (220, 255, 230)),
    "tesouro": ([(0, (120, 70, 20)), (0.4, (200, 140, 50)), (0.7, (240, 190, 80)), (0.9, (255, 220, 120)), (1, (255, 240, 190))], -1, (255, 200, 120)),
    "fornalha": ([(0, (255, 80, 10)), (0.35, (200, 30, 10)), (0.4, (40, 16, 14)), (0.7, (70, 30, 26)), (1, (120, 60, 50))], -1, None),
    "remanso": ([(0, (6, 30, 90)), (0.6, (30, 100, 200)), (0.66, (200, 220, 240)), (0.8, (120, 170, 220)), (1, (250, 250, 255))], 0.64, (240, 250, 255)),
    "silencio": ([(0, (30, 14, 70)), (0.4, (70, 40, 150)), (0.65, (150, 110, 230)), (0.85, (210, 180, 255)), (1, (255, 255, 255))], -1, None),
    "coracao": ([(0, (60, 10, 60)), (0.45, (160, 40, 150)), (0.5, (220, 120, 220)), (0.8, (150, 60, 170)), (1, (255, 210, 250))], 0.47, (255, 220, 250)),
    "necropole": ([(0, (8, 8, 10)), (0.5, (34, 34, 40)), (0.8, (70, 70, 78)), (1, (130, 130, 140))], -1, None),
}


def planeta(nome, rampa, mar, nuvem, seed):
    xw, yw, zw = gp.warp(1.4, seed, 0.9)
    h = gp.norm01(gp.fbm(1.8, seed + 1, 7, xw, yw, zw))
    img = gp.ramp(h, rampa)
    img = gp.shade(img, np.maximum(h, mar if mar > 0 else 0), 0.9)
    if nome == "fornalha":
        rg = gp.ridged(2.8, seed + 2, 6)
        img = gp.mix(img, (255, 120, 20), np.clip((rg - 0.8) * 9, 0, 1) * (h > 0.4))
    if nome == "silencio":
        img = gp.mix(img, (190, 150, 255), np.clip((gp.ridged(4, seed + 3, 4) - 0.85) * 8, 0, 1))
    if nome == "necropole":
        img = gp.craters(img, seed + 4, 120, 0.09, (4, 4, 6), (110, 110, 120))
        img = gp.mix(img, (60, 220, 120), np.clip((gp.ridged(5, seed + 5, 3) - 0.92) * 12, 0, 1) * 0.6)
    if nome == "tesouro":
        img = gp.mix(img, (255, 230, 120), np.clip((gp.fbm(20, seed + 6, 2) - 0.72) * 8, 0, 1))
    if nuvem:
        img = gp.clouds(img, seed + 7, 0.45, nuvem, 0.58)
    gp.save("lend_" + nome, img)


def hanks():
    """Hanks: esfera de placas escuras com sulcos, veios e aberturas de luz amarela, e o símbolo amarelo do mod."""
    X, Y, Z, TH, PH = gp.X, gp.Y, gp.Z, gp.TH, gp.PH
    lat = (TH / np.pi) * 32
    lon = (PH / (2 * np.pi)) * 64
    n = gp.fbm(3, 501, 4)
    placa = 40 + 30 * gp.norm01(gp.fbm(9, 502, 3))
    img = np.stack([placa * 1.0, placa * 0.96, placa * 0.9], -1)
    # costuras entre as placas (grade irregular) e rebites
    cost = (np.abs(lat - np.round(lat)) < 0.06) | (np.abs(lon - np.round(lon + (np.floor(lat) % 2) * 0.5)) < 0.05)
    img = np.where(cost[..., None], img * 0.45, img)
    # sulcos largos (cânions mecânicos) com luz no fundo
    sul = np.abs(np.sin(PH * 6 + np.sin(TH * 3) * 1.2)) < 0.035
    eq = np.abs(Y) < 0.04
    luz = (sul & (np.abs(Y) < 0.8)) | eq
    img = gp.mix(img, (255, 200, 40), luz * 0.9)
    # aberturas brilhando (como janelas e respiros)
    ab = (gp.fbm(14, 503, 2) > 0.7) & ~cost
    img = gp.mix(img, (255, 214, 60), ab * 0.85)
    # anéis de fortaleza perto dos polos
    for p in (0.86, -0.86):
        img = gp.mix(img, (255, 200, 40), (np.abs(Y - p) < 0.012) * 0.9)
    # o símbolo amarelo do mod (lua crescente com três raios) em linhas de luz, num lado do planeta
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(im)
    cx, cy, r = 256, 230, 46
    d.arc((cx - r, cy - r, cx + r, cy + r), 60, 300, fill=(255, 220, 60), width=9)
    d.arc((cx - r + 22, cy - r + 8, cx + r + 6, cy + r - 8), 80, 280, fill=(255, 220, 60), width=5)
    for k in (-1, 0, 1):
        a = math.radians(180 + k * 32)
        d.line((cx + math.cos(a) * (r + 8), cy + math.sin(a) * (r + 8), cx + math.cos(a) * (r + 34), cy + math.sin(a) * (r + 34)),
               fill=(255, 220, 60), width=7)
    im.save(A + "textures/space/hanks.png", optimize=True)
    print("ok hanks")


if __name__ == "__main__" and sys.argv[1:] == ["hanks"]:
    hanks()
elif __name__ == "__main__" and sys.argv[1:] == ["trajes"]:
    for lid, (t, c) in LENDAS.items():
        traje(lid, t, c)
elif __name__ == "__main__":
    for lid, (t, c) in LENDAS.items():
        traje(lid, t, c)
        insignia(lid, t)
    fragmento()
    modelos()
    for i, (n, (r, m, nv)) in enumerate(PLANETAS.items()):
        planeta(n, r, m, nv, 700 + i * 37)
    hanks()

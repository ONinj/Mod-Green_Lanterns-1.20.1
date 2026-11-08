import sys, os, json, shutil
sys.path.insert(0, "/home/claude/aneis"); sys.path.insert(0, "/home/claude/unif2")
from PIL import Image, ImageDraw
import anel, prev
from previa import font as F
OUT = "/home/claude/Aneis_HD"
if os.path.exists(OUT): shutil.rmtree(OUT)
M = f"{OUT}/assets/lanterna/models/item/ring"; T = f"{OUT}/assets/lanterna/textures/item/ring"
for d in (M, T, f"{OUT}/previas", f"{OUT}/gerador"): os.makedirs(d, exist_ok=True)
NOME = dict(verde="Vontade", amarelo="Medo", vermelho="Fúria", laranja="Avareza", azul="Esperança", indigo="Compaixão", violeta="Amor", preto="Morte", branco="Vida")
cards = {}; stats = {}
for cor in anel.CORES:
    m, t = anel.construir(cor); n = anel.ARQ[cor]
    for e in m["elements"]:
        for fc in e["faces"].values(): assert all(0 <= v <= 16 for v in fc["uv"])
    json.dump(m, open(f"{M}/{n}.json", "w"), indent=1); t.save(f"{T}/{n}.png")
    stats[cor] = len(m["elements"])
    v = prev.vistas(m, t, size=520); cards[cor] = v
    W = Image.new("RGBA", (4 * 520, 580), (16, 18, 24, 255)); d = ImageDraw.Draw(W)
    for i, im in enumerate(v): W.alpha_composite(im, (i * 520, 50))
    d.text((1040, 25), f"Anel da {NOME[cor]} ({n}) — {stats[cor]} elementos", font=F(26), fill=(235, 235, 245), anchor="mm")
    W.convert("RGB").save(f"{OUT}/previas/{n}.png")
    print(cor, stats[cor])
G = Image.new("RGBA", (9 * 300, 680), (16, 18, 24, 255)); d = ImageDraw.Draw(G)
d.text((G.width / 2, 28), "Anéis das Tropas — alta definição", font=F(30), fill=(235, 235, 245), anchor="mm")
for k, cor in enumerate(anel.CORES):
    G.alpha_composite(cards[cor][0].resize((300, 300), Image.LANCZOS), (k * 300, 60))
    G.alpha_composite(cards[cor][1].resize((300, 300), Image.LANCZOS), (k * 300, 360))
    d.text((k * 300 + 150, 660), NOME[cor], font=F(20), fill=(220, 220, 230), anchor="mm")
G.convert("RGB").save(f"{OUT}/previas/galeria_aneis.png")
for f in ("anel.py", "simbolos.py", "prev.py", "build.py"): shutil.copy(f"/home/claude/aneis/{f}", f"{OUT}/gerador/{f}")
json.dump(stats, open("/home/claude/aneis/_stats.json", "w"))

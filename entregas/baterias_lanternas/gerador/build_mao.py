import sys, os, json, shutil
sys.path.insert(0,'/home/claude/baterias'); sys.path.insert(0,'/home/claude/aneis'); sys.path.insert(0,'/home/claude/lore3d'); sys.path.insert(0,'/home/claude/unif2')
import mao, prev
from core import render
from previa import font as F
from PIL import Image, ImageDraw
OUT = "/home/claude/Baterias_DC/mao"; SRC = "/home/claude/mod-green_lanterns-1.20.1/src/main/resources/assets/lanterna"
if os.path.exists(OUT): shutil.rmtree(OUT)
R = f"{OUT}/src/main/resources/assets/lanterna"
for d in ("models/block/battery", "textures/block/battery", "blockstates", "models/item"): os.makedirs(f"{R}/{d}", exist_ok=True)
os.makedirs(f"{OUT}/previas", exist_ok=True)
NOME = dict(verde="Vontade", amarelo="Medo", vermelho="Fúria", laranja="Avareza", azul="Esperança", violeta="Amor", preto="Morte")
cards = []; st = {}
for cor in mao.PAL:
    m, t = mao.construir(cor); n = mao.ARQ[cor]
    json.dump(m, open(f"{R}/models/block/battery/{n}.json", "w"), indent=1); t.save(f"{R}/textures/block/battery/{n}.png")
    for e in m["elements"]:
        assert all(-16 <= v <= 32 for v in e["from"] + e["to"])
    st[cor] = len(m["elements"])
    g, at, gl = prev.para_geo(m, t)
    v = [render(g, at, gl, yaw=y, pitch=p, size=(420, 480), margin=0.06) for y, p in ((-30, 12), (180, 6), (90, 6))]
    W = Image.new("RGBA", (1260, 530), (16, 18, 24, 255)); d = ImageDraw.Draw(W)
    for i, im in enumerate(v): W.alpha_composite(im, (i * 420, 45))
    d.text((630, 22), f"Bateria da {NOME[cor]} ({n}) — {st[cor]} elementos", font=F(24), fill=(235, 235, 245), anchor="mm")
    W.convert("RGB").save(f"{OUT}/previas/{n}.png"); cards.append((cor, v[0]))
    print(cor, st[cor], flush=True)
bs = json.load(open(f"{SRC}/blockstates/power_battery.json")); bs["variants"]["corps=black"]["model"] = "lanterna:block/battery/bateria_morte_preto"
json.dump(bs, open(f"{R}/blockstates/power_battery.json", "w"), indent=1)
it = json.load(open(f"{SRC}/models/item/power_battery.json"))
for o in it["overrides"]:
    if o["model"].endswith("caveira_morte_preto"): o["model"] = "lanterna:block/battery/bateria_morte_preto"
json.dump(it, open(f"{R}/models/item/power_battery.json", "w"), indent=1)
G = Image.new("RGBA", (7 * 300, 420), (16, 18, 24, 255)); d = ImageDraw.Draw(G)
d.text((G.width / 2, 24), "Baterias de mão — lanternas", font=F(26), fill=(235, 235, 245), anchor="mm")
for i, (cor, im) in enumerate(cards):
    G.alpha_composite(im.resize((300, 343), Image.LANCZOS), (i * 300, 45)); d.text((i * 300 + 150, 400), NOME[cor], font=F(18), fill=(220, 220, 230), anchor="mm")
G.convert("RGB").save(f"{OUT}/previas/galeria_mao.png")
json.dump(st, open("/home/claude/baterias/_st_mao.json", "w"))

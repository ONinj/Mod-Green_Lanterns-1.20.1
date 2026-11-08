import os, sys, json, shutil, copy
sys.path.insert(0, "/home/claude/unif2"); sys.path.insert(0, "/home/claude/lore3d")
from PIL import Image, ImageDraw
import designs, traje, previa, energia
from core import render
OUT = "/home/claude/Trajes_Segunda_Pele"
if os.path.exists(OUT): shutil.rmtree(OUT)
A = f"{OUT}/forge/src/main/resources/assets/seu_modid"
for d in ("geo/item/armor", "animations/item/armor", "textures/item/armor", "models/item", "textures/item", "lang"): os.makedirs(f"{A}/{d}", exist_ok=True)
for d in ("previas", "blockbench"): os.makedirs(f"{OUT}/{d}", exist_ok=True)
NOMES_TROPA = dict(verde="Vontade", amarelo="Medo", vermelho="Fúria", laranja="Avareza", azul="Esperança", indigo="Compaixão", violeta="Amor", preto="Morte", branco="Vida")
EN_TROPA = dict(verde="Will", amarelo="Fear", vermelho="Rage", laranja="Avarice", azul="Hope", indigo="Compassion", violeta="Love", preto="Death", branco="Life")
PECA = {"helmet": ("Máscara", "Mask", ("armorHead",)), "chestplate": ("Traje (tronco)", "Suit Top", ("armorBody", "armorRightArm", "armorLeftArm")),
        "leggings": ("Traje (pernas)", "Suit Legs", ("armorRightLeg", "armorLeftLeg")), "boots": ("Botas", "Boots", ("armorRightBoot", "armorLeftBoot"))}
anim = {"format_version": "1.8.0", "animations": {"misc.idle": {"loop": True, "animation_length": 1, "bones": {}}}}
pt, en, stats, cards = {}, {}, {}, {}
for (tropa, var), D in designs.DESIGNS.items():
    T = D["fn"](); geo, img, gl = traje.build(T, D["fabric"], D["panel"])
    n = f"traje_{tropa}_{var}"
    # conferências
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []): assert min(c["size"]) > 0, (n, b["name"], c)
    assert gl.getbbox() is not None and img.size == (256, 256)
    json.dump(geo, open(f"{A}/geo/item/armor/{n}.geo.json", "w"), indent=1)
    json.dump(anim, open(f"{A}/animations/item/armor/{n}.animation.json", "w"), indent=1)
    img.save(f"{A}/textures/item/armor/{n}.png"); gl.save(f"{A}/textures/item/armor/{n}_glowmask.png")
    json.dump(geo, open(f"{OUT}/blockbench/{n}.geo.json", "w"), indent=1); img.save(f"{OUT}/blockbench/{n}.png")
    stats[n] = traje.count(geo)
    # ícones: cada peça renderizada sozinha, 32x32
    for peca, (ptn, enn, bones) in PECA.items():
        g = copy.deepcopy(geo); G = g["minecraft:geometry"][0]
        G["bones"] = [b for b in G["bones"] if b["name"] in bones]
        gg, TT, GG = previa.vestido(g, img, gl, sem_manequim=True)
        icon = render(gg, TT, GG, yaw=-30, pitch=18, size=(128, 128), margin=0.06).resize((32, 32), Image.LANCZOS)
        icon.save(f"{A}/textures/item/{n}_{peca}.png")
        json.dump({"parent": "item/generated", "textures": {"layer0": f"seu_modid:item/{n}_{peca}"}}, open(f"{A}/models/item/{n}_{peca}.json", "w"), indent=1)
        pt[f"item.seu_modid.{n}_{peca}"] = f"{ptn} de Energia ({NOMES_TROPA[tropa]})" if var == "energia" else f"{ptn} — {D['nome']} ({NOMES_TROPA[tropa]})"
        en[f"item.seu_modid.{n}_{peca}"] = f"{'Energy' if var == 'energia' else D['nome']} {enn} ({EN_TROPA[tropa]})"
    cards[(tropa, var)] = previa.vistas(geo, img, gl, size=(400, 660))
    print(n, stats[n], flush=True)
json.dump(pt, open(f"{A}/lang/pt_br.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
json.dump(en, open(f"{A}/lang/en_us.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
json.dump(stats, open("/home/claude/unif2/_stats.json", "w"), indent=1)
# prévias: uma folha por Tropa (A e B, frente/costas/lado) e uma galeria geral
F = previa.font
for tropa in NOMES_TROPA:
    W = Image.new("RGBA", (1200 * 3 + 80, 760), (16, 18, 24, 255)); d = ImageDraw.Draw(W)
    for j, var in enumerate(("a", "b", "energia")):
        for i, im in enumerate(cards[(tropa, var)]): W.alpha_composite(im, (j * 1240 + i * 400, 40))
        d.text((j * 1240 + 600, 20), f"{designs.DESIGNS[(tropa, var)]['nome']}  (traje_{tropa}_{var})", font=F(24), fill=(235, 235, 245), anchor="mm")
    d.text((1840, 735), f"Tropa da {NOMES_TROPA[tropa]}", font=F(22), fill=(200, 200, 215), anchor="mm")
    W.convert("RGB").save(f"{OUT}/previas/trajes_{tropa}.png")
G = Image.new("RGBA", (9 * 2 * 200, 2 * 360 + 80), (16, 18, 24, 255)); d = ImageDraw.Draw(G)
d.text((G.width / 2, 30), "Trajes \"segunda pele\" — 9 Tropas x 2 variações", font=F(28), fill=(235, 235, 245), anchor="mm")
for k, tropa in enumerate(NOMES_TROPA):
    for j, var in enumerate("ab"):
        fr = cards[(tropa, var)][0].resize((200, 330), Image.LANCZOS); bk = cards[(tropa, var)][1].resize((200, 330), Image.LANCZOS)
        x = (k * 2 + j) * 200
        G.alpha_composite(fr, (x, 70)); G.alpha_composite(bk, (x, 70 + 360))
        d.text((x + 100, 410), designs.DESIGNS[(tropa, var)]["nome"], font=F(14), fill=(220, 220, 230), anchor="mm")
G.convert("RGB").save(f"{OUT}/previas/galeria.png")
G = Image.new("RGBA", (9 * 240, 2 * 400 + 80), (16, 18, 24, 255)); d = ImageDraw.Draw(G)
d.text((G.width / 2, 30), "Trajes de energia — um por Tropa", font=F(28), fill=(235, 235, 245), anchor="mm")
for k, tropa in enumerate(NOMES_TROPA):
    fr = cards[(tropa, "energia")][0].resize((240, 396), Image.LANCZOS); bk = cards[(tropa, "energia")][1].resize((240, 396), Image.LANCZOS)
    G.alpha_composite(fr, (k * 240, 60)); G.alpha_composite(bk, (k * 240, 470))
    d.text((k * 240 + 120, 462), NOMES_TROPA[tropa], font=F(16), fill=(220, 220, 230), anchor="mm")
G.convert("RGB").save(f"{OUT}/previas/galeria_energia.png")
os.makedirs(f"{OUT}/gerador", exist_ok=True)
for f in ("traje.py", "motivos.py", "designs.py", "energia.py", "previa.py", "build_trajes.py"): shutil.copy(f"/home/claude/unif2/{f}", f"{OUT}/gerador/{f}")
J = f"{OUT}/forge/src/main/java/seu/pacote/trajes"
shutil.copytree("/home/claude/unif2/java/trajes", J)
shutil.copy("/home/claude/unif2/LEIA-ME.txt", f"{OUT}/LEIA-ME.txt")
print("ok")

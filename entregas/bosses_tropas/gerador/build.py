import os, json, sys, importlib
from PIL import Image, ImageDraw, ImageFont
from core import render, animations
OUT = "/home/claude/Lore3D"

def font(sz):
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", sz)

def build(items, kind, extra_anims=None):
    res = []
    A = f"{OUT}/forge/src/main/resources/assets/seu_modid"
    for d in ("geo/entity", "textures/entity", "animations/entity"): os.makedirs(f"{A}/{d}", exist_ok=True)
    os.makedirs(f"{OUT}/blockbench/{kind}", exist_ok=True); os.makedirs(f"{OUT}/previas/{kind}", exist_ok=True)
    for name, fn in items:
        g, meta = fn()
        geo = g.export()
        anim = animations(g, meta.pop("extra", None))
        json.dump(geo, open(f"{A}/geo/entity/{name}.geo.json", "w"), indent=1)
        json.dump(anim, open(f"{A}/animations/entity/{name}.animation.json", "w"), indent=1)
        g.A.img.save(f"{A}/textures/entity/{name}.png"); g.A.glow.save(f"{A}/textures/entity/{name}_glowmask.png")
        json.dump(geo, open(f"{OUT}/blockbench/{kind}/{name}.geo.json", "w"), indent=1)
        g.A.img.save(f"{OUT}/blockbench/{kind}/{name}.png")
        fr = render(geo, g.A.img, g.A.glow, yaw=-32, pitch=12, size=(560, 560))
        bk = render(geo, g.A.img, g.A.glow, yaw=150, pitch=12, size=(560, 560))
        W = Image.new("RGBA", (1120, 620), (16, 18, 24, 255)); W.alpha_composite(fr, (0, 0)); W.alpha_composite(bk, (560, 0))
        ImageDraw.Draw(W).text((560, 595), f"{name}  ·  {g.count()} cubos", font=font(22), fill=(220, 220, 230), anchor="mm")
        W.convert("RGB").save(f"{OUT}/previas/{kind}/{name}.png")
        meta.update(name=name, cubes=g.count(), tex=g.A.size, translucent=meta.get("translucent", bool(g.A.translucent)),
                    anims=list(anim["animations"].keys()), bones=[x for x in g.order])
        res.append((name, fr, meta))
        print(name, g.count(), "cubos", flush=True)
    return res

def sheet(res, title, path, cols=4, cw=420, ch=470):
    rows = (len(res) + cols - 1) // cols
    W = Image.new("RGBA", (cw * cols, ch * rows + 70), (16, 18, 24, 255)); d = ImageDraw.Draw(W)
    d.text((cw * cols / 2, 36), title, font=font(34), fill=(235, 235, 245), anchor="mm")
    for i, (name, im, meta) in enumerate(res):
        x, y = (i % cols) * cw, (i // cols) * ch + 70
        W.alpha_composite(im.resize((cw - 20, cw - 20)), (x + 10, y))
        d.text((x + cw / 2, y + ch - 36), meta.get("label", name), font=font(22), fill=(225, 225, 235), anchor="mm")
    W.convert("RGB").save(path)

if __name__ == "__main__":
    which = sys.argv[1]
    if which == "races":
        import races
        res = build(races.RACES, "racas")
        labels = {"brakkari": "Brakkari", "ghallos": "Ghallos", "skrell_vath": "Skrell'Vath", "veltari": "Veltari", "aelinos": "Aelínos",
                  "ves_kai": "Ves'Kai", "zaelith": "Zaëlith", "silente": "Silente", "coletor": "O Coletor", "broto": "Broto", "eco_cinzento": "Eco Cinzento"}
        for n, im, m in res: m["label"] = labels[n]
        sheet(res, "Raças do Vazio Cromático", f"{OUT}/previas/racas.png")
        json.dump([m for _, _, m in res], open(f"{OUT}/racas_meta.json", "w"), ensure_ascii=False, indent=1)
    if which == "bosses":
        import bosses
        res = build(bosses.BOSSES, "bosses")
        sheet(res, "Bosses do Vazio Cromático", f"{OUT}/previas/bosses.png", cols=3, cw=520, ch=560)
        json.dump([m for _, _, m in res], open(f"{OUT}/bosses_meta.json", "w"), ensure_ascii=False, indent=1)

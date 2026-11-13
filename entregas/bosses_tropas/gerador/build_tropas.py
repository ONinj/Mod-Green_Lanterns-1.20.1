import os, json, sys, shutil
sys.path.insert(0, "/home/claude/lore3d")
import build as Bd
Bd.OUT = "/home/claude/Bosses_Tropas"
if os.path.exists(Bd.OUT): shutil.rmtree(Bd.OUT)
import bosses_tropas as B
res = Bd.build(B.BOSSES, "bosses")
# caminhos do mod (lanterna) em vez de seu_modid
src = f"{Bd.OUT}/forge/src/main/resources/assets/seu_modid"; dst = f"{Bd.OUT}/src/main/resources/assets/lanterna"
os.makedirs(os.path.dirname(dst), exist_ok=True); shutil.move(src, dst); shutil.rmtree(f"{Bd.OUT}/forge")
Bd.sheet(res, "Bosses das Tropas", f"{Bd.OUT}/previas/bosses_tropas.png", cols=4, cw=540, ch=600)
json.dump([m for _, _, m in res], open(f"{Bd.OUT}/bosses_meta.json", "w"), ensure_ascii=False, indent=1)
os.makedirs(f"{Bd.OUT}/gerador", exist_ok=True)
for f in ("bosses_tropas.py", "core.py", "races.py", "build.py", "build_tropas.py"): shutil.copy(f"/home/claude/lore3d/{f}", f"{Bd.OUT}/gerador/{f}")
for m in json.load(open(f"{Bd.OUT}/bosses_meta.json")): print(m["name"], m["cubes"], m["scale"], m["anims"])

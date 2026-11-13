"""Texturas dos sóis e buracos negros (originais):
  textures/space/sol_base.png    superfície de estrela em tons de cinza (granulação, manchas, filamentos), tingida no jogo
  textures/space/disco_buraco.png disco de acreção (u = volta, v = do raio de dentro para o de fora), em tons de cinza
Rodar da raiz do mod:  python3 tools/gerar_perigos.py
"""
import sys
import numpy as np
from PIL import Image
sys.path.insert(0, "tools")
import gerar_planetas as gp

A = "src/main/resources/assets/lanterna/textures/space/"

# ---- superfície da estrela
gran = gp.fbm(22, 901, 4)                       # células de granulação
gran = np.abs(gran - 0.5) * 2
cel = 1 - np.clip(gran * 1.6, 0, 1) * 0.16
grande = gp.fbm(3.2, 902, 5)
fil = gp.ridged(5, 903, 4)                      # filamentos claros
man = np.clip((gp.fbm(2.6, 904, 4) - 0.7) * 5, 0, 1) * (gp.LAT < 0.55)   # manchas escuras
v = 0.9 + (grande - 0.5) * 0.16
v = v * cel + np.clip(fil - 0.75, 0, 1) * 0.35
v = v * (1 - man * 0.45)
v = np.clip(v, 0, 1)
img = (np.stack([v, v, v], -1) * 255).astype(np.uint8)
Image.fromarray(img, "RGB").save(A + "sol_base.png", optimize=True)
print("ok sol_base")

# ---- disco de acreção: 1024 (volta) x 128 (raio)
W, H = 1024, 128
u = (np.arange(W) + 0.5) / W
r = (np.arange(H) + 0.5) / H
U, R = np.meshgrid(u, r)
ang = U * 2 * np.pi
# ruído periódico na volta: usa um círculo no espaço 3D do ruído
x = np.cos(ang) * 3
z = np.sin(ang) * 3
y = R * 6
fino = gp.fbm(1.0, 905, 6, x * 2.2, y * 4.0 + np.sin(ang * 3) * 0.6, z * 2.2)
espiral = gp.fbm(1.0, 906, 4, x, y * 1.5 + ang * 0.0, z)
# faixas finas (anéis de gás) ao longo do raio
faixas = 0.65 + 0.35 * np.sin(R * 90 + (fino - 0.5) * 12)
brilho = (1 - R) ** 1.4                       # mais quente perto do buraco
borda = np.clip(R / 0.06, 0, 1)                # borda de dentro bem definida
fora = np.clip((1 - R) / 0.25, 0, 1)           # some na borda de fora
v = brilho * faixas * (0.55 + fino * 0.9) * borda * fora
v = np.clip(v * 1.5, 0, 1)
alpha = np.clip(v * 1.4, 0, 1)
img = np.stack([v, v, v, alpha], -1)
Image.fromarray((img * 255).astype(np.uint8), "RGBA").save(A + "disco_buraco.png", optimize=True)
print("ok disco_buraco")

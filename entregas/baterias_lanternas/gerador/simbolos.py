import numpy as np
from PIL import Image
SRC = "/home/claude/aneis_in/assets/lanterna/textures/item"
CORES = ["verde", "amarelo", "vermelho", "laranja", "azul", "indigo", "violeta", "preto", "branco"]
REGRA = {
    "verde":    lambda r, g, b: (g > 200) & (r < 120),
    "amarelo":  lambda r, g, b: (r > 200) & (g > 160) & (b < 90),
    "vermelho": lambda r, g, b: (r + g + b) < 45,
    "laranja":  lambda r, g, b: (r + g + b) < 60,
    "azul":     lambda r, g, b: (r + g + b) > 600,
    "indigo":   lambda r, g, b: (r + g + b) > 600,
    "violeta":  lambda r, g, b: (r + g + b) > 600,
    "preto":    lambda r, g, b: (r + g + b) > 600,
    "branco":   lambda r, g, b: (r + g + b) < 45,
}
def tile(cor): return np.array(Image.open(f"{SRC}/espectro_{cor}.png").convert("RGB").crop((16, 0, 32, 16)), int)
def extrair(cor):
    a = tile(cor); m = REGRA[cor](a[..., 0], a[..., 1], a[..., 2])
    m[0, 11:] = False if cor in ("azul", "indigo", "violeta", "verde") else m[0, 11:]   # reflexo do canto
    if m[0].all(): m[0] = m[15] = m[:, 0] = m[:, 15] = False                          # moldura da textura
    sym = a[m].mean(0) if m.any() else np.array([255, 255, 255]); bg = a[~m].mean(0)
    return m, sym, bg
if __name__ == "__main__":
    for cor in CORES:
        m, s, b = extrair(cor); print(cor, s.astype(int).tolist(), b.astype(int).tolist())
        for r in m: print("".join(".#"[int(v)] for v in r))

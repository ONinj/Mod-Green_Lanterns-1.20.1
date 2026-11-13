"""Gera as texturas dos planetas vistos do espaço (textures/space/*.png), 1024x512, sem emenda.

O ruído é calculado sobre a esfera (x, y, z), então a textura fecha certinho nas bordas e nos polos.
Rodar da raiz do mod:  python3 tools/gerar_planetas.py [id ...]
(Terra e Mogo não são gerados aqui: têm textura própria.)
"""
import sys
import numpy as np
from PIL import Image, ImageFilter

W, H = 1024, 512
OUT = "src/main/resources/assets/lanterna/textures/space/"

th = (np.arange(H) + 0.5) / H * np.pi
ph = (np.arange(W) + 0.5) / W * 2 * np.pi
TH, PH = np.meshgrid(th, ph, indexing="ij")
X = np.sin(TH) * np.cos(PH)
Y = np.cos(TH)
Z = np.sin(TH) * np.sin(PH)
LAT = np.abs(Y)


def _hash(ix, iy, iz, seed):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h = h.astype(np.uint64)
    h ^= h >> np.uint64(13)
    h *= np.uint64(0x5BD1E995)
    h ^= h >> np.uint64(15)
    return (h & np.uint64(0xFFFFFF)).astype(np.float64) / float(0xFFFFFF)


def noise(x, y, z, seed):
    x0, y0, z0 = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = x - x0, y - y0, z - z0
    fx, fy, fz = [t * t * t * (t * (t * 6 - 15) + 10) for t in (fx, fy, fz)]
    x0, y0, z0 = x0.astype(np.int64), y0.astype(np.int64), z0.astype(np.int64)
    c = {}
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                c[dx, dy, dz] = _hash(x0 + dx, y0 + dy, z0 + dz, seed)
    lerp = lambda a, b, t: a + (b - a) * t
    a = lerp(c[0, 0, 0], c[1, 0, 0], fx); b = lerp(c[0, 1, 0], c[1, 1, 0], fx)
    cc = lerp(c[0, 0, 1], c[1, 0, 1], fx); d = lerp(c[0, 1, 1], c[1, 1, 1], fx)
    return lerp(lerp(a, b, fy), lerp(cc, d, fy), fz)


def fbm(s, seed, oct=6, x=None, y=None, z=None, gain=0.5):
    x = X if x is None else x; y = Y if y is None else y; z = Z if z is None else z
    tot, amp, norm = 0.0, 0.5, 0.0
    for i in range(oct):
        tot = tot + amp * noise(x * s + 17.3 * i, y * s, z * s, seed + i * 101)
        norm += amp; s *= 2.03; amp *= gain
    return tot / norm


def ridged(s, seed, oct=5):
    tot, amp, norm = 0.0, 0.5, 0.0
    for i in range(oct):
        n = 1 - np.abs(noise(X * s, Y * s + 3.1 * i, Z * s, seed + i * 57) * 2 - 1)
        tot = tot + amp * n * n; norm += amp; s *= 2.1; amp *= 0.5
    return tot / norm


def warp(s, seed, k=0.5):
    """Coordenadas deslocadas por ruído (deixa os continentes com formas naturais)."""
    wx = fbm(s, seed + 1, 4) - 0.5
    wy = fbm(s, seed + 2, 4) - 0.5
    wz = fbm(s, seed + 3, 4) - 0.5
    return X + wx * k, Y + wy * k, Z + wz * k


def bands(seed, freq=9, turb=0.35):
    """Faixas de gigante gasoso / atmosfera densa, com turbulência."""
    t = fbm(3.0, seed, 5) - 0.5
    return 0.5 + 0.5 * np.sin((Y + t * turb) * freq * np.pi)


def ramp(v, stops):
    """Gradiente: stops = [(pos, (r,g,b)), ...]."""
    v = np.clip(v, 0, 1)
    out = np.zeros(v.shape + (3,))
    ps = [p for p, _ in stops]; cs = [np.array(c, float) for _, c in stops]
    for i in range(len(stops) - 1):
        m = (v >= ps[i]) & (v <= ps[i + 1])
        t = ((v - ps[i]) / max(1e-6, ps[i + 1] - ps[i]))[..., None]
        out[m] = (cs[i] * (1 - t) + cs[i + 1] * t)[m]
    out[v < ps[0]] = cs[0]; out[v > ps[-1]] = cs[-1]
    return out


def mix(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return a * (1 - t) + np.array(b, float) * t if not isinstance(b, np.ndarray) or b.ndim == 1 else a * (1 - t) + b * t


def norm01(v):
    return (v - v.min()) / (v.max() - v.min() + 1e-9)


def clouds(img, seed, amt=0.55, cor=(255, 255, 255), thr=0.55, esc=3.2):
    xw, yw, zw = warp(2.0, seed + 40, 0.7)
    c = fbm(esc, seed + 41, 6, xw, yw * 1.6, zw)
    c = np.clip((c - thr) / (1 - thr) * 2.4, 0, 1) * amt
    return mix(img, cor, c)


def shade(img, h, k=0.9):
    """Relevo: ilumina pela derivada da altura (dá volume às montanhas)."""
    gx = np.roll(h, -1, axis=1) - np.roll(h, 1, axis=1)
    gy = np.roll(h, -1, axis=0) - np.roll(h, 1, axis=0)
    l = np.clip(1 + (gx * 0.7 - gy * 0.7) * k * 16, 0.78, 1.22)
    return img * l[..., None]


def craters(img, seed, n=140, rmax=0.09, dark=(40, 40, 46), rim=(170, 170, 175)):
    rng = np.random.default_rng(seed)
    for _ in range(n):
        v = rng.normal(size=3); v /= np.linalg.norm(v)
        r = rmax * rng.random() ** 2.2 + 0.006
        d = np.arccos(np.clip(X * v[0] + Y * v[1] + Z * v[2], -1, 1))
        inner = np.clip(1 - d / r, 0, 1)
        ring = np.exp(-((d - r) / (r * 0.18)) ** 2)
        img = mix(img, dark, (inner > 0) * (0.35 + 0.3 * inner))
        img = mix(img, rim, ring * 0.45)
    return img


def save(nome, img):
    img = np.clip(img, 0, 255).astype(np.uint8)
    Image.fromarray(img, "RGB").save(OUT + nome + ".png", optimize=True)
    print("ok", nome)


# ----------------------------------------------------------------- planetas

def oa():
    # Oa: mundo antigo e verde-escuro, mares esmeralda, a grande cidade dos Guardiões brilhando em linhas
    xw, yw, zw = warp(1.3, 11, 0.8)
    h = fbm(1.6, 12, 7, xw, yw, zw)
    h = norm01(h)
    sea = 0.47
    mar = ramp(h / sea, [(0, (6, 40, 34)), (0.8, (14, 92, 70)), (1, (60, 170, 120))])
    terra = ramp((h - sea) / (1 - sea), [(0, (70, 96, 66)), (0.3, (52, 78, 52)), (0.6, (96, 104, 92)), (1, (210, 220, 210))])
    img = np.where((h < sea)[..., None], mar, terra)
    img = shade(img, np.maximum(h, sea), 0.8)
    # cidade: grade de avenidas de energia verde no grande continente
    cidade = (h > sea + 0.03) & (np.abs(Y) < 0.55)
    gx = np.abs(np.sin(PH * 90)) < 0.06
    gy = np.abs(np.sin(TH * 70)) < 0.06
    r = fbm(6, 13, 3)
    linhas = cidade & (gx | gy) & (r > 0.48)
    img = mix(img, (120, 255, 150), linhas * 0.75)
    img = mix(img, (40, 70, 50), cidade * (r > 0.6) * 0.35)
    img = clouds(img, 14, 0.5, (225, 245, 230), 0.58)
    save("oa", img)


def korugar():
    # Korugar: deserto dourado com cânions escuros e tempestades de areia âmbar
    xw, yw, zw = warp(1.5, 21, 0.9)
    h = norm01(fbm(1.8, 22, 7, xw, yw, zw))
    rg = ridged(3.0, 23)
    img = ramp(h, [(0, (90, 52, 20)), (0.35, (170, 110, 40)), (0.6, (222, 170, 70)), (0.85, (245, 214, 120)), (1, (255, 240, 190))])
    canion = np.clip((rg - 0.72) * 5, 0, 1)
    img = mix(img, (60, 30, 12), canion * 0.8)
    img = shade(img, h + rg * 0.15, 1.0)
    # mares rasos de mercúrio/sal (pequenos)
    img = mix(img, (120, 96, 60), (h < 0.18) * 0.7)
    tempest = bands(24, 7, 0.6)
    img = mix(img, (230, 180, 90), np.clip(tempest - 0.7, 0, 1) * 1.2 * (fbm(4, 25, 4) > 0.5))
    img = clouds(img, 26, 0.35, (255, 225, 150), 0.6)
    save("korugar", img)


def ysmault():
    # Ysmault: rocha vulcânica negra, rachaduras incandescentes e mares de sangue
    xw, yw, zw = warp(1.4, 31, 0.9)
    h = norm01(fbm(1.7, 32, 7, xw, yw, zw))
    rg = ridged(2.6, 33, 6)
    img = ramp(h, [(0, (70, 6, 8)), (0.38, (140, 14, 16)), (0.42, (40, 18, 18)), (0.7, (66, 30, 28)), (1, (110, 70, 64))])
    img = shade(img, np.maximum(h, 0.4), 1.2)
    cracks = np.clip((rg - 0.78) * 8, 0, 1) * (h > 0.42)
    img = mix(img, (255, 70, 20), cracks)
    img = mix(img, (255, 200, 80), np.clip((rg - 0.9) * 12, 0, 1) * (h > 0.42))
    img = clouds(img, 34, 0.35, (60, 20, 20), 0.55)
    save("ysmault", img)


def odym():
    # Odym: oceano azul profundo e gelo cristalino, ilhas claras
    xw, yw, zw = warp(1.4, 41, 0.7)
    h = norm01(fbm(1.9, 42, 7, xw, yw, zw))
    sea = 0.56
    mar = ramp(h / sea, [(0, (8, 30, 80)), (0.75, (26, 90, 170)), (1, (110, 190, 240))])
    terra = ramp((h - sea) / (1 - sea), [(0, (200, 225, 240)), (0.5, (160, 200, 230)), (1, (245, 250, 255))])
    img = np.where((h < sea)[..., None], mar, terra)
    img = shade(img, np.maximum(h, sea), 0.7)
    gelo = LAT > 0.72 - (fbm(5, 43, 3) - 0.5) * 0.25
    img = mix(img, (235, 245, 255), gelo * 0.95)
    cris = np.clip((ridged(5, 44, 3) - 0.85) * 8, 0, 1) * (h >= sea)
    img = mix(img, (140, 230, 255), cris)
    img = clouds(img, 45, 0.6, (250, 252, 255), 0.55)
    save("odym", img)


def zamaron():
    # Zamaron: selvas violeta, mares magenta, flores de luz rosada
    xw, yw, zw = warp(1.3, 51, 0.8)
    h = norm01(fbm(1.7, 52, 7, xw, yw, zw))
    sea = 0.45
    mar = ramp(h / sea, [(0, (70, 10, 70)), (0.8, (170, 40, 150)), (1, (240, 120, 210))])
    terra = ramp((h - sea) / (1 - sea), [(0, (120, 50, 130)), (0.4, (90, 30, 110)), (0.75, (150, 80, 170)), (1, (240, 200, 245))])
    img = np.where((h < sea)[..., None], mar, terra)
    img = shade(img, np.maximum(h, sea), 0.8)
    flores = (fbm(9, 53, 3) > 0.66) & (h > sea + 0.05)
    img = mix(img, (255, 150, 230), flores * 0.6)
    img = clouds(img, 54, 0.5, (255, 220, 250), 0.57)
    save("zamaron", img)


def nok():
    # Nok: oceano índigo escuro, arquipélagos e auroras
    xw, yw, zw = warp(1.6, 61, 0.9)
    h = norm01(fbm(2.1, 62, 7, xw, yw, zw))
    sea = 0.6
    mar = ramp(h / sea, [(0, (14, 8, 50)), (0.8, (40, 30, 120)), (1, (100, 90, 200))])
    terra = ramp((h - sea) / (1 - sea), [(0, (60, 70, 70)), (0.4, (40, 80, 60)), (1, (160, 150, 200))])
    img = np.where((h < sea)[..., None], mar, terra)
    img = shade(img, np.maximum(h, sea), 0.8)
    aurora = np.clip(1 - np.abs(LAT - 0.82) * 12, 0, 1) * fbm(6, 63, 3)
    img = mix(img, (140, 120, 255), aurora * 0.9)
    img = clouds(img, 64, 0.4, (200, 200, 255), 0.6)
    save("nok", img)


def okaara():
    # Okaara: mundo laranja de deserto e savana, tempestades em faixas cor de ferrugem
    xw, yw, zw = warp(1.5, 71, 0.8)
    h = norm01(fbm(1.8, 72, 7, xw, yw, zw))
    img = ramp(h, [(0, (120, 50, 14)), (0.3, (190, 90, 30)), (0.6, (230, 140, 50)), (0.85, (250, 190, 100)), (1, (255, 230, 170))])
    img = shade(img, h, 1.0)
    lago = h < 0.16
    img = mix(img, (40, 80, 90), lago * 0.8)
    b = bands(73, 11, 0.5)
    img = mix(img, (170, 70, 30), np.clip(b - 0.75, 0, 1) * 1.6)
    img = clouds(img, 74, 0.4, (255, 210, 160), 0.6)
    save("okaara", img)


def ryut():
    # Ryut: mundo morto, cinzento, crateras e poeira; restos de energia negra
    h = norm01(fbm(2.2, 81, 7))
    img = ramp(h, [(0, (34, 34, 40)), (0.5, (78, 78, 86)), (1, (150, 150, 158))])
    img = shade(img, h, 1.2)
    img = craters(img, 82, 160, 0.1)
    veia = np.clip((ridged(4, 83, 4) - 0.86) * 9, 0, 1)
    img = mix(img, (20, 10, 26), veia * 0.8)
    save("ryut", img)


def genese():
    # Gênese: luz branca primordial, terras pálidas e mares de luz
    xw, yw, zw = warp(1.4, 91, 0.9)
    h = norm01(fbm(1.7, 92, 7, xw, yw, zw))
    sea = 0.5
    mar = ramp(h / sea, [(0, (170, 220, 200)), (1, (240, 255, 245))])
    terra = ramp((h - sea) / (1 - sea), [(0, (200, 215, 200)), (0.6, (225, 235, 225)), (1, (255, 255, 255))])
    img = np.where((h < sea)[..., None], mar, terra)
    img = shade(img, np.maximum(h, sea), 0.6)
    fios = np.clip((ridged(3, 93, 4) - 0.8) * 6, 0, 1)
    arco = np.stack([np.sin(PH * 3) * 0.5 + 0.5, np.sin(PH * 3 + 2) * 0.5 + 0.5, np.sin(PH * 3 + 4) * 0.5 + 0.5], -1) * 255
    img = img * (1 - fios[..., None] * 0.5) + arco * fios[..., None] * 0.5
    img = clouds(img, 94, 0.5, (255, 255, 255), 0.55)
    save("genese", img)


def sun():
    # Sol: granulação, manchas e filamentos
    g = fbm(14, 101, 5)
    big = fbm(2.5, 102, 5)
    img = ramp(0.35 + g * 0.5 + (big - 0.5) * 0.4, [(0, (200, 70, 10)), (0.45, (255, 150, 30)), (0.7, (255, 210, 90)), (1, (255, 250, 210))])
    manchas = np.clip((fbm(4, 103, 4) - 0.7) * 6, 0, 1) * (LAT < 0.5)
    img = mix(img, (90, 30, 8), manchas * 0.8)
    save("sun", img)


ALL = {"oa": oa, "korugar": korugar, "ysmault": ysmault, "odym": odym, "zamaron": zamaron, "nok": nok,
       "okaara": okaara, "ryut": ryut, "genese": genese, "sun": sun}

if __name__ == "__main__":
    for n in (sys.argv[1:] or ALL):
        ALL[n]()

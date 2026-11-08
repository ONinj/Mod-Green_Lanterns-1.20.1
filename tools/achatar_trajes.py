"""
Achata os uniformes/trajes 3D (GeckoLib) numa "skin": para cada pixel da skin do jogador (camada de baixo), procura a
face do traje que fica logo por cima dele (até ~1,3 px para fora: camada de base + relevos) e copia a cor. O resultado
é uma textura no layout de skin (64x64 x ESCALA) para pintar direto no modelo do jogador, sem espessura.

Saída: assets/lanterna/textures/skin_traje/<arquivo>_<wide|slim>.png e _<wide|slim>_glow.png
Uso: python3 tools/achatar_trajes.py [arquivo ...]
"""
import json, os, sys, glob
from PIL import Image

RAIZ = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'lanterna')
ESCALA = 4
EW_TROCA = os.environ.get('EW_TROCA', '1') == '1'   # bedrock "east" = lado -X do Java (lado direito do jogador)
MAX_FORA = 1.3

# partes do jogador (coordenadas do modelo Java em pixels, Y para baixo): caixa e UV da camada de baixo
PARTES = {
    'head': ((-4, -8, -4), (8, 8, 8), (0, 0)),
    'body': ((-4, 0, -2), (8, 12, 4), (16, 16)),
    'rightArm': ((-8, 0, -2), (4, 12, 4), (40, 16)),
    'leftArm': ((4, 0, -2), (4, 12, 4), (32, 48)),
    'rightLeg': ((-3.9, 12, -2), (4, 12, 4), (0, 16)),
    'leftLeg': ((-0.1, 12, -2), (4, 12, 4), (16, 48)),
}
# UV da segunda camada de cada parte (onde vai o relevo)
SEGUNDA = {'head': (32, 0), 'body': (16, 32), 'rightArm': (40, 32), 'leftArm': (48, 48), 'rightLeg': (0, 32), 'leftLeg': (0, 48)}
RELEVO = 0.45      # faces mais para fora que isto também vão para a segunda camada (relevo de verdade)
SOBRA = 1.3        # cubos que saem mais que isto do corpo (ou girados) são peças 3D: não entram na skin

OSSOS = {'armorHead': 'head', 'armorBody': 'body', 'armorRightArm': 'rightArm', 'armorLeftArm': 'leftArm',
         'armorRightLeg': 'rightLeg', 'armorRightBoot': 'rightLeg', 'armorLeftLeg': 'leftLeg', 'armorLeftBoot': 'leftLeg'}

# faces físicas: front(-Z) back(+Z) nx(-X) px(+X) top(-Y) bottom(+Y)
def rect_skin(face, u, v, w, h, d):
    return {'nx': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'px': (u + d + w, v + d, d, h),
            'back': (u + 2 * d + w, v + d, w, h), 'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d)}[face]

def ponto(face, x0, y0, z0, x1, y1, z1, s, t):
    """(s,t) da face -> ponto 3D, na orientação da skin do Java."""
    if face == 'front': return (x0 + s * (x1 - x0), y0 + t * (y1 - y0), z0)
    if face == 'back': return (x1 - s * (x1 - x0), y0 + t * (y1 - y0), z1)
    if face == 'nx': return (x0, y0 + t * (y1 - y0), z1 - s * (z1 - z0))
    if face == 'px': return (x1, y0 + t * (y1 - y0), z0 + s * (z1 - z0))
    if face == 'top': return (x0 + s * (x1 - x0), y0, z1 - t * (z1 - z0))
    if face == 'bottom': return (x0 + s * (x1 - x0), y1, z1 - t * (z1 - z0))

def st(face, x0, y0, z0, x1, y1, z1, p):
    X, Y, Z = p
    f = lambda a, b, c: (a - b) / (c - b) if c != b else 0.5
    if face == 'front': return f(X, x0, x1), f(Y, y0, y1)
    if face == 'back': return f(X, x1, x0), f(Y, y0, y1)
    if face == 'nx': return f(Z, z1, z0), f(Y, y0, y1)
    if face == 'px': return f(Z, z0, z1), f(Y, y0, y1)
    if face == 'top': return f(X, x0, x1), f(Z, z1, z0)
    if face == 'bottom': return f(X, x0, x1), f(Z, z1, z0)

def fora(face, caixa, cubo):
    """Quanto o plano da face do cubo está para fora do plano da face da parte."""
    (x0, y0, z0, x1, y1, z1), (a0, b0, c0, a1, b1, c1) = caixa, cubo
    return {'front': z0 - c0, 'back': c1 - z1, 'nx': x0 - a0, 'px': a1 - x1, 'top': y0 - b0, 'bottom': b1 - y1}[face]

NOME_FACE = {'north': 'front', 'south': 'back', 'up': 'top', 'down': 'bottom',
             'east': 'nx' if EW_TROCA else 'px', 'west': 'px' if EW_TROCA else 'nx'}

def cubos_do_modelo(geo):
    g = json.load(open(geo))['minecraft:geometry'][0]
    out = []
    for b in g['bones']:
        parte = OSSOS.get(b['name'])
        if not parte: continue
        for c in b.get('cubes', []):
            if 'rotation' in c and any(abs(r) > 0.01 for r in c['rotation']): continue
            ox, oy, oz = c['origin']; sx, sy, sz = c['size']; inf = c.get('inflate', 0)
            caixa = (ox - inf, 24 - (oy + sy) - inf, oz - inf, ox + sx + inf, 24 - oy + inf, oz + sz + inf)
            (px0, py0, pz0), (pw, ph, pd), _ = PARTES[parte]
            if (px0 - caixa[0] > SOBRA or caixa[3] - (px0 + pw) > SOBRA or py0 - caixa[1] > SOBRA or caixa[4] - (py0 + ph) > SOBRA
                    or pz0 - caixa[2] > SOBRA or caixa[5] - (pz0 + pd) > SOBRA):
                continue  # peça 3D (asa, auréola, espinho...): continua 3D no jogo
            uv = c.get('uv')
            faces = {}
            if isinstance(uv, list):
                u, v = uv; w, h, d = sx, sy, sz
                # box UV no layout do Java (o mesmo da skin)
                for ff in ('front', 'back', 'nx', 'px', 'top', 'bottom'):
                    faces[ff] = rect_skin(ff, u, v, w, h, d)
            elif isinstance(uv, dict):
                for nome, fv in uv.items():
                    if nome not in NOME_FACE: continue
                    u, v = fv['uv']; uw, uh = fv.get('uv_size', (0, 0))
                    faces[NOME_FACE[nome]] = (u, v, uw, uh)
            out.append((parte, caixa, faces))
    return out

def achatar(arquivo, slim):
    geo = os.path.join(RAIZ, 'geo', 'item', 'armor', arquivo + '.geo.json')
    tex = Image.open(os.path.join(RAIZ, 'textures', 'item', 'armor', arquivo + '.png')).convert('RGBA')
    gpath = os.path.join(RAIZ, 'textures', 'item', 'armor', arquivo + '_glowmask.png')
    glow = Image.open(gpath).convert('RGBA') if os.path.exists(gpath) else None
    g = json.load(open(geo))['minecraft:geometry'][0]
    tw, th = g['description']['texture_width'], g['description']['texture_height']
    kx, ky = tex.width / tw, tex.height / th
    cubos = cubos_do_modelo(geo)
    N = 64 * ESCALA
    out = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    outg = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    po, pg = out.load(), outg.load()
    tp, gp = tex.load(), glow.load() if glow else None
    for parte, ((px0, py0, pz0), (w, h, d), (u0, v0)) in PARTES.items():
        x0, y0, z0, x1, y1, z1 = px0, py0, pz0, px0 + w, py0 + h, pz0 + d
        caixa = (x0, y0, z0, x1, y1, z1)
        ws = 3 if slim and 'Arm' in parte else w   # largura do braço na skin
        meus = [(cx, fs) for (pp, cx, fs) in cubos if pp == parte]
        for face in ('front', 'back', 'nx', 'px', 'top', 'bottom'):
            ru, rv, rw, rh = rect_skin(face, u0, v0, ws, h, d)
            cand = sorted([(fora(face, caixa, cx), cx, fs[face]) for cx, fs in meus if face in fs], key=lambda c: -c[0])
            cand = [c for c in cand if -0.05 <= c[0] <= MAX_FORA]
            su, sv = SEGUNDA[parte]
            r2u, r2v, _, _ = rect_skin(face, su, sv, ws, h, d)
            for j in range(int(rh * ESCALA)):
                for i in range(int(rw * ESCALA)):
                    s, t = (i + 0.5) / (rw * ESCALA), (j + 0.5) / (rh * ESCALA)
                    p = ponto(face, *caixa, s, t)
                    for camada, lista in ((0, cand), (1, [c for c in cand if c[0] > RELEVO])):
                        for off, cx, (fu, fv, fw, fh) in lista:
                            s2, t2 = st(face, *cx, p)
                            if not (-1e-4 <= s2 <= 1 + 1e-4 and -1e-4 <= t2 <= 1 + 1e-4): continue
                            s2 = min(max(s2, 0), 0.9999); t2 = min(max(t2, 0), 0.9999)
                            tx = int((fu + s2 * fw) * kx); ty = int((fv + t2 * fh) * ky)
                            tx = min(max(tx, 0), tex.width - 1); ty = min(max(ty, 0), tex.height - 1)
                            c = tp[tx, ty]
                            if c[3] < 24: continue
                            if camada == 0: X, Y = int(ru * ESCALA) + i, int(rv * ESCALA) + j
                            else: X, Y = int(r2u * ESCALA) + i, int(r2v * ESCALA) + j
                            po[X, Y] = (c[0], c[1], c[2], 255)
                            if gp: pg[X, Y] = gp[tx, ty]
                            break
    return out, outg

if __name__ == '__main__':
    nomes = sys.argv[1:] or sorted(os.path.basename(p)[:-9] for p in glob.glob(os.path.join(RAIZ, 'geo', 'item', 'armor', '*.geo.json')))
    dst = os.path.join(RAIZ, 'textures', 'skin_traje')
    os.makedirs(dst, exist_ok=True)
    for n in nomes:
        for slim in (False, True):
            a, b = achatar(n, slim)
            k = 'slim' if slim else 'wide'
            a.save(os.path.join(dst, f'{n}_{k}.png'), optimize=True)
            if b.getbbox(): b.save(os.path.join(dst, f'{n}_{k}_glow.png'), optimize=True)
        print('ok', n)

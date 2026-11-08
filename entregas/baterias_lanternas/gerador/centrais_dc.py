# Baterias Centrais v3: lanternas gigantes no estilo das baterias de poder clássicas.
# Silhueta: pé largo (com a câmara do portal dentro) · pescoço · BOCA LARGA embaixo · globo redondo de vidro com o
# núcleo · BOCA LARGA em cima · pescoço · tampa em cúpula · HASTE · remate · ALÇA em arco por cima de tudo.
# Mesma origem/câmara/portal das versões anteriores (o código do mod não muda, só a origem da estrutura).
import sys, math, random
sys.path.insert(0, "/home/claude/baterias"); sys.path.insert(0, "/home/claude/aneis")
from centrais import Bat, nova, mats_comuns, S22
from simbolos import extrair

S11 = math.tan(math.radians(11.25))

def disco(N, bone, r, y0, y1, mat, lados=16, cx=0, cz=0):
    """disco maciço de 16 lados (faixas cruzadas em 0/22,5/45/67,5 graus)"""
    s = 2 * r * (S11 if lados == 16 else S22)
    angs = (0, 22.5, 45, 67.5) if lados == 16 else (0, 45)
    for a in angs:
        N.R(bone, cx, y0, y1, cz, 2 * r, s, mat, ang=a)
        N.R(bone, cx, y0, y1, cz, s, 2 * r, mat, ang=a)

def perfil(N, bone, pts, mat, lados=16):
    """pilha de discos: pts = [(r, y0, y1)]"""
    for r, y0, y1 in pts: disco(N, bone, r, y0, y1, mat, lados)

def emblema(N, bone, cor, cy, z, px, sinal, placa, luz, moldura):
    """símbolo do anel da Tropa (o mesmo 16x16) em relevo, numa placa vertical; sinal -1 = frente (-z), +1 = costas"""
    m, _, _ = extrair(cor)
    L = 16 * px; t = 5
    zf = z * sinal                                   # face externa da placa
    zb = zf - sinal * t
    # placa com cantos chanfrados + moldura
    for (w, h) in ((L + 10, L - 6), (L - 6, L + 10)):
        N.g.box(bone, (-w / 2, cy - h / 2, min(zf, zb)), (w / 2, cy + h / 2, max(zf, zb)), placa, soft=False)
    for (x0, x1, y0, y1) in ((-L / 2 - 8, L / 2 + 8, cy + L / 2 + 2, cy + L / 2 + 8), (-L / 2 - 8, L / 2 + 8, cy - L / 2 - 8, cy - L / 2 - 2),
                             (-L / 2 - 8, -L / 2 - 2, cy - L / 2 - 2, cy + L / 2 + 2), (L / 2 + 2, L / 2 + 8, cy - L / 2 - 2, cy + L / 2 + 2)):
        N.g.box(bone, (x0, y0, min(zf, zf + sinal * 3)), (x1, y1, max(zf, zf + sinal * 3)), moldura, soft=False)
    used = [[False] * 16 for _ in range(16)]
    for r in range(16):
        c = 0
        while c < 16:
            if m[r, c] and not used[r][c]:
                c1 = c
                while c1 < 16 and m[r, c1] and not used[r][c1]: c1 += 1
                r1 = r + 1
                while r1 < 16 and all(m[r1, cc] and not used[r1][cc] for cc in range(c, c1)): r1 += 1
                for rr in range(r, r1):
                    for cc in range(c, c1): used[rr][cc] = True
                # coluna c cresce para a direita de quem olha a placa de fora
                xa, xb = (L / 2 - c * px, L / 2 - c1 * px) if sinal < 0 else (-L / 2 + c * px, -L / 2 + c1 * px)
                ya, yb = cy + L / 2 - r * px, cy + L / 2 - r1 * px
                N.g.box(bone, (min(xa, xb), min(ya, yb), min(zf, zf + sinal * 2.5)), (max(xa, xb), max(ya, yb), max(zf, zf + sinal * 2.5)), luz, soft=False)
                c = c1
            else: c += 1

def arco(N, bone, cx, cy, R, a0, a1, n, w, d, mat):
    """arco no plano x-y (alça), de a0 a a1 graus"""
    for k in range(n):
        t0 = math.radians(a0 + (a1 - a0) * k / n); t1 = math.radians(a0 + (a1 - a0) * (k + 1) / n)
        x0, y0 = cx + R * math.cos(t0), cy + R * math.sin(t0); x1, y1 = cx + R * math.cos(t1), cy + R * math.sin(t1)
        L = math.hypot(x1 - x0, y1 - y0) + w * 0.5; th = math.degrees(math.atan2(y1 - y0, x1 - x0))
        mx, my = (x0 + x1) / 2, (y0 + y1) / 2
        N.g.box(bone, (mx - L / 2, my - w / 2, -d / 2), (mx + L / 2, my + w / 2, d / 2), mat, rot=(0, 0, th), pivot=(mx, my, 0), soft=False)

def base(N, P):
    """pé da lanterna com a câmara do portal (5 x 5 x 6 blocos por dentro) e a porta na frente (-z)"""
    N.bone("root", (0, 0, 0)); N.bone("estrutura", (0, 0, 0), "root")
    disco(N, "estrutura", 128, 0, 16, P["piso"])
    N.casca("estrutura", 40, 100, 16, 104, P["corpo"], porta=(24, 80), lados=16)
    N.casca("estrutura", 40, 108, 16, 26, P["borda"], porta=(24, 26), lados=16)          # rodapé
    N.casca("estrutura", 40, 104, 58, 64, P["borda"], porta=(24, 58), lados=16)          # cinta do meio
    N.R("estrutura", 0, 80, 86, -72, 56, 62, P["borda"])                                  # verga da porta
    for sg in (-1, 1): N.R("estrutura", sg * 27, 26, 80, -101, 4, 2, P["luz"])            # batentes acesos
    for k in range(1, 16):                                                                 # frisos de luz no pé
        a = 2 * math.pi * k / 16
        if k in (1, 15): continue
        N.R("estrutura", -101 * math.sin(a), 30, 54, -101 * math.cos(a), 5, 1.5, P["luz"], ang=-22.5 * k)
    perfil(N, "estrutura", [(112, 104, 116), (100, 116, 126)], P["borda"])
    N.bone("campo", (0, 16, 0), "root"); N.R("campo", 0, 16, 112, 0, 44, 44, "campo")
    N.bone("campo_brilho", (0, 16, 0), "campo"); N.R("campo_brilho", 0, 16, 112, 0, 24, 24, "campo2")

def lanterna(N, cor, P, st):
    """corpo clássico; st = dicionário de estilo"""
    L = "lanterna"; N.bone(L, (0, 112, 0), "root")
    g = st.get("globo", 1.0); lados = st.get("lados", 16)
    # pescoço de baixo e BOCA LARGA de baixo
    perfil(N, L, [(70, 126, 152)], P["corpo"], lados); perfil(N, L, [(80, 136, 142)], P["borda"], lados)
    perfil(N, L, [(94, 152, 160), (120, 160, 168)], P["corpo2"], lados)
    perfil(N, L, [(142, 168, 178)], P["borda"], lados); perfil(N, L, [(134, 178, 184)], P["corpo2"], lados)
    # globo (vidro) com o núcleo
    N.bone("nucleo", (0, 260, 0), "root")
    gl = [(100 * g, 184, 200), (112 * g, 200, 220), (118 * g, 220, 300), (112 * g, 300, 320), (100 * g, 320, 336)]
    perfil(N, "nucleo", gl, "vidro", lados)
    N.bone("nucleo_brilho", (0, 260, 0), "nucleo")
    perfil(N, "nucleo_brilho", [(56 * g, 214, 306)], "nucleo", 16); N.R("nucleo_brilho", 0, 112, 214, 0, 18, 18, "nucleo")
    perfil(N, "nucleo_brilho", [(40 * g, 200, 320)], "nucleo", 16)
    # costelas da gaiola seguindo a curva do globo
    prof = [(100 * g + 4, 184), (116 * g + 4, 214), (122 * g + 4, 260), (116 * g + 4, 306), (100 * g + 4, 336)]
    n = st.get("costelas", 8)
    for k in range(n):
        a = 2 * math.pi * (k + 0.5) / n
        for (r0, y0), (r1, y1) in zip(prof, prof[1:]):
            N.seg(L, a, r0, y0, r1, y1, st.get("larg_costela", 12), P["corpo"], d=10)
        N.seg(L, a, prof[1][0] + 5, prof[1][1], prof[3][0] + 5, prof[3][1], 4, P["luz"], d=2)
    # cintas horizontais do globo
    for y in (214, 300): perfil(N, L, [(118 * g + 6, y, y + 6)], P["borda"], lados) if False else None
    # emblema na frente e atrás
    emblema(N, L, cor, 260, 124 * g + 2, st.get("px", 6.5), -1, P["placa"], "simbolo", P["borda"])
    emblema(N, L, cor, 260, 124 * g + 2, st.get("px", 6.5), 1, P["placa"], "simbolo", P["borda"])
    # BOCA LARGA de cima e pescoço
    perfil(N, L, [(134, 336, 342)], P["corpo2"], lados); perfil(N, L, [(142, 342, 352)], P["borda"], lados)
    perfil(N, L, [(120, 352, 360), (94, 360, 368)], P["corpo2"], lados)
    perfil(N, L, [(70, 368, 392)], P["corpo"], lados); perfil(N, L, [(80, 376, 382)], P["borda"], lados)
    # tampa em cúpula, haste e remate
    perfil(N, L, [(100, 392, 404), (88, 404, 416), (68, 416, 428), (44, 428, 440)], P["corpo2"], lados)
    perfil(N, L, [(104, 396, 400)], P["luz"], lados)
    perfil(N, L, [(16, 440, 500)], P["corpo"], 16)
    perfil(N, L, [(24, 456, 462), (24, 484, 490)], P["borda"], 16)
    # respiros na tampa
    for k in range(8):
        a = 2 * math.pi * (k + 0.5) / 8
        N.R(L, 89 * math.sin(a), 406, 414, 89 * math.cos(a), 14, 2, P["luz"], ang=math.degrees(a))
    # alça em arco por cima, presa nas laterais da tampa
    N.bone("alca", (0, 420, 0), L)
    for sg in (-1, 1):
        N.R("alca", sg * 102, 398, 424, 0, 16, 26, P["borda"])
        N.R("alca", sg * 114, 404, 418, 0, 12, 14, P["corpo"])
    if st.get("alca", "redonda") == "redonda":
        arco(N, "alca", 0, 420, 122, 0, 180, 16, 16, 18, P["corpo2"])
        arco(N, "alca", 0, 420, 122, 10, 170, 14, 4, 20, P["luz"])
    else:   # alça angular (Medo): 5 trechos retos com quinas
        arco(N, "alca", 0, 420, 124, 0, 180, 5, 18, 18, P["corpo2"])
    # anel de energia que flutua em volta do globo
    N.bone("anel_1", (0, 260, 0), "root")
    N.anel("anel_1", 160 * g, 256, 24, 30, 6, 5, P["luz"])
    return L

# ---------------------------------------------------------------------------------------------- estilos de cada Tropa
def remate(N, L, tipo, P):
    if tipo == "esfera":
        perfil(N, L, [(20, 500, 506), (28, 506, 520), (20, 520, 526)], P["corpo2"], 16); perfil(N, L, [(10, 526, 540)], "brilho", 16)
    elif tipo == "espinho":
        for i, (r, y0, y1) in enumerate(((22, 500, 512), (16, 512, 530), (10, 530, 552), (5, 552, 580))):
            disco(N, L, r, y0, y1, P["corpo2"] if i < 3 else "brilho", 8)
    elif tipo == "coroa":
        perfil(N, L, [(30, 500, 510)], P["corpo2"], 16)
        for k in range(8):
            a = 2 * math.pi * k / 8
            N.R(L, 26 * math.sin(a), 510, 536 + (k % 2) * 10, 26 * math.cos(a), 7, 7, P["corpo2"], ang=math.degrees(a), rx=-12, piv=(26 * math.sin(a), 510, 26 * math.cos(a)))
        perfil(N, L, [(10, 506, 530)], "brilho", 16)
    elif tipo == "cebola":
        perfil(N, L, [(22, 500, 506), (34, 506, 518), (36, 518, 530), (28, 530, 540), (16, 540, 550), (8, 550, 566)], P["corpo2"], 16)
        N.R(L, 0, 566, 584, 0, 14, 14, "gema", ang=45)
    elif tipo == "estrela":
        perfil(N, L, [(20, 500, 510)], P["corpo2"], 16)
        N.R(L, 0, 510, 570, 0, 8, 8, "brilho", ang=45)
        for a in (0, 90): N.R(L, 0, 528, 536, 0, 52, 6, "brilho", ang=a)
        for a in (45, 135): N.R(L, 0, 529, 535, 0, 30, 5, "brilho", ang=a)
    elif tipo == "cajado":
        perfil(N, L, [(20, 500, 510)], P["corpo2"], 16)
        arco(N, L, 0, 540, 26, -90, 270, 16, 8, 8, P["corpo2"])
        N.R(L, 0, 528, 552, 0, 12, 12, "brilho", ang=45)
    elif tipo == "coracao":
        perfil(N, L, [(20, 500, 510)], P["corpo2"], 16)
        for sg in (-1, 1):
            N.g.box(L, (sg * 2 - 14, 520, -10), (sg * 2 + 14, 548, 10), "gema", rot=(0, 0, 45 * sg), pivot=(sg * 2, 520, 0), soft=False)
        N.R(L, 0, 510, 528, 0, 16, 16, "gema", ang=45, rx=0)

def detalhes(N, L, cor, P, st):
    rnd = random.Random(5)
    if cor == "amarelo":       # pontas afiadas saindo das bocas, como garras
        for y, inc in ((172, 1), (346, -1)):
            for k in range(8):
                a = 2 * math.pi * (k + 0.5) / 8
                for i, (r, s, L_) in enumerate(((140, 14, 14), (154, 10, 12), (166, 6, 10), (176, 3, 8))):
                    N.R(L, r * math.sin(a), y - s / 2 + inc * i * 4, y + s / 2 + inc * i * 4, r * math.cos(a), s, s, P["corpo2"] if i < 3 else "brilho", ang=math.degrees(a) + 45)
        for k in range(8):
            a = 2 * math.pi * k / 8
            N.seg(L, a, 96, 128, 132, 100, 8, P["corpo2"])                 # garras do pé
    elif cor == "vermelho":    # chifres na boca de cima, presas na de baixo, sangue escorrendo
        for k in range(10):
            a = 2 * math.pi * k / 10
            for i, (r, y0, y1, s) in enumerate(((136, 352, 372, 12), (140, 372, 390, 8), (146, 390, 404, 4))):
                N.R(L, r * math.sin(a), y0, y1, r * math.cos(a), s, s, P["corpo2"], ang=math.degrees(a) + 45, rx=-14, piv=(r * math.sin(a), y0, r * math.cos(a)))
            for i, (r, y0, y1, s) in enumerate(((138, 150, 168, 10), (142, 136, 150, 6))):
                N.R(L, r * math.sin(a), y0, y1, r * math.cos(a), s, s, P["corpo2"], ang=math.degrees(a) + 45)
        for k in range(14):   # escorridos de sangue/energia
            a = 2 * math.pi * (k + 0.3) / 14; h = 20 + rnd.random() * 40
            N.R(L, 135 * math.sin(a), 178 - h, 178, 135 * math.cos(a), 5, 5, "sangue", ang=math.degrees(a))
            N.R(L, 135 * math.sin(a), 172 - h, 178 - h, 135 * math.cos(a), 8, 8, "sangue", ang=math.degrees(a))
    elif cor == "laranja":     # ourivesaria: gemas nas bocas, contas, faixas de filigrana
        for y in (172, 346):
            for k in range(16):
                a = 2 * math.pi * k / 16
                N.R(L, 142 * math.sin(a), y - 5, y + 5, 142 * math.cos(a), 10, 10, "gema" if k % 2 == 0 else P["borda"], ang=math.degrees(a) + 45)
        for y in (220, 300):
            N.anel(L, 122 * st.get("globo", 1), y, 24, 16, 5, 4, P["borda"])
        for k in range(12):
            a = 2 * math.pi * k / 12
            N.R(L, 104 * math.sin(a), 40, 52, -104 * math.cos(a) if False else 104 * math.cos(a), 10, 3, "gema", ang=math.degrees(a)) if (k not in (0,)) else None
    elif cor == "azul":        # asas nas bocas (o leque da esperança) e estrelas nas bandas
        for sg in (-1, 1):
            for i in range(5):
                L_ = 120 - i * 18; y = 350 + i * 12
                N.g.box(L, (sg * 140, y, -12 - 2 * i), (sg * (140 + L_), y + 6, 12 + 2 * i), P["borda"] if i % 2 == 0 else P["corpo2"], rot=(0, 0, sg * (18 + i * 6)), pivot=(sg * 140, y, 0), soft=False)
            for i in range(3):
                L_ = 70 - i * 18; y = 166 - i * 10
                N.g.box(L, (sg * 140, y, -5), (sg * (140 + L_), y + 7, 5), P["borda"], rot=(0, 0, -sg * (14 + i * 8)), pivot=(sg * 140, y, 0), soft=False)
            N.g.box(L, (sg * 142, 356, -2), (sg * 252, 359, 2), "brilho", rot=(0, 0, sg * 18), pivot=(sg * 140, 356, 0), soft=False)
    elif cor == "indigo":      # amarrações tribais e amuletos pendurados
        for y in (140, 380):
            perfil(N, L, [(74, y - 2, y + 10)], "corda", 16)
        for k in range(8):
            a = 2 * math.pi * (k + 0.5) / 8
            x, z = 140 * math.sin(a), 140 * math.cos(a)
            N.R(L, x, 120, 168, z, 2, 2, "corda")
            N.R(L, x, 104, 120, z, 8, 3, "osso" if k % 2 else "pena", ang=math.degrees(a))
            N.R(L, x, 98, 104, z, 4, 4, "brilho", ang=math.degrees(a))
    elif cor == "violeta":     # pétalas de cristal nas bocas e cristais na gaiola
        for y, inc, sg in ((168, 1, -1), (352, -1, 1)):
            for k in range(8):
                a = 2 * math.pi * k / 8
                r = 132; cx, cz = r * math.sin(a), r * math.cos(a)
                N.R(L, cx, y, y + 46 * (-sg), cz, 22, 8, "gema", ang=math.degrees(a), rx=sg * 35, piv=(cx, y, cz)) if sg == 1 else \
                N.R(L, cx, y - 46, y, cz, 22, 8, "gema", ang=math.degrees(a), rx=35, piv=(cx, y, cz))
        for k in range(8):
            a = 2 * math.pi * k / 8
            N.R(L, 128 * math.sin(a), 240, 280, 128 * math.cos(a), 12, 12, "gema", ang=math.degrees(a) + 45)

PAL = {
    "verde":    dict(corpo=("hull", "#1C4A2C", "#0E2A18"), corpo2=("hull", "#2A8A48", "#145A2C"), borda=("metal", "#8A9A92"), piso=("stone", "#3A4440", "#2A3430"),
                     placa=("metal", "#14201A"), vidro="#5AFF9A", nucleo="#4CFF8A", luz2="#8CFFB8", simbolo="#7CFF9C"),
    "amarelo":  dict(corpo=("hull", "#1A1A1C", "#0A0A0C"), corpo2=("hull", "#C8A400", "#6A5600"), borda=("metal", "#2E2E30"), piso=("obsidian", "#141416", "#3A3A10"),
                     placa=("metal", "#141416"), vidro="#FFE84A", nucleo="#FFD820", luz2="#FFF28A", simbolo="#FFE65A"),
    "vermelho": dict(corpo=("hull", "#3A0E0E", "#1E0606"), corpo2=("hull", "#8A1414", "#4A0808"), borda=("metal", "#2A1A1A"), piso=("magma", "#2A1410", "#FF5010"),
                     placa=("metal", "#1A0A0A"), vidro="#E0281A", nucleo="#FF3A10", luz2="#FF9A40", simbolo="#FF5A3A"),
    "laranja":  dict(corpo=("hull", "#6A3A18", "#3A1E0A"), corpo2=("hull", "#C88A20", "#7A4E0A"), borda=("metal", "#E0B040"), piso=("coins", "#5A3A10", "#E8B840"),
                     placa=("metal", "#3A1E0A"), vidro="#FF9A2A", nucleo="#FF8A10", luz2="#FFC870", simbolo="#FFB050"),
    "azul":     dict(corpo=("hull", "#1E4E9A", "#0E2A5A"), corpo2=("hull", "#D8E2EE", "#A8B8CC"), borda=("metal", "#C8D4E4"), piso=("stone", "#C8D4E4", "#8AA0BC"),
                     placa=("metal", "#0E2A5A"), vidro="#6AC8FF", nucleo="#3AA8FF", luz2="#BDEBFF", simbolo="#CDEBFF"),
    "indigo":   dict(corpo=("hull", "#2A2050", "#16102E"), corpo2=("hull", "#4A3A8A", "#2A1E5A"), borda=("metal", "#8A7AB8"), piso=("stone", "#3A344A", "#5A4A8A"),
                     placa=("metal", "#16102E"), vidro="#8A6AFF", nucleo="#7A5AFF", luz2="#CFC4FF", simbolo="#E0D8FF"),
    "violeta":  dict(corpo=("hull", "#4A1A5A", "#2A0A34"), corpo2=("hull", "#9A3AB8", "#5A1A6A"), borda=("metal", "#D8B8E8"), piso=("stone", "#4A2A5A", "#8A4AAA"),
                     placa=("metal", "#2A0A34"), vidro="#E070FF", nucleo="#D040FF", luz2="#FFB8FF", simbolo="#FFD0FF"),
}
ESTILO = {
    "verde":    dict(remate="esfera"),
    "amarelo":  dict(remate="espinho", alca="angular", lados=8, costelas=8, larg_costela=14),
    "vermelho": dict(remate="coroa", costelas=10),
    "laranja":  dict(remate="cebola", globo=1.1, costelas=12, larg_costela=10),
    "azul":     dict(remate="estrela", costelas=8),
    "indigo":   dict(remate="cajado", costelas=6),
    "violeta":  dict(remate="coracao", costelas=8),
}
NOMES = dict(verde="Bateria Central da Vontade", amarelo="Bateria Central do Medo", vermelho="Bateria Central da Fúria",
             laranja="Bateria Central da Avareza", azul="Bateria Central da Esperança", indigo="Bateria Central da Compaixão",
             violeta="Bateria Central do Amor")

def fazer(cor):
    def fn():
        A, N = nova(f"bateria_central_{cor}", 200 + list(PAL).index(cor))
        p = PAL[cor]
        for k in ("corpo", "corpo2", "borda", "piso", "placa"):
            spec = p[k]
            if spec[0] == "hull": A.material(k, "hull", spec[1], spec[2], step=64, seed=hash(k) % 50)
            elif spec[0] == "metal": A.material(k, "metal", spec[1])
            else: A.material(k, spec[0], spec[1], spec[2])
        A.material("vidro", "glass", p["vidro"], alpha=100, translucent=True)
        A.material("nucleo", "glow", p["nucleo"], core="#FFFFFF")
        A.material("simbolo", "glow", p["simbolo"], core="#FFFFFF")
        A.material("gema", "crystal", p["vidro"], alpha=230, glowing=True)
        A.material("sangue", "embers", base="#3A0606", hot="#FF2A10")
        A.material("corda", "fabric", "#D9CCB8", trim="#8A7A68")
        A.material("osso", "bone", "#E0D6C0")
        A.material("pena", "fabric", "#4B2BBF", trim="#CFC4FF")
        mats_comuns(A, p["nucleo"], p["luz2"])
        P = dict(corpo="corpo", corpo2="corpo2", borda="borda", piso="piso", placa="placa", luz="faixa_luz")
        base(N, P)
        st = ESTILO[cor]
        L = lanterna(N, cor, P, st)
        remate(N, L, st["remate"], P)
        detalhes(N, L, cor, P, st)
        return N.g, A
    return fn

BATERIAS = {c: fazer(c) for c in PAL}

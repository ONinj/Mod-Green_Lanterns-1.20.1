# Baterias Centrais das Tropas (modelos GeckoLib de bloco, gigantes)
# Origem do modelo = centro do bloco "núcleo", que fica embaixo do piso. 16 unidades = 1 bloco.
#   y 0..16    : laje do piso (o núcleo fica no meio dela)
#   y 16..112  : câmara (5 x 5 blocos por dentro, 6 de altura) com entrada de 3 x 4 blocos na frente (-z)
#                no meio da câmara: o campo de energia (portal) de 3 x 3 x 4 blocos
#   acima      : a lanterna/fonte temática de cada Tropa
import sys, math, random
sys.path.insert(0, "/home/claude/naves"); sys.path.insert(0, "/home/claude/lore3d"); sys.path.insert(0, "/home/claude/especies")
from naves import *            # padrões extras, Nave, emblem_decal, free_pixel
from naves2 import pat_burnt, pat_embers
from core import Atlas, hx, mix, lt, dk, PATTERNS

S22 = math.tan(math.radians(22.5))

class Bat(Nave):
    def R(self, bone, cx, y0, y1, cz, w, d, mat, ang=0, piv=None, rx=0, rz=0):
        """caixa centrada em (cx, cz), girada 'ang' graus no eixo vertical"""
        rot = (rx, ang, rz) if (ang or rx or rz) else None
        return self.g.box(bone, (cx - w / 2, y0, cz - d / 2), (cx + w / 2, y1, cz + d / 2), mat, rot=rot,
                          pivot=piv if piv else (cx, (y0 + y1) / 2, cz), soft=False)
    def oct(self, bone, r, y0, y1, mat, cx=0, cz=0):
        """octógono maciço (4 faixas cruzadas)"""
        s = 2 * r * S22
        for a in (0, 45):
            self.R(bone, cx, y0, y1, cz, 2 * r, s, mat, ang=a)
            self.R(bone, cx, y0, y1, cz, s, 2 * r, mat, ang=a)
    def casca(self, bone, r_in, r_out, y0, y1, mat, porta=None, lados=8):
        """parede oca em polígono; porta = (meia_largura, y_topo) abre a face da frente (-z)"""
        rm = (r_in + r_out) / 2; t = r_out - r_in; side = 2 * r_out * math.tan(math.pi / lados) + 2
        for k in range(lados):
            ang = 360 / lados * k
            a = math.radians(ang)
            cx, cz = -rm * math.sin(a), -rm * math.cos(a)
            if porta and k == 0:
                hw, yt = porta
                L = (side - 2 * hw) / 2
                for sg in (-1, 1):
                    self.R(bone, sg * (hw + L / 2), y0, y1, cz, L, t, mat)
                if yt < y1: self.R(bone, 0, yt, y1, cz, 2 * hw, t, mat)
            else:
                self.R(bone, cx, y0, y1, cz, side, t, mat, ang=-ang)
    def anel(self, bone, r, y, n, w, h, d, mat, fase=0.0):
        for k in range(n):
            a = 2 * math.pi * (k + fase) / n
            self.R(bone, r * math.sin(a), y, y + h, r * math.cos(a), w, d, mat, ang=math.degrees(a))
    def seg(self, bone, a, r0, y0, r1, y1, w, mat, d=None):
        """barra no plano radial do ângulo a (rad), de (r0, y0) até (r1, y1)"""
        L = math.hypot(r1 - r0, y1 - y0); th = math.degrees(math.atan2(r1 - r0, y1 - y0))
        cx, cz = r0 * math.sin(a), r0 * math.cos(a)
        self.g.box(bone, (cx - w / 2, y0, cz - (d or w) / 2), (cx + w / 2, y0 + L, cz + (d or w) / 2), mat, rot=(th, math.degrees(a), 0), pivot=(cx, y0, cz), soft=False)
    def barra(self, bone, x0, z0, x1, z1, y, h, w, mat):
        """barra horizontal de (x0, z0) a (x1, z1) na altura y"""
        dx, dz = x1 - x0, z1 - z0; L = math.hypot(dx, dz) + w * 0.6
        cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
        self.g.box(bone, (cx - L / 2, y, cz - w / 2), (cx + L / 2, y + h, cz + w / 2), mat, rot=(0, math.degrees(math.atan2(-dz, dx)), 0), pivot=(cx, y, cz), soft=False)
    def costelas(self, bone, r, y0, y1, n, w, d, mat, fase=0.0, inclina=0.0):
        """pilares verticais em volta (inclina > 0 abre para fora no topo)"""
        for k in range(n):
            a = 2 * math.pi * (k + fase) / n
            cx, cz = r * math.sin(a), r * math.cos(a)
            self.R(bone, cx, y0, y1, cz, w, d, mat, ang=math.degrees(a), rx=inclina, piv=(cx, y0, cz))

def base_comum(N, parede, piso, borda, luz=None, r_out=96):
    """laje do piso, paredes da câmara com a entrada e o campo do portal"""
    N.bone("root", (0, 0, 0)); N.bone("estrutura", (0, 0, 0), "root")
    N.oct("estrutura", 120, 0, 16, piso)
    N.casca("estrutura", 40, r_out, 16, 112, parede, porta=(24, 80))
    N.casca("estrutura", 40, r_out + 6, 104, 116, borda)
    N.casca("estrutura", 40, r_out + 6, 16, 22, borda, porta=(24, 22))
    N.R("estrutura", 0, 80, 84, -(40 + r_out) / 2 - 1, 52, r_out - 40 + 4, borda)     # verga da porta
    if luz:
        for k in range(1, 8):
            a = math.radians(45 * k)
            N.R("estrutura", -(r_out + 0.5) * math.sin(a), 30, 98, -(r_out + 0.5) * math.cos(a), 6, 1.5, luz, ang=-45 * k)
        for sg in (-1, 1): N.R("estrutura", sg * 27, 22, 78, -r_out - 0.5, 3, 1.5, luz)   # batentes acesos
    # campo de energia (portal) e coluna que sobe até o núcleo
    N.bone("campo", (0, 16, 0), "root")
    N.R("campo", 0, 16, 112, 0, 44, 44, "campo")
    N.bone("campo_brilho", (0, 16, 0), "campo")
    N.R("campo_brilho", 0, 16, 112, 0, 24, 24, "campo2")

def mats_comuns(A, cor, cor2, branco="#FFFFFF"):
    A.material("campo", "glass", cor, alpha=110, translucent=True)
    A.material("campo2", "flame", cor2, core=branco, translucent=True)
    A.material("brilho", "glow", cor, core=branco)
    A.material("faixa_luz", "strip", cor, "#202020")

def nova(nome, seed):
    A = Atlas(1024, 128, seed=seed)
    return A, Bat(nome, A)

# ======================================================================== VERDE — a Lanterna da Vontade
def verde():
    A, N = nova("bateria_central_verde", 101)
    A.material("metal", "hull", "#2A3A34", "#18221E", step=64, seed=1)
    A.material("metal2", "hull", "#1E7A44", "#0E4024", step=64, seed=2)
    A.material("piso", "stone", "#3A4440", "#2A3430")
    A.material("borda", "metal", "#6A7A74")
    A.material("vidro", "glass", "#5AFF9A", alpha=95, translucent=True)
    A.material("nucleo", "glow", "#4CFF8A", core="#F0FFF4")
    mats_comuns(A, "#4CFF8A", "#8CFFB8")
    A.material("colisao", "invisible")
    base_comum(N, "metal", "piso", "borda", luz="faixa_luz")
    # tambor da lanterna: aros, 8 costelas e cúpula
    N.bone("lanterna", (0, 112, 0), "root")
    N.oct("lanterna", 104, 116, 132, "metal2"); N.oct("lanterna", 96, 132, 140, "borda")
    N.costelas("lanterna", 94, 140, 372, 8, 22, 18, "metal", fase=0.5)
    N.costelas("lanterna", 103.5, 160, 352, 8, 6, 1.5, "faixa_luz", fase=0.5)
    for y in (250,):
        N.casca("lanterna", 86, 108, y, y + 14, "metal2")
    N.oct("lanterna", 104, 372, 388, "metal2"); N.oct("lanterna", 88, 388, 404, "metal"); N.oct("lanterna", 64, 404, 418, "borda")
    N.oct("lanterna", 40, 418, 432, "metal2"); N.oct("lanterna", 16, 432, 470, "borda")
    N.R("lanterna", 0, 470, 486, 0, 10, 10, "brilho")
    # alça de cima (como a de uma lanterna de mão, gigante)
    for sg in (-1, 1): N.R("lanterna", sg * 60, 432, 500, 0, 12, 14, "metal2", rz=sg * 18, piv=(sg * 60, 432, 0))
    N.R("lanterna", 0, 492, 506, 0, 82, 14, "metal2")
    # vidro e núcleo de energia
    N.bone("nucleo", (0, 256, 0), "root")
    N.oct("nucleo", 84, 140, 372, "vidro")
    N.bone("nucleo_brilho", (0, 256, 0), "nucleo")
    N.oct("nucleo_brilho", 44, 170, 342, "nucleo")
    N.R("nucleo_brilho", 0, 112, 170, 0, 18, 18, "nucleo")
    # anéis flutuantes que giram
    N.bone("anel_1", (0, 200, 0), "root"); N.anel("anel_1", 132, 196, 16, 26, 8, 6, "metal2")
    N.anel("anel_1", 132.5, 198, 16, 18, 4, 6.5, "faixa_luz")
    N.bone("anel_2", (0, 310, 0), "root"); N.anel("anel_2", 126, 306, 12, 30, 8, 6, "metal2", fase=0.5)
    N.anel("anel_2", 126.5, 308, 12, 22, 4, 6.5, "faixa_luz", fase=0.5)
    return N.g, A

# ======================================================================== AMARELO — a Gaiola do Medo
def amarelo():
    A, N = nova("bateria_central_amarelo", 102)
    A.material("preto", "hull", "#1A1A1C", "#0A0A0C", step=64, seed=3)
    A.material("quitina", "hull", "#C8A400", "#6A5600", step=32, seed=4)
    A.material("piso", "obsidian", "#141416", "#3A3A10")
    A.material("borda", "metal", "#4A4A40")
    A.material("vidro", "glass", "#FFE84A", alpha=100, translucent=True)
    A.material("nucleo", "glow", "#FFD820", core="#FFFFF0")
    mats_comuns(A, "#FFE040", "#FFF28A")
    base_comum(N, "preto", "piso", "quitina", luz="faixa_luz")
    N.bone("lanterna", (0, 112, 0), "root")
    # espinhos na base, voltados para fora
    for k in range(12):
        a = 2 * math.pi * (k + 0.5) / 12
        cx, cz = 104 * math.sin(a), 104 * math.cos(a)
        if cz < -60 and abs(cx) < 50: continue
        for i in range(3):
            N.R("lanterna", cx + 10 * i * math.sin(a), 30 + i * 4, 46 - i * 2, cz + 10 * i * math.cos(a), 14 - i * 4, 14 - i * 4, "quitina", ang=math.degrees(a))
    N.oct("lanterna", 100, 116, 136, "preto")
    # seis garras curvas que prendem o núcleo (3 segmentos cada, fechando para dentro no alto)
    for k in range(6):
        a = 2 * math.pi * k / 6; ang = math.degrees(a)
        segs = [(100, 136, 240, 6), (112, 236, 330, -8), (92, 326, 420, -26)]
        for (r, y0, y1, inc) in segs:
            cx, cz = r * math.sin(a), r * math.cos(a)
            N.R("lanterna", cx, y0, y1, cz, 26, 22, "preto", ang=ang, rx=inc, piv=(cx, y0, cz))
            N.R("lanterna", cx * 1.02, y0 + 8, y1 - 8, cz * 1.02, 8, 24, "quitina", ang=ang, rx=inc, piv=(cx, y0, cz))
        cx, cz = 60 * math.sin(a), 60 * math.cos(a)
        for i in range(4):   # ponta da garra
            N.R("lanterna", cx * (1 - i * 0.12), 414 + i * 14, 430 + i * 14, cz * (1 - i * 0.12), 18 - i * 4, 18 - i * 4, "quitina", ang=ang)
    N.bone("nucleo", (0, 270, 0), "root")
    N.oct("nucleo", 74, 150, 380, "vidro")
    N.bone("nucleo_brilho", (0, 270, 0), "nucleo")
    for (r, y0, y1) in ((36, 170, 220), (48, 220, 320), (36, 320, 360)): N.oct("nucleo_brilho", r, y0, y1, "nucleo")
    N.R("nucleo_brilho", 0, 112, 170, 0, 16, 16, "nucleo")
    # estilhaços negros flutuando
    N.bone("anel_1", (0, 260, 0), "root")
    for k in range(10):
        a = 2 * math.pi * k / 10; r = 150 + (k % 3) * 10; y = 220 + (k % 4) * 30
        N.R("anel_1", r * math.sin(a), y, y + 26 + (k % 2) * 12, r * math.cos(a), 10, 10, "preto", ang=math.degrees(a) + 20, rz=15 * ((-1) ** k))
    N.bone("anel_2", (0, 380, 0), "root"); N.anel("anel_2", 120, 376, 9, 16, 6, 6, "quitina")
    return N.g, A

# ======================================================================== VERMELHO — o Coração Vulcânico da Fúria
def vermelho():
    A, N = nova("bateria_central_vermelho", 103)
    A.material("rocha", "cracks", "#3A1E18", "#FF4A10", density=4, width=1, glow=True)
    A.material("rocha2", "stone", "#4A2A22", "#2A140E")
    A.material("piso", "magma", "#2A1410", "#FF5010")
    A.material("borda", "stone", "#2A1A16", "#5A2A1A")
    A.material("vidro", "crystal", "#C81A10", alpha=170, glowing=False, translucent=True)
    A.material("nucleo", "glow", "#FF3A10", core="#FFE0A0")
    A.material("lava", "embers", base="#2A0A06", hot="#FF5A10")
    A.material("cristal", "crystal", "#FF2A10", alpha=230, glowing=True)
    mats_comuns(A, "#FF3A20", "#FF9A40")
    base_comum(N, "rocha2", "piso", "borda", luz="faixa_luz")
    N.bone("lanterna", (0, 112, 0), "root")
    rnd = random.Random(7)
    # monólito de rocha partido: blocos irregulares empilhados com fendas de lava
    for (r, y0, y1, n) in ((104, 112, 180, 9), (98, 176, 260, 8), (90, 254, 340, 7), (76, 334, 410, 6), (52, 404, 460, 5)):
        for k in range(n):
            a = 2 * math.pi * (k + rnd.random() * 0.3) / n
            cx, cz = r * math.sin(a), r * math.cos(a)
            if 176 <= y0 <= 334 and k % 2 == 0:      # fendas: a casca se abriu e o núcleo aparece; cristais saem pela fenda
                N.R("lanterna", cx * 1.05, y0 + 20, y1 + 30, cz * 1.05, 18, 18, "cristal", ang=math.degrees(a) + 45, rx=rnd.uniform(15, 30), piv=(cx * 0.8, y0, cz * 0.8))
                continue
            N.R("lanterna", cx, y0 + rnd.uniform(0, 8), y1 + rnd.uniform(-6, 10), cz, 2 * r * math.tan(math.pi / n) * 0.95, 26 + rnd.uniform(0, 10),
                "rocha" if k % 2 else "rocha2", ang=math.degrees(a) + rnd.uniform(-6, 6), rx=rnd.uniform(-5, 5), rz=rnd.uniform(-4, 4))
    N.R("lanterna", 0, 456, 490, 0, 30, 30, "rocha2", ang=20)
    for k in range(6):   # escorridos de lava
        a = 2 * math.pi * k / 6 + 0.3
        N.R("lanterna", 108 * math.sin(a), 120 + k * 10, 230 + k * 10, 108 * math.cos(a), 6, 3, "lava", ang=math.degrees(a))
    N.bone("nucleo", (0, 270, 0), "root")
    N.oct("nucleo", 70, 150, 400, "vidro")
    N.bone("nucleo_brilho", (0, 270, 0), "nucleo")
    N.oct("nucleo_brilho", 56, 180, 370, "nucleo"); N.R("nucleo_brilho", 0, 112, 180, 0, 18, 18, "nucleo")
    # pedras flutuantes em brasa
    N.bone("anel_1", (0, 300, 0), "root")
    for k in range(9):
        a = 2 * math.pi * k / 9; r = 150 + (k % 2) * 16; y = 250 + (k % 3) * 40
        N.R("anel_1", r * math.sin(a), y, y + 18 + (k % 3) * 6, r * math.cos(a), 20, 18, "rocha", ang=k * 33, rx=k * 7, rz=-k * 5)
    return N.g, A

# ======================================================================== LARANJA — o Relicário da Avareza
def laranja():
    A, N = nova("bateria_central_laranja", 104)
    A.material("ouro", "metal", "#D4A030")
    A.material("ouro2", "hull", "#9A6A18", "#5A3A08", step=32, seed=5)
    A.material("bronze", "hull", "#6A3A18", "#3A1E0A", step=64, seed=6)
    A.material("piso", "coins", "#5A3A10", "#E8B840")
    A.material("moedas", "coins", "#8A5A10", "#FFD050")
    A.material("vidro", "glass", "#FF9A2A", alpha=105, translucent=True)
    A.material("nucleo", "glow", "#FF8A10", core="#FFF4D0")
    A.material("gema", "crystal", "#FF6A10", alpha=220, glowing=True)
    mats_comuns(A, "#FF9A30", "#FFC870")
    base_comum(N, "bronze", "piso", "ouro", luz="faixa_luz")
    N.bone("lanterna", (0, 112, 0), "root")
    # pilhas de moedas em volta da base
    for k in range(10):
        a = 2 * math.pi * (k + 0.5) / 10; cx, cz = 118 * math.sin(a), 118 * math.cos(a)
        if cz < -70 and abs(cx) < 60: continue
        N.R("lanterna", cx, 16, 16 + 12 + (k % 3) * 10, cz, 18, 18, "moedas", ang=k * 17)
    # templete: colunas douradas, cúpula em gomos e pináculo bulboso
    N.oct("lanterna", 104, 116, 132, "ouro"); N.oct("lanterna", 96, 132, 144, "ouro2")
    N.costelas("lanterna", 90, 144, 330, 8, 16, 16, "ouro", fase=0.5)
    N.costelas("lanterna", 90, 144, 160, 8, 26, 26, "ouro2", fase=0.5)
    N.costelas("lanterna", 90, 314, 330, 8, 26, 26, "ouro2", fase=0.5)
    N.oct("lanterna", 104, 330, 344, "ouro2"); N.oct("lanterna", 98, 344, 354, "ouro")
    for (r, y0, y1) in ((92, 354, 372), (80, 372, 390), (62, 390, 406), (40, 406, 420)): N.oct("lanterna", r, y0, y1, "ouro2" if r % 3 else "ouro")
    for (r, y0, y1) in ((22, 420, 440), (30, 440, 456), (22, 456, 470), (10, 470, 500)): N.oct("lanterna", r, y0, y1, "ouro")
    N.R("lanterna", 0, 500, 516, 0, 12, 12, "gema", ang=45)
    for k in range(8):   # gemas na cúpula
        a = 2 * math.pi * (k + 0.5) / 8
        N.R("lanterna", 99 * math.sin(a), 340, 352, 99 * math.cos(a), 10, 4, "gema", ang=math.degrees(a))
    N.bone("nucleo", (0, 240, 0), "root")
    N.oct("nucleo", 80, 144, 330, "vidro")
    N.bone("nucleo_brilho", (0, 240, 0), "nucleo")
    N.oct("nucleo_brilho", 40, 170, 300, "nucleo"); N.R("nucleo_brilho", 0, 112, 170, 0, 18, 18, "nucleo")
    # anel de moedas gigantes flutuando
    N.bone("anel_1", (0, 250, 0), "root"); N.anel("anel_1", 140, 240, 14, 20, 20, 3, "ouro")
    N.anel("anel_1", 140.5, 246, 14, 10, 8, 3.5, "gema")
    return N.g, A

# ======================================================================== AZUL — o Farol da Esperança
def azul():
    A, N = nova("bateria_central_azul", 105)
    A.material("marmore", "stone", "#E8EEF6", "#B8C8DC")
    A.material("marmore2", "hull", "#D0DCEA", "#A0B4CC", step=64, seed=7)
    A.material("azul", "hull", "#1E6AD8", "#0A3A8A", step=64, seed=8)
    A.material("prata", "metal", "#C8D4E4")
    A.material("piso", "stone", "#C8D4E4", "#8AA0BC")
    A.material("vidro", "glass", "#6AC8FF", alpha=95, translucent=True)
    A.material("nucleo", "glow", "#3AA8FF", core="#FFFFFF")
    mats_comuns(A, "#5ABAFF", "#BDEBFF")
    base_comum(N, "marmore", "piso", "prata", luz="faixa_luz")
    N.bone("lanterna", (0, 112, 0), "root")
    N.oct("lanterna", 100, 116, 134, "marmore2"); N.oct("lanterna", 88, 134, 150, "azul")
    # torre esguia de vidro com costelas de prata
    N.costelas("lanterna", 70, 150, 400, 8, 10, 12, "prata", fase=0.5)
    # barbatanas/asas em leque (como raios de um nascer do sol)
    for k in range(8):
        a = 2 * math.pi * (k + 0.5) / 8; ang = math.degrees(a)
        for i, (r, y0, y1, w, inc) in enumerate(((96, 150, 300, 30, 14), (120, 230, 400, 18, 20), (138, 320, 470, 8, 24))):
            cx, cz = r * math.sin(a), r * math.cos(a)
            N.R("lanterna", cx, y0, y1, cz, 3, w, "marmore2" if i != 1 else "azul", ang=ang + 90, rx=0, rz=0)
        N.R("lanterna", 97.5 * math.sin(a), 170, 290, 97.5 * math.cos(a), 1.5, 4, "faixa_luz", ang=ang + 90)
    N.oct("lanterna", 80, 400, 414, "prata"); N.oct("lanterna", 56, 414, 428, "marmore2"); N.oct("lanterna", 30, 428, 460, "azul")
    N.oct("lanterna", 12, 460, 520, "prata"); N.R("lanterna", 0, 520, 540, 0, 14, 14, "brilho", ang=45)
    N.bone("nucleo", (0, 270, 0), "root")
    N.oct("nucleo", 64, 150, 400, "vidro")
    N.bone("nucleo_brilho", (0, 270, 0), "nucleo")
    N.oct("nucleo_brilho", 36, 180, 370, "nucleo"); N.R("nucleo_brilho", 0, 112, 180, 0, 16, 16, "nucleo")
    # auréolas que giram
    N.bone("anel_1", (0, 420, 0), "root"); N.anel("anel_1", 100, 416, 24, 26, 5, 5, "prata"); N.anel("anel_1", 100.5, 417, 24, 14, 3, 5.5, "brilho")
    N.bone("anel_2", (0, 250, 0), "root"); N.anel("anel_2", 160, 248, 28, 34, 4, 4, "faixa_luz")
    return N.g, A

# ======================================================================== ÍNDIGO — o Totem da Compaixão
def indigo():
    A, N = nova("bateria_central_indigo", 106)
    A.material("madeira", "bone", "#5A4030")
    A.material("pedra", "stone", "#4A4458", "#2A2438")
    A.material("pano", "fabric", "#D9CCB8", trim="#8A7A68")
    A.material("pano2", "fabric", "#4B2BBF", trim="#CFC4FF")
    A.material("piso", "stone", "#3A344A", "#5A4A8A", vein_glow=True)
    A.material("vidro", "glass", "#8A6AFF", alpha=100, translucent=True)
    A.material("nucleo", "glow", "#7A5AFF", core="#F0ECFF")
    A.material("osso", "bone", "#E0D6C0")
    mats_comuns(A, "#8A70FF", "#CFC4FF")
    base_comum(N, "pedra", "piso", "madeira", luz="faixa_luz")
    N.bone("lanterna", (0, 112, 0), "root")
    # estandartes de pano pendurados nas paredes da base
    for k in (1, 3, 5, 7):
        a = math.radians(45 * k)
        N.R("lanterna", -(98) * math.sin(a), 40, 108, -(98) * math.cos(a), 26, 1.5, "pano2", ang=-45 * k)
    N.oct("lanterna", 100, 116, 132, "madeira")
    # berço de galhos: 5 troncos contínuos que sobem, abrem e voltam a fechar sobre o núcleo
    pts = [(78, 132), (100, 200), (108, 270), (100, 340), (80, 400), (52, 450), (30, 480)]
    for k in range(5):
        a = 2 * math.pi * k / 5
        for i, ((r0, y0), (r1, y1)) in enumerate(zip(pts, pts[1:])):
            N.seg("lanterna", a, r0, y0, r1, y1, 22 - i * 2.5, "madeira")
        # galhinhos laterais
        N.seg("lanterna", a + 0.18, 104, 250, 140, 300, 8, "madeira"); N.seg("lanterna", a - 0.15, 100, 340, 130, 380, 7, "madeira")
        cx, cz = 109 * math.sin(a), 109 * math.cos(a)
        N.R("lanterna", cx, 250, 290, cz, 26, 26, "pano", ang=math.degrees(a))                 # ataduras nos galhos
        for j in range(3):                                                                      # amuletos pendurados
            x, z = 132 * math.sin(a + 0.18), 132 * math.cos(a + 0.18)
            N.R("lanterna", x, 286 - j * 14, 298 - j * 14, z, 6, 2, "osso" if j % 2 else "pano2", ang=math.degrees(a))
        N.R("lanterna", 132 * math.sin(a + 0.18), 298, 302, 132 * math.cos(a + 0.18), 1.5, 1.5, "pano")
    N.bone("nucleo", (0, 280, 0), "root")
    N.oct("nucleo", 66, 160, 400, "vidro")
    N.bone("nucleo_brilho", (0, 280, 0), "nucleo")
    for (r, y0, y1) in ((30, 180, 230), (44, 230, 340), (30, 340, 380)): N.oct("nucleo_brilho", r, y0, y1, "nucleo")
    N.R("nucleo_brilho", 0, 112, 180, 0, 14, 14, "nucleo")
    N.bone("anel_1", (0, 330, 0), "root")
    for k in range(7):   # contas gigantes orbitando
        a = 2 * math.pi * k / 7; N.oct("anel_1", 8, 324 + (k % 2) * 10, 340 + (k % 2) * 10, "brilho" if k % 2 else "osso", cx=140 * math.sin(a), cz=140 * math.cos(a))
    return N.g, A

# ======================================================================== VIOLETA — o Cristal do Amor
def violeta():
    A, N = nova("bateria_central_violeta", 107)
    A.material("pedra", "stone", "#5A3A60", "#FF9CF2", vein_glow=True)
    A.material("piso", "stone", "#4A2A50", "#B03AD8")
    A.material("borda", "metal", "#E8D8F0")
    A.material("cristal", "crystal", "#C04AE8", alpha=200, glowing=False, translucent=True)
    A.material("cristal2", "crystal", "#FF7AD8", alpha=200, glowing=True, translucent=True)
    A.material("cristal3", "crystal", "#8A2AB0", alpha=230, glowing=False)
    A.material("nucleo", "glow", "#FF5CC8", core="#FFF0FA")
    A.material("vidro", "glass", "#FF8ADA", alpha=95, translucent=True)
    mats_comuns(A, "#FF6AD0", "#FFB8F2")
    base_comum(N, "pedra", "piso", "borda", luz="faixa_luz")
    N.bone("lanterna", (0, 112, 0), "root")
    rnd = random.Random(11)
    N.oct("lanterna", 100, 116, 136, "cristal3")
    # aglomerado de cristais gigantes inclinados para fora em volta do núcleo
    for k in range(14):
        a = 2 * math.pi * (k + rnd.random() * 0.4) / 14
        r = 86 + rnd.uniform(-8, 12); h = rnd.uniform(160, 330); w = rnd.uniform(26, 44)
        cx, cz = r * math.sin(a), r * math.cos(a)
        m = ("cristal", "cristal2", "cristal3")[k % 3]
        N.R("lanterna", cx, 132, 132 + h, cz, w, w, m, ang=math.degrees(a) + 45, rx=rnd.uniform(8, 22), piv=(cx, 132, cz))
        N.R("lanterna", cx, 132 + h, 132 + h + w * 0.8, cz, w * 0.55, w * 0.55, m, ang=math.degrees(a) + 45, rx=rnd.uniform(8, 22), piv=(cx, 132, cz))
    # cristal-mestre no alto
    N.R("lanterna", 0, 400, 520, 0, 50, 50, "cristal2", ang=45); N.R("lanterna", 0, 520, 560, 0, 28, 28, "cristal2", ang=45)
    N.bone("nucleo", (0, 270, 0), "root")
    N.oct("nucleo", 70, 140, 400, "vidro")
    N.bone("nucleo_brilho", (0, 270, 0), "nucleo")
    for (r, y0, y1) in ((26, 190, 220), (46, 220, 300), (30, 300, 340), (14, 340, 360)): N.oct("nucleo_brilho", r, y0, y1, "nucleo")
    N.R("nucleo_brilho", 0, 112, 190, 0, 14, 14, "nucleo")
    N.bone("anel_1", (0, 300, 0), "root")
    for k in range(8):
        a = 2 * math.pi * k / 8; r = 160; y = 270 + (k % 3) * 30
        N.R("anel_1", r * math.sin(a), y, y + 34, r * math.cos(a), 12, 12, "cristal2" if k % 2 else "cristal", ang=45 + k * 20, rx=20, rz=10)
    return N.g, A

# ======================================================================== PRETO — o Ossuário da Morte
def preto():
    A, N = nova("bateria_central_preto", 108)
    A.material("obsidiana", "obsidian", "#0E0E14", "#3A3A5A")
    A.material("pedra", "stone", "#1A1A20", "#2A2A33")
    A.material("piso", "obsidian", "#101016", "#2A2A40")
    A.material("osso", "bone", "#D8D6CC")
    A.material("osso2", "bone", "#A8A69C")
    A.material("vidro", "glass", "#20202A", alpha=150, translucent=True)
    A.material("nucleo", "glow", "#C8D0FF", core="#FFFFFF")
    A.material("olho", "glow", "#E8E8FF", core="#FFFFFF")
    mats_comuns(A, "#B8C0FF", "#E8ECFF")
    base_comum(N, "pedra", "piso", "osso2", luz="faixa_luz")
    N.bone("lanterna", (0, 112, 0), "root")
    # crânios na base (um por face, menos a da porta)
    for k in range(1, 8):
        a = math.radians(45 * k); cx, cz = -100 * math.sin(a), -100 * math.cos(a)
        N.R("lanterna", cx, 70, 92, cz, 22, 18, "osso", ang=-45 * k)
        N.R("lanterna", cx * 1.0, 64, 72, cz * 1.0, 16, 16, "osso2", ang=-45 * k)
        tx, tz = -math.cos(a), math.sin(a)
        for sg in (-1, 1):
            N.R("lanterna", cx * 1.095 + sg * 5 * tx, 80, 86, cz * 1.095 + sg * 5 * tz, 5, 1, "olho", ang=-45 * k)
    # obelisco negro com caixa torácica de osso em volta do núcleo
    N.oct("lanterna", 100, 116, 140, "obsidiana")
    for i in range(10):                                                    # coluna (vértebras atrás)
        N.R("lanterna", 0, 150 + i * 27, 170 + i * 27, 96, 30, 24, "osso" if i % 2 == 0 else "osso2")
    for i in range(7):   # costelas: arcos que saem da coluna e dão a volta até o esterno
        y = 176 + i * 34
        for sg in (-1, 1):
            arco = []
            for j in range(7):
                ph = math.radians(12 + j * 25)
                arco.append((sg * 100 * math.sin(ph), 94 * math.cos(ph), y - j * 3))
            for (x0, z0, y0), (x1, z1, y1) in zip(arco, arco[1:]):
                N.barra("lanterna", x0, z0, x1, z1, y1, 10, 10, "osso")
    N.R("lanterna", 0, 168, 400, -86, 22, 10, "osso2")                    # esterno
    N.oct("lanterna", 90, 420, 440, "obsidiana")
    for i, (r, y0, y1) in enumerate(((70, 440, 470), (46, 470, 510), (24, 510, 570), (8, 570, 610))): N.oct("lanterna", r, y0, y1, "obsidiana")
    # crânio gigante na frente do pináculo
    N.R("lanterna", 0, 440, 488, -58, 52, 30, "osso"); N.R("lanterna", 0, 428, 444, -56, 36, 24, "osso2")
    for sg in (-1, 1): N.R("lanterna", sg * 12, 458, 472, -73.5, 12, 2, "olho")
    N.bone("nucleo", (0, 280, 0), "root")
    N.oct("nucleo", 70, 150, 420, "vidro")
    N.bone("nucleo_brilho", (0, 280, 0), "nucleo")
    N.oct("nucleo_brilho", 30, 190, 380, "nucleo"); N.R("nucleo_brilho", 0, 112, 190, 0, 12, 12, "nucleo")
    N.bone("anel_1", (0, 300, 0), "root")
    for k in range(8):   # ossos flutuando
        a = 2 * math.pi * k / 8; r = 150; y = 260 + (k % 3) * 34
        N.R("anel_1", r * math.sin(a), y, y + 40, r * math.cos(a), 8, 8, "osso", ang=k * 30, rx=40, rz=20)
    return N.g, A

# ======================================================================== BRANCO — a Árvore da Vida (fonte; a Tropa da Vida não usa bateria)
def branco():
    A, N = nova("bateria_central_branco", 109)
    A.material("marmore", "stone", "#F2F2EE", "#D8C890")
    A.material("tronco", "hull", "#E8E6DE", "#B8B0A0", step=64, seed=9)
    A.material("ouro", "metal", "#E2B13A")
    A.material("folha", "leaf", "#F2D070", "#C89A30")
    A.material("piso", "stone", "#E8E6E0", "#E2B13A")
    A.material("vidro", "glass", "#FFF6D8", alpha=90, translucent=True)
    A.material("nucleo", "glow", "#FFFFFF", core="#FFFFFF")
    A.material("luz", "glow", "#FFF0B0", core="#FFFFFF")
    mats_comuns(A, "#FFF6C8", "#FFFFFF")
    base_comum(N, "marmore", "piso", "ouro", luz="faixa_luz")
    N.bone("lanterna", (0, 112, 0), "root")
    # raízes descendo pelas paredes
    for k in range(1, 8):
        a = math.radians(45 * k); cx, cz = -99 * math.sin(a), -99 * math.cos(a)
        N.R("lanterna", cx, 20, 116, cz, 8, 3, "ouro", ang=-45 * k, rz=(k % 2) * 8 - 4)
    # tronco que se abre em 6 galhos segurando a esfera de luz
    for (r, y0, y1) in ((70, 116, 160), (58, 160, 220), (48, 220, 260)): N.oct("lanterna", r, y0, y1, "tronco")
    for k in range(6):
        a = 2 * math.pi * k / 6
        for i, (r, y0, y1, inc) in enumerate(((50, 250, 330, 28), (96, 326, 420, 12), (110, 416, 480, -14))):
            cx, cz = r * math.sin(a), r * math.cos(a)
            N.R("lanterna", cx, y0, y1, cz, 22 - i * 4, 22 - i * 4, "tronco" if i < 2 else "ouro", ang=math.degrees(a), rx=inc, piv=(cx, y0, cz))
        for j in range(4):   # folhas douradas
            aa = a + (j - 1.5) * 0.25; r = 128 + (j % 2) * 10; y = 440 + j * 12
            N.R("lanterna", r * math.sin(aa), y, y + 8, r * math.cos(aa), 26, 14, "folha", ang=math.degrees(aa), rx=-20)
    N.bone("nucleo", (0, 380, 0), "root")
    N.oct("nucleo", 70, 330, 450, "vidro")
    N.bone("nucleo_brilho", (0, 380, 0), "nucleo")
    for (r, y0, y1) in ((30, 340, 360), (52, 360, 420), (30, 420, 440)): N.oct("nucleo_brilho", r, y0, y1, "nucleo")
    N.R("nucleo_brilho", 0, 112, 340, 0, 12, 12, "luz")                   # raio de luz descendo pelo tronco até a câmara
    N.bone("anel_1", (0, 390, 0), "root")
    for k in range(16):   # partículas de luz (sementes) girando
        a = 2 * math.pi * k / 16; r = 150 + (k % 3) * 12; y = 340 + (k % 5) * 22
        N.R("anel_1", r * math.sin(a), y, y + 6, r * math.cos(a), 6, 6, "luz", ang=45)
    return N.g, A

BATERIAS = {"verde": verde, "amarelo": amarelo, "vermelho": vermelho, "laranja": laranja, "azul": azul,
            "indigo": indigo, "violeta": violeta}  # Morte e Vida não usam bateria (as funções preto() e branco() ficam guardadas)
NOMES = dict(verde="Lanterna da Vontade", amarelo="Gaiola do Medo", vermelho="Coração Vulcânico da Fúria", laranja="Relicário da Avareza",
             azul="Farol da Esperança", indigo="Totem da Compaixão", violeta="Cristal do Amor", preto="Ossuário da Morte", branco="Árvore da Vida")

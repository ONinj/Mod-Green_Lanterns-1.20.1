# Os 18 trajes "segunda pele": 9 Tropas x 2 variações
import sys
sys.path.insert(0, "/home/claude/unif2"); sys.path.insert(0, "/home/claude/especies"); sys.path.insert(0, "/home/claude/lore3d")
from traje import Traje, hx, mix, lt, dk
from motivos import *
from species import EMBLEMAS

DESIGNS = {}
def design(tropa, var, nome, fabric, panel_pat):
    def deco(fn):
        DESIGNS[(tropa, var)] = dict(fn=fn, nome=nome, fabric=fabric, panel=panel_pat)
        return fn
    return deco

# ======================================================================== VERDE — Vontade
@design("verde", "a", "Patrulheiro", "carbono", "colmeia")
def verde_a():
    P = dict(base="#1C2226", g="#1FA34B", g2="#0E5A2A", tr="#8A949C", glow="#9CFFB8", esc="#0A0D0F")
    T = Traje("traje_verde_a", P)
    anatomia(T); costuras_padrao(T, "#050607")
    # zona verde em "V": dos ombros descendo até o emblema (o resto é tecido grafite)
    T.draw("body", "front", "poly", [(0, 0), (8, 0), (8, 1.8), (4.0, 8.8), (0, 1.8)], col=P["g"], grad=0.2, tag=1, rz=0.1)
    T.sym("body", "front", "line", [(0, 1.8), (4.0, 8.8)], 0.5, col=P["g2"], rz=0.1, tag=1)
    T.draw("body", "back", "poly", [(0, 0), (8, 0), (8, 1.6), (4, 4.4), (0, 1.6)], col=P["g"], grad=0.15, tag=1, rz=0.1)
    for f in ("right", "left"): T.draw("body", f, "poly", [(0, 0), (4, 0), (4, 1.4), (0, 2.6)], col=P["g"], grad=0.15, tag=1, rz=0.1)
    gola(T, P["g2"], alto=0.7, rz=0.22)
    emblema3d(T, EMBLEMAS["verde"], P["glow"], fundo=P["g2"], aro=P["tr"], b_c=3.3)
    linhas_painel(T, "#06080A")
    flancos(T, P["esc"], rz=0.1, b0=5.0, glow=None)
    for f in ("right", "left"): T.draw("body", f, "rect", 1.75, 5.6, 2.25, 10.2, col=P["glow"], glow=True)
    cinto(T, "#1A1E22", fivela=P["tr"], b0=10.4, b1=11.3)
    coluna(T, "#1A1E22", rz=0.1, vertebras=P["g2"])
    # braços: ombro verde, faixa verde externa até a luva, braçadeira escura com luzes
    ombro_fino(T, P["g"], borda=P["g2"], alto=2.6)
    for s in "rl":
        T.draw(s + "arm", "outer", "rect", 1.2, 2.4, 2.8, 10.2, col=P["g"], grad=0.12, rz=0.1, tag=1)
        T.draw(s + "arm", "outer", "rect", 1.75, 3.0, 2.25, 6.6, col=P["glow"], glow=True)
    bracadeira(T, "#1B2024", rz=0.22, b0=7.0, b1=10.0, borda=P["g2"], glow=P["glow"])
    luva(T, P["g"], rz=0.1, b0=10.0, junta=P["g2"])
    # pernas: faixa verde lateral que vira a canela, joelheira escura
    for s in "rl":
        T.draw(s + "leg", "outer", "poly", [(1.0, 0.0), (3.0, 0.0), (3.4, 7.0), (2.4, 12), (1.0, 12), (0.6, 7.0)], col=P["g"], grad=0.1, rz=0.1, tag=1)
        T.draw(s + "leg", "outer", "line", [(2.0, 0.6), (2.0, 6.6)], 0.5, col=P["glow"], glow=True)
        T.draw(s + "leg", "front", "rect", 0.0, 7.6, 4.0, 12.0, col=P["g"], grad=0.1, rz=0.1, tag=1)
    joelheira(T, "#1B2024", rz=0.22, borda=P["g2"])
    bota(T, P["g"], P["esc"], cano=P["g2"], biqueira=P["g2"])
    for s in "rl": T.draw(s + "boot", "outer", "rect", 1.0, 1.4, 3.0, 1.9, col=P["glow"], glow=True)
    mascara(T, P["g2"], lente="#EAFFF2")
    return T

@design("verde", "b", "Sentinela", "colmeia", "placa")
def verde_b():
    P = dict(base="#1A8A42", g="#24B552", g2="#0E5A2A", esc="#151A1D", tr="#9AA4AC", glow="#A8FFC4")
    T = Traje("traje_verde_b", P)
    anatomia(T, 1.2); costuras_padrao(T, "#0B3F1E", "#3FCB70")
    # laterais escuras (inverso do Patrulheiro): faixa grafite do sovaco ao tornozelo
    for f in ("right", "left"): T.draw("body", f, "rect", 0.6, 0, 3.4, 12, col=P["esc"], grad=0.1, pat=3, tag=1)
    for s in "rl":
        for p in (s + "arm", s + "leg"): T.draw(p, "inner", "rect", 0.4, 0, 3.6, 12, col=P["esc"], pat=3, tag=1)
    # couraça segmentada no peito (placas finas com frisos), emblema no meio
    for i, (b0, b1) in enumerate(((0.9, 2.6), (2.9, 4.6), (4.9, 6.4))):
        T.symrect("body", "front", 0.4, b0, 3.6, b1, col=lt(P["g"], 0.05 * (2 - i)), grad=0.15, rz=0.22, tag=2)
        T.symrect("body", "front", 0.4, b1 - 0.5, 3.6, b1, col=P["g2"], rz=0.22, tag=2)
    emblema3d(T, EMBLEMAS["verde"], P["glow"], fundo=P["esc"], aro=P["tr"], b_c=3.4)
    abdomen(T, P["esc"], rz=0.1, top=7.0, rows=2, alto=1.1)
    gola(T, P["esc"], alto=0.8, rz=0.22, glow=P["glow"])
    cinto(T, P["esc"], fivela=P["tr"], fivela_glow=False)
    # costas: espinha de energia e emissor dorsal
    T.draw("body", "back", "rect", 3.5, 0.8, 4.5, 10.2, col=P["esc"], rz=0.22, tag=6)
    T.draw("body", "back", "rect", 3.75, 1.2, 4.25, 9.8, col=P["glow"], glow=True, rz=0.22)
    omoplatas(T, P["g"], rz=0.1)
    T.mat("metal", P["tr"], "metal"); T.mat("luz", P["glow"], glow=True); T.mat("escuro", P["esc"], "metal")
    T.box("armorBody", (-1.5, 18.5, 2.5), (1.5, 22.5, 3.3), "escuro")
    T.box("armorBody", (-1.0, 19.0, 3.3), (1.0, 22.0, 3.6), "luz")
    # braços: ombreira em degraus, braçadeira com respiros luminosos
    ombro_fino(T, P["g"], borda=P["esc"], alto=2.8, rz=0.22)
    bracadeira(T, P["esc"], rz=0.22, b0=6.8, b1=10.2, borda=P["g2"], glow=P["glow"])
    luva(T, P["esc"], rz=0.1, junta=P["tr"])
    for s in "rl":
        T.box({"r": "armorRightArm", "l": "armorLeftArm"}[s], (-8.7, 15.5, -1.2) if s == "r" else (8.3, 15.5, -1.2),
              (-8.3, 19.0, 1.2) if s == "r" else (8.7, 19.0, 1.2), "escuro")
    # pernas: placas de coxa e canela, joelheira
    coxa(T, P["g"], rz=0.1, faces=("front", "outer"))
    joelheira(T, P["esc"], rz=0.22, borda=P["glow"])
    for s in "rl": T.draw(s + "leg", "front", "rect", 0.6, 8.0, 3.4, 11.6, col=P["g"], grad=0.1, rz=0.22, tag=2)
    bota(T, P["esc"], "#0A0C0E", cano=P["g"], biqueira=P["g2"])
    for s in "rl": T.draw(s + "boot", "front", "rect", 1.0, 1.4, 3.0, 1.9, col=P["glow"], glow=True)
    # meia-cogula verde com lentes brancas
    capuz(T, P["g"], borda=P["g2"], rosto=(0.8, 2.4, 7.2, 8.0))
    T.draw("head", "front", "poly", [(0.0, 2.4), (8.0, 2.4), (8.0, 4.9), (5.2, 5.2), (4.4, 4.6), (3.6, 4.6), (2.8, 5.2), (0.0, 4.9)], col=P["g"], alpha=255)
    T.draw("head", "front", "line", [(0, 4.95), (2.8, 5.2), (3.6, 4.6), (4.4, 4.6), (5.2, 5.2), (8, 4.95)], 0.4, col=P["g2"], alpha=255)
    lentes(T, "#F0FFF4", forma="angular")
    return T

# ======================================================================== AMARELO — Medo
@design("amarelo", "a", "Espectro", "escamas", "placa")
def amarelo_a():
    P = dict(base="#151515", y="#E8BE00", y2="#8A6C00", esc="#0A0A0A", tr="#3C3C3C", glow="#FFF070")
    T = Traje("traje_amarelo_a", P)
    anatomia(T, 1.3); costuras_padrao(T, "#000000")
    # quitina: costelas amarelas nos flancos e no peito, como uma carapaça de inseto
    for i in range(5):
        b = 4.6 + i * 1.15
        T.sym("body", "front", "poly", [(0.0, b), (2.4, b + 0.5), (2.6, b + 0.95), (0.0, b + 0.65)], col=P["y"], grad=0.2, rz=0.22, tag=2)
    for f in ("right", "left"):
        for i in range(5):
            b = 4.6 + i * 1.15
            T.draw("body", f, "rect", 0, b, 4, b + 0.65, col=P["y"], grad=0.2, rz=0.22, tag=2)
    T.sym("body", "front", "poly", [(0.4, 0.9), (3.6, 1.2), (3.6, 3.7), (2.2, 4.0), (0.4, 3.0)], col=P["y2"], grad=0.15, rz=0.1, tag=2)
    emblema3d(T, EMBLEMAS["amarelo"], P["glow"], fundo=P["esc"], aro=P["y2"], b_c=3.0)
    gola(T, P["y2"], alto=0.8, rz=0.22)
    # costas: espinha com placas
    for i in range(7):
        b = 0.8 + i * 1.3
        T.draw("body", "back", "poly", [(2.8, b), (5.2, b), (4.6, b + 1.0), (3.4, b + 1.0)], col=P["y"], grad=0.2, rz=0.22, tag=6)
    T.mat("quitina", P["y"], "metal"); T.mat("quitina2", P["y2"], "metal"); T.mat("garra", "#E8E0C0", "osso")
    for i in range(4):
        espinho(T, "armorBody", (0, 22.0 - i * 2.4, 2.3), "+z", 1.4 - i * 0.2, 0.7, "quitina", passos=3)
    # braços: ombros de quitina com espinhos, lâminas no antebraço, garras
    ombro_fino(T, P["y"], borda=P["y2"], alto=2.6, rz=0.22)
    for s, bn, xo, sg in (("r", "armorRightArm", -8.24, -1), ("l", "armorLeftArm", 8.24, 1)):
        espinho(T, bn, (xo - sg * 0.6, 24.4, -0.6), "+y", 1.6, 0.8, "quitina", passos=3)
        espinho(T, bn, (xo - sg * 0.6, 24.4, 0.9), "+y", 1.1, 0.6, "quitina", passos=3)
        T.box(bn, (xo, 13.0, -0.25), (xo + sg * 0.9, 18.0, 0.25), "quitina2", rot=(0, 0, sg * 0), pivot=(xo, 15, 0))
        T.box(bn, (xo + sg * 0.9, 13.8, -0.15), (xo + sg * 1.5, 17.0, 0.15), "quitina")
        for k in range(4):   # garras nas pontas dos dedos
            x = (-7.6 + k * 0.95) if s == "r" else (4.4 + k * 0.95)
            T.box(bn, (x, 11.0, -2.5), (x + 0.4, 12.0, -2.1), "garra", rot=(-20, 0, 0), pivot=(x, 12.0, -2.3))
    for s in "rl":
        for i in range(3): T.draw(s + "arm", "outer", "rect", 0.3, 4.0 + i * 1.0, 3.7, 4.6 + i * 1.0, col=P["y"], grad=0.2, rz=0.1, tag=2)
    bracadeira(T, P["y2"], rz=0.22, b0=7.2, b1=10.0, borda=P["y"])
    luva(T, P["esc"], rz=0.1)
    # pernas: listras de quitina
    for s in "rl":
        for i in range(4): T.draw(s + "leg", "outer", "poly", [(0, 1.0 + i * 1.4), (4, 0.4 + i * 1.4), (4, 1.0 + i * 1.4), (0, 1.6 + i * 1.4)], col=P["y"], grad=0.2, rz=0.1, tag=2)
    joelheira(T, P["y"], rz=0.22, borda=P["esc"])
    bota(T, P["esc"], "#050505", cano=P["y2"], biqueira=P["y"])
    # máscara angular com sobrancelha pontuda
    mascara(T, P["esc"], lente=None)
    T.draw("head", "front", "poly", [(0.0, 2.0), (3.4, 3.0), (4.0, 2.6), (4.6, 3.0), (8.0, 2.0), (8.0, 4.8), (5.0, 5.2), (4.0, 4.6), (3.0, 5.2), (0.0, 4.8)], col=P["esc"], alpha=255)
    T.draw("head", "front", "line", [(0.0, 2.0), (3.4, 3.0), (4.0, 2.6), (4.6, 3.0), (8.0, 2.0)], 0.4, col=P["y"], alpha=255, rz=0.1)
    lentes(T, P["glow"], forma="angular")
    return T

@design("amarelo", "b", "Arauto do Terror", "fibra", "osso")
def amarelo_b():
    P = dict(base="#D8AE00", y="#F2CC10", y2="#6A5200", esc="#121212", glow="#FFF6A0", osso="#E8E2CC")
    T = Traje("traje_amarelo_b", P)
    anatomia(T, 1.2); costuras_padrao(T, "#5A4600")
    # rachaduras negras em raio partindo do emblema (o medo se espalhando)
    rnd = __import__("random").Random(11)
    for part, faces in (("body", ("front", "back", "right", "left")), ("rarm", ("outer", "front", "back")), ("larm", ("outer", "front", "back")),
                        ("rleg", ("outer", "front", "back")), ("lleg", ("outer", "front", "back"))):
        for f in faces:
            w = 8 if (part == "body" and f in ("front", "back")) else 4
            for _ in range(3 if part == "body" else 2):
                a, b = rnd.uniform(0.5, w - 0.5), rnd.uniform(0, 3); pts = [(a, b)]
                for _ in range(6): a += rnd.uniform(-1.0, 1.0); b += rnd.uniform(1.0, 2.0); pts.append((max(0, min(w, a)), min(12, b)))
                T.draw(part, f, "line", pts, 0.5, col=P["esc"], tag=1)
                T.draw(part, f, "line", [(a_ + 0.25, b_) for a_, b_ in pts], 0.25, col=P["y2"])
    T.draw("body", "front", "ellipse", 1.3, 0.8, 6.7, 6.2, col=P["esc"], rz=0.1, tag=2)
    emblema3d(T, EMBLEMAS["amarelo"], P["glow"], aro=P["y2"], b_c=3.5)
    gola(T, P["esc"], alto=0.9, rz=0.22)
    cinto(T, P["esc"], fivela=P["osso"])
    T.mat("preto", P["esc"], "metal"); T.mat("osso", P["osso"], "osso"); T.mat("amarelo", P["y"], "metal")
    # gola alta em leque atrás da cabeça
    T.box("armorBody", (-3.6, 22.6, 2.2), (3.6, 24.4, 4.7), "preto")
    for i, (x, h) in enumerate(((-3.2, 3.0), (-2.0, 4.2), (-0.7, 5.0), (0.7, 5.0), (2.0, 4.2), (3.2, 3.0))):
        T.box("armorBody", (x - 0.55, 24.0, 4.5), (x + 0.55, 24.0 + h, 5.0), "preto", rot=(-14, 0, x * 6), pivot=(x, 24.0, 4.7))
        T.box("armorBody", (x - 0.25, 24.0 + h - 0.4, 4.45), (x + 0.25, 24.0 + h + 0.3, 4.55), "amarelo", rot=(-14, 0, x * 6), pivot=(x, 24.0, 4.7))
    # espinhos na coluna
    for i in range(5): espinho(T, "armorBody", (0, 21.5 - i * 2.2, 2.3), "+z", 1.8 - i * 0.2, 0.8, "osso", passos=3)
    # ombros: três chifres de osso
    for s, bn, xo, sg in (("r", "armorRightArm", -8.0, -1), ("l", "armorLeftArm", 8.0, 1)):
        for k, (zz, L) in enumerate(((-1.2, 1.4), (0.0, 2.2), (1.2, 1.4))):
            espinho(T, bn, (xo + sg * 0.2, 24.3, zz), "+y", L, 0.7, "osso", passos=3, rot=(0, 0, sg * -25))
        T.box(bn, (xo - (0.4 if s == "l" else 0), 13.2, -2.5), (xo + (0.4 if s == "r" else 0) - (0 if s == "r" else 0) + (-0.0), 13.6, 2.5), "preto") if False else None
    ombro_fino(T, P["esc"], borda=P["y2"], alto=2.4, rz=0.22)
    bracadeira(T, P["esc"], rz=0.22, b0=7.4, b1=10.0, borda=P["osso"])
    luva(T, P["esc"], rz=0.1, junta=P["osso"])
    joelheira(T, P["esc"], rz=0.22, borda=P["osso"])
    bota(T, P["esc"], "#050505", cano=P["y2"], biqueira=P["osso"])
    # máscara de caveira preta só na parte de cima do rosto
    mascara(T, P["esc"], lente=None, b0=1.8, b1=5.4)
    lentes(T, P["glow"], b0=3.3, b1=4.6, forma="angular")
    T.draw("head", "front", "rect", 3.6, 4.2, 4.4, 5.0, col="#000000", alpha=255)
    return T

# ======================================================================== VERMELHO — Fúria
def veias(T, rnd, col, n_body=4, n_limb=2, w=0.5, rz=0.1):
    for part, faces in (("body", ("front", "back", "right", "left")), ("rarm", MEMBRO), ("larm", MEMBRO), ("rleg", MEMBRO), ("lleg", MEMBRO)):
        for f in faces:
            wd = 8 if (part == "body" and f in ("front", "back")) else 4
            for _ in range(n_body if part == "body" else n_limb):
                a, b = rnd.uniform(0.5, wd - 0.5), rnd.uniform(0, 12); pts = [(a, b)]
                for _ in range(rnd.randint(3, 6)):
                    a += rnd.uniform(-1.2, 1.2); b += rnd.choice((-1, 1)) * rnd.uniform(0.6, 1.6); pts.append((max(0.2, min(wd - 0.2, a)), max(0.2, min(11.8, b))))
                T.draw(part, f, "line", pts, w, col=col, glow=True, rz=rz, rz_mode="max")

@design("vermelho", "a", "Berserker", "fibra", "couro")
def vermelho_a():
    P = dict(base="#5A0F0C", r="#A3170F", r2="#3A0806", esc="#1A0806", lava="#FF6A20", rocha="#3A2E2A", glow="#FFB060")
    T = Traje("traje_vermelho_a", P)
    rnd = __import__("random").Random(5)
    anatomia(T, 1.4); costuras_padrao(T, "#200403")
    # músculo exposto: fibras + veias de magma em relevo
    peitoral(T, P["r"], rz=0.1, borda=P["r2"], estilo="curvo")
    abdomen(T, P["r"], rz=0.1, linhas=P["r2"])
    veias(T, rnd, P["lava"])
    emblema3d(T, EMBLEMAS["vermelho"], P["glow"], fundo=P["esc"], aro=P["rocha"], b_c=3.2)
    gola(T, P["esc"], alto=0.7)
    cinto(T, P["esc"], fivela=P["rocha"])
    # antebraço direito enfaixado; ombro esquerdo com placas de rocha
    for f in MEMBRO: T.draw("rarm", f, "rect", 0, 6.6, 4, 10.4, col="#C8B8A0", pat=7, rz=0.1, tag=3)
    for f in MEMBRO: T.draw("rarm", f, "rect", 0, 9.0, 4, 9.5, col="#8A1810", rz=0.1)     # sangue na atadura
    luva(T, P["esc"], rz=0.1, junta=P["rocha"])
    T.mat("rocha", P["rocha"], "metal"); T.mat("rocha2", "#4A3A34", "metal"); T.mat("lava", P["lava"], glow=True)
    for (x, y, z, w, h, d, r) in ((7.4, 23.4, -1.3, 2.4, 1.8, 2.0, (8, 10, -14)), (7.6, 23.6, 0.9, 2.2, 1.9, 1.9, (-6, -8, -10)),
                                  (7.8, 21.4, -0.2, 1.8, 1.6, 3.2, (0, 0, -6)), (6.0, 24.2, -0.3, 2.8, 1.4, 2.6, (4, 6, -4)),
                                  (8.1, 19.8, 0.4, 1.3, 1.4, 2.2, (0, 6, -4)), (5.0, 24.2, -1.8, 1.6, 0.9, 1.4, (0, 15, 0))):
        T.box("armorLeftArm", (x, y, z - d / 2), (x + w, y + h, z + d / 2), "rocha" if y < 24 else "rocha2", rot=r, pivot=(x + w / 2, y + h / 2, z))
    T.box("armorLeftArm", (9.0, 22.0, -1.2), (9.9, 22.3, 1.6), "lava", rot=(0, 0, -6), pivot=(9.4, 22.1, 0))
    T.box("armorLeftArm", (7.0, 25.4, -1.0), (8.6, 25.7, 0.4), "lava", rot=(4, 6, -4), pivot=(7.8, 25.5, -0.3))
    for (y, z) in ((8.5 + 6, -0.5), (16.0, 0.8)):
        T.box("armorRightArm", (-9.0, y - 1.4, z - 0.8), (-8.0, y, z + 0.8), "rocha", rot=(0, 0, 6), pivot=(-8.5, y, z))
    ombro_fino(T, P["r2"], borda=P["esc"], alto=2.4, rz=0.1, lado="r")
    # pernas
    coxa(T, P["r"], rz=0.1, faces=("front",))
    joelheira(T, P["rocha"], rz=0.22, borda=P["lava"])
    bota(T, P["esc"], "#0A0302", cano=P["rocha"], biqueira=P["rocha"])
    for s in "rl":
        for f in ("front", "outer"): T.draw(s + "boot", f, "line", [(0.6, 0.8), (1.6, 2.0), (1.2, 3.4), (2.6, 4.0)], 0.5, col=P["lava"], glow=True)
    # máscara com sobrancelha franzida
    mascara(T, P["r2"], lente=None, b0=2.4)
    T.draw("head", "front", "poly", [(0.4, 2.4), (3.6, 3.4), (3.6, 3.9), (0.4, 3.0)], col=P["esc"], alpha=255, rz=0.1)
    T.draw("head", "front", "poly", [(7.6, 2.4), (4.4, 3.4), (4.4, 3.9), (7.6, 3.0)], col=P["esc"], alpha=255, rz=0.1)
    for a0 in (1.0, 5.0): T.draw("head", "front", "rect", a0, 3.9, a0 + 2.0, 4.6, col=P["lava"], glow=True, alpha=255)
    return T

@design("vermelho", "b", "Sangue Fervente", "couro", "placa")
def vermelho_b():
    P = dict(base="#1C0B0A", r="#C01A12", r2="#6A0C08", esc="#0E0505", tr="#5A4A48", glow="#FF4A2A")
    T = Traje("traje_vermelho_b", P)
    anatomia(T, 1.2); costuras_padrao(T, "#000000", "#3A1A18")
    # lâminas vermelhas tribais (formas de chama apontando para cima)
    def chama(part, face, a, b, w, h):
        T.draw(part, face, "poly", [(a, b + h), (a + w * 0.15, b + h * 0.4), (a + w * 0.35, b + h * 0.65), (a + w * 0.5, b), (a + w * 0.65, b + h * 0.65),
                                     (a + w * 0.85, b + h * 0.4), (a + w, b + h)], col=P["r"], grad=0.25, rz=0.1, tag=2)
    chama("body", "front", 0.2, 5.5, 7.6, 5.0)
    T.sym("body", "front", "poly", [(0.3, 0.9), (3.6, 1.3), (3.6, 4.4), (0.3, 3.6)], col=P["r2"], grad=0.2, rz=0.22, tag=2)
    emblema3d(T, EMBLEMAS["vermelho"], P["glow"], fundo=P["esc"], aro=P["tr"], b_c=3.0)
    # "gotas" de energia escorrendo do emblema
    for a, L in ((2.6, 3.2), (3.4, 4.6), (4.6, 4.0), (5.4, 2.6)):
        T.draw("body", "front", "line", [(a, 5.6), (a, 5.6 + L)], 0.5, col=P["glow"], glow=True)
        T.draw("body", "front", "rect", a - 0.25, 5.6 + L - 0.1, a + 0.25, 5.6 + L + 0.4, col=P["glow"], glow=True)
    chama("body", "back", 0.2, 3.0, 7.6, 7.5)
    for s in "rl":
        for f in ("outer", "front", "back"): chama(s + "leg", f, 0.0, 3.5, 4.0, 6.5)
        chama(s + "arm", "outer", 0.0, 3.5, 4.0, 6.5)
    gola(T, P["r2"], alto=0.8, rz=0.22)
    cinto(T, P["esc"], fivela=P["r"], fivela_glow=False)
    ombro_fino(T, P["r2"], borda=P["r"], alto=2.6, rz=0.22)
    bracadeira(T, P["esc"], rz=0.22, b0=7.0, b1=10.0, borda=P["r"])
    luva(T, P["esc"], rz=0.1)
    T.mat("metal", P["tr"], "metal"); T.mat("chifre", "#2A1414", "osso")
    for s, bn, xo, sg in (("r", "armorRightArm", -8.0, -1), ("l", "armorLeftArm", 4.0, 1)):
        for k in range(4):   # cravos nos nós dos dedos
            x = (-7.6 + k * 0.95) if s == "r" else (4.4 + k * 0.95)
            espinho(T, bn, (x + 0.25, 11.2, -2.3), "-z", 0.7, 0.45, "metal", passos=2)
    for s in "rl": espinho(T, "armorRightArm" if s == "r" else "armorLeftArm", (-8.3 if s == "r" else 8.3, 22.6, 0), "-x" if s == "r" else "+x", 1.2, 0.8, "metal", passos=3)
    joelheira(T, P["r2"], rz=0.22, borda=P["r"])
    bota(T, P["esc"], "#050202", cano=P["r2"], biqueira=P["r2"])
    # máscara com chifres curtos
    mascara(T, P["r2"], lente=None)
    lentes(T, P["glow"], forma="angular")
    for sg in (-1, 1):
        espinho(T, "armorHead", (sg * 3.0, 32.3, -2.6), "+y", 1.8, 0.8, "chifre", passos=4, rot=(-25, 0, sg * -20))
    T.draw("head", "top", "rect", 0.8, 0.6, 2.2, 2.0, col=P["r2"], alpha=255) if False else None
    return T

# ======================================================================== LARANJA — Avareza
def filigrana(T, part, face, a, b, w, col, rz=0.1):
    """arabesco dourado (voluta dupla)"""
    T.draw(part, face, "line", [(a, b), (a + w * 0.3, b - 0.6), (a + w * 0.5, b), (a + w * 0.3, b + 0.5), (a + w * 0.15, b + 0.1)], 0.25, col=col, rz=rz, rz_mode="max")
    T.draw(part, face, "line", [(a + w, b), (a + w * 0.7, b - 0.6), (a + w * 0.5, b), (a + w * 0.7, b + 0.5), (a + w * 0.85, b + 0.1)], 0.25, col=col, rz=rz, rz_mode="max")

@design("laranja", "a", "Tesoureiro", "seda", "placa")
def laranja_a():
    P = dict(base="#B85A00", o="#E07800", o2="#6A3000", ouro="#E2B33A", ouro2="#8A6418", esc="#2A1606", glow="#FFD27A", gema="#FF8A1A")
    T = Traje("traje_laranja_a", P)
    anatomia(T, 1.1); costuras_padrao(T, "#5A2A00", P["ouro2"])
    # colete escuro com bordas douradas sobre o macacão laranja
    T.draw("body", "front", "poly", [(0.6, 0), (2.8, 0), (4, 5.2), (5.2, 0), (7.4, 0), (8, 3), (8, 12), (0, 12), (0, 3)], col=P["esc"], grad=0.15, rz=0.1, tag=1, pat=10)
    T.sym("body", "front", "line", [(2.8, 0), (4.0, 5.2)], 0.5, col=P["ouro"], rz=0.22, rz_mode="max")
    T.draw("body", "back", "all", col=P["esc"], grad=0.15, rz=0.1, tag=1, pat=10)
    for f in ("right", "left"): T.draw("body", f, "all", col=P["esc"], grad=0.15, rz=0.1, tag=1, pat=10)
    for i in range(3):   # botões de ouro
        T.sym("body", "front", "rect", 2.6, 6.0 + i * 1.3, 3.1, 6.5 + i * 1.3, col=P["ouro"], rz=0.38)
    for b in (7.0, 8.8): filigrana(T, "body", "front", 4.6, b, 2.6, P["ouro"]); filigrana(T, "body", "front", 0.8, b, 2.6, P["ouro"])
    filigrana(T, "body", "back", 1.0, 3.0, 6.0, P["ouro"]); filigrana(T, "body", "back", 2.0, 6.0, 4.0, P["ouro"])
    emblema3d(T, EMBLEMAS["laranja"], P["glow"], fundo=P["o2"], aro=P["ouro"], a_c=4.0, b_c=2.4, rz=0.38)
    cinto(T, P["o2"], fivela=P["ouro"], fivela_glow=False)
    T.mat("ouro", P["ouro"], "metal"); T.mat("ouro2", P["ouro2"], "metal"); T.mat("gema", P["gema"], "cristal", glow=True)
    # moedas penduradas no cinto
    for i, x in enumerate((-3.4, -2.4, -1.4, 1.4, 2.4, 3.4)):
        T.box("armorBody", (x - 0.4, 11.0 - (i % 2) * 0.3, -2.75), (x + 0.4, 11.8 - (i % 2) * 0.3, -2.55), "ouro")
    # gola alta dourada
    T.box("armorBody", (-3.8, 24.0, -2.4), (3.8, 25.4, -2.0), "ouro2"); T.box("armorBody", (-3.8, 24.0, 2.0), (3.8, 25.6, 2.4), "ouro2")
    for sg in (-1, 1): T.box("armorBody", (sg * 3.8 - (0.4 if sg > 0 else 0), 24.0, -2.4), (sg * 3.8 + (0 if sg > 0 else 0.4), 25.5, 2.4), "ouro2")
    T.box("armorBody", (-0.6, 24.6, -2.6), (0.6, 25.6, -2.3), "gema")
    # braços: ombreira com franja dourada, braçadeira cinzelada
    ombro_fino(T, P["o2"], borda=P["ouro"], alto=2.6, rz=0.22)
    for s, bn, xo, sg in (("r", "armorRightArm", -8.25, -1), ("l", "armorLeftArm", 8.25, 1)):
        for k in range(6):
            z = -1.9 + k * 0.76
            T.box(bn, (xo + sg * 0.0 - (0.2 if s == "r" else 0), 20.6 - (k % 2) * 0.3, z), (xo + (0.2 if s == "l" else 0), 21.8, z + 0.3), "ouro")
    bracadeira(T, P["ouro2"], rz=0.22, b0=7.0, b1=10.0, borda=P["ouro"])
    for s in "rl": filigrana(T, s + "arm", "outer", 0.6, 8.5, 2.8, P["ouro"], rz=0.38)
    luva(T, P["esc"], rz=0.1, junta=P["ouro"])
    for s in "rl": T.draw(s + "arm", "front", "rect", 1.4, 10.6, 2.6, 11.4, col=P["gema"], glow=True, rz=0.22)   # anel na mão
    # pernas: listra dourada lateral
    for s in "rl":
        T.draw(s + "leg", "outer", "rect", 1.5, 0, 2.5, 12, col=P["ouro2"], rz=0.1, tag=2)
        T.draw(s + "leg", "outer", "rect", 1.75, 0, 2.25, 12, col=P["ouro"], rz=0.1, tag=2)
    joelheira(T, P["ouro2"], rz=0.22, borda=P["ouro"])
    bota(T, P["esc"], "#140A02", cano=P["ouro"], biqueira=P["ouro2"])
    # máscara com monóculo
    mascara(T, P["o2"], lente=None)
    T.draw("head", "front", "rect", 1.0, 3.5, 3.0, 4.5, col="#FFE6A0", glow=True, alpha=255)
    T.draw("head", "front", "ellipse", 4.6, 2.9, 7.4, 5.1, col=P["ouro"], alpha=255, rz=0.22)
    T.draw("head", "front", "ellipse", 5.0, 3.3, 7.0, 4.7, col=P["glow"], glow=True, alpha=255, rz=0.22)
    T.box("armorHead", (3.0, 26.6, -4.5), (3.2, 28.6, -4.4), "ouro")
    return T

@design("laranja", "b", "Saqueador", "couro", "escamas")
def laranja_b():
    P = dict(base="#5A3418", o="#E07800", o2="#8A4400", ouro="#D4A82A", esc="#2A1808", couro="#6A4022", glow="#FFC060")
    T = Traje("traje_laranja_b", P)
    anatomia(T, 1.0); costuras_padrao(T, "#2A1408", "#8A6040")
    # cota de moedas (escamas douradas) no tronco
    T.draw("body", "front", "rect", 0.4, 1.0, 7.6, 10.0, col=P["o"], pat=4, grad=0.2, rz=0.1, tag=2)
    for f in ("right", "left"): T.draw("body", f, "rect", 0, 1.0, 4, 10.0, col=P["o"], pat=4, grad=0.2, rz=0.1, tag=2)
    T.draw("body", "back", "rect", 0.4, 1.0, 7.6, 10.0, col=P["o2"], pat=4, grad=0.2, rz=0.1, tag=2)
    # bandoleira diagonal com bolsinhas
    T.draw("body", "front", "line", [(0.2, 0.4), (7.6, 9.8)], 1.0, col=P["couro"], rz=0.22, rz_mode="max", pat=10)
    T.draw("body", "back", "line", [(7.8, 0.4), (0.4, 9.8)], 1.0, col=P["couro"], rz=0.22, rz_mode="max", pat=10)
    emblema3d(T, EMBLEMAS["laranja"], P["glow"], fundo=P["esc"], aro=P["ouro"], a_c=5.4, b_c=3.2, rz=0.38)
    T.mat("couro", P["couro"], "solid"); T.mat("ouro", P["ouro"], "metal"); T.mat("saco", "#8A6A40", "solid")
    for (x, y) in ((-2.3, 21.0), (-0.4, 18.6), (1.5, 16.2)):
        T.box("armorBody", (x - 0.6, y - 0.8, -2.85), (x + 0.6, y + 0.4, -2.45), "couro")
        T.box("armorBody", (x - 0.6, y + 0.1, -2.9), (x + 0.6, y + 0.4, -2.45), "ouro")
    cinto(T, P["couro"], fivela=P["ouro"])
    # saco de pilhagem nas costas
    T.box("armorBody", (-2.4, 14.0, 2.3), (2.0, 18.2, 4.0), "saco", rot=(0, 0, 8), pivot=(0, 16, 3))
    T.box("armorBody", (-1.0, 18.0, 2.6), (0.6, 19.0, 3.6), "saco", rot=(0, 0, 8), pivot=(0, 16, 3))
    T.box("armorBody", (-1.1, 17.8, 2.5), (0.7, 18.2, 3.7), "couro", rot=(0, 0, 8), pivot=(0, 16, 3))
    ombro_fino(T, P["couro"], borda=P["ouro"], alto=2.4, rz=0.22)
    for s in "rl":
        for f in MEMBRO: T.draw(s + "arm", f, "rect", 0, 6.6, 4, 10.2, col=P["couro"], pat=10, rz=0.1, tag=3)
        for b in (7.3, 9.2): T.draw(s + "arm", "outer", "rect", 0, b, 4, b + 0.5, col=P["ouro"], rz=0.22)
    luva(T, P["esc"], rz=0.1)
    for s in "rl":
        T.draw(s + "leg", "outer", "rect", 0.8, 1.4, 3.2, 4.4, col=P["couro"], rz=0.22, tag=3)
        T.draw(s + "leg", "outer", "rect", 0.8, 1.4, 3.2, 1.9, col=P["ouro"], rz=0.22)
    joelheira(T, P["couro"], rz=0.22, borda=P["ouro"])
    bota(T, P["couro"], "#1A0E04", cano=P["ouro"], biqueira=P["esc"])
    # capuz laranja com óculos de visão
    capuz(T, P["o2"], borda=P["couro"], rosto=(1.0, 2.0, 7.0, 8.0), pat=10)
    T.draw("head", "front", "rect", 0.6, 3.2, 7.4, 4.8, col=P["couro"], alpha=255, rz=0.1)
    for a0 in (1.0, 5.0):
        T.draw("head", "front", "ellipse", a0 - 0.2, 3.0, a0 + 2.2, 5.0, col=P["ouro"], alpha=255, rz=0.22)
        T.draw("head", "front", "ellipse", a0 + 0.2, 3.4, a0 + 1.8, 4.6, col=P["glow"], glow=True, alpha=255, rz=0.22)
    return T

# ======================================================================== AZUL — Esperança
@design("azul", "a", "Aurora", "seda", "liso")
def azul_a():
    P = dict(base="#E6EEF8", b="#1E7BFF", b2="#0A3A8A", b3="#7AB8FF", pr="#C8D4E4", glow="#BDEBFF", ouro="#F2D88A")
    T = Traje("traje_azul_a", P)
    anatomia(T, 1.0); costuras_padrao(T, "#B0BCCC")
    # raios azuis saindo do emblema (amanhecer), em leque
    import math as _m
    cx, cy = 4.0, 3.3
    for k in range(9):
        ang = _m.radians(200 + k * 17.5)
        for rr, wd, col in ((9.0, 1.1, P["b"]), (9.0, 0.5, P["b3"])):
            T.draw("body", "front", "line", [(cx, cy), (cx + rr * _m.cos(ang) * -1 * 0 + cx * 0 + rr * _m.cos(ang), cy - rr * _m.sin(ang))] if False else
                   [(cx, cy), (cx + rr * _m.cos(ang), cy - rr * _m.sin(ang))], wd, col=col, rz=0.1, rz_mode="max", tag=2)
    T.draw("body", "front", "rect", 0, 8.2, 8, 12, col=P["b2"], grad=0.2, rz=0.1, tag=2)
    T.draw("body", "front", "poly", [(0, 8.2), (4, 6.6), (8, 8.2)], col=P["b2"], rz=0.1, tag=2)
    emblema3d(T, EMBLEMAS["azul"], P["glow"], fundo=P["b2"], aro=P["ouro"], b_c=cy)
    gola(T, P["b"], alto=0.6, rz=0.1)
    cinto(T, P["b2"], fivela=P["ouro"])
    # costas: asas estilizadas em relevo (penas em degrau)
    for sgn in (1, -1):
        for k in range(5):
            b0 = 1.2 + k * 1.2; L = 3.4 - k * 0.45
            a0 = 3.7 if sgn > 0 else 4.3
            pts = [(a0, b0), (a0 + sgn * L, b0 - 0.4), (a0 + sgn * L * 0.8, b0 + 0.8), (a0, b0 + 0.9)]
            T.draw("body", "back", "poly", pts, col=P["b"] if k % 2 == 0 else P["b3"], grad=0.15, rz=0.22 - (0.12 if k > 2 else 0), tag=2, rz_mode="max")
    T.draw("body", "back", "rect", 0, 8.2, 8, 12, col=P["b2"], grad=0.2, rz=0.1, tag=2)
    # braços e pernas: azul que "nasce" de baixo para cima
    for s in "rl":
        for f in MEMBRO:
            T.draw(s + "arm", f, "poly", [(0, 12), (0, 7.5), (2, 6.4), (4, 7.5), (4, 12)], col=P["b"], grad=0.15, rz=0.1, tag=2)
            T.draw(s + "leg", f, "poly", [(0, 12), (0, 5.5), (2, 4.2), (4, 5.5), (4, 12)], col=P["b"], grad=0.15, rz=0.1, tag=2)
        T.draw(s + "arm", "outer", "line", [(2, 6.6), (2, 1.0)], 0.5, col=P["glow"], glow=True)
        T.draw(s + "leg", "outer", "line", [(2, 4.4), (2, 0.6)], 0.5, col=P["glow"], glow=True)
        for b in (2.0, 3.2): T.draw(s + "arm", "outer", "rect", 1.5, b, 2.5, b + 0.5, col=P["glow"], glow=True)   # estrelinhas
    ombro_fino(T, P["pr"], borda=P["b"], alto=2.2, rz=0.1)
    luva(T, P["b2"], rz=0.1, junta=P["ouro"])
    joelheira(T, P["pr"], rz=0.22, borda=P["glow"])
    bota(T, P["b"], P["b2"], cano=P["pr"], biqueira=P["b2"])
    T.mat("asa", P["pr"], "solid"); T.mat("luz", P["glow"], glow=True)
    for sg, x0 in ((-1, -1.0), (1, 1.0)):   # pequenas asas nas botas
        for s, bn, xo in (("r", "armorRightBoot", -4.3), ("l", "armorLeftBoot", 4.3)):
            pass
    for s, bn, xo, sg in (("r", "armorRightBoot", -4.26, -1), ("l", "armorLeftBoot", 4.26, 1)):
        for k, (L, y) in enumerate(((2.0, 3.2), (1.5, 2.5), (1.0, 1.8))):
            T.box(bn, (xo + (0 if sg < 0 else 0), y, 0.6), (xo + sg * 0.15, y + 0.5, 0.6 + L), "asa", rot=(-25, 0, 0), pivot=(xo, y, 0.6))
    mascara(T, P["b"], lente=P["glow"])
    T.box("armorHead", (-0.4, 30.0, -4.55), (0.4, 30.8, -4.45), "luz")
    return T

@design("azul", "b", "Estrela-Guia", "malha", "seda")
def azul_b():
    P = dict(base="#0C2458", b="#1E7BFF", b2="#08163A", pr="#C8D4E4", glow="#CFF0FF", ouro="#F2D88A")
    T = Traje("traje_azul_b", P)
    anatomia(T, 1.2); costuras_padrao(T, "#061230", "#2A4A8A")
    rnd = __import__("random").Random(21)
    # constelações: estrelas brilhantes ligadas por linhas finas
    for part, faces in (("body", ("front", "back", "right", "left")), ("rarm", MEMBRO), ("larm", MEMBRO), ("rleg", MEMBRO), ("lleg", MEMBRO)):
        for f in faces:
            wd = 8 if (part == "body" and f in ("front", "back")) else 4
            pts = [(rnd.uniform(0.6, wd - 0.6), rnd.uniform(0.8, 11.2)) for _ in range(4 if part == "body" else 3)]
            pts.sort(key=lambda p: p[1])
            T.draw(part, f, "line", pts, 0.25, col="#4A7AC8")
            for (a, b) in pts:
                T.draw(part, f, "rect", a - 0.25, b - 0.25, a + 0.25, b + 0.25, col=P["glow"], glow=True)
                T.draw(part, f, "rect", a - 0.5, b - 0.12, a + 0.5, b + 0.12, col="#7AB8FF", glow=True)
            for _ in range(10 if part == "body" else 5):
                a, b = rnd.uniform(0.3, wd - 0.3), rnd.uniform(0.3, 11.7)
                T.draw(part, f, "rect", a, b, a + 0.25, b + 0.25, col="#9AB8E8")
    # faixa prateada em "V" e emblema
    T.sym("body", "front", "line", [(0.4, 0.0), (4.0, 7.6)], 0.75, col=P["pr"], rz=0.1, rz_mode="max")
    emblema3d(T, EMBLEMAS["azul"], P["glow"], fundo=P["b2"], aro=P["pr"], b_c=3.4)
    gola(T, P["pr"], alto=0.6, rz=0.22)
    cinto(T, P["pr"], fivela=P["glow"], fivela_glow=True)
    ombro_fino(T, P["pr"], borda=P["b"], alto=2.6, rz=0.22)
    bracadeira(T, P["pr"], rz=0.22, b0=7.6, b1=10.0, borda=P["b"])
    luva(T, P["b2"], rz=0.1)
    joelheira(T, P["pr"], rz=0.22, borda=P["glow"])
    bota(T, P["b2"], "#050C20", cano=P["pr"], biqueira=P["pr"])
    T.mat("prata", P["pr"], "metal"); T.mat("luz", P["glow"], glow=True)
    # barbatanas de luz nas costas (como uma cauda de cometa)
    for sg in (-1, 1):
        for k in range(3):
            T.box("armorBody", (sg * (1.2 + k * 0.9) - 0.15, 18.0 - k * 1.2, 2.3), (sg * (1.2 + k * 0.9) + 0.15, 22.5 - k * 1.2, 3.0 + (2 - k) * 0.5), "prata",
                  rot=(-30, 0, sg * 12), pivot=(sg * (1.2 + k * 0.9), 20, 2.4))
    # tiara com estrela de quatro pontas
    cabeca_vazia(T)
    T.draw("head", "front", "rect", 0, 1.8, 8, 2.4, col=P["pr"], alpha=255, rz=0.1)
    for f in ("right", "left", "back"): T.draw("head", f, "rect", 0, 1.8, 8 if f == "back" else 8, 2.4, col=P["pr"], alpha=255)
    T.box("armorHead", (-0.3, 29.9, -4.75), (0.3, 31.9, -4.45), "luz"); T.box("armorHead", (-1.0, 30.6, -4.75), (1.0, 31.2, -4.45), "luz")
    T.draw("head", "front", "rect", 1.0, 3.5, 3.0, 4.5, col=P["glow"], glow=True, alpha=255) if False else None
    return T

# ======================================================================== ÍNDIGO — Compaixão
def tatuagem(T, part, face, a, b, col, k=1.0):
    """marca tribal: losango com raios"""
    T.draw(part, face, "poly", [(a, b - 0.9 * k), (a + 0.6 * k, b), (a, b + 0.9 * k), (a - 0.6 * k, b)], col=col, glow=True)
    T.draw(part, face, "poly", [(a, b - 0.45 * k), (a + 0.3 * k, b), (a, b + 0.45 * k), (a - 0.3 * k, b)], col="#1A0E40", glow=False)
    for dx in (-1.1, 1.1): T.draw(part, face, "rect", a + dx * k - 0.25, b - 0.12, a + dx * k + 0.25, b + 0.13, col=col, glow=True)

@design("indigo", "a", "Peregrino", "atadura", "atadura")
def indigo_a():
    P = dict(base="#2A1A6A", i="#4B2BBF", i2="#1C0E52", pano="#D9CCB8", pano2="#8A7A68", glow="#CFC4FF", couro="#3A2C24")
    T = Traje("traje_indigo_a", P)
    anatomia(T, 1.1); costuras_padrao(T, "#140A38")
    # ataduras de pano cru envolvendo antebraços e canelas, faixa no peito
    for s in "rl":
        for f in MEMBRO:
            T.draw(s + "arm", f, "rect", 0, 6.4, 4, 10.4, col=P["pano"], pat=7, rz=0.1, tag=3)
            T.draw(s + "leg", f, "rect", 0, 6.6, 4, 12, col=P["pano"], pat=7, rz=0.1, tag=3)
    T.draw("body", "front", "line", [(0.0, 1.0), (8.0, 9.0)], 1.25, col=P["pano"], rz=0.1, rz_mode="max", pat=7)
    T.draw("body", "back", "line", [(8.0, 1.0), (0.0, 9.0)], 1.25, col=P["pano"], rz=0.1, rz_mode="max", pat=7)
    # tatuagens luminosas
    for (part, face, a, b) in (("body", "front", 6.0, 2.4), ("body", "front", 2.0, 8.0), ("body", "back", 4.0, 3.0), ("body", "back", 4.0, 7.2),
                               ("rarm", "outer", 2.0, 4.4), ("larm", "outer", 2.0, 4.4), ("rleg", "outer", 2.0, 3.0), ("lleg", "outer", 2.0, 3.0)):
        tatuagem(T, part, face, a, b, P["glow"])
    emblema3d(T, EMBLEMAS["indigo"], P["glow"], fundo=P["i2"], aro=P["pano2"], a_c=3.0, b_c=3.2)
    gola(T, P["i2"], alto=0.6, rz=0.1)
    cinto(T, P["couro"], fivela=P["pano2"])
    T.mat("pano", P["pano"], "solid"); T.mat("pano2", P["pano2"], "solid"); T.mat("indigo", P["i"], "solid"); T.mat("conta", P["glow"], glow=True)
    # tabardo de pano (frente e trás) pendurado do cinto, pontas soltas
    tiras(T, "armorBody", [(-2.0, 11.4, -2.75, 4.6, 6), (0.0, 11.4, -2.75, 5.2, 4), (2.0, 11.4, -2.75, 4.4, 7)], "indigo", larg=1.9, esp=0.15)
    tiras(T, "armorBody", [(-1.0, 11.4, 2.75, 4.8, -5), (1.0, 11.4, 2.75, 4.2, -6)], "indigo", larg=1.9, esp=0.15)
    tiras(T, "armorBody", [(-1.6, 7.0, -2.9, 0.6, 6), (0.2, 6.3, -2.9, 0.5, 4)], "pano2", larg=1.2, esp=0.12) if False else None
    # contas no pescoço
    for i, x in enumerate((-2.5, -1.7, -0.85, 0.0, 0.85, 1.7, 2.5)):
        y = 23.0 - (2.5 - abs(x)) * 0.35
        T.box("armorBody", (x - 0.25, y - 0.25, -2.75), (x + 0.25, y + 0.25, -2.45), "conta" if i == 3 else "pano2")
    ombro_fino(T, P["i2"], borda=P["pano2"], alto=2.2, rz=0.1)
    luva(T, P["i2"], rz=0.1)
    # pés enrolados em pano (sem bota rígida)
    for s in "rl":
        p = s + "boot"
        for f in MEMBRO: T.draw(p, f, "all", col=P["pano"], pat=7, tag=3)
        for f in MEMBRO: T.draw(p, f, "rect", 0, 4.3, 4, 5, col=P["couro"], rz=0.1)
        T.draw(p, "bottom", "all", col=P["couro"]); T.draw(p, "top", "all", alpha=0)
    # faixa na testa com pontas
    cabeca_vazia(T)
    for f in ("front", "right", "left", "back"): T.draw("head", f, "rect", 0, 2.0, 8, 3.0, col=P["i"], alpha=255, rz=0.1)
    T.draw("head", "front", "poly", [(3.4, 2.0), (4.6, 2.0), (4.3, 3.3), (4.0, 3.6), (3.7, 3.3)], col=P["glow"], glow=True, alpha=255, rz=0.1)
    T.box("armorHead", (-0.6, 26.6, 4.45), (0.6, 29.4, 4.6), "indigo", rot=(-12, 0, 10), pivot=(0, 29, 4.5))
    T.box("armorHead", (0.2, 26.0, 4.45), (1.2, 29.4, 4.6), "indigo", rot=(-8, 0, -14), pivot=(0.7, 29, 4.5))
    return T

@design("indigo", "b", "Xamã", "fibra", "pena")
def indigo_b():
    P = dict(base="#1C0E52", i="#4B2BBF", i2="#120838", osso="#E0D6C0", pena="#6A4ACF", glow="#B8A8FF", couro="#2A1E18")
    T = Traje("traje_indigo_b", P)
    anatomia(T, 1.2); costuras_padrao(T, "#0A0420")
    # pintura tribal em faixas (linhas paralelas e triângulos)
    for part, faces in (("body", ("front", "back")), ("rarm", ("outer", "front")), ("larm", ("outer", "front")), ("rleg", ("outer", "front")), ("lleg", ("outer", "front"))):
        for f in faces:
            wd = 8 if part == "body" else 4
            for b in (6.4, 9.6) if part == "body" else (2.4, 8.2):
                T.draw(part, f, "rect", 0, b, wd, b + 0.25, col=P["glow"], glow=True)
                T.draw(part, f, "rect", 0, b + 0.6, wd, b + 0.85, col=P["glow"], glow=True)
                for a in [x * 1.0 + 0.5 for x in range(int(wd))]:
                    T.draw(part, f, "poly", [(a - 0.35, b + 0.85), (a + 0.35, b + 0.85), (a, b + 1.5)], col=P["glow"], glow=True)
    emblema3d(T, EMBLEMAS["indigo"], P["glow"], fundo=P["i2"], aro=P["osso"], b_c=3.0)
    gola(T, P["couro"], alto=0.6)
    cinto(T, P["couro"], fivela=P["osso"])
    T.mat("osso", P["osso"], "osso"); T.mat("pena", P["pena"], "solid"); T.mat("pena2", P["i"], "solid"); T.mat("couro", P["couro"], "solid"); T.mat("luz", P["glow"], glow=True)
    # colar de dentes/ossos
    for i, x in enumerate((-3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0)):
        y = 22.8 - (3 - abs(x)) * 0.3
        T.box("armorBody", (x - 0.2, y - 0.9, -2.7), (x + 0.2, y, -2.45), "osso")
    # franja de penas nos ombros
    for s, bn, xo, sg in (("r", "armorRightArm", -8.25, -1), ("l", "armorLeftArm", 8.25, 1)):
        for k in range(5):
            z = -2.0 + k * 1.0
            T.box(bn, (xo - (0.15 if s == "r" else 0), 19.4 - (k % 2) * 0.6, z - 0.4), (xo + (0.15 if s == "l" else 0), 23.8, z + 0.4),
                  "pena" if k % 2 else "pena2", rot=(0, 0, sg * -14), pivot=(xo, 23.8, z))
    ombro_fino(T, P["couro"], borda=P["osso"], alto=2.0, rz=0.1)
    bracadeira(T, P["couro"], rz=0.22, b0=7.2, b1=10.0, borda=P["osso"], glow=P["glow"])
    luva(T, P["i2"], rz=0.1)
    joelheira(T, P["couro"], rz=0.22, borda=P["osso"])
    bota(T, P["couro"], "#140C08", cano=P["osso"])
    # máscara de xamã com riscos e penas na nuca
    mascara(T, P["i2"], lente=None, b0=1.8, b1=5.6)
    for a in (1.2, 2.2, 5.8, 6.8): T.draw("head", "front", "rect", a, 4.6, a + 0.25, 5.6, col=P["glow"], glow=True, alpha=255)
    lentes(T, "#E8E0FF", b0=3.3, b1=4.3)
    for k, (x, rz_) in enumerate(((-1.4, 18), (-0.5, 6), (0.5, -6), (1.4, -18))):
        T.box("armorHead", (x - 0.35, 27.0, 4.42), (x + 0.35, 31.5, 4.6), "pena" if k % 2 else "pena2", rot=(-25, 0, rz_), pivot=(x, 28.0, 4.5))
    return T

# ======================================================================== VIOLETA — Amor
@design("violeta", "a", "Safira", "acolchoado", "cristal")
def violeta_a():
    P = dict(base="#7A1E9A", v="#B03AD8", v2="#3E0850", rosa="#FF7AD8", cr="#FF9CF2", glow="#FFB8F2", prata="#E8D8F0")
    T = Traje("traje_violeta_a", P)
    anatomia(T, 1.0); costuras_padrao(T, "#4A0A5A", P["rosa"])
    # corpete escuro em forma de coração abraçando o emblema
    T.draw("body", "front", "poly", [(4.0, 8.2), (1.0, 5.4), (0.6, 3.4), (1.4, 1.6), (2.8, 1.2), (4.0, 2.0), (5.2, 1.2), (6.6, 1.6), (7.4, 3.4), (7.0, 5.4)],
           col=P["v2"], grad=0.15, rz=0.1, tag=2)
    T.draw("body", "front", "line", [(4.0, 8.2), (1.0, 5.4), (0.6, 3.4), (1.4, 1.6), (2.8, 1.2), (4.0, 2.0), (5.2, 1.2), (6.6, 1.6), (7.4, 3.4), (7.0, 5.4), (4.0, 8.2)],
           0.5, col=P["rosa"], glow=True, rz=0.1)
    emblema3d(T, EMBLEMAS["violeta"], P["glow"], fundo=P["v2"], aro=P["prata"], b_c=4.0)
    gola(T, P["prata"], alto=0.5, rz=0.1)
    cinto(T, P["v2"], fivela=P["cr"], fivela_glow=True)
    for f in ("right", "left"): T.draw("body", f, "rect", 1.6, 1.0, 2.4, 10.2, col=P["rosa"], glow=True)
    T.mat("cristal", P["cr"], "cristal", glow=True); T.mat("cristal2", "#C86AE8", "cristal"); T.mat("prata", P["prata"], "metal")
    # aglomerados de cristal nos ombros
    for s, bn, xo, sg in (("r", "armorRightArm", -6.0, -1), ("l", "armorLeftArm", 6.0, 1)):
        for (dx, dz, h, w, r) in ((0.0, 0.0, 2.6, 0.9, (0, 20, sg * -18)), (-sg * 0.9, -0.9, 1.8, 0.7, (12, 0, sg * -32)), (sg * 0.8, 0.9, 1.6, 0.6, (-14, 30, sg * -8)),
                                  (sg * 1.2, -0.6, 1.2, 0.5, (8, -20, sg * -45))):
            cristal(T, bn, (xo + dx, 24.2, dz), h, w, "cristal" if h > 2 else "cristal2", rot=r)
    ombro_fino(T, P["v2"], borda=P["prata"], alto=2.4, rz=0.1)
    for s in "rl":
        T.draw(s + "arm", "outer", "poly", [(2, 3.0), (3.0, 5.0), (2, 7.0), (1.0, 5.0)], col=P["cr"], glow=True, rz=0.22)   # losango
        T.draw(s + "leg", "outer", "poly", [(2, 1.0), (3.0, 3.0), (2, 5.0), (1.0, 3.0)], col=P["cr"], glow=True, rz=0.22)
    bracadeira(T, P["v2"], rz=0.22, b0=7.6, b1=10.0, borda=P["prata"])
    luva(T, P["v2"], rz=0.1)
    joelheira(T, P["v2"], rz=0.22, borda=P["rosa"])
    bota(T, P["v2"], "#1A0322", cano=P["prata"], biqueira=P["v"])
    # máscara de baile com gema e tiara de cristais
    mascara(T, P["v2"], lente=None)
    T.draw("head", "front", "poly", [(0.0, 2.6), (8.0, 2.6), (8.6, 1.8), (8.0, 4.9), (5.2, 5.0), (4.0, 4.4), (2.8, 5.0), (0.0, 4.9), (-0.6, 1.8)], col=P["v2"], alpha=255)
    lentes(T, "#FFE8FA", b0=3.5, b1=4.4, forma="angular")
    T.draw("head", "front", "poly", [(4.0, 2.6), (4.5, 3.2), (4.0, 3.8), (3.5, 3.2)], col=P["cr"], glow=True, alpha=255, rz=0.22)
    for (x, h, r) in ((-1.4, 1.4, 14), (0.0, 2.0, 0), (1.4, 1.4, -14)):
        cristal(T, "armorHead", (x, 30.8, -4.5), h, 0.6, "cristal", rot=(-12, 0, r))
    return T

@design("violeta", "b", "Coração Estelar", "seda", "acolchoado")
def violeta_b():
    P = dict(base="#1E0A26", v="#B03AD8", v2="#5A1478", rosa="#FF5CC8", glow="#FF9CF2", prata="#E8D8F0", cr="#FF9CF2")
    T = Traje("traje_violeta_b", P)
    anatomia(T, 1.2); costuras_padrao(T, "#0E0414", "#5A1478")
    # corpete com amarração cruzada na frente
    T.draw("body", "front", "poly", [(1.0, 4.0), (7.0, 4.0), (6.6, 10.3), (1.4, 10.3)], col=P["v2"], pat=6, grad=0.2, rz=0.1, tag=2)
    for i in range(6):
        b = 4.6 + i * 0.95
        T.draw("body", "front", "line", [(3.2, b), (4.8, b + 0.9)], 0.25, col=P["prata"], rz=0.22, rz_mode="max")
        T.draw("body", "front", "line", [(4.8, b), (3.2, b + 0.9)], 0.25, col=P["prata"], rz=0.22, rz_mode="max")
    T.symrect("body", "front", 2.9, 4.2, 3.2, 10.1, col=P["rosa"], rz=0.22)
    emblema3d(T, EMBLEMAS["violeta"], P["glow"], fundo=P["v2"], aro=P["prata"], b_c=2.4, rz=0.38)
    gola(T, P["v2"], alto=0.6, rz=0.22)
    cinto(T, P["v2"], fivela=P["cr"], fivela_glow=True)
    # rabo de casaca (abas longas atrás) e ombreiras de cristal
    T.mat("casaca", P["v2"], "solid"); T.mat("forro", P["rosa"], "solid"); T.mat("cristal", P["cr"], "cristal", glow=True); T.mat("prata", P["prata"], "metal")
    for sg in (-1, 1):
        T.box("armorBody", (sg * 1.9 - 1.7, 3.4, 2.3), (sg * 1.9 + 1.7, 11.6, 2.55), "casaca", rot=(-6, 0, sg * 4), pivot=(sg * 1.9, 11.6, 2.4))
        T.box("armorBody", (sg * 1.9 - 1.7, 3.4, 2.18), (sg * 1.9 + 1.7, 3.9, 2.3), "forro", rot=(-6, 0, sg * 4), pivot=(sg * 1.9, 11.6, 2.4))
    for s, bn, xo, sg in (("r", "armorRightArm", -8.3, -1), ("l", "armorLeftArm", 8.3, 1)):
        for k in range(4):
            z = -1.5 + k * 1.0
            cristal(T, bn, (xo + sg * 0.1, 22.6 - (k % 2) * 0.4, z), 0.9, 0.45, "cristal", rot=(0, 0, sg * -70))
    ombro_fino(T, P["v2"], borda=P["prata"], alto=2.6, rz=0.22)
    for s in "rl":
        T.draw(s + "arm", "outer", "rect", 1.75, 2.6, 2.25, 11.0, col=P["rosa"], glow=True)
        T.draw(s + "leg", "outer", "rect", 1.75, 0.5, 2.25, 11.5, col=P["rosa"], glow=True)
    bracadeira(T, P["v2"], rz=0.1, b0=8.0, b1=10.0, borda=P["prata"])
    luva(T, "#0E0414", rz=0.1, junta=P["prata"])
    joelheira(T, P["v2"], rz=0.22, borda=P["prata"])
    bota(T, P["v2"], "#08020A", cano=P["prata"], biqueira=P["prata"])
    # máscara fina com gema
    mascara(T, P["v2"], lente="#FFE8FA")
    T.draw("head", "front", "poly", [(4.0, 2.2), (4.5, 2.8), (4.0, 3.4), (3.5, 2.8)], col=P["cr"], glow=True, alpha=255, rz=0.22)
    return T

# ======================================================================== PRETO — Morte
def esqueleto(T, osso, sombra, rz=0.22):
    # costelas curvas, esterno e clavículas na frente
    T.draw("body", "front", "rect", 3.6, 1.2, 4.4, 7.4, col=osso, rz=rz, tag=2)
    for i in range(5):
        b = 1.6 + i * 1.2
        T.sym("body", "front", "poly", [(3.6, b), (1.8, b + 0.1 + i * 0.05), (0.6, b + 0.6 + i * 0.12), (0.6, b + 1.0 + i * 0.12), (1.8, b + 0.6 + i * 0.05), (3.6, b + 0.5)],
              col=osso, rz=rz, tag=2)
    T.sym("body", "front", "line", [(0.4, 0.8), (3.6, 1.2)], 0.5, col=osso, rz=rz, rz_mode="max", tag=2)
    T.sym("body", "front", "poly", [(3.4, 9.2), (0.8, 9.6), (1.4, 11.0), (3.6, 10.6)], col=osso, rz=rz, tag=2)   # bacia
    # coluna e omoplatas atrás
    for i in range(9):
        b = 0.8 + i * 1.05
        T.draw("body", "back", "rect", 3.3, b, 4.7, b + 0.7, col=osso, rz=rz, tag=2)
    T.sym("body", "back", "poly", [(0.8, 1.2), (3.0, 1.6), (2.6, 4.6), (1.2, 5.0)], col=osso, rz=0.1, tag=2)
    for i in range(3):
        b = 5.6 + i * 1.1
        T.sym("body", "back", "line", [(3.3, b), (1.2, b + 0.4), (0.4, b + 1.0)], 0.5, col=osso, rz=0.1, rz_mode="max")
    # ossos dos membros (úmero/rádio, fêmur/tíbia) e dos dedos
    for s in "rl":
        for f in ("outer", "front"):
            T.draw(s + "arm", f, "rect", 1.4, 0.8, 2.6, 6.0, col=osso, rz=rz, tag=2); T.draw(s + "arm", f, "ellipse", 1.0, 5.6, 3.0, 7.0, col=osso, rz=rz, tag=2)
            T.draw(s + "arm", f, "rect", 0.8, 7.0, 1.6, 10.4, col=osso, rz=0.1, tag=2); T.draw(s + "arm", f, "rect", 2.4, 7.0, 3.2, 10.4, col=osso, rz=0.1, tag=2)
            T.draw(s + "leg", f, "rect", 1.3, 0.6, 2.7, 5.0, col=osso, rz=rz, tag=2); T.draw(s + "leg", f, "ellipse", 0.9, 4.8, 3.1, 6.6, col=osso, rz=rz, tag=2)
            T.draw(s + "leg", f, "rect", 1.4, 6.6, 2.6, 12.0, col=osso, rz=0.1, tag=2)
        for a in (0.4, 1.4, 2.4, 3.2):
            T.draw(s + "arm", "front", "rect", a, 10.6, a + 0.5, 11.8, col=osso, rz=0.1, tag=2)

@design("preto", "a", "Ossuário", "malha", "osso")
def preto_a():
    P = dict(base="#0E0E12", osso="#D8D6CC", osso2="#8A887E", esc="#050507", glow="#E8E8FF", fumo="#2A2A33")
    T = Traje("traje_preto_a", P)
    anatomia(T, 1.4); costuras_padrao(T, "#000000", "#1E1E26")
    esqueleto(T, P["osso"], P["osso2"])
    emblema3d(T, EMBLEMAS["preto"], P["glow"], fundo=P["esc"], aro=P["osso2"], b_c=3.6, rz=0.6, rz_fundo=0.38)
    gola(T, P["fumo"], alto=0.6)
    cinto(T, P["fumo"], fivela=P["osso"])
    ombro_fino(T, P["fumo"], borda=P["osso2"], alto=2.2, rz=0.1)
    luva(T, P["esc"], rz=0.1)
    esqueleto_mao = None
    T.mat("osso", P["osso"], "osso"); T.mat("osso2", P["osso2"], "osso"); T.mat("luz", P["glow"], glow=True)
    # pequeno crânio na ombreira esquerda
    T.box("armorLeftArm", (6.2, 24.25, -1.0), (8.0, 25.7, 0.8), "osso")
    T.box("armorLeftArm", (6.4, 24.25, -1.25), (7.8, 24.8, -1.0), "osso2")
    T.box("armorLeftArm", (6.5, 25.0, -1.06), (6.95, 25.4, -1.0), "luz"); T.box("armorLeftArm", (7.25, 25.0, -1.06), (7.7, 25.4, -1.0), "luz")
    joelheira(T, P["fumo"], rz=0.1)
    bota(T, P["esc"], "#000000", cano=P["fumo"], biqueira=P["osso2"])
    for s in "rl":
        for f in ("front", "outer"): T.draw(s + "boot", f, "rect", 1.4, 0.6, 2.6, 4.2, col=P["osso"], rz=0.1, tag=2)
    # meia-caveira: só a parte dos olhos (órbitas, nariz e a fileira de dentinhos de cima); boca e queixo livres
    cabeca_vazia(T)
    contorno = [(0.0, 1.4), (2.0, 1.0), (4.0, 1.5), (6.0, 1.0), (8.0, 1.4), (8.0, 4.6), (6.9, 5.4), (5.6, 5.6), (2.4, 5.6), (1.1, 5.4), (0.0, 4.6)]
    T.draw("head", "front", "poly", contorno, col=P["osso"], alpha=255, grad=0.14, pat=12, rz=0.1)
    for f in ("right", "left"): T.draw("head", f, "poly", [(0, 1.4), (2.6, 1.8), (2.6, 4.2), (0, 4.6)], col=P["osso2"], alpha=255, pat=12)
    T.draw("head", "front", "line", contorno + [contorno[0]], 0.25, col=P["osso2"], alpha=255, rz=0.1)
    for sg in (0, 1):   # órbitas fundas com a pupila brilhando
        orb = [(0.9, 2.4), (2.4, 2.1), (3.5, 2.7), (3.4, 4.3), (2.4, 4.8), (1.2, 4.3)]
        if sg: orb = [(8 - a, b) for a, b in orb]
        T.draw("head", "front", "poly", orb, col=P["esc"], alpha=255, rz=0.1)
        T.draw("head", "front", "line", orb + [orb[0]], 0.25, col=dk(P["osso"], 0.35), alpha=255, rz=0.1)
    for a0 in (1.9, 5.4): T.draw("head", "front", "rect", a0, 3.3, a0 + 0.75, 4.05, col=P["glow"], glow=True, alpha=255, rz=0.1)
    T.draw("head", "front", "poly", [(4.0, 4.0), (4.55, 5.1), (3.45, 5.1)], col=P["esc"], alpha=255, rz=0.1)           # nariz
    T.draw("head", "front", "line", [(2.0, 1.75), (3.4, 2.15)], 0.25, col=lt(P["osso"], 0.15), alpha=255, rz=0.1)        # sobrancelha (luz)
    T.draw("head", "front", "line", [(6.0, 1.75), (4.6, 2.15)], 0.25, col=lt(P["osso"], 0.15), alpha=255, rz=0.1)
    for i in range(7):   # dentinhos pendendo da borda (cobrem só o lábio de cima)
        a = 2.25 + i * 0.5
        T.draw("head", "front", "rect", a, 5.6, a + 0.25 + 0.0, 6.35 if i % 3 else 6.1, col=lt(P["osso"], 0.08), alpha=255, rz=0.1)
        T.draw("head", "front", "rect", a + 0.25, 5.6, a + 0.5, 5.85, col=P["osso2"], alpha=255, rz=0.1)
    return T

@design("preto", "b", "Ceifador", "fibra", "couro")
def preto_b():
    P = dict(base="#121216", pano="#1C1C24", pano2="#2A2A34", osso="#C8C6BC", esc="#050507", glow="#DDE6FF", fio="#6A7088", sombra="#0A0A0E")
    T = Traje("traje_preto_b", P)
    anatomia(T, 1.25); costuras_padrao(T, "#000000", "#24242C")
    # costelas fantasmas brilhando por baixo do tecido e coluna luminosa nas costas
    for i in range(4):
        b = 1.5 + i * 1.05
        T.sym("body", "front", "line", [(3.55, b), (2.3, b + 0.1), (1.1, b + 0.7), (0.5, b + 1.4)], 0.25, col=P["glow"], glow=True)
    T.draw("body", "front", "rect", 3.75, 1.3, 4.25, 5.6, col=P["glow"], glow=True)
    for i in range(8): T.draw("body", "back", "rect", 3.6, 1.0 + i * 1.15, 4.4, 1.6 + i * 1.15, col=P["glow"], glow=True)
    emblema3d(T, EMBLEMAS["preto"], P["glow"], fundo=P["esc"], aro=P["fio"], a_c=4.0, b_c=8.0, rz=0.38)
    cinto(T, P["pano2"], fivela=P["osso"])
    T.mat("pano", P["pano"], "solid"); T.mat("pano2", P["pano2"], "solid"); T.mat("dobra", "#16161C", "solid"); T.mat("osso", P["osso"], "osso")
    # capuz CAÍDO em volta do pescoço (gola de pano dobrado, mais alta atrás), sem cobrir a cabeça
    for (x0, x1, z0, z1, y1) in ((-4.6, 4.6, -2.75, -2.25, 24.9), (-4.6, 4.6, 2.25, 3.1, 25.9), (-4.75, -4.25, -2.75, 3.1, 25.3), (4.25, 4.75, -2.75, 3.1, 25.3)):
        T.box("armorBody", (x0, 23.2, z0), (x1, y1, z1), "pano")
    T.box("armorBody", (-4.4, 25.0, -2.6), (4.4, 25.5, -2.2), "dobra"); T.box("armorBody", (-4.4, 25.6, 2.3), (4.4, 26.6, 3.4), "dobra")
    T.box("armorBody", (-3.8, 20.6, 3.0), (3.8, 25.9, 3.55), "pano")                         # o capuz pendurado nas costas
    T.box("armorBody", (-3.0, 19.6, 3.0), (3.0, 20.6, 3.45), "pano")
    T.box("armorBody", (-2.0, 18.8, 3.0), (2.0, 19.6, 3.35), "pano")
    T.box("armorBody", (-3.5, 21.0, 3.55), (3.5, 21.4, 3.7), "dobra")
    # mortalha rasgada caindo dos ombros e da cintura
    tiras(T, "armorBody", [(-3.2, 12.0, -2.65, 3.4, 4), (-1.1, 12.0, -2.65, 4.4, 3), (1.1, 12.0, -2.65, 3.0, 5), (3.2, 12.0, -2.65, 4.0, 4)], "pano2", larg=1.5, esp=0.15)
    tiras(T, "armorBody", [(-2.6, 18.6, 3.4, 7.0, -4), (-0.8, 18.6, 3.4, 8.6, -3), (0.9, 18.6, 3.4, 7.6, -4), (2.6, 18.6, 3.4, 9.0, -3)], "pano", larg=1.6, esp=0.15)
    for s, bn, xo in (("r", "armorRightArm", -8.3), ("l", "armorLeftArm", 8.3)):
        tiras(T, bn, [(xo, 23.2, -1.2, 4.6, 0), (xo, 23.2, 0.3, 6.0, 0), (xo, 23.2, 1.6, 4.0, 0)], "pano", larg=1.2, esp=0.15, eixo="x")
    ombro_fino(T, P["pano2"], borda=P["fio"], alto=2.6, rz=0.22)
    bracadeira(T, P["pano2"], rz=0.1, b0=7.4, b1=10.0, borda=P["fio"])
    luva(T, P["esc"], rz=0.1)
    for s in "rl":
        for a in (0.4, 1.4, 2.4, 3.2): T.draw(s + "arm", "front", "rect", a, 10.6, a + 0.5, 11.8, col=P["osso"], rz=0.1, tag=2)
    joelheira(T, P["pano2"], rz=0.1)
    bota(T, P["esc"], "#000000", cano=P["pano2"], biqueira=P["pano2"])
    # rosto livre: só uma sombra escura em volta dos olhos, com as pupilas brilhando
    cabeca_vazia(T)
    T.draw("head", "front", "poly", [(0.0, 2.6), (8.0, 2.6), (8.0, 4.6), (5.2, 5.0), (4.4, 4.5), (3.6, 4.5), (2.8, 5.0), (0.0, 4.6)], col=P["sombra"], alpha=255)
    for f in ("right", "left"): T.draw("head", f, "poly", [(0, 2.6), (2.2, 2.9), (2.2, 4.3), (0, 4.6)], col=P["sombra"], alpha=255)
    for a0 in (1.6, 5.2): T.draw("head", "front", "rect", a0, 3.5, a0 + 1.2, 4.2, col=P["glow"], glow=True, alpha=255)
    return T

# ======================================================================== BRANCO — Vida
@design("branco", "a", "Gênese", "seda", "placa")
def branco_a():
    P = dict(base="#F0F2F4", br="#FFFFFF", cinza="#C8CED6", ouro="#E2B13A", ouro2="#9A7418", glow="#FFF6C8", luz="#FFE38A")
    T = Traje("traje_branco_a", P)
    anatomia(T, 1.0); costuras_padrao(T, "#C8CED6", P["ouro2"])
    import math as _m
    cx, cy = 4.0, 3.4
    for k in range(12):   # raios de sol dourados em relevo partindo do emblema
        ang = _m.radians(k * 30 + 15)
        r0, r1 = 3.0, 5.6 if k % 2 == 0 else 4.4
        T.draw("body", "front", "line", [(cx + r0 * _m.cos(ang), cy + r0 * _m.sin(ang)), (cx + r1 * _m.cos(ang), cy + r1 * _m.sin(ang))], 0.5,
               col=P["ouro"], rz=0.22, rz_mode="max", tag=2)
    emblema3d(T, EMBLEMAS["branco"], P["glow"], fundo=P["ouro2"], aro=P["ouro"], b_c=cy)
    gola(T, P["ouro"], alto=0.5, rz=0.22)
    cinto(T, P["ouro"], fivela=P["luz"], fivela_glow=True)
    # costuras douradas (debruns) contornando o macacão
    for s in "rl":
        for p in (s + "arm", s + "leg"):
            for f in ("outer",): T.draw(p, f, "rect", 1.75, 0, 2.25, 12, col=P["ouro"], rz=0.1)
    ombro_fino(T, P["br"], borda=P["ouro"], alto=2.6, rz=0.22)
    bracadeira(T, P["br"], rz=0.22, b0=7.4, b1=10.0, borda=P["ouro"])
    luva(T, P["cinza"], rz=0.1, junta=P["ouro"])
    joelheira(T, P["br"], rz=0.22, borda=P["ouro"])
    bota(T, P["br"], P["cinza"], cano=P["ouro"], biqueira=P["br"])
    T.mat("ouro", P["ouro"], "metal"); T.mat("folha", "#C8A030", "metal"); T.mat("luz", P["glow"], glow=True)
    # louros dourados nos ombros
    for s, bn, xo, sg in (("r", "armorRightArm", -7.6, -1), ("l", "armorLeftArm", 7.6, 1)):
        for k in range(5):
            z = -1.8 + k * 0.9
            T.box(bn, (xo - 0.3, 24.25, z - 0.25), (xo + 0.3, 24.55, z + 0.25), "folha", rot=(0, 35, 0), pivot=(xo, 24.4, z))
            T.box(bn, (xo + sg * 0.6 - 0.25, 24.25, z - 0.2), (xo + sg * 0.6 + 0.25, 24.5, z + 0.2), "ouro", rot=(0, -35, 0), pivot=(xo + sg * 0.6, 24.4, z))
    # auréola flutuando acima da cabeça
    aureola(T, 4.2, 34.4, "luz", n=30, esp=0.45)
    cabeca_vazia(T)
    for f in ("front", "right", "left", "back"): T.draw("head", f, "rect", 0, 1.6, 8, 2.2, col=P["ouro"], alpha=255, rz=0.1)
    T.draw("head", "front", "poly", [(3.4, 1.6), (4.6, 1.6), (4.0, 2.8)], col=P["glow"], glow=True, alpha=255, rz=0.1)
    return T

@design("branco", "b", "Fênix", "pena", "seda")
def branco_b():
    P = dict(base="#F6F6F4", cinza="#D0D6DE", ouro="#E8C060", glow="#FFFFFF", fogo="#FFB040")
    T = Traje("traje_branco_b", P)
    anatomia(T, 1.0); costuras_padrao(T, "#C0C8D2")
    # espectro inteiro: sete linhas de luz (todas as Tropas) contornando o corpo em espiral
    cores = ["#FF4A2A", "#FF9A1A", "#FFE04A", "#3CE07A", "#3C9AFF", "#6A4AFF", "#C04AE8"]
    for i, c in enumerate(cores):
        o = i * 0.5
        T.draw("body", "front", "line", [(0.0, 9.0 - o), (4.0, 5.6 - o), (8.0, 9.0 - o)], 0.25, col=c, glow=True)
        T.draw("body", "back", "line", [(0.0, 3.0 + o), (8.0, 6.4 + o)], 0.25, col=c, glow=True)
        for s in "rl":
            T.draw(s + "arm", "outer", "line", [(0.2 + o * 0.5, 0.2), (3.8 - o * 0.2, 11.8)], 0.25, col=c, glow=True)
            T.draw(s + "leg", "outer", "line", [(3.8 - o * 0.4, 0.2), (0.2 + o * 0.3, 11.8)], 0.25, col=c, glow=True)
    emblema3d(T, EMBLEMAS["branco"], "#FFF6D8", fundo="#E8E2D0", aro=P["ouro"], b_c=2.9)
    gola(T, P["ouro"], alto=0.5, rz=0.22)
    cinto(T, P["cinza"], fivela=P["glow"], fivela_glow=True)
    ombro_fino(T, P["glow"], borda=P["ouro"], alto=2.4, rz=0.22)
    luva(T, P["cinza"], rz=0.1, junta=P["ouro"])
    joelheira(T, P["cinza"], rz=0.22, borda=P["ouro"])
    bota(T, P["cinza"], "#A8B0BA", cano=P["ouro"], biqueira=P["glow"])
    T.mat("pena", "#FFFFFF", "solid", glow=True); T.mat("pena2", "#FFE6A8", "solid", glow=True); T.mat("ouro", P["ouro"], "metal")
    # asas de luz nas costas (penas em leque, pontas brilhando)
    T.mat("pena3", "#F2EEE4", "solid")
    for sg in (-1, 1):
        for k in range(7):
            ang = 30 + k * 13
            L = 8.0 - k * 0.7
            T.box("armorBody", (sg * 0.9 - 0.4, 19.6, 4.6), (sg * 0.9 + 0.4, 19.6 + L, 4.85), "pena3",
                  rot=(-6, 0, -sg * ang), pivot=(sg * 0.9, 19.6, 4.7))
            T.box("armorBody", (sg * 0.9 - 0.3, 19.6 + L - 1.4, 4.55), (sg * 0.9 + 0.3, 19.6 + L, 4.9), "pena" if k % 2 == 0 else "pena2",
                  rot=(-6, 0, -sg * ang), pivot=(sg * 0.9, 19.6, 4.7))
        T.box("armorBody", (sg * 0.9 - 0.7, 19.0, 2.3), (sg * 0.9 + 0.7, 20.4, 4.95), "ouro")
    # coroa de luz
    cabeca_vazia(T)
    for f in ("front", "right", "left", "back"): T.draw("head", f, "rect", 0, 1.4, 8, 2.0, col=P["ouro"], alpha=255, rz=0.1)
    for k, x in enumerate((-3.0, -1.5, 0.0, 1.5, 3.0)):
        h = (1.0, 1.6, 2.4, 1.6, 1.0)[k]
        T.box("armorHead", (x - 0.25, 30.6, -4.6), (x + 0.25, 30.6 + h, -4.45), "pena" if k == 2 else "pena2")
    return T

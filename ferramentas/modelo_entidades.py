"""
0.60.0: as sete entidades emocionais, presas dentro das Baterias Centrais (animais feitos de energia, como nos quadrinhos).

    python3 ferramentas/modelo_entidades.py
      -> geo/entity/entidade_<tropa>.geo.json, textures/entity/entidade_<tropa>.png,
         animations/entity/entidade_<tropa>.animation.json ("animation.entidade_<tropa>.nadar", em laço)

  verde     Íon        a Vontade: uma baleia do espaço (corpo em 7 segmentos, nadadeiras, cauda larga)
  amarelo   Parallax   o Medo: um inseto-larva enorme (11 segmentos, mandíbulas, antenas, 6 patas articuladas, ferrão)
  vermelho  Açougueiro a Raiva: um touro-demônio (chifres, crina de espinhos, 4 patas com coxa e canela, cauda)
  laranja   Ofidiano   a Avareza: uma serpente coroada (16 segmentos, capuz de espinhos, mandíbula, língua bifurcada)
  azul      Adara      a Esperança: uma ave (asas com 3 partes e penas uma a uma, crista, leque da cauda)
  indigo    Prosélito  a Compaixão: uma lula (manto, olhos, 8 tentáculos com 5 segmentos cada, nadadeiras)
  violeta   Predador   o Amor: um caçador alado (pescoço longo, chifres, asas de membrana, cauda com farpa)

Cada corpo é feito de muitos cubos pequenos (fatias de elipsoide), com a pintura de energia na cor da Tropa: núcleo mais
claro, veios de luz e bordas mais escuras. O mod desenha a textura brilhando e meio transparente.
Convenção dos ossos: frente = -z. Animações: mesma convenção do anim_necron.py (graus; X e Y gravados invertidos).
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import modelo_necron as base  # noqa: E402

RAIZ_ANIM = os.path.join(base.RAIZ, "animations", "entity")

CORES = {
    "verde": ((150, 245, 185), (40, 150, 90), (240, 255, 245)),
    "amarelo": ((255, 214, 40), (120, 84, 0), (255, 252, 210)),
    "vermelho": ((240, 40, 24), (96, 6, 6), (255, 196, 170)),
    "laranja": ((255, 140, 30), (120, 50, 0), (255, 232, 180)),
    "azul": ((70, 170, 255), (10, 50, 130), (215, 240, 255)),
    "indigo": ((120, 90, 255), (40, 20, 120), (226, 214, 255)),
    "violeta": ((240, 90, 210), (100, 20, 90), (255, 214, 245)),
}


# ============================== pintura de energia ==============================

def pintores(tropa, semente):
    cor, esc_, clara = CORES[tropa]

    def energia(P, N, f, u, v, w, h, k=1.0, alfa=235):
        x, y, z = P
        n = base.fbm(x, y, z, 3.5, semente, 3)
        veio = abs(base.ruido3(x * 0.9 + 7, y * 0.9, z * 0.9, 2.2, semente + 3) - 0.5)
        c = base.mistura(esc_, cor, 0.45 + 0.55 * n)
        if veio < 0.06:
            c = base.mistura(c, clara, 1 - veio / 0.06)
        # borda de cada face um pouco mais escura (dá volume aos cubos)
        borda = min(u, 1 - u, v, 1 - v)
        if borda < 0.08:
            c = base.esc(c, 0.82)
        if N[1] > 0.5:
            c = base.mistura(c, clara, 0.12)
        return base.rgba(base.esc(c, k), alfa)

    def membrana(P, N, f, u, v, w, h):
        x, y, z = P
        n = base.fbm(x, y, z, 2.5, semente + 9, 2)
        c = base.mistura(cor, clara, 0.25 + 0.35 * n)
        # nervuras
        if abs(math.sin((x + z) * 0.9)) < 0.08:
            c = base.mistura(c, clara, 0.7)
        return base.rgba(c, 170)

    def olho(P, N, f, u, v, w, h):
        d = math.hypot(u - 0.5, v - 0.5)
        return base.rgba(base.mistura((255, 255, 255), clara, d * 1.6), 255)

    def escuro(P, N, f, u, v, w, h):
        return energia(P, N, f, u, v, w, h, 0.55, 245)

    def claro(P, N, f, u, v, w, h):
        x, y, z = P
        return base.rgba(base.mistura(clara, cor, 0.25 * base.fbm(x, y, z, 2, semente + 5, 2)), 245)

    return {"corpo": energia, "membrana": membrana, "olho": olho, "escuro": escuro, "claro": claro}


# ============================== ferramentas de forma ==============================

def elipsoide(m, osso, c, r, mat, eixo="z", fatias=8, D=3, cortar=None):
    """Um elipsoide em fatias (cubos) ao longo de um eixo, com uma segunda camada para arredondar."""
    cx, cy, cz = c
    rx, ry, rz = r
    fatias *= 2   # 0.60.0: o dobro de fatias (o contorno fica bem mais redondo)
    for i in range(fatias):
        t0, t1 = -1 + 2 * i / fatias, -1 + 2 * (i + 1) / fatias
        tm = (t0 + t1) / 2
        k = math.sqrt(max(0.0, 1 - tm * tm))
        if k < 0.05:
            continue
        for camada, (a, b) in enumerate(((1.0, 0.55), (0.55, 1.0), (0.86, 0.86))):
            if eixo == "z":
                sx, sy, sz = 2 * rx * k * a, 2 * ry * k * b, (t1 - t0) * rz
                o = (cx - sx / 2, cy - sy / 2, cz + t0 * rz)
            elif eixo == "y":
                sx, sy, sz = 2 * rx * k * a, (t1 - t0) * ry, 2 * rz * k * b
                o = (cx - sx / 2, cy + t0 * ry, cz - sz / 2)
            else:
                sx, sy, sz = (t1 - t0) * rx, 2 * ry * k * a, 2 * rz * k * b
                o = (cx + t0 * rx, cy - sy / 2, cz - sz / 2)
            if cortar and cortar(o, (sx, sy, sz)):
                continue
            m.cubo(osso, o, (max(sx, 0.2), max(sy, 0.2), max(sz, 0.2)), mat, D=D)


def espinho(m, osso, base_pt, altura, larg, mat, inclina=(0, 0, 0), D=4):
    """Um espinho em degraus (3 cubos afinando), inclinado por rotação do primeiro eixo."""
    x, y, z = base_pt
    for k in range(3):
        f = 1 - k / 3
        a = altura / 3
        m.cubo(osso, (x - larg * f / 2, y + k * a, z - larg * f / 2), (larg * f, a + 0.05, larg * f), mat,
               rot=inclina if any(inclina) else None, pivo=base_pt, D=D)


def cadeia(m, nomes, pai, inicio, passo):
    """Ossos em sequência (cada um filho do anterior), começando em 'inicio' e andando 'passo' por osso."""
    x, y, z = inicio
    for i, n in enumerate(nomes):
        m.osso(n, pai if i == 0 else nomes[i - 1], (x + passo[0] * i, y + passo[1] * i, z + passo[2] * i))


# ============================== animação ==============================

def gravar_anim(nome, dur, quadros):
    """quadros(t) -> {osso: (x, y, z)} em graus; gera 13 quadros no laço."""
    ossos = {}
    n = 12
    for i in range(n + 1):
        t = dur * i / n
        for b, v in quadros(t / dur).items():
            ossos.setdefault(b, {}).setdefault("rotation", {})["%.3f" % t] = {
                "post": [round(-v[0], 2), round(-v[1], 2), round(v[2], 2)], "lerp_mode": "catmullrom"}
    os.makedirs(RAIZ_ANIM, exist_ok=True)
    with open(os.path.join(RAIZ_ANIM, nome + ".animation.json"), "w") as f:
        json.dump({"format_version": "1.8.0", "animations": {"animation.%s.nadar" % nome: {"loop": True, "animation_length": dur, "bones": ossos}}}, f, indent=1)


def onda(segs, f, amp, fase=0.9, eixo=1, tempo_ciclos=1):
    """Ondulação passando pelos segmentos (cada um um pouco depois do anterior)."""
    out = {}
    for i, s in enumerate(segs):
        a = amp * (0.4 + 0.6 * i / max(1, len(segs) - 1)) * math.sin(2 * math.pi * f * tempo_ciclos - i * fase)
        v = [0, 0, 0]
        v[eixo] = a
        out[s] = tuple(v)
    return out


S = math.sin
TAU = 2 * math.pi


# ============================== VERDE: Íon, a baleia do espaço ==============================

def ion():
    m = base.Modelo("entidade_verde")
    m.osso("root")
    segs = ["corpo_%d" % i for i in range(1, 11)]
    cadeia(m, segs, "root", (0, 10, -14), (0, 0, 7))
    raios = [(8, 7.4), (8.6, 7.8), (8.4, 7.4), (7.6, 6.8), (6.6, 6), (5.6, 5), (4.6, 4.2), (3.6, 3.3), (2.7, 2.5), (2, 1.8)]
    for i, s_ in enumerate(segs):
        z0 = -14 + 7 * i
        rx, ry = raios[i]
        elipsoide(m, s_, (0, 10, z0 + 3.5), (rx, ry, 4.2), "corpo", "z", 5, D=3)
        for k in range(4):   # pregas da barriga
            m.cubo(s_, (-rx * 0.7 + k * rx * 0.46 - 0.3, 10 - ry + 0.2, z0 + 0.6), (0.6, 0.5, 6), "escuro", D=3)
        for k in range(2):   # crista dorsal em fita
            m.cubo(s_, (-0.3, 10 + ry - 0.6 + k * 1.1, z0 + 1 + k * 1.5), (0.6, 1.6 - k * 0.4, 4.5 - k), "membrana", D=3)
    m.osso("cabeca", "corpo_1", (0, 10, -14))
    elipsoide(m, "cabeca", (0, 10.8, -19.5), (7.4, 6.4, 6.2), "corpo", "z", 6, D=3)
    m.osso("boca", "cabeca", (0, 7, -16))
    elipsoide(m, "boca", (0, 6.4, -20.5), (6.6, 2.2, 6), "escuro", "z", 5, D=3)
    for sx in (-1, 1):
        m.cubo("cabeca", (sx * 5.6 - 1.1, 11.4, -23.4), (2.2, 2.0, 2.0), "olho", D=6)
        m.cubo("cabeca", (sx * 5.6 - 1.4, 13.6, -23.0), (2.8, 0.7, 2.4), "escuro", D=4)
        for k in range(5):   # barbilhões finos e longos saindo do queixo
            m.cubo("boca", (sx * (2 + k * 0.2) - 0.25, 5 - k * 1.3, -25 + k * 0.6), (0.5, 1.4, 0.5), "membrana", D=5)
    # nadadeiras peitorais longas, em fita, que ondulam (3 partes cada)
    for sx, lado in ((-1, "d"), (1, "e")):
        ant = "corpo_2"
        for parte in range(3):
            n = "nadadeira%d_%s" % (parte + 1, lado)
            x0 = sx * (7.5 + parte * 9)
            m.osso(n, ant, (x0, 7 - parte * 0.6, -7 + parte * 4))
            for k in range(8):
                m.cubo(n, (x0 + (sx * k * 1.15 if sx > 0 else -k * 1.15 - 1.2), 6.6 - parte * 0.6 - k * 0.05, -8 + parte * 4 + k * 0.5),
                       (1.2, 0.5, 6.5 - parte * 1.6 - k * 0.25), "membrana", D=3)
            ant = n
    # cauda: lóbulos largos e três fitas longas que flutuam atrás
    m.osso("cauda", "corpo_10", (0, 10, 56))
    for sx in (-1, 1):
        for k in range(8):
            m.cubo("cauda", (sx * (0.8 + k * 1.9) - (2.0 if sx < 0 else 0), 9.6, 56.5 + k * 1.1), (2.0, 0.8, 6 - k * 0.5), "membrana", D=3)
    for f, (dx, dy) in enumerate(((-3, 0), (0, 1.2), (3, 0))):
        ant = "cauda"
        for k in range(5):
            n = "fita%d_%d" % (f, k)
            m.osso(n, ant, (dx, 10 + dy, 62 + k * 6))
            m.cubo(n, (dx - 0.8 + k * 0.05, 9.8 + dy, 62 + k * 6), (1.6 - k * 0.2, 0.35, 6.2), "membrana", D=3)
            ant = n
    m.salvar(pintores("verde", 11), {}, vis_w=6, vis_h=4)

    def q(f):
        p = onda(segs, f, 7, 0.55, 1)
        for s_, v in onda(segs, f, 4, 0.55, 0).items():
            p[s_] = (v[0], p[s_][1], 0)
        p["cauda"] = (12 * S(TAU * f - 10 * 0.55), 0, 0)
        for fi in range(3):
            for k in range(5):
                p["fita%d_%d" % (fi, k)] = (14 * S(TAU * f * 2 - k * 0.9 - fi), 10 * S(TAU * f - k * 0.7 + fi), 0)
        for parte in range(3):
            p["nadadeira%d_d" % (parte + 1)] = (0, 0, 20 * S(TAU * f - parte * 0.9))
            p["nadadeira%d_e" % (parte + 1)] = (0, 0, -20 * S(TAU * f - parte * 0.9))
        p["boca"] = (5 + 5 * S(TAU * f * 2), 0, 0)
        p["cabeca"] = (2 * S(TAU * f), 3 * S(TAU * f + 1), 0)
        return p
    gravar_anim("entidade_verde", 7.0, q)


# ============================== AMARELO: Parallax, o inseto do Medo ==============================

def parallax():
    m = base.Modelo("entidade_amarelo")
    m.osso("root")
    segs = ["corpo_%d" % i for i in range(1, 12)]
    cadeia(m, segs, "root", (0, 10, -6), (0, 0, 4.6))
    for i, s in enumerate(segs):
        z0 = -6 + 4.6 * i
        r = 6.2 - abs(i - 2.5) * 0.35 if i < 5 else 5.4 - (i - 5) * 0.62
        elipsoide(m, s, (0, 10, z0 + 2.3), (r, r * 0.8, 2.6), "corpo", "z", 4, D=3)
        m.cubo(s, (-r * 0.85, 10 + r * 0.55, z0 + 0.3), (r * 1.7, 0.7, 1.0), "escuro", D=3)   # placa do dorso
        espinho(m, s, (0, 10 + r * 0.75, z0 + 2), 1.6 + (i % 2) * 0.8, 0.9, "claro", (-30, 0, 0))
        for sx in (-1, 1):
            espinho(m, s, (sx * r * 0.8, 10 + r * 0.3, z0 + 2), 1.2, 0.7, "escuro", (0, 0, sx * 55))
    # cabeça: olhos compostos, mandíbulas, antenas
    m.osso("cabeca", "corpo_1", (0, 10, -6))
    elipsoide(m, "cabeca", (0, 10.5, -10), (5.5, 4.8, 4.5), "corpo", "z", 5, D=3)
    for sx in (-1, 1):
        for k in range(9):   # o olho composto, facetas
            m.cubo("cabeca", (sx * 4.6 - 1.2 + (k % 3) * 0.8 * sx, 10.6 + (k // 3) * 0.8, -13.2 + (k % 2) * 0.3), (0.8, 0.8, 0.8), "olho", D=6)
        lado = "d" if sx < 0 else "e"
        m.osso("mandibula_" + lado, "cabeca", (sx * 2.4, 8, -13))
        for k in range(5):
            m.cubo("mandibula_" + lado, (sx * (2.2 + k * 0.35) - 0.5, 7.6 - k * 0.15, -14 - k * 1.3), (1.0, 1.0 - k * 0.12, 1.4), "claro", D=4)
        m.cubo("mandibula_" + lado, (sx * 3.6 - 0.4, 7.2, -20.2), (0.8, 0.8, 0.8), "escuro", D=5)
        m.osso("antena_" + lado, "cabeca", (sx * 2, 14.5, -11))
        for k in range(10):
            m.cubo("antena_" + lado, (sx * (2 + k * 0.7) - 0.25, 14.5 + k * 1.2, -11.5 - k * 0.8), (0.5, 1.3, 0.5), "escuro", D=5)
    # seis patas articuladas (coxa e canela) nos três primeiros segmentos
    for i in range(3):
        s = segs[i + 1]
        z = -6 + 4.6 * (i + 1) + 2.3
        for sx, lado in ((-1, "d"), (1, "e")):
            coxa, canela = "pata%d_%s" % (i + 1, lado), "patab%d_%s" % (i + 1, lado)
            m.osso(coxa, s, (sx * 5, 8, z))
            for k in range(4):
                m.cubo(coxa, (sx * (5 + k * 1.5) - 0.7, 8 - k * 0.3, z - 0.7), (1.4, 1.2, 1.4), "corpo", D=4)
            m.osso(canela, coxa, (sx * 11, 7, z))
            for k in range(5):
                m.cubo(canela, (sx * 11 - 0.5, 7 - k * 1.6 - 1.6, z - 0.5 - k * 0.3), (1.0, 1.7, 1.0), "escuro", D=4)
            m.cubo(canela, (sx * 11 - 0.4, -1.6, z - 2.6), (0.8, 0.6, 1.6), "claro", D=5)   # garra
    # presas grandes saindo da boca
    for sx in (-1, 1):
        for k in range(5):
            m.cubo("cabeca", (sx * (1.4 - k * 0.15) - 0.4, 7.4 - k * 0.9, -13.6 - k * 0.5), (0.8, 1.0, 0.8), "claro", D=6)
    # dois pares de asas de inseto (membrana com nervuras), no 2º e 3º segmentos
    for par in range(2):
        for sx, lado in ((-1, "d"), (1, "e")):
            n = "asa%d_%s" % (par + 1, lado)
            z = -6 + 4.6 * (par + 1) + 1.5
            m.osso(n, segs[par + 1], (sx * 2, 15.5, z))
            for k in range(10):
                larg = 3.2 - abs(k - 3) * 0.32
                m.cubo(n, (sx * (2 + k * 1.6) - (1.6 if sx < 0 else 0), 15.4 + k * 0.12, z - larg / 2 + k * 0.35), (1.6, 0.25, max(0.6, larg * 1.8)), "membrana", D=3)
                if k % 3 == 0:
                    m.cubo(n, (sx * (2 + k * 1.6) - (1.6 if sx < 0 else 0), 15.55 + k * 0.12, z - 0.15 + k * 0.35), (1.6, 0.2, 0.3), "claro", D=4)
    # garras em pinça nas patas da frente
    for sx, lado in ((-1, "d"), (1, "e")):
        for k in range(2):
            m.cubo("patab1_" + lado, (sx * 11 - 0.6 + k * 0.9 * sx, -2.4, -0.5 - 3.4), (0.5, 1.4, 2.4), "claro", D=5)
    # ferrão
    m.osso("ferrao", segs[-1], (0, 10, 44))
    for k in range(6):
        m.cubo("ferrao", (-1.1 + k * 0.15, 10 + k * 0.7, 44 + k * 1.4), (2.2 - k * 0.3, 1.6 - k * 0.15, 1.6), "claro", D=4)
    m.salvar(pintores("amarelo", 23), {}, vis_w=6, vis_h=4)

    def q(f):
        p = onda(segs, f, 7, 0.55, 1, 1)
        for s, v in onda(segs, f, 5, 0.55, 0, 2).items():
            p[s] = (v[0], p[s][1], 0)
        for i in range(3):
            for sx, lado in ((-1, "d"), (1, "e")):
                fase = TAU * f * 3 + i * 2.1 + (math.pi if lado == "e" else 0)
                p["pata%d_%s" % (i + 1, lado)] = (25 * S(fase), 0, sx * (8 + 10 * S(fase + 1)))
                p["patab%d_%s" % (i + 1, lado)] = (-15 + 20 * S(fase + 0.8), 0, 0)
        for lado, sx in (("d", -1), ("e", 1)):
            p["mandibula_" + lado] = (0, sx * (10 + 10 * S(TAU * f * 4)), 0)
            p["antena_" + lado] = (10 * S(TAU * f * 2 + sx), 0, sx * 8 * S(TAU * f * 3))
        p["ferrao"] = (20 * S(TAU * f * 2), 0, 0)
        for par in range(2):   # as asas vibram rápido
            p["asa%d_d" % (par + 1)] = (0, 0, 28 * S(TAU * f * 10 + par * 1.5))
            p["asa%d_e" % (par + 1)] = (0, 0, -28 * S(TAU * f * 10 + par * 1.5))
        return p
    gravar_anim("entidade_amarelo", 5.0, q)


# ============================== VERMELHO: o Açougueiro, o touro-demônio da Raiva ==============================

def acougueiro():
    m = base.Modelo("entidade_vermelho")
    m.osso("root")
    m.osso("corpo", "root", (0, 16, 0))
    elipsoide(m, "corpo", (0, 18, -4), (8.6, 8, 9), "corpo", "z", 8, D=3)       # peito e ombros enormes
    elipsoide(m, "corpo", (0, 23, -5), (6.4, 4.2, 6.5), "corpo", "z", 6, D=3)   # a corcova de músculo
    elipsoide(m, "corpo", (0, 17, 8), (6.5, 6, 7), "corpo", "z", 6, D=3)        # ancas
    for k in range(12):                                                         # crina de espinhos nas costas
        espinho(m, "corpo", (0, 24.5 - abs(k - 4) * 0.4, -10 + k * 1.8), 3.2 - abs(k - 3) * 0.25, 1.3, "escuro", (-35, 0, 0))
    for k in range(6):                                                          # músculos (feixes mais escuros)
        m.cubo("corpo", (-8.2, 14 + k * 1.4, -9 + k), (0.6, 1.0, 8), "escuro", D=3)
        m.cubo("corpo", (7.6, 14 + k * 1.4, -9 + k), (0.6, 1.0, 8), "escuro", D=3)
    m.osso("cabeca", "corpo", (0, 20, -12))
    elipsoide(m, "cabeca", (0, 18.5, -17), (6.6, 6.2, 6.6), "corpo", "z", 7, D=3)
    for k in range(6):                                                          # rugas e veias saltadas na testa
        m.cubo("cabeca", (-3 + k * 1.2, 22.8, -21 + (k % 2) * 0.6), (0.6, 0.6, 3), "escuro", D=4)
    for sx in (-1, 1):                                                          # narinas fumegando
        m.cubo("cabeca", (sx * 1.6 - 0.5, 16.6, -25.3), (1.0, 0.9, 0.5), "escuro", D=6)
    elipsoide(m, "cabeca", (0, 16, -22), (3.6, 3.2, 3.2), "escuro", "z", 4, D=3)   # focinho
    m.osso("mandibula", "cabeca", (0, 15, -16))
    m.cubo("mandibula", (-3, 12.4, -24), (6, 1.6, 8), "escuro", D=3)
    for k in range(6):
        m.cubo("mandibula", (-2.6 + k * 1.0, 13.9, -23.6), (0.5, 1.2, 0.5), "claro", D=6)   # presas
    for sx in (-1, 1):
        m.cubo("cabeca", (sx * 3.6 - 0.9, 19.6, -22.2), (1.8, 1.3, 1.2), "olho", D=6)
        # chifres: curvos, para os lados e para a frente (em degraus)
        for k in range(9):
            a = k / 8
            m.cubo("cabeca", (sx * (5 + a * 7) - (1.6 - a) / 2, 22 + math.sin(a * math.pi) * 4, -16 - a * 5), (1.6 - a + 0.3, 1.6 - a + 0.3, 1.6 - a + 0.3),
                   "claro", D=4)
    # quatro patas: coxa e canela, cascos
    for z0, nome in ((-8, "dianteira"), (9, "traseira")):
        for sx, lado in ((-1, "d"), (1, "e")):
            coxa, canela = "perna_%s_%s" % (nome, lado), "canela_%s_%s" % (nome, lado)
            m.osso(coxa, "corpo", (sx * 5.5, 14, z0))
            elipsoide(m, coxa, (sx * 5.5, 10, z0), (2.4, 4.5, 2.6), "corpo", "y", 5, D=3)
            m.osso(canela, coxa, (sx * 5.5, 6, z0))
            elipsoide(m, canela, (sx * 5.5, 3.2, z0 + 0.5), (1.5, 3.4, 1.6), "corpo", "y", 4, D=3)
            m.cubo(canela, (sx * 5.5 - 1.6, 0, z0 - 1.2), (3.2, 1.2, 3.2), "escuro", D=4)   # casco
            espinho(m, canela, (sx * 5.5, 5.4, z0 + 1.8), 1.4, 0.7, "claro", (60, 0, 0))
    m.osso("cauda", "corpo", (0, 19, 14))
    for k in range(10):
        m.cubo("cauda", (-0.6 + k * 0.03, 19 - k * 0.9, 14 + k * 1.4), (1.2, 1.2, 1.6), "corpo", D=4)
    espinho(m, "cauda", (0, 10, 28), 2.0, 1.4, "claro", (100, 0, 0))
    m.salvar(pintores("vermelho", 37), {}, vis_w=5, vis_h=5)

    def q(f):
        a = TAU * f * 2
        p = {"corpo": (3 * S(a), 4 * S(TAU * f), 0), "cabeca": (12 * S(a + 1) - 6, 14 * S(TAU * f), 0),
             "mandibula": (12 + 12 * max(0, S(a * 1.5)), 0, 0), "cauda": (18 * S(a), 25 * S(TAU * f * 3), 0)}
        for nome, off in (("dianteira", 0), ("traseira", math.pi)):
            for lado, sx in (("d", 0), ("e", math.pi)):
                fa = a + off + sx
                p["perna_%s_%s" % (nome, lado)] = (24 * S(fa), 0, 0)
                p["canela_%s_%s" % (nome, lado)] = (-18 + 18 * S(fa + 1.2) if nome == "dianteira" else 18 - 18 * S(fa + 1.2), 0, 0)
        return p
    gravar_anim("entidade_vermelho", 3.0, q)


# ============================== LARANJA: Ofidiano, a serpente da Avareza ==============================

def ofidiano():
    m = base.Modelo("entidade_laranja")
    m.osso("root")
    segs = ["corpo_%d" % i for i in range(1, 17)]
    cadeia(m, segs, "root", (0, 8, -4), (0, 0, 4))
    for i, s in enumerate(segs):
        z0 = -4 + 4 * i
        r = 5.0 - max(0, i - 4) * 0.3
        elipsoide(m, s, (0, 8, z0 + 2), (r, r * 0.85, 2.3), "corpo", "z", 4, D=3)
        m.cubo(s, (-r * 0.75, 8 - r * 0.85 - 0.1, z0 + 0.4), (r * 1.5, 0.6, 3.2), "claro", D=3)    # escamas da barriga
        if i % 2 == 0:
            for sx in (-1, 1):
                m.cubo(s, (sx * r * 0.55 - 0.6, 8 + r * 0.55, z0 + 1), (1.2, 0.6, 1.6), "escuro", D=4)   # losangos do dorso
        m.cubo(s, (-0.25, 8 + r * 0.8, z0 + 0.4), (0.5, 1.8 - i * 0.06, 3.2), "membrana", D=3)           # barbatana do dorso
    m.osso("cabeca", "corpo_1", (0, 8, -4))
    elipsoide(m, "cabeca", (0, 9, -9), (5.2, 4.0, 5.5), "corpo", "z", 6, D=3)
    elipsoide(m, "cabeca", (0, 8.6, -15), (3.4, 2.6, 3.6), "corpo", "z", 5, D=3)     # focinho de dragão
    for sx in (-1, 1):                                                                # bigodes longos
        for k in range(6):
            m.cubo("cabeca", (sx * (3 + k * 1.1) - 0.2, 8 - k * 0.4, -16.5 + k * 1.2), (0.4, 0.4, 1.3), "claro", D=6)
    m.osso("mandibula", "cabeca", (0, 7, -5))
    elipsoide(m, "mandibula", (0, 6, -9.5), (3.8, 1.3, 5), "escuro", "z", 5, D=3)
    for sx in (-1, 1):
        m.cubo("cabeca", (sx * 2.7 - 0.7, 10.2, -13.2), (1.4, 1.1, 1.4), "olho", D=6)
        m.cubo("mandibula", (sx * 2.2 - 0.3, 6.6, -17.4), (0.6, 1.8, 0.6), "claro", D=6)    # presas
    # o capuz de espinhos e a coroa (a Avareza se coroa)
    for k in range(11):
        a = (k - 5) / 5 * 1.25
        espinho(m, "cabeca", (math.sin(a) * 5.5, 9 + math.cos(a) * 3.2, -5 + abs(a) * 0.8), 3.6 - abs(k - 5) * 0.25, 1.0, "claro",
                (-20, 0, -math.degrees(a)))
    for k in range(5):
        espinho(m, "cabeca", (-2.4 + k * 1.2, 12.4, -9 + abs(k - 2) * 0.6), 2.2 + (1 if k == 2 else 0), 0.8, "claro")
    m.osso("lingua", "mandibula", (0, 6.6, -13))
    for k in range(5):
        m.cubo("lingua", (-0.25, 6.4, -14 - k * 1.2), (0.5, 0.3, 1.3), "claro", D=6)
    for sx in (-1, 1):
        m.cubo("lingua", (sx * 0.5 - 0.15, 6.4, -20.2), (0.3, 0.3, 1.2), "claro", D=6)
    m.salvar(pintores("laranja", 53), {}, vis_w=6, vis_h=4)

    def q(f):
        p = onda(segs, f, 16, 0.6, 1, 1)
        for s, v in onda(segs, f, 4, 0.6, 0, 2).items():
            p[s] = (v[0], p[s][1], 0)
        p["cabeca"] = (5 * S(TAU * f * 2), -10 * S(TAU * f), 0)
        p["mandibula"] = (8 + 14 * max(0, S(TAU * f * 3)), 0, 0)
        p["lingua"] = (0, 20 * S(TAU * f * 8), 0)
        return p
    gravar_anim("entidade_laranja", 6.0, q)


# ============================== AZUL: Adara, a ave da Esperança ==============================

def adara():
    m = base.Modelo("entidade_azul")
    m.osso("root")
    m.osso("corpo", "root", (0, 12, 0))
    elipsoide(m, "corpo", (0, 12, 0), (4.2, 4.4, 8.5), "corpo", "z", 8, D=3)
    for k in range(8):                                                       # penas do peito
        m.cubo("corpo", (-3 + (k % 4) * 1.5, 8.2 + (k // 4) * 1.2, -6 + k), (1.4, 0.6, 2.4), "claro", D=4)
    for k in range(24):                                                      # penas do corpo (escamas de pena)
        a = (k % 6) / 6 * math.pi
        m.cubo("corpo", (math.cos(a) * 3.6 - 0.6, 12 + math.sin(a) * 3.8 - 0.3, -5 + (k // 6) * 3), (1.2, 0.6, 2.6), "claro" if k % 3 == 0 else "corpo", D=4)
    m.osso("pescoco", "corpo", (0, 14, -7))
    elipsoide(m, "pescoco", (0, 15.5, -10), (2.4, 2.6, 3.5), "corpo", "z", 4, D=3)
    m.osso("cabeca", "pescoco", (0, 16.5, -12.5))
    elipsoide(m, "cabeca", (0, 17, -14.5), (2.6, 2.6, 3), "corpo", "z", 5, D=3)
    for k in range(5):                                                       # bico curvo
        m.cubo("cabeca", (-0.9 + k * 0.1, 16.6 - k * 0.35, -17.2 - k * 1.0), (1.8 - k * 0.2, 1.2 - k * 0.12, 1.2), "claro", D=5)
    for sx in (-1, 1):
        m.cubo("cabeca", (sx * 2.1 - 0.5, 17.5, -16), (1.0, 0.9, 0.9), "olho", D=6)
    for k in range(6):                                                       # crista de penas
        m.cubo("cabeca", (-0.3, 19 + k * 0.5, -15 + k * 1.3), (0.6, 2.4 - k * 0.15, 0.8), "claro", D=5)
    # asas: braço → antebraço → mão, com as penas uma a uma
    for sx, lado in ((-1, "d"), (1, "e")):
        b1, b2, b3 = "asa1_" + lado, "asa2_" + lado, "asa3_" + lado
        m.osso(b1, "corpo", (sx * 3.5, 14, -3))
        for k in range(4):
            m.cubo(b1, (sx * (3.5 + k * 2) - (2.2 if sx < 0 else 0), 13.6, -4 + k * 0.2), (2.2, 1.0, 4.5 + k * 0.5), "corpo", D=3)
        for k in range(5):
            m.cubo(b1, (sx * (4 + k * 1.6) - (1.4 if sx < 0 else 0), 13.2, 1 + k * 0.3), (1.4, 0.5, 4.5 + k * 0.4), "membrana", D=3)
        m.osso(b2, b1, (sx * 11.5, 14, -2.5))
        for k in range(4):
            m.cubo(b2, (sx * (11.5 + k * 2) - (2.2 if sx < 0 else 0), 13.7, -2.5 + k * 0.4), (2.2, 0.9, 3.5), "corpo", D=3)
        for k in range(6):
            m.cubo(b2, (sx * (11.6 + k * 1.4) - (1.2 if sx < 0 else 0), 13.3, 1 + k * 0.2), (1.2, 0.45, 6 + k * 0.3), "membrana", D=3)
        for fila in range(2):                                                # penas de cobertura (2 fileiras)
            for k in range(7):
                m.cubo(b1, (sx * (3.8 + k * 1.1) - (1.0 if sx < 0 else 0), 13.9 + fila * 0.15, -2.5 + fila * 2.2 + (k % 2) * 0.3), (1.0, 0.3, 2.4), "claro" if fila else "corpo", D=4)
                m.cubo(b2, (sx * (11.7 + k * 1.1) - (1.0 if sx < 0 else 0), 13.95 + fila * 0.15, -1.6 + fila * 2.0 + (k % 2) * 0.3), (1.0, 0.3, 2.2), "claro" if fila else "corpo", D=4)
        m.osso(b3, b2, (sx * 19.5, 14, -1))
        for k in range(8):                                                   # primárias: as penas longas da ponta
            m.cubo(b3, (sx * (19.5 + k * 1.3) - (1.0 if sx < 0 else 0), 13.4 - k * 0.05, -1 + k * 0.6), (1.0, 0.4, 7 + k * 0.6), "membrana", D=3)
            m.cubo(b3, (sx * (19.6 + k * 1.3) - (0.4 if sx < 0 else 0), 13.85, -0.6 + k * 0.6), (0.4, 0.3, 6.5 + k * 0.6), "claro", D=4)
    m.osso("cauda", "corpo", (0, 12, 8))
    for k in range(13):                                                      # o leque da cauda
        a = (k - 6) * 0.15
        m.cubo("cauda", (math.sin(a) * 4 - 0.7, 11.6, 8), (1.2, 0.5, 12 - abs(k - 6) * 0.6), "membrana", rot=(0, math.degrees(a), 0), pivo=(0, 11.6, 8), D=3)
    for sx, lado in ((-1, "d"), (1, "e")):                                   # patas recolhidas
        m.cubo("corpo", (sx * 1.6 - 0.4, 7, 2), (0.8, 2.5, 0.8), "escuro", D=5)
        m.cubo("corpo", (sx * 1.6 - 0.8, 6.4, 1), (1.6, 0.6, 2.2), "claro", D=5)
    m.salvar(pintores("azul", 71), {}, vis_w=7, vis_h=4)

    def q(f):
        a = TAU * f * 2
        p = {"corpo": (4 * S(a + 1.5), 0, 3 * S(TAU * f)), "pescoco": (-6 * S(a + 1.5), 0, 0), "cabeca": (5 * S(a + 2), 8 * S(TAU * f), 0),
             "cauda": (8 * S(a + 2.5), 0, 6 * S(TAU * f))}
        for lado, sx in (("d", 1), ("e", -1)):
            p["asa1_" + lado] = (0, 0, sx * 38 * S(a))
            p["asa2_" + lado] = (0, 0, sx * 22 * S(a - 0.6))
            p["asa3_" + lado] = (0, 0, sx * 16 * S(a - 1.2))
        return p
    gravar_anim("entidade_azul", 3.0, q)


# ============================== ÍNDIGO: o Prosélito, a lula da Compaixão ==============================

def proselito():
    m = base.Modelo("entidade_indigo")
    m.osso("root")
    m.osso("manto", "root", (0, 12, 0))
    elipsoide(m, "manto", (0, 21, 0), (6, 12, 6), "corpo", "y", 10, D=3)
    for k in range(10):                                                     # manchas
        a = k * 0.62
        m.cubo("manto", (math.cos(a) * 5.4 - 0.6, 16 + k * 1.3, math.sin(a) * 5.4 - 0.6), (1.2, 1.2, 1.2), "claro", D=5)
    for sx, lado in ((-1, "d"), (1, "e")):                                  # nadadeiras no topo
        m.osso("aleta_" + lado, "manto", (sx * 4, 29, 0))
        for k in range(5):
            m.cubo("aleta_" + lado, (sx * (4 + k * 1.5) - (1.5 if sx < 0 else 0), 28 + k * 0.6, -3 + k * 0.5), (1.5, 3.5 - k * 0.5, 6 - k), "membrana", D=3)
        m.cubo("manto", (sx * 4.6 - 1.1, 12, -4.6), (2.2, 2.2, 1.4), "olho", D=6)
        m.cubo("manto", (sx * 4.6 - 1.4, 14.3, -4.4), (2.8, 0.6, 1.2), "escuro", D=4)
    # oito tentáculos, 5 segmentos cada, em volta da boca
    tent = []
    for j in range(8):
        a = j * TAU / 8
        x, z = math.cos(a) * 3, math.sin(a) * 3
        nomes = ["tent%d_%d" % (j, k) for k in range(7)]
        tent.append(nomes)
        for k, n in enumerate(nomes):
            m.osso(n, "manto" if k == 0 else nomes[k - 1], (x, 9 - k * 3.4, z))
            r = 1.5 - k * 0.17
            m.cubo(n, (x - r, 9 - k * 3.4 - 3.5, z - r), (2 * r, 3.6, 2 * r), "corpo", D=3)
            for v in range(3):   # ventosas no lado de dentro
                m.cubo(n, (x - r * 0.35 + math.cos(a) * -r, 9 - k * 3.4 - 0.9 - v * 1.1, z - r * 0.35 + math.sin(a) * -r), (r * 0.7, 0.5, r * 0.7), "claro", D=5)
            m.cubo(n, (x - r * 0.6 + math.cos(a) * r * 0.9, 9 - k * 3.4 - 3.2, z - r * 0.6 + math.sin(a) * r * 0.9), (r * 1.2, 2.8, r * 0.4), "escuro", D=4)
    m.cubo("manto", (-2.6, 8.4, -2.6), (5.2, 1.4, 5.2), "escuro", D=3)
    for j in range(2):                                                       # os dois tentáculos longos de caça
        x = -1.2 if j == 0 else 1.2
        nomes = ["caca%d_%d" % (j, k) for k in range(8)]
        tent.append(nomes)
        for k, n in enumerate(nomes):
            m.osso(n, "manto" if k == 0 else nomes[k - 1], (x, 9 - k * 3.2, -1.5))
            m.cubo(n, (x - 0.5, 9 - k * 3.2 - 3.3, -2.0), (1.0, 3.4, 1.0), "corpo", D=4)
            if k >= 6:
                m.cubo(n, (x - 1.1, 9 - k * 3.2 - 3.0, -2.6), (2.2, 2.6, 2.2), "claro", D=4)   # a clava com ventosas
    for k in range(16):                                                      # cristas e manchas no manto
        a = k * TAU / 16
        m.cubo("manto", (math.cos(a) * 5.7 - 0.3, 14 + (k % 4) * 3.2, math.sin(a) * 5.7 - 0.3), (0.6, 2.4, 0.6), "escuro", D=5)
    m.salvar(pintores("indigo", 89), {}, vis_w=5, vis_h=6)

    def q(f):
        a = TAU * f
        p = {"manto": (6 * S(a * 2), 15 * S(a), 0), "aleta_d": (0, 0, 25 * S(a * 4)), "aleta_e": (0, 0, -25 * S(a * 4))}
        for j, nomes in enumerate(tent):
            ang = j * TAU / 8 if j < 8 else math.pi / 2
            for k, n in enumerate(nomes):
                w = 14 * S(a * 2 - k * 0.8 + j) + (8 if k == 0 else 0) + (22 if k >= 4 else 0)   # a ponta se enrola
                p[n] = (w * math.cos(ang + math.pi / 2), 0, w * math.sin(ang + math.pi / 2))
        return p
    gravar_anim("entidade_indigo", 4.0, q)


# ============================== VIOLETA: o Predador, o caçador alado do Amor ==============================

def predador():
    m = base.Modelo("entidade_violeta")
    m.osso("root")
    m.osso("corpo", "root", (0, 14, 0))
    elipsoide(m, "corpo", (0, 15, -2), (6.5, 6, 9), "corpo", "z", 7, D=3)
    elipsoide(m, "corpo", (0, 14, 9), (5.2, 5, 6), "corpo", "z", 5, D=3)
    for k in range(14):                                                    # placas e espinhos nas costas
        espinho(m, "corpo", (0, 20.6 - abs(k - 5) * 0.35, -10 + k * 1.6), 2.6 - abs(k - 4) * 0.12, 1.2, "claro" if k % 2 else "escuro", (-30, 0, 0))
    for k in range(10):                                                    # escamas grossas nos flancos
        for sx in (-1, 1):
            m.cubo("corpo", (sx * 6.2 - 0.5, 12 + (k % 3) * 2.2, -8 + k * 1.8), (1.0, 1.4, 1.5), "escuro", D=4)
    pesc = ["pescoco_%d" % i for i in range(1, 4)]
    cadeia(m, pesc, "corpo", (0, 17, -10), (0, 1.0, -3.4))
    for i, s_ in enumerate(pesc):
        elipsoide(m, s_, (0, 17.4 + i * 1.0, -11.7 - i * 3.4), (4 - i * 0.3, 4 - i * 0.3, 2.2), "corpo", "z", 3, D=3)
    # a cabeça grande de lagarto: crânio, focinho, olhos fundos, chifres para trás e a crista (franja) de espinhos
    m.osso("cabeca", pesc[-1], (0, 20, -20))
    elipsoide(m, "cabeca", (0, 20.5, -24), (5, 4.4, 5.5), "corpo", "z", 6, D=3)
    elipsoide(m, "cabeca", (0, 19, -31), (3.4, 2.8, 4.2), "corpo", "z", 5, D=3)
    m.osso("mandibula", "cabeca", (0, 17.5, -22))
    elipsoide(m, "mandibula", (0, 16.2, -28), (3.6, 1.5, 6.5), "escuro", "z", 6, D=3)
    for k in range(9):                                                     # dentes em cima e embaixo
        m.cubo("cabeca", (-2.6 + k * 0.65, 16.6, -34.2 + abs(k - 4) * 0.9), (0.35, 1.0, 0.35), "claro", D=6)
        m.cubo("mandibula", (-2.4 + k * 0.6, 17.0, -33.8 + abs(k - 4) * 0.9), (0.3, 0.9, 0.3), "claro", D=6)
    for sx in (-1, 1):
        m.cubo("cabeca", (sx * 3.2 - 0.6, 21.6, -28.4), (1.2, 1.0, 1.0), "olho", D=6)
        m.cubo("cabeca", (sx * 3.2 - 0.9, 22.8, -28.6), (1.8, 0.8, 1.6), "escuro", D=4)    # sobrancelha óssea
        for k in range(9):                                                 # chifres longos curvos para trás
            a_ = k / 8
            m.cubo("cabeca", (sx * (3.6 + a_ * 3) - (1.1 - a_ * 0.6) / 2, 23.5 + math.sin(a_ * 2.2) * 3.5, -23 + a_ * 9),
                   (1.1 - a_ * 0.6 + 0.2, 1.1 - a_ * 0.6 + 0.2, 1.4), "claro", D=4)
    for k in range(11):                                                    # a franja de espinhos atrás da cabeça
        a_ = (k - 5) / 5 * 1.3
        espinho(m, "cabeca", (math.sin(a_) * 5.2, 21 + math.cos(a_) * 3.6, -19.5), 2.8 - abs(k - 5) * 0.15, 0.9, "escuro", (-40, 0, -math.degrees(a_)))
    # quatro patas fortes com coxa, canela e garras
    for z0, nome in ((-6, "dianteira"), (9, "traseira")):
        for sx, lado in ((-1, "d"), (1, "e")):
            coxa, canela = "perna_%s_%s" % (nome, lado), "canela_%s_%s" % (nome, lado)
            m.osso(coxa, "corpo", (sx * 5.5, 13, z0))
            elipsoide(m, coxa, (sx * 6.5, 10, z0), (2.4, 4, 2.4), "corpo", "y", 4, D=3)
            m.osso(canela, coxa, (sx * 7, 6.5, z0))
            elipsoide(m, canela, (sx * 7, 3.6, z0 - 0.4), (1.5, 3.2, 1.6), "corpo", "y", 3, D=3)
            for g in range(3):
                m.cubo(canela, (sx * 7 - 1.0 + g * 0.8, 0, z0 - 3), (0.45, 0.6, 2.2), "claro", D=6)
    rabo = ["cauda_%d" % i for i in range(1, 8)]
    cadeia(m, rabo, "corpo", (0, 14, 14), (0, -0.5, 3.6))
    for i, s_ in enumerate(rabo):
        r = 3.4 - i * 0.42
        elipsoide(m, s_, (0, 14 - i * 0.5, 15.8 + i * 3.6), (r, r * 0.9, 2), "corpo", "z", 2, D=3)
        espinho(m, s_, (0, 14 - i * 0.5 + r * 0.8, 15.6 + i * 3.6), 1.4 - i * 0.12, 0.7, "escuro", (-40, 0, 0))
    m.salvar(pintores("violeta", 97), {}, vis_w=6, vis_h=5)

    def q(f):
        a_ = TAU * f * 2
        p = {"corpo": (2 * S(a_), 5 * S(TAU * f), 0), "cabeca": (8 * S(a_ + 1) - 4, 12 * S(TAU * f), 0),
             "mandibula": (8 + 16 * max(0, S(TAU * f * 3)), 0, 0)}
        p.update(onda(pesc, f, 6, 0.6, 1, 1))
        p.update(onda(rabo, f, 18, 0.6, 1, 1))
        for nome, off in (("dianteira", 0), ("traseira", math.pi)):
            for lado, sx in (("d", 0), ("e", math.pi)):
                fa = a_ + off + sx
                p["perna_%s_%s" % (nome, lado)] = (22 * S(fa), 0, 0)
                p["canela_%s_%s" % (nome, lado)] = (-14 + 14 * S(fa + 1.2) if nome == "dianteira" else 14 - 14 * S(fa + 1.2), 0, 0)
        return p
    gravar_anim("entidade_violeta", 3.4, q)


if __name__ == "__main__":
    escolha = sys.argv[1:] or ["verde", "amarelo", "vermelho", "laranja", "azul", "indigo", "violeta"]
    feitos = {"verde": ion, "amarelo": parallax, "vermelho": acougueiro, "laranja": ofidiano, "azul": adara, "indigo": proselito, "violeta": predador}
    for t in escolha:
        feitos[t]()

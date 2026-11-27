"""
0.59.3: as animações do Necron em quadros-chave (formato do Blockbench / GeckoLib), geradas daqui:
    python3 ferramentas/anim_necron.py  ->  src/main/resources/assets/lanterna/animations/entity/necron.animation.json

O arquivo abre no Blockbench (junto com geo/entity/necron.geo.json) para ver e ajustar. O mod lê o arquivo e mistura
as animações conforme o estado (client/necron/AnimNecron.java + PoseNecron.java).

Convenção aqui (graus): X positivo leva braço/perna PARA A FRENTE e para cima; num osso que aponta para cima (corpo,
cabeça, haste) inclina o topo PARA TRÁS. Joelho dobrando = X negativo na canela; cotovelo dobrando = X positivo no
antebraço. O arquivo grava como o Blockbench (X e Y invertidos).
A foice é filha da mão direita: cada pose diz o ângulo da HASTE no mundo e a foice recebe o que falta.
"""
import json, math, os

RAIZ = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "lanterna", "animations", "entity")
FARRAPOS = ["farrapo_%d" % i for i in range(1, 13)]
HASTE_PARADO = -3


def foice(p, haste):
    """Completa a foice para a haste ficar no ângulo pedido (soma da cadeia do braço direito)."""
    s = sum(p.get(b, (0, 0, 0))[0] for b in ("braco_d", "antebraco_d", "mao_d"))
    p["foice"] = (haste - s, p.get("foice", (0, 0, 0))[1], p.get("foice", (0, 0, 0))[2])
    return p


def parado(f):
    """A pose parada num ponto do ciclo de respiração (f = 0..1)."""
    r = math.sin(f * 2 * math.pi)
    p = {
        "corpo": (-1.5 + 1.8 * r, 0, 0.6 * math.sin(f * 2 * math.pi + 1)),
        "cabeca": (2 - 1.5 * r, 4 * math.sin(f * 2 * math.pi * 0.5), 0),
        "mandibula": (5 + 3 * max(0, math.sin(f * 4 * math.pi)), 0, 0),
        "capuz": (1.5 * r, 0, 0),
        "saia": (1.5 * r, 0, 0.6 * r),
        # a mão direita apoiada na foice como numa bengala, o cotovelo dobrado
        "ombro_d": (0, 0, 2), "braco_d": (20 + 1.5 * r, 0, 7), "antebraco_d": (22, 0, 0), "mao_d": (-12, 0, 0), "dedos_d": (35, 0, 0),
        "ombro_e": (0, 0, -2), "braco_e": (3 + 2 * r, 0, -9), "antebraco_e": (14 + 3 * r, 0, 0), "mao_e": (-4, 0, 0), "dedos_e": (18 + 6 * r, 0, 0),
        # o peso numa perna só
        "perna_d": (-3, 0, 2), "canela_d": (2, 0, 0), "pe_d": (1, 0, 0),
        "perna_e": (4, 0, -1), "canela_e": (-8, 0, 0), "pe_e": (4, 0, 0),
    }
    for i, b in enumerate(FARRAPOS):
        p[b] = (10 * math.sin(f * 2 * math.pi * 2 + i * 1.3), 0, 5 * math.sin(f * 2 * math.pi + i))
    return foice(p, HASTE_PARADO)


def mistura(a, b, k):
    out = dict(a)
    for n, v in b.items():
        u = a.get(n, (0, 0, 0))
        out[n] = tuple(u[i] + (v[i] - u[i]) * k for i in range(3))
    return out


ANIMS = {}


def anim(nome, dur, loop, quadros, posicoes=None):
    """quadros: lista de (tempo, pose). posicoes: lista de (tempo, {osso: (x,y,z)})."""
    ossos = {}
    for t, p in quadros:
        for b, v in p.items():
            ossos.setdefault(b, {}).setdefault("rotation", {})["%.3f" % t] = {
                "post": [round(-v[0], 2), round(-v[1], 2), round(v[2], 2)], "lerp_mode": "catmullrom"}
    for t, p in posicoes or []:
        for b, v in p.items():
            ossos.setdefault(b, {}).setdefault("position", {})["%.3f" % t] = {
                "post": [round(v[0], 2), round(v[1], 2), round(v[2], 2)], "lerp_mode": "catmullrom"}
    ANIMS["animation.necron." + nome] = {"loop": loop, "animation_length": dur, "bones": ossos}


# ---------------- parado (4 s, respira; a foice como bengala) ----------------
anim("parado", 4.0, True, [(i, parado(i / 4)) for i in range(5)])


# ---------------- andar (1,2 s por passada; pernas de verdade, a bengala acompanha) ----------------
def andar(f):
    a = 2 * math.pi * f
    p = parado(0)

    def perna(fase):
        coxa = 24 * math.cos(fase)                      # para a frente na batida do calcanhar
        # o joelho dobra muito no balanço (perna indo para a frente) e pouco no apoio
        balanco = max(0, math.sin(fase - math.pi))      # 0..1 na segunda metade
        joelho = -6 - 52 * balanco ** 1.4 - 8 * max(0, math.sin(fase))
        pe = 10 * math.cos(fase) - 14 * max(0, math.sin(fase - 2.6))
        return coxa, joelho, pe
    for lado, fase in (("d", a), ("e", a + math.pi)):
        c, j, pe = perna(fase)
        p["perna_" + lado] = (c, 0, 0)
        p["canela_" + lado] = (j, 0, 0)
        p["pe_" + lado] = (pe, 0, 0)
    p["corpo"] = (-7 + 1.5 * math.cos(2 * a), 6 * math.cos(a), 2 * math.cos(a))
    p["cabeca"] = (6 - 1.5 * math.cos(2 * a), -4 * math.cos(a), 0)
    p["saia"] = (-4 + 4 * math.cos(2 * a), -5 * math.cos(a), 0)
    # o braço esquerdo balança contra a perna esquerda; o direito leva a bengala à frente junto com a perna esquerda
    p["braco_e"] = (-20 * math.cos(a + math.pi), 0, -8)
    p["antebraco_e"] = (18 + 14 * max(0, -math.cos(a + math.pi)), 0, 0)
    p["braco_d"] = (24 + 9 * math.cos(a + math.pi), 0, 8)
    p["antebraco_d"] = (22 + 6 * math.cos(a + math.pi), 0, 0)
    for i, b in enumerate(FARRAPOS):
        p[b] = (-14 + 16 * math.sin(2 * a - i * 0.8), 0, 6 * math.sin(a - i))
    p["capuz"] = (-6 + 2 * math.cos(2 * a), 0, 0)
    return foice(p, HASTE_PARADO + 6 * math.cos(a + math.pi))


anim("andar", 1.2, True, [(1.2 * i / 8, andar(i / 8)) for i in range(9)],
     [(1.2 * i / 8, {"root": (0, -0.5 + 0.5 * math.cos(4 * math.pi * i / 8), 0)}) for i in range(9)])


# ---------------- voar (2 s; o corpo é deitado por fora; pernas para trás, mão esquerda à frente) ----------------
def voar(f):
    a = 2 * math.pi * f
    p = parado(0)
    p.update({"cabeca": (55 + 2 * math.sin(a), 3 * math.sin(a), 0), "capuz": (-18 + 4 * math.sin(3 * a), 0, 0),
              "mandibula": (16 + 3 * math.sin(a), 0, 0), "saia": (-8 + 3 * math.sin(2 * a), 0, 2 * math.sin(a)),
              "braco_e": (160 + 3 * math.sin(a), 0, -5), "antebraco_e": (8, 0, 0), "mao_e": (10, 0, 0), "dedos_e": (-10, 0, 0),
              "braco_d": (-12 + 2 * math.sin(a), 0, 6), "antebraco_d": (12, 0, 0), "mao_d": (-5, 0, 0),
              "perna_d": (-22 + 5 * math.sin(a), 0, 3), "canela_d": (-38 + 8 * math.sin(a + 1), 0, 0), "pe_d": (-35, 0, 0),
              "perna_e": (-30 + 5 * math.sin(a + 2), 0, -3), "canela_e": (-30 + 8 * math.sin(a + 3), 0, 0), "pe_e": (-35, 0, 0)})
    for i, b in enumerate(FARRAPOS):
        p[b] = (-17 + 18 * math.sin(3 * a - i * 0.9), 0, 7 * math.sin(2 * a + i * 2.1))
    return foice(p, 8)


anim("voar", 2.0, True, [(2.0 * i / 8, voar(i / 8)) for i in range(9)])


# ---------------- golpe 1 (0,8 s): ergue a foice por cima do ombro e desce cortando à frente ----------------
def g1_prep():
    p = parado(0)
    p.update({"corpo": (10, 14, -3), "cabeca": (-4, 8, 0), "saia": (4, 8, 0), "mandibula": (8, 0, 0),
              "ombro_d": (0, 0, 6), "braco_d": (168, 6, 10), "antebraco_d": (42, 0, 0), "mao_d": (12, 0, 0), "dedos_d": (40, 0, 0),
              "ombro_e": (0, 0, -6), "braco_e": (140, -18, 12), "antebraco_e": (55, 0, 0), "mao_e": (5, 0, 0), "dedos_e": (40, 0, 0),
              "perna_d": (8, 0, 3), "canela_d": (-12, 0, 0), "perna_e": (-6, 0, -3), "canela_e": (-4, 0, 0)})
    for i, b in enumerate(FARRAPOS):
        p[b] = (-12, 0, 6 * (1 if i % 2 else -1))
    return foice(p, 45)


def g1_corte(passa=0.0):
    p = parado(0)
    p.update({"corpo": (-17 - 4 * passa, -12 - 4 * passa, 2), "cabeca": (10, -6, 0), "saia": (-8, -8, 0), "mandibula": (26, 0, 0),
              "ombro_d": (0, 0, -2), "braco_d": (52 - 8 * passa, -10, -4), "antebraco_d": (6, 0, 0), "mao_d": (-12 - 4 * passa, 0, 0), "dedos_d": (45, 0, 0),
              "ombro_e": (0, 0, 3), "braco_e": (58 - 6 * passa, -26, 14), "antebraco_e": (14, 0, 0), "mao_e": (-8, 0, 0), "dedos_e": (45, 0, 0),
              "perna_d": (-16, 0, 3), "canela_d": (-12, 0, 0), "pe_d": (10, 0, 0),
              "perna_e": (28, 0, -3), "canela_e": (-34, 0, 0), "pe_e": (8, 0, 0)})
    for i, b in enumerate(FARRAPOS):
        p[b] = (18, 0, -8 * (1 if i % 2 else -1))
    return foice(p, -110 - 10 * passa)


anim("golpe1", 0.8, False, [(0, parado(0)), (0.22, g1_prep()), (0.3, g1_prep()), (0.4, g1_corte()), (0.46, g1_corte(1)),
                            (0.56, g1_corte(0.6)), (0.8, parado(0))],
     [(0, {"root": (0, 0, 0)}), (0.3, {"root": (0, 0.3, 0)}), (0.46, {"root": (0, -1.4, 0)}), (0.8, {"root": (0, 0, 0)})])


# ---------------- golpe 2 (0,8 s): o revés, a foice deitada varrendo na altura da cintura ----------------
def g2(c):
    p = parado(0)
    p.update({"corpo": (-6, -46 + 98 * c, 0), "cabeca": (4, 24 - 50 * c, 0), "saia": (0, 16 - 36 * c, 0), "mandibula": (20, 0, 0),
              "braco_d": (72, 30 - 70 * c, 6), "antebraco_d": (22 - 12 * c, 0, 0), "mao_d": (-4, 0, 0), "dedos_d": (45, 0, 0),
              "braco_e": (66, 28 - 72 * c, 8 - 14 * c), "antebraco_e": (32 - 14 * c, 0, 0), "dedos_e": (45, 0, 0),
              "perna_d": (8, 0, 8), "canela_d": (-14, 0, 0), "perna_e": (-8, 0, -8), "canela_e": (-14, 0, 0)})
    for i, b in enumerate(FARRAPOS):
        p[b] = (8, 0, 26 * (0.5 - c))
    return foice(p, -84)


anim("golpe2", 0.8, False, [(0, parado(0)), (0.24, g2(0)), (0.3, g2(0)), (0.44, g2(1)), (0.5, g2(1.08)), (0.58, g2(1)), (0.8, parado(0))],
     [(0, {"root": (0, 0, 0)}), (0.3, {"root": (0, -1.0, 0)}), (0.5, {"root": (0, -1.2, 0)}), (0.8, {"root": (0, 0, 0)})])


# ---------------- conjurar (1 s): a mão esquerda se ergue aberta ----------------
def conj(tremor):
    p = parado(0)
    p.update({"braco_e": (108, 14, -16 + tremor), "antebraco_e": (18, 0, 0), "mao_e": (-22, 0, 0), "dedos_e": (-25, 0, 0),
              "cabeca": (-8, 6, 0), "mandibula": (28, 0, 0), "corpo": (-5, -9, 0), "capuz": (-4, 0, 0)})
    return p


anim("conjurar", 1.0, False, [(0, parado(0)), (0.25, conj(0)), (0.5, conj(3)), (0.72, conj(-2)), (1.0, parado(0))])


# ---------------- surgir (1,3 s): curvado no chão, ele se ergue abrindo os braços ----------------
def curvado():
    p = parado(0)
    p.update({"corpo": (-38, 0, 0), "cabeca": (30, 0, 0), "braco_d": (25, 0, 6), "antebraco_d": (30, 0, 0),
              "braco_e": (25, 0, -6), "antebraco_e": (30, 0, 0), "perna_d": (48, 0, 4), "canela_d": (-92, 0, 0), "pe_d": (40, 0, 0),
              "perna_e": (52, 0, -4), "canela_e": (-96, 0, 0), "pe_e": (40, 0, 0)})
    return foice(p, 30)


def aberto():
    p = parado(0)
    p.update({"corpo": (6, 0, 0), "cabeca": (-16, 0, 0), "mandibula": (32, 0, 0), "braco_d": (25, 0, 62), "antebraco_d": (10, 0, 0),
              "braco_e": (25, 0, -70), "antebraco_e": (10, 0, 0), "dedos_e": (-20, 0, 0)})
    for i, b in enumerate(FARRAPOS):
        p[b] = (-28, 0, 10 * (1 if i % 2 else -1))
    return foice(p, HASTE_PARADO)


anim("surgir", 1.3, False, [(0, curvado()), (0.35, curvado()), (0.75, aberto()), (0.95, aberto()), (1.3, parado(0))],
     [(0, {"root": (0, -6.5, 0)}), (0.35, {"root": (0, -6.5, 0)}), (0.75, {"root": (0, 0.6, 0)}), (1.3, {"root": (0, 0, 0)})])


# ---------------- alimentar (4,6 s): pega uma caveira, leva até a boca e drena ----------------
def segura(tremor=0.0):
    p = parado(0)
    # o cotovelo dobrado leva a caveira para diante do rosto, a uns 6 pixels da boca
    p.update({"ombro_e": (0, 0, -4), "braco_e": (38, -24, -4), "antebraco_e": (98 + tremor, 0, 0), "mao_e": (6, 0, 0), "dedos_e": (30, 0, 0),
              "caveira": (-10, 180, 0), "cabeca": (-6, -14, 0), "mandibula": (34 + 6 * tremor, 0, 0), "corpo": (-6, -10, 0), "capuz": (-3, 0, 0)})
    return p


def estende():
    p = parado(0)
    p.update({"braco_e": (32, 0, -26), "antebraco_e": (24, 0, 0), "mao_e": (-10, 0, 0), "dedos_e": (-15, 0, 0), "cabeca": (8, -12, 0)})
    return p


anim("alimentar", 4.6, False, [(0, parado(0)), (0.55, estende()), (0.8, estende()), (1.6, segura()), (2.2, segura(2)), (2.8, segura(-2)),
                               (3.4, segura(2)), (3.85, segura()), (4.15, estende()), (4.6, parado(0))])


# ---------------- ajoelhar (3 s, em laço): de joelhos, a foice caída para trás ----------------
def joelhos(f):
    r = math.sin(f * 2 * math.pi)
    p = parado(f)
    p.update({"corpo": (-16 + 1.5 * r, 0, 0), "cabeca": (30 + 2 * r, 0, 0), "mandibula": (16, 0, 0),
              "perna_d": (-6, 0, 2), "canela_d": (-96, 0, 0), "pe_d": (48, 0, 0),
              "perna_e": (82, 0, -2), "canela_e": (-86, 0, 0), "pe_e": (4, 0, 0),
              "braco_d": (12, 0, 18), "antebraco_d": (8, 0, 0), "braco_e": (8, 0, -25), "antebraco_e": (10, 0, 0)})
    return foice(p, 95)


anim("ajoelhar", 3.0, True, [(3.0 * i / 4, joelhos(i / 4)) for i in range(5)],
     [(0, {"root": (0, -7.0, 0)}), (3.0, {"root": (0, -7.0, 0)})])


# ---------------- cair (1 s, em laço): a túnica sobe, braços abertos ----------------
def caindo(f):
    a = 2 * math.pi * f
    p = parado(0)
    p.update({"braco_e": (23, 0, -52 + 4 * math.sin(a)), "braco_d": (8, 0, 30), "saia": (-12, 0, 0), "capuz": (-17, 0, 0),
              "perna_d": (12 + 5 * math.sin(a), 0, 4), "canela_d": (-24, 0, 0), "perna_e": (-6 + 5 * math.sin(a + 2), 0, -4), "canela_e": (-30, 0, 0)})
    for i, b in enumerate(FARRAPOS):
        p[b] = (-34 + 9 * math.sin(5 * a + i), 0, 0)
    return foice(p, 20)


anim("cair", 1.0, True, [(i / 4, caindo(i / 4)) for i in range(5)])

# os nomes que os controladores do GeckoLib da entidade ainda chamam (vazios: quem anima é o PoseNecron)
ANIMS["misc.idle"] = {"loop": True, "animation_length": 1.0, "bones": {}}
ANIMS["attack.swing"] = {"loop": False, "animation_length": 0.8, "bones": {}}
os.makedirs(RAIZ, exist_ok=True)
with open(os.path.join(RAIZ, "necron.animation.json"), "w") as f:
    json.dump({"format_version": "1.8.0", "animations": ANIMS}, f, indent=1)
print("necron: %d animações" % len(ANIMS))

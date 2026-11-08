# Trajes de ENERGIA (estilo "segunda pele de energia" de filme): um por Tropa.
# Base escura colada ao corpo; placas em forma de músculo na cor da Tropa, separadas por sulcos;
# linhas de energia acesas contornando os músculos principais; emblema grande no peito; máscara dominó.
import sys
sys.path.insert(0, "/home/claude/unif2"); sys.path.insert(0, "/home/claude/especies"); sys.path.insert(0, "/home/claude/lore3d")
from traje import Traje, hx, mix, lt, dk
from motivos import emblema3d, anatomia, mascara
from species import EMBLEMAS
import designs

PAL = {
    "verde":    dict(c="#27B356", c2="#14763A", esc="#08150C", glow="#B4FFC9", lente="#F2FFF6"),
    "amarelo":  dict(c="#EEC417", c2="#A88306", esc="#161204", glow="#FFF6B0", lente="#FFFBE6"),
    "vermelho": dict(c="#CF1E1E", c2="#850C0C", esc="#160505", glow="#FFA898", lente="#FFE6E0"),
    "laranja":  dict(c="#EE7714", c2="#A24A06", esc="#180C03", glow="#FFD6A6", lente="#FFF2E2"),
    "azul":     dict(c="#2B7BE2", c2="#144894", esc="#050C18", glow="#B2DAFF", lente="#EEF7FF"),
    "indigo":   dict(c="#5434B4", c2="#2E1C70", esc="#0A0716", glow="#CDB6FF", lente="#F3EEFF"),
    "violeta":  dict(c="#C236D8", c2="#7A1A8E", esc="#140516", glow="#FAB8FF", lente="#FFEEFF"),
    "preto":    dict(c="#3A3B46", c2="#22232B", esc="#060608", glow="#E4EAFF", lente="#FFFFFF"),
    "branco":   dict(c="#F4F3EE", c2="#C8C6BC", esc="#7E7D78", glow="#FFE59A", lente="#FFF8DC"),
}
NOMES = dict(verde="Energia da Vontade", amarelo="Energia do Medo", vermelho="Energia da Fúria", laranja="Energia da Avareza",
             azul="Energia da Esperança", indigo="Energia da Compaixão", violeta="Energia do Amor", preto="Energia da Morte",
             branco="Energia da Vida")

def fechar(p): return list(p) + [p[0]]

def criar(tropa):
    P = dict(PAL[tropa]); P["base"] = P["esc"]
    T = Traje(f"traje_{tropa}_energia", P, seed=7)
    c, c2, esc, gl = P["c"], P["c2"], P["esc"], P["glow"]
    sulco = dk(esc, 0.4) if tropa != "branco" else dk(esc, 0.25)
    gl_emb = gl; gl = mix(lt(c, 0.2), hx(gl), 0.45)   # linhas: luz na própria cor da Tropa (o branco puro fica no emblema e nos olhos)
    anatomia(T, 1.15)

    fibra = mix(c, hx(gl), 0.42)
    import math, numpy as np
    from traje import face_size, sem_map
    def painel(part, face, pts, col=c, luz=True, rz=0.1, grad=0.16, sym=False, fib=90, passo=1.0):
        versoes = [(pts, fib)]
        if sym:
            w = T.F(part, face).w
            versoes.append(([(w - a, b) for a, b in pts], 180 - fib))
        for pp, ang in versoes:
            m = T.draw(part, face, "poly", pp, col=col, grad=grad, tag=1, rz=rz, rz_mode="max")
            T.draw(part, face, "line", fechar(pp), 0.25, col=sulco)
            if not luz: continue
            # fibras de energia dentro do músculo (acesas), longe da borda
            mi = m.copy(); mi[1:] &= m[:-1]; mi[:-1] &= m[1:]; mi[:, 1:] &= m[:, :-1]; mi[:, :-1] &= m[:, 1:]
            mi2 = mi.copy(); mi2[1:] &= mi[:-1]; mi2[:-1] &= mi[1:]; mi2[:, 1:] &= mi[:, :-1]; mi2[:, :-1] &= mi[:, 1:]
            xs = [a for a, b in pp]; ys = [b for a, b in pp]; cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
            dx, dy = math.cos(math.radians(ang)), math.sin(math.radians(ang)); nx, ny = -dy, dx
            R = max(max(xs) - min(xs), max(ys) - min(ys)) + 1
            k = -R
            while k <= R:
                p0 = (cx + nx * k - dx * R, cy + ny * k - dy * R); p1 = (cx + nx * k + dx * R, cy + ny * k + dy * R)
                T.draw(part, face, "line", [p0, p1], 0.25, col=fibra, glow=True, where=lambda f, mm=mi2: mm)
                k += passo

    def veia(part, face, pts, sym=False):
        (T.sym if sym else T.draw)(part, face, "line", pts, 0.25, col=gl, glow=True)

    # ------------------------------------------------------------- tronco, frente
    painel("body", "front", [(0.3, 0.75), (3.75, 0.65), (3.75, 4.5), (2.4, 4.95), (0.9, 4.5), (0.2, 2.8)], sym=True, fib=20)          # peitorais
    for b0, b1 in ((5.1, 6.3), (6.55, 7.7), (7.95, 9.1)):                                                                   # abdômen
        painel("body", "front", [(2.0, b0), (3.75, b0), (3.75, b1), (2.15, b1)], luz=False, grad=0.1, sym=True)
    painel("body", "front", [(2.7, 9.35), (3.75, 9.35), (3.75, 11.3), (3.1, 10.8)], luz=False, sym=True)
    painel("body", "front", [(0.2, 5.2), (1.8, 5.3), (1.95, 9.3), (2.6, 11.4), (0.2, 11.7)], col=c2, sym=True)             # oblíquos
    for k in range(3): T.sym("body", "front", "line", [(0.3, 5.9 + k * 1.0), (1.7, 6.3 + k * 1.0)], 0.25, col=sulco)   # serrátil
    veia("body", "front", [(4.0, 4.9), (4.0, 11.4)])                                                                        # linha alba acesa
    veia("body", "front", [(2.3, 11.3), (3.1, 11.9)], sym=True)                                                             # "V" do quadril
    T.draw("body", "front", "poly", [(2.6, 0.0), (5.4, 0.0), (5.0, 0.55), (3.0, 0.55)], col=dk(c2, 0.2), rz=0.22, tag=3)   # gola
    veia("body", "front", [(2.8, 0.6), (5.2, 0.6)])
    emblema3d(T, EMBLEMAS[tropa], gl_emb, fundo=esc, aro=lt(c, 0.3), a_c=4.0, b_c=2.6, cel=0.36)
    # ------------------------------------------------------------- tronco, costas
    painel("body", "back", [(2.6, 0.2), (3.75, 0.2), (3.75, 4.0), (1.0, 1.7)], sym=True)                                 # trapézio
    painel("body", "back", [(0.5, 1.9), (2.5, 2.6), (3.3, 4.5), (2.4, 5.7), (0.8, 5.3), (0.35, 3.3)], sym=True, fib=120)          # escápulas
    painel("body", "back", [(0.3, 5.9), (2.6, 6.2), (3.5, 8.6), (3.0, 10.3), (0.4, 9.8)], col=c2, sym=True, fib=60)             # dorsais
    painel("body", "back", [(3.05, 9.0), (3.75, 9.0), (3.75, 11.5), (3.05, 11.5)], luz=False, sym=True)                   # lombar
    T.draw("body", "back", "rect", 3.75, 0.2, 4.25, 11.8, col=sulco)
    veia("body", "back", [(4.0, 0.3), (4.0, 11.7)])
    for k in range(6): T.draw("body", "back", "rect", 3.75, 1.0 + k * 1.8, 4.25, 1.5 + k * 1.8, col=gl, glow=True)       # vértebras de luz
    # ------------------------------------------------------------- laterais e topo
    for f in ("right", "left"):
        painel("body", f, [(0.0, 0.9), (1.6, 1.2), (1.6, 4.2), (0.0, 4.6)], col=c2)
        veia("body", f, [(2.0, 0.4), (2.3, 3.0), (1.8, 6.0), (2.2, 9.0), (2.0, 11.6)])
    T.draw("body", "top", "all", col=c, grad=0.0, tag=1)
    T.draw("body", "top", "rect", 2.4, 0.6, 5.6, 3.4, col=dk(c2, 0.25))
    T.draw("body", "bottom", "all", col=esc)

    for s in "rl":
        arm, leg, boot = s + "arm", s + "leg", s + "boot"
        # ------------------------------------------------------------- braços
        painel(arm, "outer", [(0.3, 0.0), (3.7, 0.0), (3.6, 2.5), (2.0, 3.4), (0.4, 2.5)])                                   # deltoide
        painel(arm, "front", [(0.0, 0.0), (2.6, 0.0), (2.3, 2.2), (0.0, 3.3)])
        painel(arm, "back", [(0.0, 0.0), (2.6, 0.0), (2.3, 2.2), (0.0, 3.3)])
        T.draw(arm, "top", "all", col=c, tag=1, rz=0.1)
        painel(arm, "front", [(0.6, 3.0), (3.4, 3.2), (3.5, 5.2), (2.6, 6.6), (1.2, 6.6), (0.5, 5.2)])                       # bíceps
        painel(arm, "back", [(0.5, 2.9), (3.5, 3.0), (3.4, 5.6), (2.0, 6.8), (0.6, 5.6)])                                    # tríceps
        painel(arm, "outer", [(1.2, 3.6), (2.8, 3.6), (2.6, 6.4), (1.4, 6.4)], col=c2, luz=False)
        for f in ("front", "back"): painel(arm, f, [(0.4, 7.1), (3.6, 7.1), (3.3, 10.2), (0.7, 10.2)], grad=0.1)            # antebraço
        painel(arm, "outer", [(0.4, 7.1), (3.6, 7.1), (3.0, 10.2), (1.0, 10.2)], col=c2)
        veia(arm, "inner", [(2.0, 0.4), (2.4, 4.0), (1.7, 7.4), (2.1, 10.2)])
        for f in ("front", "back", "outer", "inner"):                                                                         # luva escura
            T.draw(arm, f, "rect", 0, 10.4, 4, 12, col=dk(esc, 0.2), rz=0.1, tag=2, rz_mode="max")
            T.draw(arm, f, "line", [(0, 10.45), (4, 10.45)], 0.25, col=gl, glow=True)
        T.draw(arm, "front", "rect", 0.4, 10.9, 3.6, 11.5, col=c2, rz=0.22, tag=2, rz_mode="max")
        T.draw(arm, "bottom", "all", col=dk(esc, 0.3))
        # ------------------------------------------------------------- pernas
        painel(leg, "front", [(0.4, 0.6), (3.6, 0.6), (3.5, 3.8), (2.6, 5.6), (1.4, 5.6), (0.5, 3.8)])                       # quadríceps
        T.draw(leg, "front", "ellipse", 1.0, 5.85, 3.0, 7.25, col=c2, rz=0.22, tag=1, rz_mode="max")                         # joelho
        T.draw(leg, "front", "line", [(1.2, 6.0), (2.8, 6.0)], 0.25, col=gl, glow=True)
        painel(leg, "front", [(0.8, 7.5), (3.2, 7.5), (2.8, 11.6), (1.2, 11.6)])                                            # canela
        painel(leg, "outer", [(0.4, 0.6), (3.4, 0.6), (3.0, 5.4), (1.0, 5.4)], col=c2)                                      # vasto lateral
        painel(leg, "outer", [(0.8, 7.6), (3.2, 7.6), (2.8, 11.4), (1.2, 11.4)], col=c2, luz=False)
        painel(leg, "back", [(0.5, 0.6), (3.5, 0.6), (3.2, 5.0), (0.8, 5.0)], col=c2)                                       # posterior
        painel(leg, "back", [(0.5, 7.2), (3.5, 7.2), (3.0, 10.0), (2.0, 10.6), (1.0, 10.0)])                                # panturrilha
        veia(leg, "inner", [(2.0, 0.4), (1.6, 4.0), (2.2, 7.6), (1.9, 11.6)])
        veia(leg, "outer", [(3.7, 0.6), (3.7, 11.6)])
        # ------------------------------------------------------------- botas (energia mais densa)
        for f in ("front", "back", "outer", "inner"):
            T.draw(boot, f, "rect", 0, 0, 4, 5, col=dk(esc, 0.15), tag=3)
            T.draw(boot, f, "rect", 0, 4.4, 4, 5, col=dk(esc, 0.45), rz=0.22, tag=3, rz_mode="max")
            T.draw(boot, f, "line", [(0, 4.3), (4, 4.3)], 0.25, col=gl, glow=True)
        painel(boot, "front", [(1.0, 0.0), (3.0, 0.0), (2.8, 2.8), (1.2, 2.8)])
        painel(boot, "front", [(0.4, 3.1), (3.6, 3.1), (3.6, 4.2), (0.4, 4.2)], col=c2, luz=False, rz=0.22)
        painel(boot, "outer", [(0.6, 0.4), (3.4, 0.4), (3.0, 3.6), (1.0, 3.6)], col=c2)
        painel(boot, "back", [(1.0, 0.0), (3.0, 0.0), (2.6, 3.4), (1.4, 3.4)], col=c2, luz=False)
        T.draw(boot, "bottom", "all", col=dk(esc, 0.5)); T.draw(boot, "top", "all", col=c, alpha=0)

    # ------------------------------------------------------------- toques de cada Tropa
    if tropa == "vermelho":   # veias de fúria em zigue-zague
        for s in "rl":
            T.draw(s + "arm", "outer", "line", [(1.0, 7.4), (2.4, 8.2), (1.4, 9.0), (2.8, 9.8)], 0.25, col=gl, glow=True)
        T.sym("body", "front", "line", [(0.6, 1.6), (1.6, 2.4), (1.0, 3.1), (2.0, 3.8)], 0.25, col=gl, glow=True)
    elif tropa == "amarelo":  # divisas afiadas nos antebraços
        for s in "rl":
            for b in (7.6, 8.6, 9.6): T.draw(s + "arm", "front", "line", [(0.8, b), (2.0, b + 0.6), (3.2, b)], 0.25, col=gl, glow=True)
    elif tropa == "azul":     # raios ascendentes nas costas
        T.sym("body", "back", "line", [(3.6, 9.0), (2.0, 6.4), (1.0, 2.6)], 0.25, col=gl, glow=True)
    elif tropa == "violeta":  # curvas sinuosas
        T.sym("body", "front", "line", [(0.6, 1.4), (1.6, 2.0), (2.0, 3.0), (2.8, 3.6)], 0.25, col=gl, glow=True)
    elif tropa == "indigo":   # marcas tribais em pontos
        for s in "rl":
            for b in (7.6, 8.4, 9.2): T.draw(s + "arm", "outer", "rect", 1.75, b, 2.25, b + 0.4, col=gl, glow=True)
    elif tropa == "laranja":  # filetes paralelos (acúmulo)
        for s in "rl":
            for a in (1.2, 2.0, 2.8): T.draw(s + "leg", "front", "line", [(a, 1.4), (a, 3.6)], 0.25, col=gl, glow=True)
    elif tropa == "preto":    # costelas de luz fria
        for k in range(3): T.sym("body", "front", "line", [(3.6, 5.3 + k * 1.4), (2.6, 5.5 + k * 1.4)], 0.25, col=gl, glow=True)
    elif tropa == "branco":   # filigrana dourada nos ombros
        for s in "rl":
            T.draw(s + "arm", "outer", "line", [(0.8, 0.6), (2.0, 1.6), (3.2, 0.6)], 0.25, col=gl, glow=True)

    # ------------------------------------------------------------- máscara dominó
    mascara(T, c, lente=P["lente"], rz=0.0)
    T.draw("head", "front", "line", [(0.2, 2.65), (7.8, 2.65)], 0.25, col=gl, glow=True)
    return T

for _t in PAL:
    designs.DESIGNS[(_t, "energia")] = dict(fn=(lambda t=_t: criar(t)), nome="Energia", fabric="pele", panel="energia")

"""
Gera os sons de "fala" de enquanto não há vozes gravadas: sílabas curtinhas (vogais com formantes, consoantes de
ruído), tocadas em sequência e com o tom de cada personagem. Rodar na raiz:  python3 ferramentas/sons_fala.py
Saída: assets/lanterna/sounds/fala/{voz_1..8, anel_1..6, morte_1..6}.ogg  (precisa do ffmpeg)
"""
import os, subprocess, tempfile, wave
import numpy as np

SR = 22050
SAIDA = os.path.join("src", "main", "resources", "assets", "lanterna", "sounds", "fala")
# formantes (F1, F2, F3) das vogais
VOGAIS = {"a": (800, 1200, 2500), "e": (500, 1800, 2500), "i": (300, 2300, 3000), "o": (500, 900, 2400), "u": (320, 800, 2300),
          "ê": (420, 2000, 2600)}


def ressoar(sinal, f, banda):
    """Filtro ressonante simples (2 polos) na frequência f."""
    r = np.exp(-np.pi * banda / SR)
    a1, a2 = -2 * r * np.cos(2 * np.pi * f / SR), r * r
    y = np.zeros_like(sinal)
    for n in range(len(sinal)):
        y[n] = sinal[n] - a1 * (y[n - 1] if n > 0 else 0) - a2 * (y[n - 2] if n > 1 else 0)
    return y


def silaba(vogal, f0=150, dur=0.12, consoante=None, rouca=0.0, brilho=0.0, seed=0):
    rng = np.random.default_rng(seed)
    n = int(SR * dur)
    t = np.arange(n) / SR
    # fonte: pulsos da glote com um pouco de vibrato (soa humano, não bip)
    f = f0 * (1 + 0.03 * np.sin(2 * np.pi * 5.5 * t) + 0.04 * (t / dur))
    fase = np.cumsum(f / SR)
    fonte = (fase % 1.0) * 2 - 1
    fonte = np.sign(fonte) * np.abs(fonte) ** 3
    fonte += rouca * rng.normal(0, 1, n)
    F1, F2, F3 = VOGAIS[vogal]
    s = ressoar(fonte, F1, 90) * 1.0 + ressoar(fonte, F2, 120) * 0.55 + ressoar(fonte, F3, 180) * 0.25
    if brilho > 0:   # o anel: um coro suave por cima (como duas vozes juntas)
        fase2 = np.cumsum(f * 1.006 / SR)
        fonte2 = np.sign((fase2 % 1.0) * 2 - 1) * np.abs((fase2 % 1.0) * 2 - 1) ** 3
        s += brilho * (ressoar(fonte2, F1, 110) + 0.6 * ressoar(fonte2, F2, 140))
        s += brilho * 0.15 * np.sin(2 * np.pi * f * 4 * t)
    env = np.minimum(1, t / 0.012) * np.minimum(1, (dur - t) / 0.045)
    s *= np.clip(env, 0, 1)
    if consoante:
        m = int(SR * 0.035)
        ruido = rng.normal(0, 1, m)
        ruido = ressoar(ruido, {"s": 5500, "t": 3500, "k": 2200, "p": 900, "m": 300}[consoante], 600 if consoante != "m" else 80)
        ruido *= np.linspace(1, 0, m) * (0.5 if consoante != "m" else 2.0)
        s = np.concatenate([ruido, s])
    s /= max(1e-9, np.max(np.abs(s)))
    return (s * 0.6).astype(np.float32)


def eco(s, atraso=0.09, ganho=0.35, vezes=3):
    out = np.concatenate([s, np.zeros(int(SR * atraso * vezes))])
    for k in range(1, vezes + 1):
        d = int(SR * atraso * k)
        out[d:d + len(s)] += s * ganho ** k
    return out / max(1e-9, np.max(np.abs(out))) * 0.6


def gravar(nome, s):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        caminho = tmp.name
    with wave.open(caminho, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(s, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", caminho, "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(SAIDA, nome + ".ogg")], check=True)
    os.remove(caminho)


if __name__ == "__main__":
    os.makedirs(SAIDA, exist_ok=True)
    voz = [("a", "m"), ("e", "t"), ("i", "s"), ("o", "p"), ("u", "k"), ("a", None), ("ê", "s"), ("o", "m")]
    for i, (v, c) in enumerate(voz, 1):
        gravar("voz_%d" % i, silaba(v, 150, 0.11 + 0.02 * (i % 3), c, seed=i))
    anel = [("a", None), ("e", "m"), ("i", None), ("o", "s"), ("u", None), ("ê", "m")]
    for i, (v, c) in enumerate(anel, 1):
        gravar("anel_%d" % i, silaba(v, 170, 0.14, c, brilho=0.6, seed=20 + i))
    morte = [("o", "k"), ("u", None), ("a", "m"), ("o", None), ("u", "s"), ("a", "t")]
    for i, (v, c) in enumerate(morte, 1):
        gravar("morte_%d" % i, eco(silaba(v, 70, 0.17, c, rouca=0.35, seed=40 + i)))
    print("ok: sons de fala")

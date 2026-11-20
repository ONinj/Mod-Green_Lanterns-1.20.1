"""
Registra as vozes gravadas. Coloque os áudios (.ogg, mono) em
  src/main/resources/assets/lanterna/sounds/voz/pt_br/<chave>.ogg
  src/main/resources/assets/lanterna/sounds/voz/en_us/<chave>.ogg
com o nome igual à chave da fala (a lista está em docs/FALAS.csv, gerada por ferramentas/falas.py) e rode na raiz:
  python3 ferramentas/vozes.py
Ele escreve as entradas "voz.<idioma>.<chave>" no sounds.json. No jogo, a fala com áudio toca o áudio; sem áudio,
continua o falatório de sílabas.
"""
import json, os

RAIZ = os.path.join("src", "main", "resources", "assets", "lanterna")
SONS = os.path.join(RAIZ, "sounds.json")

dados = json.load(open(SONS, encoding="utf-8"))
for k in [k for k in dados if k.startswith("voz.")]:
    del dados[k]
n = 0
for lang in ("pt_br", "en_us"):
    pasta = os.path.join(RAIZ, "sounds", "voz", lang)
    if not os.path.isdir(pasta):
        continue
    for f in sorted(os.listdir(pasta)):
        if not f.endswith(".ogg"):
            continue
        chave = f[:-4]
        dados["voz.%s.%s" % (lang, chave)] = {"sounds": [{"name": "lanterna:voz/%s/%s" % (lang, chave), "stream": True}]}
        n += 1
json.dump(dados, open(SONS, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print("vozes registradas:", n)

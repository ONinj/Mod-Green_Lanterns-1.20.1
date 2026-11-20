"""
Lista de todas as falas com voz da saga, para gravar ou gerar: docs/FALAS.csv
(chave; personagem; texto pt; texto en; arquivo pt; arquivo en). Rodar na raiz:  python3 ferramentas/falas.py
O arquivo de cada fala é a chave + .ogg, na pasta sounds/voz/<idioma>/ (depois rode ferramentas/vozes.py).
"""
import csv, json, os, re

RAIZ = os.path.join("src", "main", "resources", "assets", "lanterna", "lang")
pt = json.load(open(os.path.join(RAIZ, "pt_br.json"), encoding="utf-8"))
en = json.load(open(os.path.join(RAIZ, "en_us.json"), encoding="utf-8"))

# (padrão da chave, personagem)
QUEM = [
    (r"^necron\.lanterna\.leg\.", "Narrador"),
    (r"^necron\.lanterna\.(nome|nome_sub|chegou|continua)$", "Narrador"),
    (r"^necron\.lanterna\.fuga\.(cai|acorda)$", "Narrador"),
    (r"^necron\.lanterna\.fuga\.(aguenta|segura|fala_\d+)$", "Lanterna da Tropa do jogador"),
    (r"^necron\.lanterna\.alerta_[12]$", "Necron"),
    (r"^necron\.lanterna\.alerta_3$", "Lanterna da Tropa do jogador"),
    (r"^necron\.lanterna\.pedido_\d$", "Lanterna de outra Tropa (pedindo ajuda)"),
    (r"^necron\.lanterna\.pedido\.[a-z]+_\d$", "Lanterna de outra Tropa (pedindo ajuda)"),
    (r"^necron\.lanterna\.defesa_fala\.[a-z]+$", "Lanterna da Tropa defendida"),
    (r"^necron\.lanterna\.queda_[12]$", "Narrador"),
    (r"^necron\.lanterna\.rastro_[123]$", "O anel"),
    (r"^necron\.lanterna\.(defesa_fala|final_fala|vitoria_fala)$", "Lanterna aliado"),
    (r"^necron\.lanterna\.mestre_(1|apoio|duelo|rende|segura|fenda|fenda_fim)$", "Mestre da Tropa do jogador"),
    (r"^necron\.lanterna\.ancora_fala_fim$", "Lanterna da Tropa do jogador"),
    (r"^necron\.lanterna\.socorro_(prisioneiros|sobreviventes|ladroes)$", "Lanterna de outra Tropa (pedindo ajuda)"),
    (r"^necron\.lanterna\.preso_fala$", "Lanterna preso (solto da jaula)"),
    (r"^necron\.lanterna\.ruina_chamado$", "Lanterna da Tropa do jogador"),
    (r"^necron\.lanterna\.caido_fala_\d$", "Lanterna ferido (Tropa caída)"),
    (r"^necron\.lanterna\.carcereiro_fala_\d$", "O Carcereiro (servo do Necron)"),
    (r"^necron\.lanterna\.nucleo_guardado$", "Lanterna da Tropa do jogador"),
    (r"^necron\.lanterna\.(fenda_terra_aviso|pulso_[123]|santuario_achou|cerco_embriao_fim)$", "O anel"),
    (r"^necron\.lanterna\.cerco_aurora_(comeca|fim)$", "Sgt. Rui Tavares (Vila Aurora)"),
    (r"^necron\.lanterna\.cerco_(aurora|embriao)_arauto$", "Necron"),
    (r"^necron\.lanterna\.recruta\.[a-z]+$", "Líder da Tropa chamada"),
    (r"^necron\.lanterna\.(desperta_[123]|terra_curada)$", "O anel"),
    (r"^necron\.lanterna\.corrompidos_[a-z]+$", "Lanterna da Tropa do jogador"),
    (r"^necron\.lanterna\.lider_[a-z]+_1$", "Líder corrompido"),
    (r"^necron\.lanterna\.lider_[a-z]+_fim$", "Líder purificado"),
    (r"^necron\.lanterna\.ritual_[123]$", "Lanternas do ritual"),
    (r"^necron\.lanterna\.(epilogo_[12]|parallax_1)$", "Narrador"),
    (r"^necron\.lanterna\.(tentacao_[123]|tentacao_sim_1|tentacao_nao_1|c1_fala_[123]|c3_guardioes_fim|c4_fala_[12])$", "Necron"),
    (r"^necron\.lanterna\.espectro_fala$", "O Espectro (a voz do próprio jogador)"),
    (r"^necron\.lanterna\.c3_guardioes$", "Guardião do Embrião"),
    (r"^necron\.lanterna\.c4_epilogo_[12]$", "Narrador"),
    (r"^mission\.lanterna\.necron_a1\.talk$", "Sgt. Rui Tavares (Vila Aurora)"),
    (r"^mission\.lanterna\.necron_a3\.talk$", "Dra. Lívia Andrade (Vila Aurora)"),
    (r"^mission\.lanterna\.necron_[a-z0-9_]+\.talk$", "NPC da missão"),
]

linhas = []
for k in pt:
    for padrao, quem in QUEM:
        if re.match(padrao, k):
            linhas.append([k, quem, pt[k], en.get(k, ""), "voz/pt_br/%s.ogg" % k, "voz/en_us/%s.ogg" % k])
            break
os.makedirs("docs", exist_ok=True)
with open(os.path.join("docs", "FALAS.csv"), "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f, delimiter=";")
    w.writerow(["chave", "personagem", "texto pt", "texto en", "arquivo pt", "arquivo en"])
    w.writerows(linhas)
print("falas:", len(linhas))

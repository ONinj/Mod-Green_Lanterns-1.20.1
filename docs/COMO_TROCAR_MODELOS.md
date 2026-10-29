# Como trocar os modelos 3D (anel, bateria e uniformes)

O jeito mais fácil: **me manda o arquivo** (`.bbmodel` do Blockbench, `.json` ou `.png`) e eu coloco no mod.
Se quiser fazer sozinho, siga este guia.

## Tipo de projeto no Blockbench
Use **"Java Block/Item"**. Ele exporta um `.json` que o Minecraft lê direto, sem outro mod.
- Cubos podem girar só em **um eixo** e em passos de **22,5°** (limite do Minecraft).
- Se você precisar de rotação livre, ossos ou animação própria no modelo, me avisa: dá para usar o GeckoLib (aí o mod passa a pedir esse mod junto).

## Cor da Tropa ("Tint")
Toda face marcada com **Tint** (no Blockbench: botão direito na face → Tint, ou campo "Tint" no painel da face) é pintada na cor da Tropa.
Pinte essas partes da textura em **branco ou cinza** — o jogo multiplica pela cor (verde, amarelo, vermelho...).
Faces sem Tint aparecem com a cor original da textura.

## Anéis (um modelo por Tropa)
| Tropa | Modelo | Textura |
|---|---|---|
| Verde | `models/item/ring/anel_vontade_verde.json` | `textures/item/espectro_verde.png` |
| Amarela | `anel_medo_amarelo.json` | `espectro_amarelo.png` |
| Vermelha | `anel_raiva_vermelho.json` | `espectro_vermelho.png` |
| Azul | `anel_esperanca_azul.json` | `espectro_azul.png` |
| Violeta | `anel_amor_violeta.json` | `espectro_violeta.png` |
| Índigo | `anel_compaixao_indigo.json` | `espectro_indigo.png` |
| Laranja | `anel_avareza_laranja.json` | `espectro_laranja.png` |
| Negra | `anel_morte_preto.json` | `espectro_preto.png` |
| Branca | `anel_vida_branco.json` | `espectro_branco.png` |

- Ao exportar do Blockbench, a textura no .json deve ser `lanterna:item/espectro_<cor>` (textura e particle).
- Peças com "Light Emission" brilham (o mod converte para o formato do Forge 1.20.1).
- O anel no dedo usa a posição de exibição **"fixed"**; deixe-a sem rotação (o mod já ajusta).

## Baterias (um modelo por Tropa)
`models/block/battery/<nome>.json` + `textures/block/battery/<nome>.png`:
`bateria_vontade_verde`, `bateria_raiva_vermelho`, `bateria_avareza_laranja`, `bateria_medo_amarelo`,
`bateria_esperanca_azul`, `bateria_amor_violeta`, `caveira_morte_preto`.
Cajado da Compaixão: `models/item/compassion_staff.json` + `textures/item/cajado_compaixao_indigo.png`.
Pode me mandar os .bbmodel direto que eu converto.

## Uniformes 3D (GeckoLib)
`geo/item/armor/uniforme_<cor>.geo.json`, `textures/item/armor/uniforme_<cor>.png` (+ `_glowmask.png`),
`animations/item/armor/uniforme_<cor>.animation.json`.

**Animações feitas no Blockbench:**
- `misc.idle` toca sempre (loop). `misc.walk`, se existir, toca enquanto anda.
- Os ossos `armorHead`, `armorBody`, `armorRightArm`... seguem o corpo do jogador — o jogo sobrescreve o movimento deles.
  Para animar uma peça (cristal girando, capa balançando), crie um **osso filho** (ex.: `cristal` dentro de `armorBody`),
  coloque os cubos nele e anime esse osso. Testado no jogo: funciona.
- Ao ligar o anel, o uniforme 3D da Tropa se forma sozinho (camada de base pixel por pixel e peças cubinho por cubinho).

## Uniforme (skin antiga, opcional)

### Opção 1: só textura (skin)
Coloque uma skin 64x64 em `config/lanterna/uniformes/<tropa>.png` (ex.: `green.png`).
Ela é vestida por cima do jogador, um pouquinho maior que o corpo. Não precisa recompilar o mod.

### Opção 2: peças 3D (capacete, ombreiras, peitoral...)
Uma peça por parte do corpo, cada uma em seu arquivo:

```
assets/lanterna/models/uniform/<tropa>/<parte>.json   só para essa Tropa (ex.: uniform/green/head.json)
assets/lanterna/models/uniform/all/<parte>.json       para todas as Tropas que não tiverem a sua
```

`<parte>` = `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`
`<tropa>` = `green`, `yellow`, `red`, `blue`, `violet`, `indigo`, `orange`, `black`, `white`

Cada peça segue a parte do corpo (andar, virar a cabeça, voar) e aparece com a animação de nanotecnologia.
Dá para usar as duas opções juntas (skin + peças 3D).

**Moldes prontos:** abra `blockbench_templates/uniforme/<parte>.json`. Cada um tem um cubo de vidro chamado
`REFERENCIA_...` mostrando onde fica aquela parte do corpo. Modele em volta dele e **apague o cubo antes de exportar**.

Posições (1 unidade = 1 pixel da skin, personagem olhando para o **NORTE**):

| Parte | A parte do corpo ocupa | Observação |
|---|---|---|
| head | (4,0,4) até (12,8,12) | rosto no norte (Z menor) |
| body | (4,4,6) até (12,16,10) | peito no norte |
| right_arm | (7,2,6) até (11,14,10) | lado de fora em X=11 (leste), mão embaixo |
| left_arm | (5,2,6) até (9,14,10) | lado de fora em X=5 (oeste), mão embaixo |
| right_leg | (6,0,6) até (10,12,10) | pé embaixo |
| left_leg | (6,0,6) até (10,12,10) | pé embaixo |

Texturas das peças: coloque em `assets/lanterna/textures/block/uniform/...` e use o caminho
`lanterna:block/uniform/<nome>` no modelo (o Blockbench faz isso se o arquivo estiver nessa pasta).

## Alienígenas (GeckoLib)
Cada planeta tem a sua espécie. O id da espécie é o id do planeta:
`oa`, `korugar`, `ysmault`, `odym`, `zamaron`, `nok`, `okaara`, `ryut`
(planetas gerados têm ids como `p3_m2`; o mapa estelar mostra o nome deles).

Para trocar o modelo de uma espécie, use o tipo de projeto **"GeckoLib Animated Model"** (plugin GeckoLib no Blockbench)
e exporte três arquivos com o id da espécie no nome:

```
assets/lanterna/geo/alien/<espécie>.geo.json
assets/lanterna/textures/entity/alien/<espécie>.png
assets/lanterna/animations/alien/<espécie>.animation.json
```

- As animações precisam se chamar **`animation.alien.idle`** (parado) e **`animation.alien.walk`** (andando).
- O que faltar usa o provisório (`default.geo.json`, `default.png`, `default.animation.json`) — dá para trocar só a textura, por exemplo.
- Espécie com textura própria aparece com as cores da textura; sem textura própria, o provisório é pintado na cor da espécie.
- O modelo provisório tem ossos com nomes especiais que o mod liga/desliga por espécie: `brain`, `eye_left`, `eye_right`,
  `eye_mid`, `antenna_left`, `antenna_right`, `arm2_left`, `arm2_right`, `tail` e `head` (que muda de tamanho).
  No seu modelo eles são opcionais.
- O tamanho da criatura no jogo também segue a espécie (os Oanos são baixinhos, por exemplo).

## Depois de trocar
Me manda os arquivos que eu compilo e te entrego o `.jar` novo. Se você mesmo compilar, basta dar push no GitHub:
o Actions gera o `.jar`.

# Lanterna — mod de Minecraft (Forge 1.20.1)

Anéis de poder, baterias e o espectro emocional, com espaço sideral e planetas.

## Como compilar
Cada push no GitHub roda o Actions ("Compilar mod"). O `.jar` fica em **Actions → última execução → Artifacts → lanterna-mod**.

## Requisitos
- Minecraft 1.20.1 + Forge 47.3+
- Nenhum outro mod é necessário

## Fase 1: anel + bateria + entrega
- **Anel de Poder**: um item só para todas as Tropas; cor, carga e dono ficam no NBT. Clique direito ou coloque na aba Lanterna (tecla K).
- **Bateria de Poder**: recarrega só anéis da mesma Tropa. Texto do juramento em `lang/*.json` (`oath.lanterna.<tropa>`).
- **Voo**: tecla **V**, gasta carga por segundo (`flight_drain` no JSON da Tropa).
- **HUD**: barra de carga na cor da Tropa, aviso de carga baixa, indicador de voo.
- **Entrega do anel com animação**:
  - Verde: chance ao acordar após dormir a noite inteira. O anel cruza o céu como cometa e gira até o dedo.
  - Vermelho: matar N mobs hostis dentro de uma janela de tempo. Colunas de sangue se fecham em volta do jogador.
- **Tropas em JSON**: `data/lanterna/lanterna_corps/*.json` (cor, emoção, carga máxima, gasto de voo). `/reload` aplica mudanças.
- **Config**: `config/lanterna-common.toml` (chance do verde, abates do vermelho, duração da animação).

## Comandos de teste (cheats ligados)
- `/lanterna escolher <tropa> [jogador]` — roda a cerimônia (ex: `lanterna:green`)
- `/lanterna carga <valor>`
- `/lanterna remover`
- `/lanterna resetar`

## Roteiro
1. ✅ Anel + bateria + entrega (verde e vermelho)
1.5 ✅ Aba "Lanterna" própria no inventário (slots de anel e bateria, tecla K), modelos 3D, anel visível na mão, recarga pela bateria do slot (tecla R)
1.6 ✅ Rituais para as 9 Tropas (`/lanterna rituais`), anel liga/desliga (G) com contorno na cor da Tropa, voo deitado com braço à frente e rastro de energia
1.7 ✅ Voo com física (embalo, derrapagem nas curvas, inclinação lateral, mergulho, arrancada, vento, FOV), capa correta no voo, anel embaixo do punho, aba Lanterna também no criativo, botões Anel/Voo na tela
1.8 ✅ Voo mais vivo (inclinação contínua, derrapagem lateral, estrondo sônico, poeira e água no rasante, pousar com Shift, tremor), uniformes com transformação estilo nanotecnologia, ESPAÇO com Terra, Sol e 8 planetas visitáveis
1.9 ✅ Animação de recarga (tecla R): portal na cintura, a bateria sai dele, o braço encosta e a energia flui para o anel. Sistema pronto para modelos 3D próprios (anel, bateria e peças de uniforme)
2. Poderes: rajada, escudo, constructos (entidades), menu radial de constructos
5. Estruturas: nave caída (verde), rio de sangue (vermelho)
6. Planetas: habitantes alienígenas, vilas e minérios próprios de cada planeta, missões
8. Crafting: receitas para baterias e alguns anéis

## Arte
As texturas atuais são provisórias e genéricas. Substitua os PNGs em `assets/lanterna/textures/` mantendo os mesmos nomes. A pedra do anel e o núcleo da bateria devem ficar em tons de cinza/branco, porque o jogo pinta na cor da Tropa.

## Teste visual automático (desenvolvimento)
`gradle runTestClient` abre o jogo com um roteiro que roda cerimônias, liga o anel, voa e tira prints em `run/screenshots`.
Precisa de um mundo chamado `test` em `run/saves`. O código de teste (`client/devtest`) não vai para o .jar.

## Espaço
- Voe acima de **Y 330** para sair da atmosfera. Você chega no espaço com a Terra embaixo.
- Planetas (de perto para longe): Oa, Korugar, Ysmault, Odym, Zamaron, Nok, Okaara, Ryut. O HUD mostra direção e distância.
- Entre num planeta para pousar nele; voe acima de Y 330 no planeta para voltar ao espaço; desça até a Terra para voltar para casa.
- No espaço só o anel ligado dá oxigênio (gasta 1 de carga por segundo). O anel voa 4x mais rápido no vácuo.

## Uniformes
Ligar o anel (G) veste o uniforme da Tropa com uma animação que sai da mão do anel.
Para usar suas próprias texturas: coloque skins 64x64 em `config/lanterna/uniformes/<tropa>.png` (veja o LEIA-ME que o mod cria lá).
Peças 3D de uniforme (capacete, ombreiras...) e troca dos modelos do anel e da bateria: veja `docs/COMO_TROCAR_MODELOS.md` e os moldes em `blockbench_templates/`.

# Lanterna — mod de Minecraft (Forge 1.20.1)

Anéis de poder, baterias e o espectro emocional, com espaço sideral e planetas.

## Como compilar
Cada push no GitHub roda o Actions ("Compilar mod"). O `.jar` fica em **Actions → última execução → Artifacts → lanterna-mod**.

## Requisitos
- Minecraft 1.20.1 + Forge 47.3+
- **GeckoLib 4.8 ou mais nova** (modelos animados dos alienígenas)

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
1.10 ✅ Uniforme surgindo pixel por pixel
1.11 ✅ Hiperpropulsão no espaço, piloto automático com mapa estelar (J), voo com mais física (parafuso, freio aerodinâmico, impacto, água, turbulência, inércia no vácuo)
1.12 ✅ Planetas gerados sem fim pelo espaço (8 tipos), alienígenas com trocas próprias de cada planeta, materiais dos planetas
1.13 ✅ Modelos do autor: 9 anéis, baterias de cada Tropa (lanterna, braseiro, relicário, torre, farol, drusa, caveira), Cajado da Compaixão, uniformes 3D (GeckoLib) das 9 Tropas — vestidos pelo anel com transformação, ou como armadura
1.14 ✅ Lore completa (pacote Lore3D): 9 raças + Guardiões do Universo, 9 bosses com mecânicas, minérios e blocos de cada planeta, Gênese escondido, missões com HUD e diário (M), afinidade do anel, 12 construtos com roda (Z/X) e presets, anéis forjados com rituais, Anel Branco por fusão. Detalhes em `docs/LORE_E_MISSOES.md`
1.15 ✅ Ser escolhido na Terra: O Chamado (conquistas) e O Anel Caído (cratera, Anel Apagado e Bateria Cinzelada). Todo anel leva ao planeta da Tropa, Guardião Emissário em cada planeta, Tropas inimigas, bônus passivos do anel e menos mobs nos planetas
1.16 ✅ (0.13) Tropas vivas: 18 espécies de Lanternas NPC, piratas e mercadores; naves pilotáveis com interior; nível e postos; contratos; encontros no espaço; Prova da Emoção. Detalhes em `docs/LORE_E_MISSOES.md`
1.17 ✅ (0.14) Cidades das Tropas com Bateria Central, patrulha de setores e exploração por posto, piloto automático da nave, trava de mira, laser contínuo
1.18 ✅ (0.15) Cidade completa com interiores, lojas e missões dos NPCs; Palácio dos Guardiões; Técnicas dos Capitães
1.19 ✅ (0.15.1) Auditoria das missões de derrotar: toda missão de "matar N" garante alvos suficientes
1.20 ✅ (0.15.2) Juramento com a Anciã Miraeth: degraus nos tronos do Conselho e "fale com" conta ao aceitar
1.21 ✅ (0.16) Equilíbrio para multiplayer: vida, armadura e dureza por nível; PvP sem hit kill (configurável)
1.22 ✅ (0.17) Guildas e conquista de planetas: bandeira com base pronta, minerador, general, tropas, escudo com símbolo, HUD e invasões
1.23 ✅ (0.18) Minerador com 4 níveis; Anéis Supremos (Branco e Preto); Anel Preto só por corrupção na morte (linha "A Morte"); linha principal mais longa e travada por nível; planetas das Tropas NPC; mais piratas e saque melhor
1.24 ✅ (0.19) Construto próprio de cada Tropa (Pavor trava construtos), animações de lançar, laser novo e polimento visual de todos os construtos
1.25 ✅ (0.20) Porta Estelar: linha dos Guardiões (nível 32, afinidade Confiável) e teleporte animado para planetas visitados (tecla N)
1.26 ✅ (0.21) Comandos de teste (op): anel de qualquer Tropa, nível, afinidade, técnicas, poderes, linha da Morte, missões, invocar NPCs/chefes/naves e kits (`/lanterna ajuda`)
5. Estruturas: nave caída (verde), rio de sangue (vermelho)

## Arte
As texturas atuais são provisórias e genéricas. Substitua os PNGs em `assets/lanterna/textures/` mantendo os mesmos nomes. A pedra do anel e o núcleo da bateria devem ficar em tons de cinza/branco, porque o jogo pinta na cor da Tropa.

## Teste visual automático (desenvolvimento)
`gradle runTestClient` abre o jogo com um roteiro que roda cerimônias, liga o anel, voa e tira prints em `run/screenshots`.
Precisa de um mundo chamado `test` em `run/saves`. O código de teste (`client/devtest`) não vai para o .jar.

## Espaço
- Voe acima de **Y 330** para sair da atmosfera. Você chega no espaço com a Terra embaixo.
- **Hiperpropulsão**: no espaço, segure **Ctrl + W** em velocidade máxima. O anel rompe a barreira e chega a ~1.800 blocos/s; freia sozinho perto dos planetas.
- **Mapa estelar e piloto automático (J)**: escolha um destino e clique em "Voo automático". O anel sobe, viaja (desviando de planetas no caminho) e pousa. S cancela.
- **Planetas gerados**: além dos planetas das Tropas, o espaço tem planetas novos a cada ~8.000 blocos, sem fim (verdejantes, desérticos, gelados, vulcânicos, oceânicos, cristalinos, fúngicos e rochosos). São sempre os mesmos para a mesma semente do mundo.
- **Alienígenas**: cada planeta tem a sua espécie, com trocas próprias (clique com o botão direito). Nas vilas dos planetas eles moram no lugar dos aldeões. Os planetas das Tropas vendem o material raro de lá (Cristal de Oa, Âmbar de Korugar...).
- Planetas (de perto para longe): Oa, Korugar, Ysmault, Odym, Zamaron, Nok, Okaara, Ryut. O HUD mostra direção e distância.
- Entre num planeta para pousar nele; voe acima de Y 330 no planeta para voltar ao espaço; desça até a Terra para voltar para casa.
- No espaço só o anel ligado dá oxigênio (gasta 1 de carga por segundo). O anel voa 4x mais rápido no vácuo.

## Uniformes
Ligar o anel (G) veste o uniforme da Tropa: ele se forma pixel por pixel, subindo dos pés até a cabeça, com os pixels novos brilhando na cor da Tropa.
Para usar suas próprias texturas: coloque skins 64x64 em `config/lanterna/uniformes/<tropa>.png` (veja o LEIA-ME que o mod cria lá).
Peças 3D de uniforme (capacete, ombreiras...) e troca dos modelos do anel e da bateria: veja `docs/COMO_TROCAR_MODELOS.md` e os moldes em `blockbench_templates/`.

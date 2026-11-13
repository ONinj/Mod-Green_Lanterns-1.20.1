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
1.27 ✅ (0.22) Trajes "segunda pele" (18), Perfil do Lanterna (H: estatísticas e visual, com ajuste da máscara), tela K e HUD de missões novos, voo de combate, piloto que abre caminho, naves destrutíveis pelo anel
1.28 ✅ (0.23) Hiperpropulsão nos planetas (70 blocos/s), NPCs de missão que voltam se sumirem, uniforme sem a segunda camada da skin por cima, ícones novos dos uniformes, inglês completo e polimento de animações
1.29 ✅ (0.24) Manga do uniforme em primeira pessoa com a transformação, laser saindo da mão certa, correção dos NPCs repetidos e das faces brilhando nos trajes
1.30 ✅ (0.25) Cidade futurista dos nativos em cada planeta, NPCs que voltam ao posto, fontes e bordas da cidade sem vazar água
1.31 ✅ (0.26) Megacidades dos povos (400 blocos, monumentos de até 210, ~150 casas, arquitetura própria em cada planeta) e as Baterias Centrais das 7 Tropas com portal para a dimensão de energia
1.32 ✅ (0.27) A Fonte: Chamado da Fonte (provação + juramento), pressão de 2 h, Banho de Luz (+150% de carga e aura), Árvore da Luz com 70 construtos novos, construtos e ícones refeitos, 7 chefes (Corações das Emoções), salas da Fonte e o evento "A Bateria Enfraquece"
1.33 ✅ (0.27.1) Corações das Emoções muito mais fortes (vida ~1300–1600, golpes de 40–60% da vida com aviso e recarga longa, golpe supremo na fase 2) e o comando /lanterna maximo
1.34 ✅ (0.28) Combate contra Lanternas NPC refeito (IA que mira e para para atirar, caixa de acerto no voo, compensação de atraso no laser e nos construtos), megacidades com bairro novo (~70 casas a mais) e mais moradores, Ecos Cinzentos só na floresta (raros nos QGs, nunca nas cidades; em Odym só nas ilhas)
1.35 ✅ (0.29) Naves: sem dano de queda ao pousar, todos os poderes do anel ferem naves (tripulação protegida pelo casco), sair da nave depois de renascer no beliche leva até a nave, Chave da Nave fica na morte e chama a nave com reentrada na atmosfera, sons próprios das naves
1.36 ✅ (0.30) Juramento falado na Bateria Central como primeira missão do anel (microfone, palavra por palavra, PT/EN), comunicador do anel com ligações em grupo e holograma (1ª e 3ª pessoa), voz via Simple Voice Chat, câmera em terceira pessoa por cima do ombro
1.37 ✅ (0.30.1) Tripulação acompanha a inclinação da nave (assento, corpo e visão em primeira pessoa inclinam nas curvas e subidas)
1.38 ✅ (0.31) Favores dos Ves'Kai com Moedas de Gratidão e Lágrimas Índigo mais fáceis de trocar, Cajado da Compaixão nas costas e segurado com as duas mãos, recarga Índigo encostando o anel no cajado (1ª e 3ª pessoa)
1.39 ✅ (0.31.1) Juramentos novos das 9 Tropas (originais, inspirados no espírito dos quadrinhos), em PT/EN: aparecem no chat a cada recarga e são os mesmos recitados na Bateria Central
1.40 ✅ (0.32) História na Terra (Vila Aurora, Base Vigília, Dra. Lívia, estrelas cadentes, cratera do anel), Ritual da Bateria em 3 etapas, receita da Bateria Cinzelada mais difícil, escudo de força sem tremer, animações das criaturas revistas (passos no ritmo do andar, ciclos mais naturais, golpes suavizados)
1.41 ✅ (0.33) Oficina de Construtos (Alt+Z): desenhe construtos em 3D cubinho por cubinho, escolha o comportamento (arma, escudo, disparo, queda do céu, veículo, estrutura) com animações prontas, 8 espaços e presets Alt+1..4
1.42 ✅ (0.34) Oficina: grade 32³ com cubinhos menores, superfície lisa (ou modo quadrado), luz de verdade nos construtos (sombra, borda acesa, reflexo, halo), pincel 1-3, modelos refeitos e animações novas (fio de luz ao montar, faixa de montagem, cópias no golpe e no disparo, rastro e onda de choque na queda, propulsores no veículo, estilhaço ao desmontar)
1.43 ✅ (0.35) Energia da Emoção (Gerador Emocional das 7 emoções, Refinaria, Conversor de Luz), Mineradora Planetária animada nos planetas conquistados (produção offline e ataques), Forjador da guilda; integrações opcionais com Mekanism (10x), Create, JEI e Curios
1.44 ✅ (0.36) Cerimônias de escolha do anel refeitas: cenas 3D por Tropa ligadas ao ritual (cometa, poço de plasma, olhos e raios, aurora, cristais, feixe, moedas, sombras, fusão dos sete), modelo 3D do anel, câmera de cinema, névoa do ritual, visíveis para quem está perto
1.45 ✅ (0.37) Jade Viva (3 níveis na bateria verde), Chamado do Mogo (jaula, planeta no céu, raio que mata e abre poço, 1 h de recarga, recusa contra os guardiões corrompidos, comando para zerar) e aura de energia translúcida na cor da Tropa
1.45.1 ✅ (0.37.1) Aura refeita: energia líquida arredondada e animada em volta do corpo
1.46 ✅ (0.38.0) O planeta Mogo: as 6 missões (cinturão, corrida, ponte, prova, cerco, núcleo) e a Segunda Chance
1.46.1 ✅ (0.38.1) Naves: saída ao lado da nave, colocar sem prender o jogador, hitbox justa; Ecos nas ilhas de Odym; /lanterna laranja
1.46.2 ✅ (0.38.2) Aura: halo contínuo e macio em volta da silhueta inteira (sem peças por parte do corpo)
1.46.3 ✅ (0.38.3) Transformação: o uniforme escorre pelo corpo como líquido a partir da mão do anel
1.46.4 ✅ (0.38.4) Trajes: máscaras refeitas e 9 trajes de energia (um por Tropa)
1.46.5 ✅ (0.38.5) Aura: opção de ligar/desligar e "Iluminar" (luz de tocha na cor da Tropa)
1.46.6 ✅ (0.38.6) Baterias em forma de lanterna, anéis HD e máscaras por cima da segunda camada da skin
1.47 ✅ (0.39.0) Uniforme do anel vira troca de skin: roupa pintada pixel por pixel no corpo, sem espessura; cabelo/chapéu da skin por cima da máscara
1.47.1 ✅ (0.39.1) Traje na skin: ajuste da máscara volta a funcionar, relevo na segunda camada e peças 3D (asas, auréola, espinhos) de volta
1.47.2 ✅ (0.39.2) Waypoints no chão certo; Mogo com o símbolo do anel, andando pelo espaço, Rastro do Mogo e defesa contra a Morte
1.47.3 ✅ (0.39.3) Mogo como planeta vivo; recarga do Índigo em 1ª pessoa corrigida; Fome do Anel Preto vira frenesi
1.47.4 ✅ (0.39.4) Textos que saíam da tela com interface grande: mensagens longas da barra de ação, título e subtítulo vão para o chat; Bateria Cinzelada explica o ritual no chat em passos; HUD do anel, missões, roda de construtos, diário, perfil, oficina, árvore, comunicador e outros cabem em 427x240
1.47.5 ✅ (0.39.5) Tudo em inglês quando o jogo está em inglês: nomes dos trajes (Shaman, Star Heart, Genesis, Phoenix, Specter...), guildas das Tropas e o Conselho, livro do Arquivo da Fonte, títulos dos moradores da Terra, respostas e ajuda dos comandos de teste, descrição do mod e comentários da config (inglês + português)
1.47.6 ✅ (0.39.6) Opção de tirar a máscara do traje (Personalização); anel sem energia apaga a aura, a luz e as partes que brilham do traje; mais minério em Korugar (Fragmento de Pavor) e Nok (Lágrima Índigo); Ves'Kai feridos com textura machucada, ajoelhados apoiados no cajado, acampamento de feridos na base de Nok e cidade mais cheia; mais Ecos Cinzentos em Odym
1.48.0 ✅ (0.40.0) Trocas nunca trancadas por respeito (falta de respeito encarece até +200% e negociar devolve o respeito); Ves'Kai trocam lágrimas por maçã dourada e maçãs por moedas; construtos se formam com energia e um fio de luz sai do anel e encaixa neles (parede, ponte e jaula montam bloco a bloco a partir do fio); picareta na frente da mão; novo construto Broca (míssil de luz com coroas dentadas girando, túnel 3x3 com 2 de fundo, 5x5 no nível 4) para todas as Tropas
1.49.0 ✅ (0.41.0) Missão do Anel Branco destravada (qualquer Guardião/líder lista o que falta e oferece a linha do anel); inimigos não nascem mais em cima das Baterias Centrais; Anel Verde do sono mais raro (6% por noite, a partir da 4ª noite); Anel Negro alcançável no single-player (chance cresce a cada morte, Lanternas Negros caçam em Ryut); defesa do nível compatível com armaduras de outros mods; Mogo a 25–45 mil blocos, mudando de lugar a cada 20 min; sóis que queimam e puxam forte, buracos negros que engolem; texturas novas dos planetas
1.50.0 ✅ (0.42.0) Visão de longe no espaço (jogadores aparecem a até 9 mil blocos: boneco com skin, luz com rastro e nome); 7 planetas lendários fixos a 50 mil blocos com arenas e os chefes Hal Jordan, Larfleeze, Atrocitus, Santo Andarilho, Índigo-1, Carol Ferris e Mão Negra (Tropa inimiga = chefe; mesma Tropa ou aliada = duelo de treino para farmar); Hanks, o planeta-máquina dos Amarelos que anda pelos setores, com a cidadela do Sinestro, sentinelas e 3 missões Amarelas; Insígnias e Fragmentos Lendários
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

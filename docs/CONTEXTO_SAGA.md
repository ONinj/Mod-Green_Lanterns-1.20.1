# Contexto para o chat da SAGA NOVA (ramo `main`)

Leia este arquivo inteiro antes de mexer em qualquer coisa. Ele resume ~2 meses de trabalho do dono com o Claude.
O mod se chama **New Lanterns** na CurseForge (jar `lanterna-<versão>.jar`, modid `lanterna`, pacote `com.lanterna`).

## Os dois ramos (dois chats)
- `main` (ESTE chat): a saga nova. Versão atual **0.56.1** (README: 1.64.1). O trabalho fica no ramo da sessão do Claude até o dono pedir para levar ao `main`.
- `estavel` (outro chat): correções para os jogadores a partir da 0.52.4 (já está na 0.52.5+). Não publique nada da 0.53 na CurseForge; a saga sai primeiro só para os beta testers (pelo Discord).
- Correções do `estavel` que valham para a saga precisam ser trazidas para cá (`git merge origin/estavel` ou cherry-pick, resolvendo conflitos no README/docs/versão). A 0.52.5 (Odym/Morrow, Nok, Zamaron) já foi trazida para o `main`; traga também o que vier depois.

## Regras do dono (sempre)
- Responder sempre em **português**. O dono não programa: explique o resultado de forma simples, sem jargão.
- Toda a parte criativa (lore, nomes, design, ideias, mecânicas) é do dono; o Claude faz o código. Quando ele descrever uma ideia, implemente o que ele pediu; onde faltar detalhe, escolha algo sensato e diga no fim o que escolheu.
- Nomes do universo DC podem ficar (decisão dele). Personagens e chefes novos: nomes originais.
- Comandos do jogo em **inglês** (`/lantern ...`). Lore, chefes e textos seguem o idioma do jogo: sempre **pt_br e en_us** juntos.
- O Anel Branco (Vida) NÃO tem construtos da Morte (preto). Coerência de lore sempre.
- Sem voz/downloads/processos do sistema/bibliotecas nativas (a revisão da CurseForge travou por isso). Não baixar nem rodar jars externos.
- Integrações com outros mods sempre opcionais (só bônus).
- Pensar sempre em **multiplayer e servidor dedicado** (tudo que é visual vai por pacote para quem está perto; nada de código de cliente no servidor).
- Entregar o jar dentro de um **.zip**. Testar no jogo antes de entregar e mandar fotos do teste.
- Não renomear arquivos de modelo/textura que o dono mandou.
- Nunca `rm -rf` com glob depois de `cd`.
- O e-mail do dono é privado.
- O dono tem limite de uso semanal: seja econômico (um teste bem montado no final em vez de vários).

## Ambiente, build e testes
- Forge 1.20.1 (47.x), Java 17, Gradle 8.1.1, GeckoLib. Numa máquina nova: instalar JDK 17 e Gradle 8.1.1 e clonar o repositório.
- Compilar: `gradle compileJava -q 2>&1 | grep error -A3`
- Jar: `gradle build -x test -q` → `build/libs/lanterna-<versão>.jar`
- Teste no cliente sem tela: `xvfb-run -a -s "-screen 0 1280x720x24" gradle runTestClient -Pscene=<cena> -Plargura=1280 -Paltura=720 -q > log 2>&1`
  - Cenas em `src/main/java/com/lanterna/client/devtest/DevAutoTest.java` (`else if (ENABLED && SCENE.equals("x")) sceneX();`). Use `at(ticks, () -> ...)` em sequência, `server(sp -> ...)` para rodar no servidor integrado, `glog("...")` para log (`grep -a "AUTOTEST G"`), `shot("nome")` para foto (`run/screenshots/lanterna_nome.png`), `palco(mc)` cria uma plataforma em 0,200,0.
  - Cenas úteis de exemplo: `emocoes` (Árvore das Emoções, treino, Dois Anéis, cenas), `rota` (piloto automático), `nulo052`, `ritual051`, `capsula`.
  - Jogo em português: acrescentar `lang:pt_br` em `run/options.txt` (e tirar depois).
- Servidor: `gradle runServer` em segundo plano, esperar "Done (" no log, parar pelo rcon/matar o processo. Fazer isso quando mexer em pacotes de rede ou em classes comuns.

## Como lançar uma versão
1. `gradle.properties` → `mod_version`; linha 7 de `src/main/resources/META-INF/mods.toml`.
2. README: linha nova depois da última (`1.61.x ✅ (0.53.x) ...`).
3. `docs/LORE_E_MISSOES.md`: seção nova no fim, explicando para jogadores.
4. Textos: `python3 -c "import sys; sys.path.insert(0,'ferramentas'); from addlang import add; add({pt}, {en})"` (rodar na raiz).
5. Dúvidas que o anel responde no chat: editar `ferramentas/faq.py` e rodar `python3 ferramentas/faq.py` na raiz (gera `assets/lanterna/anel/duvidas.json`). Guia do Portador (F9): `ferramentas/guia.py`.
6. Build, teste, commit com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, `git push origin main`, zip do jar e entregar ao dono com as fotos.

## Modelagem 3D e texturas (como o dono trabalha)
- O dono desenha e modela tudo no **Blockbench** e manda os arquivos; o Claude integra. Ele quer qualidade: cubos pequenos, detalhe, nada "inflado".
- **Anéis**: modelos de item Blockbench (`models/item/ring/anel_<emoção>_<cor>.json`, texture_size 64x64, ~150 cubos), variações `corroido/` (rachado) e `corrompido/` (manchado). Textura em `textures/item/ring/`. O anel aparece na mão (`client/RingHandLayer.java`; o 2º anel na outra mão).
- **Trajes**: NÃO são armadura. São (1) uma camada de base pintada na própria skin, pixel a pixel (`textures/skin_traje/uniforme_<cor>_<wide|slim>.png` 256x256 + `_glow`), mantendo rosto/cabelo/segunda camada do jogador; e (2) peças 3D GeckoLib rentes ao corpo (`geo/` + `client/uniform/RingUniformRenderer.java`). `client/uniform/PeleTraje.java` monta a skin (máscara, corrupção, metade do traje do 2º anel); `UniformTransition.java` faz o traje "escorrer" ao vestir (`refazer(id)` repete a animação); `Corrompida.java` faz as versões cinza/preto e branco.
- **Entidades** (espécies, chefes, NPCs): GeckoLib, `geo/entity/*.geo.json` + `animations/` + texturas em `textures/entity/`. Escala no renderer.
- **Baterias**: formato de lanterna da DC (boca larga em cima e embaixo, corpo redondo, haste e alça). A da Compaixão é um cajado na mão esquerda; a da Morte uma caveira preta; a Vida não tem bateria.
- Partículas próprias: `particle/GlowOrbOptions.of(cor, tamanho)`. Cenas de câmera: `mixin/CameraMixin` chama `RitualCliente.camera` e `client/vida/CenaEmocoes.camera` (devolvem {x,y,z,yaw,pitch}).

## Mapa do código
- Anel, carga, poderes: `power/RingPowers.java` (tick do anel; cor da luz; traje do treino; 2º anel), `capability/RingData.java` (slots, `segundoSlot`, `traje()/segundo()`), `item/PowerRingItem.java`.
- Rede: `network/ModNetwork.java`; o que os outros veem: `network/SyncVisibleRingPacket.java` → `client/ClientVisibleRings.java`.
- Construtos: `construct/ConstructType.java` (`disponivel(ringCorps, portador, Contexto)`), `construct/Constructs.java`, roda `client/construct/ConstructWheelScreen.java`, Árvore da Luz `construct/fonte/`.
- **Saga (0.53)**: `vida/Emocoes.java` (Árvore das Emoções: 7 aspectos, Insígnia desperta, fragmentos 3/5/8/12/16 até o nível 6, limite de construtos, +1,5% força/nível, treino, Dois Anéis por 20 fragmentos, flag `dominada` quando os 7 chegam ao 6), `client/vida/EmocoesScreen.java` (tela, botão "Emoções" na aba Lanterna), `client/vida/ClientEmocoes.java`, `client/vida/CenaEmocoes.java` (cenas do treino e do 2º anel), slot do 2º anel em `menu/LanternMenu.java`.
- Corrupção/Morte/Purificação: `morte/` (Corrupcao, Arauto, RitualPurificacao, PurificacaoBranca).
- Chefes: `lore/entity/boss/Bosses.java` (Nulo, 5 fases; Morrow etc.), `tropa/` (Bosses das Tropas, Fragmentos), `lendarios/` (8 lendários, Insígnias em `lore/LoreItems.INSIGNIAS`).
- Voo/espaço: `client/flight/` (`ClientFlight`, `Autopilot`, `Rota`), `space/` (`SpaceBodies`, `Perigos`, `ProceduralPlanets`).
- Mundo/planetas: `lore/world/PlanetSites.java` (cidades), `terra/` (Cidade Aurora), `mogo/`, `hanks/`.
- Missões: `mission/`. IA do anel (só chat): `ia/`.

## A saga do Necron — o que o dono já decidiu

O Ser da Morte se chama **Necron** (com C). Saga pós-Nulo inspirada na "Noite Mais Densa" (sem copiar). Depois virá outra saga (Parallax).

**Tamanho:** a saga tem que ser LONGA: no mínimo ~5 horas de jogo direto até terminar, rica em cutscenes, falas e, acima de tudo, **jogabilidade** (o principal do mod é jogar, não só farmar).

**Duas linhas (dois finais)** — decidido pelo dono, a fazer DEPOIS que a linha do herói estiver toda polida:
- **Herói** (contra o Necron): vence; é a linha que está sendo feita agora.
- **Corrompido** (ajuda o Necron a destruir tudo): o Necron vence, as Tropas caem, sobram poucos Lanternas, e na saga seguinte eles se juntam para purificar o jogador (o jogador não tem escolha nisso na próxima saga). A escolha desta saga reflete na próxima.

**Feito (0.53.0–0.56.1):**
1. 0.53: Árvore das Emoções do Anel da Vida, treino, Dois Anéis (`vida/Emocoes.java`, `client/vida/`).
2. 0.54.0: cutscene da divisão (`client/necron/CinemaNecron.java`, tela de cinema própria: Embrião escurecendo, o anel se desfaz em 7, só o da Tropa de origem volta, Ryut se parte, Necron sai, voa e cai na Terra). Modelos `necron` e `embriao_vida` gerados por `ferramentas/modelos_saga.py` (o dono pode refazer no Blockbench com os mesmos nomes de arquivo e ossos).
3. 0.54.1: chamado de Ryut + piloto travado até Ryut (`necron/SagaNecron.java`, `client/necron/ClientSaga.java`, `Autopilot.travar`); Ryut partida e fechada (`client/space/RyutPartida.java`).
4. 0.54.2: cratera na Terra (`necron/Cratera.java`: terra morta gerada aos poucos, Bateria Central da Morte), `necron/NecronEntity.java`, servos imunes a quem tem um anel só.
5. 0.54.3: a fuga (`necron/Fuga.java`, `client/necron/CenaFuga.java`): resgate pelo Lanterna da Tropa, conversa no planeta, missão Duas Luzes.
6. 0.55.0: fenda sobre a Bateria Central e a defesa em 3 ondas (`necron/Defesa.java`, portal desenhado em `client/necron/RenderFenda.java`).
7. 0.55.1: pedidos de ajuda e quedas do Amor/Índigo/Esperança (`necron/Pedidos.java`, `necron/Queda.java`, `client/necron/CenaPedido.java`, `CinemaQueda.java`).
8. 0.55.2: polimento (voo do Necron, resgate suave, cúpula do escudo, fenda como portal).
9. 0.56.0: A Terra Escurece (3 etapas, `necron/TerraEscurece.java`) e a batalha final em 4 fases com 15 aliados (`necron/BatalhaFinal.java`).
10. 0.56.1: equilíbrio: nível máximo 250 (`tropa/Nivel.java`), nível cai para 90 na divisão, a Morte tira % da vida máxima, dano no Necron pelo nível de quem bate.

Missões da linha `necron` em `necron/MissoesNecron.java` (necron_1 a necron_7; necron_4_<tropa> = defesa de uma Tropa). Comandos de teste: `/lantern saga replay|start|reset|impact`. Cenas de teste em `DevAutoTest`: `necron`, `chamado`, `cratera`, `fuga`, `defesa`, `pedidos`, `queda`, `final`, `nivel`.

**Vozes:** o dono vai gravar/gerar as vozes depois (modelo de IA pago). Já está preparado: `client/voz/Falas.java` toca `sounds/voz/<idioma>/<chave>.ogg` se existir (registrar com `ferramentas/vozes.py`); sem áudio, silêncio (o dono pediu em 0.56.8 para tirar o falatório de sílabas; não colocar de volta). Lista de falas: `ferramentas/falas.py` → `docs/FALAS.csv` (acrescentar os padrões de chave novos no falas.py a cada capítulo). Toda fala nova de personagem tem que chamar `Falas.falar(chave, perfil)` (ou entrar no mapa CHAT do Falas).

**Capítulos aprovados pelo dono (linha do herói, ~5 h):** 1 A Divisão (feito) · 2 A Queda/Vila Aurora (feito, 0.56.2) · 3 Duas Luzes: treino, duelo, primeira fenda (feito, 0.56.3) · 4 A Defesa da Tropa + destruir 3 âncoras da Morte antes · 5 Os Pedidos: uma missão própria por planeta antes de cada defesa · 6 As Quedas: resgatar sobreviventes e o núcleo da bateria no planeta caído · 7 A Terra Escurece: uma missão por etapa (fendas perto das cidades, defender Aurora, achar onde o **Embrião da Vida** dorme escondido na Terra e protegê-lo) · 8 A Última Luz: reunir os aliados antes (o número de aliados depende disso) · 9 A Purificação: o Embrião sai da Terra, se reanima e purifica tudo (decisão do dono); ritual da Terra, Lanternas corrompidos nos 3 planetas caídos, epílogo (gancho para Parallax). Entregar capítulo por capítulo, testado.

**Próximo:** a purificação (Terra e Lanternas corrompidos) e, principalmente, encher a linha do herói de jogabilidade até ~5 h (o Claude propôs um esqueleto de capítulos ao dono; seguir o que ele aprovar).

Valores escolhidos pelo Claude que o dono ainda pode mudar: custos dos fragmentos, +1,5% por nível, lado esquerdo do traje para o 2º anel, CONTROL fora do 2º anel; na saga do Necron: falas e legendas, ordem dos pedidos e das quedas, 3 ondas por defesa, vida do Necron (1000) e a curva de dano por nível, corte para o nível 90, mínimos de 6%/15%.

Não testado com dois jogadores reais: Purificar do Anel Branco; cenas vistas por outro player.

**Andamento:** 0.56.4 Cap. 4 (`necron/Ancoras.java`) · 0.56.5 Cap. 5 socorro (`necron/Socorro.java`, missões `necron_s_<tropa>`) · 0.56.6 Cap. 6 ruínas das Tropas caídas (`necron/Ruinas.java`, missões `necron_q_<tropa>`, item `nucleo_bateria`; os núcleos guardados ficam na marca do mundo `saga:nucleo:<tropa>`, para usar na purificação). Próximo: Cap. 7 A Terra Escurece.
0.56.7 Cap. 7 (`necron/Escurece.java`: missões necron_5 → 5b → 5c → 5d → 5e → 5f, depois necron_6; o Santuário do Embrião fica em Aurora + (-320, 170), câmara a 22 blocos de fundo, marca `saga:santuario`; o cliente desenha o Embrião pela entrada "embriao" das fendas). Próximo: Cap. 8 A Última Luz (recrutar aliados antes).
0.56.8 ajustes do dono: sem falatório; pedidos/agradecimentos próprios de cada Tropa; trégua dos Lanternas NPC contra os servos (LanternaEntity.hostilA, de Ryut partida até a derrota do Necron); laser com dois anéis (2º feixe papel=1, CastAnim.DUPLO); aba do 2º anel na roda.
0.56.9 Cap. 8 (`necron/UltimaLuz.java`, missão necron_6a; BatalhaFinal usa UltimaLuz.exercito) e Cap. 9 (`necron/Purificacao.java`: necron_7 despertar + cura da Terra, necron_8 corrompidos e líderes Aelira/Tovrek/Irmã Calyx com restauração do planeta, necron_9 Ritual da Terra e epílogo de Parallax; marca `saga:necron_fim`). **A linha do herói está completa.** Falta: a linha corrompida (segundo final), quando o dono pedir.
0.57.0 o segundo final: `necron/Tentacao.java` (morte para a Morte → 35% / queda na batalha → 20%; tela `client/necron/TentacaoScreen.java`; Não = anéis presos + `EspectroEntity` com `client/necron/EspectroRenderer.java` (skin do dono + traje_preto); Sim = Corrupcao.converter + linha corrompida) e `necron/LinhaCorrompida.java` (missões necron_c1..c4; marcas `saga:embriao_preso`, `saga:necron_venceu`, `saga:noite_t:`). Comando `/lantern saga tempt`. As duas linhas desta saga estão completas.
0.57.1 acabamentos + **gancho da próxima saga** em `necron/ProximaSaga.java`: `ProximaSaga.caminho(player)` = "heroi" | "corrompido" | "" (tag `proxima_saga` no SagaNecron.tag do jogador); `destinoParallax(player)`; marcas do mundo `saga:proxima:parallax` (os dois finais) e `saga:proxima:purificadores` (final corrompido). **Próxima saga (decisão do dono, para mais tarde):** o jogador corrompido é purificado pelos Lanternas que restaram, sem escolha, e acaba se tornando o próprio **Parallax**; o herói enfrenta o Parallax que acordou em Korugar. Comando `/lantern saga next`.
0.58.0 cutscenes no mundo (`necron/Cenas.java` servidor + `client/necron/CenaMundo.java` cliente: queda, despertar, juramento, noite, espectro, parallax, parallax_anel; câmera pelo CameraMixin) e missões novas: corrompida `necron/CorrompidaMeio.java` (necron_c2b A Caçada, necron_c2c Aurora Apagada) e `necron/Resistencia.java` (necron_c3b); epílogo `necron/Epilogo.java` (necron_10, Medalha da Última Luz; Fragmento da Noite Eterna). Nas missões da saga com duas conversas, cada NPC tem a fala `mission.lanterna.<id>.talk.<papel>` (NpcDialogs).

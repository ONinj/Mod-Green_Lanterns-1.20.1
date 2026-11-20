# Contexto para o chat da SAGA NOVA (ramo `main`)

Leia este arquivo inteiro antes de mexer em qualquer coisa. Ele resume ~2 meses de trabalho do dono com o Claude.
O mod se chama **New Lanterns** na CurseForge (jar `lanterna-<versão>.jar`, modid `lanterna`, pacote `com.lanterna`).

## Os dois ramos (dois chats)
- `main` (ESTE chat): a saga nova. Versão atual **0.53.1** (README: 1.61.1). Próximas: 0.53.2, 0.54.0...
- `estavel` (outro chat): correções para os jogadores a partir da 0.52.4 (já está na 0.52.5+). Não publique nada da 0.53 na CurseForge; a saga sai primeiro só para os beta testers (pelo Discord).
- Correções do `estavel` que valham para a saga precisam ser trazidas para cá (`git merge origin/estavel` ou cherry-pick, resolvendo conflitos no README/docs/versão). **Pendente:** trazer a 0.52.5 (Odym/Morrow, Nok, Zamaron) e o que vier depois.

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

## A saga nova — o que o dono já decidiu
Base: saga pós-Nulo inspirada na "Noite Mais Densa" (sem copiar). Foco no Anel da Morte, uso das insígnias lendárias, cutscenes com NPCs bem modelados. Depois virá outra saga (Parallax).

Feito (0.53.0–0.53.1):
1. **Árvore das Emoções** do Anel da Vida: sem missões de ir e voltar, é farm dos chefes (Insígnias e Fragmentos Lendários). Cada aspecto começa com 3 construtos e abre mais a cada nível.
2. **Treino**: o anel continua branco; traje e construtos viram os da Tropa treinada; cena com o traje se refazendo.
3. **Dois Anéis**: o corpo preparado aguenta um 2º anel (fica para sempre, mesmo sem o Anel da Vida); o 2º empresta só construtos de defesa/cura/ajuda (DEFENSE e UTILITY); metade do traje (lado esquerdo) na cor dele; anel na outra mão; cena ao colocar.

Próximo (combinado com o dono):
4. **Ritual da divisão**: quando começa o primeiro evento do Ser da Morte (o nome NÃO é Nekron, ele vai dar outro), o player perde o controle do Anel da Vida. Cutscene: o player estende a mão, o anel se desfaz em **7 anéis** girando em volta dele; fica só o da Tropa escolhida no começo (com as mesmas estatísticas), os outros 6 partem para o espaço. Mensagem: ele não é mais digno; o Ser da Morte nasceu/foi libertado quando o Nulo morreu; a entidade da Vida (o "embrião") fica dormente/enfraquecida. Gatilho natural: a flag `dominada` (os 7 aspectos no nível 6).
5. Depois: o despertar do Ser da Morte, chefes e mecânicas da saga (o dono descreve antes).
6. Futuro: voz (pt e en) para os personagens que contam a história (só depois, e sem quebrar a regra de não baixar nada).

Valores escolhidos pelo Claude que o dono ainda pode mudar: custos dos fragmentos, +1,5% por nível, lado esquerdo do traje para o 2º anel, CONTROL fora do 2º anel.

Não testado com dois jogadores reais: Purificar do Anel Branco; cenas vistas por outro player.

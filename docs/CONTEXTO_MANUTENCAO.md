# Contexto para o chat de manutenção (linha estável 0.52.x)

Leia este arquivo inteiro antes de mexer em qualquer coisa.

## Para que serve este ramo
- Ramo `estavel`: correções de bugs e ajustes para os jogadores, a partir da **0.52.4** (a versão publicada na CurseForge).
- O ramo `main` é outro chat: o desenvolvimento da saga nova (0.53+, Árvore das Emoções, Dois Anéis, Noite Mais Densa). **Não** traga nada da 0.53 para cá.
- Versões deste ramo: 0.52.5, 0.52.6, ... (README: 1.60.5, 1.60.6, ...). Correção que também valha para a saga: avise o dono para levar ao `main` também.

## Regras do dono (sempre)
- Responder sempre em português.
- Sem arte ou texto oficial da DC: nomes originais para personagens novos.
- Comandos do jogo em inglês. Lore e nomes de chefes seguem o idioma do jogo (pt_br / en_us).
- O Anel Branco (Vida) NÃO tem construtos da Morte (preto): coerência.
- Não baixar nem rodar jars externos. A voz do anel foi removida na 0.52.4; não voltar com Vosk/Piper/downloads/processos do sistema (a revisão manual da CurseForge travou por causa disso).
- Integrações com outros mods sempre opcionais.
- Entregar o jar dentro de um `.zip`.
- Não renomear arquivos de modelo (os modelos e texturas foram desenhados pelo dono).
- Pensar sempre em multiplayer e servidor dedicado.
- Nunca `rm -rf` com glob depois de `cd`.
- O e-mail do dono é privado.

## Montar o ambiente (sessão nova)
- Forge 1.20.1, Java 17, Gradle 8.1.1. Instale o JDK 17 e o Gradle 8.1.1 (ou use o wrapper se existir) e clone:
  `git clone -b estavel https://github.com/ONinj/mod-green_lanterns-1.20.1`
- Compilar: `gradle compileJava -q 2>&1 | grep error -A3`
- Jar: `gradle build -x test -q` → `build/libs/lanterna-<versão>.jar`
- Teste no cliente (sem tela): `xvfb-run -a -s "-screen 0 1280x720x24" gradle runTestClient -Pscene=<cena> -Plargura=1280 -Paltura=720 -q`
  As cenas ficam em `client/devtest/DevAutoTest.java` (logs com `AUTOTEST G`, fotos em `run/screenshots/lanterna_*.png`).
  Para testar em português: `lang:pt_br` no `run/options.txt`.
- Servidor: `gradle runServer` em segundo plano, esperar "Done (", parar pelo rcon ou matando o processo.

## Como lançar uma versão
1. `gradle.properties` → `mod_version`; linha 7 do `src/main/resources/META-INF/mods.toml`.
2. README: uma linha nova depois da última (`1.60.x ✅ (0.52.x) ...`).
3. `docs/LORE_E_MISSOES.md`: uma seção nova no fim, explicando a mudança para jogadores.
4. Se mudar algo que o anel explica no chat, atualizar as dúvidas do anel em `src/main/resources/assets/lanterna/anel/duvidas.json` (pt e en), e os textos em `assets/lanterna/lang/pt_br.json` e `en_us.json` (sempre os dois idiomas).
5. Build, testar, commit com os trailers abaixo, `git push origin estavel`, zip do jar e entregar.

Trailers dos commits:
```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Onde fica o quê (o essencial)
- Anel, carga, poderes: `power/RingPowers.java`, `capability/RingData.java`, `item/PowerRingItem.java`.
- Construtos: `construct/ConstructType.java`, `construct/Constructs.java`, roda em `client/construct/ConstructWheelScreen.java`, Árvore da Luz em `construct/fonte/`.
- Voo e espaço: `client/flight/ClientFlight.java` (hiperpropulsão por nível), `client/flight/Autopilot.java` + `Rota.java` (rota desviando de planetas/sóis/buracos negros), `space/SpaceBodies.java`, `space/Perigos.java`, `space/ProceduralPlanets.java`.
- Traje: `client/uniform/` (`PeleTraje` pinta o traje na skin, `RingUniformRenderer` os modelos 3D, `UniformTransition` a transformação, `Corrompida` a corrupção).
- Missões e lore: `mission/`, `lore/`, `mogo/`, `hanks/`, `terra/` (Cidade Aurora: `TerraSite`), `morte/` (corrupção, purificação, Arauto).
- Chefes: `lore/entity/boss/Bosses.java` (Nulo com 5 fases), `tropa/` (Bosses das Tropas), `lendarios/`.
- Cápsula Estelar (baú por jogador): `block/CapsulaEstelarBlock(Entity).java`.
- IA do anel (só chat): `ia/`, `client/ia/`. `VozAnel` ficou só com o idioma.

## Estado da 0.52.4
- O que entrou desde a 0.41 está resumido nas linhas do README (1.4x a 1.60.4) e em `docs/LORE_E_MISSOES.md`.
- Não testado com dois jogadores reais: o dom Purificar do Anel Branco; a velocidade de voo dentro dos planetas.
- Saga "Noite Mais Densa" (1.0): é do outro chat, não começar aqui.

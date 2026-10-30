# Lore, missões, construtos e afinidade (0.12.0)

## Planetas (nomes do mod, características da lore)
| Planeta | Tropa | Na lore | Raça | Boss | Minério | Drop do boss |
|---|---|---|---|---|---|---|
| Odym | Azul | Lumeris | Aelínos | Morrow | Pérola do Amanhã | Lágrima de Aurora |
| Oa | Verde | Veridia | Veltari + Guardiões do Universo | Vigia Partido | Jade Estelar | Núcleo de Vontade |
| Okaara | Laranja | Aurumvex | Ghallos | Mammok | Âmbar da Posse | Âmbar Primordial |
| Ysmault | Vermelho | Kharuun | Brakkari | Vorgath | Ira Bruta | Coração de Vorgath |
| Zamaron | Violeta | Selenaar | Zaëlith | Nyrielle | Cristal Devoto | Coração de Nyrielle |
| Korugar | Amarelo | Nyxkor | Skrell'Vath | Thessara | Fragmento de Pavor | Coroa de Pavor |
| Nok | Índigo | Tal'Eun | Ves'Kai | Penitente Sem Rosto | Lágrima Índigo | Coração do Penitente |
| Ryut | Preto | Umbrall | Silentes + O Coletor | Devorador de Ecos | Obsidiana do Fim | Núcleo do Fim |
| Gênese | Branco | Gênese (escondido) | Brotos | O Nulo | — | Fragmento Primordial |

Cada planeta tem:
- uma **base** em X 0, Z 0, com a Forja do Espectro, a bateria da Tropa, o líder do povo e 3 alvos de treino;
- uma **arena** em X 120, Z 0, com o Altar do Desafio.

Em Oa, a base é a cidadela: Bateria Central gigante, Instrutor Veltari (Mestre Toran) e os três Guardiões do Universo. Em Gênese ficam o Altar da Vida (0, 0) e a rachadura do Nulo (0, −24).

## Como ser escolhido na Terra
Além dos rituais de cada Tropa (`/lanterna rituais`), há dois caminhos novos:

| Caminho | Como funciona |
|---|---|
| O Chamado | Conquistas de peso chamam a atenção dos anéis: diamante, Nether, encantar, curar aldeão zumbi, fortaleza, vara de blaze, Olho do Fim, herói da vila, caçador de monstros, Wither e Dragão. A cada 3 conquistas da lista, um anel aleatório escolhe você. A lista e o número ficam no config. |
| O Anel Caído | Uma cratera de meteoro na Terra, com uma coluna de fumaça para achar de longe. No meio, dentro da casca do meteoro, um baú guarda o Anel Apagado. Fabrique uma Bateria Cinzelada, coloque no chão e clique nela com o anel: a bateria se parte e o anel escolhe uma Tropa ao acaso. Ele chega com só 10% de carga. |

- A cratera aparece em `/locate structure lanterna:anel_caido`, e o cartógrafo (nível Aprendiz) vende o Mapa do Anel Caído.
- Receita da Bateria Cinzelada: tijolos de pedra cinzelados, fragmento de ametista, lingotes de ferro e uma lanterna no meio.
- As baterias de cada Tropa também têm receita (aparecem no livro de receitas quando você pega um anel).

## Depois de escolhido
- Todo anel novo leva o portador no piloto automático até o planeta da sua Tropa (o Preto vai para Ryut). Essa primeira viagem não gasta carga.
- Em cada planeta das Tropas mora um **Guardião Emissário**: ninguém precisa ir a Oa para começar a lore.
- A linha principal começa no planeta da sua Tropa e dá a volta pelos outros. No seu próprio planeta você não junta minério nem forja: o anel já é seu. Só ganha o respeito do povo e vence o boss (o drop entra na fusão).

## Quando o Lanterna morre
- A bateria sempre fica onde você morreu. Ela não some com o tempo e não queima.
- Com 50% de carga ou mais, o anel volta sozinho para a sua mão, mas gasta 25% da carga. O que sobra dá para voltar e buscar a bateria (ou recarregar na bateria da base do seu planeta).
- Com menos de 50%, o anel não tem força para voltar e fica no chão junto com a bateria.
- O anel Branco sempre volta. Com a regra `keepInventory` ligada, nada cai.
- A mensagem no chat diz as coordenadas e o mundo onde a bateria ficou.

## Tropas inimigas
Os povos tomam o lado da Tropa do seu planeta. Lanternas de Tropas inimigas são atacados e ninguém faz trocas com eles até conquistarem o respeito daquele povo. Os líderes e os Guardiões conversam com todos.

| Tropa | Inimigos |
|---|---|
| Verde | Amarelo, Vermelho, Laranja, Preto |
| Amarelo | Verde, Azul, Vermelho, Laranja, Preto |
| Vermelho | Verde, Amarelo, Azul, Laranja, Preto |
| Azul | Amarelo, Vermelho, Laranja, Preto |
| Violeta | Laranja, Preto |
| Índigo | Laranja, Preto |
| Laranja | todos menos o Branco |
| Preto | todos |

## Linha de missões
1. **Início** (cada Tropa tem a sua):
   - O anel verde leva o jogador para Oa no piloto automático. As outras Tropas mostram o caminho no HUD.
   - Depois: falar com o líder, treinar construtos nos alvos e se apresentar aos Guardiões em Oa.
2. **O Caminho do Branco**: Odym → Oa → Okaara → Ysmault → Zamaron → Korugar → Nok. Em cada planeta:
   - viajar até ele;
   - ganhar o respeito da raça;
   - minerar 8 minérios;
   - derrotar o boss;
   - forjar o anel.
3. **Gênese**: juntar os 7 anéis e os 7 drops (Gênese aparece), fundir o Anel Branco no Altar da Vida e derrotar o Nulo.
4. **Opcional**: O Silêncio de Ryut (Coletor, Devorador de Ecos, Anel Preto), oferecida pelos Guardiões. A Patrulha (Ecos) pode ser repetida e é oferecida pelo Instrutor.

## Fabricar anéis
- **Anel Vazio**: receita na bancada com 4 diamantes, 4 Méritos e 1 lingote de netherite. Também é recompensa de cada boss na linha principal, e os Veltari o vendem.
- **Forja do Espectro**: Anel Vazio + drop do boss + 4 minérios do planeta, mais o ritual da emoção (10 s perto da forja):

| Planeta | Ritual |
|---|---|
| Odym | céu aberto, de noite ou na chuva, com a vida cheia |
| Oa | ter vencido o Vigia Partido |
| Okaara | 16 lingotes de ouro e ninguém num raio de 32 blocos |
| Ysmault | vida pela metade ou menos |
| Zamaron | outro jogador ou um bicho domado perto |
| Korugar | 10 s na escuridão sem fugir |
| Nok | nenhum animal pacífico morto nos últimos 3 dias do jogo |
| Ryut | 2 corações ou menos |

- Com o drop do boss da **sua** Tropa na mão, a forja melhora a bateria: o anel passa a ter 50% a mais de carga.
- Cada boss derrota dá **2 drops**: um para a forja e outro para o Altar da Vida.
- O Anel Branco **não** é mais escolhido por ritual (o Totem): só nasce da fusão.

## Teclas
| Tecla | O que faz |
|---|---|
| Z (segurar) | roda de construtos |
| X | usa o construto escolhido |
| Alt+1..4 | presets (na roda, passe o mouse e aperte 1–4 para gravar) |
| M | Diário do Lanterna (missões, povos, afinidade) |
| J | mapa estelar |

## Construtos
| Categoria | Construtos |
|---|---|
| Ataque | Laser do Punho (segurar), Punho Gigante, Rajada, Martelo Gigante, Lâminas Giratórias |
| Controle | Jaula, Mão Gigante |
| Defesa | Parede, Escudo-Bolha |
| Utilidade | Picareta (segurar), Ponte, Gancho |

- Alguns construtos só liberam com o nível de afinidade (o diário mostra quais).
- **Força** = afinidade × Tropa. A afinidade vai de 1,0 a 2,0. A Tropa multiplica por 1,5 no Branco, 1,35 no Preto e 1,0 nas outras.

## Bônus do anel (com o anel ligado e com carga)
| Nível de afinidade | Fome | Cura | Soco (mão vazia) | Construtos |
|---|---|---|---|---|
| Desconfiado | +1 comida a cada 20 s | ½ coração a cada 12 s | +1,5 | ×1,0 |
| Tolerante | a cada 17 s | a cada 10 s | +2,5 | ×1,2 |
| Aceito | a cada 14 s | a cada 8 s | +3,5 | ×1,4 |
| Confiável | a cada 11 s | a cada 6 s | +4,5 | ×1,6 |
| Vinculado | a cada 8 s | a cada 4 s | +5,5 | ×1,8 |
| Uno com o anel | a cada 5 s | a cada 2 s | +6,5 | ×2,0 |

- Cada cura gasta 1 de carga. O soco e os construtos ainda multiplicam por 1,35 no Preto e 1,5 no Branco.
- O multiplicador dos construtos vale para o dano e para a força (empurrão, duração).

## Afinidade (aceitação do anel)
- Fica gravada no próprio anel e cresce com o tempo de anel ligado, os construtos e as missões.
- Níveis: Desconfiado, Tolerante, Aceito, Confiável, Vinculado, Uno com o anel.
- No nível Desconfiado o anel às vezes hesita.

## Comandos de teste
| Comando | O que faz |
|---|---|
| `/lanterna missao <id>` | começa uma missão |
| `/lanterna afinidade <pontos>` | soma afinidade (24000 = nível máximo) |
| `/lanterna respeito <raça> <valor>` | soma respeito com a raça |
| `/lanterna genese` | revela Gênese |
| `/lanterna reconstruir` | constrói de novo a base e a arena do planeta onde você está, na próxima vez que chegar perto |
| `/lanterna resetar` | zera anel e lore |

## Trocar os modelos
- Raças e bosses: `geo/entity/<nome>.geo.json`, `textures/entity/<nome>.png` (+ `_glowmask.png`) e `animations/entity/<nome>.animation.json`.
- Animações: `misc.idle`, `move.walk`, `attack.strike` e as extras de cada boss.
- O Guardião do Universo (`guardiao`) e o Alvo de Treino (`alvo_treino`) foram feitos por código; dá para trocar pelos seus, com os mesmos nomes de arquivo.
- Ossos que o mod mexe:
  - Brakkari `chifre_1..6`
  - Skrell'Vath `mascara_lider`
  - Veltari `body` (reverência)
  - Zaëlith `fio_de_luz`
  - Penitente `rosto_1..8`
  - Nyrielle `orbita_1..6`
  - Devorador `cabeca_*`
  - Guardião `halo`


---

# 0.13 — Tropas vivas, naves e nível

## Nível e postos (separado da afinidade)
- XP vem de: piratas (30), capitães (250), Lanternas inimigos (60, oficial 120), bosses (400), Ecos (10), outros hostis (2), naves destruídas (200; Capitânia 1200), saquear destroços (25), primeira visita a um planeta (150) e missões/contratos.
- Nível 1 a 50. Cada nível custa 100 + 40×nível de XP (~54 mil no total).
- A cada 5 níveis: +1 coração. Construtos e voo gastam 1% menos carga por nível (até 40%). Força dos construtos e do soco: +1% por nível.
- Postos (recompensa ao subir):
  | Nível | Posto | Presente |
  |---|---|---|
  | 10 | Patrulheiro | insígnia + nave Lanceira |
  | 20 | Oficial | insígnia + nave Patrulha |
  | 30 | Veterano | insígnia + 3 núcleos propulsores + 8 diamantes |
  | 40 | Guarda de Honra | insígnia + 16 Méritos |
- O HUD do anel mostra nível, barra de XP e posto.

## Ordem livre das missões
- Cada anel só pode ser forjado a partir de um nível: Odym 5, Oa 8, Okaara 12, Ysmault 16, Zamaron 20, Korugar 25, Nok 30, Ryut 32, Gênese 35.
- Fale com o Guardião ou o líder de qualquer planeta liberado para pegar a linha dele, na ordem que quiser.
- Sem nenhum planeta liberado, o diário passa um "treino" (subir até o nível pedido) usando contratos e encontros.

## Contratos (missões secundárias)
- Onde pegar: no Quadro de Contratos da base do planeta da **sua** Tropa, ou no **painel (console) dentro da sua nave**. Os quadros de outras Tropas e as naves de outros donos não abrem. O Lanterna Branco usa os contratos de Oa.
- 4 contratos novos por dia, até 3 ativos. Tipos: caçar piratas, derrubar um capitão (com uma nave pirata estacionada que dá para roubar), limpar Ecos, invasores, resgatar um Lanterna ferido, entrega para outro planeta e derrubar uma nave pirata.
- O alvo aparece quando você chega a 110 blocos do local.
- Se faltarem alvos vivos para terminar (um caiu num buraco, morreu na lava, foi morto por outro bicho), chegam reforços a cada 30 s enquanto você estiver perto.
- Alvos mortos por aliados ou por outra coisa perto de você (até 96 blocos) também contam.
- **Desistir** de um contrato destrói o local (some com os alvos, a fogueira e os destroços) e coloca um contrato novo no mesmo espaço do quadro, em outro lugar.

## Lanternas NPC e Tropas inimigas
- 18 espécies com 3 corpos (padrão, grande e pequeno), cada uma com Tropa preferida.
- Lanternas voam, atiram raios do anel, criam martelos e escudos. Os da sua Tropa ajudam na briga; os de Tropas inimigas atacam.
- Piratas usam pistolas de plasma; capitães são mais fortes e deixam o Blaster de Capitão.
- Mercadores aparecem nos cargueiros e fazem trocas.

## Naves
- Tipos: Lanceira (1 lugar), Patrulha, Cargueiro, Pirata e Capitânia Pirata, além de destroços para saquear.
- Item de nave: clique num bloco para colocar. Nave sem dono: monte nela para tomar e ganhar a Chave da Nave.
- Controles pilotando: W/A/S/D e mouse, Espaço sobe, **C** desce, Ctrl turbo, **X** atira, **B** abre/fecha a porta, **H** abre o hangar (Capitânia).
- **Shift + clique** na nave: entra no interior. Clique na porta externa da sala para sair (o alçapão do chão foi tirado).
- Blocos do interior:
  | Bloco | O que faz |
  |---|---|
  | Console (painel) | contratos da sua Tropa |
  | Holomapa | abre o mapa estelar |
  | Estação de recarga | enche o anel (qualquer Tropa); depois espera 5 min |
  | Cadeira | clique para sentar, Shift levanta |
  | Armário | guarda 27 itens |
  | Beliche | renascer na nave, cura um pouco e, se for noite lá fora, passa a noite |
  | Luz do teto | clique liga/desliga |
- Destroços não são sólidos: dá para andar por dentro deles; clique neles para saquear.
- Fragmento de Casco na mão + clique: conserta a nave.
- A nave viaja com quem está dentro entre a Terra, o espaço e os planetas.

## Mapa estelar (J)
- Campo de busca acima da lista: procure pelo nome do planeta, pelo tipo (gelado, vulcânico, oceânico...) ou pela Tropa (verde, amarelo...). Funciona em português e inglês. Enter escolhe o primeiro resultado e centraliza o mapa nele; a busca olha bem mais longe que a lista normal.

## Encontros no espaço
- Voando no espaço às vezes aparecem: piratas, a Capitânia pirata (nível 25+), patrulhas da sua Tropa, patrulhas inimigas, cargueiros e destroços.

## Prova da Emoção (nível 50)
- Junte 3000 pontos da emoção da sua Tropa (Verde: lutar ferido; Amarelo: espalhar medo; Vermelho: causar dano; Azul: curar e estar com aliados; Laranja: coletar; Índigo: ver outros se curarem; Violeta: estar perto de quem protege; Preto: mortes por perto), derrote 3 capitães piratas e fale com o Guardião do seu planeta.
- Recompensa: o anel passa a se recarregar da sua própria emoção (um pouco o tempo todo e mais quando você sente a emoção da Tropa).

## Novos comandos de teste
| Comando | O que faz |
|---|---|
| `/lanterna xp <n>` | soma XP |
| `/lanterna spawn <espécie> [papel= tropa= roupa= pele= oficial corrompido]` | cria um NPC |
| `/lanterna encontro <pirata\|capitania\|patrulha\|inimiga\|cargueiro\|destrocos>` | força um encontro |
| `/lanterna interior` | entra na nave mais próxima |
| `/lanterna contratos` | abre o quadro de contratos |

## Limitações conhecidas
- A cor das faixas e luzes do interior vem da região da sala (cada Tropa tem a sua).
- O Armário só abre e fecha (não guarda itens).
- O interior é uma dimensão separada: lá fora o mundo continua sem você.
- Os bosses não ficam mais fortes com o nível; a dificuldade vem dos níveis pedidos para forjar.


---

# 0.14 — Cidades das Tropas, piloto automático da nave e mira travada

## Cidades das Tropas
- Em cada planeta de Tropa, **150 blocos ao norte da base**, fica a cidade da Tropa (construída quando você chega perto).
- No centro, a **Bateria Central** gigante no formato da bateria da Tropa: lanterna (Verde), torre de lâminas (Amarelo), braseiro (Vermelho), farol (Azul), relicário (Laranja), drusa (Violeta), caveira (Preto) e o Cajado da Compaixão (Índigo, em Nok).
- Em volta: Salão da Tropa (norte, com o quadro de contratos, console e holomapa), alojamento (leste, beliches, armários e recarga), campo de treino (oeste, forja, bateria e alvos), pouso de naves (sul), quatro torres de vigia, ruas iluminadas e muralha com portões.
- Seis Lanternas da Tropa moram lá (dois oficiais).

## Missões por posto
- **Patrulha de setores** (a partir de Patrulheiro, nível 10): passe por 3 setores do espaço longe do planeta da Tropa e derrube 2 naves piratas (elas aparecem nos setores). 900 XP.
- **Exploração** (a partir de Oficial, nível 20): pouse num planeta que você nunca visitou. 1200 XP.
- Aparecem como contratos extras no quadro da sua Tropa.

## Piloto automático da nave
- Dentro da nave, abra o **holomapa**, escolha o destino e clique em **Levar a nave até lá**.
- A nave sai de onde estava e viaja sozinha (8 a 120 s, conforme a distância). Durante a viagem a porta fica fechada.
- Ao chegar, saia pela porta: você e a nave estão no destino.

## Trava de mira (Tab)
- **Tab** trava a mira no inimigo mais perto do centro da tela; a câmera segue o alvo suavemente e aparece uma mira com nome, distância e vida.
- Tab de novo solta; solta sozinha se o alvo morrer, ficar longe ou sumir de vista.

## Outras mudanças
- Laser do punho mais fino, com pulsos correndo, e contínuo (a repetição da tecla reiniciava o feixe).
- Cargueiro e Capitânia: todas as portas da parede de fora saem da nave; portas lado a lado viram portas duplas.


---

# 0.15 — Cidade completa, Palácio dos Guardiões e Técnicas dos Capitães

## Cidade da Tropa (substitui a da 0.14, no mesmo lugar: 150 blocos ao norte da base)
- 128 x 128 blocos, mesma planta em todas as Tropas com os materiais de cada uma; muralha, 4 portões, torres nos cantos.
- **Praça** com a Bateria Central gigante, fontes e bancos.
- **Quartel-General** (3 andares): térreo com o Oficial de Contratos e o quadro; 1º andar sala de briefing; 2º andar sala do Comandante (varanda para a praça). Em Oa, o **Arauto** leva ao Palácio.
- **Mercado**: barracas (Intendente, Cartógrafo, Relíquias) e lojas (Armeiro, Boticário).
- **Hangar** (Engenheiro-chefe e Estaleiro), **Enfermaria** (Médica), **Arquivo** (Arquivista), **Celas** (Carcereiro), **Academia e arena** (Instrutor), **6 casas** com interior, guardas e cidadãos.

## Lojas (pagas em Méritos) e reputação
- Cada cidade tem reputação própria: Desconhecido, Conhecido (50), Respeitado (150), Herói (300). Mais reputação, mais itens nas lojas (naves no Estaleiro, blaster de capitão no Armeiro, Anel Vazio nas Relíquias...).
- Lanternas de Tropas inimigas da cidade entram, mas lojas e quem dá missões não atendem.

## Missões dos NPCs ("!" no nome)
- Cadeia de 3 missões com história: Comandante (sabotagem no hangar), Engenheiro (peças e teste de voo pelos anéis no céu), Médica (Ecos, ingredientes, resgate), Arquivista (registros, planeta novo, setores), Instrutor (disciplina, duelo, simulação), Carcereiro (fuga e o chefe da fuga).
- Diárias de cada um e pedidos dos cidadãos. Até 4 missões da cidade ao mesmo tempo.

## Palácio dos Guardiões (Oa)
- Ilha flutuante a 320 blocos ao norte da cidade de Oa, ligada a ela por uma ponte de luz. Só entra quem foi chamado (nível 30).
- Portão com as 7 emoções (só o verde aceso), pátio com estátuas, Grande Salão com o Conselho nos tronos, Câmara da Bateria Primordial (recarrega o anel), biblioteca, observatório, celas, 6 alas de Capitães e o Campo de Provas.
- Conselho: Ancião Varo, Ancião Selun (biblioteca), Anciã Miraeth, Ancião Dorvek, Anciã Ilyss.
- Linha: O Chamado de Oa (nível 30) → Diante do Conselho → provações → A Guarda de Honra (nível 40 e 2 provações: equipa 3 técnicas).

## Técnicas (tela: O · teclas: Y, U, I)
| Técnica | Capitão | Requisitos | Provação |
|---|---|---|---|
| Olhar do Anel | Aurith Tal | nível 30, 2 bosses | achar 3 relíquias no palácio |
| Bastião | Ilith Vey | nível 32, 4 bosses | derrotar 12 Ecos no Campo de Provas |
| Onda de Vontade | Drevan Kael | nível 35, 5 bosses | derrotar 12 piratas no Campo de Provas |
| Salto de Dobra | Lumiae Sorn | nível 38, Gênese revelado | passar pelos 5 anéis no céu |
| Armadura de Construto | Brakhul | nível 42, 7 bosses | vencer o duelo contra Brakhul |
| Comando | Qiri Nox | nível 45, Prova da Emoção iniciada | lidere aliados contra 6 invasores |

# 0.15.1 — Alvos garantidos nas missões de derrotar

Auditoria automática de todas as missões de "derrotar N" (contratos de piratas, Ecos, invasores e capitão; cadeias
da cidade; provas do Palácio; Prova da Emoção; Patrulha; destruir alvos de treino). Três casos podiam travar:

- **Patrulha** (8 Ecos sem lugar marcado): o planeta só deixa uns 3 Ecos por perto, então faltavam alvos.
- **Prova da Emoção** (3 capitães piratas sem lugar marcado): nada fazia capitães aparecerem.
- **Destruir alvos de treino** na base: os alvos não voltavam se faltassem.

Agora (`mission/AlvosGarantidos`): para cada objetivo de derrotar que ainda falta e não tem quem cuide dele, se houver
menos alvos vivos num raio de 48 blocos do que o necessário (até 4 por vez), chegam os que faltam a 16–26 blocos do
jogador, em chão firme (aviso "Alvos se aproximando"). Repete a cada 20 s enquanto faltar. Os alvos de treino voltam
perto da base. Contratos, cidade e Palácio continuam com os próprios reforços.

# 0.15.2 — Juramento da Guarda de Honra

- Os Guardiões do Conselho ficam 4 blocos acima do chão do Grande Salão; a pé não dava para alcançá-los, então
  "Apresente-se à Anciã Miraeth" não concluía. Agora cada trono tem uma escadinha de 3 degraus na frente. Mundos que
  já têm o Palácio recebem os degraus sozinhos quando o jogador chega perto.
- Aceitar uma missão de "fale com X" direto do próprio X conta a conversa na hora (antes era preciso clicar de novo).
- A Guarda de Honra agora é oferecida só pela Miraeth (o Varo também mostrava a missão, mas ela pede a Miraeth).

# 0.16 — Equilíbrio para multiplayer

**Defesa pelo nível** (vale contra tudo): a cada nível o Lanterna ganha vida, armadura e dureza.

| Nível | Vida | Armadura | Dureza |
|---|---|---|---|
| 1 | 20 | 0 | 0 |
| 10 | 26 | 1,5 | 1 |
| 25 | 38 | 4 | 2,5 |
| 50 | 58 | 8 | 5 |

**Jogador contra jogador** (`combate/Equilibrio`):
1. Poderes do anel (construtos, técnicas, tiros de plasma, soco com o anel) passam 40% do dano; armas comuns passam inteiras.
2. Resistência contra jogadores: 0,5% por nível de quem apanha (25% no 50).
3. Diferença de nível: 1,5% por nível (entre -40% e +25%).
4. Depois da armadura, um golpe tira no máximo 30% da vida máxima: acabou o hit kill.

Medido no jogo (martelo de construto, mesmo nível): antes matava com 1 golpe a partir do nível 25; agora são 4–5 golpes.

Tudo ajustável em `config/lanterna-common.toml`, seção `[combate]` (inclui `pvp_mesma_tropa` para desligar o fogo amigo
entre jogadores da mesma Tropa). Ao subir de nível, o chat mostra a defesa nova.

# 0.17 — Guildas e conquista de planetas

**Guilda** (`/guilda`): `criar <nome>` (precisa ter anel; a cor da guilda é a da sua Tropa) · `simbolo` (editor 16x16) ·
`convidar <jogador>` · `entrar <guilda>` · `sair` · `expulsar <nome>` · `desfazer` · `info [guilda]` · `lista` ·
`abandonar` (no planeta). Admin: `/guilda admin tropas <n>`, `admin defesa <n>`, `admin sem_escudo`.

**Bandeira da Guilda** (receita: bloco de esmeralda em cima, diamante · estandarte · diamante no meio, 3 ouros embaixo).
Fincada num **planeta gerado** sem dono: o planeta é da guilda e aparece a base pronta (praça com a bandeira, Cabana
Principal, Cabana do Minerador, Quartel do General com pátio de treino, muralha com 4 portões), nas cores da Tropa.
Máximo de 3 planetas por guilda. Território: 64 blocos em volta da bandeira; só a guilda quebra/constrói ali.

**Economia** (tempo real, funciona offline):
- **Minerador**: junta 1 Essência de Poder a cada 3 min (até 64). Forja **Anel de Recruta** com 8 essências.
  O Anel de Recruta não pode ser usado por jogador.
- **General**: cada anel vira 1 tropa em 60 s — **defensor** (fica no planeta, máx. 20) ou **tropa de ataque**
  (da guilda, máx. 30, vai junto nas invasões). As tropas são Lanternas NPC na cor da Tropa da guilda.

**No espaço**: planeta conquistado tem campo de escudo na cor da guilda com o símbolo virado para você. Olhando para
ele aparece o painel: guilda, dono, tropas de defesa, suas tropas de ataque e o estado do escudo.

**Invasão**: alguém de outra guilda pousa no território com o escudo desligado → 10 s de aviso → começa (barra no topo).
Os defensores saem da praça; as tropas de ataque da guilda invasora chegam junto do invasor; os membros da guilda
invasora no planeta participam. Vitória do invasor: derrotar todos os defensores (a bandeira desce a meio-mastro e
fica quebrável) e **derrubar a bandeira** → o planeta e a base passam para a guilda invasora. Vitória da defesa: o
tempo (10 min) acaba ou não sobra invasor no território por 30 s; os defensores vivos voltam a contar. Depois de
qualquer invasão: 4 h de escudo (2 h depois de fincar a bandeira). Tropas de ataque que sobrevivem voltam.
Sem fogo amigo entre a mesma guilda. Tudo ajustável em `lanterna-common.toml`, seção `[guildas]`.

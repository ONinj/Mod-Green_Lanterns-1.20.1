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

# 0.18 — Supremos, a Morte, Tropas NPC e a linha principal mais longa

**Minerador da guilda: 4 níveis** (melhora no painel dele).

| Nível | Essências por coleta | Tempo (base 3 min) | Depósito | Custo para chegar |
|---|---|---|---|---|
| 1 | 1 | 3 min | 64 | — |
| 2 | 2 | 2 min | 128 | 32 essências, 8 diamantes, 8 Méritos |
| 3 | 3 | 81 s | 192 | 96 essências, 16 diamantes, 24 Méritos, 1 netherita |
| 4 | 4 | 54 s | 256 | 192 essências, 32 diamantes, 48 Méritos, 4 netheritas |

**Anéis Supremos** (Branco e Preto): os mais fortes do jogo.
Branco: dano x1,7, voo x1,4, +20 de vida, +6 armadura, +4 dureza, 20% a menos de dano de jogadores, cura devagar.
Preto: dano x1,6, voo x1,32, +16 de vida, +5 armadura, +3 dureza, 15% a menos de dano de jogadores, rouba 15% do dano como vida.

**O Anel Preto não se ganha mais por meios normais** (nem anel dormente, nem o Chamado, nem morrer de wither, nem a linha
de Ryut). Toda morte de um portador de anel (nível 15+) tem 0,4% de chance de corromper o anel (8% se quem matou foi um
Lanterna Negro). O corrompido renasce com a linha **A Morte**: O Sussurro (Ryut, Coletor) → Colheita (60 criaturas,
24 almas) → Os Silentes (nível 25, respeito 6) → Os Mortos Andam (15 Lanternas inimigos, 12 Obsidianas do Fim) → O Preço
do Coletor (3 Relíquias, 32 almas) → O Devorador (nível 35, 6 capitães, boss de Ryut) → Os Sete Túmulos (Odym, Okaara,
Zamaron, Nok, 150 criaturas, 48 almas) → Dominar a Morte (nível 42, forjar o Anel Preto em Ryut).
Corrompidos colhem almas ao matar. O Lanterna Negro tem o dever **Ceifar** (repetível: derrotar portadores de anel); se
passar 3 dias (tempo real) sem ceifar, fica faminto (dano cai para x0,9). Tudo em `[morte]` na config.

**Linha principal mais longa e travada por nível**: em cada planeta (que não é o da sua Tropa) agora são 7 passos —
chegar, respeito, **Rastro** (ir a outro planeta: 10 piratas + 4 minérios de lá), minério, **Provação** (nível do
planeta +4, 3 capitães e 15 Ecos), boss, forja. Níveis: Odym 6, Oa 12, Okaara 17, Ysmault 22, Zamaron 27, Korugar 32,
Nok 37, Ryut 40, Gênese 46. Antes da fusão: **O Juramento da Vida** (nível 46, 25 Lanternas inimigos, 10 capitães,
32 Méritos).

**Planetas das Tropas NPC**: ~7% dos planetas gerados já são de uma Tropa (base pronta, 10–16 defensores da cor dela,
defesa volta 1 a cada 10 min). Dá para invadir (precisa de guilda; não dá para invadir a sua própria Tropa). Quem invade
vira **inimigo daquela Tropa**: os Lanternas dela atacam você e as cidades dela não atendem. Depois de tomado, o planeta
nunca mais volta a ser da Tropa.

**Espaço**: encontros mais frequentes (a cada ~1–2 min) e mais piratas; nave pirata derrubada dá 2–4 Méritos, às vezes
Essência de Poder e diamante; a capitânia dá 8–12 Méritos, 6 Essências, diamantes e às vezes Relíquia.

# 0.19 — Construtos das Tropas, animações e polimento

**Construto próprio de cada Tropa** (aparece na roda só para o anel daquela Tropa; libera com afinidade "Tolerante"):

| Tropa | Construto | O que faz |
|---|---|---|
| Verde | Aríete de Vontade | cunha gigante que atravessa tudo em linha reta e arremessa |
| Amarelo | Onda do Pavor | explosão de medo em volta: **Pavor** — jogador não faz construtos, tela treme e escurece, coração bate; criaturas fogem |
| Vermelho | Jorro de Ira | (segurar) cone de plasma que queima |
| Azul | Luz da Esperança | cura aliados, Regeneração, tira o Pavor; fere mortos-vivos e Lanternas Negros |
| Violeta | Cristal do Amor | prende o alvo num cristal; ao quebrar, **Encanto** (criaturas param de brigar, jogadores ficam fracos) |
| Índigo | Salto Índigo | teleporta para onde olha; quem está na chegada sente náusea e fraqueza |
| Laranja | Garras da Avareza | até 4 garras sugam vida (e carga de anel de outros Lanternas) |
| Preto | Mãos dos Mortos | mãos de ossos brotam do chão: prendem, Wither, roubam vida |
| Branco | Explosão da Vida | cura e protege aliados, limpa efeitos ruins, arrasa inimigos |

O Amarelo é imune ao próprio Pavor. Aliados (mesma guilda, Lanternas aliados, bichos domados) não são atingidos.

**Animações de lançar** (terceira e primeira pessoa): estocada (punho, rajada, aríete, mão, gancho...), golpe por cima
da cabeça (martelo, mãos dos mortos), empurrão com os dois braços (parede, escudo, ondas), varrida (lâminas) e mira
contínua (laser, picareta, napalm: o braço aponta para onde você olha). O anel solta um clarão na cor da Tropa.

**Laser do punho**: sai da mão de verdade, "corre" até o alvo ao ligar, é redondo com miolo branco, duas espirais
girando, pulsos correndo e clarões no punho e no ponto de impacto (com faíscas). Para quem atira, o fim do feixe é
calculado no próprio jogo (gruda na mira sem atraso).

**Polimento**: construtos em "luz sólida" com contornos nas arestas, entram com um pulo de escala, deixam imagens
fantasmas quando se movem (punho, rajada, aríete, lâminas) e rastro de partículas; punho com dedos, polegar e antebraço;
martelo com onda de choque em anel; escudo com malha de luz; corrente do gancho com elos alternados; textura nova e
animada dos blocos de construto (parede, ponte, jaula); o Preto brilha num cinza-violeta (antes sumia).

# 0.20 — A Porta Estelar

**Como liberar**: nível 32 e afinidade **Confiável** (3) com o anel → os Guardiões chamam (linha "A Porta Estelar"):
1. **O Chamado da Porta** — falar com o Guardião de Oa.
2. **Os Caminhos do Vazio** — visitar Odym, Korugar e Zamaron (com nível 32 e afinidade Confiável).
3. **A Primeira Porta** — voltar ao Guardião de Oa. Pronto: o poder está liberado.

**Como usar**: tecla **N** abre a tela da Porta Estelar com a Terra e todos os planetas já visitados (com busca).
Escolha e aperte "Abrir a porta". Custa 300 de carga; espera de 3 minutos entre portas.

**A animação**: a câmera vai para a terceira pessoa; o Lanterna sobe no ar e estende o braço; a energia corre do anel
até a frente dele e a porta nasce (colunas subindo, arco se fechando, redemoinho de luz no meio); ele voa para dentro e
a tela clareia; do outro lado a câmera fica de frente e ele sai de outra porta, que se fecha atrás dele. A chegada
procura chão firme e seco perto da base do planeta (ou perto de onde se pousa num planeta gerado). Ninguém fere quem
está atravessando. Se o planeta ainda está carregando, a animação de chegada espera o chão aparecer.

# 0.21 — Comandos de teste (só operador / cheats ligados)

`/lanterna ajuda` mostra tudo no jogo. Todos os argumentos têm autocompletar com Tab.

| Comando | O que faz |
|---|---|
| `/lanterna anel <tropa>` | Equipa o anel de qualquer Tropa (green, yellow, red, blue, violet, indigo, orange, black, white). O antigo vai para o inventário. O Preto já vem "dominado". |
| `/lanterna cheio` | Carga cheia, poder ligado, zera recargas dos construtos e a espera da Porta Estelar. |
| `/lanterna nivel <1-50>` | Muda o nível da Tropa (ao subir dá as recompensas de posto). |
| `/lanterna afinidade_nivel <0-5>` | Afinidade do anel equipado (libera os construtos). |
| `/lanterna tecnica <id\|todas>` | Técnicas dos Capitães: olhar, bastiao, onda, salto, armadura, comando. |
| `/lanterna poder <portal\|autorecarga\|honra\|genese\|tudo>` | Porta Estelar, autorrecarga (Prova da Emoção), Guarda de Honra (3º espaço), Gênese, ou tudo (nível 50, afinidade máxima, todas as técnicas e planetas visitados). |
| `/lanterna morte <corromper\|dominar\|faminto\|alimentar\|limpar>` | Linha da Morte / Anel Preto e a fome do Lanterna Negro. |
| `/lanterna missao_iniciar <id>` · `missao_concluir <id>` · `missao_abandonar <id>` | Missões (concluir dá recompensas e abre a próxima da linha). |
| `/lanterna missao_limpar` · `missao_concluidas_limpar` | Abandona todas as ativas / apaga o histórico para refazer. |
| `/lanterna visitar <planeta\|todos>` | Marca planetas como visitados (aparecem na Porta Estelar). |
| `/lanterna tropa_hostil <tropa\|limpar>` | Inimizade com uma Tropa (como se tivesse invadido o planeta dela). |
| `/lanterna invocar <o_que> [qtd]` | pirata, capitao, mercador, eco, alvo_treino, broto, lanterna_<tropa>, oficial_<tropa>, boss_<nome>. |
| `/lanterna nave <tipo> [pintura]` | Item de nave. |
| `/lanterna kit <aneis\|materiais\|reliquias\|guilda\|naves\|tudo>` | Itens. |

Os comandos antigos continuam: `escolher`, `xp`, `afinidade`, `respeito`, `encontro`, `genese`, `contratos`, `interior`,
`reconstruir`, `resetar`, `/guilda admin ...`.

# 0.22 — Perfil, trajes, voo de combate e correções

**Perfil do Lanterna (tecla H, ou as abas da tela K)**
- *Estatísticas*: Tropa, posto, nível com XP, guilda, vida, armadura, dureza, resistência contra jogadores, carga,
  afinidade, força dos construtos, desconto de carga, velocidade de voo, recarga sem bateria, Porta Estelar, técnicas,
  missões concluídas, planetas visitados, chefes derrotados, Guarda de Honra, Tropas inimigas e a linha da Morte.
- *Visual*: o uniforme que o anel veste — Padrão da Tropa, os dois trajes "segunda pele" da sua Tropa ou nenhum — e a
  altura da máscara (de -2 a +2 pixels, em passos de 1/4), com o rosto ampliado para acertar nos olhos da skin.
  Fica salvo no jogador e todos os jogadores veem.

**Trajes "segunda pele"**: 18 trajes (2 por Tropa), também como itens vestíveis (72 peças na aba do mod):
Patrulheiro e Sentinela (Verde), Espectro e Arauto do Terror (Amarelo), Berserker e Sangue Fervente (Vermelho),
Tesoureiro e Saqueador (Laranja), Aurora e Estrela-Guia (Azul), Peregrino e Xamã (Índigo), Safira e Coração Estelar
(Violeta), Ossuário e Ceifador (Preto), Gênese e Fênix (Branco).

**Tela K** e **HUD de missões** redesenhados (objetivo atual em destaque com barra, seta relativa à sua visão, marcador
com o que fazer em cima). O marcador vai em cima do NPC (ou da criatura a derrotar) quando ele está carregado por
perto, e os pontos com altura (Palácio, salas) ficam na altura certa. "Fale com" mostra o nome do NPC (antes aparecia o
nome interno, ex.: no "O Chamado de Oa" do Palácio).

**Voo**: nos planetas, sem Ctrl, é o voo de combate — vai direto para onde você manda (23 blocos/s), para no ar ao
soltar, S recua, A/D deslizam, dois toques em A/D esquivam. Com Ctrl, o impulso chega a 48 blocos/s com curvas mais
fechadas. No espaço continua igual.

**Piloto automático**: S cancela em qualquer fase (inclusive a viagem do anel novo); desligar o voo (V) também cancela.
O anel abre caminho quebrando os blocos na frente (não quebra baús, blocos inquebráveis, nem as cidades e o Palácio).
Durante o piloto o anel protege de batidas, quedas e falta de ar; a primeira viagem do anel novo não causa mais dano
no espaço.

**Naves**: o laser, o punho, a rajada, o aríete, o martelo, as lâminas e o napalm do anel ferem naves (piratas,
cargueiros, capitânias) — energia do anel rende 2,2x contra casco. Não acerta a sua própria nave nem a que você pilota.

**Recarga sem bateria** (depois da Prova da Emoção): ~1,2% da carga a cada 2 s, o dobro parado no chão sem voar,
e a emoção da Tropa recarrega o dobro de antes.

# 0.23 — Hiperpropulsão nos planetas, moradores que voltam e polimento

**Hiperpropulsão nos planetas**: em impulso máximo (Ctrl + W, ~48 blocos/s), segure por 1,5 s: o anel rompe a
barreira e dispara a **70 blocos/s** (o Branco e o Preto um pouco mais). Curvas mais abertas; soltar W ou Ctrl,
entrar na água ou bater em algo desliga na hora. Gasta o triplo de carga, como no espaço. Pose nova: os dois
punhos à frente.

**Moradores que voltam**: os NPCs de missão (Capitães e Anciões do Palácio, Comandante, Instrutor, lojistas e guardas
das cidades, os Guardiões e o Instrutor da base de Oa, o líder e o Guardião emissário de cada planeta) só nasciam uma
vez. Agora, com um jogador por perto, a lista é conferida a cada 30 s e quem faltar volta ao lugar dele (só o que falta:
não duplica). Era isso que deixava "Fale com a Capitã Qiri Nox" sem ninguém no Palácio.

**Uniforme**: com o uniforme do anel vestido, a segunda camada da skin (cabelo, chapéu, jaqueta, mangas, calça) fica
escondida onde o uniforme cobre. Ela é maior que o uniforme e aparecia por cima como faixas pretas (ou da cor do
cabelo) em cima e embaixo da máscara.

**Polimento**: ícones novos dos 36 itens de uniforme (renderizados do próprio modelo 3D, no mesmo estilo dos trajes);
barra de carga que desliza e mostra em branco o que foi gasto; painel de missão entra deslizando ao trocar de missão,
objetivo cumprido pisca em verde; aviso de missão nova "salta"; a seta do objetivo não fica mais em cima da barra de
itens. O inglês (en_us) foi traduzido por completo (antes ~800 textos estavam em português no jogo em inglês).

# 0.24 — Primeira pessoa, laser e correções

- **NPCs repetidos (0.23)**: o vigia da base procurava o Mestre Toran e os Guardiões perto de Y 0 e não achava ninguém
  (a base fica em cima do terreno), então criava outro a cada 30 s. Agora procura do fundo ao teto do mundo e, ao
  passar perto, **apaga os repetidos** (fica 1 Instrutor e 3 Guardiões em Oa; 1 líder e 1 Guardião nos outros
  planetas; nas cidades e no Palácio, a quantidade certa de cada função).
- **Laser do punho em terceira pessoa (F5)**: saía do lado esquerdo; agora sai da mão do anel.
- **Primeira pessoa**: com o anel ligado, o braço que você vê usa a manga do uniforme (o mesmo escolhido no Perfil:
  padrão, traje A ou B). Ao ligar/desligar o anel, a manga se forma da mão até o ombro (e se desfaz ao contrário),
  com uma faixa de luz da cor da Tropa atravessando a tela e as bordas brilhando.
- **Trajes "segunda pele"**: as faces escondidas dos modelos (mapeadas num pixel do canto) estavam brilhando na
  camada de brilho e apareciam como placas por cima da cabeça e do corpo. O pixel foi apagado nas 18 _glowmask.

# 0.25 — A cidade dos nativos

Em cada planeta das Tropas (menos Gênese), **a oeste da base** (centro em X -210, Z 0), nasce a cidade do povo do
planeta (Veltari em Oa, Brakkari em Ysmault, Aelínos em Odym, Ghallos em Okaara, Skrell'Vath em Korugar, Ves'Kai
em Nok, Zaëlith em Zamaron, Silentes em Ryut). É construída quando um jogador chega perto, nas cores do planeta:

- disco de 117 blocos com 12 raios de luz no chão, anéis e avenidas para os quatro lados, mureta de vidro;
- **Torre da Luz** no centro: agulha de vidro com nervuras e núcleo de luz, ~90 blocos, anéis flutuantes e uma coroa
  larga a 60 blocos;
- **8 torres-cálice**: hastes finas com raízes de apoio e um casulo de vidro no alto (38 a 56 blocos), cada uma
  ligada à Torre da Luz por uma ponte aérea;
- **4 portais** em arco nas avenidas, **16 casas-cúpula** com cama e luz, postes e 16 agulhas de luz na borda;
- 12 nativos moram lá; se faltarem, o vigia repõe (mínimo 10).

**Correções**
- Moradores de posto fixo (Capitães, Anciões, Comandante...) que se afastam mais que alguns blocos do posto voltam
  para ele: a Capitã Qiri Nox fica na ala dela, onde a missão "Diante do Conselho" aponta.
- Fontes do Palácio e da cidade com borda de bloco inteiro (com laje a água vazava); o Palácio seca a água que
  vazou. A cidade da Tropa ganhou uma faixa de 12 blocos em volta que segura a água de lagos e rios de fora (que
  descia pela muralha) e seca o que já entrou. Mundos antigos são consertados sozinhos ao passar perto.

# 0.26 — As megacidades dos povos

Cada planeta das Tropas ganhou uma cidade gigante do seu povo, a oeste da base (centro em **-480, 0**). Todas têm o
mesmo tamanho, mas cada uma tem arquitetura e cultura próprias.

- **Tamanho:** disco de 400 blocos de diâmetro, com muralha e 8 portões.
- **Centro:** praça de 80 blocos com o monumento do povo, de 125 a 210 blocos de altura.
- **Ruas:** 8 avenidas largas e 3 anéis de ruas.
- **Construções:** 8 marcos de 40 a 100 blocos, cerca de 40 prédios médios (salões, mercados, quartéis, pavilhões) e cerca de 150 casas em 4 bairros.
- **Moradores:** 40 nativos, e o vigia repõe quem faltar (mínimo 30).

| Planeta | Povo | Cidade | Monumento | Marcos, prédios e casas |
|---|---|---|---|---|
| Oa | Veltari | Cidade de Luz | Torre da Luz (≈210) com 3 coroas e raízes de apoio | torres-cálice com casulos de vidro ligados à Torre por pontes, torres de vidro, casas-cúpula |
| Korugar | Skrell'Vath | A Colmeia do Medo | Colmeia negra (≈205) com janelas âmbar, andares do ninho e espinhos em espiral | agulhas de espinhos, torres hexagonais, casulos meio enterrados, portões em presa |
| Ysmault | Brakkari | A Fortaleza de Gorrak | Coliseu de 110 blocos (arena, 14 fileiras, trono do campeão) e 4 torres de chifres de 110 | torres de vigia com chifres, quartéis, casas-bunker, muralha de 20 com ameias, veios de magma |
| Odym | Aelínos | A Cidade das Marés | Farol de Pérola (≈195) com fitas de onda em hélice sobre uma concha gigante | sobre a água, em palafitas: jardins de coral, torres de conchas, casas-concha com passarelas |
| Okaara | Ghallos | O Mercado dos Cofres | Zigurate do Tesouro de 12 degraus, cofre de ouro e obelisco dourado (≈155), com salão do tesouro | torres-cofre de cúpula dourada, mercados cobertos com bancas, casas com terraço, barracas nas avenidas |
| Nok | Ves'Kai | O Santuário das Raízes | Árvore-Mãe (≈150), com copa de 130 blocos sobre a praça, raízes e lágrimas de luz | templos de 6 degraus, casas longas e cabanas sobre estacas com telhado roxo, jardins |
| Zamaron | Zaëlith | O Jardim de Cristal | Coração de Cristal de 100 x 100 entre duas agulhas gêmeas de 150, ligadas por um arco de luz | pares de agulhas com pontes, pavilhões octogonais, casas em pares, canteiros de flores |
| Ryut | Silentes | A Necrópole Silenciosa | Obelisco do Silêncio (170) dentro do Colosso: costelas e espinha de osso de 170 blocos, com o crânio na entrada norte | grupos de obeliscos com fogo das almas, mausoléus grandes e pequenos com bigornas, lápides |

**Como a cidade é construída**

- O mundo da área é carregado em segundo plano (lotes de 48 chunks), e a obra é feita em pedaços de até ~15 ms por tick. A cidade aparece em cerca de 1 minuto.
- No fim, a obra drena a água que escorreu dos lagos para o chão aplainado.
- Mundos que já tinham a cidade pequena da 0.25 (em -210, 0) na primeira visita: ela é desmontada, os nativos se mudam para a cidade nova e a megacidade é construída (site `megacidade`).

## Baterias Centrais (0.26)

Cada Tropa tem a sua **Bateria Central** gigante na megacidade do planeta, numa praça própria a sudeste do centro
(no lugar de um dos marcos, entre duas avenidas), com a câmara virada para o norte. Ryut não tem (Morte e Vida não
usam bateria).

| Planeta | Bateria | Tamanho (L x A x C) |
|---|---|---|
| Oa | Lanterna da Vontade (verde) | 15 x 32 x 17 |
| Korugar | Gaiola do Medo (amarela) | 15 x 28 x 17 |
| Ysmault | Coração Vulcânico da Fúria (vermelha) | 16 x 31 x 17 |
| Okaara | Relicário da Avareza (laranja) | 16 x 32 x 17 |
| Odym | Farol da Esperança (azul) | 15 x 34 x 17 |
| Nok | Totem da Compaixão (índigo) | 15 x 29 x 17 |
| Zamaron | Cristal do Amor (violeta) | 21 x 35 x 23 |

- **Corpo:** o modelo é desenhado pelo núcleo (GeckoLib, animações `misc.idle` e `misc.ativo`). Os blocos invisíveis da casca dão o corpo, então dá para bater, subir e entrar.
- **Portal:** fique 2 s no campo de energia da câmara para ir à dimensão de energia da Tropa (`lanterna:fonte_<tropa>`). Lá, a plataforma de chegada tem no centro o círculo de volta.
- **Quem atravessa:** quem está usando o anel da Tropa daquela Bateria, quem tem a tag `bateria_<tropa>` (`/tag NOME add bateria_verde`) ou operadores. Os outros são empurrados para fora.
- **Itens de construção (criativo):** `bateria_central_<tropa>`, um clique no chão e a Bateria inteira aparece. Também dá para usar `/place template lanterna:bateria_central_<tropa>`.
- **Origem:** o pacote veio da branch `entrega/baterias-centrais`. Os modelos, ossos e animações são os do pacote, sem mudar nomes. Só o modid passou a ser `lanterna`, inclusive dentro das estruturas `.nbt`.

## A Fonte, a Árvore da Luz e os Corações (0.27)

### O Chamado da Fonte (missões `fonte_<tropa>_1..3`)
- Começa sozinho quando o jogador tem afinidade **Unida** (nível 4) e nível 20 com o anel de uma Tropa que tem Bateria.
- **1 · O Chamado:** vá até a Bateria Central do planeta da Tropa.
- **2 · A Provação:** clique na Bateria. Cada Tropa testa a sua emoção na praça:

| Tropa | Provação |
|---|---|
| Verde (Vontade) | aguentar 3 ondas sem sair de 16 blocos da Bateria |
| Amarela (Medo) | caçar 10 sombras em 3 minutos |
| Vermelha (Fúria) | vencer um campeão em duelo |
| Laranja (Avareza) | entregar à Bateria o item mais valioso que você carrega |
| Azul (Esperança) | proteger 3 Aelínos de um ataque pirata por 90 s (2 têm de sobreviver) |
| Índigo (Compaixão) | curar um pirata ferido com comida, em vez de matá-lo |
| Violeta (Amor) | trazer de volta o Zaëlith perdido na cidade |

- **3 · O Juramento:** clique de novo na Bateria; o juramento da Tropa (texto original) aparece verso a verso. Recompensa: **Selo da Fonte** daquela Tropa e 10 de Essência.
- Sem o selo (ou a tag `bateria_<tropa>`, ou ser operador), o portal da Bateria não deixa passar.

### Dentro da Fonte
- **Pressão:** dá para ficar **2 horas reais** lá dentro. Avisos em 50/75/90/97%. No limite, a Fonte expulsa o jogador (anel zerado, efeitos ruins) e o portal fica fechado para ele até a pressão baixar da metade (ela cai fora da Fonte).
- **Banho de Luz:** 10 minutos lá dentro (uma vez por visita) enchem o anel e dão **+150% de carga** (anel a 250%), gasta antes da carga normal, e uma **aura** na cor da Tropa enquanto durar.
- **Essência da Fonte:** +2 por minuto lá dentro; também vem das provações, do evento das Baterias e dos chefes. Paga os nós da Árvore da Luz.
- **Salas** (construídas na primeira visita, em volta da plataforma):
  - **Arquivo** (oeste): púlpito com o livro de memórias dos Lanternas antigos daquela Tropa (5 páginas, texto original).
  - **Forja de Construtos** (leste): 4 alvos de treino para testar construtos.
  - **Salão do Conselho** (norte): bancos em roda e o quadro de contratos.
  - **Ilha do Coração** (sul, a ~140 blocos, no fim da ponte): o altar (farol) onde o chefe desperta.

### Árvore da Luz (tecla P)
- Três ramos por Tropa (**Ataque**, **Proteção**, **Domínio**), 7 degraus. Custos por degrau: 3, 5, 8, 11, 15, 20, 28 de Essência. Precisa do Selo da Fonte da Tropa e do degrau de baixo do mesmo ramo.
- Cada Tropa tem **10 construtos novos** (70 no total) e passivas (dano +15% ×2, carga máx. +20% ×2, custo −10%, recarga −15%, duração +30%, regeneração do anel +50%, couraça, voo +20%).
- Os construtos novos aparecem num anel externo da roda (Z) só depois de liberados. Bosses e NPCs usam o mesmo motor.
- Todas as texturas de construto (corpo de energia, brilho, bloco) e os ícones da roda/HUD foram refeitos.

### Os Corações das Emoções (chefes das Fontes)
- Clique no altar da Ilha do Coração com **8 nós** da Árvore da Luz daquela Tropa. Depois de derrotado, o Coração leva **20 minutos** para despertar de novo.

| Fonte | Chefe | Vida |
|---|---|---|
| Verde | Colosso de Jade | 1500 |
| Amarela | Mãe das Sombras (voa) | 1300 |
| Vermelha | Serpente Escarlate | 1420 |
| Laranja | Rei Faminto | 1600 |
| Azul | Arcanjo da Aurora (voa) | 1330 |
| Índigo | Cervo Sereno | 1380 |
| Violeta | Rainha de Cristal (voa) | 1300 |

**Força (0.27.1):** armadura 16 e resistência 8, imunes a empurrão. Os golpes são muito pesados, mas lentos e anunciados:
- **Construto:** 22 de dano (28 na fase 2) antes da armadura, a cada 8,5 s (6,5 s na fase 2). O chefe para 1,5 s e concentra a luz, e um círculo marca o chão embaixo do alvo: dá para fugir.
- **Corpo a corpo:** 34 de dano, a cada 3 s (2,25 s na fase 2).
- **Golpe supremo (fase 2):** a cada 30 s, 3 s de aviso ("concentra toda a emoção! Saia de perto!"), depois solta os três construtos da fase 2 juntos.
- **Nunca mata de um golpe:** ninguém perde mais de 60% da vida máxima para o chefe em 1 s; o que chega menos de 1,5 s depois de um golpe vale só um quarto; e quem está com mais da metade da vida fica com pelo menos 1 coração.

- Cada um luta com os construtos da sua emoção e, na metade da vida, muda de fase: invoca aliados, acelera e troca de golpes.
- **Primeira vitória:** 60 de Essência e o **Coração da Emoção**: +10% de dano e +20% de carga máxima com o anel daquela Tropa, para sempre. Vitórias seguintes: 15 de Essência.
- Modelos 3D próprios (GeckoLib, 70–215 cubos), com textura, brilho e animações `misc.idle`, `move.walk`, `attack.strike`, `attack.special`, `misc.phase2`.

### Evento de servidor: A Bateria Enfraquece
- A cada ~2–3 horas reais, piratas atacam a Bateria Central de um planeta onde há jogadores. Todos no servidor recebem o aviso com as coordenadas.
- 4 ondas (crescem com o número de jogadores; capitão pirata na última). Piratas a menos de 7 blocos da Bateria a drenam (barra no topo).
- Enquanto dura: anéis daquela Tropa recarregam pela metade.
- **Bateria resistiu:** recarga +50% por 30 min para aquela Tropa e 8–12 de Essência para quem defendeu. **Caiu:** recarga −50% por 30 min.

### Comandos de teste (operador)
`/lanterna fonte <selo|pressao|banho|essencia|prova|juramento|arvore [tudo|zerar]|evento|chefe|ir [ilha]|zerar> [valor]`

`/lanterna maximo` (com o anel equipado): nível máximo, afinidade máxima, todas as técnicas e poderes (Porta Estelar, autorrecarga, Guarda de Honra, Gênese, planetas visitados), Selos e Corações das 7 Fontes, as 7 Árvores da Luz completas, 9999 de Essência, pressão zerada e anel a 250% de carga.

# 0.28 — Combate contra os Lanternas NPC, cidades mais cheias e onde nascem os Ecos

## Combate contra os Lanternas NPC
- **IA nova** (`LanternaEntity.CombateGoal`): o Lanterna NPC fica a 10–13 blocos, um pouco acima do alvo e de frente
  para ele, e troca de manobra a cada 1–3 s: pairar mirando, deslizar de lado ou investir (e socar se chegar perto).
  Antes do laser ele **para e concentra a luz por meio segundo** (som e brilho na cor da Tropa): dá para ver, desviar e
  acertar. Quando apanha, às vezes dá uma esquiva curta. Sem linha de visão, sobe e contorna. Não fica mais rodando em
  círculos a 9 blocos/s.
- **Caixa de acerto no voo**: voando, o corpo fica deitado; a caixa fica mais larga (≈1,15 a 1,9x) para bater com o
  que se vê.
- **Compensação de atraso** (`combate/Mira`): a tela mostra os outros alguns ticks atrás de onde estão no servidor.
  O laser do punho, os projéteis (punho, rajada, aríete) e os construtos da Árvore da Luz agora contam também onde o
  alvo estava nos últimos 4 ticks, e a caixa cresce um pouco com a velocidade dele (no máximo +1,1 bloco; contra
  jogadores, no máximo +0,35).
- Medido no teste automático (jogador mirando onde o cliente mostra o NPC, laser seguro por 12 s): ~85% dos pulsos
  acertavam sem a compensação e ~100% com ela; o NPC (1024 de vida) cai em ~10 s com tudo no máximo.

## Cidades mais cheias
- **Bairro novo nas megacidades**: a cidade procura sozinha lotes vazios nos bairros (sem prédio, marco ou monumento;
  postes e enfeites pequenos saem) e constrói até **70 casas a mais**, no estilo do povo, a 16 blocos umas das outras.
  Cidades que já existiam ganham o bairro novo na primeira visita (site `megacidade_densa`).
- **Mais moradores**: 90 nativos por megacidade (antes 40); o vigia repõe até 75. A conta agora é feita só nas casas
  carregadas (antes contava a cidade inteira e criava gente a mais a cada visita; os que sobram vão embora).
- **Cidade da Tropa**: 12 cidadãos a mais andando pelas ruas e 4 guardas a mais nos cantos.

## Onde nascem os Ecos Cinzentos (`lore/world/ZonasDeNascimento`)
| Lugar | Nasce? |
|---|---|
| Megacidade do povo e Palácio dos Guardiões | nunca |
| QGs (cidade da Tropa, base e arena do planeta) | raramente (8% da chance normal) |
| Oa | só perto de árvores (floresta) |
| Odym (planeta de água) | só nas ilhas (areia, grama, terra) |
| Outros planetas (sem árvores) | em chão natural, fora das construções |

Missões, contratos e chefes que criam Ecos de propósito continuam funcionando em qualquer lugar.

# 0.29 — Naves: pouso, dano, chave que chama e sons

## Correções
- **Dano de queda ao pousar**: o jogo somava a "altura de queda" da nave enquanto ela descia (mesmo devagar) e, ao
  tocar o chão, aplicava o dano em quem estava sentado. A nave agora não sofre nem repassa dano de queda.
- **Naves das Tropas e os anéis**: os Lanternas NPC sentados dentro da nave "seguravam" o laser e os tiros (o feixe
  parava neles e a nave nunca levava dano). Agora quem está dentro fica protegido pelo casco e o tiro acerta a nave.
  Além do laser, punho, rajada, aríete, martelo, lâminas e napalm, agora também ferem naves: os 70 construtos da Árvore
  da Luz (projéteis, raios, explosões, áreas, armadilhas), a Onda do Pavor e a Explosão da Vida.
- **Sair da nave depois de renascer**: renascendo no beliche e saindo pela porta, o jogador caía no spawn da Terra sem
  a nave. Cada nave de jogador agora avisa onde está (a cada 2 s), e a porta da sala leva para o lado dela, no
  planeta/dimensão onde ela estiver (se o lugar ainda está carregando, sai ao lado, em chão firme). A volta também
  sobrevive à morte.

## Chave da Nave (o chip de acesso)
- **Não cai quando o jogador morre**: volta para o inventário ao renascer (com ou sem `keepInventory`).
- **Chamar a nave**: com a chave na mão, clique direito. A nave sai de onde estiver (qualquer planeta, a Terra ou o
  espaço), entra pelo céu atrás de você **em reentrada** (fogo, brasas e fumaça, som de rugido), faz uma curva,
  freia e pousa a ~12 blocos à sua direita, já com a porta aberta. No espaço ela chega na mesma altura.
- Espera de 45 s entre chamadas. Não chama se outro jogador estiver pilotando, se você estiver dentro de uma nave,
  ou se ela já estiver perto (menos de 40 blocos).
- Naves de mundos antigos passam a ser encontradas depois que forem carregadas uma vez (a nave avisa onde está).

## Sons próprios das naves (feitos para o mod)
| Som | Quando |
|---|---|
| Motor | sempre que a nave voa por perto (mais alto e mais agudo com a velocidade e no turbo; naves grandes mais graves) |
| Turbo | ao ligar o turbo |
| Decolar / Pousar | ao sair do chão e ao pousar |
| Reentrada | quando a chave chama a nave |
| Porta | abrir e fechar a porta |
| Tiro | disparos das naves |
| Chamado | ao usar a chave |


## 0.30 — O Juramento, o Comunicador e a Câmera do Lanterna

### Juramento falado (primeira missão)
Quando um anel escolhe alguém (Verde, Amarelo, Vermelho, Laranja, Azul, Índigo, Violeta), a primeira missão agora é
**O Juramento**: ir até a Bateria Central da Tropa (praça das Baterias, em Oa) e clicar nela com o anel.
Abre a tela do juramento, com 4 versos originais de cada Tropa. O jogador **recita em voz alta**:
cada palavra só acende enquanto o microfone capta voz por tempo suficiente para dizê-la (palavras longas pedem mais).
O jogo não reconhece a fala, só detecta que há voz. Texto em português ou inglês, conforme o idioma do jogo.
- Com Simple Voice Chat: usa o microfone do SVC (no modo push-to-talk, segure a tecla de falar).
- Sem SVC: usa o microfone do sistema direto. Sem microfone nenhum: segurar ESPAÇO enquanto fala.
- Concluído: coluna de luz na Bateria, aviso para o servidor, +400 de afinidade e a linha segue para o líder da Tropa.
- Anéis Preto e Branco não têm Bateria Central: a linha deles começa como antes.
O juramento do Chamado da Fonte (etapa 3) também passou a ser falado.

### Comunicador do anel (F4)
- F4 abre o comunicador: lista de Lanternas online, Ligar / Adicionar (até 4 na ligação), Desligar.
- Chamada chegando: aviso no meio da tela e toque; F4 atende, Shift+F4 recusa (30 s para atender).
- Painel da ligação no canto: rostos, quem está falando (anel verde e barrinhas), duração.
- **Holograma**: cada pessoa na ligação aparece como uma figura de luz na cor do anel (linhas de varredura, disco de
  projeção, nome). Em primeira pessoa flutua à frente-esquerda da visão; em terceira pessoa é projetada pelo anel ao lado
  do personagem. Gesticula quando a pessoa fala.
- Voz: com o mod **Simple Voice Chat** instalado (servidor e jogadores), a ligação vira um grupo de voz privado, valendo
  a qualquer distância e em qualquer dimensão. Sem SVC, a ligação funciona (painel e holograma), mas sem áudio.

### Câmera do Lanterna (terceira pessoa melhorada)
- Câmera por cima do ombro, suavizada, que se afasta ao voar rápido e se encolhe ao bater em parede.
- Mira própria em terceira pessoa, mostrando onde o olhar do personagem realmente acerta (fica vermelha sobre alvos).
- F6 liga/desliga, F7 troca o ombro, Alt + roda do mouse ajusta a distância. Lembra a escolha (config/lanterna-camera.txt).
- Pilotando naves continua a câmera própria das naves.

### 0.30.1 — Inclinação da nave
- Quem está sentado na nave (piloto, passageiros, Lanternas NPC) inclina junto com ela nas curvas e ao subir/descer:
  o assento se move com o casco e o corpo gira junto.
- Em primeira pessoa, a visão do piloto inclina de lado nas curvas (um pouco menos que o casco, para não enjoar).


## 0.31 — Favores dos Ves'Kai e o Cajado da Compaixão

### Lágrimas Índigo mais fáceis
- A Gratidão Ves'Kai virou **Moeda de Gratidão**. Ganha de duas formas:
  - curando um Ves'Kai ferido (como antes);
  - fazendo **favores**: cada Ves'Kai (morador comum, não ferido) pede um favor por dia. Fale com ele e aceite.
    Até 3 favores ao mesmo tempo. Ao concluir, paga 2 ou 3 moedas.
- Favores possíveis: frutos brilhantes (12), bagas doces (16), velas (6), lã branca (8), fragmentos de ametista (8),
  garrafas de mel (3), pães (8), derrotar Ecos Cinzentos (6), derrotar zumbis (8).
- Trocas com os Ves'Kai (Shift + clique direito):
  1 moeda → 1 Lágrima Índigo · 4 moedas → 5 Lágrimas · 6 ametistas → 1 Lágrima ·
  10 frutos brilhantes + 1 moeda → 2 Lágrimas · 1 maçã dourada → 2 moedas (e as trocas antigas).

### Cajado da Compaixão
- No slot de bateria: fica **preso nas costas**, na diagonal (antes ficava reto na mão esquerda).
- Segurando o cajado: fica **em pé na frente do corpo, segurado com as duas mãos**, balançando de leve ao andar.
- Recarga Índigo (R): sem portal. O cajado se forma na mão do lado oposto ao anel, a mão do anel sobe e **encosta o
  anel no cristal do topo**, a luz gira em volta e entra no anel, e o cajado volta para as costas.
  Em primeira pessoa o cajado sobe pela esquerda e a mão vai até o cristal.

### 0.31.1 — Juramentos das Tropas
Cada Tropa tem um juramento de 4 versos, escrito para o mod (texto original, inspirado na intenção dos juramentos
dos quadrinhos, sem copiar as frases): Verde (vontade), Amarelo (medo), Vermelho (fúria), Laranja (ganância),
Azul (esperança), Índigo (compaixão), Violeta (amor), Preto (morte) e Branco (vida).
- Aparece no chat, na cor da Tropa, toda vez que o anel é recarregado (bateria, R, cajado).
- É o mesmo texto que se recita em voz alta na Bateria Central (primeira missão do anel).


## 0.32 — A Terra: Vila Aurora e a Base Vigília

### O começo da história (antes do anel)
Uma vila pequena, a Vila Aurora, é construída uma vez por mundo a uns 300 blocos do ponto de nascimento, em terreno
firme. Tem praça com fonte, seis casas, moradores, e um observatório branco com cúpula na ponta leste. Embaixo do
observatório (alçapão no centro da torre) fica a Base Vigília, laboratório escondido da Dra. Lívia Andrade, que rastreia
o que cai do céu. No terraço ao lado do observatório há um altar de ensaio do Ritual da Bateria.

Personagens (humanos, modelo de jogador com skins próprias): Dra. Lívia Andrade (cientista), Téo Brandão (assistente),
Sgt. Rui Tavares (segurança) e cinco moradores. Se morrerem, voltam ao posto quando alguém passa pela vila.

Linha "terra" (jogador novo, sem anel, recebe a primeira sozinha; os moradores também oferecem):
1. **Luzes sobre Aurora**: falar com o Sgt. Rui na porta do observatório.
2. **A Base Vigília**: descer pelo alçapão e falar com a Dra. Lívia (ganha picareta de ferro e escudo).
3. **Estrelas Cadentes**: de noite, estrelas caem perto do jogador (uma por minuto, mais ou menos) abrindo crateras
   com uma Rocha Estelar (picareta de ferro; dá 1 a 2 Fragmentos Estelares) e trazendo Contaminados (zumbis
   brilhando, mais fortes). Juntar 3 fragmentos (ela devolve 4 e um bloco de ametista).
4. **O Sinal**: a Dra. Lívia dá um mapa da cratera do anel caído; pegar o Anel Apagado.
5. **O Ritual da Bateria**: fazer a Bateria Cinzelada e carregá-la no ritual.
6. **O Anel Escolhe**: encostar o Anel Apagado na bateria carregada. O anel escolhe a Tropa e começa a linha dela
   (juramento na Bateria Central).
Fora da missão, estrelas cadentes ainda caem de vez em quando à noite (fragmentos nunca acabam).

### Bateria Cinzelada (mais difícil)
- Receita: 2 Fragmentos Estelares, 1 bloco de ametista, 2 obsidianas, 1 lanterna, 2 pedras cinzeladas e 1 lingote de ouro.
- Ela nasce vazia. **Ritual da Bateria**, em 3 etapas:
  1. Altar: de noite, a céu aberto, a bateria sobre um bloco de ametista, com 4 velas acesas nos cantos (2 blocos na
     diagonal, mesma altura). Clique nela com um Fragmento Estelar.
  2. Carga (1 minuto): coluna de luz do céu e 3 ondas de Contaminados. Ficar a até 24 blocos.
  3. Vontade: agachar do lado dela (até 3 blocos) por 5 segundos.
  Falha (amanhecer, morrer, se afastar, quebrar) apaga as velas e perde o fragmento. Pronta, ela brilha dourada.
- Só a bateria carregada acorda o Anel Apagado.

### Animações
- Escudo de força: desenhado colado na posição suave do dono (não dá mais "pulinhos" ao andar).
- Criaturas, povos e chefes: a animação de andar acompanha a velocidade real (pés não patinam), ciclos de andar com
  balanço do corpo, giro do tronco e cabeça estabilizada; respiração no parado; golpes e especiais com curvas
  suaves (Catmull-Rom) no lugar de movimentos lineares. Lanternas NPC com ações suavizadas.


## 0.33 — Oficina de Construtos

**Alt+Z** abre a Oficina: uma tela para desenhar o próprio construto em 3D, cubinho por cubinho (grade 16x16x16,
até 900 cubinhos), na cor do anel. Guarda em 8 espaços e põe num preset (Alt+1..4), igual aos construtos normais.

Tela:
- Vista 3D no meio: botão esquerdo usa a ferramenta (arrastar continua), botão direito arrastando gira, roda dá zoom.
  Chão em grade e uma seta mostrando a FRENTE do desenho.
- Ferramentas: Lápis (B), Borracha (E), Pintar (P), Caixa (C, dois cliques nos cantos). Espelho (M) desenha dos dois
  lados ao mesmo tempo. 4 tons (1-4): cor do anel, clara, escura e quase branca. Camada do ar (PgUp/PgDn) para pôr
  cubinhos soltos no alto. Desfazer/Refazer (Ctrl+Z/Ctrl+Y), Salvar (Ctrl+S).
- Modelos para começar: Espada, Machado, Escudo, Flecha, Martelo, Carro, Muralha.
- Tamanho de 0,5x a 2x. Testar (Espaço) mostra a animação ali mesmo.

Comportamentos (cada um com as animações prontas: montar a partir da base com clarão, desmontar de fora para dentro):
- **Arma** (20 s): fica na mão; clique esquerdo golpeia em arco (prepara, varre com rastro de luz, volta). Alcance e
  dano crescem com o tamanho. O braço acompanha.
- **Escudo** (14 s): flutua na frente seguindo o olhar; segura flechas e corta 70% do dano que vem da frente.
- **Disparo**: voa para a mira girando no próprio eixo e estoura no impacto.
- **Queda do céu**: aparece no alto em cima da mira, com um anel de aviso no chão, cai de ponta-cabeça e achata no
  impacto (dano grande em área).
- **Veículo** (60 s): você monta; W/S anda, olhe para virar; flutua, inclina nas curvas e atropela.
- **Estrutura** (15 s): sobe do chão onde você mira; segura flechas e empurra inimigos.
Usar de novo a arma, o escudo ou o veículo desfaz. Custo de carga = base do comportamento + 1 a cada 120 cubinhos.
O desenho é sincronizado: todo mundo por perto vê o mesmo construto.

## 0.34 — Oficina: forma lisa e animações novas
- A grade agora é 32×32×32 (cubinhos com metade do tamanho, até 4000). Desenhos antigos são ampliados sozinhos.
- **Liso** (padrão, tecla L): a peça vira uma superfície contínua por cima dos cubinhos, sem degraus. Desligado,
  volta ao estilo de blocos com contorno.
- **Pincel** 1, 2 ou 3 cubinhos de lado (teclas [ e ]).
- Os construtos ganharam luz: sombra pela direção da face, borda acesa contra o olhar, reflexo e halo; a cor do
  anel fica viva (antes saíam esbranquiçados).
- Animações: fio de luz da mão até a peça e faixa de luz subindo enquanto monta; cópias de luz no golpe da arma e
  atrás do disparo; rastro de velocidade, quique e onda de choque na queda do céu; propulsores no veículo;
  ao desmontar uma faixa apaga a peça e ela estilhaça em faíscas.
- Modelos prontos refeitos com formas arredondadas (espada, machado, escudo, flecha, martelo, carro, muralha).

## 0.35 — Energia da Emoção, Mineradora Planetária e integrações
A Essência de Poder agora vem de uma operação de mineração nos planetas conquistados (o NPC Minerador virou o
**Forjador da Guilda**: ele forja os Anéis de Recruta e as melhorias dele aceleram as mineradoras do planeta).

**Cadeia própria (sem nenhum outro mod)**
1. **Gerador Emocional**: produz Emoção Bruta (fluido) com a emoção do lugar. Começa na emoção da Tropa do anel de
   quem colocou; agachar + mão vazia troca.
   - Vontade (verde): jogadores quebrando/colocando blocos por perto + um pouco sempre.
   - Medo (amarelo): monstros vivos por perto (até 8).
   - Raiva (vermelho): criaturas morrendo por perto (mais vida, mais emoção).
   - Avareza (laranja): consome ouro, esmeralda, diamante e bloco de ouro.
   - Esperança (azul): pouco, sem parar.
   - Compaixão (índigo): queima Lágrimas Índigo.
   - Amor (violeta): jogadores juntos por perto e animais nascendo.
2. **Refinaria Emocional** (encostada no gerador): 1000 mB + 1 redstone = 1 Cristal Emocional (8 s).
3. **Conversor de Luz**: Cristal = 60 mil FE, Núcleo = 300 mil FE; empurra FE para tudo encostado.
4. **Mineradora Planetária**: só num planeta da sua guilda. Gasta 100 FE/tick; a cada leva (3 min de trabalho no
   nível 1, mais rápido nos níveis do Forjador) dá Essência de Poder, e 40% das vezes um minério do tipo do planeta.
   Monta uma torre de perfuração e um braço escavadeira animados. Com o planeta descarregado, recupera o trabalho
   quando ele carrega de novo (até 24 h, limitado pela energia guardada — 4 milhões de FE).
   - Ataques: com um jogador por perto, o barulho atrai criaturas (em média a cada 25 min). Encostadas na máquina
     elas tiram 1% a cada 2 s; com 0% ela para. Conserto: barra de ferro (+10%).
   - Se o planeta for tomado por outra guilda, a mineradora passa a trabalhar para a nova dona.

**Integrações (todas opcionais)**
- **Mekanism** (10x energia): Câmara de Reação Pressurizada (1000 mB + redstone + hidrogênio) → Cristal Bruto;
  Purificação (oxigênio) → Cristal Puro; Enriquecimento → 2 Núcleos (300 mil FE cada). Rocha Estelar no
  enriquecimento → 3 fragmentos. Cabos do Mekanism (e de qualquer mod de FE) ligam direto na mineradora; o
  Entangloporter leva a energia da base até o planeta.
- **Create**: misturador aquecido (1000 mB + redstone → 3 cristais) e moedor (Rocha Estelar → 2-3 fragmentos).
- **JEI**: receitas da refinaria e do conversor e páginas de explicação de cada máquina.
- **Curios**: o anel pode ir no espaço "ring" e a bateria no "belt" (o espaço próprio do mod continua valendo).
- Configuração em `energia`: multiplicador do gerador, FE/tick da mineradora, horas offline, ataques e intervalo.

## 0.36 — Cerimônias de escolha do anel
A entrega do anel virou uma cena de cinema desenhada no mundo (todo mundo por perto vê), com o modelo 3D do anel de
cada Tropa, câmera que dá a volta e termina no close da mão erguida, névoa e cor do céu do ritual e tremor:
- **Verde** (dormir a noite toda): o anel risca o céu como um cometa com rastro, dá duas voltas em você, um círculo de
  luz se desenha no chão e uma coluna verde sobe.
- **Vermelho** (matar muitos): o chão racha, um poço de plasma ferve, seis colunas de fogo pulsam com o coração e o
  anel sobe do poço.
- **Amarelo** (encarar o Warden): o mundo apaga, olhos amarelos abrem e piscam no escuro, raios caem cada vez mais
  perto e um raio traz o anel.
- **Azul** (curar o aldeão zumbi): aurora no céu, ondas calmas no chão e o anel desce devagar como uma estrela.
- **Violeta** (filhotes com o bicho domado): cristais crescem em volta, pétalas caem em espiral e um botão de cristal
  se abre com o anel dentro.
- **Índigo** (salvar o Golem): um feixe desce do céu, ondas como na água, lágrimas sobem e o anel desce no feixe.
- **Laranja** (o tesouro sobre o ouro): um redemoinho de moedas gira cada vez mais rápido e se funde no anel.
- **Negro** (morrer sob o Wither): tentáculos de sombra saem da terra, almas giram e se juntam no anel.
- **Branco** (fusão): os sete anéis orbitam e se fundem num anel branco; flores de luz se abrem no chão.
Todas terminam com o anel parando na frente do peito e encaixando no dedo da mão erguida: clarão, onda no chão e a luz
subindo pelo corpo. A cerimônia dura pelo menos 10 s (configurável para mais).

## 0.37 — Jade Viva, Chamado do Mogo e aura de energia
**Mogo** (planeta vivo, Lanterna Verde; o planeta, a história e a Segunda Chance vêm na próxima etapa).
- **Jade Viva**: clique com ela em cima da bateria verde no inventário (até 3).
  I: +25% de carga · II: +50% e a bateria recarrega o anel aos poucos · III (Selo do Mogo): +75% e o Chamado.
- **Chamado do Mogo** (bateria com o Selo na mão, botão direito mirando em alguém até 96 blocos):
  uma jaula de barras de luz prende o alvo (nem pérola do Ender tira), o Mogo surge no céu com a faixa de jade acesa,
  carrega e dispara um raio gigante. Mata na hora (jogadores incluídos) e abre um poço no terreno (não passa da
  bedrock). Gasta metade da carga do anel; recarga de 1 hora (tempo real). Quem chama vê com câmera de cinema; todos
  por perto veem o planeta, o raio e sentem o tremor.
- O Mogo **se recusa** contra os chefes (os guardiões corrompidos): "Eu não ergo a minha luz contra quem um dia a
  carregou ao meu lado." — sem gastar carga nem tempo.
- Comandos (operador): `/lanterna mogo resetar` (zera o tempo do Chamado), `/lanterna mogo jade` (3 Jades),
  `/lanterna mogo selo` (põe o Selo na bateria verde da mão).
- **Aura de energia** (0.37.1): com o anel ligado, energia líquida na cor da Tropa envolve cada parte do corpo em
  formas arredondadas que ondulam sem parar; transparente no meio, acesa nas beiradas; no alto da cabeça e dos ombros
  sobe em línguas como chama, e uma faixa de luz corre pelo corpo. Junto com o contorno.

## 0.38 — O planeta Mogo
O Mogo fica no espaço (perto de -4600, 350, -3800). **Só os inimigos dos Verdes não pousam** (Amarela, Vermelha,
Laranja, Negra): a nave ou o jogador é empurrado de volta para o espaço com um aviso do planeta. As outras Tropas e quem
não tem anel podem visitar. No pouso há uma clareira com o Coração de Jade; a faixa de jade atravessa o planeta.

**Linha do Mogo** (só Lanternas Verdes; o convite chega no nível 18). O Mogo fala pouco, em frases curtas:
1. **A Chegada pelo Cinturão** — perto do Mogo, uma tempestade de rochas vem na direção da sua nave. Desvie e pouse.
2. **A Corrida da Ferida** — no pouso, 8 focos amarelos aparecem ao longo da faixa de jade (para o leste). Você tem
   2min10s para quebrar todos; a terra corrompida se espalha em volta deles. Falhou? recomeça. Recompensa: Jade Viva.
3. **O Chão que se Move** — a ponte do cânion (ao sul) desaba de trás para frente e pedaços à frente afundam e voltam.
   O Mogo segura o voo: só a pé. Caiu, volta ao começo.
4. **A Prova do Mogo** — na arena ao norte o planeta luta com você: pedras arremessadas, linhas de raízes que rompem o
   chão e guardiões de terra. Quebre os 4 olhos de cristal. Ninguém morre ali: o Mogo levanta você na borda.
   Recompensa: Jade Viva.
5. **O Cerco** — a oeste, três torres da faixa. Três levas da Tropa Amarela (com naves a partir da segunda) atacam as
   torres. Perdeu as três, recomeça.
6. **O Mergulho no Núcleo** — a fenda (a sudeste) desce até o núcleo; o Mogo segura sua queda. Lá embaixo está a
   **Ferida**. Na metade da vida ela se fecha num casulo que nada atravessa: com qualquer bateria verde na mão,
   botão direito — o primeiro Chamado, o raio desce do teto da câmara. Recompensa: Jade Viva (a terceira completa o Selo)
   e o **Vínculo**.

**Segunda Chance** (Vínculo + anel verde): ao morrer, uma vez por dia (tempo real), o Mogo aparece no céu, segura você
num feixe calmo e te puxa até o pouso dele. Não vale contra o próprio raio do Mogo.
Comandos (operador): `/lanterna mogo ir`, `/lanterna mogo missao <1-6>`, `/lanterna mogo segunda` (recarrega).

## 0.38.1 — Naves, Ecos em Odym e o Anel da Avareza
- **Sair da nave**: o jogador sai do lado da porta (ou do outro lado, atrás, na frente), sempre fora das caixas da
  nave e em chão firme se houver; com a nave no ar, sai ao lado dela na mesma altura. Vale também para a porta da sala.
- **Colocar a nave**: ela aparece à frente de quem colocou, virada para onde ele olha, sem nenhuma parte pegando no
  jogador ou no terreno (anda para a frente e, se preciso, sobe um pouco).
- **Hitbox das naves**: com a nave girada, cada peça vira uma grade de caixas menores que seguem o formato (antes
  eram caixas enormes ou cortadas na diagonal).
- **Ecos Cinzentos em Odym**: só nas ilhas (areia do planeta, a céu aberto, acima do mar, longe da beira, fora das
  cidades), nunca na água. Como Odym é quase todo mar, de tempos em tempos um Eco sobe do mar numa ilha perto de
  quem está lá (no máximo 3 por perto).
- **Anel da Avareza**: `/lanterna laranja` mostra o modo; `/lanterna laranja unico` (padrão: um só Lanterna Laranja
  por mundo), `/lanterna laranja varios` (qualquer um pode fazer o ritual), `/lanterna laranja liberar` (tira o dono).

## 0.38.2 — Aura contínua
A aura do anel ligado agora é um único halo de luz macio em volta da silhueta inteira do personagem, na cor da Tropa:
forte e claro colado na borda, sumindo para fora, com uma leve ondulação viva. Saiu a aura por partes do corpo.
(O efeito Brilho do Minecraft, de flechas espectrais, também passa a aparecer com esse halo macio.)

## 0.38.3 — O uniforme escorre
Ao ligar o anel, o uniforme não sobe mais em faixa dos pés à cabeça: ele **escorre pelo corpo como líquido**, a partir
da mão do anel. Sobe pelo braço, chega no ombro, se espalha pelo peito e daí sobe o pescoço até a cabeça, desce pelo
outro braço e pelas pernas. A frente é irregular (avança em línguas) e a beirada brilha na cor da Tropa; faíscas
pequenas acompanham a frente. A camada rente à skin vai pixel por pixel e as peças 3D seguem a mesma frente. Leva
cerca de 1,8 s. Ao desligar, o líquido recua de volta para a mão. Saiu a nuvem de pó verde ao ligar.

## 0.38.4 — Máscaras novas e trajes de energia
- As máscaras dos trajes "segunda pele" foram refeitas (meia-máscara esculpida, olhos amendoados acesos; formas
  clássica, angular para Medo/Fúria e de baile para Amor). A meia-caveira do Ossuário ficou igual.
- **Trajes de energia** (`traje_<tropa>_energia`): um por Tropa, base escura colada com os músculos em placas na cor da
  Tropa e fibras de energia acesas; emblema grande no peito e máscara dominó. Viram a terceira opção de traje na
  Personalização (Padrão, A, B, **Energia**, Sem uniforme) e também existem como itens vestíveis (36 peças novas).

## 0.38.5 — Aura que ilumina
Na Personalização (aba Visual) há duas opções novas, salvas no jogador e vistas por todos:
- **Aura: ligada/desligada** — o halo em volta do corpo quando o anel está ligado.
- **Iluminar** (só com a aura ligada) — o jogador carrega uma luz de tocha (nível 14) que anda com ele, e o chão e as
  paredes que essa luz alcança ganham a cor da Tropa: vermelho para a Fúria, verde para a Vontade, azul, violeta,
  amarelo, laranja, índigo, branco e preto. Paredes no meio cortam a luz, como uma tocha. De dia a cor aparece pouco; no
  escuro, toma conta. A luz é de verdade (afasta monstros) e some ao desligar, sair, morrer ou trocar de mundo.

## 0.38.6 — Baterias-lanterna, anéis HD e máscaras
- **Baterias Centrais** (7 Tropas) e **baterias de mão** no formato de lanterna (entrega `baterias-lanternas`); a da Morte
  agora é uma lanterna negra (`bateria_morte_preto`).
- **Anéis em alta definição** (entrega `aneis-hd`): mesmos símbolos, aro redondo, sinete detalhado; texturas novas em
  `textures/item/ring/`. O `power_ring.json` e as texturas `espectro_*` continuam.
- **Máscaras dos trajes por cima da segunda camada da skin** (entrega `mascara-segunda-camada`): o cabelo/chapéu da skin
  continua aparecendo; só Ossuário (preto A), Sentinela (verde B) e Saqueador (laranja B) escondem a camada da cabeça.

## 0.39.0 — O uniforme vira a skin
O uniforme do anel (padrão da Tropa, trajes A e B e de Energia) não é mais uma armadura 3D por cima do corpo: ele é
**pintado pixel por pixel na própria skin do jogador**, no mesmo modelo, sem espessura nenhuma.
- Cada uniforme/traje 3D foi "achatado" para o layout de skin em alta resolução (4x) pelo script
  `tools/achatar_trajes.py` (texturas em `textures/skin_traje/`, versões para braço largo e fino). Se um traje 3D
  mudar, rode o script de novo.
- Só a camada de baixo da skin é trocada. A **segunda camada da cabeça (cabelo, chapéu, barba...) fica sempre por
  cima**, então a máscara do mod fica por baixo dela, como se fosse da própria skin. A jaqueta, as mangas e a calça da
  segunda camada somem enquanto o uniforme está vestido.
- As partes que brilham (lentes, emblema, fibras de energia) continuam acesas no escuro.
- A transformação continua escorrendo como líquido a partir da mão do anel, agora pixel por pixel na skin, com a
  beirada acesa na cor da Tropa (shader `traje_pele`). Em primeira pessoa o braço segue igual.
- As peças de traje vestidas como armadura de verdade (itens) continuam 3D.

## 0.39.1 — Relevo e peças 3D de volta
- **Altura da máscara** (Personalização) volta a funcionar com o traje na skin: a pintura da cabeça desliza para cima
  ou para baixo em passos de 1/4 de pixel (até 2 px).
- **Relevo na segunda camada**: o que no traje 3D era saliente (placas, ombreiras, frisos, a máscara) vai também para a
  segunda camada da skin, um pouco acima do corpo, como a jaqueta de uma skin. Na cabeça, essa camada fica entre a
  cabeça e o cabelo/chapéu do jogador, então o cabelo continua por cima.
- **Peças 3D**: o que sai do corpo mais do que uma skin consegue mostrar (asas da Fênix, auréola da Gênese, espinhos,
  chifres, cristais, moedas, tiras de pano...) continua 3D por cima da skin, e entra na transformação junto com o
  líquido.

## 0.39.2 — Waypoints e o Mogo que anda
- **Waypoints**: o marcador de missão ia para o fundo do mundo (embaixo da bedrock) porque o cliente lia um mapa de
  altura que o servidor não manda. Agora usa o chão de verdade (e guarda a altura já vista); vale para a Terra e para
  todos os planetas das Tropas. O "suba para o espaço" aponta para o alto à sua frente (a seta apontava para baixo).
- **Textura do Mogo**: planeta verde com a faixa de jade no equador e o quadrado com o símbolo do anel verde (o mesmo
  desenho do anel do jogo), dos dois lados.
- **O Mogo anda**: dá uma volta lenta pelo espaço (~30 min por volta). Toda vez que alguém entra no espaço vindo da Terra
  ou de outro planeta (e não há mais ninguém lá), ele aparece num lugar diferente. Quem sai do próprio Mogo o encontra
  onde estava.
- **Rastro do Mogo** (construto do anel Verde, nível 2): no espaço, marca o Mogo na tela por 30 s com a distância e
  solta um rastro de luz na direção dele; fora do espaço, diz em que ponto do espaço ele está.
- **Defesa do Mogo**: a Tropa da **Morte** ele sempre ataca quando chega perto (feixe de luz enorme, dano igual ao laser
  de um Lanterna oficial, nunca mata). As outras Tropas inimigas não pousam, mas ele só revida se atirarem nele
  (fica bravo por 20 s). Os Verdes ele não ataca.

## 0.39.3 — Mogo vivo, Índigo e a Fome do Anel Preto
- **Textura do Mogo**: planeta vivo — continentes de floresta e campo, montanhas, mares, rios, nuvens e polos de gelo —
  com a faixa de jade e o quadrado com o símbolo do anel verde.
- **Recarga do Índigo em primeira pessoa**: o braço não é mais jogado para trás; ele sobe até o cristal do cajado na base
  do braço normal, e o anel aparece na ponta da mão.
- **A Fome do Anel Preto**: o Lanterna Negro que passa os dias da configuração (padrão 3) sem ceifar um portador de anel
  não enfraquece mais — ele fica **faminto**. O anel toma o controle: o jogador não mexe mais o boneco (teclado e mouse
  travados, tela escura com a borda vermelha) e é levado para um planeta de Tropa ao acaso (Oa, Korugar, Ysmault, Odym,
  Zamaron, Nok ou Okaara — nunca a Gênese nem Ryut). Ele voa sozinho até qualquer ser vivo (criaturas, NPCs, aliados ou
  não; jogadores não) e mata até se saciar: **25 a 30 mortes**. Sem ninguém por perto por 15 s, o anel o leva para outro
  planeta. Saciado, o controle volta e a fome some por mais uns dias. Comando de teste: `/lanterna frenesi [parar]`.

## 0.39.4 — Textos que cabem na tela

- **Bateria Cinzelada:** clicar nela explica no chat, com título e passos numerados (vazia: o Ritual da Bateria; em andamento; cheia). Não repete em menos de 4 s.
- **Mensagens no meio da tela:** a barra de ação, o título e o subtítulo não quebram linha no Minecraft. Quando não cabem na largura da tela, vão inteiros para o chat (sem repetir em 6 s); título e subtítulo aparecem cortados com "…".
- **HUDs e menus em interface grande** (testado em 427x240 e 480x270): HUD do anel (posto, afinidade, status em várias linhas), painel de missão (objetivo atual e dica quebram em linhas), aviso de missão nova, roda de construtos (a ficha vai para o lado ou a roda encolhe), diário (altura se ajusta, abas visíveis), perfil, oficina (modo compacto), árvore da luz (ficha do nó dentro da tela), mapa estelar, comunicador, ligação, guilda, quadro de contratos, portal e diálogo.
- As técnicas (Y, U, I) não ficam mais por cima da barra de construtos.

## 0.39.5 — Inglês completo

- Com o jogo em inglês, nada mais aparece em português (fora os nomes próprios e os nomes dos comandos, como /lanterna missao_concluir).
- Trajes: Specter, Herald of Terror, Guiding Star, Genesis, Phoenix, Pilgrim, Shaman, Treasurer, Plunderer, Ossuary, Reaper, Patroller, Sentinel, Boiling Blood, Sapphire, Star Heart.
- Guildas das Tropas (e o dono "Conselho da Tropa") são traduzidas na hora de mostrar; guildas de jogadores mantêm o nome que o jogador deu.
- Livro do Arquivo da Fonte com nome traduzido; moradores da Terra com título traduzido (Dra./Dr., Dona/Ms., Seu/Mr.), inclusive em mundos antigos.
- Respostas e ajuda dos comandos de teste (/lanterna ajuda, cheio, maximo, nivel, missao_*, poder, morte, fonte...) traduzidas.
- Descrição do mod e comentários do lanterna-common.toml em inglês e português.

## 0.39.6 — Máscara, energia, Nok e Odym

- **Máscara:** novo cartão na Personalização ("Máscara: usando/retirada"). Sem máscara, a cabeça fica com a skin do jogador (rosto, cabelo, chapéu) e as peças 3D da cabeça somem.
- **Anel sem energia (0%):** a aura em volta do corpo apaga, a luz do "Iluminar" apaga e as partes do traje que brilham (lentes, emblema, fibras) ficam apagadas, para todos os jogadores verem. O Anel Branco nunca descarrega.
- **Minérios:** Fragmento de Pavor em Korugar (24 veios de 8, de y -60 a 72; antes 10 de 6 só abaixo de 0). Lágrima Índigo em Nok (20 veios de 7 e 60 gotas no teto das cavernas; antes 8 de 5 e 30).
- **Ves'Kai feridos:** textura própria (curativos, cortes, cores apagadas, brilho fraco), ficam ajoelhados apoiados no cajado com as duas mãos e não andam até serem curados. Antes, por um defeito, nenhum Ves'Kai nascia ferido. Agora 35% dos moradores nascem feridos, a base de Nok mantém até 4 feridos em volta (até 12 Ves'Kai) e a cidade de Nok tem 60% mais moradores.
- **Odym:** Ecos Cinzentos sobem nas ilhas a cada 14 s (antes 25 s), até 5 por perto (antes 3), mais vezes em dupla, e o spawn normal ficou mais frequente.

## 0.40.0 — Construtos que se formam, Broca e trocas

- **Trocas:** nenhum povo tranca a troca por falta de respeito (nem Tropa inimiga). Cada ponto que falta (e cada ponto negativo) deixa tudo 20% mais caro, até +200%, e o povo avisa. Cada troca feita assim devolve 1 de respeito. Só não negociam: Guardiões em serviço, Zaëlith de luto e quem viu você matar um dos seus nos últimos 5 minutos. Shift + clique abre a troca mesmo com o povo bravo com sua Tropa.
- **Ves'Kai:** 3 Lágrimas Índigo → 1 Maçã Dourada; 4 Maçãs Douradas → 9 Moedas de Gratidão (além de 1 → 2). Ves'Kai de mundos antigos recebem as trocas novas.
- **Formação dos construtos:** parede, ponte e jaula aparecem bloco a bloco: primeiro as arestas de luz crescem, depois o miolo enche e dá um clarão. Um fio de luz fino sai do anel, corre até o construto e encaixa nele (anel de encaixe na ponta) enquanto ele se forma. Martelo, mão, punho, rajada, aríete e cristal: a energia espirala até o lugar e o fio do anel encosta neles ao nascer.
- **Picareta:** agora fica na frente da mão, apontada para a mira, batendo; ligada ao anel pelo fio.
- **Broca (novo construto, todas as Tropas, nível 2, segure a tecla):** um míssil de luz com aletas na cauda e três coroas dentadas na ponta girando em sentidos alternados. Vai até a parede (até 4,5 blocos) e abre de uma vez um túnel 3x3 com 2 de fundo (5x5 no nível 4); não pega obsidiana e blocos mais duros. Custa 2 de carga por tick e mais pelos blocos quebrados.

## 0.41.0 — Sóis, buracos negros, Mogo distante e correções

- **Anel Branco (BUG 01):** quem está juntando os sete anéis (missão Gênese ativa ou já feita) pode falar com qualquer Guardião ou líder de planeta: ele lista no chat o que falta (anel e/ou relíquia de cada planeta, e onde conseguir). O Guardião de Oa oferece a próxima linha de qualquer planeta que falta; os outros, a do próprio planeta. Não depende mais de nível, de outra linha ativa nem de falar com o Guardião certo (Ysmault, Nok).
- **Prova da Bateria (BUG 02):** inimigos e ondas nascem sempre num chão livre a pelo menos 12 blocos da Bateria Central, perto da altura do jogador, fora de líquidos e nunca em cima dos blocos da bateria.
- **Anel Verde dormindo:** 6% por noite (antes bem mais) e só a partir da 4ª noite dormida (config `chance_verde_por_noite` e `noites_minimas_verde`).
- **Anel Negro no single-player:** a chance de corromper ao morrer sobe 0,4% a cada morte sem corromper (até +10%; volta a zero quando corrompe). Morrer para um Lanterna Negro conta como o Ceifador. Em Ryut, a cada 45 s, Lanternas Negros (até 2 por perto) caçam quem tem anel e nível suficiente.
- **Armaduras de outros mods:** o nível não soma mais pontos de armadura (que o Minecraft limita a 30 e brigava com armaduras de outros mods). Agora o nível dá **defesa**: corta até 35% do dano depois da armadura, junto com qualquer armadura. O perfil e a mensagem de subir de nível mostram a defesa.
- **Mogo distante:** agora fica a 25–45 mil blocos do centro (uns 20 a 30 s de hiperpropulsão) e muda de lugar sozinho a cada 20 minutos quando ninguém está nele ou perto dele. Mundos antigos: na primeira vez que o servidor abre, ele já vai para longe. Nunca fica perto de planetas, sóis ou buracos negros.
- **Sóis:** além do Sol grande (Hélio), estrelas amarelas, laranja, brancas, azuis (pequenas e furiosas) e gigantes vermelhas aparecem nos setores vazios do espaço, longe do centro. A gravidade delas é muito mais forte que a dos planetas e puxa até 3 raios de distância (mesmo acelerando). A até 1,6 raio queimam (fogo e dano que atravessa a resistência do anel); entrar nelas é morte.
- **Buracos negros:** esfera preta com disco de acreção girando e anel de luz. Puxam tudo em até 40 vezes o raio do horizonte, cada vez mais forte; perto do horizonte as forças de maré machucam e quem passa dele morre (nem a Segunda Chance do Mogo salva). O Abismo fica entre Nok e Odym; os outros estão espalhados.
- **Avisos:** o painel do espaço mostra o sol ou buraco negro mais perto (seta, nome e distância; pisca em vermelho dentro do alcance) e a barra de ação avisa quando a gravidade pega. A hiperpropulsão freia sozinha ao chegar perto, e é ela que tira você de lá. Naves, criaturas e itens soltos também caem.
- **Texturas dos planetas:** Oa, Korugar, Ysmault, Odym, Zamaron, Nok, Okaara, Ryut, Gênese e o Sol redesenhados em 1024x512 (relevo, continentes, mares, calotas, nuvens e detalhes de cada mundo). Planetas gerados com o dobro da resolução e relevo.

## 0.42.0 — Visão de longe, Lanternas lendários e Hanks

- **Visão de longe no espaço:** o servidor manda 5 vezes por segundo onde estão os outros jogadores do espaço (até 9 mil blocos), sem carregar chunk nenhum. Até 64 blocos aparece o jogador de verdade; até ~450, o boneco com a skin dele inclinado no voo e a aura da Tropa; mais longe, uma luz na cor da Tropa (sempre grande o bastante para ser vista) com o rastro do voo. Quem pilota nave aparece como uma luz maior. Nome e distância aparecem embaixo de cada um. Entre os pacotes a posição é prevista pela velocidade, então dá para acompanhar até alguém em hiperpropulsão.
- **Planetas lendários (fixos, a 50 mil blocos do centro, cada um num setor):** Vigília Esmeralda (Hal Jordan, Verde), Cofre de Larfleeze (Larfleeze, Laranja), Fornalha Rubra (Atrocitus, Vermelho), Remanso Azul (Santo Andarilho, Azul), Silêncio Índigo (Índigo-1, Índigo), Coração Violeta (Carol Ferris, Violeta) e Necrópole (Mão Negra, Negro). Aparecem no mapa estelar; o chão de cada um fica numa região própria do mundo do tipo do planeta. Na primeira visita a arena é montada no pouso (disco com pilares de luz na cor da Tropa e trono).
- **Os lendários:** trajes próprios (capas, golas, ombreiras), barra de chefe, vida alta (850 a 1024), golpes fortes, uma onda de energia a cada 8–12 s, e não saem da arena. Sozinhos por 30 s, voltam ao trono e se curam.
  - **Tropa inimiga:** luta de verdade. Aos 66% e 33% de vida chamam dois Lanternas da Tropa. Ao morrer, todos que lutaram (até 128 blocos) ganham 1.500 de experiência, a Insígnia do lendário, 3 Fragmentos Lendários e 3 diamantes; o servidor anuncia. Ele volta em 30 minutos.
  - **Mesma Tropa ou aliada:** clique nele para um duelo de treino. Ninguém morre: o jogador para em meio coração e o lendário em 12% da vida. Vencer dá 500 de experiência, 1 Fragmento Lendário e a Insígnia (garantida na primeira vitória, 25% depois); perder dá um pouco de experiência e regeneração. Um duelo a cada 20 minutos por lendário; no máximo 3 minutos de duelo.
  - **Sem anel:** o lendário não briga.
- **Hanks:** o planeta-máquina que a Tropa Amarela construiu. Anda pelo espaço como o Mogo (volta de 40 min, a 40–55 mil blocos do centro, muda de setor a cada 25 min se ninguém estiver nele ou perto). Mundo próprio: placas de metal escuro com veios de luz amarela, torres, respiros, três núcleos de energia e a cidadela do Sinestro ao norte do pouso. Não tem ar (só com o anel). Quem é de Tropa inimiga dos Amarelos é atacado por sentinelas Amarelas a cada 40 s.
- **Missões Amarelas (nível 25+, oferecidas sozinhas):** O Planeta-Máquina (achar e pousar em Hanks), Os Três Núcleos (ficar 5 s junto de cada núcleo: leste, oeste e sul) e A Prova de Sinestro (vencer o duelo na cidadela). Recompensas: Méritos, Fragmentos Lendários e Fragmentos de Pavor.
- **Itens novos:** Insígnia de cada lendário e Fragmento Lendário (moeda da próxima saga).
- **Comandos de teste:** `/lanterna lendario <id> ir|reviver`, `/lanterna hanks ir`, `/lanterna hanks missao <1-3>`.

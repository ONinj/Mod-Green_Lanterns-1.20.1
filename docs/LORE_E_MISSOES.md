# Lore, missões, construtos e afinidade (0.11.0)

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

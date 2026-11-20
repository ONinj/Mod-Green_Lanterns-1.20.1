import json
C=[]
def cap(id, icone, pt, en, rpt, ren, paginas, cor=None, spoiler=False):
    c={"id":id,"icone":icone,"titulo":{"pt":pt,"en":en},"resumo":{"pt":rpt,"en":ren},"paginas":paginas}
    if cor: c["cor"]=cor
    if spoiler: c["spoiler"]=True
    C.append(c)
def pag(tpt, ten, xpt, xen, teclas=None, itens=None, receitas=None, dpt=None, den=None):
    p={"titulo":{"pt":tpt,"en":ten},"texto":{"pt":xpt,"en":xen}}
    if teclas: p["teclas"]=teclas
    if itens: p["itens"]=itens
    if receitas: p["receitas"]=receitas
    if dpt: p["dica"]={"pt":dpt,"en":den}
    return p
K="key.lanterna."

cap("inicio","anel:green","Bem-vindo, Portador","Welcome, Bearer",
 "O que é o mod e como usar este guia.","What the mod is and how to use this guide.",[
 pag("Uma luz para cada emoção","A light for every emotion",
  "Este mod coloca no seu mundo **nove Tropas**, cada uma movida por uma emoção: **vontade, medo, fúria, avareza, esperança, compaixão, amor, morte e vida**.\n\nCada Tropa tem um **planeta** no espaço, com cidade, povo, quartel, minério, chefe e uma **Bateria Central**. O anel te leva até lá voando.\n\nVocê começa na Terra, sem anel. O caminho é: **ganhar um anel**, fazer o **Juramento**, subir de **nível**, forjar os anéis dos outros planetas e, um dia, fundir todos no **Anel Branco**.",
  "This mod brings **nine Corps** to your world, each driven by an emotion: **will, fear, rage, avarice, hope, compassion, love, death and life**.\n\nEach Corps has a **planet** in space, with a city, a people, a barracks, an ore, a boss and a **Central Battery**. The ring flies you there.\n\nYou start on Earth without a ring. The path: **earn a ring**, take the **Oath**, level up, forge the rings of the other planets and, one day, fuse them all into the **White Ring**.",
  itens=["anel:green","anel:yellow","anel:red","anel:orange","anel:blue","anel:indigo","anel:violet","anel:black","anel:white"]),
 pag("Como usar este guia","How to use this guide",
  "Use as **setas** (ou A/D) para virar a página e **W/S** para trocar de capítulo. A **rodinha do mouse** rola o texto quando ele é longo.\n\nPasse o mouse em cima de itens e ingredientes para ver o nome. As receitas são as **de verdade** do jogo: se um ingrediente aceita várias opções, ele vai trocando.\n\nCapítulo lido ganha um **✔**. Abra o guia quando quiser com {tecla:key.lanterna.guia} ou com o comando **/guide**.",
  "Use the **arrows** (or A/D) to turn pages and **W/S** to switch chapters. The **mouse wheel** scrolls long text.\n\nHover items and ingredients to see their names. Recipes are the game's **real** ones: if an ingredient accepts several options, it cycles.\n\nRead chapters get a **✔**. Open the guide anytime with {tecla:key.lanterna.guia} or the **/guide** command.",
  teclas=[K+"guia",K+"journal"],
  dpt="Também dá para escrever no chat começando com **anel,** e perguntar: \"anel, como faço a bateria?\"",
  den="You can also type in chat starting with **ring,** and ask: \"ring, how do I make the battery?\""),
 pag("O primeiro anel","Your first ring",
  "Há três jeitos de ser escolhido:\n\n**1. A história da Terra:** vá até a **Cidade Aurora** (300 a 500 blocos do spawn), fale com o **Sgt. Rui** e siga a **Dra. Lívia** até o Anel Apagado e o Ritual da Bateria.\n\n**2. O ritual de cada Tropa:** cada anel tem um teste da sua emoção. Ex.: o Verde chega em sonho depois de várias noites dormidas; o Vermelho, quando a fúria transborda matando muitos monstros.\n\n**3. O Chamado:** a cada 3 conquistas grandes (diamante, Nether, Dragão...), um anel aleatório te escolhe.",
  "There are three ways to be chosen:\n\n**1. Earth's story:** go to **Aurora City** (300 to 500 blocks from spawn), talk to **Sgt. Rui** and follow **Dr. Lívia** to the Dormant Ring and the Battery Ritual.\n\n**2. Each Corps' ritual:** every ring tests its emotion. E.g.: Green arrives in a dream after many nights of sleep; Red, when rage overflows from killing many monsters.\n\n**3. The Calling:** every 3 big achievements (diamond, Nether, Dragon...), a random ring picks you.",
  itens=["lanterna:anel_apagado","minecraft:red_bed","minecraft:diamond","minecraft:ender_eye"],
  dpt="Pergunte ao anel \"como consigo o anel amarelo\" (ou outra cor) para ver o ritual de cada Tropa.",
  den="Ask the ring \"how do I get the yellow ring\" (or another color) to see each Corps' ritual."),
])

cap("anel","bateria:green","O Anel e a Bateria","The Ring and the Battery",
 "Ligar, carga, recarga, Juramento e o que acontece ao morrer.","Power, charge, recharge, the Oath and what happens on death.",[
 pag("Ligar e cuidar da carga","Power and charge",
  "Com o anel no dedo, {tecla:key.lanterna.toggle_power} liga e desliga o poder. Ligado, ele dá **armadura igual à netherite**, mantém a **fome cheia**, deixa **respirar no espaço e debaixo d'água** e libera voo e construtos.\n\nTudo gasta **carga** (a barra no canto da tela). Sem carga, o anel apaga.\n\nA aba **Lanterna** ({tecla:key.lanterna.open_lantern}) mostra o anel, a bateria e as suas estatísticas.",
  "With the ring on, {tecla:key.lanterna.toggle_power} turns the power on and off. Powered, it gives **netherite-level armor**, keeps **hunger full**, lets you **breathe in space and underwater** and unlocks flight and constructs.\n\nEverything spends **charge** (the bar in the corner). Out of charge, the ring goes dark.\n\nThe **Lantern** tab ({tecla:key.lanterna.open_lantern}) shows the ring, the battery and your stats.",
  teclas=[K+"toggle_power",K+"open_lantern",K+"pocket_recharge"]),
 pag("Recarregar","Recharging",
  "Coloque a **bateria da sua Tropa** no espaço da aba Lanterna e aperte {tecla:key.lanterna.pocket_recharge}.\n\nTambém recarrega: clicar com o anel numa **bateria no chão**, a **Estação de Recarga** dentro da nave ou a **Bateria Central** do seu planeta.\n\nA bateria **só recarrega anéis da mesma Tropa**. As receitas de cada uma estão no capítulo **Receitas**.",
  "Put your **Corps' battery** in the Lantern tab slot and press {tecla:key.lanterna.pocket_recharge}.\n\nAlso recharges: clicking a **placed battery** with the ring, the **Recharge Station** inside your ship, or your planet's **Central Battery**.\n\nA battery **only recharges rings of the same Corps**. Each recipe is in the **Recipes** chapter.",
  receitas=["lanterna:bateria_green","lanterna:bateria_cinzelada"]),
 pag("O Juramento","The Oath",
  "A primeira missão de todo anel: vá até a **Bateria Central** da sua Tropa e clique nela com o anel.\n\nRecite os versos (cada palavra acende quando é reconhecida), aperte **TAB** para digitar, ou segure **Espaço** se não tiver microfone.\n\nO Juramento abre a linha de missões do seu planeta.",
  "Every ring's first mission: go to your Corps' **Central Battery** and click it with the ring.\n\nRecite the verses (each word lights up when recognized), press **TAB** to type, or hold **Space** if you have no microphone.\n\nThe Oath opens your planet's mission line.",
  itens=["lanterna:bateria_central_verde","lanterna:bateria_central_amarelo","lanterna:bateria_central_vermelho"]),
 pag("Se você cair","If you fall",
  "Ao morrer, a bateria fica no lugar e o chat mostra as coordenadas.\n\nCom **50% de carga ou mais**, o anel volta sozinho para a sua mão gastando 25%. Com menos, ele fica no chão junto com a bateria: volte para buscar.\n\nO **Anel Branco** sempre volta.",
  "When you die, the battery stays where it was and chat shows the coordinates.\n\nWith **50% charge or more**, the ring returns to your hand spending 25%. With less, it stays on the ground with the battery: go back for it.\n\nThe **White Ring** always returns.",
  dpt="Antes de uma luta grande, encha a carga. Anel cheio volta para você mesmo se a luta der errado.",
  den="Before a big fight, top up your charge. A full ring comes back to you even if the fight goes wrong."),
])

cap("voo","minecraft:feather","Voo, Espaço e Planetas","Flight, Space and Planets",
 "Voar, sair da Terra, o mapa estelar e os perigos do espaço.","Flying, leaving Earth, the star map and space hazards.",[
 pag("Voar","Flying",
  "Com o anel ligado, {tecla:key.lanterna.toggle_flight} liga o voo; **Shift** pousa. **Ctrl** acelera.\n\nNo espaço e nos planetas existe a **hiperpropulsão**, muito rápida, que freia sozinha perto de perigos. No espaço ela vai de **1.800 blocos/s** (nível 1) a **3.000** (nível 75); o anel Preto chega a **4.000** e o Branco a **4.500**.\n\nDebaixo d'água o anel também voa (e você respira), mas a água freia um pouco.",
  "With the ring powered, {tecla:key.lanterna.toggle_flight} toggles flight; **Shift** lands. **Ctrl** boosts.\n\nIn space and on planets there's **hyperpropulsion**, very fast, which brakes by itself near hazards. In space it goes from **1,800 blocks/s** (level 1) to **3,000** (level 75); the Black ring reaches **4,000** and the White **4,500**.\n\nUnderwater the ring also flies (and you breathe), but water slows you a little.",
  teclas=[K+"toggle_flight",K+"camera",K+"ombro",K+"travar_mira"]),
 pag("Para o espaço","Into space",
  "Voe bem alto para sair da Terra. No espaço **não há ar**: só o anel deixa você respirar, gastando carga.\n\n{tecla:key.lanterna.star_map} abre o **mapa estelar**: escolha um planeta e o anel te leva. Ou peça no chat: \"anel, me leve para Zamaron\".\n\nCada planeta das Tropas tem uma **cidade** com o marco do povo no centro, o **quartel** com a Bateria Central e, na borda, a **arena** do chefe.",
  "Fly very high to leave Earth. In space there's **no air**: only the ring lets you breathe, spending charge.\n\n{tecla:key.lanterna.star_map} opens the **star map**: pick a planet and the ring takes you. Or ask in chat: \"ring, take me to Zamaron\".\n\nEach Corps planet has a **city** with its people's landmark in the center, the **barracks** with the Central Battery and, at the edge, the boss **arena**.",
  teclas=[K+"star_map",K+"portal"]),
 pag("Perigos","Hazards",
  "**Sóis** puxam forte e queimam de perto; entrar num é morte.\n\n**Buracos negros** puxam cada vez mais forte; passar do horizonte mata.\n\n**Piratas** atacam em naves e a pé. Os **cargueiros mercadores** são pacíficos: clique na nave para entrar e negociar lá dentro.\n\nO painel do espaço sempre mostra o perigo mais perto.",
  "**Suns** pull hard and burn up close; entering one is death.\n\n**Black holes** pull harder and harder; crossing the horizon kills.\n\n**Pirates** attack in ships and on foot. **Merchant freighters** are peaceful: click the ship to board and trade inside.\n\nThe space panel always shows the nearest hazard.",
  itens=["lanterna:blaster_pirata","lanterna:nave_pirata","lanterna:nave_cargueiro"],
  dpt="A Porta Estelar ({tecla:key.lanterna.portal}) libera no nível 32 com afinidade Confiável e leva você a planetas já visitados.",
  den="The Star Gate ({tecla:key.lanterna.portal}) unlocks at level 32 with Trusted affinity and takes you to planets you've visited."),
])

cap("construtos","lanterna:essencia_poder","Construtos","Constructs",
 "A roda, os atalhos, a Oficina e a Árvore da Luz.","The wheel, shortcuts, the Workshop and the Tree of Light.",[
 pag("A roda de construtos","The construct wheel",
  "Segure {tecla:key.lanterna.construct_wheel} para abrir a roda, escolha um construto e use com {tecla:key.lanterna.construct_use}.\n\nNa roda, passe o mouse num construto e aperte **1 a 4** para gravar nos atalhos **Alt+1 a Alt+4**.\n\nO anel interno tem os construtos de todos; o externo, os **da sua Tropa**.\n\n**Defesa**: com a mão vazia, **segure o botão direito** para levantar o escudo do anel. Cada golpe gasta carga em vez de vida; soltou, ele some. Os arremessáveis são **teleguiados**, e jaulas e correntes pegam quem estiver perto da mira (a corrente puxa e derruba quem voa).",
  "Hold {tecla:key.lanterna.construct_wheel} to open the wheel, pick a construct and use it with {tecla:key.lanterna.construct_use}.\n\nIn the wheel, hover a construct and press **1 to 4** to save it to **Alt+1 to Alt+4**.\n\nThe inner ring has everyone's constructs; the outer ring, **your Corps'**.\n\n**Guard**: with an empty hand, **hold right click** to raise the ring's shield. Every hit drains charge instead of health; let go and it fades. Thrown constructs are **homing**, and cages and chains grab whoever is near your aim (chains pull targets in and knock flyers out of the air).",
  teclas=[K+"construct_wheel",K+"construct_use",K+"preset_1",K+"oficina"]),
 pag("Afinidade","Affinity",
  "Afinidade é o quanto o anel **aceita você**: Desconfiado, Tolerante, Aceito, Confiável, Vinculado e Uno com o anel.\n\nEla cresce com o tempo de anel ligado, usando construtos e fazendo missões. Mais afinidade libera **construtos melhores**, mais cura e soco mais forte.",
  "Affinity is how much the ring **accepts you**: Distrustful, Tolerant, Accepted, Trusted, Bonded and One with the ring.\n\nIt grows with time powered on, using constructs and doing missions. More affinity unlocks **better constructs**, more healing and stronger punches.",
  dpt="Com afinidade Vinculado e nível 20, começa o Chamado da Fonte, que abre a Árvore da Luz.",
  den="With Bonded affinity and level 20, the Calling of the Source begins, which opens the Tree of Light."),
 pag("Oficina e Árvore da Luz","Workshop and Tree of Light",
  "**Oficina** ({tecla:key.lanterna.oficina}): desenhe o seu próprio construto, cubinho por cubinho, e escolha o comportamento: arma, escudo, disparo, queda do céu, veículo ou estrutura.\n\n**Árvore da Luz** ({tecla:key.lanterna.arvore}): três ramos por Tropa (Ataque, Proteção e Domínio). Os nós custam **Essência da Fonte** e liberam **10 construtos novos** por Tropa e bônus passivos.",
  "**Workshop** ({tecla:key.lanterna.oficina}): design your own construct, cube by cube, and choose its behavior: weapon, shield, shot, sky drop, vehicle or structure.\n\n**Tree of Light** ({tecla:key.lanterna.arvore}): three branches per Corps (Attack, Protection and Dominion). Nodes cost **Source Essence** and unlock **10 new constructs** per Corps plus passive bonuses.",
  teclas=[K+"oficina",K+"arvore"]),
])

cap("tropas","anel:violet","As Nove Tropas","The Nine Corps",
 "Emoções, planetas, alianças e inimizades.","Emotions, planets, alliances and rivalries.",[
 pag("Emoções e planetas","Emotions and planets",
  "**Verde** · vontade · Oa\n**Amarela** · medo · Korugar\n**Vermelha** · fúria · Ysmault\n**Laranja** · avareza · Okaara\n**Azul** · esperança · Odym\n**Índigo** · compaixão · Nok\n**Violeta** · amor · Zamaron\n**Negra** · morte · Ryut\n**Branca** · vida · Gênese (escondido)",
  "**Green** · will · Oa\n**Yellow** · fear · Korugar\n**Red** · rage · Ysmault\n**Orange** · avarice · Okaara\n**Blue** · hope · Odym\n**Indigo** · compassion · Nok\n**Violet** · love · Zamaron\n**Black** · death · Ryut\n**White** · life · Genesis (hidden)",
  itens=["lanterna:jade_estelar","lanterna:fragmento_de_pavor","lanterna:ira_bruta","lanterna:ambar_da_posse","lanterna:perola_do_amanha","lanterna:lagrima_indigo","lanterna:cristal_devoto","lanterna:obsidiana_do_fim"]),
 pag("Amigos e inimigos","Friends and foes",
  "Os povos tomam o lado da Tropa do seu planeta. A **Verde** é inimiga da Amarela, Vermelha, Laranja e Negra; a **Laranja** e a **Negra** são inimigas de quase todos.\n\nLanternas de Tropas inimigas **atacam você**, e ninguém de um povo inimigo troca com você até ganhar o **respeito** dele.\n\nPara trocar com um morador: **Shift + clique direito**. Respeito sobe com missões, favores, trocas e elogios.",
  "Peoples side with their planet's Corps. **Green** is enemy of Yellow, Red, Orange and Black; **Orange** and **Black** are enemies of almost everyone.\n\nEnemy Corps Lanterns **attack you**, and no one from an enemy people trades with you until you earn their **respect**.\n\nTo trade with a resident: **Shift + right click**. Respect grows with missions, favors, trades and compliments."),
])

cap("missoes","minecraft:writable_book","Missões, Nível e Contratos","Missions, Levels and Contracts",
 "Diário, XP, postos, naves de presente e contratos.","Journal, XP, ranks, gift ships and contracts.",[
 pag("O diário","The journal",
  "{tecla:key.lanterna.journal} abre o diário com as suas missões. A linha principal começa no planeta da sua Tropa; depois, fale com o **Guardião** ou o líder de qualquer planeta.\n\nA missão acompanhada aparece no canto da tela com a **distância e a direção** do alvo.",
  "{tecla:key.lanterna.journal} opens the journal with your missions. The main line starts on your Corps' planet; after that, talk to the **Guardian** or the leader of any planet.\n\nThe tracked mission shows in the corner with the target's **distance and direction**.",
  teclas=[K+"journal",K+"perfil",K+"tecnicas",K+"comunicador"]),
 pag("Nível e postos","Levels and ranks",
  "XP vem de piratas, capitães, Lanternas inimigos, chefes, Ecos, naves destruídas, missões, contratos e da **primeira visita** a cada planeta. O nível vai até **250** (do 150 ao 250 é a saga do Necron, e cada nível custa bem mais).\n\n**Nível 10:** Patrulheiro + nave Lanceira\n**Nível 20:** Oficial + nave Patrulha\n**Nível 30:** Veterano · **40:** Guarda de Honra\n\nA cada 5 níveis você ganha mais um coração, e a defesa sobe até cortar 60% do dano.",
  "XP comes from pirates, captains, enemy Lanterns, bosses, Echoes, destroyed ships, missions, contracts and your **first visit** to each planet. Level caps at **250** (150 to 250 is the Necron saga, and each level costs much more).\n\n**Level 10:** Patroller + Lancer ship\n**Level 20:** Officer + Patrol ship\n**Level 30:** Veteran · **40:** Honor Guard\n\nEvery 5 levels you gain a heart, and defense grows up to 60% damage reduction.",
  itens=["lanterna:insignia_recruta","lanterna:insignia_patrulheiro","lanterna:insignia_oficial","lanterna:insignia_veterano","lanterna:insignia_guarda_honra"]),
 pag("Contratos","Contracts",
  "Pegue contratos no **Quadro de Contratos** do quartel ou no console da sua nave. Saem **4 novos por dia**, até **3 ativos**: caçar piratas, derrubar capitães, escoltar, entregar...\n\nContratos pagam XP, Méritos e itens.",
  "Take contracts at the barracks' **Contract Board** or your ship's console. **4 new ones a day**, up to **3 active**: hunt pirates, take down captains, escort, deliver...\n\nContracts pay XP, Merits and items.",
  receitas=["lanterna:quadro_contratos"]),
])

cap("chefes","lanterna:coracao_de_vorgath","Chefes e a Forja","Bosses and the Forge",
 "Arenas, os poderes dos chefes e como forjar anéis.","Arenas, boss powers and forging rings.",[
 pag("O Altar do Desafio","The Challenge Altar",
  "Cada planeta tem um **chefe** na arena (com muro em volta), chamado no **Altar do Desafio**. Vencer dá **2 drops**: um para **forjar o anel** daquele planeta e outro para o **Altar da Vida**.\n\nDepois, o mesmo altar chama o **Chefe da Tropa**: agachado (Shift + usar), com 8 minérios do planeta e nível do planeta + 5.",
  "Each planet has a **boss** in its (walled) arena, summoned at the **Challenge Altar**. Winning gives **2 drops**: one to **forge that planet's ring** and one for the **Altar of Life**.\n\nThen the same altar summons the **Corps Boss**: crouching (Shift + use), with 8 planet ores and planet level + 5.",
  itens=["lanterna:altar_desafio","lanterna:nucleo_de_vontade","lanterna:lagrima_de_aurora","lanterna:coroa_de_pavor","lanterna:coracao_de_vorgath","lanterna:nucleo_do_fim"]),
 pag("Os poderes dos chefes","Boss powers",
  "Os chefes não ficam parados apanhando. A cada poucos segundos, com um **brilho e um som de aviso**, usam um de três poderes:\n\n**Silêncio** · o anel fica abafado: sem construtos por alguns segundos.\n**Prisão** · correntes de luz te prendem no lugar, sem voo.\n**Queda** · o voo apaga e você cai; volta sozinho depois.\n\nQuando vir o aviso, **afaste-se** ou **pouse** antes: cair de alto é o que mais mata.",
  "Bosses don't just stand there. Every few seconds, with a **glow and a warning sound**, they use one of three powers:\n\n**Silence** · your ring is smothered: no constructs for a few seconds.\n**Prison** · chains of light hold you in place, no flight.\n**Fall** · your flight goes out and you drop; it comes back after.\n\nWhen you see the warning, **back off** or **land** first: falling from high up is the real killer.",
  dpt="Morrow, em Odym, só sente dano com os 5 faróis acesos: reacenda cada um com uma Pérola do Amanhã.",
  den="Morrow, on Odym, only takes damage with all 5 beacons lit: relight each one with a Pearl of Tomorrow."),
 pag("A Forja do Espectro","The Spectrum Forge",
  "Na **Forja do Espectro** de cada planeta: use o **Anel Vazio**, o drop do chefe e **4 minérios** do planeta, e cumpra o ritual da emoção perto da forja por 10 segundos.\n\nNível mínimo: Odym 5 · Oa 8 · Okaara 12 · Ysmault 16 · Zamaron 20 · Korugar 25 · Nok 30 · Ryut 32.\n\nO Anel Vazio sai na bancada:",
  "At each planet's **Spectrum Forge**: use the **Empty Ring**, the boss drop and **4 planet ores**, and complete the emotion ritual near the forge for 10 seconds.\n\nMinimum level: Odym 5 · Oa 8 · Okaara 12 · Ysmault 16 · Zamaron 20 · Korugar 25 · Nok 30 · Ryut 32.\n\nThe Empty Ring is crafted:",
  itens=["lanterna:forja_espectral","lanterna:merito"],
  receitas=["lanterna:anel_vazio"]),
])

cap("lendarios","lanterna:fragmento_lendario","Os Lendários","The Legendaries",
 "Oito Lanternas lendários, nível 60, só a partir do 50.","Eight legendary Lanterns, level 60, only from level 50.",[
 pag("Nos confins","At the edge",
  "Os **8 Lanternas lendários** vivem em planetas fixos a **50 mil blocos** do centro (veja no mapa estelar).\n\nEles lutam sempre no **nível 60**, usam muitos construtos (martelos, rajadas de punhos, chuva de disparos, prisões) e ficam mais fortes com mais jogadores.\n\n**De Tropa inimiga:** luta de verdade; dá **Insígnia** e **Fragmentos Lendários**.\n**Da sua Tropa ou aliada:** clique para um **duelo de treino**.",
  "The **8 legendary Lanterns** live on fixed planets **50 thousand blocks** from the center (see the star map).\n\nThey always fight at **level 60**, use many constructs (hammers, fist volleys, bolt rain, prisons) and grow stronger with more players.\n\n**Enemy Corps:** a real fight; drops an **Insignia** and **Legendary Fragments**.\n**Your Corps or ally:** click for a **training duel**.",
  itens=["lanterna:fragmento_lendario","lanterna:insignia_hal_jordan","lanterna:insignia_sinestro","lanterna:insignia_atrocitus","lanterna:insignia_mao_negra"]),
 pag("Ainda não é a hora","Not yet",
  "Abaixo do **nível 50**, os lendários não lutam com você:\n\n· Se ele for de **Tropa inimiga**, derruba você num golpe e te **arremessa para fora do planeta**, com pouca vida. Não é morte de verdade, mas vai doer.\n· Se for **aliado**, ele só diz que ainda não é hora de treinar.\n\nSuba de nível, forje anéis e volte preparado.",
  "Below **level 50**, legendaries won't fight you:\n\n· If he's from an **enemy Corps**, he knocks you down in one blow and **hurls you off the planet** with low health. Not a real death, but it hurts.\n· If he's an **ally**, he just says it's not time to train yet.\n\nLevel up, forge rings and come back ready.",
  dpt="Leve carga cheia: ser arremessado no espaço sem carga é a pior parte.",
  den="Bring a full charge: being thrown into space without charge is the worst part."),
])

cap("receitas","minecraft:crafting_table","Receitas","Recipes",
 "Todas as receitas de bancada do mod.","Every crafting recipe in the mod.",[
 pag("Baterias das Tropas","Corps batteries",
  "Cada bateria só recarrega anéis da sua Tropa. A Índigo não tem bateria: ela usa o **Cajado da Compaixão**.",
  "Each battery only recharges its own Corps' rings. Indigo has no battery: it uses the **Compassion Staff**.",
  receitas=["lanterna:bateria_green","lanterna:bateria_yellow","lanterna:bateria_red","lanterna:bateria_orange","lanterna:bateria_blue","lanterna:bateria_violet","lanterna:bateria_black","lanterna:cajado_compaixao"]),
 pag("Anel e rituais","Ring and rituals",
  "O **Anel Vazio** é a base de todo anel forjado. A **Bateria Cinzelada** é usada no Ritual da Bateria: de noite e a céu aberto. **Fragmentos Estelares** vêm das estrelas que caem à noite (quebre a Rocha Estelar com picareta de ferro).",
  "The **Empty Ring** is the base of every forged ring. The **Chiseled Battery** is used in the Battery Ritual: at night, under open sky. **Star Fragments** come from stars that fall at night (break the Star Rock with an iron pickaxe).",
  itens=["lanterna:fragmento_estelar","lanterna:rocha_estelar","lanterna:merito"],
  receitas=["lanterna:anel_vazio","lanterna:bateria_cinzelada"]),
 pag("Naves","Ships",
  "**Fragmentos de Casco** saem de naves destruídas. Com eles você monta o núcleo, o cargueiro e as peças para mobiliar a sua nave.",
  "**Hull Fragments** drop from destroyed ships. With them you build the core, the freighter and the parts to furnish your ship.",
  itens=["lanterna:fragmento_casco","lanterna:chave_nave"],
  receitas=["lanterna:nucleo_propulsor","lanterna:nave_cargueiro","lanterna:nave_console","lanterna:nave_recarga","lanterna:nave_placa","lanterna:nave_porta"]),
 pag("Guilda e energia","Guild and energy",
  "A **Bandeira da Guilda** finca a posse de um planeta gerado sem dono. As máquinas de energia transformam emoção em FE para a **Mineradora Planetária**.",
  "The **Guild Banner** claims an unowned generated planet. The energy machines turn emotion into FE for the **Planetary Miner**.",
  receitas=["lanterna:bandeira_guilda","lanterna:energia/gerador_emocional","lanterna:energia/refinaria_emocional","lanterna:energia/conversor_luz","lanterna:energia/mineradora_planetaria"]),
])

cap("naves","lanterna:nave_lanceira","Naves e Comércio","Ships and Trade",
 "Pilotar, o hangar e os mercadores do espaço.","Piloting, the hangar and space merchants.",[
 pag("Pilotar","Piloting",
  "Naves chegam com os postos (Lanceira no 10, Patrulha no 20), no **Estaleiro** do hangar, ou tomando uma nave sem dono (monte nela).\n\n**W A S D** e mouse · **Espaço** sobe · {tecla:key.lanterna.nave_descer} desce · **Ctrl** turbo · **X** atira · {tecla:key.lanterna.nave_porta} abre a porta.\n\n**Shift + clique** entra no interior. A **Chave da Nave** chama a nave até você.",
  "Ships come with ranks (Lancer at 10, Patrol at 20), from the hangar's **Shipyard**, or by taking an unowned ship (mount it).\n\n**W A S D** and mouse · **Space** up · {tecla:key.lanterna.nave_descer} down · **Ctrl** boost · **X** fires · {tecla:key.lanterna.nave_porta} opens the door.\n\n**Shift + click** enters the interior. The **Ship Key** calls the ship to you.",
  teclas=[K+"nave_descer",K+"nave_porta",K+"nave_hangar"],
  itens=["lanterna:nave_lanceira","lanterna:nave_patrulha","lanterna:chave_nave"]),
 pag("Mercadores","Merchants",
  "No espaço passam **cargueiros mercadores**. Clique na nave para entrar: o mercador espera no porão e negocia com **Shift + clique direito**. Enquanto alguém está a bordo, o cargueiro espera até 10 minutos.",
  "**Merchant freighters** pass through space. Click the ship to board: the merchant waits in the hold and trades with **Shift + right click**. While someone's aboard, the freighter waits up to 10 minutes.",
  dpt="Guarde Méritos: eles são a moeda dos Veltari e servem para o Anel Vazio.",
  den="Save Merits: they're the Veltari currency and go into the Empty Ring."),
])

cap("branco","anel:white","O Anel Branco","The White Ring",
 "Sete anéis, sete drops, Gênese e o poder da vida.","Seven rings, seven drops, Genesis and the power of life.",[
 pag("A fusão","The fusion",
  "Junte os **sete anéis forjados** e os **sete drops dos chefes**. Qualquer Guardião ou líder de planeta diz o que falta.\n\nCom tudo, **Gênese** aparece no mapa: lá fica o **Altar da Vida**, onde os sete se fundem no Anel Branco, e a rachadura do **Nulo**, o chefe final da linha principal.\n\nA **afinidade dos sete anéis é somada** e passa para o Anel Branco, que tem **todos os construtos de todas as Tropas** (menos os da Morte), já liberados, e o dom **Purificar**: aponte o anel para um jogador corrompido e a luz branca o devolve ao normal (recarga de 10 minutos).\n\nO **Nulo** é gigante e tem **cinco fases**: copia os chefes, puxa quem voa, fecha a rachadura, come a luz dos anéis e, no fim, ataca sem parar. Só o Anel Branco o fere.",
  "Gather the **seven forged rings** and the **seven boss drops**. Any Guardian or planet leader tells you what's missing.\n\nWith everything, **Genesis** appears on the map: there's the **Altar of Life**, where the seven fuse into the White Ring, and the rift of the **Void**, the main line's final boss.\n\nThe **affinity of the seven rings is added up** and carried into the White Ring, which has **every construct of every Corps** (except Death's), already unlocked, and the **Purify** gift: point the ring at a corrupted player and the white light returns them to normal (10-minute cooldown).\n\nThe **Null** is a giant with **five phases**: it copies the bosses, pulls down flyers, closes the rift, devours the rings' light and, at the end, attacks nonstop. Only the White Ring can hurt it.",
  itens=["lanterna:altar_vida","lanterna:rachadura_nulo","lanterna:terra_branca"]),
 pag("Todas as luzes","Every light",
  "O Anel Branco faz **os construtos de todas as Tropas**, menos os da Tropa da Morte, inclusive os da Árvore da Luz, sem comprar os nós.\n\nNa roda ({tecla:key.lanterna.construct_wheel}), cada Tropa é uma **página**: a rodinha do mouse ou as setas trocam.\n\nE ele **sempre volta** para você.",
  "The White Ring makes **every Corps' constructs** except the Death Corps', including Tree of Light ones, without buying the nodes.\n\nIn the wheel ({tecla:key.lanterna.construct_wheel}), each Corps is a **page**: the mouse wheel or arrows switch.\n\nAnd it **always comes back** to you.",
  teclas=[K+"construct_wheel"]),
])

cap("morte","anel:black","A Tropa da Morte","The Death Corps",
 "Ryut, os Silentes e o arsenal da Morte.","Ryut, the Silent Ones and Death's arsenal.",[
 pag("Ryut","Ryut",
  "**Ryut** é o planeta da Tropa Negra. Povo: os **Silentes** e o **Coletor**. Chefe: o **Devorador de Ecos**. Minério: **Obsidiana do Fim**.\n\nO anel Negro chega a quem morre sob o efeito **Wither**, ou corrompe um anel de outra Tropa na morte. O ritual da Morte se faz à beira dela: com 2 corações ou menos.\n\nO **Anel Preto é um só por mundo**: quem o forja vira o **Arauto da Morte**. A corrupção tem **três estágios**: 1) anel rachado e manchas cinzas; 2) anel escuro com manchas pretas, traje mais cinza que colorido; 3) **Portador Negro**. Corrompido, começa a **linha da Purificação** (Odym → Oa → **Ritual da Purificação** no altar dos Guardiões), com **prazo de 30 minutos de jogo** (na metade, o estágio 2): se não terminar, a Morte toma você. **Morto pelo Arauto**, o anel se corrompe **na hora**, mas ainda dá para se purificar. O **Portador Negro** mantém os poderes da própria Tropa, mas anel, traje e construtos ficam **pretos**, e ganha só **Farejar** e **Drenar Vida** da Morte.",
  "**Ryut** is the Black Corps' planet. People: the **Silent Ones** and the **Collector**. Boss: the **Echo Devourer**. Ore: **End Obsidian**.\n\nThe Black ring comes to those who die under **Wither**, or corrupts another Corps' ring on death. The Death ritual is done at its edge: with 2 hearts or less.\n\nThere is **only one Black Ring per world**: whoever forges it becomes the **Herald of Death**. Corruption has **three stages**: 1) cracked ring and gray stains; 2) dark ring with black stains, suit more gray than colored; 3) **Black Bearer**. Once corrupted, the **Purification line** begins (Odym → Oa → the **Rite of Purification** at the Guardians' altar) with a **30-minute play deadline** (stage 2 at the halfway point): if you don't finish, Death takes you. **Killed by the Herald**, the ring is corrupted **at once**, but you can still purify yourself. A **Black Bearer** keeps their own Corps' powers, but ring, suit and constructs turn **black**, and they get only **Scent** and **Drain Life** from Death.",
  itens=["lanterna:obsidiana_do_fim","lanterna:nucleo_do_fim","lanterna:silente_spawn_egg","lanterna:devorador_de_ecos_spawn_egg"]),
 pag("O arsenal da Morte","Death's arsenal",
  "Só o Anel Preto (o Arauto) faz todos (os Portadores Negros têm só Farejar e Drenar Vida):\n\n**Farejar** · sente quem carrega um anel até 400 blocos: distância, direção, rastro de fumaça. Mostra até quem está em outros mundos.\n**Drenar Vida** · suga o alvo e cura você.\n**Mortalha** · nuvem que cega, seca e abafa anéis.\n**Túmulo** · cripta de luz negra que prende o alvo.\n**Ceifa** · a colheita.",
  "Only the Black Ring (the Herald) makes all of them (Black Bearers get only Scent and Drain Life):\n\n**Scent** · smells ring bearers up to 400 blocks: distance, direction, smoke trail. Even shows those in other worlds.\n**Drain Life** · drains the target and heals you.\n**Shroud** · a cloud that blinds, withers and smothers rings.\n**Tomb** · a crypt of black light that traps the target.\n**Reap** · the harvest.",
  dpt="O **Arauto da Morte** é o mais forte do jogo: mais vida, dano, armadura e voo. Nenhum portador o fere, só o **Anel Branco** ou quem tem **nível igual ou maior** que o dele. Os Portadores Negros são bem mais fracos que ele.",
  den="The **Herald of Death** is the strongest in the game: more health, damage, armor and flight. No bearer can hurt him, only the **White Ring** or someone of **equal or higher level**. Black Bearers are far weaker than him."),
])

cap("prepare","lanterna:pergaminho_segredo","Prepare-se","Get Ready",
 "O que já existe no mod e vai importar na próxima saga.","What's already in the mod and will matter in the next saga.",[
 pag("Antes da noite","Before the night",
  "A próxima saga vai cobrar tudo o que você construiu até aqui. O que vale preparar desde já:\n\n**Nível 50 ou mais** · é o mínimo para encarar os lendários, e não vai ficar mais fácil.\n**Os anéis forjados** · guarde-os e cuide da afinidade de cada um.\n**O Anel Branco** · quem tiver um vai sentir a diferença.\n**Ryut e a Tropa Negra** · conheça o planeta, o povo e o Farejar.\n**Insígnias e Fragmentos Lendários** · não jogue nada fora.",
  "The next saga will call on everything you've built so far. Worth preparing now:\n\n**Level 50 or higher** · the minimum to face the legendaries, and it won't get easier.\n**Your forged rings** · keep them and tend each one's affinity.\n**The White Ring** · whoever has one will feel the difference.\n**Ryut and the Black Corps** · know the planet, its people and Scent.\n**Insignias and Legendary Fragments** · don't throw anything away.",
  itens=["anel:white","lanterna:fragmento_lendario","lanterna:obsidiana_do_fim","lanterna:essencia_poder"]),
], cor="B8A060")

cap("noite","anel:black","Transmissão Interceptada","Intercepted Transmission",
 "...","...",[
 pag("[sinal fraco]","[weak signal]",
  "...os anéis de Ryut não estão mais em silêncio.\n\nAlgo está ||farejando|| quem carrega luz, em todos os mundos ao mesmo tempo. Os Silentes pararam de ||sussurrar||.\n\nGuarde bem o seu anel.\nNem toda luz que se apaga volta da mesma cor.\n\n**— A Noite Mais Densa —**\n||em breve||",
  "...the rings of Ryut are no longer silent.\n\nSomething is ||smelling out|| those who carry light, on every world at once. The Silent Ones stopped ||whispering||.\n\nKeep your ring close.\nNot every light that goes out comes back the same color.\n\n**— The Darkest Night —**\n||coming soon||"),
], cor="7A5AB0", spoiler=True)

cap("necron","lanterna:nucleo_bateria","A Saga do Necron","The Necron Saga",
 "O caminho inteiro, do chamado à purificação.","The whole path, from the call to the purification.",[
 pag("O chamado e as duas luzes","The call and the two lights",
  "Quando os 7 aspectos da Árvore das Emoções chegam ao nível 6, o Embrião da Vida escurece, o Anel da Vida se divide e o **Necron** cai na Terra. A luz de **um anel só** não fere os servos dele.\n\nForje um **segundo anel** de outra Tropa e coloque-o no **punho** (aba Lanterna). Com os dois:\n**· os servos sentem os golpes**\n**· o laser sai das duas mãos**, um feixe de cada cor\n**· a roda de construtos ganha uma aba** com os construtos do segundo anel (rodinha, setas ou Tab).",
  "When the 7 aspects of the Tree of Emotions reach level 6, the Embryo of Life darkens, the Ring of Life splits and the **Necron** falls on Earth. The light of **a single ring** can't hurt his servants.\n\nForge a **second ring** of another Corps and wear it on your **wrist** (Lantern tab). With both:\n**· servants feel your blows**\n**· the laser fires from both hands**, one beam of each color\n**· the construct wheel gets a tab** with the second ring's constructs (wheel, arrows or Tab).",
  dpt="Durante a guerra contra o Necron, os Lanternas de todas as Tropas fazem trégua e atacam os servos com você.",
  den="During the war against the Necron, Lanterns of every Corps call a truce and fight the servants with you."),
 pag("Defender as Tropas","Defending the Corps",
  "**Âncoras:** antes da defesa da sua Tropa, destrua as três âncoras da Morte (derrote os 5 guardas, quebre o núcleo).\n\n**A defesa:** uma fenda abre sobre a Bateria Central; aguente 3 ondas sem deixar os servos sugarem a bateria.\n\n**Os pedidos:** outras Tropas pedem socorro. Antes de cada defesa há uma missão própria: **soltar presos** das jaulas, **caçar os ladrões** de fragmentos da bateria ou **resgatar moradores** cercados numa bolha de luz.",
  "**Anchors:** before your Corps' defense, destroy the three anchors of Death (defeat the 5 guards, break the core).\n\n**The defense:** a rift opens above the Central Battery; hold 3 waves without letting the servants drain the battery.\n\n**The calls:** other Corps ask for help. Before each defense there's a mission of its own: **free prisoners** from cages, **hunt the thieves** of battery fragments or **rescue residents** surrounded, in a bubble of light."),
 pag("As quedas e as ruínas","The falls and the ruins",
  "O Amor, o Índigo e a Esperança **caem** (menos a sua Tropa). A forja continua, mas a terra morre e os servos rondam.\n\nNo planeta caído: **cave os montes de escombros** para soltar 3 Lanternas feridos, derrote o **Carcereiro** (Velkara, Ossuth ou Grenmor) e leve o **Núcleo da Bateria** até a Bateria Central da sua Tropa. Ele fica guardado lá até a purificação.",
  "Love, Indigo and Hope **fall** (except your own Corps). The forge still works, but the land dies and servants roam.\n\nOn the fallen world: **dig through the rubble mounds** to free 3 wounded Lanterns, defeat the **Jailer** (Velkara, Ossuth or Grenmor) and carry the **Battery Core** to your Corps' Central Battery. It is kept there until the purification.",
  itens=["lanterna:nucleo_bateria","lanterna:fragmento_bateria"]),
 pag("A Terra escurece","Earth darkens",
  "Cada etapa é uma missão, e a cada uma a terra morta cresce:\n\n**1.** Feche as **três fendas** sobre a Terra (6 servos cada).\n**2.** Defenda o **escudo de luz de Aurora** em 3 ondas (se cair, recomeça).\n**3.** Siga o **Pulso da Vida**: fique perto dos canteiros de flores até o anel escutar, e desça o poço até o **Santuário**.\n**4.** Proteja o **Embrião da Vida** dos servos que saem das paredes.",
  "Each stage is a mission, and with each one the dead land grows:\n\n**1.** Close the **three rifts** over Earth (6 servants each).\n**2.** Defend **Aurora's shield of light** through 3 waves (if it falls, it starts over).\n**3.** Follow the **Pulse of Life**: stay near the flower patches until the ring hears it, then go down the shaft to the **Sanctuary**.\n**4.** Protect the **Embryo of Life** from the servants coming out of the walls."),
 pag("A Última Luz","The Last Light",
  "Antes da batalha, **chame as Tropas que resistem**: fique perto da Bateria Central de cada planeta até ela responder. **Três bastam**, mas cada Tropa a mais traz **3 Lanternas** para a luta (e as Tropas caídas cujo núcleo você salvou mandam mais 2).\n\nNa cratera, o Necron luta em **quatro fases**: Amor, Índigo, Esperança e Morte. Quem cai na batalha não morre: os Lanternas puxam você para a borda. Ele só sente golpes de verdade a partir do **nível 200**.",
  "Before the battle, **call the Corps still standing**: stay near each planet's Central Battery until it answers. **Three are enough**, but every extra Corps brings **3 Lanterns** to the fight (and fallen Corps whose core you saved send 2 more).\n\nAt the crater, the Necron fights in **four phases**: Love, Indigo, Hope and Death. Falling in the battle doesn't kill you: the Lanterns pull you to the rim. He only really feels blows from **level 200** on."),
 pag("A Purificação","The Purification",
  "Com o Necron derrotado:\n\n**O Despertar:** volte ao Santuário. O Embrião acorda, voa até o céu da cratera e uma **onda de luz** cura a Terra; a bateria da Morte vira a **Bateria Branca**.\n**Os corrompidos:** nos planetas caídos, derrote os 6 Lanternas corrompidos (eles são purificados, não morrem) e o líder (**Aelira**, **Tovrek** ou **Irmã Calyx**). A Tropa volta e o núcleo guardado reacende a bateria.\n**O Ritual da Terra:** na Bateria Branca, as sete luzes libertam o Embrião.",
  "With the Necron defeated:\n\n**The Awakening:** return to the Sanctuary. The Embryo wakes, flies to the sky over the crater and a **wave of light** heals the Earth; the Battery of Death becomes the **White Battery**.\n**The corrupted:** on the fallen worlds, defeat the 6 corrupted Lanterns (they are purified, not killed) and the leader (**Aelira**, **Tovrek** or **Sister Calyx**). The Corps returns and the kept core rekindles the battery.\n**The Ritual of Earth:** at the White Battery, the seven lights set the Embryo free.",
  dpt="Tudo da saga é do mundo: no multiplayer, o que um jogador faz conta para todos.",
  den="Everything in the saga belongs to the world: in multiplayer, what one player does counts for everyone."),
 pag("A Tentação","The Temptation",
  "Durante a guerra, quem morre para a Morte (o Necron, os servos, um Lanterna Negro) às vezes ouve a voz dela; na batalha final, quem cai também. Aparece a pergunta:\n\n**Não. Eu luto.** Os seus anéis ficam presos onde você caiu (fora da Terra, na borda da cratera; uma coluna roxa mostra o lugar). Vá embora, volte, pegue-os e enfrente o **seu Espectro**: você mesmo, de traje negro e Anel da Morte. Vencendo, a Morte te deixa em paz por 3 mortes.\n\n**Sim. Eu aceito a Morte.** O anel fica negro e começa a **linha corrompida**: o Juramento diante do Necron, apagar três Baterias Centrais (cada uma derruba a Tropa), prender o Embrião na escuridão e a **Noite Eterna**, em que o Necron vence e todas as Tropas caem.",
  "During the war, whoever dies to Death (the Necron, his servants, a Black Lantern) sometimes hears its voice; in the final battle, whoever falls may too. A question appears:\n\n**No. I fight.** Your rings are left trapped where you fell (outside Earth, at the crater's rim; a purple column marks the spot). Walk away, come back, take them and face **your Specter**: yourself, in the black suit with the Ring of Death. Win, and Death leaves you alone for 3 deaths.\n\n**Yes. I accept Death.** Your ring turns black and the **corrupted line** begins: the Oath before the Necron, putting out three Central Batteries (each one brings its Corps down), trapping the Embryo in darkness and the **Eternal Night**, when the Necron wins and every Corps falls.",
  dpt="A escolha vale para o mundo: se alguém completar a linha corrompida, o Necron vence para todos.",
  den="The choice affects the world: if someone completes the corrupted line, the Necron wins for everyone."),
], cor="5A4A7A", spoiler=True)

json.dump({"capitulos":C}, open('src/main/resources/assets/lanterna/guia/guia.json','w'), ensure_ascii=False, indent=1)
print(len(C), sum(len(c["paginas"]) for c in C))

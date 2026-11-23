# Versões de teste da saga do Necron (0.59.0)

Cada versão de teste libera uma parte da saga e complementa a anterior. Os testadores podem continuar no mesmo mundo:
quando chegam ao fim do que a versão libera, aparece um aviso no chat; ao entrar no mundo com a versão seguinte, a
missão que estava esperando começa sozinha. A versão normal (0.59.0, sem "teste") libera tudo.

Como gerar: `gradle build -Pfase=N -Pmod_version=0.59.0-testeN` (com a linha 7 do mods.toml igual à versão).

## Teste 1 — O começo (Capítulos 1 e 2)
- Saga: a Vila Aurora em pânico (Sgt. Rui), o resgate dos 4 moradores, o rastro de marcas até a cratera, a Queda do
  Necron e o primeiro encontro com ele (ele derruba o portador e a fuga vem numa cena).
- Sistemas novos para testar: geração rápida das cidades (Vila Aurora e cidades dos planetas aparecem bem mais
  rápido e os moradores da vila surgem logo que você chega); o modelo 3D novo do Necron e suas animações (golpes com
  a foice).
- Fim desta versão: depois do encontro com o Necron (a missão "Duas Luzes" fica para o Teste 2).

## Teste 2 — Duas Luzes (Capítulos 3 e 4)
- Saga: forjar um segundo anel e usar os dois juntos, o treino e o duelo no planeta da Tropa, a primeira fenda e a
  defesa das Âncoras da Morte no planeta da sua Tropa.
- Sistemas para testar: usar dois anéis ao mesmo tempo; o segundo anel em aba separada na roda de constructos; o
  laser do punho saindo dos dois braços (dois feixes).
- Fim desta versão: o pedido de socorro de outra Tropa fica para o Teste 3.

## Teste 3 — Socorro e Ruínas (Capítulos 5 e 6)
- Saga: o socorro no planeta de outra Tropa (resgatar civis: chegar perto do civil inicia a bolha de resgate), as
  Ruínas de uma Tropa caída, o Carcereiro e o Núcleo de Bateria.
- Sistemas para testar: frases variadas dos Lanternas que pedem ajuda; trégua dos NPCs durante a guerra do Necron.
- Fim desta versão: a Terra começa a escurecer no Teste 4.

## Teste 4 — A Terra Escurece e a Última Luz (Capítulos 7 e 8)
- Saga: as fendas na Terra, o cerco à Vila Aurora, os pulsos, o Santuário e o embrião, chamar as Tropas aliadas e a
  batalha final contra o Necron (com a cena da queda quando ele é vencido).
- Sistema novo: a Forma do Necron (dom do Anel Preto do Arauto da Morte: vira o Necron por 60 s, com a foice) e o
  Arauto da Morte sem recarga.
- Fim desta versão: a purificação (depois da vitória) fica para o Teste 5. Morrer na batalha final ainda não traz a
  Tentação.

## Teste 5 — A saga inteira (Capítulo 9, epílogo e o final corrompido)
- Saga: o despertar, a purificação da Terra e dos planetas, o ritual, o epílogo com a Medalha da Última Luz e o
  presságio do Parallax.
- O segundo final: morrer na guerra pode trazer a Tentação. Recusar = recuperar os anéis e vencer o próprio
  Espectro; aceitar = a linha corrompida (Juramento, Apagar as Luzes, A Caçada, Aurora Apagada, Coração da Vida,
  Resistência e Noite Eterna).
- Todas as cutscenes da saga.

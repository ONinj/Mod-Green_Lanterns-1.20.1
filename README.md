# Lanterna — mod de Minecraft (Forge 1.20.1)

Anéis de poder, baterias e o espectro emocional, com espaço sideral e planetas.

## Como compilar
Cada push no GitHub roda o Actions ("Compilar mod"). O `.jar` fica em **Actions → última execução → Artifacts → lanterna-mod**.

## Requisitos
- Minecraft 1.20.1 + Forge 47.3+
- Nenhum outro mod é necessário

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
2. Poderes: rajada, escudo, constructos (entidades), menu radial de constructos
4. Uniformes que aparecem ao ativar o anel
5. Estruturas: nave caída (verde), rio de sangue (vermelho)
6. Espaço: acima de certa altura, teleporte para a dimensão espacial
7. Planetas: habitantes, vilas, minérios e mobs próprios

## Arte
As texturas atuais são provisórias e genéricas. Substitua os PNGs em `assets/lanterna/textures/` mantendo os mesmos nomes. A pedra do anel e o núcleo da bateria devem ficar em tons de cinza/branco, porque o jogo pinta na cor da Tropa.

## Teste visual automático (desenvolvimento)
`gradle runTestClient` abre o jogo com um roteiro que roda cerimônias, liga o anel, voa e tira prints em `run/screenshots`.
Precisa de um mundo chamado `test` em `run/saves`. O código de teste (`client/devtest`) não vai para o .jar.

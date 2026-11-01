# Baterias Centrais — pacote para integrar

Este é o pacote **Baterias_Centrais** (7 Tropas: verde, amarelo, vermelho, laranja, azul, indigo, violeta — Morte e Vida não têm Bateria Central).
Está fora de `src/` de propósito, para não quebrar o build: os arquivos ainda usam os nomes provisórios
`seu_modid` e `seu.pacote.baterias`. O mod usa `mod_id=lanterna` e o pacote `com.lanterna`.

## Como integrar
1. Leia `LEIA-ME.txt` (explica modelos, hitbox, portal, dimensões e o código).
2. Copie `forge/src/main/resources/assets/seu_modid/*` para `src/main/resources/assets/lanterna/`
   e `forge/src/main/resources/data/seu_modid/*` para `src/main/resources/data/lanterna/`,
   trocando `seu_modid` por `lanterna` dentro dos .json.
3. As estruturas `.nbt` (data/.../structures) guardam os nomes dos blocos como `seu_modid:bateria_...`:
   regere com o modid certo (o gerador fica em `gerador/`) ou use um script de troca de modid em NBT.
4. Copie `forge/src/main/java/seu/pacote/baterias/` para `src/main/java/com/lanterna/baterias/`,
   trocando o pacote para `com.lanterna.baterias` e `MODID` para `"lanterna"`.
5. Registre: `ModBaterias.register(modEventBus);` e, no cliente,
   `BlockEntityRenderers.register(ModBaterias.NUCLEO_BE.get(), BateriaNucleoRenderer::new);`
6. Depende do GeckoLib 4.8.4 (o mod já usa).

`previas/` tem as imagens de referência; `blockbench/` os .bbmodel para edição; `gerador/` os scripts.

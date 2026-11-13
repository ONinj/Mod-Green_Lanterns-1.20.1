"""Gera as tabelas de itens dos baús das casas (0.48): data/lanterna/loot_tables/chests/<planeta>/{casa,barril}.json"""
import json, os
BASE = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/data/lanterna/loot_tables/chests')

def item(nome, peso, mn=1, mx=1):
    e = {"type": "minecraft:item", "name": nome, "weight": peso}
    if mx > 1 or mn != 1:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": mn, "max": mx}}]
    return e

def tabela(pools):
    return {"type": "minecraft:chest", "pools": pools}

def pool(rolls_min, rolls_max, entradas):
    return {"rolls": {"type": "minecraft:uniform", "min": rolls_min, "max": rolls_max}, "entries": entradas}

COMIDA = [item("minecraft:bread", 10, 1, 4), item("minecraft:apple", 8, 1, 3), item("minecraft:baked_potato", 6, 1, 4),
          item("minecraft:cooked_mutton", 4, 1, 3), item("minecraft:honey_bottle", 2)]
COMUM = [item("minecraft:torch", 6, 2, 8), item("minecraft:string", 4, 1, 4), item("minecraft:paper", 4, 1, 5),
         item("minecraft:iron_nugget", 5, 2, 9), item("minecraft:emerald", 3, 1, 3), item("minecraft:book", 2)]

PLANETAS = {
    # planeta: (itens da casa, itens do barril)
    "oa": ([item("lanterna:jade_estelar", 6, 1, 3), item("lanterna:merito", 3, 1, 2), item("minecraft:glowstone_dust", 5, 2, 6),
            item("minecraft:writable_book", 2), item("minecraft:lime_dye", 3, 1, 4), item("minecraft:emerald", 4, 1, 4)],
           [item("minecraft:quartz", 6, 2, 8), item("minecraft:birch_log", 5, 2, 8), item("minecraft:sea_lantern", 2, 1, 2), item("lanterna:jade_estelar", 3, 1, 2)]),
    "korugar": ([item("minecraft:spider_eye", 8, 1, 4), item("lanterna:fragmento_de_pavor", 5, 1, 2), item("minecraft:ink_sac", 5, 1, 4),
                 item("minecraft:black_candle", 3, 1, 3), item("minecraft:gold_nugget", 4, 2, 8), item("minecraft:fermented_spider_eye", 2)],
                [item("minecraft:coal", 7, 2, 10), item("minecraft:blackstone", 5, 4, 16), item("minecraft:spider_eye", 4, 1, 3), item("minecraft:arrow", 4, 4, 12)]),
    "ysmault": ([item("lanterna:carne_de_fera", 8, 1, 3), item("minecraft:cooked_beef", 8, 1, 4), item("lanterna:ira_bruta", 5, 1, 2),
                 item("minecraft:iron_ingot", 5, 1, 4), item("minecraft:magma_cream", 3, 1, 3), item("lanterna:martelo_brakkari", 1)],
                [item("minecraft:iron_ingot", 5, 1, 5), item("minecraft:basalt", 5, 4, 16), item("minecraft:blaze_powder", 2, 1, 3), item("minecraft:leather", 4, 1, 5)]),
    "odym": ([item("lanterna:perola_do_amanha", 5, 1, 2), item("minecraft:cooked_cod", 8, 1, 4), item("minecraft:cooked_salmon", 6, 1, 3),
              item("minecraft:prismarine_crystals", 5, 1, 5), item("lanterna:bencao_aelinos", 1), item("minecraft:nautilus_shell", 1)],
             [item("minecraft:kelp", 6, 4, 16), item("minecraft:prismarine_shard", 6, 2, 8), item("minecraft:sea_pickle", 3, 1, 4), item("minecraft:sponge", 1)]),
    "okaara": ([item("minecraft:gold_ingot", 8, 2, 6), item("minecraft:gold_nugget", 8, 4, 16), item("minecraft:raw_gold", 5, 1, 4),
                item("lanterna:ambar_da_posse", 6, 1, 3), item("minecraft:emerald", 5, 1, 5), item("minecraft:golden_apple", 1)],
               [item("minecraft:gold_ingot", 4, 1, 4), item("minecraft:sandstone", 5, 4, 16), item("minecraft:gold_nugget", 6, 4, 12), item("lanterna:ambar_da_posse", 2, 1, 2)]),
    "nok": ([item("minecraft:apple", 10, 2, 6), item("lanterna:erva_taleun", 6, 1, 3), item("lanterna:lagrima_indigo", 5, 1, 2),
             item("lanterna:gratidao", 4, 1, 3), item("minecraft:purple_candle", 3, 1, 3), item("minecraft:golden_apple", 1)],
            [item("minecraft:apple", 6, 2, 6), item("minecraft:wheat", 5, 2, 10), item("minecraft:bone_meal", 4, 2, 8), item("lanterna:erva_taleun", 3, 1, 2)]),
    "zamaron": ([item("minecraft:lily_of_the_valley", 8, 1, 4), item("minecraft:allium", 6, 1, 4), item("minecraft:pink_petals", 6, 1, 6),
                 item("minecraft:peony", 4, 1, 2), item("minecraft:amethyst_shard", 6, 1, 5), item("lanterna:cristal_devoto", 5, 1, 2),
                 item("minecraft:sweet_berries", 5, 2, 6), item("minecraft:cake", 1)],
                [item("minecraft:azure_bluet", 5, 1, 4), item("minecraft:rose_bush", 3, 1, 2), item("minecraft:amethyst_shard", 5, 2, 6), item("minecraft:honeycomb", 3, 1, 3)]),
    "ryut": ([item("minecraft:bone", 10, 2, 8), item("lanterna:alma", 4, 1, 2), item("lanterna:obsidiana_do_fim", 3, 1, 1),
              item("minecraft:soul_torch", 6, 2, 6), item("minecraft:gray_candle", 4, 1, 3), item("minecraft:dried_kelp", 5, 2, 6)],
             [item("minecraft:bone_block", 4, 1, 3), item("minecraft:coal", 6, 2, 8), item("minecraft:soul_sand", 5, 4, 12), item("minecraft:bone", 5, 2, 8)]),
}

for pl, (casa, barril) in PLANETAS.items():
    d = os.path.join(BASE, pl)
    os.makedirs(d, exist_ok=True)
    json.dump(tabela([pool(2, 4, COMIDA), pool(2, 4, casa), pool(1, 2, COMUM)]), open(os.path.join(d, "casa.json"), "w"), indent=1)
    json.dump(tabela([pool(2, 4, barril), pool(1, 2, COMIDA)]), open(os.path.join(d, "barril.json"), "w"), indent=1)
json.dump(tabela([{"rolls": 1, "entries": [item("lanterna:reliquia", 1)]}, {"rolls": 1, "entries": [item("lanterna:merito", 1, 3, 3)]}]),
          open(os.path.join(BASE, "oa", "reliquia.json"), "w"), indent=1)
print("ok")

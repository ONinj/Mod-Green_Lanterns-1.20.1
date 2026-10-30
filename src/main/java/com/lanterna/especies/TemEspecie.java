package com.lanterna.especies;

/** Implementado pelas entidades humanoides (Lanternas, piratas, mercadores). */
public interface TemEspecie {
    /** id da espécie, ex.: "ilith", "korrvaal", "qiri" */
    String getEspecie();
    /** "padrao", "grande" ou "pequeno" (ver tabela no LEIA-ME) */
    String getTipoCorpo();
    /** 1, 2 ou 3 (arquivo especie_<id>_v<n>.png) */
    int getVariantePele();
    /** null = sem roupa; ex.: "roupa_tropa_green", "roupa_pirata" (o tipo de corpo é acrescentado automaticamente) */
    default String getRoupa() { return null; }
    /** true = desenha roupa_oficial por cima da roupa (faixas de patente) */
    default boolean isOficial() { return false; }
    /** true = Lanterna Negro: usa corrupcao_<id>.png no lugar da pele */
    default boolean isCorrompido() { return false; }
    /** true para espécies de pele translúcida (lumiae, aurith) */
    default boolean isTranslucida() {
        String s = getEspecie();
        return "lumiae".equals(s) || "aurith".equals(s);
    }
}

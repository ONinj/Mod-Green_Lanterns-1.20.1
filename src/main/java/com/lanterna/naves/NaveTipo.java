package com.lanterna.naves;

import org.jetbrains.annotations.Nullable;

/**
 * Dados de cada nave, tirados dos .geo.json do pacote (em pixels do modelo; 16 = 1 bloco; frente = -z, esquerda = +x):
 * assentos (o primeiro é o piloto), bocas dos canhões, caixas de colisão col_N (origem x,y,z + tamanho) e a dobradiça da porta.
 * Gerado a partir dos .geo.json do pacote.
 */
public enum NaveTipo {
    NAVE_LANCEIRA("nave_lanceira", 120, 1.1f, 3.0f, 2.4f, "tropa", new double[][]{{0.0,14.0,-26.0},{0.0,14.0,-6.0}}, new double[][]{{-44.0,12.0,-16.0},{44.0,12.0,-16.0}}, new double[][]{{-16.0,8.0,-72.0,32.0,20.0,32.0},{-16.0,8.0,-40.0,32.0,35.0,46.0},{-18.0,8.0,6.0,36.0,38.0,20.0},{-18.0,8.0,26.0,36.0,32.0,46.0},{-47.0,10.0,8.0,29.0,10.0,48.0},{18.0,10.0,8.0,29.0,10.0,48.0},{-2.0,34.0,44.0,4.0,14.0,26.0},{-3.0,0.0,-54.0,6.0,8.0,98.0}}, new double[]{17.0,42.0,16.0}, new int[]{}),
    NAVE_PIRATA("nave_pirata", 170, 0.85f, 5.0f, 3.5f, "12", new double[][]{{0.0,32.0,-78.0},{-12.0,32.0,-66.0},{12.0,32.0,-66.0},{0.0,32.0,-62.0}}, new double[][]{{0.0,56.5,-46.0},{0.0,56.5,40.0},{0.0,17.0,-110.0}}, new double[][]{{-36.0,8.0,-60.0,72.0,42.0,152.0},{-26.0,22.0,-94.0,52.0,42.0,42.0},{36.0,10.0,-20.0,25.0,30.0,90.0},{-20.0,12.0,92.0,40.0,36.0,36.0},{-40.0,12.0,-128.0,18.0,24.0,68.0},{22.0,12.0,-128.0,18.0,24.0,68.0},{-48.0,14.0,-42.0,12.0,26.0,44.0},{-10.0,50.0,-46.0,20.0,11.0,118.0}}, new double[]{-37.0,10.0,24.0}, new int[]{1,2}),
    NAVE_PATRULHA("nave_patrulha", 230, 0.95f, 5.0f, 3.0f, "tropa", new double[][]{{-9.0,19.0,-72.0},{9.0,19.0,-72.0},{-9.0,19.0,-52.0},{9.0,19.0,-52.0}}, new double[][]{{-54.0,42.5,-18.0},{54.0,42.5,-18.0}}, new double[][]{{-28.0,10.0,-112.0,56.0,30.0,24.0},{-28.0,10.0,-88.0,56.0,39.0,78.0},{-28.0,8.0,-10.0,56.0,44.0,80.0},{-16.0,14.0,70.0,32.0,28.0,38.0},{-64.0,12.0,-18.0,20.0,24.0,118.0},{44.0,12.0,-18.0,20.0,24.0,118.0},{-44.0,18.0,-12.0,88.0,8.0,72.0},{-63.0,36.0,-8.0,126.0,20.0,106.0}}, new double[]{27.0,44.0,-24.0}, new int[]{1,2}),
    NAVE_CARGUEIRO("nave_cargueiro", 280, 0.55f, 6.0f, 5.0f, "12", new double[][]{{0.0,49.0,-136.0},{-14.0,49.0,-124.0},{14.0,49.0,-124.0}}, new double[][]{}, new double[][]{{-32.0,12.0,-160.0,64.0,34.0,50.0},{-32.0,40.0,-155.0,64.0,56.0,45.0},{-48.0,12.0,-110.0,96.0,66.0,202.0},{-92.0,14.0,-106.0,44.0,46.0,162.0},{48.0,14.0,-106.0,44.0,46.0,162.0},{-82.0,18.0,58.0,36.0,44.0,94.0},{46.0,18.0,58.0,36.0,44.0,94.0},{-10.0,74.0,68.0,20.0,20.0,88.0}}, new double[]{0.0,14.0,91.0}, new int[]{}),
    NAVE_PIRATA_CAPITANIA("nave_pirata_capitania", 950, 0.45f, 12.0f, 8.0f, "12", new double[][]{{0.0,125.0,116.0},{-22.0,125.0,128.0},{22.0,125.0,128.0},{-22.0,125.0,146.0},{22.0,125.0,146.0},{0.0,125.0,152.0}}, new double[][]{{-64.0,133.0,-166.0},{64.0,133.0,-166.0},{-70.0,133.0,-16.0},{70.0,133.0,-16.0}}, new double[][]{{-120.0,16.0,-200.0,90.0,64.0,448.0},{-30.0,16.0,-200.0,154.0,64.0,136.0},{-30.0,16.0,64.0,154.0,64.0,184.0},{-110.0,16.0,-288.0,220.0,60.0,88.0},{-88.0,80.0,-150.0,192.0,42.0,330.0},{-48.0,116.0,96.0,96.0,44.0,76.0},{-180.0,20.0,-180.0,60.0,56.0,470.0},{120.0,20.0,120.0,60.0,56.0,170.0}}, new double[]{-122.0,18.0,-150.0}, new int[]{1,2,3,4}),
    DESTROCOS_1("destrocos_1", 60, 0f, 3.0f, 1.6f, "", new double[][]{}, new double[][]{}, new double[][]{{-18.0,0.0,-84.0,36.0,30.0,140.0},{-80.0,0.0,6.0,44.0,8.0,54.0},{26.0,0.0,40.0,26.0,18.0,42.0}}, null, new int[]{}),
    DESTROCOS_2("destrocos_2", 80, 0f, 5.0f, 3.0f, "", new double[][]{}, new double[][]{}, new double[][]{{-60.0,0.0,-100.0,120.0,76.0,162.0},{46.0,0.0,-48.0,50.0,40.0,58.0},{36.0,0.0,30.0,48.0,20.0,60.0},{-100.0,0.0,52.0,40.0,40.0,56.0},{-4.0,0.0,76.0,48.0,40.0,48.0}}, null, new int[]{}),
    DESTROCOS_3("destrocos_3", 60, 0f, 4.0f, 1.8f, "", new double[][]{}, new double[][]{}, new double[][]{{18.0,0.0,-62.0,44.0,24.0,44.0},{-70.0,0.0,6.0,60.0,36.0,74.0},{-20.0,0.0,-16.0,40.0,6.0,32.0},{40.0,0.0,10.0,44.0,8.0,40.0}}, null, new int[]{});

    public final String id;
    public final int maxHp;
    /** Velocidade máxima em blocos/tick (sem turbo). */
    public final float speed;
    /** Caixa principal (para o terreno). */
    public final float width, height;
    /** "tropa" (8 cores), "12" (pinturas 1 e 2) ou "" (textura única). */
    public final String pinturas;
    public final double[][] assentos, bocas, colisao;
    @Nullable public final double[] porta;
    /** Índices (1..4) dos canhões que giram (torres). */
    public final int[] torres;

    NaveTipo(String id, int hp, float speed, float w, float h, String pinturas, double[][] assentos, double[][] bocas,
             double[][] colisao, @Nullable double[] porta, int[] torres) {
        this.id = id; this.maxHp = hp; this.speed = speed; this.width = w; this.height = h; this.pinturas = pinturas;
        this.assentos = assentos; this.bocas = bocas; this.colisao = colisao; this.porta = porta; this.torres = torres;
    }

    /** Pintura que existe para este tipo (corrige valores vazios ou errados, ex.: /summon sem NBT). */
    public String pinturaValida(String p) {
        if (pinturas.isEmpty()) return "";
        if (pinturas.equals("12")) return p.equals("1") || p.equals("2") ? p : "1";
        for (String c : new String[]{"green", "yellow", "red", "blue", "violet", "indigo", "orange", "black"}) if (c.equals(p)) return p;
        return "green";
    }

    public boolean destrocos() {
        return id.startsWith("destrocos");
    }

    public boolean torre(int i) {
        for (int t : torres) if (t == i) return true;
        return false;
    }

    public static NaveTipo byId(String id) {
        for (NaveTipo t : values()) if (t.id.equals(id)) return t;
        return NAVE_LANCEIRA;
    }

    /** Estrutura do interior (data/lanterna/structures/interior_<x>.nbt), ou null para destroços. */
    @Nullable
    public String interior() {
        return switch (this) {
            case NAVE_LANCEIRA -> "interior_lanceira";
            case NAVE_PIRATA -> "interior_pirata";
            case NAVE_PATRULHA -> "interior_patrulha";
            case NAVE_CARGUEIRO -> "interior_cargueiro";
            case NAVE_PIRATA_CAPITANIA -> "interior_capitania";
            default -> null;
        };
    }
}

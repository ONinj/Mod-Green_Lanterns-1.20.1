package com.lanterna.space;

import java.util.Random;

/** Nomes de planetas inventados (sempre os mesmos para a mesma semente). */
public final class PlanetNames {
    private static final String[] START = {
            "Ka", "Vel", "Xan", "Or", "Thra", "Zy", "Mor", "Ish", "Quel", "Bra", "Ny", "Sol", "Ux", "Rhe", "Tal",
            "Gor", "Ael", "Dra", "Kry", "Lun", "Phe", "Sei", "Vor", "Zan", "Eri", "Hal", "Ixa", "Jor", "Ome", "Tyr"};
    private static final String[] MIDDLE = {
            "ra", "lo", "the", "ne", "va", "ri", "do", "ma", "xi", "su", "ka", "lle", "ro", "ze", "ny", "tho", "qua"};
    private static final String[] END = {
            "x", "ris", "th", "on", "ar", "ia", "us", "ek", "or", "is", "a", "um", "yn", "os", "ae", "ul", "ix"};
    private static final String[] SUFFIX = {" Prime", " II", " III", " IV", " V", " VII", " Maior", " Menor"};

    public static String generate(Random r) {
        StringBuilder sb = new StringBuilder(START[r.nextInt(START.length)]);
        int middles = r.nextInt(3);
        for (int i = 0; i < middles; i++) sb.append(MIDDLE[r.nextInt(MIDDLE.length)]);
        sb.append(END[r.nextInt(END.length)]);
        if (r.nextInt(5) == 0) {
            int cut = 1 + r.nextInt(Math.max(1, sb.length() - 2));
            sb.insert(cut, '\'');
            sb.setCharAt(cut + 1, Character.toUpperCase(sb.charAt(cut + 1)));
        }
        if (r.nextInt(4) == 0) sb.append(SUFFIX[r.nextInt(SUFFIX.length)]);
        return sb.toString();
    }

    private PlanetNames() {}
}

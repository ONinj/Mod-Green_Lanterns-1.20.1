package com.lanterna.power;

/**
 * Linha do tempo da recarga pela bateria do slot (em ticks, 20 = 1 segundo):
 *
 *   0 ..10  portal abre na cintura
 *   8 ..22  a bateria sobe de dentro do portal
 *   20..28  o braço do anel vai até a bateria
 *   28..58  energia flui da bateria para o anel  (a carga entra no tick 40)
 *   56..64  o braço volta
 *   60..70  a bateria desce de volta para o portal
 *   66..76  o portal fecha
 */
public final class RechargeTimeline {
    public static final int TOTAL = 76;
    public static final int CHARGE_AT = 40;

    public static final int PORTAL_OPEN_END = 10;
    public static final int RISE_START = 8, RISE_END = 22;
    public static final int REACH_START = 20, REACH_END = 28;
    public static final int FLOW_START = 28, FLOW_END = 58;
    public static final int RETRACT_START = 56, RETRACT_END = 64;
    public static final int SINK_START = 60, SINK_END = 70;
    public static final int CLOSE_START = 66;

    private RechargeTimeline() {}
}

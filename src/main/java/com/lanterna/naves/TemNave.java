package com.lanterna.naves;

import net.minecraft.world.phys.Vec3;

/** Implementado pela entidade da nave. */
public interface TemNave {
    /** nome dos arquivos .geo/.animation: nave_lanceira, nave_pirata, nave_patrulha, nave_cargueiro, nave_pirata_capitania, destrocos_1..3 */
    String getTipoNave();
    /** Lanceira/Patrulha: green, yellow, red, blue, violet, indigo, orange, black. Pirata/Cargueiro/Capitânia: "1" ou "2".
     *  Destroços: "" (textura única destrocos_N.png). */
    String getPintura();

    /** Ângulo das torres em relação à nave, em graus (0 = para a frente). */
    default float getAnguloTorre(float partialTick) { return 0f; }

    /**
     * Converte o pivô de um osso assento_* (em pixels do modelo, como está no .geo.json) para a posição no mundo.
     * Modelo: frente = -z, esquerda = +x, 16 pixels = 1 bloco.
     * Ex.: Lanceira assento_piloto = (0, 14, -26); assento_1 = (0, 14, -6).
     * Pirata: assento_piloto (0, 32, -78), assento_1 (-12, 32, -66), assento_2 (12, 32, -66), assento_3 (0, 32, -62).
     */
    static Vec3 posicaoAssento(Vec3 posNave, float yawGraus, double px, double py, double pz) {
        double yaw = Math.toRadians(yawGraus);
        Vec3 frente = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 esquerda = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        return posNave.add(frente.scale(-pz / 16.0)).add(esquerda.scale(px / 16.0)).add(0, py / 16.0, 0);
    }
}

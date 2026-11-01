package seu.pacote.baterias;

import net.minecraft.util.StringRepresentable;
import org.joml.Vector3f;

/** As 7 Tropas que têm Bateria Central (Morte e Vida não usam bateria). */
public enum Tropa implements StringRepresentable {
    VERDE("verde", 0x4CFF8A), AMARELO("amarelo", 0xFFE040), VERMELHO("vermelho", 0xFF3A20), LARANJA("laranja", 0xFF9A30),
    AZUL("azul", 0x5ABAFF), INDIGO("indigo", 0x8A70FF), VIOLETA("violeta", 0xFF6AD0);

    public final String nome; public final int cor;
    Tropa(String nome, int cor) { this.nome = nome; this.cor = cor; }
    @Override public String getSerializedName() { return nome; }
    public Vector3f corVetor() { return new Vector3f(((cor >> 16) & 255) / 255f, ((cor >> 8) & 255) / 255f, (cor & 255) / 255f); }
}

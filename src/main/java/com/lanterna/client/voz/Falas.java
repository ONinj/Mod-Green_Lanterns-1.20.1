package com.lanterna.client.voz;

import com.lanterna.LanternaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * As falas dos personagens (cutscenes, conversas, o anel).
 *
 * Cada fala é identificada pela chave do texto dela (a mesma da legenda). Se existir o áudio gravado daquela fala no
 * idioma do jogo ("assets/lanterna/sounds/voz/pt_br/&lt;chave&gt;.ogg", registrado pelo ferramentas/vozes.py), ele toca.
 * Se não, toca o "falatório": sílabas curtinhas no ritmo do texto, com o tom de quem fala (ver ferramentas/sons_fala.py).
 *
 * Perfis: homem, mulher, pessoa (tom pelo número), lanterna, anel, necron e narrador (o narrador só fala com áudio
 * gravado; sem ele fica em silêncio).
 */
@Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Falas {
    private record Perfil(String som, float tom, float variacao, int ritmo) {}

    private static final Map<String, Perfil> PERFIS = Map.of(
            "homem", new Perfil("fala.voz", 0.85f, 0.08f, 3),
            "mulher", new Perfil("fala.voz", 1.3f, 0.1f, 3),
            "lanterna", new Perfil("fala.voz", 1.0f, 0.08f, 3),
            "anel", new Perfil("fala.anel", 1.0f, 0.05f, 3),
            "necron", new Perfil("fala.morte", 0.85f, 0.05f, 5));

    private static final class Fala {
        final String perfil;
        int silabas, espera;
        final float tomBase;
        Fala(String perfil, int silabas, float tomBase) { this.perfil = perfil; this.silabas = silabas; this.tomBase = tomBase; }
    }

    private static final Deque<Object[]> FILA = new ArrayDeque<>();
    private static Fala atual;
    private static SimpleSoundInstance gravada;
    private static final RandomSource RND = RandomSource.create();

    private static String idioma() {
        String c = Minecraft.getInstance().getLanguageManager().getSelected();
        return c != null && c.startsWith("pt") ? "pt_br" : "en_us";
    }

    /** O som gravado desta fala, se existir. */
    private static SoundEvent gravacao(String chave) {
        ResourceLocation id = LanternaMod.id("voz." + idioma() + "." + chave);
        return Minecraft.getInstance().getSoundManager().getSoundEvent(id) == null ? null : SoundEvent.createVariableRangeEvent(id);
    }

    /** Fala agora (interrompe a fala anterior). tom: 0 = o do perfil; para "pessoa", de 0.8 a 1.4. */
    public static void falar(String chave, String perfil, float tom) {
        FILA.clear();
        parar();
        comecar(chave, perfil, tom);
    }

    public static void falar(String chave, String perfil) { falar(chave, perfil, 0); }

    /** Várias falas em seguida (as conversas com os NPCs). */
    public static void falarFila(List<String> chaves, String perfil, float tom) {
        FILA.clear();
        parar();
        for (String c : chaves) FILA.add(new Object[]{c, perfil, tom});
        proxima();
    }

    public static void parar() {
        atual = null;
        if (gravada != null) Minecraft.getInstance().getSoundManager().stop(gravada);
        gravada = null;
    }

    private static void proxima() {
        Object[] o = FILA.poll();
        if (o != null) comecar((String) o[0], (String) o[1], (Float) o[2]);
    }

    private static void comecar(String chave, String perfil, float tom) {
        String limpa = chave.contains("|") ? chave.substring(0, chave.indexOf('|')) : chave;
        SoundEvent g = gravacao(limpa);
        var mc = Minecraft.getInstance();
        if (g != null) {
            gravada = SimpleSoundInstance.forUI(g, 1f, 1f);
            mc.getSoundManager().play(gravada);
            return;
        }
        if (perfil.equals("narrador")) { proxima(); return; }
        String texto = I18n.exists(limpa) ? I18n.get(limpa) : limpa;
        int silabas = Math.max(3, Math.min(48, texto.replaceAll("[^\\p{L}]", "").length() / 3));
        float base = tom > 0 ? tom : PERFIS.getOrDefault(perfil, PERFIS.get("homem")).tom;
        atual = new Fala(perfil.equals("pessoa") ? "homem" : perfil, silabas, base);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { FILA.clear(); atual = null; return; }
        if (gravada != null) {
            if (!mc.getSoundManager().isActive(gravada)) { gravada = null; proxima(); }
            return;
        }
        if (atual == null) return;
        if (--atual.espera > 0) return;
        Perfil p = PERFIS.getOrDefault(atual.perfil, PERFIS.get("homem"));
        SoundEvent som = SoundEvent.createVariableRangeEvent(LanternaMod.id(p.som));
        float tom = atual.tomBase * (1 + (RND.nextFloat() * 2 - 1) * p.variacao);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(som, tom, 0.55f));
        atual.silabas--;
        // pausas de vez em quando, como entre palavras
        atual.espera = p.ritmo + (RND.nextInt(5) == 0 ? 3 : 0);
        if (atual.silabas <= 0) { atual = null; proxima(); }
    }

    /** Tom de uma "pessoa" pelo número (cada NPC fala sempre no mesmo tom). */
    public static float tomDe(int semente) {
        return 0.8f + (Math.floorMod(semente * 2654435761L, 1000) / 1000f) * 0.6f;
    }

    // ===================== falas que chegam pelo chat =====================

    /** Quem fala em cada mensagem de chat da saga (o resto do chat não fala). */
    private static final Map<String, String> CHAT = Map.ofEntries(
            Map.entry("necron.lanterna.rastro_", "anel"),
            Map.entry("necron.lanterna.defesa_fala", "lanterna"),
            Map.entry("necron.lanterna.final_fala", "lanterna"),
            Map.entry("necron.lanterna.vitoria_fala", "lanterna"),
            Map.entry("necron.lanterna.mestre_", "lanterna"),
            Map.entry("necron.lanterna.ancora_fala", "lanterna"),
            Map.entry("necron.lanterna.socorro_", "lanterna"),
            Map.entry("necron.lanterna.preso_fala", "lanterna"),
            Map.entry("necron.lanterna.ruina_chamado", "lanterna"),
            Map.entry("necron.lanterna.caido_fala", "lanterna"),
            Map.entry("necron.lanterna.carcereiro_fala", "necron"),
            Map.entry("necron.lanterna.nucleo_guardado", "lanterna"),
            Map.entry("necron.lanterna.fenda_terra_aviso", "anel"),
            Map.entry("necron.lanterna.pulso_1", "anel"),
            Map.entry("necron.lanterna.pulso_2", "anel"),
            Map.entry("necron.lanterna.pulso_3", "anel"),
            Map.entry("necron.lanterna.santuario_achou", "anel"),
            Map.entry("necron.lanterna.cerco_aurora_comeca", "homem"),
            Map.entry("necron.lanterna.cerco_aurora_fim", "homem"),
            Map.entry("necron.lanterna.cerco_aurora_arauto", "necron"),
            Map.entry("necron.lanterna.cerco_embriao_arauto", "necron"),
            Map.entry("necron.lanterna.cerco_embriao_fim", "anel"));

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent e) {
        if (!(e.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc)) return;
        String k = tc.getKey();
        for (var en : CHAT.entrySet()) if (k.startsWith(en.getKey())) { falar(k, en.getValue()); return; }
    }

    private Falas() {}
}

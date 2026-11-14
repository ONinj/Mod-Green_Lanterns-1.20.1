package com.lanterna.client.ia;

import com.lanterna.LanternaMod;
import com.lanterna.ia.ConfigCliente;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.sound.sampled.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Qual microfone o anel ouve, e em que formato.
 *
 * O Java pega o "dispositivo de gravação padrão" do Windows, que nem sempre é o microfone que a pessoa usa (o Simple
 * Voice Chat escolhe o dele à parte). Aí o anel gravava silêncio e dizia "não ouvi nada". Agora:
 *  - "auto": usa o mesmo microfone escolhido no Simple Voice Chat (lê config/voicechat/voicechat-client.properties);
 *    se não houver, o padrão do sistema;
 *  - ou o nome (parte dele) escolhido em /lanternmic;
 *  - se o microfone não grava em 16 kHz mono, grava no formato dele (44,1/48 kHz, estéreo) e converte.
 */
public final class Microfone {

    /** Uma linha aberta e pronta, e como converter o que ela grava para 16 kHz mono. */
    public record Linha(TargetDataLine linha, AudioFormat formato, String nome) {
        /** Lê e devolve amostras em 16 kHz mono, 16 bits (little endian), já convertidas. */
        public int ler(byte[] saida, byte[] bruto) {
            int n = linha.read(bruto, 0, bruto.length - bruto.length % formato.getFrameSize());
            if (n <= 0) return 0;
            if (formato.getSampleRate() == 16000f && formato.getChannels() == 1) {
                System.arraycopy(bruto, 0, saida, 0, n);
                return n;
            }
            int canais = formato.getChannels();
            boolean grande = formato.isBigEndian();
            int quadros = n / formato.getFrameSize();
            double passo = formato.getSampleRate() / 16000.0;
            int o = 0;
            for (double f = 0; f < quadros && o + 1 < saida.length; f += passo) {
                int q = (int) f;
                int soma = 0;
                for (int c = 0; c < canais; c++) {
                    int i = q * formato.getFrameSize() + c * 2;
                    int a = grande ? (short) ((bruto[i] << 8) | (bruto[i + 1] & 0xFF)) : (short) ((bruto[i + 1] << 8) | (bruto[i] & 0xFF));
                    soma += a;
                }
                int v = soma / canais;
                saida[o++] = (byte) v;
                saida[o++] = (byte) (v >> 8);
            }
            return o;
        }
    }

    private static final AudioFormat[] FORMATOS = {
            new AudioFormat(16000f, 16, 1, true, false),
            new AudioFormat(48000f, 16, 1, true, false), new AudioFormat(44100f, 16, 1, true, false),
            new AudioFormat(48000f, 16, 2, true, false), new AudioFormat(44100f, 16, 2, true, false),
            new AudioFormat(32000f, 16, 1, true, false), new AudioFormat(22050f, 16, 1, true, false)};

    /** Microfones que gravam (nome de cada um). */
    public static List<String> lista() {
        List<String> l = new ArrayList<>();
        for (Mixer.Info i : AudioSystem.getMixerInfo()) {
            Mixer m = AudioSystem.getMixer(i);
            if (m.isLineSupported(new Line.Info(TargetDataLine.class)) && !i.getName().toLowerCase(Locale.ROOT).startsWith("port ")) l.add(i.getName());
        }
        return l;
    }

    /** O nome pedido: o da config ou, em "auto", o do Simple Voice Chat (ou vazio = padrão do sistema). */
    public static String pedido() {
        String c = ConfigCliente.MICROFONE.get();
        if (c != null && !c.isBlank() && !c.equalsIgnoreCase("auto")) return c.trim();
        try {
            Path f = FMLPaths.CONFIGDIR.get().resolve("voicechat").resolve("voicechat-client.properties");
            if (Files.exists(f)) for (String linha : Files.readAllLines(f)) {
                if (!linha.startsWith("microphone=")) continue;
                String v = linha.substring("microphone=".length()).trim().replace("\\:", ":").replace("\\=", "=");
                if (v.startsWith("OpenAL Soft on ")) v = v.substring("OpenAL Soft on ".length());
                return v;
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** Abre o microfone escolhido (ou o padrão) no primeiro formato que ele aceitar. */
    public static Linha abrir() throws LineUnavailableException {
        String quer = pedido().toLowerCase(Locale.ROOT);
        List<Mixer.Info> ordem = new ArrayList<>();
        if (!quer.isEmpty()) for (Mixer.Info i : AudioSystem.getMixerInfo()) {
            String n = i.getName().toLowerCase(Locale.ROOT);
            // o Windows às vezes corta o nome no Java (31 letras): vale o começo igual também
            int k = Math.min(Math.min(n.length(), quer.length()), 24);
            boolean comeco = k >= 10 && n.substring(0, k).equals(quer.substring(0, k));
            if ((n.contains(quer) || quer.contains(n) || comeco) && !n.startsWith("port ")) ordem.add(i);
        }
        ordem.add(null); // o padrão do sistema
        LineUnavailableException ultimo = null;
        for (Mixer.Info i : ordem) for (AudioFormat f : FORMATOS) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, f);
            try {
                TargetDataLine t = (TargetDataLine) (i == null ? AudioSystem.getLine(info) : AudioSystem.getMixer(i).getLine(info));
                t.open(f, (int) (f.getSampleRate() * f.getFrameSize() / 2));
                t.start();
                String nome = i == null ? "padrão do sistema" : i.getName();
                LanternaMod.LOGGER.info("Lanterna: o anel ouve o microfone '{}' em {}", nome, f);
                return new Linha(t, f, nome);
            } catch (LineUnavailableException | IllegalArgumentException e) {
                if (e instanceof LineUnavailableException l) ultimo = l;
            }
        }
        throw ultimo != null ? ultimo : new LineUnavailableException("nenhum microfone");
    }

    private Microfone() {}
}

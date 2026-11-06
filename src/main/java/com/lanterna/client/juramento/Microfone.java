package com.lanterna.client.juramento;

import com.lanterna.LanternaMod;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Ouve o microfone só para saber SE o jogador está falando (não reconhece palavras).
 *
 * Duas fontes:
 *  - Simple Voice Chat, quando está instalado e conectado: o áudio que ele capta para transmitir chega aqui
 *    (com "apertar para falar", só enquanto a tecla dele está segurada);
 *  - senão, o próprio mod abre o microfone padrão do sistema enquanto a tela do juramento está aberta.
 */
public final class Microfone {
    /** Nível atual (0..1, já suavizado) e momento da última amostra com voz. */
    private static volatile float nivel;
    private static volatile long ultimaVoz;
    private static volatile float pisoRuido = 250;
    /** O Simple Voice Chat está conectado (então ele é a fonte). */
    public static volatile boolean svcConectado;
    private static volatile Thread linha;
    private static volatile boolean ligado;
    private static volatile boolean falhou;

    /** Amostra de áudio (PCM 16 bits mono), de qualquer fonte. */
    public static void amostra(short[] pcm, boolean doSvc) {
        if (pcm == null || pcm.length == 0) return;
        double soma = 0;
        for (short s : pcm) soma += (double) s * s;
        float rms = (float) Math.sqrt(soma / pcm.length);
        // o piso de ruído desce rápido e sobe devagar (acompanha o ambiente)
        pisoRuido = rms < pisoRuido ? pisoRuido * 0.9f + rms * 0.1f : pisoRuido * 0.998f + rms * 0.002f;
        float limiar = doSvc ? 180 : Math.max(500, pisoRuido * 2.8f);
        float n = Math.min(1f, rms / 6000f);
        nivel = nivel * 0.6f + n * 0.4f;
        if (rms > limiar) ultimaVoz = System.currentTimeMillis();
    }

    public static boolean falando() {
        return System.currentTimeMillis() - ultimaVoz < 180;
    }

    public static float nivel() {
        if (System.currentTimeMillis() - ultimaVoz > 400) nivel *= 0.9f;
        return nivel;
    }

    /** Há algum microfone ouvindo agora? */
    public static boolean disponivel() {
        return svcConectado || (ligado && !falhou);
    }

    /** Abre o microfone do sistema (se o Voice Chat não está cuidando dele). */
    public static synchronized void ligar() {
        falhou = false;
        if (svcConectado || ligado) return;
        ligado = true;
        Thread t = new Thread(() -> {
            AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
            try (TargetDataLine line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt))) {
                line.open(fmt, 3200);
                line.start();
                byte[] buf = new byte[640]; // 20 ms
                short[] pcm = new short[320];
                while (ligado) {
                    int n = line.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    for (int i = 0; i < n / 2; i++) pcm[i] = (short) ((buf[2 * i] & 0xFF) | (buf[2 * i + 1] << 8));
                    amostra(java.util.Arrays.copyOf(pcm, n / 2), false);
                }
                line.stop();
            } catch (Exception | LinkageError e) {
                falhou = true;
                LanternaMod.LOGGER.info("Lanterna: microfone do sistema indisponível ({})", e.toString());
            } finally {
                ligado = false;
            }
        }, "lanterna-microfone");
        t.setDaemon(true);
        linha = t;
        t.start();
    }

    public static synchronized void desligar() {
        ligado = false;
        linha = null;
    }

    public static boolean falhou() {
        return falhou && !svcConectado;
    }

    /** Teste automático: simula voz. */
    public static void simular() {
        ultimaVoz = System.currentTimeMillis();
        nivel = 0.6f;
    }

    private Microfone() {}
}

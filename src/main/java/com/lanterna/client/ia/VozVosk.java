package com.lanterna.client.ia;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reconhecimento de fala com o Vosk, embutido no mod (funciona sem internet depois de baixar o modelo, em Windows,
 * Mac e Linux). Segure a tecla "Falar com o anel" enquanto fala e solte no fim: o anel entende e responde.
 *
 * Na primeira vez, o mod baixa o modelo pequeno do idioma do jogo (português ~31 MB, inglês ~40 MB) para
 * config/lanterna/vosk e guarda para as próximas vezes.
 */
public final class VozVosk {
    private static final String URL_PT = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip";
    private static final String URL_EN = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";

    private static volatile org.vosk.Model modelo;
    private static volatile String idiomaModelo;
    private static final AtomicBoolean BAIXANDO = new AtomicBoolean();
    private static final AtomicBoolean GRAVANDO = new AtomicBoolean();
    private static volatile boolean soltou;
    /** O Vosk não carregou neste computador (biblioteca nativa): usa o reconhecedor do Windows. */
    public static volatile boolean quebrado;
    /** Teste automático: o último texto ouvido. */
    public static volatile String ultimoTexto = "";

    public static boolean pronto(String idioma) {
        return modelo != null && idioma.equals(idiomaModelo);
    }

    public static Path pasta() {
        return FMLPaths.CONFIGDIR.get().resolve("lanterna").resolve("vosk");
    }

    private static Path pastaModelo(String idioma) {
        return pasta().resolve(idioma.startsWith("pt") ? "pt" : "en");
    }

    /** Começa a ouvir (tecla apertada). Devolve false se ainda não dá (baixando o modelo, por exemplo). */
    public static boolean comecar(String idioma) {
        if (quebrado) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!pronto(idioma)) {
            prepararModelo(idioma);
            return true;
        }
        if (!GRAVANDO.compareAndSet(false, true)) return true;
        soltou = false;
        mc.player.displayClientMessage(Component.translatable("anel.ia.voz.ouvindo_segure").withStyle(s -> s.withColor(ClientRingData.color)), true);
        Thread t = new Thread(VozVosk::gravar, "lanterna-vosk");
        t.setDaemon(true);
        t.start();
        return true;
    }

    /** Tecla solta: termina a frase. */
    public static void soltar() {
        soltou = true;
    }

    public static boolean gravando() {
        return GRAVANDO.get();
    }

    private static void gravar() {
        Minecraft mc = Minecraft.getInstance();
        String texto = "";
        AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
        try (org.vosk.Recognizer rec = new org.vosk.Recognizer(modelo, 16000f);
             TargetDataLine line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt))) {
            rec.setMaxAlternatives(6);
            line.open(fmt, 8000);
            line.start();
            byte[] buf = new byte[3200];
            long inicio = System.currentTimeMillis();
            // grava enquanto a tecla está apertada (no máximo 15 s), e mais um pouquinho depois de soltar
            long fimDepoisDeSoltar = -1;
            List<List<String>> trechos = new ArrayList<>();
            while (System.currentTimeMillis() - inicio < 15000) {
                int n = line.read(buf, 0, buf.length);
                // o Vosk fecha um trecho a cada pausa (quem fala devagar faz várias): guarda cada um, senão só a
                // última parte da frase chegava ao anel
                if (n > 0 && rec.acceptWaveForm(buf, n)) trecho(trechos, rec.getResult());
                if (soltou && fimDepoisDeSoltar < 0) fimDepoisDeSoltar = System.currentTimeMillis() + 450;
                if (fimDepoisDeSoltar > 0 && System.currentTimeMillis() > fimDepoisDeSoltar) break;
            }
            line.stop();
            trecho(trechos, rec.getFinalResult());
            texto = montar(trechos);
        } catch (Throwable e) {
            LanternaMod.LOGGER.warn("Lanterna: Vosk não conseguiu ouvir ({})", e.toString());
            if (e instanceof LinkageError) quebrado = true;
            String msg = e.toString();
            mc.execute(() -> { if (mc.player != null) mc.player.displayClientMessage(Component.translatable("anel.ia.voz.sem_microfone", msg), false); });
        } finally {
            GRAVANDO.set(false);
        }
        String fim = texto;
        ultimoTexto = fim;
        mc.execute(() -> {
            if (mc.player == null) return;
            if (fim.isBlank()) mc.player.displayClientMessage(Component.translatable("anel.ia.voz.nao_ouvi"), true);
            else AnelIA.ouvir(fim, true);
        });
    }

    /**
     * O Vosk devolve as frases mais prováveis. Fica com a primeira que vira um pedido que o anel entende (depois de
     * consertar os nomes do mod); se nenhuma virar, a mais provável.
     */
    static String escolher(String json) {
        List<List<String>> t = new ArrayList<>();
        trecho(t, json);
        return montar(t);
    }

    /** As frases prováveis de um trecho (com os acentos consertados). */
    static void trecho(List<List<String>> trechos, String json) {
        List<String> alts = new ArrayList<>();
        try {
            var o = com.google.gson.JsonParser.parseString(acentos(json)).getAsJsonObject();
            if (o.has("alternatives")) for (var e : o.getAsJsonArray("alternatives")) alts.add(e.getAsJsonObject().get("text").getAsString().trim());
            else if (o.has("text")) alts.add(o.get("text").getAsString().trim());
        } catch (Exception e) {
            alts.add(textoDe(acentos(json)));
        }
        alts.removeIf(String::isBlank);
        if (!alts.isEmpty()) trechos.add(alts);
    }

    /**
     * Junta os trechos. Primeiro a junção das frases mais prováveis; se ela não vira um pedido que o anel entende,
     * tenta trocar um trecho de cada vez pelas outras frases prováveis dele.
     */
    static String montar(List<List<String>> trechos) {
        if (trechos.isEmpty()) return "";
        List<String> base = new ArrayList<>();
        for (List<String> t : trechos) base.add(t.get(0));
        String primeira = String.join(" ", base);
        if (entende(primeira)) return primeira;
        for (int i = trechos.size() - 1; i >= 0; i--) {
            for (int j = 1; j < trechos.get(i).size(); j++) {
                List<String> c = new ArrayList<>(base);
                c.set(i, trechos.get(i).get(j));
                String f = String.join(" ", c);
                if (entende(f)) {
                    LanternaMod.LOGGER.debug("Lanterna: voz '{}' (em vez de '{}')", f, primeira);
                    return f;
                }
            }
        }
        return primeira;
    }

    private static boolean entende(String frase) {
        String c = com.lanterna.ia.Vocabulario.consertar(com.lanterna.ia.Texto.limpo(frase));
        return com.lanterna.ia.Intencao.ler(c).intencao() != com.lanterna.ia.Intencao.NADA;
    }

    /**
     * O Vosk devolve UTF-8, mas no Windows a ponte com a biblioteca nativa lê o texto como se fosse a codificação do
     * sistema (cp1252): "faço" chegava como "faÃ§o" e "nó" como "nÃ³", e o anel não entendia mais nada com acento.
     * Desfaz: volta aos bytes originais e lê como UTF-8.
     */
    static String acentos(String s) {
        if (s == null || (s.indexOf('Ã') < 0 && s.indexOf('Â') < 0)) return s;
        try {
            java.nio.charset.Charset cs;
            try { cs = java.nio.charset.Charset.forName(com.sun.jna.Native.getDefaultStringEncoding()); }
            catch (Throwable t) { cs = java.nio.charset.Charset.forName("windows-1252"); }
            if (cs.equals(java.nio.charset.StandardCharsets.UTF_8)) cs = java.nio.charset.Charset.forName("windows-1252");
            String r = new String(s.getBytes(cs), java.nio.charset.StandardCharsets.UTF_8);
            return r.indexOf('\uFFFD') >= 0 ? s : r;
        } catch (Exception e) {
            return s;
        }
    }

    /** {"text" : "me leve para zamaron"} -> me leve para zamaron */
    static String textoDe(String json) {
        if (json == null) return "";
        int i = json.indexOf("\"text\"");
        if (i < 0) return "";
        int a = json.indexOf('"', json.indexOf(':', i) + 1);
        int b = json.indexOf('"', a + 1);
        return a < 0 || b < 0 ? "" : json.substring(a + 1, b).trim();
    }

    /** Carrega (ou baixa e carrega) o modelo do idioma, numa thread separada. */
    public static void prepararModelo(String idioma) {
        if (!BAIXANDO.compareAndSet(false, true)) return;
        Minecraft mc = Minecraft.getInstance();
        Thread t = new Thread(() -> {
            try {
                Path dir = pastaModelo(idioma);
                if (!temModelo(dir)) {
                    aviso(mc, Component.translatable("anel.ia.voz.baixando", idioma.startsWith("pt") ? "31" : "40"));
                    baixar(idioma.startsWith("pt") ? URL_PT : URL_EN, dir);
                }
                org.vosk.LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS);
                org.vosk.Model m = new org.vosk.Model(raizModelo(dir).toString());
                if (modelo != null) modelo.close();
                modelo = m;
                idiomaModelo = idioma;
                aviso(mc, Component.translatable("anel.ia.voz.pronto"));
            } catch (Throwable e) {
                LanternaMod.LOGGER.warn("Lanterna: o Vosk não ficou pronto ({})", e.toString(), e);
                if (e instanceof LinkageError) quebrado = true;
                String msg = e.toString();
                aviso(mc, Component.translatable("anel.ia.voz.vosk_falhou", msg));
            } finally {
                BAIXANDO.set(false);
            }
        }, "lanterna-vosk-modelo");
        t.setDaemon(true);
        t.start();
    }

    private static boolean ehModelo(Path p) {
        return Files.exists(p.resolve("am").resolve("final.mdl")) || Files.exists(p.resolve("final.mdl"));
    }

    private static boolean temModelo(Path dir) {
        try {
            return Files.isDirectory(dir) && ehModelo(raizModelo(dir));
        } catch (Exception e) {
            return false;
        }
    }

    /** O zip do Vosk traz uma pasta com o nome do modelo dentro: acha onde está o "am". */
    private static Path raizModelo(Path dir) throws java.io.IOException {
        if (ehModelo(dir)) return dir;
        try (var s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) if (Files.isDirectory(p) && ehModelo(p)) return p;
        }
        return dir;
    }

    private static void baixar(String endereco, Path destino) throws Exception {
        Files.createDirectories(destino);
        File zip = destino.resolve("modelo.zip").toFile();
        HttpURLConnection c = (HttpURLConnection) new URL(endereco).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = new BufferedInputStream(c.getInputStream()); FileOutputStream out = new FileOutputStream(zip)) {
            in.transferTo(out);
        }
        Path raiz = destino.toAbsolutePath().normalize();
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip.toPath())))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                Path alvo = raiz.resolve(e.getName()).normalize();
                if (!alvo.startsWith(raiz)) continue; // nada de sair da pasta
                if (e.isDirectory()) Files.createDirectories(alvo);
                else {
                    Files.createDirectories(alvo.getParent());
                    Files.copy(z, alvo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.deleteIfExists(zip.toPath());
    }

    private static void aviso(Minecraft mc, Component c) {
        mc.execute(() -> { if (mc.player != null) mc.player.displayClientMessage(c.copy().withStyle(s -> s.withColor(ClientRingData.color)), false); });
    }

    /** Teste: reconhece um arquivo .wav (16 kHz mono) com o modelo já carregado. */
    public static String reconhecerArquivo(Path wav) throws Exception {
        try (org.vosk.Recognizer rec = new org.vosk.Recognizer(modelo, 16000f);
             var ais = AudioSystem.getAudioInputStream(wav.toFile())) {
            byte[] buf = new byte[4096];
            int n;
            rec.setMaxAlternatives(6);
            List<List<String>> trechos = new ArrayList<>();
            while ((n = ais.read(buf)) >= 0) if (rec.acceptWaveForm(buf, n)) trecho(trechos, rec.getResult());
            trecho(trechos, rec.getFinalResult());
            LanternaMod.LOGGER.info("Lanterna: trechos ouvidos: {}", trechos);
            return montar(trechos);
        }
    }

    private VozVosk() {}
}

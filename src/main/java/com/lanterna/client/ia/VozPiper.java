package com.lanterna.client.ia;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.ia.ConfigCliente;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Voz neural do anel (Piper, de código aberto, roda no próprio computador e sem internet depois de baixar).
 * Soa como uma pessoa falando, bem diferente das vozes antigas do sistema.
 *
 * Na primeira vez baixa o programa do Piper do sistema (Windows, Mac ou Linux, ~20 MB) e a voz do idioma (~63 MB) para
 * config/lanterna/piper. Enquanto baixa (ou se não funcionar neste computador), o anel usa a voz do sistema.
 * Cada Tropa ganha um tom: o áudio é tocado um pouco mais grave ou mais agudo, com a velocidade compensada.
 */
public final class VozPiper {
    private static final String VERSAO = "2023.11.14-2";
    private static final String BASE_PROGRAMA = "https://github.com/rhasspy/piper/releases/download/" + VERSAO + "/";
    private static final String BASE_VOZES = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/";

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final String ARCH = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win"), MAC = OS.contains("mac");
    private static final boolean ARM = ARCH.contains("aarch64") || ARCH.contains("arm64");

    private static final AtomicBoolean BAIXANDO = new AtomicBoolean();
    /** Não funcionou neste computador nesta sessão: fica com a voz do sistema. */
    public static volatile boolean quebrado;
    private static volatile boolean avisou;

    public static Path pasta() {
        return FMLPaths.CONFIGDIR.get().resolve("lanterna").resolve("piper");
    }

    private static String arquivoPrograma() {
        if (WINDOWS) return "piper_windows_amd64.zip";
        if (MAC) return ARM ? "piper_macos_aarch64.tar.gz" : "piper_macos_x64.tar.gz";
        return ARM ? "piper_linux_aarch64.tar.gz" : "piper_linux_x86_64.tar.gz";
    }

    private static Path pastaPrograma() {
        return pasta().resolve("programa").resolve(arquivoPrograma().replace(".tar.gz", "").replace(".zip", ""));
    }

    private static Path executavel() {
        return pastaPrograma().resolve("piper").resolve(WINDOWS ? "piper.exe" : "piper");
    }

    static String nomeVoz(String idioma) {
        String v = idioma.startsWith("pt") ? ConfigCliente.VOZ_PT.get() : ConfigCliente.VOZ_EN.get();
        // só nomes no formato do Piper (ex.: pt_BR-faber-medium): nada de caminhos
        if (v == null || !v.matches("[a-z]{2}_[A-Z]{2}-[A-Za-z0-9_]+-(x_low|low|medium|high)")) v = idioma.startsWith("pt") ? "pt_BR-faber-medium" : "en_US-ryan-medium";
        return v;
    }

    private static Path modelo(String voz) {
        return pasta().resolve("vozes").resolve(voz + ".onnx");
    }

    public static boolean pronto(String idioma) {
        String v = nomeVoz(idioma);
        return Files.isRegularFile(executavel()) && Files.isRegularFile(modelo(v)) && Files.isRegularFile(Paths.get(modelo(v) + ".json"));
    }

    /**
     * Fala com a voz neural. Devolve false se ainda não dá (baixando, desligada ou quebrada): aí quem chamou usa a voz
     * do sistema. Roda na thread da fala (bloqueia até terminar).
     */
    public static boolean falar(String texto, String idioma, String tropa, int velocidade) {
        if (quebrado || !ConfigCliente.VOZ_NEURAL.get()) return false;
        if (!pronto(idioma)) {
            preparar(idioma);
            return false;
        }
        String voz = nomeVoz(idioma);
        Process pr = null;
        try {
            int taxa = taxa(Paths.get(modelo(voz) + ".json"));
            float tom = tom(tropa);
            // velocidade -10..10 da config; o tom muda a velocidade ao tocar, então a fala é gerada compensando
            double comprimento = (1.0 - velocidade * 0.04) * tom;
            Path exe = executavel();
            ProcessBuilder pb = new ProcessBuilder(exe.toString(), "--model", modelo(voz).toString(), "--output-raw",
                    "--length_scale", String.format(Locale.ROOT, "%.3f", comprimento), "--sentence_silence", "0.15");
            pb.directory(exe.getParent().toFile());
            String libs = exe.getParent().toString();
            pb.environment().put("LD_LIBRARY_PATH", libs);
            pb.environment().put("DYLD_LIBRARY_PATH", libs);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pr = pb.start();
            try (OutputStream in = pr.getOutputStream()) {
                in.write((texto.replace('\n', ' ') + "\n").getBytes(StandardCharsets.UTF_8));
            }
            byte[] cru;
            try (InputStream som = pr.getInputStream()) {
                cru = som.readAllBytes();
            }
            boolean tocou = cru.length >= 2;
            if (tocou) tocar(mudarTom(cru, tom), taxa);
            if (!pr.waitFor(20, TimeUnit.SECONDS)) pr.destroyForcibly();
            if (!tocou) {
                LanternaMod.LOGGER.warn("Lanterna: a voz neural não gerou som (código {}); usando a voz do sistema", pr.exitValue());
                quebrado = true;
                return false;
            }
            return true;
        } catch (Throwable e) {
            LanternaMod.LOGGER.warn("Lanterna: a voz neural não funcionou ({}); usando a voz do sistema", e.toString());
            quebrado = true;
            if (pr != null) pr.destroyForcibly();
            return false;
        }
    }

    /**
     * Toca mais grave (tom < 1) ou mais agudo, como uma fita mais lenta ou mais rápida: a fala foi gerada mais rápida ou
     * mais lenta na mesma medida, então a duração fica igual. Também aplica o volume do jogo.
     */
    static short[] mudarTom(byte[] cru, float tom) {
        int n = cru.length / 2;
        short[] in = new short[n];
        for (int i = 0; i < n; i++) in[i] = (short) ((cru[2 * i] & 0xFF) | (cru[2 * i + 1] << 8));
        int m = Math.max(1, (int) (n / tom));
        short[] out = new short[m];
        float vol = volume();
        for (int i = 0; i < m; i++) {
            double p = i * (double) tom;
            int a = (int) p;
            double f = p - a;
            double v = a + 1 < n ? in[a] * (1 - f) + in[a + 1] * f : in[Math.min(a, n - 1)];
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(v * vol)));
        }
        return out;
    }

    private static void tocar(short[] s, int taxa) throws Exception {
        AudioFormat fmt = new AudioFormat(taxa, 16, 1, true, false);
        byte[] b = new byte[s.length * 2];
        for (int i = 0; i < s.length; i++) { b[2 * i] = (byte) s[i]; b[2 * i + 1] = (byte) (s[i] >> 8); }
        try (SourceDataLine linha = AudioSystem.getSourceDataLine(fmt)) {
            linha.open(fmt, taxa);
            linha.start();
            linha.write(b, 0, b.length);
            linha.drain();
        }
    }

    /** Teste: gera a fala num .wav (sem tocar). */
    public static boolean gerarArquivo(String texto, String idioma, Path wav) throws Exception {
        if (!pronto(idioma)) return false;
        String voz = nomeVoz(idioma);
        Path exe = executavel();
        ProcessBuilder pb = new ProcessBuilder(exe.toString(), "--model", modelo(voz).toString(), "--output_file", wav.toAbsolutePath().toString());
        pb.directory(exe.getParent().toFile());
        pb.environment().put("LD_LIBRARY_PATH", exe.getParent().toString());
        pb.redirectErrorStream(true);
        Process pr = pb.start();
        try (OutputStream in = pr.getOutputStream()) { in.write((texto + "\n").getBytes(StandardCharsets.UTF_8)); }
        String saida = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        pr.waitFor(60, TimeUnit.SECONDS);
        if (!saida.isBlank()) LanternaMod.LOGGER.info("Lanterna: piper: {}", saida.trim());
        return Files.size(wav) > 1000;
    }

    /** Volume do jogo (geral × vozes). */
    private static float volume() {
        try {
            var o = Minecraft.getInstance().options;
            return o.getSoundSourceVolume(SoundSource.MASTER) * o.getSoundSourceVolume(SoundSource.VOICE);
        } catch (Throwable t) {
            return 1f;
        }
    }

    /** Tom de cada Tropa: abaixo de 1 = mais grave. Pequeno, para continuar soando como gente. */
    static float tom(String tropa) {
        return switch (tropa) {
            case "yellow" -> 0.93f;
            case "red" -> 0.89f;
            case "orange" -> 0.95f;
            case "black" -> 0.86f;
            case "blue" -> 1.04f;
            case "violet" -> 1.06f;
            case "indigo" -> 0.98f;
            case "white" -> 1.07f;
            default -> 0.97f;
        };
    }

    private static int taxa(Path json) {
        try {
            String s = Files.readString(json);
            int i = s.indexOf("\"sample_rate\"");
            if (i >= 0) {
                int a = s.indexOf(':', i) + 1, b = a;
                while (b < s.length() && (Character.isWhitespace(s.charAt(b)) || Character.isDigit(s.charAt(b)))) b++;
                return Integer.parseInt(s.substring(a, b).trim());
            }
        } catch (Exception ignored) {}
        return 22050;
    }

    // ===================== baixar =====================

    /** Baixa o que falta (programa e voz), numa thread separada. */
    public static void preparar(String idioma) {
        if (quebrado || !BAIXANDO.compareAndSet(false, true)) return;
        Minecraft mc = Minecraft.getInstance();
        Thread t = new Thread(() -> {
            try {
                if (!avisou) {
                    avisou = true;
                    aviso(mc, Component.translatable("anel.ia.voz.piper_baixando"));
                }
                if (!Files.isRegularFile(executavel())) {
                    Path dir = pastaPrograma();
                    Files.createDirectories(dir);
                    Path tmp = dir.resolve("download.part");
                    baixar(BASE_PROGRAMA + arquivoPrograma(), tmp);
                    if (WINDOWS) extrairZip(tmp, dir);
                    else extrairTarGz(tmp, dir);
                    Files.deleteIfExists(tmp);
                    executavel().toFile().setExecutable(true);
                }
                String voz = nomeVoz(idioma);
                Path m = modelo(voz);
                if (!Files.isRegularFile(m) || !Files.isRegularFile(Paths.get(m + ".json"))) {
                    Files.createDirectories(m.getParent());
                    String[] p = voz.split("-");
                    String url = BASE_VOZES + p[0].substring(0, 2) + "/" + p[0] + "/" + p[1] + "/" + p[2] + "/" + voz + ".onnx";
                    baixar(url + ".json", Paths.get(m + ".json.part"));
                    baixar(url, Paths.get(m + ".part"));
                    Files.move(Paths.get(m + ".json.part"), Paths.get(m + ".json"), StandardCopyOption.REPLACE_EXISTING);
                    Files.move(Paths.get(m + ".part"), m, StandardCopyOption.REPLACE_EXISTING);
                }
                aviso(mc, Component.translatable("anel.ia.voz.piper_pronta"));
            } catch (Throwable e) {
                LanternaMod.LOGGER.warn("Lanterna: não deu para baixar a voz neural ({}); usando a voz do sistema", e.toString());
                quebrado = true;
            } finally {
                BAIXANDO.set(false);
            }
        }, "lanterna-piper");
        t.setDaemon(true);
        t.start();
    }

    private static void baixar(String endereco, Path destino) throws IOException {
        String atual = endereco;
        for (int i = 0; i < 6; i++) { // segue os redirecionamentos (GitHub e Hugging Face mudam de domínio)
            HttpURLConnection c = (HttpURLConnection) new URL(atual).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "lanterna-mod");
            int cod = c.getResponseCode();
            if (cod >= 300 && cod < 400 && c.getHeaderField("Location") != null) {
                atual = new URL(new URL(atual), c.getHeaderField("Location")).toString();
                continue;
            }
            if (cod != 200) throw new IOException("HTTP " + cod + " em " + endereco);
            try (InputStream in = new BufferedInputStream(c.getInputStream()); OutputStream out = Files.newOutputStream(destino)) {
                in.transferTo(out);
            }
            return;
        }
        throw new IOException("redirecionamentos demais em " + endereco);
    }

    private static Path dentro(Path raiz, String nome) throws IOException {
        Path alvo = raiz.resolve(nome).normalize();
        if (!alvo.startsWith(raiz)) throw new IOException("caminho fora da pasta: " + nome);
        return alvo;
    }

    private static void extrairZip(Path zip, Path destino) throws IOException {
        Path raiz = destino.toAbsolutePath().normalize();
        try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                Path alvo = dentro(raiz, e.getName());
                if (e.isDirectory()) Files.createDirectories(alvo);
                else {
                    Files.createDirectories(alvo.getParent());
                    Files.copy(z, alvo, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** .tar.gz (Mac e Linux): arquivos, pastas e atalhos (as bibliotecas usam atalhos). */
    private static void extrairTarGz(Path tgz, Path destino) throws IOException {
        Path raiz = destino.toAbsolutePath().normalize();
        java.util.List<String[]> atalhos = new java.util.ArrayList<>();
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(tgz)))) {
            byte[] cab = new byte[512];
            String nomeLongo = null;
            while (true) {
                if (in.readNBytes(cab, 0, 512) < 512) break;
                boolean vazio = true;
                for (byte b : cab) if (b != 0) { vazio = false; break; }
                if (vazio) break;
                String nome = texto(cab, 0, 100);
                String prefixo = texto(cab, 345, 155);
                if (!prefixo.isEmpty()) nome = prefixo + "/" + nome;
                long tam = Long.parseLong(texto(cab, 124, 12).trim().isEmpty() ? "0" : texto(cab, 124, 12).trim(), 8);
                int modo = Integer.parseInt(texto(cab, 100, 8).trim().isEmpty() ? "644" : texto(cab, 100, 8).trim(), 8);
                char tipo = (char) cab[156];
                String link = texto(cab, 157, 100);
                if (nomeLongo != null) { nome = nomeLongo; nomeLongo = null; }
                long resto = (512 - tam % 512) % 512;
                if (tipo == 'L') {
                    nomeLongo = new String(in.readNBytes((int) tam), StandardCharsets.UTF_8).replace("\0", "");
                    in.skipNBytes(resto);
                    continue;
                }
                Path alvo = dentro(raiz, nome);
                if (tipo == '5') Files.createDirectories(alvo);
                else if (tipo == '2') atalhos.add(new String[]{alvo.toString(), link});
                else if (tipo == '0' || tipo == 0) {
                    Files.createDirectories(alvo.getParent());
                    try (OutputStream out = Files.newOutputStream(alvo)) {
                        long falta = tam;
                        byte[] b = new byte[8192];
                        while (falta > 0) {
                            int n = in.read(b, 0, (int) Math.min(b.length, falta));
                            if (n < 0) throw new EOFException();
                            out.write(b, 0, n);
                            falta -= n;
                        }
                    }
                    if ((modo & 0100) != 0) alvo.toFile().setExecutable(true);
                    in.skipNBytes(resto);
                } else in.skipNBytes(tam + resto);
            }
        }
        // atalhos por último (o arquivo de destino já existe): copia, que funciona em qualquer sistema
        for (int volta = 0; volta < 3; volta++) for (String[] a : atalhos) {
            Path alvo = Paths.get(a[0]);
            if (Files.exists(alvo)) continue;
            Path origem = dentro(raiz, raiz.relativize(alvo.getParent().resolve(a[1]).normalize()).toString());
            if (Files.isRegularFile(origem)) Files.copy(origem, alvo, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String texto(byte[] b, int de, int tam) {
        int fim = de;
        while (fim < de + tam && b[fim] != 0) fim++;
        return new String(b, de, fim - de, StandardCharsets.UTF_8);
    }

    private static void aviso(Minecraft mc, Component c) {
        mc.execute(() -> { if (mc.player != null) mc.player.displayClientMessage(c.copy().withStyle(s -> s.withColor(ClientRingData.color)), false); });
    }

    private VozPiper() {}
}

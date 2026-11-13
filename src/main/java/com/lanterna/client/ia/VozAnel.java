package com.lanterna.client.ia;

import com.lanterna.LanternaMod;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.KeyBindings;
import com.lanterna.client.flight.Destinations;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A voz do anel, sem nenhum serviço pago e sem biblioteca de fora: usa o que o próprio sistema já tem.
 *
 *  Ouvir (tecla "Falar com o anel", padrão Ç / ;): no Windows, o reconhecimento de fala do Windows (o mesmo do ditado).
 *  Primeiro tenta o ditado livre (precisa de "Reconhecimento de fala online" ligado em Configurações > Privacidade >
 *  Fala); sem ele, usa uma lista de frases que o anel conhece (funciona offline, com o pacote de fala do idioma). Se nada
 *  disso existir, tenta o reconhecedor antigo do Windows (inglês). No Mac e no Linux, avisa para usar o chat.
 *
 *  Falar (as respostas do anel): Windows: as vozes do Windows (ex.: "Maria" em português); Mac: "say"; Linux: espeak.
 *  Cada Tropa tem um tom de voz próprio. Liga e desliga em config/lanterna-client.toml (voz_do_anel).
 *
 * O texto nunca é montado dentro de um comando: vai por variável de ambiente para o processo (sem risco de injeção).
 */
public final class VozAnel {
    public static final KeyMapping FALAR = new KeyMapping("key.lanterna.falar_anel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, KeyBindings.CATEGORY);

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    private static final AtomicBoolean OUVINDO = new AtomicBoolean();
    private static final ExecutorService FALA = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lanterna-voz-anel");
        t.setDaemon(true);
        return t;
    });
    private static volatile int naFila;
    /** Teste automático: não abre processo nenhum. */
    public static boolean mudo;

    @Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registro {
        @SubscribeEvent
        public static void teclas(RegisterKeyMappingsEvent e) {
            e.register(FALAR);
        }
    }

    @Mod.EventBusSubscriber(modid = LanternaMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Tecla {
        private static boolean antes, comVosk, semVosk;

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            while (FALAR.consumeClick()) { /* o que vale é segurar (abaixo) */ }
            boolean baixo = FALAR.isDown() && Minecraft.getInstance().screen == null;
            if (baixo && !antes) apertou();
            if (!baixo && antes && comVosk) VozVosk.soltar();
            antes = baixo;
        }

        /** Segurou a tecla: o Vosk ouve enquanto ela estiver apertada; sem o Vosk, o reconhecedor do Windows. */
        private static void apertou() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (!ClientRingData.hasRing) {
                AnelIA.responder(Component.translatable("anel.ia.sem_anel"), false);
                return;
            }
            comVosk = false;
            if (!semVosk) {
                try {
                    comVosk = !VozVosk.quebrado && VozVosk.comecar(idioma());
                } catch (Throwable t) {
                    semVosk = true;
                    LanternaMod.LOGGER.info("Lanterna: Vosk indisponível ({}), usando o reconhecimento do sistema", t.toString());
                }
            }
            if (!comVosk) ouvir();
        }
    }

    // ===================== ouvir =====================

    public static void ouvir() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!ClientRingData.hasRing) {
            AnelIA.responder(Component.translatable("anel.ia.sem_anel"), false);
            return;
        }
        if (!WINDOWS) {
            mc.player.displayClientMessage(Component.translatable("anel.ia.voz.so_windows"), false);
            return;
        }
        if (!OUVINDO.compareAndSet(false, true)) return;
        mc.player.displayClientMessage(Component.translatable("anel.ia.voz.ouvindo").withStyle(s -> s.withColor(ClientRingData.color)), true);
        String idioma = idioma();
        String frases = String.join("|", frases());
        Thread t = new Thread(() -> {
            String texto = null, erro = null;
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-EncodedCommand", codificar(SCRIPT_OUVIR));
                pb.environment().put("LANTERNA_LANG", idioma);
                pb.environment().put("LANTERNA_FRASES", frases);
                pb.redirectErrorStream(true);
                Process pr = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                    String linha;
                    while ((linha = r.readLine()) != null) {
                        linha = linha.replace("﻿", "").trim();
                        if (linha.startsWith("OK:")) texto = linha.substring(3).trim();
                        else if (linha.startsWith("ERR:")) erro = linha.substring(4).trim();
                    }
                }
                if (!pr.waitFor(25, TimeUnit.SECONDS)) pr.destroyForcibly();
            } catch (Exception ex) {
                erro = ex.toString();
            } finally {
                OUVINDO.set(false);
            }
            final String ouvido = texto, falha = erro;
            if (falha != null) LanternaMod.LOGGER.info("Lanterna: reconhecimento de voz falhou: {}", falha);
            mc.execute(() -> {
                if (mc.player == null) return;
                if (ouvido != null && !ouvido.isBlank()) AnelIA.ouvir(ouvido, true);
                else if (falha != null) mc.player.displayClientMessage(Component.translatable("anel.ia.voz.indisponivel"), false);
                else mc.player.displayClientMessage(Component.translatable("anel.ia.voz.nao_ouvi"), true);
            });
        }, "lanterna-ouvir-anel");
        t.setDaemon(true);
        t.start();
    }

    /** O que o anel sabe ouvir quando o ditado livre não está disponível (lista fechada de frases). */
    private static Set<String> frases() {
        Set<String> s = new LinkedHashSet<>();
        boolean pt = idioma().startsWith("pt");
        for (Destinations.Destination d : Destinations.all(2)) {
            String n = d.name().getString();
            if (pt) { s.add("me leve para " + n); s.add("leve me para " + n); s.add("ir para " + n); s.add("distância até " + n); s.add("fale sobre " + n); }
            else { s.add("take me to " + n); s.add("go to " + n); s.add("how far is " + n); s.add("tell me about " + n); }
        }
        if (pt) {
            s.add("me leve para casa"); s.add("me leve para a terra");
            for (String f : new String[]{"pare", "cancelar", "qual a minha carga", "carga", "bateria", "qual o meu nível", "nível", "missão",
                    "o que eu faço agora", "onde estou", "perigo", "inimigos", "ligar", "desligar", "voar", "pousar", "juramento",
                    "quem é você", "ajuda", "oi", "obrigado", "como vai", "conte uma piada", "tchau"}) s.add(f);
        } else {
            s.add("take me home"); s.add("take me to earth");
            for (String f : new String[]{"stop", "cancel", "charge", "battery", "what is my level", "level", "mission", "what should i do",
                    "where am i", "danger", "enemies", "power on", "power off", "fly", "land", "oath", "who are you", "help", "hello",
                    "thanks", "how are you", "tell me a joke", "bye"}) s.add(f);
        }
        return s;
    }

    // ===================== falar =====================

    public static void falar(String texto) {
        if (mudo || !com.lanterna.ia.ConfigCliente.VOZ.get() || texto == null || texto.isBlank() || naFila > 2) return;
        String limpo = texto.replaceAll("[◈›✦◆•▶◀■□▲▼🎙]", " ").replaceAll("\\s+", " ").trim();
        if (limpo.length() > 400) limpo = limpo.substring(0, 400);
        final String fala = limpo;
        final String idioma = idioma();
        final String tom = tom(ClientRingData.corps.getPath());
        final int vel = com.lanterna.ia.ConfigCliente.VELOCIDADE.get();
        naFila++;
        FALA.submit(() -> {
            try {
                ProcessBuilder pb;
                if (WINDOWS) {
                    pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                            "-EncodedCommand", codificar(SCRIPT_FALAR));
                    pb.environment().put("LANTERNA_TTS", fala);
                    pb.environment().put("LANTERNA_LANG", idioma);
                    pb.environment().put("LANTERNA_RATE", String.valueOf(vel));
                    pb.environment().put("LANTERNA_PITCH", tom);
                } else if (MAC) {
                    pb = idioma.startsWith("pt") ? new ProcessBuilder("say", "-v", "Luciana", fala) : new ProcessBuilder("say", fala);
                } else {
                    pb = new ProcessBuilder("espeak-ng", "-v", idioma.startsWith("pt") ? "pt-br" : "en", fala);
                }
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                Process pr = pb.start();
                if (!pr.waitFor(40, TimeUnit.SECONDS)) pr.destroyForcibly();
            } catch (Exception ex) {
                LanternaMod.LOGGER.debug("Lanterna: voz do anel indisponível ({})", ex.toString());
            } finally {
                naFila--;
            }
        });
    }

    /** Tom de voz de cada Tropa (SSML). */
    private static String tom(String tropa) {
        return switch (tropa) {
            case "yellow" -> "-25%";
            case "red" -> "-30%";
            case "orange" -> "-20%";
            case "black" -> "-40%";
            case "blue" -> "+5%";
            case "violet" -> "+10%";
            case "indigo" -> "-5%";
            case "white" -> "+15%";
            default -> "-10%";
        };
    }

    static String idioma() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.startsWith("pt") ? "pt-BR" : "en-US";
    }

    private static String codificar(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    private static final String SCRIPT_FALAR = String.join("\n",
            "$ErrorActionPreference = 'Stop'",
            "Add-Type -AssemblyName System.Speech",
            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
            "$cult = $env:LANTERNA_LANG",
            "$v = $s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq $cult } | Select-Object -First 1",
            "if (-not $v) { $two = $cult.Substring(0,2); $v = $s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq $two } | Select-Object -First 1 }",
            "if ($v) { $s.SelectVoice($v.VoiceInfo.Name) }",
            "$s.Rate = [int]$env:LANTERNA_RATE",
            "$t = [System.Security.SecurityElement]::Escape($env:LANTERNA_TTS)",
            "$xml = \"<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='\" + $s.Voice.Culture.Name + \"'><prosody pitch='\" + $env:LANTERNA_PITCH + \"'>\" + $t + \"</prosody></speak>\"",
            "try { $s.SpeakSsml($xml) } catch { $s.Speak($env:LANTERNA_TTS) }");

    private static final String SCRIPT_OUVIR = String.join("\n",
            "$ErrorActionPreference = 'Stop'",
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
            "$lang = $env:LANTERNA_LANG",
            "$frases = [string[]]($env:LANTERNA_FRASES -split '\\|')",
            "function Saida($t) { [Console]::Out.WriteLine('OK:' + $t); [Console]::Out.Flush() }",
            "try {",
            "  Add-Type -AssemblyName System.Runtime.WindowsRuntime",
            "  $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
            "  function Await($op, [Type]$tipo) { $task = $asTask.MakeGenericMethod($tipo).Invoke($null, @($op)); $null = $task.Wait(-1); $task.Result }",
            "  $null = [Windows.Media.SpeechRecognition.SpeechRecognizer, Windows.Media.SpeechRecognition, ContentType = WindowsRuntime]",
            "  $null = [Windows.Media.SpeechRecognition.SpeechRecognitionListConstraint, Windows.Media.SpeechRecognition, ContentType = WindowsRuntime]",
            "  $null = [Windows.Globalization.Language, Windows.Globalization, ContentType = WindowsRuntime]",
            "  function Novo($comLista) {",
            "    try { $r = [Windows.Media.SpeechRecognition.SpeechRecognizer]::new([Windows.Globalization.Language]::new($lang)) } catch { $r = [Windows.Media.SpeechRecognition.SpeechRecognizer]::new() }",
            "    $r.Timeouts.InitialSilenceTimeout = [TimeSpan]::FromSeconds(6)",
            "    $r.Timeouts.EndSilenceTimeout = [TimeSpan]::FromMilliseconds(1200)",
            "    if ($comLista) { $r.Constraints.Add([Windows.Media.SpeechRecognition.SpeechRecognitionListConstraint]::new($frases, 'lanterna')) }",
            "    $comp = Await ($r.CompileConstraintsAsync()) ([Windows.Media.SpeechRecognition.SpeechRecognitionCompilationResult])",
            "    if ($comp.Status -ne [Windows.Media.SpeechRecognition.SpeechRecognitionResultStatus]::Success) { throw ('compile ' + $comp.Status) }",
            "    return $r",
            "  }",
            "  try {",
            "    $r = Novo $false",
            "    $res = Await ($r.RecognizeAsync()) ([Windows.Media.SpeechRecognition.SpeechRecognitionResult])",
            "    Saida $res.Text",
            "  } catch {",
            "    $r = Novo $true",
            "    $res = Await ($r.RecognizeAsync()) ([Windows.Media.SpeechRecognition.SpeechRecognitionResult])",
            "    Saida $res.Text",
            "  }",
            "} catch {",
            "  $primeiro = $_.Exception.Message",
            "  try {",
            "    Add-Type -AssemblyName System.Speech",
            "    $e = New-Object System.Speech.Recognition.SpeechRecognitionEngine",
            "    $e.SetInputToDefaultAudioDevice()",
            "    $e.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar))",
            "    $res = $e.Recognize([TimeSpan]::FromSeconds(8))",
            "    if ($res) { Saida $res.Text } else { Saida '' }",
            "  } catch { [Console]::Out.WriteLine('ERR:' + $primeiro + ' / ' + $_.Exception.Message) }",
            "}");

    private VozAnel() {}
}

package com.lanterna.client.guia;

import com.lanterna.client.ClientRingData;
import com.lanterna.client.KeyBindings;
import com.lanterna.client.hud.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Guia do Portador: um tutorial que se folheia dentro do jogo. À esquerda, os capítulos (com o que já foi lido); à
 * direita, a página, com texto, as teclas que o jogador usa de verdade, itens, receitas de verdade desenhadas na grade
 * (lidas do jogo, com os ingredientes trocando) e dicas. O último capítulo traz um gostinho do que vem por aí.
 *
 * Setas ou A/D trocam de página, W/S de capítulo, a rodinha rola o texto. Tudo na cor da Tropa do jogador.
 */
public class GuiaScreen extends Screen {
    private static final int LADO = 132, ROW = 20, TOPO_LADO = 46, RODAPE = 26;

    private final List<GuiaDados.Capitulo> caps;
    private int cap, pag;
    private float rolar, rolarAlvo, rolarLado;
    private int alturaConteudo;
    private long abriu, trocou;
    private int direcao = 1;
    private ItemStack dicaItem = ItemStack.EMPTY;
    private List<Component> dicaTexto;

    public GuiaScreen() {
        super(Component.translatable("guia.lanterna.titulo"));
        caps = GuiaDados.capitulos();
        String ultimo = GuiaProgresso.ultimoCapitulo();
        for (int i = 0; i < caps.size(); i++) if (caps.get(i).id().equals(ultimo)) {
            cap = i;
            pag = Mth.clamp(GuiaProgresso.ultimaPagina(), 0, caps.get(i).paginas().size() - 1);
        }
    }

    /** Abre direto num capítulo (pelo id). */
    public static GuiaScreen em(String id) {
        GuiaScreen s = new GuiaScreen();
        for (int i = 0; i < s.caps.size(); i++) if (s.caps.get(i).id().equals(id)) { s.cap = i; s.pag = 0; }
        return s;
    }

    @Override
    protected void init() {
        abriu = Util.agora();
        trocou = abriu - 1000;
        marcar();
        mostrarNaLateral();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===================== geometria =====================

    private int w() { return Math.min(width - 16, 520); }
    private int h() { return Math.min(height - 16, 300); }
    private int x0() { return (width - w()) / 2; }
    private int y0() { return (height - h()) / 2; }
    private int cx() { return x0() + LADO + 14; }
    private int cw() { return w() - LADO - 26; }
    private int cy() { return y0() + 44; }
    private int ch() { return h() - 44 - RODAPE - 6; }

    private int cor() {
        if (caps.isEmpty()) return 0x2BFF4A;
        int c = caps.get(cap).cor();
        if (c >= 0) return c;
        int anel = ClientRingData.hasRing ? ClientRingData.color : 0x2BFF4A;
        // anel Preto/Branco quase sem cor no escuro: puxa para um tom que aparece
        if ((anel & 0xFFFFFF) == 0x3A3A44) return 0x8A7FA8;
        return anel;
    }

    // ===================== desenho =====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        dicaItem = ItemStack.EMPTY;
        dicaTexto = null;
        float t = (Util.agora() - abriu) / 1000f;
        float entrada = Mth.clamp(t / 0.28f, 0, 1);
        entrada = 1 - (1 - entrada) * (1 - entrada) * (1 - entrada);
        rolar += (rolarAlvo - rolar) * 0.35f;

        fundo(g, t);
        if (caps.isEmpty()) {
            Hud.textCenter(g, Component.literal("guia.json?"), width / 2, height / 2, 0xFFFFFFFF);
            return;
        }
        int cor = cor();
        var pose = g.pose();
        pose.pushPose();
        float esc = 0.96f + 0.04f * entrada;
        pose.translate(width / 2f, height / 2f, 0);
        pose.scale(esc, esc, 1);
        pose.translate(-width / 2f, -height / 2f, 0);

        moldura(g, cor, t, entrada);
        lateral(g, mx, my, cor, t);
        conteudo(g, mx, my, cor, t);
        rodape(g, mx, my, cor);
        pose.popPose();

        if (!dicaItem.isEmpty()) g.renderTooltip(font, dicaItem, mx, my);
        else if (dicaTexto != null) g.renderComponentTooltip(font, dicaTexto, mx, my);
    }

    /** O céu atrás do guia: escurece o jogo, estrelas piscando e um brilho da cor da Tropa embaixo. */
    private void fundo(GuiGraphics g, float t) {
        g.fill(0, 0, width, height, 0xC8040608);
        int cor = caps.isEmpty() ? 0x2BFF4A : cor();
        boolean spoiler = !caps.isEmpty() && caps.get(cap).spoiler();
        g.fillGradient(0, height / 2, width, height, Hud.argb(cor, 0), Hud.argb(cor, spoiler ? 0x30 : 0x22));
        for (int i = 0; i < 90; i++) {
            long h = i * 2654435761L;
            int sx = (int) ((h >>> 3) % Math.max(1, width));
            int sy = (int) (((h >>> 17) % 1000) / 1000.0 * height);
            double drift = (t * (2 + (i % 5))) % Math.max(1, width);
            int x = (int) ((sx + drift) % Math.max(1, width));
            float brilho = 0.35f + 0.65f * (float) (0.5 + 0.5 * Math.sin(t * (1.3 + (i % 7) * 0.4) + i));
            int a = (int) (brilho * (i % 3 == 0 ? 200 : 110));
            int c = i % 11 == 0 ? Hud.mix(cor, 0xFFFFFF, 0.5f) : 0xFFFFFF;
            g.fill(x, sy, x + 1, sy + 1, Hud.argb(c, a));
            if (i % 13 == 0 && brilho > 0.9f) {
                g.fill(x - 1, sy, x + 2, sy + 1, Hud.argb(c, a / 3));
                g.fill(x, sy - 1, x + 1, sy + 2, Hud.argb(c, a / 3));
            }
        }
        if (spoiler) {
            // linhas de varredura, como uma transmissão interceptada
            int off = (int) (t * 30) % 4;
            for (int y = off; y < height; y += 4) g.fill(0, y, width, y + 1, 0x14000000);
        }
    }

    private void moldura(GuiGraphics g, int cor, float t, float entrada) {
        int x = x0(), y = y0(), w = w(), h = h();
        // sombra e brilho em volta
        for (int i = 1; i <= 6; i++) {
            int a = (int) ((7 - i) * 7 * entrada);
            g.fill(x - i, y - i, x + w + i, y - i + 1, Hud.argb(cor, a));
            g.fill(x - i, y + h + i - 1, x + w + i, y + h + i, Hud.argb(cor, a));
            g.fill(x - i, y - i, x - i + 1, y + h + i, Hud.argb(cor, a));
            g.fill(x + w + i - 1, y - i, x + w + i, y + h + i, Hud.argb(cor, a));
        }
        g.fillGradient(x, y, x + w, y + h, 0xF20B0F14, 0xF2070A0D);
        // lateral um pouco mais escura
        g.fill(x, y, x + LADO, y + h, 0x60000000);
        g.fillGradient(x + LADO, y + 8, x + LADO + 1, y + h - 8, Hud.argb(cor, 0x10), Hud.argb(cor, 0x10));
        g.fill(x + LADO, y + 8, x + LADO + 1, y + h - 8, Hud.argb(cor, 0x50));
        // borda fina
        int borda = Hud.argb(cor, 0x70);
        g.fill(x, y, x + w, y + 1, borda);
        g.fill(x, y + h - 1, x + w, y + h, Hud.argb(cor, 0x40));
        g.fill(x, y, x + 1, y + h, Hud.argb(cor, 0x40));
        g.fill(x + w - 1, y, x + w, y + h, Hud.argb(cor, 0x40));
        // cantos em colchete
        int k = 10, forte = Hud.argb(Hud.mix(cor, 0xFFFFFF, 0.3f), 0xF0);
        int[][] cs = {{x, y, 1, 1}, {x + w, y, -1, 1}, {x, y + h, 1, -1}, {x + w, y + h, -1, -1}};
        for (int[] c : cs) {
            int ax = c[0], ay = c[1];
            int bx = ax + c[2] * k, by = ay + c[3] * k;
            g.fill(Math.min(ax, bx), c[3] > 0 ? ay : ay - 2, Math.max(ax, bx), c[3] > 0 ? ay + 2 : ay, forte);
            g.fill(c[2] > 0 ? ax : ax - 2, Math.min(ay, by), c[2] > 0 ? ax + 2 : ax, Math.max(ay, by), forte);
        }
        // luz que corre pela borda de cima
        float p = (t * 0.25f) % 1f;
        int lx = x + (int) (p * w);
        g.fillGradient(Math.max(x, lx - 40), y, lx, y + 1, Hud.argb(cor, 0), Hud.argb(0xFFFFFF, 0xC0));
    }

    /** Coluna dos capítulos. */
    private void lateral(GuiGraphics g, int mx, int my, int cor, float t) {
        int x = x0(), y = y0();
        // cabeçalho
        ItemStack anel = GuiaDados.item(ClientRingData.hasRing ? "anel:" + ClientRingData.corps.getPath() : "anel:green");
        g.renderItem(anel, x + 8, y + 8);
        g.drawString(font, Hud.caps(Component.translatable("guia.lanterna.titulo_curto")), x + 28, y + 9, Hud.argb(Hud.mix(cor, 0xFFFFFF, 0.55f), 255), true);
        int total = 0, lidas = 0;
        for (var c : caps) for (int i = 0; i < c.paginas().size(); i++) { total++; if (GuiaProgresso.lida(c.id(), i)) lidas++; }
        g.drawString(font, Hud.body(Component.translatable("guia.lanterna.progresso", lidas, total)), x + 28, y + 20, 0xFF8A949E, false);
        Hud.bar(g, x + 8, y + 33, LADO - 16, 3, total == 0 ? 0 : lidas / (float) total, cor, false);

        int topo = y + TOPO_LADO, fim = y + h() - 6;
        int maxRolar = Math.max(0, caps.size() * ROW - (fim - topo));
        rolarLado = Mth.clamp(rolarLado, 0, maxRolar);
        g.enableScissor(x, topo, x + LADO, fim);
        for (int i = 0; i < caps.size(); i++) {
            var c = caps.get(i);
            int ry = topo + i * ROW - (int) rolarLado;
            if (ry + ROW < topo || ry > fim) continue;
            boolean sel = i == cap;
            boolean sobre = mx >= x && mx < x + LADO && my >= ry && my < ry + ROW && my >= topo && my < fim;
            int ccor = c.cor() >= 0 ? c.cor() : cor;
            if (sel) {
                g.fillGradient(x + 2, ry, x + LADO - 1, ry + ROW, Hud.argb(ccor, 0x50), Hud.argb(ccor, 0x18));
                g.fill(x, ry + 2, x + 3, ry + ROW - 2, Hud.argb(Hud.mix(ccor, 0xFFFFFF, 0.3f), 255));
            } else if (sobre) {
                g.fill(x + 2, ry, x + LADO - 1, ry + ROW, 0x18FFFFFF);
            }
            g.pose().pushPose();
            g.pose().translate(x + 7, ry + 2, 0);
            g.renderItem(GuiaDados.item(c.icone()), 0, 0);
            g.pose().popPose();
            int lidasCap = 0;
            for (int p = 0; p < c.paginas().size(); p++) if (GuiaProgresso.lida(c.id(), p)) lidasCap++;
            boolean completo = lidasCap == c.paginas().size();
            MutableComponent nome = Hud.body(Component.literal(c.titulo()));
            if (c.spoiler() && !completo && (int) (t * 2) % 7 == 0) nome = nome.withStyle(ChatFormatting.OBFUSCATED);
            int corNome = sel ? 0xFFFFFFFF : completo ? 0xFFB8C0C8 : 0xFFE6EAEE;
            Hud.textFit(g, nome, x + 27, ry + 6, LADO - 27 - 16, corNome, sel);
            if (completo) {
                g.drawString(font, "✔", x + LADO - 13, ry + 6, Hud.argb(ccor, 0xE0), false);
            } else if (c.spoiler()) {
                float pul = 0.5f + 0.5f * (float) Math.sin(t * 5);
                g.fill(x + LADO - 13, ry + 7, x + LADO - 8, ry + 12, Hud.argb(Hud.mix(ccor, 0xFFFFFF, 0.4f), (int) (120 + 135 * pul)));
            } else if (lidasCap > 0) {
                g.drawString(font, Hud.body(Component.literal(lidasCap + "/" + c.paginas().size())), x + LADO - 22, ry + 6, 0xFF6B747C, false);
            }
            if (sobre && !c.resumo().isEmpty()) dicaTexto = List.of(Component.literal(c.titulo()).withStyle(s -> s.withColor(ccor)), Component.literal(c.resumo()).withStyle(ChatFormatting.GRAY));
        }
        g.disableScissor();
        if (maxRolar > 0) {
            int trilho = fim - topo, alca = Math.max(12, trilho * trilho / (trilho + maxRolar));
            int ay = topo + (int) ((trilho - alca) * (rolarLado / maxRolar));
            g.fill(x + LADO - 3, ay, x + LADO - 1, ay + alca, Hud.argb(cor, 0x90));
        }
    }

    private void conteudo(GuiGraphics g, int mx, int my, int cor, float t) {
        var c = caps.get(cap);
        var p = c.paginas().get(pag);
        float tt = Mth.clamp((Util.agora() - trocou) / 220f, 0, 1);
        float ease = 1 - (1 - tt) * (1 - tt);
        int desliza = (int) ((1 - ease) * 18 * direcao);
        int alfa = (int) (255 * ease);
        int x = cx() + desliza, y0 = y0();
        int cw = cw();

        // cabeçalho do capítulo (não rola)
        g.drawString(font, Hud.body(Component.translatable("guia.lanterna.capitulo", cap + 1, caps.size())), x, y0 + 9, Hud.argb(0x7C8791, Math.max(16, alfa)), false);
        MutableComponent titulo = Hud.caps(Component.literal(c.titulo()));
        if (c.spoiler() && ((int) (t * 3) % 9 == 0)) titulo = titulo.withStyle(ChatFormatting.OBFUSCATED);
        g.drawString(font, titulo, x, y0 + 21, Hud.argb(Hud.mix(cor, 0xFFFFFF, 0.65f), Math.max(16, alfa)), true);
        String pagina = (pag + 1) + " / " + c.paginas().size();
        g.drawString(font, Hud.body(Component.literal(pagina)), cx() + cw - font.width(pagina) - 2, y0 + 9, Hud.argb(0x7C8791, Math.max(16, alfa)), false);
        g.fillGradient(cx(), y0 + 35, cx() + cw, y0 + 36, Hud.argb(cor, 0xA0), Hud.argb(cor, 0x10));
        g.fill(cx(), y0 + 35, cx() + Math.min(cw, 40 + (int) (ease * (cw - 40))), y0 + 36, Hud.argb(cor, 0xB0));

        int topo = cy(), fim = cy() + ch();
        int y = topo - (int) rolar;
        g.enableScissor(cx() - 4, topo, cx() + cw + 4, fim);

        if (!p.titulo().isEmpty()) {
            g.drawString(font, Hud.bold(Component.literal(p.titulo())), x, y + 2, Hud.argb(Hud.mix(cor, 0xFFFFFF, 0.25f), Math.max(16, alfa)), true);
            y += 16;
        }
        // texto
        for (FormattedCharSequence linha : linhas(p.texto(), cw, cor, c.spoiler(), t)) {
            g.drawString(font, linha, x, y, Hud.argb(0xDCE2E8, Math.max(16, alfa)), false);
            y += 11;
        }
        y += 4;
        // teclas
        if (!p.teclas().isEmpty()) y = teclas(g, p.teclas(), x, y, cw, cor, mx, my) + 6;
        // itens
        if (!p.itens().isEmpty()) y = itens(g, p.itens(), x, y, cw, cor, mx, my, topo, fim) + 6;
        // receitas
        if (!p.receitas().isEmpty()) y = receitas(g, p.receitas(), x, y, cw, cor, mx, my, t, topo, fim) + 6;
        // dica
        if (!p.dica().isEmpty()) y = dica(g, p.dica(), x, y, cw, cor) + 4;

        g.disableScissor();
        alturaConteudo = y + (int) rolar - topo;
        int maxRolar = Math.max(0, alturaConteudo - ch());
        rolarAlvo = Mth.clamp(rolarAlvo, 0, maxRolar);
        if (maxRolar > 0) {
            // sombra embaixo dizendo "tem mais"
            if (rolar < maxRolar - 2) g.fillGradient(cx(), fim - 14, cx() + cw, fim, 0x000B0F14, 0xE00B0F14);
            int trilho = ch(), alca = Math.max(14, trilho * trilho / (trilho + maxRolar));
            int ay = topo + (int) ((trilho - alca) * (rolar / Math.max(1, maxRolar)));
            g.fill(cx() + cw + 6, topo, cx() + cw + 7, fim, 0x20FFFFFF);
            g.fill(cx() + cw + 5, ay, cx() + cw + 8, ay + alca, Hud.argb(cor, 0xA0));
        }
    }

    /** O texto da página, com **destaque**, ||tarja|| e {tecla:...} já trocados, quebrado na largura. */
    private List<FormattedCharSequence> linhas(String texto, int largura, int cor, boolean spoiler, float t) {
        List<FormattedCharSequence> out = new ArrayList<>();
        texto = teclasNoTexto(texto);
        for (String par : texto.split("\n", -1)) {
            if (par.isEmpty()) { out.add(FormattedCharSequence.EMPTY); continue; }
            MutableComponent linha = Component.empty();
            boolean dest = false, tarja = false;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i <= par.length(); i++) {
                boolean fimTexto = i == par.length();
                boolean marcaD = !fimTexto && par.startsWith("**", i), marcaT = !fimTexto && par.startsWith("||", i);
                if (fimTexto || marcaD || marcaT) {
                    if (sb.length() > 0) {
                        Style st = Style.EMPTY.withFont(Hud.BODY);
                        if (dest) st = st.withColor(Hud.mix(cor, 0xFFFFFF, 0.35f)).withFont(Hud.BOLD);
                        if (tarja) st = st.withColor(spoiler ? 0x6A5A80 : 0x55606A).withObfuscated(true);
                        linha.append(Component.literal(sb.toString()).withStyle(st));
                        sb.setLength(0);
                    }
                    if (marcaD) { dest = !dest; i++; }
                    else if (marcaT) { tarja = !tarja; i++; }
                    continue;
                }
                sb.append(par.charAt(i));
            }
            out.addAll(font.split(linha, largura));
        }
        return out;
    }

    private static String teclasNoTexto(String s) {
        int i;
        while ((i = s.indexOf("{tecla:")) >= 0) {
            int j = s.indexOf('}', i);
            if (j < 0) break;
            KeyMapping k = acharTecla(s.substring(i + 7, j));
            String nome = k == null ? "?" : k.getTranslatedKeyMessage().getString();
            s = s.substring(0, i) + "**[" + nome + "]**" + s.substring(j + 1);
        }
        return s;
    }

    static KeyMapping acharTecla(String nome) {
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) if (k.getName().equals(nome)) return k;
        return null;
    }

    /** As teclas como teclinhas de teclado, com o nome do que fazem do lado. */
    private int teclas(GuiGraphics g, List<String> lista, int x, int y, int largura, int cor, int mx, int my) {
        int cx = x, linhaH = 18;
        for (String n : lista) {
            KeyMapping k = acharTecla(n);
            if (k == null) continue;
            String tecla = k.getTranslatedKeyMessage().getString();
            if (tecla.length() > 14) tecla = tecla.substring(0, 13) + "…";
            Component rotulo = Hud.body(Component.translatable(k.getName()));
            int tw = Math.max(14, font.width(tecla) + 8);
            int total = tw + 4 + font.width(rotulo) + 12;
            if (cx + total > x + largura && cx > x) { cx = x; y += linhaH; }
            // a tecla: caixinha com "relevo"
            g.fill(cx, y + 1, cx + tw, y + 15, 0xFF2A3038);
            g.fill(cx, y + 13, cx + tw, y + 15, 0xFF151A1F);
            g.fill(cx, y + 1, cx + tw, y + 2, Hud.argb(cor, 0xA0));
            g.drawString(font, tecla, cx + (tw - font.width(tecla)) / 2, y + 4, 0xFFFFFFFF, false);
            g.drawString(font, rotulo, cx + tw + 4, y + 4, 0xFFAEB7C0, false);
            cx += total;
        }
        return y + linhaH;
    }

    private int itens(GuiGraphics g, List<String> lista, int x, int y, int largura, int cor, int mx, int my, int topo, int fim) {
        int cx = x;
        for (String s : lista) {
            if (cx + 22 > x + largura) { cx = x; y += 24; }
            ItemStack st = GuiaDados.item(s);
            slot(g, cx, y, 20, cor, false);
            g.renderItem(st, cx + 2, y + 2);
            if (mx >= cx && mx < cx + 20 && my >= y && my < y + 20 && my >= topo && my < fim) {
                g.fill(cx + 1, y + 1, cx + 19, y + 19, 0x40FFFFFF);
                dicaItem = st;
            }
            cx += 23;
        }
        return y + 22;
    }

    /** Receitas de verdade: grade 3x3, seta e resultado; ingrediente com várias opções vai trocando. */
    private int receitas(GuiGraphics g, List<String> ids, int x, int y, int largura, int cor, int mx, int my, float t, int topo, int fim) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return y;
        int cx = x, bloco = 3 * 18 + 30 + 28, altura = 3 * 18 + 14;
        for (String id : ids) {
            Recipe<?> r = mc.level.getRecipeManager().byKey(new ResourceLocation(id)).orElse(null);
            if (r == null) continue;
            if (cx + bloco > x + largura && cx > x) { cx = x; y += altura + 6; }
            int gw = 3, gh = 3;
            var ing = r.getIngredients();
            if (r instanceof ShapedRecipe sr) { gw = sr.getWidth(); gh = sr.getHeight(); }
            g.fill(cx - 3, y - 3, cx + bloco - 4, y + altura - 2, 0x40000000);
            for (int gy = 0; gy < 3; gy++) for (int gx = 0; gx < 3; gx++) {
                int sx = cx + gx * 18, sy = y + gy * 18;
                slot(g, sx, sy, 18, cor, false);
                int idx = r instanceof ShapedRecipe ? (gx < gw && gy < gh ? gy * gw + gx : -1) : gy * 3 + gx;
                if (idx < 0 || idx >= ing.size()) continue;
                Ingredient in = ing.get(idx);
                ItemStack[] op = in.getItems();
                if (op.length == 0) continue;
                ItemStack st = op[(int) (t) % op.length];
                g.renderItem(st, sx + 1, sy + 1);
                if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18 && my >= topo && my < fim) {
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x40FFFFFF);
                    dicaItem = st;
                }
            }
            // seta animada
            int ax = cx + 3 * 18 + 6, ay = y + 18 + 5;
            float pul = 0.5f + 0.5f * (float) Math.sin(t * 4);
            g.fill(ax, ay + 3, ax + 14, ay + 5, Hud.argb(cor, (int) (140 + 100 * pul)));
            for (int i = 0; i < 5; i++) g.fill(ax + 13 + i - 4, ay + i - 1, ax + 14 + i - 4, ay + 9 - i, Hud.argb(cor, (int) (140 + 100 * pul)));
            // resultado
            ItemStack res = r.getResultItem(mc.level.registryAccess());
            int rx = cx + 3 * 18 + 28, ry = y + 18 - 4;
            slot(g, rx, ry, 26, cor, true);
            g.pose().pushPose();
            g.pose().translate(rx + 13, ry + 13, 0);
            g.pose().scale(1.375f, 1.375f, 1);
            g.renderItem(res, -8, -8);
            g.renderItemDecorations(font, res, -8, -8);
            g.pose().popPose();
            if (mx >= rx && mx < rx + 26 && my >= ry && my < ry + 26 && my >= topo && my < fim) dicaItem = res;
            Hud.textFit(g, Hud.body(res.getHoverName()), cx, y + 3 * 18 + 3, bloco - 8, 0xFFAEB7C0, false);
            cx += bloco + 8;
        }
        return y + altura;
    }

    private static void slot(GuiGraphics g, int x, int y, int s, int cor, boolean destaque) {
        g.fill(x, y, x + s, y + s, 0xFF1A1F25);
        g.fill(x, y, x + s, y + 1, 0xFF0C0F12);
        g.fill(x, y, x + 1, y + s, 0xFF0C0F12);
        g.fill(x + 1, y + s - 1, x + s, y + s, 0xFF39414A);
        g.fill(x + s - 1, y + 1, x + s, y + s, 0xFF39414A);
        if (destaque) {
            int c = Hud.argb(cor, 0xB0);
            g.fill(x - 1, y - 1, x + s + 1, y, c);
            g.fill(x - 1, y + s, x + s + 1, y + s + 1, c);
            g.fill(x - 1, y, x, y + s, c);
            g.fill(x + s, y, x + s + 1, y + s, c);
        }
    }

    private int dica(GuiGraphics g, String texto, int x, int y, int largura, int cor) {
        List<FormattedCharSequence> ls = linhas(texto, largura - 16, cor, false, 0);
        int h = 14 + ls.size() * 10 + 4;
        g.fillGradient(x, y, x + largura, y + h, Hud.argb(cor, 0x26), Hud.argb(cor, 0x0C));
        g.fill(x, y, x + 2, y + h, Hud.argb(cor, 0xE0));
        g.drawString(font, Hud.bold(Component.translatable("guia.lanterna.dica")), x + 8, y + 4, Hud.argb(Hud.mix(cor, 0xFFFFFF, 0.4f), 255), false);
        int ly = y + 15;
        for (var l : ls) { g.drawString(font, l, x + 8, ly, 0xFFD0D6DC, false); ly += 10; }
        return y + h;
    }

    /** Setas, bolinhas das páginas e o atalho de teclas. */
    private void rodape(GuiGraphics g, int mx, int my, int cor) {
        var c = caps.get(cap);
        int y = y0() + h() - RODAPE + 4, x = cx(), cw = cw();
        g.fill(cx(), y - 5, cx() + cw, y - 4, 0x18FFFFFF);
        boolean temAntes = pag > 0 || cap > 0, temDepois = pag < c.paginas().size() - 1 || cap < caps.size() - 1;
        botao(g, x, y, -1, temAntes, mx, my, cor);
        botao(g, x + cw - 18, y, 1, temDepois, mx, my, cor);
        int n = c.paginas().size(), larg = n * 10;
        int bx = x + 24;
        for (int i = 0; i < n; i++) {
            boolean lida = GuiaProgresso.lida(c.id(), i);
            int px = bx + i * 10 + 2;
            if (i == pag) g.fill(px - 1, y + 5, px + 6, y + 9, Hud.argb(Hud.mix(cor, 0xFFFFFF, 0.3f), 255));
            else g.fill(px + 1, y + 6, px + 4, y + 8, lida ? Hud.argb(cor, 0xA0) : 0x60FFFFFF);
        }
        Component ajuda = Hud.body(Component.translatable("guia.lanterna.controles"));
        int aw = font.width(ajuda);
        if (aw < cw - 60 - larg) g.drawString(font, ajuda, x + cw - 24 - aw, y + 3, 0xFF5E6770, false);
    }

    /** Botão com uma setinha desenhada (as fontes não têm ◀ ▶). */
    private void botao(GuiGraphics g, int x, int y, int lado, boolean ativo, int mx, int my, int cor) {
        boolean sobre = ativo && mx >= x && mx < x + 18 && my >= y && my < y + 14;
        g.fill(x, y, x + 18, y + 14, sobre ? Hud.argb(cor, 0x70) : ativo ? 0x40FFFFFF : 0x14FFFFFF);
        int c = ativo ? 0xFFFFFFFF : 0x50FFFFFF, mxx = x + 9, my0 = y + 7;
        for (int i = 0; i < 4; i++) {
            int px = lado > 0 ? mxx - 2 + i : mxx + 1 - i;
            g.fill(px, my0 - 3 + i, px + 1, my0 + 4 - i, c);
        }
    }

    // ===================== navegação =====================

    private void irPara(int novoCap, int novaPag, int dir) {
        if (caps.isEmpty()) return;
        novoCap = Mth.clamp(novoCap, 0, caps.size() - 1);
        novaPag = Mth.clamp(novaPag, 0, caps.get(novoCap).paginas().size() - 1);
        if (novoCap == cap && novaPag == pag) return;
        cap = novoCap;
        pag = novaPag;
        direcao = dir;
        trocou = Util.agora();
        rolar = rolarAlvo = 0;
        marcar();
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                caps.get(cap).spoiler() ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.BOOK_PAGE_TURN, 1f, 1.1f));
        mostrarNaLateral();
    }

    /** Rola a coluna dos capítulos até o escolhido aparecer. */
    private void mostrarNaLateral() {
        int topo = y0() + TOPO_LADO, fim = y0() + h() - 6;
        int ry = cap * ROW;
        if (ry < rolarLado) rolarLado = ry;
        if (ry + ROW > rolarLado + (fim - topo)) rolarLado = ry + ROW - (fim - topo);
    }

    private void proxima() {
        var c = caps.get(cap);
        if (pag < c.paginas().size() - 1) irPara(cap, pag + 1, 1);
        else if (cap < caps.size() - 1) irPara(cap + 1, 0, 1);
    }

    private void anterior() {
        if (pag > 0) irPara(cap, pag - 1, -1);
        else if (cap > 0) irPara(cap - 1, caps.get(cap - 1).paginas().size() - 1, -1);
    }

    private void marcar() {
        if (!caps.isEmpty()) GuiaProgresso.ler(caps.get(cap).id(), pag);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int botao) {
        if (botao == 0 && !caps.isEmpty()) {
            int x = x0(), topo = y0() + TOPO_LADO, fim = y0() + h() - 6;
            if (mx >= x && mx < x + LADO && my >= topo && my < fim) {
                int i = (int) ((my - topo + rolarLado) / ROW);
                if (i >= 0 && i < caps.size() && i != cap) irPara(i, 0, i > cap ? 1 : -1);
                return true;
            }
            int y = y0() + h() - RODAPE + 4;
            if (my >= y && my < y + 14) {
                if (mx >= cx() && mx < cx() + 18) { anterior(); return true; }
                if (mx >= cx() + cw() - 18 && mx < cx() + cw()) { proxima(); return true; }
                // bolinhas
                var c = caps.get(cap);
                int n = c.paginas().size(), bx = cx() + 24;
                if (mx >= bx && mx < bx + n * 10) { int p = (int) ((mx - bx) / 10); irPara(cap, p, p > pag ? 1 : -1); return true; }
            }
        }
        return super.mouseClicked(mx, my, botao);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < x0() + LADO) rolarLado -= (float) delta * ROW;
        else rolarAlvo -= (float) delta * 22;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (caps.isEmpty()) return super.keyPressed(key, scan, mods);
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> { proxima(); return true; }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> { anterior(); return true; }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> { irPara(cap + 1, 0, 1); return true; }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> { irPara(cap - 1, 0, -1); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { rolarAlvo += ch() * 0.8f; return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { rolarAlvo -= ch() * 0.8f; return true; }
            default -> {}
        }
        if (KeyBindings.GUIA.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** Relógio da tela (em ms), para as animações não dependerem do tick do jogo. */
    static final class Util {
        static long agora() {
            return net.minecraft.Util.getMillis();
        }
    }
}

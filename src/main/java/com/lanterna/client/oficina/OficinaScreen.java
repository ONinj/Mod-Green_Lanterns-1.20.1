package com.lanterna.client.oficina;

import com.lanterna.client.mission.ClientLoreData;
import com.lanterna.client.ClientRingData;
import com.lanterna.client.hud.Hud;
import com.lanterna.construct.oficina.Comportamento;
import com.lanterna.construct.oficina.MeusConstrutos;
import com.lanterna.construct.oficina.Modelos;
import com.lanterna.construct.oficina.Projeto;
import com.lanterna.network.ModNetwork;
import com.lanterna.network.lore.ConstructSelectPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/**
 * Oficina de Construtos: desenhe o seu construto em 3D, cubinho por cubinho, escolha o que ele faz (arma, escudo,
 * disparo, queda do céu, veículo, estrutura) e guarde num dos 8 espaços; depois ponha num preset (Alt+1..4).
 *
 *   botão esquerdo   usa a ferramenta (arrastar continua pintando)
 *   botão direito    gira a vista (arrastar)        roda do mouse   aproxima / afasta
 *   1-4              tom          M   espelho          PgUp/PgDn  camada do ar
 *   Ctrl+Z / Ctrl+Y  desfazer / refazer               Espaço      testar a animação
 */
public class OficinaScreen extends Screen {
    private enum Ferramenta { LAPIS, BORRACHA, PINTAR, CAIXA }

    private Projeto proj = new Projeto();
    private int slot;
    private Ferramenta ferramenta = Ferramenta.LAPIS;
    private int tom;
    private boolean espelho = true;
    private int camada;
    private float yaw = -35, pitch = 24, zoom = 0;
    private final Deque<Projeto> desfazer = new ArrayDeque<>(), refazer = new ArrayDeque<>();
    private int[] caixaInicio;
    private boolean testando;
    private long testeInicio;
    private boolean girando, pintando;
    private double ultimoX, ultimoY;
    private String ultimoAlvo = "";
    private EditBox nome;
    private Component aviso;
    private long avisoAte;
    private boolean sujo;

    private int vx0, vy0, vx1, vy1;

    public OficinaScreen() {
        super(Component.translatable("oficina.lanterna.titulo"));
        for (int i = 0; i < Projeto.SLOTS; i++) if (OficinaCliente.MEUS[i] == null) { slot = i; break; }
        if (OficinaCliente.MEUS[slot] != null) proj = OficinaCliente.MEUS[slot].copia();
        else proj = Modelos.TODOS.get("espada").get();
    }

    private int cor() {
        return ClientRingData.hasRing ? ClientRingData.color : 0x40FF70;
    }

    // ===================== Montagem da tela =====================

    // posições dos rótulos (calculadas no init)
    private int yFerr, yModelos, yTons, yNome, yComp, yDesc, yEscala, yEspacos, yPreset, yInfo;

    @Override
    protected void init() {
        clearWidgets();
        int esq = 6, larguraEsq = 120, dir = width - 158, larguraDir = 152;
        int h = 14, p = 16; // altura do botão e passo
        vx0 = esq + larguraEsq + 8;
        vx1 = dir - 8;
        vy0 = 22;
        vy1 = height - 4;
        if (zoom <= 0) zoom = Math.max(4, Math.min((vx1 - vx0), (vy1 - vy0)) / 30f);
        int bw = (larguraEsq - 4) / 2;
        int y = 24;
        yFerr = y; y += 10;
        Ferramenta[] fs = Ferramenta.values();
        for (int i = 0; i < fs.length; i++) {
            Ferramenta f = fs[i];
            addRenderableWidget(new Botao(esq + (i % 2) * (bw + 4), y + (i / 2) * p, bw, h,
                    Component.translatable("oficina.lanterna.ferr." + f.name().toLowerCase()), () -> ferramenta == f, () -> {
                ferramenta = f;
                caixaInicio = null;
            }));
        }
        y += 2 * p;
        addRenderableWidget(new Botao(esq, y, bw, h, Component.translatable("oficina.lanterna.espelho"), () -> espelho, () -> espelho = !espelho));
        addRenderableWidget(new Botao(esq + bw + 4, y, bw, h, Component.translatable("oficina.lanterna.testar"), () -> testando, this::alternarTeste));
        y += p;
        addRenderableWidget(new Botao(esq, y, bw, h, Component.translatable("oficina.lanterna.desfazer"), () -> false, this::desfazer));
        addRenderableWidget(new Botao(esq + bw + 4, y, bw, h, Component.translatable("oficina.lanterna.refazer"), () -> false, this::refazer));
        y += p;
        addRenderableWidget(new Botao(esq, y, bw, h, Component.translatable("oficina.lanterna.camada_menos"), () -> false, () -> camada = Math.max(0, camada - 1)));
        addRenderableWidget(new Botao(esq + bw + 4, y, bw, h, Component.translatable("oficina.lanterna.camada_mais"), () -> false, () -> camada = Math.min(Projeto.N - 1, camada + 1)));
        y += p;
        addRenderableWidget(new Botao(esq, y, larguraEsq, h, Component.translatable("oficina.lanterna.limpar"), () -> false, () -> {
            guardar();
            proj.limpar();
        }));
        y += p + 4;
        yModelos = y; y += 10;
        int i = 0;
        for (var e : Modelos.TODOS.entrySet()) {
            addRenderableWidget(new Botao(esq + (i % 2) * (bw + 4), y + (i / 2) * p, bw, h,
                    Component.translatable("oficina.lanterna.modelo." + e.getKey()), () -> false, () -> {
                guardar();
                String n = proj.nome;
                proj = e.getValue().get();
                proj.nome = n;
                camada = 0;
            }));
            i++;
        }
        y += ((Modelos.TODOS.size() + 1) / 2) * p + 4;
        yTons = y;
        // lado direito
        int ry = 24;
        yNome = ry; ry += 10;
        nome = new EditBox(font, dir, ry, larguraDir, h, Component.translatable("oficina.lanterna.nome"));
        nome.setMaxLength(24);
        nome.setValue(proj.nome);
        nome.setHint(Component.translatable("oficina.lanterna.nome_dica"));
        nome.setResponder(s -> proj.nome = s);
        addRenderableWidget(nome);
        ry += p + 4;
        yComp = ry; ry += 10;
        Comportamento[] cs = Comportamento.values();
        int cw = (larguraDir - 4) / 2;
        for (int k = 0; k < cs.length; k++) {
            Comportamento c = cs[k];
            addRenderableWidget(new Botao(dir + (k % 2) * (cw + 4), ry + (k / 2) * p, cw, h, c.nome(), () -> proj.comp == c, () -> {
                guardar();
                proj.comp = c;
            }));
        }
        ry += 3 * p + 2;
        yDesc = ry; ry += 30;
        yEscala = ry;
        addRenderableWidget(new Botao(dir, ry, 20, h, Component.literal("-"), () -> false, () -> proj.escala = Math.max(0.5f, Math.round((proj.escala - 0.25f) * 4) / 4f)));
        addRenderableWidget(new Botao(dir + larguraDir - 20, ry, 20, h, Component.literal("+"), () -> false, () -> proj.escala = Math.min(2f, Math.round((proj.escala + 0.25f) * 4) / 4f)));
        ry += p + 4;
        yEspacos = ry; ry += 10;
        int sw = (larguraDir - 12) / 4;
        for (int k = 0; k < Projeto.SLOTS; k++) {
            int s = k;
            addRenderableWidget(new Botao(dir + (k % 4) * (sw + 4), ry + (k / 4) * p, sw, h, Component.literal(String.valueOf(k + 1)), () -> slot == s, () -> escolherEspaco(s)) {
                @Override
                protected int corExtra() {
                    return OficinaCliente.MEUS[s] != null ? 0x80FFFFFF : 0;
                }
            });
        }
        ry += 2 * p + 2;
        addRenderableWidget(new Botao(dir, ry, larguraDir, h + 2, Component.translatable("oficina.lanterna.salvar"), () -> false, this::salvar));
        ry += p + 6;
        yPreset = ry; ry += 10;
        int pw = (larguraDir - 12) / 4;
        for (int k = 0; k < 4; k++) {
            int q = k;
            addRenderableWidget(new Botao(dir + k * (pw + 4), ry, pw, h, Component.literal("Alt+" + (k + 1)),
                    () -> ("meu:" + slot).equals(ClientLoreData.DATA.preset(q)), () -> porNoPreset(q)));
        }
        ry += p + 2;
        yInfo = ry;
    }

    /** Para o teste automático: abre já com este desenho. */
    public void carregar(Projeto p) {
        proj = p.copia();
        if (nome != null) nome.setValue(p.nome);
    }

    public void testar(boolean t) {
        testando = t;
        testeInicio = System.currentTimeMillis();
    }

    public void atualizarEspacos() {
        // a lista chegou do servidor (depois de salvar): nada a refazer, os botões leem direto
    }

    private void escolherEspaco(int s) {
        if (s == slot) return;
        slot = s;
        if (OficinaCliente.MEUS[s] != null) {
            guardar();
            proj = OficinaCliente.MEUS[s].copia();
            if (nome != null) nome.setValue(proj.nome);
        }
    }

    private void salvar() {
        if (proj.vazio()) {
            avisar(Component.translatable("oficina.lanterna.vazio"));
            return;
        }
        Projeto p = proj.copia();
        if (p.nome.isBlank()) p.nome = Component.translatable("oficina.lanterna.comp." + p.comp.id).getString() + " " + (slot + 1);
        proj.nome = p.nome;
        nome.setValue(p.nome);
        ModNetwork.sendToServer(new MeusConstrutos.Salvar(slot, p));
        OficinaCliente.MEUS[slot] = p.copia();
        sujo = false;
        avisar(Component.translatable("oficina.lanterna.salvo", p.nome, slot + 1));
        if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
    }

    private void porNoPreset(int p) {
        if (OficinaCliente.MEUS[slot] == null || sujo) salvar();
        if (OficinaCliente.MEUS[slot] == null) return;
        String id = "meu:" + slot;
        ModNetwork.sendToServer(new ConstructSelectPacket(p, id));
        ClientLoreData.DATA.setPreset(p, id);
        avisar(Component.translatable("oficina.lanterna.no_preset", OficinaCliente.MEUS[slot].nome, p + 1));
    }

    private void avisar(Component c) {
        aviso = c;
        avisoAte = System.currentTimeMillis() + 2500;
    }

    private void alternarTeste() {
        testando = !testando;
        testeInicio = System.currentTimeMillis();
    }

    private void guardar() {
        desfazer.push(proj.copia());
        while (desfazer.size() > 60) desfazer.removeLast();
        refazer.clear();
        sujo = true;
    }

    private void desfazer() {
        if (desfazer.isEmpty()) return;
        refazer.push(proj.copia());
        proj = desfazer.pop();
    }

    private void refazer() {
        if (refazer.isEmpty()) return;
        desfazer.push(proj.copia());
        proj = refazer.pop();
    }

    // ===================== A vista 3D =====================

    private Matrix4f modelo() {
        float cx = (vx0 + vx1) / 2f, cy = (vy0 + vy1) / 2f + 10;
        return new Matrix4f().translate(cx, cy, 400).scale(zoom, -zoom, zoom)
                .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw)).translate(-8, -6, -8);
    }

    private record Alvo(int x, int y, int z, int nx, int ny, int nz, boolean cheio) {
        int[] fora() { return new int[]{x + nx, y + ny, z + nz}; }
    }

    /** O cubinho debaixo do mouse (e a face por onde o raio entrou), ou a casa da camada. */
    private Alvo mirar(double mx, double my) {
        if (mx < vx0 || mx > vx1 || my < vy0 || my > vy1) return null;
        Matrix4f inv = modelo().invert();
        Vector3f o = inv.transformPosition(new Vector3f((float) mx, (float) my, 2000));
        Vector3f f = inv.transformPosition(new Vector3f((float) mx, (float) my, -2000));
        Vector3f d = new Vector3f(f).sub(o).normalize();
        // entra na grade
        float tMin = 0, tMax = Float.MAX_VALUE;
        float[] oo = {o.x, o.y, o.z}, dd = {d.x, d.y, d.z};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(dd[a]) < 1e-7) {
                if (oo[a] < 0 || oo[a] > Projeto.N) { tMin = 1; tMax = 0; }
                continue;
            }
            float t1 = (0 - oo[a]) / dd[a], t2 = (Projeto.N - oo[a]) / dd[a];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }
        if (tMin <= tMax) {
            float t = tMin + 1e-4f;
            int x = Mth.floor(oo[0] + dd[0] * t), y = Mth.floor(oo[1] + dd[1] * t), z = Mth.floor(oo[2] + dd[2] * t);
            int sx = dd[0] > 0 ? 1 : -1, sy = dd[1] > 0 ? 1 : -1, sz = dd[2] > 0 ? 1 : -1;
            float tdx = Math.abs(1 / dd[0]), tdy = Math.abs(1 / dd[1]), tdz = Math.abs(1 / dd[2]);
            float px = oo[0] + dd[0] * t, py = oo[1] + dd[1] * t, pz = oo[2] + dd[2] * t;
            float tx = (sx > 0 ? (x + 1 - px) : (px - x)) * tdx, ty = (sy > 0 ? (y + 1 - py) : (py - y)) * tdy, tz = (sz > 0 ? (z + 1 - pz) : (pz - z)) * tdz;
            int nx = 0, ny = 0, nz = 0;
            // normal de entrada: pela face da grade
            float e0 = Math.min(Math.min(Math.abs(px), Math.abs(px - Projeto.N)), 99);
            for (int passo = 0; passo < 64; passo++) {
                if (Projeto.dentro(x, y, z) && proj.tem(x, y, z)) return new Alvo(x, y, z, nx, ny, nz, true);
                // cruza a camada de trabalho antes de achar cubo: coloca nela
                if (tx < ty && tx < tz) { x += sx; tx += tdx; nx = -sx; ny = 0; nz = 0; }
                else if (ty < tz) { y += sy; ty += tdy; nx = 0; ny = -sy; nz = 0; }
                else { z += sz; tz += tdz; nx = 0; ny = 0; nz = -sz; }
                if (!Projeto.dentro(x, y, z)) break;
            }
        }
        // sem cubo: a casa da camada de trabalho (plano y = camada)
        if (Math.abs(d.y) > 1e-5) {
            float t = (camada + (d.y < 0 ? 1 : 0) - o.y) / d.y;
            float px = o.x + d.x * t, pz = o.z + d.z * t;
            int x = Mth.floor(px), z = Mth.floor(pz);
            if (Projeto.dentro(x, camada, z)) return new Alvo(x, camada, z, 0, 0, 0, false);
        }
        return null;
    }

    private void aplicar(Alvo a, boolean primeiroClique) {
        if (a == null) return;
        int[] onde = a.cheio() ? a.fora() : new int[]{a.x(), a.y(), a.z()};
        switch (ferramenta) {
            case LAPIS -> {
                if (!Projeto.dentro(onde[0], onde[1], onde[2])) return;
                if (proj.quantos() >= Projeto.MAX) { avisar(Component.translatable("oficina.lanterna.limite", Projeto.MAX)); return; }
                por(onde[0], onde[1], onde[2], tom + 1);
            }
            case BORRACHA -> { if (a.cheio()) por(a.x(), a.y(), a.z(), 0); }
            case PINTAR -> { if (a.cheio()) por(a.x(), a.y(), a.z(), tom + 1); }
            case CAIXA -> {
                if (!primeiroClique) return;
                int[] c = a.cheio() ? a.fora() : onde;
                if (!Projeto.dentro(c[0], c[1], c[2])) return;
                if (caixaInicio == null) { caixaInicio = c; return; }
                for (int x = Math.min(c[0], caixaInicio[0]); x <= Math.max(c[0], caixaInicio[0]); x++)
                    for (int y = Math.min(c[1], caixaInicio[1]); y <= Math.max(c[1], caixaInicio[1]); y++)
                        for (int z = Math.min(c[2], caixaInicio[2]); z <= Math.max(c[2], caixaInicio[2]); z++) por(x, y, z, tom + 1);
                caixaInicio = null;
            }
        }
    }

    private void por(int x, int y, int z, int v) {
        proj.set(x, y, z, v);
        if (espelho) proj.set(Projeto.N - 1 - x, y, z, v);
    }

    // ===================== Entrada =====================

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (super.mouseClicked(mx, my, b)) return true;
        if (mx < vx0 || mx > vx1 || my < vy0 || my > vy1) return false;
        if (b == 1 || b == 2) {
            girando = true;
            ultimoX = mx;
            ultimoY = my;
            return true;
        }
        if (b == 0) {
            if (testando) testando = false;
            Alvo a = mirar(mx, my);
            if (a == null) return true;
            guardar();
            pintando = ferramenta != Ferramenta.CAIXA;
            ultimoAlvo = "";
            aplicar(a, true);
            ultimoAlvo = a.x() + "," + a.y() + "," + a.z();
            if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.4f, 1.6f + tom * 0.1f);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (girando) {
            yaw += (float) (mx - ultimoX) * 0.8f;
            pitch = Mth.clamp(pitch + (float) (my - ultimoY) * 0.8f, -89, 89);
            ultimoX = mx;
            ultimoY = my;
            return true;
        }
        if (pintando && b == 0) {
            Alvo a = mirar(mx, my);
            if (a != null) {
                String k = a.x() + "," + a.y() + "," + a.z();
                if (!k.equals(ultimoAlvo)) {
                    // arrastando com o lápis: só na mesma camada da primeira, para não "subir" em cima do que acabou de pôr
                    if (ferramenta != Ferramenta.LAPIS || !a.cheio() || a.ny() == 0) aplicar(a, false);
                    ultimoAlvo = k;
                }
            }
            return true;
        }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        girando = false;
        pintando = false;
        return super.mouseReleased(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (mx >= vx0 && mx <= vx1 && my >= vy0 && my <= vy1) {
            zoom = Mth.clamp(zoom * (d > 0 ? 1.12f : 1 / 1.12f), 2.5f, 40f);
            return true;
        }
        return super.mouseScrolled(mx, my, d);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (nome != null && nome.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) { nome.setFocused(false); return true; }
            return super.keyPressed(key, scan, mods);
        }
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_Z) { desfazer(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_Y) { refazer(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_S) { salvar(); return true; }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) { tom = key - GLFW.GLFW_KEY_1; return true; }
        if (key == GLFW.GLFW_KEY_M) { espelho = !espelho; return true; }
        if (key == GLFW.GLFW_KEY_B) { ferramenta = Ferramenta.LAPIS; return true; }
        if (key == GLFW.GLFW_KEY_E) { ferramenta = Ferramenta.BORRACHA; return true; }
        if (key == GLFW.GLFW_KEY_P) { ferramenta = Ferramenta.PINTAR; return true; }
        if (key == GLFW.GLFW_KEY_C) { ferramenta = Ferramenta.CAIXA; caixaInicio = null; return true; }
        if (key == GLFW.GLFW_KEY_SPACE) { alternarTeste(); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_UP) { camada = Math.min(Projeto.N - 1, camada + 1); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_DOWN) { camada = Math.max(0, camada - 1); return true; }
        if (key == GLFW.GLFW_KEY_LEFT) { yaw -= 15; return true; }
        if (key == GLFW.GLFW_KEY_RIGHT) { yaw += 15; return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===================== Desenho =====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xE0080A0E);
        int cor = cor();
        Hud.panel(g, 2, 2, width - 4, 16, cor, 1f);
        g.drawString(font, Hud.title(title.copy()), 8, 6, Hud.argb(cor, 255), false);
        Hud.textRight(g, Hud.body(Component.translatable("oficina.lanterna.atalhos_curto")), width - 8, 6, 0xFF8A949C);
        Hud.panel(g, 2, 20, 128, height - 22, cor, 1f);
        Hud.panel(g, width - 162, 20, 160, height - 22, cor, 1f);
        g.fill(vx0 - 2, vy0 - 2, vx1 + 2, vy1, 0xFF0C1014);
        int esq = 6, dir = width - 158;
        g.drawString(font, Hud.caps(Component.translatable("oficina.lanterna.ferramentas")), esq, yFerr, 0xFF9AA8B0, false);
        g.drawString(font, Hud.caps(Component.translatable("oficina.lanterna.modelos")), esq, yModelos, 0xFF9AA8B0, false);
        g.drawString(font, Hud.caps(Component.translatable("oficina.lanterna.tons")), esq, yTons, 0xFF9AA8B0, false);
        for (int t = 0; t < 4; t++) {
            int x = esq + t * 29, y = yTons + 10;
            if (t == tom) g.fill(x - 2, y - 2, x + 26, y + 16, 0xFFFFFFFF);
            g.fill(x, y, x + 24, y + 14, 0xFF000000 | Malha.corTom(cor, t));
            Hud.textCenter(g, Hud.body(Component.literal(String.valueOf(t + 1))), x + 12, y + 3, t == 3 ? 0xFF202020 : 0xFFFFFFFF);
        }
        g.drawString(font, Hud.body(Component.translatable("oficina.lanterna.camada", camada)), esq, yTons + 30, 0xFFB8C4CC, false);
        g.drawString(font, Hud.caps(Component.translatable("oficina.lanterna.nome")), dir, yNome, 0xFF9AA8B0, false);
        g.drawString(font, Hud.caps(Component.translatable("oficina.lanterna.comportamento")), dir, yComp, 0xFF9AA8B0, false);
        Hud.wrapped(g, Hud.body(proj.comp.descricao()), dir, yDesc, 152, 0xFFB8C4CC, 9);
        Hud.textCenter(g, Hud.bold(Component.literal(String.format("%.2fx · %.1f m", proj.escala, alturaMundo()))), dir + 76, yEscala + 3, 0xFFFFFFFF);
        Projeto s = OficinaCliente.MEUS[slot];
        g.drawString(font, Hud.caps(Component.translatable("oficina.lanterna.espaco", slot + 1, s == null ? Component.translatable("oficina.lanterna.livre") : Component.literal(s.nome))), dir, yEspacos, 0xFF9AA8B0, false);
        g.drawString(font, Hud.caps(Component.translatable("oficina.lanterna.preset")), dir, yPreset, 0xFF9AA8B0, false);
        Hud.wrapped(g, Hud.body(Component.translatable("oficina.lanterna.info", proj.quantos(), Projeto.MAX, proj.custo(), proj.comp.recarga / 20)), dir, yInfo, 152, 0xFF8A949C, 9);

        // vista 3D
        g.enableScissor(vx0, vy0, vx1, vy1);
        desenharVista(g, mx, my, cor);
        g.disableScissor();
        super.render(g, mx, my, pt);
        if (aviso != null && System.currentTimeMillis() < avisoAte) {
            int w = font.width(aviso) + 16;
            g.pose().pushPose();
            g.pose().translate(0, 0, 800);
            Hud.panel(g, (vx0 + vx1) / 2 - w / 2, vy0 + 30, w, 18, cor, 1f);
            Hud.textCenter(g, Hud.body(aviso), (vx0 + vx1) / 2, vy0 + 35, 0xFFFFFFFF);
            g.pose().popPose();
        }
    }

    private float alturaMundo() {
        int[] b = proj.limites();
        if (b == null) return 0;
        return Math.max(Math.max(b[3] - b[0] + 1, b[4] - b[1] + 1), b[5] - b[2] + 1) * proj.bloco();
    }

    private void desenharVista(GuiGraphics g, int mx, int my, int cor) {
        Matrix4f base = new Matrix4f(g.pose().last().pose()).mul(modelo());
        float t = testando ? (System.currentTimeMillis() - testeInicio) / 50f : 999;
        float laco = testando ? t % 50 : 999;
        Matrix4f m = new Matrix4f(base);
        float mostra = testando ? Mth.clamp(laco / 10f, 0, 1) * 1.05f : 2f;
        if (testando) animar(m, laco);
        RenderSystem.enableDepthTest();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // chão (grade) e a camada de trabalho
        chao(bb, base, 0, 0x55FFFFFF);
        if (camada > 0 && !testando) chao(bb, base, camada, Hud.argb(cor, 70));
        seta(bb, base, cor);
        // os cubinhos
        Malha malha = Malha.de(proj);
        for (Malha.Face f : malha.faces) {
            if (f.ordem() > mostra) continue;
            int c = Malha.corTom(cor, f.tom());
            float sombra = f.d() == 3 ? 1f : f.d() == 2 ? 0.55f : (f.d() == 4 || f.d() == 5) ? 0.82f : 0.7f;
            int cc = 0xFF000000 | ((int) (((c >> 16) & 255) * sombra) << 16) | ((int) (((c >> 8) & 255) * sombra) << 8) | (int) ((c & 255) * sombra);
            float[][] q = Malha.cantos(f.x(), f.y(), f.z(), f.d());
            quad(bb, m, q, cc);
            // linhas de cada cubinho (fracas) e das bordas da forma (fortes)
            for (int i = 0; i < 4; i++) {
                boolean bordaForma = (f.bordas() & (1 << i)) != 0;
                float w = bordaForma ? 0.1f : 0.04f;
                int lc = bordaForma ? Hud.argb(com.lanterna.client.construct.EnergyShapes.clarear(cor, 0.6f), 255) : 0x40000000;
                float[] p0 = q[i], p1 = q[(i + 1) % 4];
                float[] mid = {(q[0][0] + q[2][0]) / 2, (q[0][1] + q[2][1]) / 2, (q[0][2] + q[2][2]) / 2};
                float[] a0 = lerp(p0, mid, w * 2), a1 = lerp(p1, mid, w * 2);
                float[] n = {Malha.DIR[f.d()][0] * 0.002f, Malha.DIR[f.d()][1] * 0.002f, Malha.DIR[f.d()][2] * 0.002f};
                quad(bb, m, new float[][]{add(p0, n), add(p1, n), add(a1, n), add(a0, n)}, lc);
            }
        }
        // o cubinho fantasma debaixo do mouse
        if (!testando) {
            Alvo a = mirar(mx, my);
            if (a != null) {
                int[] c = ferramenta == Ferramenta.BORRACHA || ferramenta == Ferramenta.PINTAR ? (a.cheio() ? new int[]{a.x(), a.y(), a.z()} : null)
                        : (a.cheio() ? a.fora() : new int[]{a.x(), a.y(), a.z()});
                if (c != null && Projeto.dentro(c[0], c[1], c[2])) {
                    int gc = ferramenta == Ferramenta.BORRACHA ? 0x90FF4040 : ferramenta == Ferramenta.PINTAR ? 0x90FFFF60 : 0x70FFFFFF;
                    cubo(bb, base, c[0], c[1], c[2], c[0] + 1, c[1] + 1, c[2] + 1, gc, 0.03f);
                    if (espelho && ferramenta != Ferramenta.CAIXA) cubo(bb, base, Projeto.N - 1 - c[0], c[1], c[2], Projeto.N - c[0], c[1] + 1, c[2] + 1, 0x40FFFFFF, 0.03f);
                    if (ferramenta == Ferramenta.CAIXA && caixaInicio != null) {
                        cubo(bb, base, Math.min(c[0], caixaInicio[0]), Math.min(c[1], caixaInicio[1]), Math.min(c[2], caixaInicio[2]),
                                Math.max(c[0], caixaInicio[0]) + 1, Math.max(c[1], caixaInicio[1]) + 1, Math.max(c[2], caixaInicio[2]) + 1, 0x40FFFFFF, 0.03f);
                    }
                }
            }
        }
        BufferUploaderDraw.desenhar(bb);
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        // rótulos (frente, mão, ...)
        rotulo(g, base, 8, 0, -2.4f, Component.translatable("oficina.lanterna.frente"), cor);
        String dica = switch (proj.comp) {
            case ARMA -> "oficina.lanterna.dica.arma";
            case QUEDA -> "oficina.lanterna.dica.queda";
            case VEICULO -> "oficina.lanterna.dica.veiculo";
            case PROJETIL -> "oficina.lanterna.dica.projetil";
            default -> null;
        };
        if (proj.comp == Comportamento.ARMA) rotulo(g, base, 8, -0.8f, 8, Component.translatable("oficina.lanterna.mao"), 0xFFFFFF);
        if (dica != null) Hud.wrapped(g, Hud.body(Component.translatable(dica)), vx0 + 6, vy0 + 6, vx1 - vx0 - 12, 0xFF9AA8B0, 9);
        Hud.wrapped(g, Hud.body(Component.translatable("oficina.lanterna.atalhos")), vx0 + 6, vy1 - 30, vx1 - vx0 - 12, 0xFF6A747C, 9);
        if (testando) Hud.textCenter(g, Hud.bold(Component.translatable("oficina.lanterna.testando")), (vx0 + vx1) / 2, vy1 - 42, Hud.argb(cor, 255));
        else if (ferramenta == Ferramenta.CAIXA) Hud.textCenter(g, Hud.body(Component.translatable(caixaInicio == null ? "oficina.lanterna.caixa1" : "oficina.lanterna.caixa2")), (vx0 + vx1) / 2, vy1 - 42, 0xFFB8C4CC);
    }

    /** Animação do teste, no espaço do desenho (unidades de cubinho). */
    private void animar(Matrix4f m, float t) {
        int[] b = proj.limites();
        float h = b == null ? 8 : b[4] + 1;
        switch (proj.comp) {
            case ARMA -> {
                float g = (t - 14) % 18;
                float arco = 0, deita = -35;
                if (t > 14 && g < 1.5f) { arco = Mth.lerp(g / 1.5f, 0, -80); deita = -50; }
                else if (t > 14 && g < 4.5f) { float k = (g - 1.5f) / 3f; arco = Mth.lerp(k, -80, 72); deita = Mth.lerp(k, -50, -88); }
                else if (t > 14 && g < 11) { float k = (g - 4.5f) / 6.5f; arco = Mth.lerp(k, 72, 0); deita = Mth.lerp(k, -88, -35); }
                m.translate(8, 0, 8).rotateY((float) Math.toRadians(arco)).rotateX((float) Math.toRadians(deita)).translate(-8, 0, -8);
            }
            case ESCUDO -> {
                float p = 1 + 0.03f * Mth.sin(t * 0.25f);
                m.translate(8, h / 2, 8).scale(p).translate(-8, -h / 2, -8);
            }
            case PROJETIL -> m.translate(8, h / 2, 8).rotateZ(t * 0.4f).translate(-8, -h / 2, -8).translate(0, 0, -((t * 0.8f) % 20) + 6);
            case QUEDA -> {
                float cai = Mth.clamp((t - 10) / 12f, 0, 1);
                float y = (1 - cai * cai) * 24;
                m.translate(0, y, 0).translate(8, h, 8).rotateX((float) Math.PI).translate(-8, 0, -8);
                if (cai >= 1) {
                    float sq = 1 - 0.15f * Mth.clamp(1 - (t - 22) / 6f, 0, 1);
                    m.translate(8, 0, 8).scale(1 / sq, sq, 1 / sq).translate(-8, 0, -8);
                }
            }
            case VEICULO -> m.translate(8, 0, 8).rotateZ((float) Math.toRadians(Mth.sin(t * 0.15f) * 6)).translate(-8, Mth.sin(t * 0.3f) * 0.2f, -8);
            case ESTRUTURA -> {
                float s = Mth.clamp(t / 12f, 0, 1);
                m.translate(0, -(1 - s * s * (3 - 2 * s)) * h, 0);
            }
        }
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float[][] q, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        for (float[] p : q) bb.vertex(m, p[0], p[1], p[2]).color(r, g, b, a).endVertex();
    }

    private static void cubo(BufferBuilder bb, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int argb, float e) {
        x0 -= e; y0 -= e; z0 -= e; x1 += e; y1 += e; z1 += e;
        quad(bb, m, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, argb);
        quad(bb, m, new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, argb);
        quad(bb, m, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}}, argb);
        quad(bb, m, new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, argb);
        quad(bb, m, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, argb);
        quad(bb, m, new float[][]{{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}}, argb);
    }

    private static void chao(BufferBuilder bb, Matrix4f m, int y, int argb) {
        float w = 0.04f;
        for (int i = 0; i <= Projeto.N; i++) {
            int c = (i == 8) ? (argb | 0x30000000) : argb;
            quad(bb, m, new float[][]{{i - w, y, 0}, {i + w, y, 0}, {i + w, y, Projeto.N}, {i - w, y, Projeto.N}}, c);
            quad(bb, m, new float[][]{{0, y, i - w}, {Projeto.N, y, i - w}, {Projeto.N, y, i + w}, {0, y, i + w}}, c);
        }
    }

    /** Seta no chão apontando para a frente do desenho (-Z). */
    private static void seta(BufferBuilder bb, Matrix4f m, int cor) {
        int c = Hud.argb(cor, 200);
        quad(bb, m, new float[][]{{7.4f, 0.01f, -0.3f}, {8.6f, 0.01f, -0.3f}, {8.6f, 0.01f, -1.6f}, {7.4f, 0.01f, -1.6f}}, c);
        quad(bb, m, new float[][]{{6.5f, 0.01f, -1.6f}, {9.5f, 0.01f, -1.6f}, {8.05f, 0.01f, -3.0f}, {7.95f, 0.01f, -3.0f}}, c);
    }

    private void rotulo(GuiGraphics g, Matrix4f m, float x, float y, float z, Component c, int cor) {
        Vector3f p = m.transformPosition(new Vector3f(x, y, z));
        if (p.x < vx0 || p.x > vx1 || p.y < vy0 || p.y > vy1) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 900);
        Hud.textCenter(g, Hud.bold(c), (int) p.x, (int) p.y - 4, Hud.argb(cor, 255));
        g.pose().popPose();
    }

    // ===================== Botões =====================

    private static class Botao extends AbstractButton {
        private final BooleanSupplier ativo;
        private final Runnable acao;

        Botao(int x, int y, int w, int h, Component msg, BooleanSupplier ativo, Runnable acao) {
            super(x, y, w, h, msg);
            this.ativo = ativo;
            this.acao = acao;
        }

        protected int corExtra() {
            return 0;
        }

        @Override
        public void onPress() {
            acao.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int cor = ClientRingData.hasRing ? ClientRingData.color : 0x40FF70;
            boolean on = ativo.getAsBoolean();
            boolean hov = isHoveredOrFocused();
            int fundo = on ? Hud.argb(cor, 200) : hov ? 0xC0303840 : 0xB0181C22;
            g.fill(getX(), getY(), getX() + width, getY() + height, fundo);
            int borda = on ? 0xFFFFFFFF : hov ? Hud.argb(cor, 255) : Hud.argb(cor, 110);
            g.fill(getX(), getY(), getX() + width, getY() + 1, borda);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borda);
            g.fill(getX(), getY(), getX() + 1, getY() + height, borda);
            g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borda);
            int extra = corExtra();
            if (extra != 0) g.fill(getX() + 2, getY() + height - 3, getX() + width - 2, getY() + height - 2, extra);
            Hud.textCenter(g, Hud.body(getMessage()), getX() + width / 2, getY() + (height - 8) / 2, on ? 0xFF101418 : 0xFFE0E6EA);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** Envia o que foi montado no BufferBuilder. */
    private static final class BufferUploaderDraw {
        static void desenhar(BufferBuilder bb) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
        }
    }
}

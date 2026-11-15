package com.lanterna.client.flight;

import com.lanterna.space.Perigos;
import com.lanterna.space.ProceduralPlanets;
import com.lanterna.space.SpaceBodies;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 0.52.3: a rota do piloto automático no espaço. Em vez de ir reto e frear perto de cada planeta, sol ou buraco negro,
 * o piloto traça antes um caminho que contorna cada um com a folga exata em que a hiperpropulsão começaria a frear
 * (que cresce com a velocidade do anel), e assim mantém a velocidade máxima a viagem toda.
 *
 * Tudo calculado no próprio jogo de quem voa (os planetas e perigos gerados são os mesmos para todos), sem pesar no
 * servidor.
 */
public final class Rota {
    /** Um obstáculo: esfera que a rota não pode cortar. */
    private record Obstaculo(String id, Vec3 c, double r) {}

    private static final int MAX_PONTOS = 14;

    /**
     * Pontos da rota de {@code de} até o destino (o último é o centro do destino). velBloco = velocidade máxima da
     * propulsão em blocos por tick (a folga dos freios depende dela).
     */
    public static List<Vec3> tracar(Vec3 de, Vec3 centro, String destino, double velBloco) {
        List<Obstaculo> obs = obstaculos(de, centro, destino, velBloco);
        List<Vec3> pontos = new ArrayList<>();
        dividir(de, centro, obs, pontos, 0, destino, velBloco);
        pontos.add(centro);
        return pontos;
    }

    /** Divide o trecho A→B em volta do primeiro obstáculo que ele corta (recursivo, com limite). */
    private static void dividir(Vec3 a, Vec3 b, List<Obstaculo> obs, List<Vec3> saida, int fundo, String destino, double vel) {
        if (fundo > 5 || saida.size() >= MAX_PONTOS) return;
        Obstaculo bate = null;
        double primeiro = Double.MAX_VALUE;
        Vec3 ab = b.subtract(a);
        double len = ab.length();
        if (len < 1) return;
        Vec3 dir = ab.scale(1 / len);
        for (Obstaculo o : obs) {
            // quem já está dentro da folga (saindo de um planeta) ou o próprio ponto final não contam
            if (a.distanceTo(o.c) < o.r || b.distanceTo(o.c) < o.r) continue;
            double along = o.c.subtract(a).dot(dir);
            if (along <= 0 || along >= len) continue;
            double miss = a.add(dir.scale(along)).distanceTo(o.c);
            if (miss < o.r && along < primeiro) { primeiro = along; bate = o; }
        }
        if (bate == null) return;
        // o desvio: do ponto mais perto do centro, para fora, um pouco além da folga
        Vec3 perto = a.add(dir.scale(primeiro));
        // contorna pelos lados (o espaço tem teto e chão), na altura em que a rota passaria
        Vec3 fora = new Vec3(perto.x - bate.c.x, 0, perto.z - bate.c.z);
        if (fora.lengthSqr() < 1) {
            fora = dir.cross(new Vec3(0, 1, 0));
            fora = new Vec3(fora.x, 0, fora.z);
            if (fora.lengthSqr() < 1e-6) fora = new Vec3(1, 0, 0);
        }
        Vec3 h = fora.normalize().scale(bate.r * 1.25 + 400);
        Vec3 w = new Vec3(bate.c.x + h.x, Math.max(-380, Math.min(1380, perto.y)), bate.c.z + h.z);
        // novos obstáculos perto do desvio também contam
        for (Obstaculo n : obstaculos(w, w, destino, vel)) if (obs.stream().noneMatch(x -> x.id.equals(n.id))) obs.add(n);
        dividir(a, w, obs, saida, fundo + 1, destino, vel);
        saida.add(w);
        dividir(w, b, obs, saida, fundo + 1, destino, vel);
    }

    /** Planetas, sóis e buracos negros ao longo do caminho A→B (setores de 8.000 blocos). */
    private static List<Obstaculo> obstaculos(Vec3 a, Vec3 b, String destino, double vel) {
        List<Obstaculo> l = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        double len = a.distanceTo(b);
        int passos = (int) Math.max(1, Math.min(200, Math.ceil(len / ProceduralPlanets.SECTOR)));
        for (int i = 0; i <= passos; i++) {
            Vec3 q = a.lerp(b, i / (double) passos);
            for (SpaceBodies.Body c : SpaceBodies.near(q, 1)) {
                if (!c.landable() || c.id().equals(destino) || !vistos.add("b" + c.id())) continue;
                // a hiperpropulsão freia quando a distância à superfície é menor que 14 x a velocidade
                l.add(new Obstaculo("b" + c.id(), c.center(), c.radius() + 14 * vel));
            }
            for (Perigos.Perigo p : Perigos.near(q, 1)) {
                if (!vistos.add("p" + p.id())) continue;
                // perto do perigo ela freia (distância / 12) dentro do alcance da puxada
                l.add(new Obstaculo("p" + p.id(), p.centro(), p.zonaDano() + Math.min(p.alcance(), 12 * vel) + 150));
            }
        }
        return l;
    }

    private Rota() {}
}

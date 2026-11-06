package com.lanterna;

/**
 * Câmera lenta para gravar vídeos no cliente de teste (o servidor e o relógio do cliente andam FATOR vezes mais devagar).
 * Fica em 1 no jogo normal: não muda nada.
 */
public final class Lento {
    public static volatile int FATOR = 1;
    private Lento() {}
}

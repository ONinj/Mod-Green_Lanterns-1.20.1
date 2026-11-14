package com.lanterna.ia;

/**
 * O que o jogador quer dizer, em português ou inglês (os dois funcionam em qualquer idioma do jogo).
 * Usado pelo anel (chat e voz) e pelos NPCs.
 */
public enum Intencao {
    VIAJAR, PARAR, CARGA, NIVEL, MISSAO, ONDE, DISTANCIA, PERIGO, LIGAR, DESLIGAR, VOAR, POUSAR, JURAMENTO, QUEM,
    AJUDA, DUVIDA, OI, TCHAU, OBRIGADO, SOBRE, ELOGIO, INSULTO, TROCA, COMO_VAI, PIADA, NADA;

    /** Intenção + o resto do texto (o destino, o assunto...). */
    public record Lida(Intencao intencao, String resto) {}

    private static final String[] VIAJAR_PT = {"me leve para", "me leve pra", "me leva para", "me leva pra", "me leve ate", "me leva ate",
            "leve me para", "leve me ate", "leva me para", "me leve a", "me leve ao", "me leve", "me leva", "leve me", "levar para", "leve para",
            "va para", "vai para", "va pra", "vai pra", "ir para", "ir pra", "voe para", "voa para", "voar para", "voe ate", "voar ate",
            "rumo a", "rumo ao", "siga para", "segue para", "piloto automatico para", "piloto automatico ate", "piloto automatico",
            "viajar para", "viaje para", "viagem para", "quero ir para", "quero ir pra", "quero ir a", "quero ir ao", "partir para", "partiu"};
    private static final String[] VIAJAR_EN = {"take me to", "take me", "bring me to", "fly me to", "fly to", "go to", "travel to",
            "head to", "set course to", "set course for", "course to", "autopilot to", "autopilot", "navigate to", "i want to go to",
            "lets go to", "let's go to", "warp to", "jump to"};

    private static final String[] COMO_PT = {"faco", "faz", "fazer", "crafto", "craftar", "crafta", "craftando", "fabrico", "fabricar",
            "fabrica", "consigo", "conseguir", "consegue", "pego", "pegar", "obtenho", "obter", "ganho", "ganhar", "chamo", "chamar",
            "completo", "completar", "termino", "terminar", "passo", "passar", "uso", "usar", "funciona", "funcionam", "recarrego",
            "recarregar", "abro", "abrir", "acho", "achar", "encontro", "encontrar", "chego", "chegar", "derroto", "derrotar", "mato",
            "matar", "libero", "liberar", "desbloqueio", "desbloquear", "subo", "subir", "escolho", "escolher", "entro", "entrar",
            "viro", "virar", "forjo", "forjar", "monto", "montar", "coloco", "colocar", "voo", "troco", "trocar", "compro", "comprar",
            "crio", "criar", "jogo", "jogar", "comeco", "comecar", "ativo", "ativar", "melhoro", "melhorar", "invoco", "invocar", "acesso",
            "acessar", "sobrevivo", "sobreviver", "respiro", "respirar", "pilota", "pilotar", "piloto", "evoluo", "evoluir", "funciona",
            "arrumo", "arrumar", "arranjo", "arranjar", "descolo", "adquiro", "adquirir", "tenho", "ter", "tiro", "tirar", "boto", "botar",
            "ponho", "por", "vou", "ir", "volto", "voltar", "salvo", "salvar", "conserto", "consertar", "curo", "curar", "mudo", "mudar",
            "troca", "upo", "upar", "farmo", "farmar", "acho", "descubro", "descobrir", "domo", "domar", "falo", "falar", "chamo"};
    private static final String[] ONDE_PT = {"acho", "encontro", "fica", "ficam", "consigo", "pego", "compro", "mora", "moram", "nasce", "nascem", "tem"};

    /** "como eu faço...", "onde acho...", "receita de...", "how do I...", "where can I find..." */
    public static boolean duvida(String t) {
        if (Texto.tem(t, "receita", "receitas", "craft", "craftar", "crafto", "fabricar", "recipe", "recipes", "crafting", "duvida", "tutorial",
                "me ensina", "me ensine", "me explica", "me explique", "explica como", "explique como", "pra que serve", "para que serve",
                "how do i", "how do you", "how can i", "how to", "how does", "where do i", "where can i", "where is the", "where are the",
                "where do you", "what does", "teach me", "explain")) return true;
        java.util.List<String> w = Texto.palavras(t);
        for (int i = 0; i + 1 < w.size(); i++) {
            String a = w.get(i);
            String b = w.get(i + 1).equals("eu") || w.get(i + 1).equals("se") || w.get(i + 1).equals("que") || w.get(i + 1).equals("da") || w.get(i + 1).equals("posso")
                    ? (i + 2 < w.size() ? w.get(i + 2) : "") : w.get(i + 1);
            if (a.equals("como") && java.util.Arrays.asList(COMO_PT).contains(b)) return true;
            if (a.equals("onde") && java.util.Arrays.asList(ONDE_PT).contains(b)) return true;
        }
        return false;
    }

    public static Lida ler(String limpo) {
        return ler(limpo, true);
    }

    /** comDuvida = false: lê como pedido comum (quando o anel não achou resposta para a dúvida). */
    public static Lida ler(String limpo, boolean comDuvida) {
        String t = limpo;
        // viagem primeiro: "me leve para X"
        String resto = Texto.depoisDe(t, VIAJAR_PT);
        if (resto == null) resto = Texto.depoisDe(t, VIAJAR_EN);
        if (resto != null && !resto.isBlank()) return new Lida(VIAJAR, resto);
        // dúvida sobre o jogo: "como faço a bateria", "receita do anel vazio", "how do i get the green ring"
        if (comDuvida && duvida(t)) return new Lida(DUVIDA, t);
        if (Texto.tem(t, "pare", "parar", "cancela", "cancelar", "cancele", "abortar", "aborta", "stop", "cancel", "abort", "halt",
                "chega", "desliga o piloto", "desligar o piloto")
                && Texto.palavras(t).size() <= 5) return new Lida(PARAR, "");
        if (Texto.tem(t, "distancia", "quao longe", "quanto falta", "how far", "distance", "longe fica", "longe esta")) {
            String r = Texto.depoisDe(t, "ate", "para", "de", "to", "from", "is", "fica", "esta");
            return new Lida(DISTANCIA, r == null ? "" : r);
        }
        if (Texto.tem(t, "carga", "bateria", "energia", "quanto de poder", "charge", "battery", "energy", "power level", "how much power"))
            return new Lida(CARGA, "");
        if (Texto.tem(t, "nivel", "level", "xp", "experiencia", "experience")) return new Lida(NIVEL, "");
        if (Texto.tem(t, "missao", "missoes", "objetivo", "o que fazer", "o que eu faco", "o que faco", "proximo passo", "mission", "missions",
                "quest", "objective", "what should i do", "what now", "what next", "next step")) return new Lida(MISSAO, "");
        if (Texto.tem(t, "onde estou", "onde eu estou", "onde a gente esta", "localizacao", "coordenadas", "where am i", "where are we",
                "location", "coordinates")) return new Lida(ONDE, "");
        if (Texto.tem(t, "perigo", "perigos", "ameaca", "ameacas", "inimigo", "inimigos", "monstro", "monstros", "danger", "threat",
                "threats", "enemy", "enemies", "hostiles", "scan", "escaneie", "escanear", "varredura")) return new Lida(PERIGO, "");
        if (Texto.tem(t, "desligar", "desliga", "desligue", "apagar", "apaga", "apague", "power off", "turn off", "deactivate", "shut down"))
            return new Lida(DESLIGAR, "");
        if (Texto.tem(t, "ligar", "liga", "ligue", "ativar", "ativa", "ative", "acender", "acende", "power on", "turn on", "activate"))
            return new Lida(LIGAR, "");
        if (Texto.tem(t, "pousar", "pouse", "pousa", "aterrissar", "descer", "land", "touch down")) return new Lida(POUSAR, "");
        if (Texto.tem(t, "voar", "voe", "voa", "decolar", "decole", "fly", "take off", "lift off")) return new Lida(VOAR, "");
        if (Texto.tem(t, "juramento", "jura", "recite", "recitar", "oath", "pledge", "recite")) return new Lida(JURAMENTO, "");
        if (Texto.tem(t, "quem e voce", "quem voce e", "quem e vc", "o que e voce", "qual seu nome", "qual o seu nome", "como voce se chama",
                "seu nome", "who are you", "what are you", "your name", "who is this", "quem e")) return new Lida(QUEM, "");
        if (Texto.tem(t, "ajuda", "socorro", "comandos", "o que voce faz", "o que voce sabe", "o que voce pode", "help", "commands",
                "what can you do", "what do you do")) return new Lida(AJUDA, "");
        if (Texto.tem(t, "como vai", "tudo bem", "como voce esta", "como esta", "beleza", "how are you", "how are things", "whats up", "what's up", "what is up", "sup"))
            return new Lida(COMO_VAI, "");
        if (Texto.tem(t, "piada", "conte uma", "me faca rir", "joke", "make me laugh", "tell me something funny")) return new Lida(PIADA, "");
        if (Texto.tem(t, "troca", "trocar", "trocas", "comprar", "vender", "negociar", "loja", "trade", "trades", "buy", "sell", "shop", "deal"))
            return new Lida(TROCA, "");
        String sobre = Texto.depoisDe(t, "fale sobre", "fala sobre", "me fale de", "me fale sobre", "me conte sobre", "conte sobre",
                "o que e", "o que sabe sobre", "o que voce sabe sobre", "tell me about", "what is", "what's", "whats", "info on", "about");
        if (sobre != null && !sobre.isBlank()) return new Lida(SOBRE, sobre);
        if (Texto.tem(t, "obrigado", "obrigada", "valeu", "vlw", "brigado", "agradeco", "thanks", "thank you", "thx", "cheers"))
            return new Lida(OBRIGADO, "");
        if (Texto.tem(t, "tchau", "adeus", "ate mais", "ate logo", "falou", "bye", "goodbye", "see you", "later", "farewell"))
            return new Lida(TCHAU, "");
        if (Texto.tem(t, "idiota", "burro", "burra", "inutil", "lixo", "otario", "imbecil", "estupido", "estupida", "feio", "feia",
                "idiot", "stupid", "useless", "trash", "dumb", "moron", "ugly", "shut up", "cala a boca", "cala boca"))
            return new Lida(INSULTO, "");
        if (Texto.tem(t, "incrivel", "lindo", "linda", "bonito", "bonita", "legal", "maneiro", "o melhor", "a melhor", "gosto de voce",
                "te amo", "parabens", "amazing", "awesome", "beautiful", "cool", "the best", "i like you", "love you", "great job", "well done"))
            return new Lida(ELOGIO, "");
        if (Texto.tem(t, "oi", "ola", "ei", "eai", "e ai", "salve", "bom dia", "boa tarde", "boa noite", "hello", "hi", "hey", "greetings",
                "good morning", "good evening", "yo")) return new Lida(OI, "");
        return new Lida(NADA, t);
    }
}

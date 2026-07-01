package br.ufla.autotarget;

import java.util.List;

/**
 * Classe que representa um canhão no jogo.
 * Opera via Runnable em Pool de Threads (AV4).
 */
public class Canhao extends EntidadeMovel {
    private final int campo;
    private volatile int intervaloDisparo = 1000; // ms
    private static final int INTERVALO_BASE = 1000;
    public static final int MAX_CANHOES = 5;
    public static final int LIMITE_SEM_PENALIDADE = 1;

    // Campos para realocação (AV2)
    private volatile double targetX;
    private volatile double targetY;
    private static final double VELOCIDADE_REALOCACAO = 3.0;
    private static final double TOLERANCIA_MOVIMENTO = 3.0;

    // Controle térmico (AV3 - CPS)
    private volatile boolean superaquecido = false;
    private static final int PENALIDADE_TERMICA = 1000;

    private long ultimoTiro = 0;

    public Canhao(double x, double y, Jogo jogo, int campo) {
        super(x, y, 0, jogo.getScreenWidth(), jogo.getScreenHeight(), jogo);
        this.campo = campo;
        this.targetX = x;
        this.targetY = y;
        this.ativo = true;
    }

    public int getCampo() { return campo; }

    /**
     * Ciclo de execução do canhão (AV4).
     */
    @Override
    public void run() {
        if (!ativo) return;
        try {
            mover();

            long agora = System.currentTimeMillis();
            if (agora - ultimoTiro >= intervaloDisparo) {
                if (jogo.isRodando() && jogo.temEnergia(this.campo)) {
                    atirar();
                    ultimoTiro = agora;
                }
            }
        } catch (Exception e) {
            // Silencioso no pool
        }
    }

    public void setSuperaquecido(boolean quente) {
        this.superaquecido = quente;
        atualizarPenalidade(jogo.getCanhoesPorCampo(this.campo).size());
    }

    public void atualizarPenalidade(int totalCanhoes) {
        int base = (totalCanhoes > LIMITE_SEM_PENALIDADE) ? 
                INTERVALO_BASE + (totalCanhoes - LIMITE_SEM_PENALIDADE) * 500 : 
                INTERVALO_BASE;
        this.intervaloDisparo = base + (superaquecido ? PENALIDADE_TERMICA : 0);
    }

    public void setPosicaoAlvo(double x, double y) {
        if (campo == 0) {
            this.targetX = Math.max(30, Math.min(x, screenWidth / 2.0 - 30));
        } else {
            this.targetX = Math.max(screenWidth / 2.0 + 30, Math.min(x, screenWidth - 30));
        }
        this.targetY = Math.max(90, Math.min(y, screenHeight - 60));
    }

    @Override
    public void mover() {
        double dist = calcularDistancia(this.x, this.y, targetX, targetY);
        if (dist > TOLERANCIA_MOVIMENTO) { 
            double dx = (targetX - this.x) / dist;
            double dy = (targetY - this.y) / dist;
            this.x += dx * Math.min(VELOCIDADE_REALOCACAO, dist);
            this.y += dy * Math.min(VELOCIDADE_REALOCACAO, dist);
        }
    }

    /**
     * Busca o alvo mais próximo no campo do canhão e cria um projétil em sua direção.
     */
    private void atirar() {
        List<Alvo> alvos = jogo.getAlvos();
        Alvo alvoAlvo = null;
        double minDist = Double.MAX_VALUE;

        for (Alvo a : alvos) {
            if (a.getCampo() == this.campo && a.isAtivo()) {
                double dist = calcularDistancia(this.x, this.y, a.getX(), a.getY());
                if (dist < minDist) {
                    minDist = dist;
                    alvoAlvo = a;
                }
            }
        }

        if (alvoAlvo != null) {
            // Calcula direção (unit vector)
            double dist = calcularDistancia(this.x, this.y, alvoAlvo.getX(), alvoAlvo.getY());
            if (dist > 0) {
                double dx = (alvoAlvo.getX() - this.x) / dist;
                double dy = (alvoAlvo.getY() - this.y) / dist;

                // Cria projétil em direção ao alvo
                jogo.dispararProjetil(this.x, this.y, dx, dy, this.campo);
            }
        }
    }

    /**
     * Método público estático para ser testável via JUnit.
     */
    public static double calcularDistancia(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}

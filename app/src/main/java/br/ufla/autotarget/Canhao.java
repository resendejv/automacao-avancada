package br.ufla.autotarget;

import java.util.List;

/**
 * Classe que representa um canhão no jogo.
 * Cada canhão roda em sua própria thread, buscando e disparando em alvos.
 * Possui um intervalo de disparo que aumenta conforme o número de canhões no campo.
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
    private static final double VELOCIDADE_REALOCACAO = 3.0; // Velocidade reduzida para movimento mais natural
    private static final double TOLERANCIA_MOVIMENTO = 3.0; // Evita o "tremor" ao chegar no destino

    // Controle térmico (AV3 - CPS)
    private volatile boolean superaquecido = false;
    private static final int PENALIDADE_TERMICA = 1000; // +1s de sleep se quente

    public Canhao(double x, double y, Jogo jogo, int campo) {
        super(x, y, 0, jogo.getScreenWidth(), jogo.getScreenHeight(), jogo);
        this.campo = campo;
        this.targetX = x;
        this.targetY = y;
        this.ativo = true;
    }

    public int getCampo() { return campo; }

    /**
     * Atualiza o estado térmico do canhão (AV3).
     */
    public void setSuperaquecido(boolean quente) {
        this.superaquecido = quente;
        // Força atualização imediata do intervalo
        atualizarPenalidade(jogo.getCanhoesPorCampo(this.campo).size());
    }

    /**
     * Atualiza o intervalo de disparo com base no número de canhões no campo.
     */
    public void atualizarPenalidade(int totalCanhoes) {
        int base = (totalCanhoes > LIMITE_SEM_PENALIDADE) ? 
                INTERVALO_BASE + (totalCanhoes - LIMITE_SEM_PENALIDADE) * 500 : 
                INTERVALO_BASE;
        
        // AV3: Aplica atraso extra (arrefecimento) se o sistema estiver superaquecido
        this.intervaloDisparo = base + (superaquecido ? PENALIDADE_TERMICA : 0);
    }

    /**
     * Define a nova posição alvo para o canhão se mover (AV2).
     */
    public void setPosicaoAlvo(double x, double y) {
        // Garante que o canhão permaneça no seu campo
        if (campo == 0) {
            this.targetX = Math.max(30, Math.min(x, screenWidth / 2.0 - 30));
        } else {
            this.targetX = Math.max(screenWidth / 2.0 + 30, Math.min(x, screenWidth - 30));
        }
        this.targetY = Math.max(90, Math.min(y, screenHeight - 60));
    }

    /**
     * Move o canhão gradualmente em direção ao target (AV2).
     */
    @Override
    public void mover() {
        double dist = calcularDistancia(this.x, this.y, targetX, targetY);
        
        // Só move se a distância for maior que a tolerância para evitar tremor
        if (dist > TOLERANCIA_MOVIMENTO) { 
            double dx = (targetX - this.x) / dist;
            double dy = (targetY - this.y) / dist;
            
            // Aplica o movimento limitado pela velocidade
            this.x += dx * Math.min(VELOCIDADE_REALOCACAO, dist);
            this.y += dy * Math.min(VELOCIDADE_REALOCACAO, dist);
        }
    }

    /**
     * Loop do canhão: busca o alvo mais próximo no seu campo e dispara.
     */
    @Override
    public void run() {
        long ultimoTiro = 0;
        while (ativo) {
            try {
                // AV2: Além de atirar, o canhão agora pode se mover para realocação
                mover();

                Thread.sleep(30); // ~33 FPS para movimento suave
                
                long agora = System.currentTimeMillis();
                if (agora - ultimoTiro >= intervaloDisparo) {
                    if (jogo.isRodando() && ativo && jogo.temEnergia(this.campo)) {
                        atirar();
                        ultimoTiro = agora;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
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
                Projetil p = new Projetil(this.x, this.y, dx, dy, jogo, this.campo);
                jogo.adicionarProjetil(p);
                p.start();
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

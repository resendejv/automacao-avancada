package br.ufla.autotarget;

import java.util.List;

/**
 * Classe que representa um canhão no jogo.
 * Cada canhão roda em sua própria thread, buscando e disparando em alvos.
 * Possui um intervalo de disparo que aumenta conforme o número de canhões no campo.
 */
public class Canhao extends EntidadeMovel {
    private final int campo;
    private int intervaloDisparo = 1000; // ms
    private static final int INTERVALO_BASE = 1000;
    public static final int MAX_CANHOES = 5;
    public static final int LIMITE_SEM_PENALIDADE = 1;

    public Canhao(double x, double y, Jogo jogo, int campo) {
        super(x, y, 0, jogo.getScreenWidth(), jogo.getScreenHeight(), jogo);
        this.campo = campo;
        this.ativo = true;
    }

    public int getCampo() { return campo; }

    /**
     * Atualiza o intervalo de disparo com base na penalidade de canhões no campo.
     * @param totalCanhoesNoCampo quantidade de canhões no campo
     */
    public void atualizarPenalidade(int totalCanhoesNoCampo) {
        // Quanto mais canhões, mais lento o disparo (ex: 1=1s, 2=1.2s, 3=1.4s)
        this.intervaloDisparo = INTERVALO_BASE + (totalCanhoesNoCampo - 1) * 200;
    }

    @Override
    public void mover() {
        // Canhões são estáticos
    }

    /**
     * Loop do canhão: busca o alvo mais próximo no seu campo e dispara.
     */
    @Override
    public void run() {
        if (!ativo) return;
        do {
            try {
                Thread.sleep(intervaloDisparo);
                // Verifica se o jogo está rodando e se o campo tem energia para atirar
                if (jogo.isRodando() && ativo && jogo.temEnergia(this.campo)) {
                    atirar();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (ativo);
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

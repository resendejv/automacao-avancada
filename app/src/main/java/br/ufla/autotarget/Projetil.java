package br.ufla.autotarget;

import java.util.List;

/**
 * Classe Projétil — opera em thread independente.
 * Move-se em linha reta na direção definida e verifica colisão com alvos.
 * Utiliza semáforo para região crítica de colisão (apenas um projétil
 * verifica colisão com um alvo por vez).
 */
public class Projetil extends Thread {
    private volatile double x;
    private volatile double y;
    private final double dx;
    private final double dy;
    private final double velocidade = 17.0;
    private volatile boolean ativo = true;
    private final Jogo jogo;
    private final int campo; // campo do canhão que disparou
    private static final double RAIO_PROJETIL = 5.0;

    public Projetil(double startX, double startY, double dx, double dy, Jogo jogo, int campo) {
        this.x = startX;
        this.y = startY;
        this.dx = dx;
        this.dy = dy;
        this.jogo = jogo;
        this.campo = campo;
    }

    // Construtor compatível com versão anterior
    public Projetil(double startX, double startY, double dx, double dy, Jogo jogo) {
        this(startX, startY, dx, dy, jogo, 0);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public int getCampo() { return campo; }

    @Override
    public void run() {
        if (!ativo) return;
        do {
            // Mover o projétil
            x += dx * velocidade;
            y += dy * velocidade;

            // Verificar se saiu da tela
            if (x < -10 || x > jogo.getScreenWidth() + 10 || y < -10 || y > jogo.getScreenHeight() + 10) {
                ativo = false;
                break;
            }

            // Verificar colisão com alvos
            verificarColisao();

            try {
                Thread.sleep(20); // ~50 FPS para o projétil
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (ativo);
        jogo.removerProjetil(this);
    }

    /**
     * Verifica colisão com todos os alvos ativos do mesmo campo.
     * Utiliza semáforo para região crítica, garantindo que apenas um
     * projétil verifique colisão com um alvo por vez.
     */
    private void verificarColisao() {
        List<Alvo> alvos = jogo.getAlvos();

        for (Alvo alvo : alvos) {
            if (!alvo.isAtivo()) continue;
            // Só verifica colisão com alvos do mesmo campo que o canhão que disparou
            if (alvo.getCampo() != this.campo) continue;

            // Região Crítica: Apenas um projétil verifica colisão com este alvo por vez
            try {
                alvo.semaforoColisao.acquire();

                // Double check após adquirir o lock
                if (!alvo.isAtivo()) {
                    continue;
                }

                double dist = verificarDistanciaColisao(
                        this.x, this.y, alvo.getX(), alvo.getY());

                if (dist < alvo.getRaio() + RAIO_PROJETIL) {
                    alvo.setAtivo(false); // Abatido
                    this.ativo = false;   // Destruído no impacto
                    jogo.registrarAbate(this.campo);
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                alvo.semaforoColisao.release();
            }
        }
    }

    /**
     * Calcula distância para verificação de colisão.
     * Método público estático para ser testável via JUnit.
     */
    public static double verificarDistanciaColisao(
            double projX, double projY, double alvoX, double alvoY) {
        return Math.hypot(projX - alvoX, projY - alvoY);
    }

    /**
     * Verifica se há colisão entre projétil e alvo dados.
     * Método estático para uso em testes unitários.
     */
    public static boolean colide(double projX, double projY,
                                  double alvoX, double alvoY, double raioAlvo) {
        double dist = Math.hypot(projX - alvoX, projY - alvoY);
        return dist < raioAlvo + RAIO_PROJETIL;
    }
}

package br.ufla.autotarget;

import java.util.Random;

/**
 * Alvo rápido — velocidade maior, raio menor.
 * Mais difícil de acertar que o AlvoComum.
 */
public class AlvoRapido extends Alvo {
    private final Random random;

    public AlvoRapido(double x, double y, int screenWidth, int screenHeight, Jogo jogo) {
        super(x, y, 15.0, 7.0, screenWidth, screenHeight, jogo);
        this.random = new Random();
    }

    @Override
    public void mover() {
        x += dx * velocidade;
        y += dy * velocidade;

        // Bounce nas bordas
        if (x - raio < 0 || x + raio > screenWidth) {
            dx = -dx;
            x = Math.max(raio, Math.min(x, screenWidth - raio));
        }
        if (y - raio < 0 || y + raio > screenHeight) {
            dy = -dy;
            y = Math.max(raio, Math.min(y, screenHeight - raio));
        }
    }
}

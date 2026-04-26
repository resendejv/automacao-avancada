package br.ufla.autotarget;

import java.util.Random;

public class AlvoRapido extends Alvo {
    private Random random;

    public AlvoRapido(double x, double y, int screenWidth, int screenHeight, Jogo jogo) {
        super(x, y, 15.0, 7.0, screenWidth, screenHeight, jogo);
        this.random = new Random();
    }

    @Override
    public void mover() {
        x += dx * velocidade;
        y += dy * velocidade;

        // Ocasionalmente muda de direção de forma errática
        if (random.nextDouble() < 0.05) {
            double angle = random.nextDouble() * 2 * Math.PI;
            dx = Math.cos(angle);
            dy = Math.sin(angle);
        }

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

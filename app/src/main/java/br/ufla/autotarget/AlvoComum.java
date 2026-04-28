package br.ufla.autotarget;

/**
 * Alvo comum — movimento linear com bounce nas bordas.
 * Velocidade menor, raio maior, mais fácil de acertar.
 */
public class AlvoComum extends Alvo {

    public AlvoComum(double x, double y, int screenWidth, int screenHeight, Jogo jogo) {
        super(x, y, 20.0, 3.0, screenWidth, screenHeight, jogo);
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

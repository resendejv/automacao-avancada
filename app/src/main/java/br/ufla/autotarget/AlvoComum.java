package br.ufla.autotarget;

/**
 * Alvo comum — movimento linear com bounce nas bordas.
 * Velocidade menor, raio maior, mais fácil de acertar.
 */
public class AlvoComum extends Alvo {

    public AlvoComum(double x, double y, int screenWidth, int screenHeight, Jogo jogo) {
        super(x, y, 20.0, 3.5, screenWidth, screenHeight, jogo);
    }
    
    // Utiliza mover() padrão da classe Alvo
}

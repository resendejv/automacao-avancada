package br.ufla.autotarget;

import android.graphics.Color;

/**
 * Alvo comum — movimento linear com bounce nas bordas.
 * Velocidade menor, raio maior, mais fácil de acertar.
 */
public class AlvoComum extends Alvo {

    public AlvoComum(double x, double y, int screenWidth, int screenHeight, Jogo jogo) {
        super(x, y, 20.0, 3.5, screenWidth, screenHeight, jogo);
    }
    
    // Utiliza mover() padrão da classe Alvo

    @Override
    public int getCor() {
        return Color.parseColor("#4FC3F7"); // COR_ALVO_COMUM
    }

    @Override
    public int getCorBorda() {
        return Color.parseColor("#B3E5FC");
    }

    @Override
    public int getGlowAlpha() {
        return 40;
    }
}

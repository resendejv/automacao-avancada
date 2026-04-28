package br.ufla.autotarget;

/**
 * Classe abstrata base para todas as entidades que se movem no jogo.
 * Estende Thread para permitir execução concorrente.
 */
public abstract class EntidadeMovel extends Thread {
    protected volatile double x;
    protected volatile double y;
    protected double velocidade;
    protected volatile boolean ativo;
    protected int screenWidth;
    protected int screenHeight;
    protected Jogo jogo;

    public EntidadeMovel(double x, double y, double velocidade, int screenWidth, int screenHeight, Jogo jogo) {
        this.x = x;
        this.y = y;
        this.velocidade = velocidade;
        this.ativo = true;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.jogo = jogo;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getVelocidade() { return velocidade; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    /**
     * Método polimórfico de movimento — cada subclasse define seu comportamento.
     */
    public abstract void mover();
}

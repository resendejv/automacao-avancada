package br.ufla.autotarget;

import java.util.List;

public class Canhao extends Thread {
    private double x;
    private double y;
    private boolean ativo = true;
    private Jogo jogo;

    public Canhao(double x, double y, Jogo jogo) {
        this.x = x;
        this.y = y;
        this.jogo = jogo;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public void run() {
        while (ativo) {
            try {
                disparar();
                Thread.sleep(1000); // Dispara a cada 1 segundo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Captura outras exceções como divisão por zero no cálculo de ângulo
                System.err.println("Erro no canhão: " + e.getMessage());
            }
        }
    }

    private void disparar() {
        List<Alvo> alvos = jogo.getAlvos();
        Alvo alvoMaisProximo = null;
        double menorDistancia = Double.MAX_VALUE;

        // Procurar o alvo mais próximo
        for (Alvo alvo : alvos) {
            if (!alvo.isAtivo()) continue;
            double dist = Math.hypot(this.x - alvo.getX(), this.y - alvo.getY());
            if (dist < menorDistancia) {
                menorDistancia = dist;
                alvoMaisProximo = alvo;
            }
        }

        if (alvoMaisProximo != null) {
            double dx = alvoMaisProximo.getX() - this.x;
            double dy = alvoMaisProximo.getY() - this.y;
            double dist = Math.hypot(dx, dy);
            
            // Exceção tratada caso distância seja zero (divisão por zero)
            if (dist == 0) dist = 0.0001;

            dx /= dist;
            dy /= dist;

            // Cria e dispara projétil
            Projetil projetil = new Projetil(this.x, this.y, dx, dy, jogo);
            jogo.adicionarProjetil(projetil);
            projetil.start();
        }
    }
}

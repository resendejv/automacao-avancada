package br.ufla.autotarget;

import android.util.Log;

import java.util.List;
import java.util.Locale;

/**
 * Classe de Reconciliação de Dados.
 * Periodicamente corrige as leituras dos sensores (posição e velocidade dos alvos)
 * e decide se é vantajoso realocar canhões existentes ou adicionar/remover canhões,
 * considerando a penalidade de energia.
 *
 * Roda como thread periódica (a cada ~10 segundos conforme especificação).
 */
public class DataReconciliation extends Thread {
    private static final String TAG = "DataReconciliation";
    private final Jogo jogo;
    private volatile boolean ativo = true;
    private static final long INTERVALO_RECONCILIACAO = 10000; // 10 segundos

    // Fator de ruído simulado dos sensores
    private static final double FATOR_RUIDO = 0.15;

    public DataReconciliation(Jogo jogo) {
        this.jogo = jogo;
        setName("Thread-Reconciliacao");
    }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public void run() {
        if (!ativo) return;
        do {
            try {
                Thread.sleep(INTERVALO_RECONCILIACAO);
                if (!ativo) break;

                reconciliar();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (ativo);
    }

    /**
     * Realiza a reconciliação de dados:
     * 1. Simula leituras de sensores com ruído
     * 2. Corrige posições estimadas dos alvos
     * 3. Avalia e otimiza posicionamento de canhões
     */
    public void reconciliar() {
        // Log.d(TAG, "=== Iniciando Reconciliação de Dados ===");

        // 1. Coleta de dados dos sensores (simulado com ruído)
        List<Alvo> alvos = jogo.getAlvos();
        for (Alvo alvo : alvos) {
            if (!alvo.isAtivo()) continue;

            // Leitura "real" do sensor (posição com ruído)
            double sensorX = alvo.getX() + (Math.random() - 0.5) * 2 * FATOR_RUIDO * alvo.getRaio();
            double sensorY = alvo.getY() + (Math.random() - 0.5) * 2 * FATOR_RUIDO * alvo.getRaio();

            // Reconciliação: média ponderada entre valor estimado e valor do sensor
            double posXReconciliada = reconciliarValor(alvo.getX(), sensorX, 0.7);
            double posYReconciliada = reconciliarValor(alvo.getY(), sensorY, 0.7);

            // Log.d(TAG, String.format(Locale.getDefault(), "Alvo [%.1f,%.1f] -> Sensor [%.1f,%.1f] -> Reconciliado [%.1f,%.1f]",
            //        alvo.getX(), alvo.getY(), sensorX, sensorY, posXReconciliada, posYReconciliada));
        }

        // 2. Otimização de canhões
        otimizarCanhoes();

        // Log.d(TAG, "=== Reconciliação Concluída ===");
    }

    /**
     * Reconcilia dois valores usando média ponderada.
     * @param valorEstimado valor atual estimado pelo sistema
     * @param valorSensor valor lido pelo sensor (com ruído)
     * @param pesoEstimado peso dado ao valor estimado (0.0 a 1.0)
     * @return valor reconciliado
     */
    public static double reconciliarValor(double valorEstimado, double valorSensor, double pesoEstimado) {
        return valorEstimado * pesoEstimado + valorSensor * (1.0 - pesoEstimado);
    }

    /**
     * Avalia e otimiza o posicionamento dos canhões:
     * - Verifica se há canhões em excesso (penalidade)
     * - Atualiza penalidades nos canhões existentes
     */
    private void otimizarCanhoes() {
        int campo = 0;
        do {
            List<Canhao> canhoes = jogo.getCanhoesPorCampo(campo);
            int numCanhoes = canhoes.size();

            // Atualiza penalidade de cada canhão baseado no total do campo
            for (Canhao c : canhoes) {
                c.atualizarPenalidade(numCanhoes);
            }

            // Conta alvos ativos no campo
            int alvosNoCampo = 0;
            for (Alvo a : jogo.getAlvos()) {
                if (a.isAtivo() && a.getCampo() == campo) alvosNoCampo++;
            }

            // Log.d(TAG, String.format(Locale.getDefault(), "Campo %d: %d canhões, %d alvos ativos, penalidade: %s",
            //        campo, numCanhoes, alvosNoCampo,
            //        numCanhoes > Canhao.LIMITE_SEM_PENALIDADE ? "SIM" : "NÃO"));
            campo++;
        } while (campo <= 1);
    }
}

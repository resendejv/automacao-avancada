package br.ufla.autotarget;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe de Reconciliação de Dados (AV2).
 * Implementa otimização de posicionamento de canhões usando reconciliação estatística.
 */
public class DataReconciliation extends Thread {
    private static final String TAG = "DataReconciliation";
    private final Jogo jogo;
    private volatile boolean ativo = true;
    private static final long INTERVALO_RECONCILIACAO = 10000; // 10 segundos

    public DataReconciliation(Jogo jogo) {
        this.jogo = jogo;
        setName("Thread-Reconciliacao");
    }

    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    @Override
    public void run() {
        while (ativo) {
            try {
                Thread.sleep(INTERVALO_RECONCILIACAO);
                if (!ativo) break;
                
                reconciliar(0); // Campo Esquerdo
                reconciliar(1); // Campo Direito
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Executa o processo de reconciliação e otimização para um campo.
     */
    private void reconciliar(int campo) {
        List<Alvo> alvos = new ArrayList<>();
        for (Alvo a : jogo.getAlvos()) {
            if (a.isAtivo() && a.getCampo() == campo) alvos.add(a);
        }

        List<Canhao> canhoes = jogo.getCanhoesPorCampo(campo);
        
        Log.d(TAG, "Iniciando reconciliação campo " + campo + ". Canhões: " + canhoes.size() + ", Alvos: " + alvos.size());
        
        if (alvos.isEmpty() || canhoes.isEmpty()) return;

        // 1. Montagem do Vetor de Observações y (distâncias ruidosas)
        int numAlvos = alvos.size();
        int numCanhoes = canhoes.size();
        double[] y = new double[numAlvos * numCanhoes];
        double[][] V = new double[y.length][y.length]; 

        int k = 0;
        for (Canhao c : canhoes) {
            for (Alvo a : alvos) {
                double[][] stats = jogo.getEstatisticasAlvo(a);
                if (stats != null) {
                    double dx = stats[0][0] - c.getX();
                    double dy = stats[0][1] - c.getY();
                    y[k] = Math.sqrt(dx * dx + dy * dy);
                    V[k][k] = stats[1][0] + stats[1][1] + 1.0; 
                } else {
                    y[k] = Canhao.calcularDistancia(c.getX(), c.getY(), a.getX(), a.getY());
                    V[k][k] = 50.0; // Variância padrão alta
                }
                k++;
            }
        }

        // 2. Matriz de Incidência A
        double[][] A = new double[numCanhoes][y.length];
        for (int i = 0; i < numCanhoes; i++) {
            for (int j = 0; j < numAlvos; j++) {
                // Simplificação: todos os alvos do campo influenciam o canhão para o centroide
                A[i][i * numAlvos + j] = 1.0; 
            }
        }

        // 3. Aplicação da Reconciliação
        double[] y_hat = reconcile(y, V, A);

        // 4. Otimização de Posicionamento (Centroide)
        otimizarPosicionamento(canhoes, alvos, y_hat);

        // 5. Decisão de Adicionar/Remover Canhões (AV2)
        avaliarCustoBeneficio(campo, canhoes, alvos, y_hat);
    }

    /**
     * Avalia se vale a pena adicionar ou remover canhões com base no ganho esperado (AV2).
     */
    private void avaliarCustoBeneficio(int campo, List<Canhao> canhoes, List<Alvo> alvos, double[] y_hat) {
        int numCanhoes = canhoes.size();
        int numAlvos = alvos.size();
        
        // Função de Utilidade: Estimativa de abates por segundo
        // Depende do número de canhões e da distância reconciliada média
        double ganhoAtual = calcularUtilidade(numCanhoes, numAlvos, y_hat);
        
        // Estima ganho com um canhão a mais
        double ganhoSeAdicionar = calcularUtilidade(numCanhoes + 1, numAlvos, y_hat);
        
        // Estima ganho com um canhão a menos
        double ganhoSeRemover = (numCanhoes > 0) ? calcularUtilidade(numCanhoes - 1, numAlvos, y_hat) : 0;

        Log.d(TAG, String.format("Campo %d | Utilidade: %.2f | Se +1: %.2f | Se -1: %.2f", 
                campo, ganhoAtual, ganhoSeAdicionar, ganhoSeRemover));

        // Regra de Decisão (Algoritmo Guloso)
        double thresholdAdicao = 0.5; // Precisa de pelo menos 0.5 abates/s de ganho marginal
        double energia = (campo == 0) ? jogo.getEnergiaEsquerda() : jogo.getEnergiaDireita();

        if (numCanhoes < Canhao.MAX_CANHOES && (ganhoSeAdicionar - ganhoAtual) > thresholdAdicao && energia > 40.0) {
            Log.i(TAG, "Decisão: ADICIONAR canhão no campo " + campo + " (Ganho marginal alto)");
            try {
                // Adiciona no centro do campo
                double x = (campo == 0) ? jogo.getScreenWidth() / 4.0 : 3 * jogo.getScreenWidth() / 4.0;
                jogo.adicionarCanhao(x, jogo.getScreenHeight() / 2.0);
            } catch (JogoException e) {
                Log.e(TAG, "Erro ao adicionar canhão autônomo: " + e.getMessage());
            }
        } else if (numCanhoes > 1 && (ganhoAtual - ganhoSeRemover) < 0.2) {
            // Se remover um canhão quase não altera os abates, removemos para economizar energia
            Log.i(TAG, "Decisão: REMOVER canhão no campo " + campo + " (Custo-benefício baixo)");
            jogo.removerCanhaoMaisAntigo(campo);
        }
    }

    /**
     * Função de Utilidade: Abates_Estimados = (NumCanhoes * TaxaDisparo) * ProbabilidadeAcerto
     * ProbabilidadeAcerto diminui com a distância e com o excesso de canhões (penalidade).
     */
    private double calcularUtilidade(int n, int m, double[] y_hat) {
        if (n <= 0 || m == 0) return 0;
        
        // Taxa de disparo diminui com n (penalidade de 200ms por canhão extra)
        double taxaDisparo = 1000.0 / (1000.0 + (n - 1) * 200.0); 
        
        // Distância média reconciliada (y_hat)
        double somaDist = 0;
        for (double d : y_hat) somaDist += d;
        double distMedia = somaDist / y_hat.length;
        
        // Probabilidade de acerto (sigmoide invertida baseada na distância)
        double probAcerto = 1.0 / (1.0 + Math.exp((distMedia - 300.0) / 100.0));
        
        return (n * taxaDisparo) * probAcerto;
    }

    /**
     * Implementa a fórmula matemática: y^ = y - V*(A^T)*(AV(A^T))^-1*Ay
     */
    public double[] reconcile(double[] y, double[][] V, double[][] A) {
        // Ay = A * y
        double[] Ay = multiply(A, y);

        // VAt = V * A^T
        double[][] At = transpose(A);
        double[][] VAt = multiplyMatrices(V, At);

        // AVAt = A * V * A^T
        double[][] AVAt = multiplyMatrices(A, VAt);

        // Inv = (AVAt)^-1
        double[][] Inv = invert(AVAt);
        if (Inv == null) return y; // Fallback se singular

        // Result = VAt * Inv * Ay
        double[] corrections = multiply(multiplyMatrices(VAt, Inv), Ay);

        // y_hat = y - corrections
        double[] y_hat = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            y_hat[i] = y[i] - corrections[i];
        }

        return y_hat;
    }

    private void otimizarPosicionamento(List<Canhao> canhoes, List<Alvo> alvos, double[] y_hat) {
        int numAlvos = alvos.size();
        for (int i = 0; i < canhoes.size(); i++) {
            Canhao c = canhoes.get(i);
            double sumX = 0, sumY = 0, count = 0;

            for (int j = 0; j < numAlvos; j++) {
                // Alvos que estão na lista influenciam o movimento
                // Usamos y_hat para demonstrar o uso do valor reconciliado
                if (y_hat[i * numAlvos + j] > 0) { 
                    Alvo a = alvos.get(j);
                    sumX += a.getX();
                    sumY += a.getY();
                    count++;
                }
            }

            if (count > 0) {
                double newX = sumX / count;
                double newY = sumY / count;
                
                // AV2: Lógica Anti-Sobreposição (Separação entre threads)
                // Adiciona um deslocamento baseado no índice do canhão para que eles 
                // se organizem ao redor do centroide em vez de ficarem um sobre o outro.
                double offsetRadius = 60.0; // Distância de separação
                double angle = (2 * Math.PI / canhoes.size()) * i;
                newX += Math.cos(angle) * offsetRadius;
                newY += Math.sin(angle) * offsetRadius;

                Log.d(TAG, "Canhão campo " + c.getCampo() + " realocando com separação para: [" + newX + ", " + newY + "]");
                c.setPosicaoAlvo(newX, newY);
            }
        }
    }

    // === Utilitários de Matriz ===

    private double[][] transpose(double[][] m) {
        double[][] temp = new double[m[0].length][m.length];
        for (int i = 0; i < m.length; i++)
            for (int j = 0; j < m[0].length; j++)
                temp[j][i] = m[i][j];
        return temp;
    }

    private double[] multiply(double[][] m, double[] v) {
        double[] res = new double[m.length];
        for (int i = 0; i < m.length; i++)
            for (int j = 0; j < v.length; j++)
                res[i] += m[i][j] * v[j];
        return res;
    }

    private double[][] multiplyMatrices(double[][] a, double[][] b) {
        int rowsA = a.length;
        int colsA = a[0].length;
        int colsB = b[0].length;
        double[][] result = new double[rowsA][colsB];
        for (int i = 0; i < rowsA; i++) {
            for (int j = 0; j < colsB; j++) {
                for (int k = 0; k < colsA; k++) {
                    result[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return result;
    }

    private double[][] invert(double[][] a) {
        int n = a.length;
        if (n == 0) return null;
        double[][] x = new double[n][n];
        double[][] b = new double[n][n];
        int[] index = new int[n];
        for (int i = 0; i < n; ++i) b[i][i] = 1;
        gaussian(a, index);
        for (int i = 0; i < n - 1; ++i)
            for (int j = i + 1; j < n; ++j)
                for (int k = 0; k < n; ++k)
                    b[index[j]][k] -= a[index[j]][i] * b[index[i]][k];
        for (int i = 0; i < n; ++i) {
            x[n - 1][i] = b[index[n - 1]][i] / a[index[n - 1]][n - 1];
            for (int j = n - 2; j >= 0; --j) {
                x[j][i] = b[index[j]][i];
                for (int k = j + 1; k < n; ++k)
                    x[j][i] -= a[index[j]][k] * x[k][i];
                x[j][i] /= a[index[j]][j];
            }
        }
        return x;
    }

    private void gaussian(double[][] a, int[] index) {
        int n = index.length;
        double[] c = new double[n];
        for (int i = 0; i < n; ++i) index[i] = i;
        for (int i = 0; i < n; ++i) {
            double c1 = 0;
            for (int j = 0; j < n; ++j) {
                double c0 = Math.abs(a[i][j]);
                if (c0 > c1) c1 = c0;
            }
            c[i] = c1;
        }
        int k = 0;
        for (int j = 0; j < n - 1; ++j) {
            double pi1 = 0;
            for (int i = j; i < n; ++i) {
                double pi0 = Math.abs(a[index[i]][j]);
                pi0 /= c[index[i]];
                if (pi0 > pi1) {
                    pi1 = pi0;
                    k = i;
                }
            }
            int itmp = index[j];
            index[j] = index[k];
            index[k] = itmp;
            for (int i = j + 1; i < n; ++i) {
                double pj = a[index[i]][j] / a[index[j]][j];
                a[index[i]][j] = pj;
                for (int l = j + 1; l < n; ++l)
                    a[index[i]][l] -= pj * a[index[j]][l];
            }
        }
    }
}

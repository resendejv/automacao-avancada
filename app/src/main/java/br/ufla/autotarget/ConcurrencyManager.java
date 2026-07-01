package br.ufla.autotarget;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Gerenciador de Concorrência para simular restrição de núcleos (AV4 - Lei de Amdahl).
 * Utiliza pools de threads fixos para limitar o processamento paralelo.
 */
public class ConcurrencyManager {
    private static ExecutorService calculationPool;
    private static int currentCores = Runtime.getRuntime().availableProcessors();

    /**
     * Configura o nível de paralelismo máximo.
     * @param numCores Número de "núcleos" (threads simultâneas) simulados.
     */
    public static void setParallelism(int numCores) {
        if (calculationPool != null) {
            calculationPool.shutdown();
            try {
                if (!calculationPool.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    calculationPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                calculationPool.shutdownNow();
            }
        }
        currentCores = numCores;
        calculationPool = Executors.newFixedThreadPool(numCores);
    }

    public static void execute(Runnable task) {
        if (calculationPool == null || calculationPool.isShutdown()) {
            setParallelism(currentCores);
        }
        calculationPool.execute(task);
    }

    public static int getCurrentCores() {
        return currentCores;
    }
    
    public static void shutdown() {
        if (calculationPool != null) {
            calculationPool.shutdownNow();
        }
    }
}

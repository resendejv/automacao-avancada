package br.ufla.autotarget;

/**
 * Representa uma única leitura ruidosa de um alvo.
 */
public class LeituraSensor {
    public final double x;
    public final double y;
    public final double vx;
    public final double vy;
    public final long timestamp;

    public LeituraSensor(double[] noisyData) {
        this.x = noisyData[0];
        this.y = noisyData[1];
        this.vx = noisyData[2];
        this.vy = noisyData[3];
        this.timestamp = System.currentTimeMillis();
    }
}

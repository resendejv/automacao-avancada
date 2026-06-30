package br.ufla.autotarget;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

/**
 * Representa uma leitura térmica do sistema ciberfísico (AV3).
 */
public class ThermalReading {
    private String uid;
    private double temperature;
    
    @ServerTimestamp
    private Date timestamp;

    public ThermalReading() {}

    public ThermalReading(String uid, double temperature) {
        this.uid = uid;
        this.temperature = temperature;
    }

    public String getUid() { return uid; }
    public double getTemperature() { return temperature; }
    public Date getTimestamp() { return timestamp; }
}

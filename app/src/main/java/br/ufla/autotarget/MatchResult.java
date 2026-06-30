package br.ufla.autotarget;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

/**
 * Modelo de dados para o resultado de uma partida (AV3).
 * Contém dados encriptados e metadados de sistema.
 */
public class MatchResult {
    private String uid;
    private String encryptedData; // Nome e pontuação encriptados em JSON
    private int totalAbates;
    private int numCanhoes;
    
    @ServerTimestamp
    private Date timestamp;

    public MatchResult() {} // Necessário para o Firebase

    public MatchResult(String uid, String encryptedData, int totalAbates, int numCanhoes) {
        this.uid = uid;
        this.encryptedData = encryptedData;
        this.totalAbates = totalAbates;
        this.numCanhoes = numCanhoes;
    }

    public String getUid() { return uid; }
    public String getEncryptedData() { return encryptedData; }
    public int getTotalAbates() { return totalAbates; }
    public int getNumCanhoes() { return numCanhoes; }
    public Date getTimestamp() { return timestamp; }
}

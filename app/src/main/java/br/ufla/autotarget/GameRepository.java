package br.ufla.autotarget;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repositório para operações de I/O em background com Firebase (AV3).
 */
public class GameRepository {
    private static final String TAG = "GameRepository";
    private FirebaseFirestore db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface RankingCallback {
        void onRankingLoaded(List<RankingAdapter.RankingItem> items);
        void onError(Exception e);
    }

    public GameRepository() {
        try {
            db = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar Firebase", e);
        }
    }

    /**
     * Recupera o ranking global (Top pontuações de todos os utilizadores) (AV3).
     */
    public void getGlobalRanking(final RankingCallback callback) {
        if (db == null) {
            callback.onError(new Exception("Firebase não configurado"));
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Buscando ranking global...");
                db.collection("partidas")
                    .get() 
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        // Map para garantir apenas o MAIOR score por usuário (UID)
                        Map<String, RankingAdapter.RankingItem> bestScores = new HashMap<>();
                        
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            try {
                                MatchResult res = doc.toObject(MatchResult.class);
                                String encryptedData = res.getEncryptedData();
                                
                                if (encryptedData != null) {
                                    String decryptedJson = Cryptography.decrypt(encryptedData);
                                    if (decryptedJson != null) {
                                        org.json.JSONObject json = new org.json.JSONObject(decryptedJson);
                                        String email = json.getString("user");
                                        String nome = email.split("@")[0];
                                        int score = json.getInt("score");
                                        
                                        String uid = res.getUid();
                                        // Se o usuário não está no map ou se este score é maior, atualiza
                                        if (!bestScores.containsKey(uid) || score > bestScores.get(uid).score) {
                                            bestScores.put(uid, new RankingAdapter.RankingItem(nome, score));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Erro ao processar documento: " + doc.getId(), e);
                            }
                        }
                        
                        // Converte para lista e ordena
                        List<RankingAdapter.RankingItem> ranking = new ArrayList<>(bestScores.values());
                        Collections.sort(ranking, new Comparator<RankingAdapter.RankingItem>() {
                            @Override
                            public int compare(RankingAdapter.RankingItem a, RankingAdapter.RankingItem b) {
                                return Integer.compare(b.score, a.score);
                            }
                        });
                        
                        int end = Math.min(ranking.size(), 10);
                        List<RankingAdapter.RankingItem> top10 = new ArrayList<>(ranking.subList(0, end));
                        callback.onRankingLoaded(top10);
                    })
                    .addOnFailureListener(callback::onError);
            }
        });
    }

    /**
     * Verifica se deve atualizar o recorde pessoal antes de gravar (AV3).
     */
    public void updateHighScoreIfBetter(final MatchResult newResult, final int newScore) {
        if (db == null) return;
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String uid = newResult.getUid();
                db.collection("partidas")
                    .whereEqualTo("uid", uid)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        boolean deveGravar = true;
                        String docIdParaAtualizar = null;

                        if (!queryDocumentSnapshots.isEmpty()) {
                            // O usuário já tem um registro, vamos ver se o novo é melhor
                            QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                            docIdParaAtualizar = doc.getId();
                            
                            MatchResult res = doc.toObject(MatchResult.class);
                            String decryptedJson = Cryptography.decrypt(res.getEncryptedData());
                            if (decryptedJson != null) {
                                try {
                                    int scoreAntigo = new org.json.JSONObject(decryptedJson).getInt("score");
                                    if (newScore <= scoreAntigo) {
                                        deveGravar = false; // Não superou o recorde
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Erro ao comparar scores", e);
                                }
                            }
                        }

                        if (deveGravar) {
                            if (docIdParaAtualizar != null) {
                                db.collection("partidas").document(docIdParaAtualizar).set(newResult);
                            } else {
                                db.collection("partidas").add(newResult);
                            }
                            Log.i(TAG, "Recorde atualizado para o usuário: " + uid);
                        } else {
                            Log.i(TAG, "Pontuação inferior ao recorde. Nada gravado.");
                        }
                    });
            }
        });
    }

    public void saveTelemetry(ThermalReading reading) {
        if (db == null) return;
        executor.execute(() -> db.collection("telemetria").add(reading));
    }

    public void shutdown() {
        executor.shutdown();
    }
}

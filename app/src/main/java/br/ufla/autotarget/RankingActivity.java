package br.ufla.autotarget;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * Activity que exibe o Top Ranking do utilizador decifrado em tempo real.
 */
public class RankingActivity extends AppCompatActivity {

    private RecyclerView recyclerRanking;
    private GameRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);

        recyclerRanking = findViewById(R.id.recyclerRanking);
        recyclerRanking.setLayoutManager(new LinearLayoutManager(this));
        
        Button btnVoltar = findViewById(R.id.btnVoltar);
        btnVoltar.setOnClickListener(v -> finish());

        repository = new GameRepository();
        carregarRanking();
    }

    private void carregarRanking() {
        String uid = SessionManager.getUserUid();
        if (uid == null) return;

        repository.getGlobalRanking(new GameRepository.RankingCallback() {
            @Override
            public void onRankingLoaded(List<RankingAdapter.RankingItem> items) {
                // Volta para a UI Thread para atualizar a lista
                runOnUiThread(() -> {
                    RankingAdapter adapter = new RankingAdapter(items);
                    recyclerRanking.setAdapter(adapter);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> 
                    Toast.makeText(RankingActivity.this, "Erro ao carregar ranking", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        repository.shutdown();
    }
}

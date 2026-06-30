package br.ufla.autotarget;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.RankingViewHolder> {

    private final List<RankingItem> items;

    public RankingAdapter(List<RankingItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public RankingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ranking, parent, false);
        return new RankingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RankingViewHolder holder, int position) {
        RankingItem item = items.get(position);
        holder.textPosicao.setText(String.valueOf(position + 1));
        holder.textNome.setText(item.nome);
        holder.textScore.setText(String.valueOf(item.score));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class RankingItem {
        String nome;
        int score;

        public RankingItem(String nome, int score) {
            this.nome = nome;
            this.score = score;
        }
    }

    static class RankingViewHolder extends RecyclerView.ViewHolder {
        TextView textPosicao, textNome, textScore;

        public RankingViewHolder(@NonNull View itemView) {
            super(itemView);
            textPosicao = itemView.findViewById(R.id.textPosicao);
            textNome = itemView.findViewById(R.id.textNome);
            textScore = itemView.findViewById(R.id.textScore);
        }
    }
}

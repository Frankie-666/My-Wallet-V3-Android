package info.blockchain.wallet.ui;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import info.blockchain.wallet.payload.Tx;
import info.blockchain.wallet.viewModel.BalanceTxViewModel;

import java.util.ArrayList;
import java.util.List;

import piuk.blockchain.android.R;
import piuk.blockchain.android.databinding.TxsLayoutExpandableBinding;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.BindingHolder> {

    private List<Tx> mTxs;
    private Context mContext;

    public TransactionAdapter(Context context) {
        mContext = context;
        mTxs = new ArrayList<>();
    }

    @Override
    public BindingHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        TxsLayoutExpandableBinding postBinding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.getContext()),
                R.layout.txs_layout_expandable,
                parent,
                false);
        return new BindingHolder(postBinding);
    }

    @Override
    public void onBindViewHolder(BindingHolder holder, int position) {
        TxsLayoutExpandableBinding postBinding = holder.binding;
        postBinding.setViewModel(new BalanceTxViewModel(mContext, mTxs.get(position)));
    }

    public void setItems(List<Tx> txs) {
        this.mTxs = txs;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mTxs.size();
    }

    public static class BindingHolder extends RecyclerView.ViewHolder {
        private TxsLayoutExpandableBinding binding;

        public BindingHolder(TxsLayoutExpandableBinding binding) {
            super(binding.container);
            this.binding = binding;
        }
    }
}

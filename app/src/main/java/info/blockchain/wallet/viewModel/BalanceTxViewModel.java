package info.blockchain.wallet.viewModel;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.View;

import info.blockchain.wallet.multiaddr.MultiAddrFactory;
import info.blockchain.wallet.payload.Tx;
import info.blockchain.wallet.ui.BalanceFragment;
import info.blockchain.wallet.util.DateUtil;
import info.blockchain.wallet.util.MonetaryUtil;
import info.blockchain.wallet.util.PrefsUtil;

import piuk.blockchain.android.R;

public class BalanceTxViewModel {

    private Context context;
    private Tx tx;

    private static int nbConfirmations = 3;
    private double btc_fx = 319.13;

    public BalanceTxViewModel(Context context, Tx tx) {
        this.context = context;
        this.tx = tx;
    }

    public String getDate(){
        return DateUtil.getInstance(context).formatted(tx.getTS());
    }

    public String getDirection(){

        String dirText = tx.getDirection();

        if (dirText.equals(MultiAddrFactory.MOVED))
            return context.getResources().getString(R.string.MOVED);
        else if (dirText.equals(MultiAddrFactory.RECEIVED))
            return context.getResources().getString(R.string.RECEIVED);
        else
            return context.getResources().getString(R.string.SENT);
    }

    public String getDisplayUnits() {
        return (String) MonetaryUtil.getInstance().getBTCUnits()[PrefsUtil.getInstance(context).getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC)];
    }

    public String getAmount(){

        double _btc_balance = tx.getAmount() / 1e8;
        double _fiat_balance = btc_fx * _btc_balance;
        String strFiat = PrefsUtil.getInstance(context).getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);

        //TODO - fix this ugly reference to isBTC
        if (BalanceFragment.isBTC) {
            return MonetaryUtil.getInstance(context).getDisplayAmountWithFormatting(Math.abs(tx.getAmount())) + " " + getDisplayUnits();
        }else{
            return MonetaryUtil.getInstance().getFiatFormat(strFiat).format(Math.abs(_fiat_balance)) + " " + strFiat;
        }
    }

    public int getAmountBackground(){

        double _btc_balance = tx.getAmount() / 1e8;

        if (tx.isMove()) {
            return tx.getConfirmations() < nbConfirmations ? R.drawable.rounded_view_lighter_blue_50 : R.drawable.rounded_view_lighter_blue;
        } else if (_btc_balance < 0.0) {
            return tx.getConfirmations() < nbConfirmations ? R.drawable.rounded_view_red_50 : R.drawable.rounded_view_red;
        } else {
            return tx.getConfirmations() < nbConfirmations ? R.drawable.rounded_view_green_50 : R.drawable.rounded_view_green;
        }
    }

    public int getWatchOnlyVisibility(){
        return tx.isWatchOnly() ? View.VISIBLE : View.GONE;
    }

    public int getDirectionColor(){
        String dirText = tx.getDirection();

        if (dirText.equals(MultiAddrFactory.MOVED))
            return ContextCompat.getColor(context, R.color.blockchain_transfer_blue);
        else if (dirText.equals(MultiAddrFactory.RECEIVED))
            return ContextCompat.getColor(context, R.color.blockchain_receive_green);
        else
            return ContextCompat.getColor(context, R.color.blockchain_send_red);
    }
}

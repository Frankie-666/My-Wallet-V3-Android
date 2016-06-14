package info.blockchain.wallet.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.Pair;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.getbase.floatingactionbutton.FloatingActionsMenu;

import info.blockchain.wallet.multiaddr.MultiAddrFactory;
import info.blockchain.wallet.payload.HDPayloadBridge;
import info.blockchain.wallet.payload.PayloadBridge;
import info.blockchain.wallet.payload.PayloadFactory;
import info.blockchain.wallet.payload.Transaction;
import info.blockchain.wallet.payload.Tx;
import info.blockchain.wallet.util.MonetaryUtil;
import info.blockchain.wallet.util.OSUtil;
import info.blockchain.wallet.util.PrefsUtil;
import info.blockchain.wallet.util.SSLVerifierThreadUtil;
import info.blockchain.wallet.util.WebUtil;
import info.blockchain.wallet.viewModel.BalanceViewModel;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import piuk.blockchain.android.R;
import piuk.blockchain.android.databinding.FragmentBalanceBinding;

public class BalanceFragment extends Fragment implements BalanceViewModel.DataListener{

    public static final String ACTION_INTENT = "info.blockchain.wallet.ui.BalanceFragment.REFRESH";
    private final static int SHOW_BTC = 1;
    private final static int SHOW_FIAT = 2;
    private final static int SHOW_HIDE = 3;
    private static int nbConfirmations = 3;
    private static int BALANCE_DISPLAY_STATE = SHOW_BTC;
    private static boolean isBottomSheetOpen = false;
    public int balanceBarHeight;
    ArrayAdapter<String> accountsAdapter = null;
    LinearLayoutManager layoutManager;
    HashMap<View, Boolean> rowViewState = null;
    Communicator comm;
    //
    // main balance display
    //
    private double btc_fx = 319.13;
    private Spannable span1 = null;
    public static boolean isBTC = true;
    //
    // accounts list
    //
    private Spinner accountSpinner = null;//TODO - move to drawer header
    //
    // tx list
    //
    private TransactionAdapter transactionAdapter = null;
    private LinearLayout noTxMessage = null;
    private Activity context = null;
    private int originalHeight = 0;
    private int newHeight = 0;
    private int expandDuration = 200;
    private boolean mIsViewExpanded = false;
    private View prevRowClicked = null;

    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {

            if (ACTION_INTENT.equals(intent.getAction())) {

                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        binding.swipeContainer.setRefreshing(true);
                    }

                    @Override
                    protected Void doInBackground(Void... params) {

                        // Update internal balance and transaction data
                        try {
                            HDPayloadBridge.getInstance(context).updateBalancesAndTransactions();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);
                        viewModel.updateAccountList();
                        viewModel.updateBalanceAndTransactionList(intent, accountSpinner.getSelectedItemPosition(), isBTC);
                        binding.swipeContainer.setRefreshing(false);
                    }

                }.execute();
            }
        }
    };

    FragmentBalanceBinding binding;
    BalanceViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        context = getActivity();

        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_balance, container, false);
        viewModel = new BalanceViewModel(context, this);
        binding.setViewModel(viewModel);

        setHasOptionsMenu(true);

        balanceBarHeight = (int) getResources().getDimension(R.dimen.action_bar_height) + 35;

        BALANCE_DISPLAY_STATE = PrefsUtil.getInstance(context).getValue(PrefsUtil.KEY_BALANCE_DISPLAY_STATE, SHOW_BTC);
        if (BALANCE_DISPLAY_STATE == SHOW_FIAT) {
            isBTC = false;
        }

        setupViews();

        return binding.getRoot();
    }

    private void setAccountSpinner(){

        Toolbar toolbar = (Toolbar) context.findViewById(R.id.toolbar);
        ((AppCompatActivity) context).setSupportActionBar(toolbar);

        if(viewModel.getActiveAccountAndAddressList().size() > 1){
            ((AppCompatActivity) context).getSupportActionBar().setDisplayShowTitleEnabled(false);
            accountSpinner.setVisibility(View.VISIBLE);
        }else{
            ((AppCompatActivity) context).getSupportActionBar().setDisplayShowTitleEnabled(true);
            accountSpinner.setSelection(0);
            ((AppCompatActivity) context).getSupportActionBar().setTitle(viewModel.getActiveAccountAndAddressList().get(0));
            accountSpinner.setVisibility(View.GONE);
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            isBottomSheetOpen = false;
            viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);
        } else {
            ;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        MainActivity.currentFragment = this;

        comm.resetNavigationDrawer();

        isBottomSheetOpen = false;

        IntentFilter filter = new IntentFilter(ACTION_INTENT);
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);

        //TODO Why start service again?????
        if (!OSUtil.getInstance(context).isServiceRunning(info.blockchain.wallet.websocket.WebSocketService.class)) {
            context.startService(new Intent(context, info.blockchain.wallet.websocket.WebSocketService.class));
        } else {
            context.stopService(new Intent(context, info.blockchain.wallet.websocket.WebSocketService.class));
            context.startService(new Intent(context, info.blockchain.wallet.websocket.WebSocketService.class));
        }

        viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);
        viewModel.updateAccountList();
    }

    @Override
    public void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        menu.findItem(R.id.action_qr).setVisible(true);
        menu.findItem(R.id.action_send).setVisible(false);
        menu.findItem(R.id.action_share_receive).setVisible(false);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        comm = (Communicator) activity;
    }

    private void initFab(){

        //First icon when fab expands
        com.getbase.floatingactionbutton.FloatingActionButton actionA = new com.getbase.floatingactionbutton.FloatingActionButton(context);
        actionA.setColorNormal(getResources().getColor(R.color.blockchain_send_red));
        actionA.setSize(com.getbase.floatingactionbutton.FloatingActionButton.SIZE_MINI);
        Drawable sendIcon = context.getResources().getDrawable(R.drawable.icon_send);
        sendIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        actionA.setIconDrawable(sendIcon);
        actionA.setColorPressed(getResources().getColor(R.color.blockchain_red_50));
        actionA.setTitle(getResources().getString(R.string.send_bitcoin));
        actionA.setOnClickListener(v -> sendClicked());

        //Second icon when fab expands
        com.getbase.floatingactionbutton.FloatingActionButton actionB = new com.getbase.floatingactionbutton.FloatingActionButton(context);
        actionB.setColorNormal(getResources().getColor(R.color.blockchain_receive_green));
        actionB.setSize(com.getbase.floatingactionbutton.FloatingActionButton.SIZE_MINI);
        Drawable receiveIcon = context.getResources().getDrawable(R.drawable.icon_receive);
        receiveIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        actionB.setIconDrawable(receiveIcon);
        actionB.setColorPressed(getResources().getColor(R.color.blockchain_green_50));
        actionB.setTitle(getResources().getString(R.string.receive_bitcoin));
        actionB.setOnClickListener(v -> receiveClicked());

        //Add buttons to expanding fab
        binding.fab.addButton(actionA);
        binding.fab.addButton(actionB);

        binding.fab.setOnFloatingActionsMenuUpdateListener(new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener() {
            @Override
            public void onMenuExpanded() {
                binding.balanceMainContentShadow.setVisibility(View.VISIBLE);
                isBottomSheetOpen = true;
                comm.setNavigationDrawerToggleEnabled(false);
            }

            @Override
            public void onMenuCollapsed() {
                binding.fab.collapse();
                binding.balanceMainContentShadow.setVisibility(View.GONE);
                isBottomSheetOpen = false;
                comm.setNavigationDrawerToggleEnabled(true);
            }
        });
    }

    private void sendClicked(){
        SSLVerifierThreadUtil.getInstance(context).validateSSLThread();

        Fragment fragment = new SendFragment();
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).addToBackStack(null).commit();
        comm.setNavigationDrawerToggleEnabled(true);
    }

    private void receiveClicked(){
        Fragment fragment = new ReceiveFragment();
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).addToBackStack(null).commit();
        comm.setNavigationDrawerToggleEnabled(true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void setupViews() {

        initFab();

        noTxMessage = (LinearLayout) binding.getRoot().findViewById(R.id.no_tx_message);//TODO databinding not supporting include tag yet
        noTxMessage.setVisibility(View.GONE);

        //Elevation compat
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //reapply layout attributes after setBackgroundResource
            int bottom = binding.balance1.getPaddingBottom();
            int top = binding.balance1.getPaddingTop();
            int right = binding.balance1.getPaddingRight();
            int left = binding.balance1.getPaddingLeft();
            binding.balance1.setBackgroundResource(R.drawable.container_blue_shadow);
            binding.balance1.setPadding(left, top, right, bottom);
            binding.balance1.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        }

        binding.balance1.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                //TODO this BALANCE_DISPLAY_STATE could be improved
                if (BALANCE_DISPLAY_STATE == SHOW_BTC) {
                    BALANCE_DISPLAY_STATE = SHOW_FIAT;
                    isBTC = false;
                    viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);//TODO OMG WHY?

                } else if (BALANCE_DISPLAY_STATE == SHOW_FIAT) {
                    BALANCE_DISPLAY_STATE = SHOW_HIDE;
                    isBTC = true;
                    viewModel.setBalance(context.getString(R.string.show_balance));

                } else {
                    BALANCE_DISPLAY_STATE = SHOW_BTC;
                    isBTC = true;
                    viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);//TODO OMG WHY?
                }
                PrefsUtil.getInstance(context).setValue(PrefsUtil.KEY_BALANCE_DISPLAY_STATE, BALANCE_DISPLAY_STATE);

                return false;
            }
        });

        accountSpinner = (Spinner) context.findViewById(R.id.account_spinner);
        viewModel.updateAccountList();
        accountsAdapter = new AccountAdapter(context, R.layout.spinner_title_bar, viewModel.getActiveAccountAndAddressList());
        accountsAdapter.setDropDownViewResource(R.layout.spinner_title_bar_dropdown);
        accountSpinner.setAdapter(accountsAdapter);
        accountSpinner.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP && MainActivity.drawerIsOpen) {
                    return true;
                } else if (isBottomSheetOpen) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        accountSpinner.post(new Runnable() {
            public void run() {
                accountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                        //Refresh balance header and tx list
                        viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> arg0) {
                        ;
                    }
                });
            }
        });

        transactionAdapter = new TransactionAdapter(context);
        layoutManager = new LinearLayoutManager(context);
        binding.rvTransactions.setLayoutManager(layoutManager);
        transactionAdapter.setItems(viewModel.getTransactionList());
        binding.rvTransactions.setAdapter(transactionAdapter);

        binding.rvTransactions.setOnScrollListener(new CollapseActionbarScrollListener() {
            @Override
            public void onMoved(int distance) {

                binding.balance1.setTranslationY(-distance);
            }
        });

        // drawerTitle account now that wallet has been created
        if (PrefsUtil.getInstance(context).getValue(PrefsUtil.KEY_INITIAL_ACCOUNT_NAME, "").length() > 0) {
            PayloadFactory.getInstance().get().getHdWallet().getAccounts().get(0).setLabel(PrefsUtil.getInstance(context).getValue(PrefsUtil.KEY_INITIAL_ACCOUNT_NAME, ""));
            PrefsUtil.getInstance(context).removeValue(PrefsUtil.KEY_INITIAL_ACCOUNT_NAME);
            PayloadBridge.getInstance(context).remoteSaveThread();
            accountsAdapter.notifyDataSetChanged();
        }

        if (!OSUtil.getInstance(context).isServiceRunning(info.blockchain.wallet.websocket.WebSocketService.class)) {
            context.startService(new Intent(context, info.blockchain.wallet.websocket.WebSocketService.class));
        } else {
            context.stopService(new Intent(context, info.blockchain.wallet.websocket.WebSocketService.class));
            context.startService(new Intent(context, info.blockchain.wallet.websocket.WebSocketService.class));
        }

        binding.balanceMainContentShadow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.fab.collapse();
            }
        });

        rowViewState = new HashMap<View, Boolean>();

        noTxMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Animation bounce = AnimationUtils.loadAnimation(context, R.anim.jump);
                binding.fab.startAnimation(bounce);
            }
        });

        binding.swipeContainer.setProgressViewEndTarget(false, (int) (getResources().getDisplayMetrics().density * (72 + 20)));
        binding.swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                Intent intent = new Intent(BalanceFragment.ACTION_INTENT);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            }
        });
        binding.swipeContainer.setColorSchemeResources(R.color.blockchain_receive_green,
                R.color.blockchain_blue,
                R.color.blockchain_send_red);
    }

    private void onRowClick(final View view, final int position) {

        if (viewModel.getTransactionList() != null) {
            final Tx transactionSummary = viewModel.getTransactionList().get(position);
            final String strTx = transactionSummary.getHash();
            final String strConfirmations = Long.toString(transactionSummary.getConfirmations());

            try {
                mIsViewExpanded = rowViewState.get(view);
            } catch (Exception e) {
                mIsViewExpanded = false;
            }

            //Set views
            View detailsView = view;

            final ScrollView txsDetails = (ScrollView) detailsView.findViewById(R.id.txs_details);
            final TextView tvOutAddr = (TextView) detailsView.findViewById(R.id.tx_from_addr);

            final TextView tvFee = (TextView) detailsView.findViewById(R.id.tx_fee_value);
            final TextView tvTxHash = (TextView) detailsView.findViewById(R.id.tx_hash);
            final ProgressBar progressView = (ProgressBar) detailsView.findViewById(R.id.progress_view);
            final TextView tvStatus = (TextView) detailsView.findViewById(R.id.transaction_status);
            final ImageView ivStatus = (ImageView) detailsView.findViewById(R.id.transaction_status_icon);
            final LinearLayout toAddressContainer = (LinearLayout) detailsView.findViewById(R.id.tx_details_to_include_container);

            final LinearLayout feeContainer = (LinearLayout) detailsView.findViewById(R.id.tx_fee_container);
            final View feeSeparator = detailsView.findViewById(R.id.tx_fee_separator);

            if (!mIsViewExpanded) {
                if (prevRowClicked != null && prevRowClicked == binding.rvTransactions.getLayoutManager().getChildAt(position)) {
                    txsDetails.setVisibility(View.INVISIBLE);
                    prevRowClicked.findViewById(R.id.tx_row).setBackgroundResource(R.drawable.selector_pearl_white_tx);
                    prevRowClicked = null;
                    return;
                }

                txsDetails.setVisibility(View.VISIBLE);
                progressView.setVisibility(View.VISIBLE);
                tvOutAddr.setVisibility(View.INVISIBLE);
                toAddressContainer.setVisibility(View.INVISIBLE);
                tvStatus.setVisibility(View.INVISIBLE);
                ivStatus.setVisibility(View.INVISIBLE);

                if (transactionSummary.getDirection().equals(MultiAddrFactory.RECEIVED)) {
                    feeContainer.setVisibility(View.GONE);
                    feeSeparator.setVisibility(View.GONE);
                }

                tvTxHash.setText(strTx);
                tvTxHash.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {

                        if (event.getAction() == MotionEvent.ACTION_UP && !strTx.isEmpty()) {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://blockchain.info/tx/" + strTx));
                            startActivity(browserIntent);
                        }
                        return true;
                    }
                });
                tvStatus.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {

                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            if (tvStatus.getTag() != null) {
                                String tag = tvStatus.getTag().toString();
                                String text = tvStatus.getText().toString();
                                tvStatus.setText(tag);
                                tvStatus.setTag(text);
                            }
                        }
                        return true;
                    }
                });

                TextView tvResult = (TextView) view.findViewById(R.id.result);
                tvResult.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {

                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            isBTC = (isBTC) ? false : true;
                            viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);
                        }
                        return true;
                    }
                });

                txsDetails.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {

                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            onRowClick(view, position);
                        }
                        return true;

                        //To be used with advance send tx display
                        // Disallow the touch request for parent scroll on touch of child view
                        //v.getParent().requestDisallowInterceptTouchEvent(true);
                        //return false;
                    }
                });

                //Get Details
                if (transactionSummary.getHash().isEmpty()) {
                    context.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText(context.getString(R.string.WAITING));

                            tvOutAddr.setText("");
                            tvTxHash.setText("");

                            tvOutAddr.setVisibility(View.VISIBLE);
                            tvStatus.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    new AsyncTask<Void, Void, String>() {

                        @Override
                        protected String doInBackground(Void... params) {

                            String stringResult = null;
                            try {
                                stringResult = WebUtil.getInstance().getURL(WebUtil.TRANSACTION + strTx + "?format=json");

                            } catch (JSONException e) {
                                e.printStackTrace();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            return stringResult;
                        }

                        @Override
                        protected void onPostExecute(String stringResult) {
                            super.onPostExecute(stringResult);

                            if (stringResult != null) {

                                //Get transaction details
                                Transaction transactionDetails = null;
                                try {
//                                    Log.v("", "stringResult: " + stringResult);
                                    transactionDetails = new Transaction(new JSONObject(stringResult));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                progressView.setVisibility(View.GONE);

                                //Fee
                                String strFiat = PrefsUtil.getInstance(context).getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
                                String fee = (MonetaryUtil.getInstance().getFiatFormat(strFiat).format(btc_fx * (transactionDetails.getFee() / 1e8)) + " " + strFiat);
                                if (isBTC)
                                    fee = (MonetaryUtil.getInstance(context).getDisplayAmountWithFormatting(transactionDetails.getFee()) + " " + viewModel.getDisplayUnits());
                                tvFee.setText(fee);

                                //Filter non-change addresses
                                Pair<HashMap<String, Long>, HashMap<String, Long>> pair = viewModel.filterNonChangeAddresses(transactionDetails, transactionSummary);

                                //From
                                HashMap<String,Long> inputMap = pair.first;

                                //TODO start- Product team considering separate fields
                                ArrayList<String> labelList = new ArrayList<String>();
                                Set<Map.Entry<String, Long>> entrySet = inputMap.entrySet();
                                for(Map.Entry<String, Long> set : entrySet){
                                    String label = viewModel.addressToLabel(set.getKey());
                                    if(!labelList.contains(label))
                                        labelList.add(label);
                                }

                                String inputMapString = StringUtils.join(labelList.toArray(), "\n");
                                tvOutAddr.setText(viewModel.addressToLabel(inputMapString));
                                //todo end

                                //To Address
                                HashMap<String,Long> outputMap = pair.second;
                                toAddressContainer.removeAllViews();

                                for (Map.Entry<String, Long> item : outputMap.entrySet()) {

                                    View v = LayoutInflater.from(context).inflate(R.layout.include_tx_details_to, toAddressContainer, false);
                                    TextView tvToAddr = (TextView) v.findViewById(R.id.tx_to_addr);

                                    TextView tvToAddrTotal = (TextView) v.findViewById(R.id.tx_to_addr_total);
                                    toAddressContainer.addView(v);

                                    tvToAddr.setText(viewModel.addressToLabel(item.getKey()));
                                    long amount = item.getValue();
                                    String amountS = (MonetaryUtil.getInstance().getFiatFormat(strFiat).format(btc_fx * (amount / 1e8)) + " " + strFiat);
                                    if (isBTC)
                                        amountS = (MonetaryUtil.getInstance(context).getDisplayAmountWithFormatting(amount) + " " + viewModel.getDisplayUnits());

                                    tvFee.setText(fee);
                                    tvToAddrTotal.setText(amountS);
                                }

                                tvStatus.setTag(strConfirmations);

                                if (transactionSummary.getConfirmations() >= nbConfirmations) {
                                    ivStatus.setImageResource(R.drawable.ic_done_grey600_24dp);
                                    tvStatus.setText(getString(R.string.COMPLETE));
                                } else {
                                    ivStatus.setImageResource(R.drawable.ic_schedule_grey600_24dp);
                                    tvStatus.setText(getString(R.string.PENDING));
                                }
                                tvOutAddr.setVisibility(View.VISIBLE);
                                toAddressContainer.setVisibility(View.VISIBLE);
                                tvStatus.setVisibility(View.VISIBLE);
                                ivStatus.setVisibility(View.VISIBLE);

                                if (inputMap.size() >= 2 || outputMap.size() >= 2)//details view needs to be scrollable now
                                        txsDetails.setOnTouchListener(new OnTouchListener() {
                                            @Override
                                            public boolean onTouch(View v, MotionEvent event) {
                                                v.getParent().requestDisallowInterceptTouchEvent(true);
                                                return false;
                                            }
                                        });
                            }
                        }
                    }.execute();
                }
            }

            if (originalHeight == 0) {
                originalHeight = view.getHeight();
            }

            newHeight = originalHeight + txsDetails.getHeight();

            if (!mIsViewExpanded) {
                expandView(view, txsDetails);

            } else {
                collapseView(view, txsDetails);
            }

            rowViewState.put(view, mIsViewExpanded);
        }
    }

    private void expandView(View view, ScrollView txsDetails) {

        view.setBackgroundColor(getResources().getColor(R.color.white));

        //Fade Details in - expansion of row will create slide down effect
        txsDetails.setVisibility(View.VISIBLE);
        txsDetails.setAnimation(AnimationUtils.loadAnimation(context, R.anim.abc_fade_in));
        txsDetails.setEnabled(true);

        mIsViewExpanded = !mIsViewExpanded;
        ValueAnimator resizeAnimator = ValueAnimator.ofInt(originalHeight, newHeight);
        startAnim(view, resizeAnimator);
    }

    private void collapseView(View view, final ScrollView txsDetails) {

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        view.setBackgroundResource(outValue.resourceId);

        mIsViewExpanded = !mIsViewExpanded;
        ValueAnimator resizeAnimator = ValueAnimator.ofInt(newHeight, originalHeight);

        txsDetails.setAnimation(AnimationUtils.loadAnimation(context, R.anim.slide_down));

        Animation anim = new AlphaAnimation(1.00f, 0.00f);
        anim.setDuration(expandDuration / 2);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                txsDetails.setVisibility(View.INVISIBLE);
                txsDetails.setEnabled(false);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        txsDetails.startAnimation(anim);

        startAnim(view, resizeAnimator);
    }

    private void startAnim(final View view, ValueAnimator resizeAnimator) {

        //Set and start row collapse/expand
        resizeAnimator.setDuration(expandDuration);
        resizeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        resizeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                Integer value = (Integer) animation.getAnimatedValue();
                view.getLayoutParams().height = value.intValue();
                view.requestLayout();
            }
        });

        resizeAnimator.start();
    }

    @Override
    public void onRefreshAccounts() {
        //TODO revise
        if(accountSpinner != null)
            setAccountSpinner();

        context.runOnUiThread(() -> {
            if (accountsAdapter != null) accountsAdapter.notifyDataSetChanged();
        });
    }

    @Override
    public void onAccountSizeChange() {
        if(accountSpinner != null)
            accountSpinner.setSelection(0);
    }

    @Override
    public void onRefreshBalanceAndTransactions() {

        //Notify adapters of change
        accountsAdapter.notifyDataSetChanged();
        transactionAdapter.notifyDataSetChanged();

        //Display help text to user if no transactionList on selected account/address
        if (viewModel.getTransactionList().size() > 0) {
            binding.rvTransactions.setVisibility(View.VISIBLE);
            noTxMessage.setVisibility(View.GONE);
        } else {
            binding.rvTransactions.setVisibility(View.GONE);
            noTxMessage.setVisibility(View.VISIBLE);
        }
    }

    interface Communicator {

        public void setNavigationDrawerToggleEnabled(boolean enabled);

        public void resetNavigationDrawer();
    }

//    private class TxAdapter extends RecyclerView.Adapter<TxAdapter.ViewHolder> {
//
//        @Override
//        public TxAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
//
//            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.txs_layout_expandable, parent, false);
//            return new ViewHolder(v);
//        }
//
//        @Override
//        public void onBindViewHolder(final ViewHolder holder, final int position) {
//
//            String strFiat = PrefsUtil.getInstance(context).getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
//
//            if (viewModel.getTransactionList() != null) {
//                final Tx tx = viewModel.getTransactionList().get(position);
//                double _btc_balance = tx.getAmount() / 1e8;
//                double _fiat_balance = btc_fx * _btc_balance;
//
//                View txTouchView = holder.itemView.findViewById(R.id.tx_touch_view);
//
//                TextView tvResult = (TextView) holder.itemView.findViewById(R.id.result);
//                tvResult.setTextColor(Color.WHITE);
//
//                TextView tvTS = (TextView) holder.itemView.findViewById(R.id.ts);
//                tvTS.setText(DateUtil.getInstance(context).formatted(tx.getTS()));
//
//                TextView tvDirection = (TextView) holder.itemView.findViewById(R.id.direction);
//                String dirText = tx.getDirection();
//                if (dirText.equals(MultiAddrFactory.MOVED))
//                    tvDirection.setText(getResources().getString(R.string.MOVED));
//                if (dirText.equals(MultiAddrFactory.RECEIVED))
//                    tvDirection.setText(getResources().getString(R.string.RECEIVED));
//                if (dirText.equals(MultiAddrFactory.SENT))
//                    tvDirection.setText(getResources().getString(R.string.SENT));
//
//                if (isBTC) {
//                    span1 = Spannable.Factory.getInstance().newSpannable(MonetaryUtil.getInstance(context).getDisplayAmountWithFormatting(Math.abs(tx.getAmount())) + " " + viewModel.getDisplayUnits());
//                    span1.setSpan(new RelativeSizeSpan(0.67f), span1.length() - viewModel.getDisplayUnits().length(), span1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//                } else {
//                    span1 = Spannable.Factory.getInstance().newSpannable(MonetaryUtil.getInstance().getFiatFormat(strFiat).format(Math.abs(_fiat_balance)) + " " + strFiat);
//                    span1.setSpan(new RelativeSizeSpan(0.67f), span1.length() - 3, span1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//                }
//                if (tx.isMove()) {
//                    tvResult.setBackgroundResource(tx.getConfirmations() < nbConfirmations ? R.drawable.rounded_view_lighter_blue_50 : R.drawable.rounded_view_lighter_blue);
//                    tvDirection.setTextColor(context.getResources().getColor(tx.getConfirmations() < nbConfirmations ? R.color.blockchain_transfer_blue_50 : R.color.blockchain_transfer_blue));
//                } else if (_btc_balance < 0.0) {
//                    tvResult.setBackgroundResource(tx.getConfirmations() < nbConfirmations ? R.drawable.rounded_view_red_50 : R.drawable.rounded_view_red);
//                    tvDirection.setTextColor(context.getResources().getColor(tx.getConfirmations() < nbConfirmations ? R.color.blockchain_red_50 : R.color.blockchain_send_red));
//                } else {
//                    tvResult.setBackgroundResource(tx.getConfirmations() < nbConfirmations ? R.drawable.rounded_view_green_50 : R.drawable.rounded_view_green);
//                    tvDirection.setTextColor(context.getResources().getColor(tx.getConfirmations() < nbConfirmations ? R.color.blockchain_green_50 : R.color.blockchain_receive_green));
//                }
//
//                TextView tvWatchOnly = (TextView) holder.itemView.findViewById(R.id.watch_only);
//                if(tx.isWatchOnly()){
//                    tvWatchOnly.setVisibility(View.VISIBLE);
//                }else{
//                    tvWatchOnly.setVisibility(View.GONE);
//                }
//
//                tvResult.setText(span1);
//
//                tvResult.setOnTouchListener(new OnTouchListener() {
//                    @Override
//                    public boolean onTouch(View v, MotionEvent event) {
//
//                        FrameLayout parent = (FrameLayout) v.getParent();
//                        event.setLocation(v.getX() + (v.getWidth() / 2), v.getY() + (v.getHeight() / 2));
//                        parent.onTouchEvent(event);
//
//                        if (event.getAction() == MotionEvent.ACTION_UP) {
//                            isBTC = (isBTC) ? false : true;
//                            viewModel.updateBalanceAndTransactionList(null, accountSpinner.getSelectedItemPosition(), isBTC);
//                        }
//                        return true;
//                    }
//                });
//
//                txTouchView.setOnTouchListener(new OnTouchListener() {
//                    @Override
//                    public boolean onTouch(View v, MotionEvent event) {
//
//                        FrameLayout parent = (FrameLayout) v.getParent();
//                        event.setLocation(event.getX(), v.getY() + (v.getHeight() / 2));
//                        parent.onTouchEvent(event);
//
//                        if (event.getAction() == MotionEvent.ACTION_UP) {
//                            onRowClick(holder.itemView, position);
//                        }
//                        return true;
//                    }
//                });
//            }
//        }
//
//        @Override
//        public int getItemCount() {
//            if (viewModel.getTransactionList() == null) return 0;
//            return viewModel.getTransactionList().size();
//        }
//
//        @Override
//        public int getItemViewType(int position) {
//            return position;
//        }
//
//        public class ViewHolder extends RecyclerView.ViewHolder {
//
//            public ViewHolder(View view) {
//                super(view);
//            }
//        }
//    }

    public abstract class CollapseActionbarScrollListener extends RecyclerView.OnScrollListener {

        private int mToolbarOffset = 0;

        public CollapseActionbarScrollListener() {
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            binding.swipeContainer.setEnabled(layoutManager.findFirstCompletelyVisibleItemPosition() == 0);

            //Only bring heading back down after 2nd item visible (0 = heading)
            if (layoutManager.findFirstCompletelyVisibleItemPosition() <= 2) {

                if ((mToolbarOffset < balanceBarHeight && dy > 0) || (mToolbarOffset > 0 && dy < 0)) {
                    mToolbarOffset += dy;
                }

                clipToolbarOffset();
                onMoved(mToolbarOffset);
            }
        }

        private void clipToolbarOffset() {
            if (mToolbarOffset > balanceBarHeight) {
                mToolbarOffset = balanceBarHeight;
            } else if (mToolbarOffset < 0) {
                mToolbarOffset = 0;
            }
        }

        public abstract void onMoved(int distance);
    }

    private class AccountAdapter extends ArrayAdapter<String> {

        Context context;
        int layoutResourceId;

        public AccountAdapter(Context context, int layoutResourceId, ArrayList<String> data) {
            super(context, layoutResourceId, data);
            this.layoutResourceId = layoutResourceId;
            this.context = context;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            View view = convertView;
            if (null == view) {
                view = LayoutInflater.from(this.getContext()).inflate(R.layout.spinner_title_bar, null);
                ((TextView) view).setText(getItem(position));
            }
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            view.setLayoutParams(params);
            return view;
        }
    }
}
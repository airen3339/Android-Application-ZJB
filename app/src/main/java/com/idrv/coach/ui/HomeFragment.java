package com.idrv.coach.ui;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.idrv.coach.R;
import com.idrv.coach.bean.AdvBean;
import com.idrv.coach.bean.AdvShareInfo;
import com.idrv.coach.bean.HomePage;
import com.idrv.coach.bean.Message;
import com.idrv.coach.bean.WebParamBuilder;
import com.idrv.coach.bean.WebShareBean;
import com.idrv.coach.bean.event.EventConstant;
import com.idrv.coach.data.constants.SPConstant;
import com.idrv.coach.data.manager.RxBusManager;
import com.idrv.coach.data.manager.UrlParserManager;
import com.idrv.coach.data.model.HomePageModel;
import com.idrv.coach.ui.adapter.HomeAdapter;
import com.idrv.coach.ui.fragment.BaseFragment;
import com.idrv.coach.ui.view.AdvDialog;
import com.idrv.coach.ui.view.decoration.SpacesItemDecoration;
import com.idrv.coach.ui.widget.SwipeRefreshLayout;
import com.idrv.coach.utils.Logger;
import com.idrv.coach.utils.PixelUtil;
import com.idrv.coach.utils.PreferenceUtil;
import com.idrv.coach.utils.StatisticsUtil;
import com.idrv.coach.utils.TimeUtil;
import com.idrv.coach.utils.ValidateUtil;
import com.idrv.coach.utils.helper.ResHelper;
import com.idrv.coach.utils.helper.UIHelper;
import com.zjb.loader.ZjbImageLoader;
import com.zjb.volley.utils.NetworkUtil;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * time:2016/8/1
 * description:
 *
 * @author sunjianfei
 */
public class HomeFragment extends BaseFragment<HomePageModel> {
    public static final String KEY_FIRST_USE_HOME_PAGE = "first_use_home_page";

    @InjectView(R.id.recycler_view)
    RecyclerView mRecyclerView;
    @InjectView(R.id.refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @InjectView(R.id.use_time_tv)
    TextView mUseTimeTv;
    @InjectView(R.id.header_layout)
    FrameLayout mHeaderLayout;
    @InjectView(R.id.main_layout)
    FrameLayout mMainLayout;

    HomeAdapter mAdapter;
    boolean isFirstRefresh = true;


    @Override
    public View createView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.frag_home, container, false);
    }

    @Override
    protected boolean hasBaseLayout() {
        return true;
    }

    @Override
    protected int getProgressBg() {
        return R.color.bg_main;
    }

    @Override
    public void initView(View view) {
        ButterKnife.inject(this, view);
        mAdapter = new HomeAdapter();

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);

        mRecyclerView.addItemDecoration(new SpacesItemDecoration(0, 0, 0, (int) PixelUtil.dp2px(30), true));
        mRecyclerView.setAdapter(mAdapter);
        //??????????????????
//        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
//            @Override
//            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//                super.onScrolled(recyclerView, dx, dy);
//                mCurrentScroll += dy;
//                mHeaderLayout.setTranslationY(mCurrentScroll);
//            }
//        });


        //???????????????????????????
        mSwipeRefreshLayout.setMode(SwipeRefreshLayout.Mode.PULL_FROM_START);
        mSwipeRefreshLayout.setOnRefreshListener(this::loadHistory);

        initViewModel();
        registerEvent();
    }

    @Override
    protected void onLazyLoad() {
        showGuideView();
    }

    @Override
    public void onClickRetry() {
        if (NetworkUtil.isConnected(getActivity())) {
            showProgressView();
            refresh();
        } else {
            UIHelper.shortToast(R.string.network_error);
        }
    }

    private void registerEvent() {
        //1.??????????????????????????????????????????????????????
        RxBusManager.register(this, EventConstant.KEY_NEWS_OR_WEBSITE_SHARE_COMPLETE, String.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateShareCus, Logger::e);
        //2.?????????????????????
        RxBusManager.register(this, EventConstant.KEY_HOME_NEW_MESSAGE, Message.class)
                .subscribe(this::onNewMessage, Logger::e);
    }

    private void initViewModel() {
        mViewModel = new HomePageModel();
        getHomePageCache();
        getPopAdv();
    }


    /**
     * ????????????????????????
     */
    private void getPopAdv() {
        //1.????????????????????????
        Subscription cacheSubscription = mViewModel.getAdvCache()
                .subscribe(__ -> Logger.e("success"), Logger::e);
        //2.????????????????????????????????????????????????
        Subscription subscription = mViewModel.getPopAdv()
                .subscribe(__ -> Logger.e("success"), Logger::e);
        addSubscription(cacheSubscription);
        addSubscription(subscription);
    }

    /**
     * ????????????????????????
     */
    private void getHomePageCache() {
        Subscription subscription = mViewModel.getHomePageCache()
                .doOnTerminate(this::onCacheComplete)
                .subscribe(this::onNext, Logger::e);
        addSubscription(subscription);
    }

    /**
     * ???????????????????????????
     */
    private void refresh() {
        Subscription subscription = mViewModel.refresh()
                .doOnTerminate(() -> {
                    //??????????????????,??????????????????.???????????????
                    new Handler().postDelayed(() -> mSwipeRefreshLayout.setRefreshing(false), 1000);
                })
                .subscribe(this::onNext, this::onError, this::showContentView);
        addSubscription(subscription);
    }

    /**
     * ??????????????????
     */
    private void loadHistory() {
        Subscription subscription = mViewModel.loadHistory()
                .doOnTerminate(() -> mSwipeRefreshLayout.setRefreshing(false))
                .subscribe(this::onLoadHistoryNext, Logger::e);
        addSubscription(subscription);
    }

    private void onNext(HomePage page) {
        //??????????????????
        ZjbImageLoader.create(page.getBgImaUrl())
                .setDisplayType(ZjbImageLoader.DISPLAY_FADE_IN)
                .setQiniu(ResHelper.getScreenWidth(), ResHelper.getScreenHeight())
                .setFadeInTime(1000)
                .setDefaultDrawable(new ColorDrawable(ContextCompat.getColor(getContext(),R.color.bg_main)))
                .into(mMainLayout);

        List<Message> messages = page.getMessages();
        mUseTimeTv.setText(page.getLoginSum() + "");

        mAdapter.setData(messages);
        mAdapter.notifyDataSetChanged();
        mRecyclerView.scrollToPosition(mAdapter.getItemCount() - 1);
        //????????????????????????
        if (isFirstRefresh) {
            showAllTips();
        }
        isFirstRefresh = false;
        //???????????????
        showContentView();
    }

    private void onLoadHistoryNext(HomePage page) {
        List<Message> messages = page.getMessages();
        if (ValidateUtil.isValidate(messages)) {
            mAdapter.addDataFromFirst(messages);
            mAdapter.notifyDataSetChanged();
            mRecyclerView.scrollToPosition(messages.size());
        } else {
            UIHelper.shortToast(R.string.no_more_message);
        }
    }

    private void onError(Throwable e) {
        UIHelper.shortToast(R.string.network_error);
        if (null == mViewModel.getHomePage()) {
            showErrorView();
        }
    }


    private void onCacheComplete() {
        mSwipeRefreshLayout.setRefreshing(true);
        refresh();
    }

    /**
     * ??????????????????
     *
     * @param s
     */
    private void updateShareCus(String s) {
        mViewModel.updateData();
    }

    /**
     * ???????????????
     *
     * @param message
     */
    private void onNewMessage(Message message) {
        //????????????
        Subscription subscription = mViewModel.updateMessageCache(message)
                .subscribe(date -> {
                }, Logger::e);
        addSubscription(subscription);

        mAdapter.addData(message);
        mAdapter.notifyDataSetChanged();
        mRecyclerView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    /**
     * ???????????????????????????
     */
    private void showAllTips() {
        //????????????
        AdvBean mAdvBean = mViewModel.getAdvBean();
        //?????????????????????url
        String loadedAdvPicUrl = PreferenceUtil.getString(SPConstant.KEY_ADV_PIC);
        //?????????????????????
        String viewsAdvId = PreferenceUtil.getString(SPConstant.KEY_ADV_VIEWS);
        //??????????????????
        String deadTime = null != mAdvBean ? mAdvBean.getDeadtime() : null;
        boolean isAdvValid = true;
        if (!TimeUtil.compareDate(deadTime)) {
            isAdvValid = false;
        }
        //???????????????????????????
        String time = PreferenceUtil.getString(SPConstant.KEY_SHOW_ADV_TIME);
        //????????????????????????????????????2???,????????????2???,????????????,????????????
        boolean isValidTime = true;
        if (!TextUtils.isEmpty(time)) {
            isValidTime = TimeUtil.compareDate(time, 2);
        }

        if (null != mAdvBean
                && mAdvBean.getImageUrl().equals(loadedAdvPicUrl)
                && !mAdvBean.getId().equals(viewsAdvId)
                && isValidTime
                && isAdvValid) {
            //2.??????
            AdvDialog dialog = new AdvDialog(getContext());
            dialog.showImage(mAdvBean.getImageUrl());
            dialog.setOnDismissListener(dl -> {
                //???????????????,???????????????,2????????????
                PreferenceUtil.putString(SPConstant.KEY_SHOW_ADV_TIME, TimeUtil.getSimpleTime());
            });
            dialog.setClickListener(() -> {
                //????????????????????????
                StatisticsUtil.onEvent(R.string.visit_home_educate_h5);
                PreferenceUtil.putString(SPConstant.KEY_ADV_VIEWS, mAdvBean.getId());
                PreferenceUtil.putString(SPConstant.KEY_SHOW_ADV_TIME, TimeUtil.getSimpleTime());
                String schema = mAdvBean.getSchema();
                //?????????????????????
                if (!TextUtils.isEmpty(schema)) {
                    //??????H5????????????
                    UrlParserManager.getInstance().addParams(UrlParserManager.METHOD_ADVTYPE, "0");
                    WebShareBean shareBean = null;
                    AdvShareInfo shareInfo = mAdvBean.getShareInfo();
                    //??????????????????
                    if (mAdvBean.isShare() && null != shareInfo) {
                        String shareUrl = shareInfo.getShareUrl();
                        shareBean = new WebShareBean();
                        shareBean.setShareTitle(shareInfo.getShareTitle());
                        shareBean.setShareContent(shareInfo.getShareContent());
                        shareBean.setShareUrl(shareUrl);
                        shareBean.setShareImageUrl(shareInfo.getShareImageUrl());
                    }
                    ToolBoxWebActivity.launch(getContext(), WebParamBuilder.create()
                            .setTitle(mAdvBean.getTitle())
                            .setUrl(schema)
                            .setPageTag(R.string.share_home_educate_h5)
                            .setShareBean(shareBean));
                }
                dialog.dismiss();
            });
            dialog.show();
        }
    }

    /**
     * ??????????????? ????????????
     */
    private void showGuideView() {
        //???????????????????????????,?????????????????????,???????????????
        if (!PreferenceUtil.getBoolean(KEY_FIRST_USE_HOME_PAGE)) {
            View mGuideView = LayoutInflater.from(getContext()).inflate(R.layout.vw_home_page_guide, null, false);
            View mRootLayout = mGuideView.findViewById(R.id.root_layout);
            mGuideView.getBackground().setAlpha(190);
            mRootLayout.setOnTouchListener((__, ___) -> true);
            mGuideView.findViewById(R.id.btn_ok).setOnClickListener(v -> {
                mGuideView.setVisibility(View.GONE);
                PreferenceUtil.putBoolean(KEY_FIRST_USE_HOME_PAGE, true);
            });
            ((ViewGroup) mRecyclerView.getRootView().findViewById(android.R.id.content)).addView(mGuideView);
        }
    }

}

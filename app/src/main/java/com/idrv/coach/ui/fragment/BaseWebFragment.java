package com.idrv.coach.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.lzyzsd.jsbridge.DefaultHandler;
import com.idrv.coach.R;
import com.idrv.coach.bean.User;
import com.idrv.coach.bean.WebParamBuilder;
import com.idrv.coach.bean.WebShareBean;
import com.idrv.coach.bean.WxPayInfo;
import com.idrv.coach.bean.event.EventConstant;
import com.idrv.coach.bean.share.ShareWebProvider;
import com.idrv.coach.data.manager.LoginManager;
import com.idrv.coach.data.manager.RxBusManager;
import com.idrv.coach.data.manager.UrlParserManager;
import com.idrv.coach.data.manager.WChatManager;
import com.idrv.coach.ui.view.ProgressWebView;
import com.idrv.coach.utils.BitmapUtil;
import com.idrv.coach.utils.Logger;
import com.idrv.coach.utils.StatisticsUtil;
import com.idrv.coach.wxapi.WXEntryActivity;
import com.tencent.mm.sdk.modelbase.BaseResp;
import com.tencent.mm.sdk.modelpay.PayReq;
import com.zjb.loader.ZjbImageLoader;
import com.zjb.volley.utils.GsonUtil;

import org.json.JSONObject;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.Optional;
import rx.android.schedulers.AndroidSchedulers;

/**
 * time:2016/8/18
 * description:
 *
 * @author sunjianfei
 */
public abstract class BaseWebFragment extends BaseFragment {
    public static final String KEY_WEB_PARAM = "web_param";
    protected String mUrl;
    protected WebParamBuilder mParamBuilder;
    private String mWxPrePayId;

    @Optional
    @InjectView(R.id.web_view)
    public ProgressWebView mWebView;
    @Optional
    @InjectView(R.id.progress_bar)
    public ProgressBar mProgressBar;
    @InjectView(R.id.title_tv)
    TextView mTitleTv;
    @InjectView(R.id.right_btn)
    ImageView mRightBtn;

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.frag_h5, container, false);
    }

    @Override
    public void initView(View view) {
        ButterKnife.inject(this,view);
        registerEvent();
        initialize();
    }

    /**
     * ??????Rx??????
     */
    private void registerEvent() {
        //1.????????????
        RxBusManager.register(this, EventConstant.KEY_PAY_RESULT, Integer.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::processPayResult, Logger::e);
    }

    protected void initialize() {
        //??????H5???????????????
        UrlParserManager.getInstance().addParams(UrlParserManager.METHOD_CHANNEL, "app");
        mParamBuilder = getParams();
        mUrl = UrlParserManager.getInstance().parsePlaceholderUrl(mParamBuilder.getUrl());

        mWebView.setProgressbar(mProgressBar);
        WebSettings ws = mWebView.getSettings();
        ws.setBuiltInZoomControls(false); // ??????
        ws.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setSaveFormData(true);
        ws.setDomStorageEnabled(true);//?????? database storage API ??????
        //???????????????UserAgent
        String agent = ws.getUserAgentString();
        ws.setUserAgentString(createUserAgent(agent));
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url != null && url.startsWith("http://"))
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });
        mWebView.loadUrl(mUrl);
        mWebView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                //??????????????????????????????
                if (keyCode == KeyEvent.KEYCODE_BACK && mWebView.canGoBack()) {
                    mWebView.goBack();   //??????
                    return true;    //?????????
                }
            }
            return false;
        });

        mWebView.setDefaultHandler(new DefaultHandler());
        //??????????????????
        mWebView.registerHandler("shareJs", (data, function) -> {
            try {
                if (!TextUtils.isEmpty(data)) {
                    WebShareBean shareBean = GsonUtil.fromJson(data, WebShareBean.class);
                    shareJs(shareBean);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        //????????????????????????
        mWebView.registerHandler("webTitleJs", ((data, function) -> webTitleJs(data)));
        //??????????????????????????????
        mWebView.registerHandler("disableShareJs", ((data, function) -> {
            if ("true".equals(data)) {
                disableShareJs(true);
            } else {
                disableShareJs(false);
            }

        }));

        //??????????????????
        mWebView.registerHandler("wxPayJs", ((data, function) -> wxPayJs(data)));
    }

    /**
     * ????????????
     *
     * @param disable
     */
    private void disableShareJs(boolean disable) {
        mRightBtn.setVisibility(disable ? View.GONE : View.VISIBLE);
    }

    /**
     * JS??????,????????????
     */
    private void webTitleJs(String title) {
        mTitleTv.setText(title);
    }

    /**
     * JS????????????
     */
    private void shareJs(WebShareBean shareBean) {
        UrlParserManager manager = UrlParserManager.getInstance();
        shareBean.setShareTitle(manager.parsePlaceholderUrl(shareBean.getShareTitle()));
        shareBean.setShareUrl(manager.parsePlaceholderUrl(shareBean.getShareUrl()));
        shareBean.setShareContent(manager.parsePlaceholderUrl(shareBean.getShareContent()));
        shareBean.setShareImageUrl(manager.parsePlaceholderUrl(shareBean.getShareImageUrl()));

        //??????????????????????????????
        disableShareJs(false);
        mRightBtn.setImageResource(R.drawable.icon_news_share);
        mRightBtn.setOnClickListener(v -> share(shareBean));

        //?????????????????????,?????????????????????
        String shareIcon = shareBean.getShareImageUrl();
        if (!TextUtils.isEmpty(shareIcon) && shareIcon.startsWith("http://")) {
            ZjbImageLoader.create(shareIcon)
                    .setDisplayType(ZjbImageLoader.DISPLAY_DEFAULT)
                    .load();
        }
    }

    /**
     * JS????????????????????????
     *
     * @param orderInfo
     */
    private void wxPayJs(String orderInfo) {
        try {
            WxPayInfo info = GsonUtil.fromJson(orderInfo, WxPayInfo.class);
            mWxPrePayId = info.getPrepayid();
            PayReq req = new PayReq();
            req.appId = info.getAppid();
            req.partnerId = info.getPartnerid();
            req.prepayId = info.getPrepayid();
            req.nonceStr = info.getNoncestr();
            req.timeStamp = info.getTimestamp();
            req.packageValue = "Sign=WXPay";
            req.sign = info.getSign();
            WChatManager.getInstance().WXAPI.sendReq(req);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ??????????????????
     *
     * @param code
     */
    private void processPayResult(int code) {
        try {
            boolean payStatus = code == BaseResp.ErrCode.ERR_OK;
            JSONObject object = new JSONObject();
            object.put("prepayid", mWxPrePayId);
            object.put("payStatus", payStatus);

            //??????JS????????????
            mWebView.callHandler("webWxPayNotify", object.toString(), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ??????
     */
    protected void share(WebShareBean bean) {
        if (mParamBuilder.getPageTag() != 0) {
            //????????????,?????????????????????UID?????????
            User user = LoginManager.getInstance().getLoginUser();
            String uid = user.getUid();
            String nickName = user.getNickname();
            StatisticsUtil.onEvent(mParamBuilder.getPageTag(), uid, nickName);
        }
        ShareWebProvider provider = new ShareWebProvider();
        String targetUrl = bean.getShareUrl();
        String imagePath;
        String url = bean.getShareImageUrl();
        if (!TextUtils.isEmpty(url) && url.startsWith("http://")) {
            imagePath = BitmapUtil.getImagePath(url);
        } else {
            imagePath = url;
        }
        provider.setTitle(bean.getShareTitle());
        provider.setDesc(bean.getShareContent());
        provider.setImagePath(imagePath);
        provider.setUrl(targetUrl);
        WXEntryActivity.launch(getContext(), provider, R.string.share_str, bean.getPageSubject());
    }

    private String createUserAgent(String agent) {
        if (!TextUtils.isEmpty(agent)) {
            int index = agent.indexOf(")");
            String headerStr = agent.substring(0, index);
            String footerStr = agent.substring(index, agent.length());
            return headerStr + ";ejia;zhujiabang" + footerStr;
        }
        return agent;
    }

    public abstract WebParamBuilder getParams();
}

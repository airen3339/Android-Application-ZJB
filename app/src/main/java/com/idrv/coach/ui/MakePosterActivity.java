package com.idrv.coach.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.idrv.coach.R;
import com.idrv.coach.bean.Effect;
import com.idrv.coach.bean.PosterPage;
import com.idrv.coach.bean.SpreadTool;
import com.idrv.coach.bean.WebParamBuilder;
import com.idrv.coach.data.constants.SPConstant;
import com.idrv.coach.data.manager.UrlParserManager;
import com.idrv.coach.ui.view.EditInputDialog;
import com.idrv.coach.ui.view.MakePosterNotifyDialog;
import com.idrv.coach.ui.view.SelectPhotoDialog;
import com.idrv.coach.utils.BitmapUtil;
import com.idrv.coach.utils.FileUtil;
import com.idrv.coach.utils.Logger;
import com.idrv.coach.utils.PictureUtil;
import com.idrv.coach.utils.PixelUtil;
import com.idrv.coach.utils.PreferenceUtil;
import com.idrv.coach.utils.QRCodeUtil;
import com.idrv.coach.utils.ValidateUtil;
import com.idrv.coach.utils.helper.DialogHelper;
import com.idrv.coach.utils.helper.ResHelper;
import com.idrv.coach.utils.helper.UIHelper;
import com.idrv.coach.utils.helper.ViewUtils;
import com.tbruyelle.rxpermissions.RxPermissions;
import com.zjb.loader.ZjbImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.ButterKnife;
import butterknife.InjectView;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

/**
 * time:2016/7/11
 * description:
 *
 * @author sunjianfei
 */
public class MakePosterActivity extends AbsPayActivity {
    //??????????????????720 * 1280??????
    private static final int TARGET_SCREEN_HEIGHT = 1280;
    //?????????????????????
    private static final String KEY_PARAM_IS_REMAKE = "param_is_remake";

    @InjectView(R.id.poster_layout)
    FrameLayout mContentLayout;
    @InjectView(R.id.buy_or_make_tv)
    TextView mBuyOrMakeTv;

    private View mBaseLayout;
    private ImageView mCurrentEditImageView;
    //?????????????????????????????????
    private List<View> mEditViews = new ArrayList<>();
    //????????????????????????ImageView
    private List<ImageView> mImageViews = new ArrayList<>();
    //????????????????????????
    private boolean isEdit;
    String mPageTitle;
    //?????????????????????
    private boolean isRemake = false;

    public static void launch(Context context, SpreadTool tool, boolean isReMake) {
        Intent intent = new Intent(context, MakePosterActivity.class);
        intent.putExtra(KEY_TOOL_PARAM, tool);
        intent.putExtra(KEY_PARAM_IS_REMAKE, isReMake);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_customize_poster);
        ButterKnife.inject(this);
        isRemake = getIntent().getBooleanExtra(KEY_PARAM_IS_REMAKE, false);
    }

    @Override
    public boolean isSwipeBackEnabled() {
        return false;
    }

    @Override
    public void onToolbarLeftClick(View view) {
        onBackPressed();
    }

    @Override
    public void onToolbarRightClick(View view) {
        showNotifyPopWindow();
    }

    @Override
    protected void refreshUI() {
        setBottomViewStatus();
    }

    @Override
    protected void share() {
        //do nothing
    }

    @Override
    protected void refresh() {
        Subscription subscription = mViewModel.getCommission()
                .doOnTerminate(this::getPosterData)
                .subscribe(__ -> {
                }, Logger::e);
        addSubscription(subscription);
    }

    @Override
    protected int getToolType() {
        return TYPE_POSTER;
    }

    @Override
    public void onBackPressed() {
        if (isEdit) {
            //????????????????????????,?????????????????????
            DialogHelper.create(DialogHelper.TYPE_NORMAL)
                    .cancelable(true)
                    .canceledOnTouchOutside(true)
                    .title(getString(R.string.tip))
                    .content(getString(R.string.poster_exit_tips))
                    .leftButton(getString(R.string.cancel), ContextCompat.getColor(this, R.color.black_54))
                    .rightButton(getString(R.string.make_next_time), ContextCompat.getColor(this, R.color.themes_main))
                    .leftBtnClickListener(((dialog, view) -> dialog.dismiss()))
                    .rightBtnClickListener(((dialog, view) -> finish()))
                    .show();
        } else {
            finish();
        }
    }

    private void setBottomViewStatus() {
        SpreadTool tool = mViewModel.getTool();
        String payType = tool.getPayType();

        if (!TextUtils.isEmpty(payType)) {
            //?????????????????????
            mBuyOrMakeTv.setBackgroundColor(ContextCompat.getColor(this, R.color.themes_main));
            mBuyOrMakeTv.setText(R.string.make_now);
            mBuyOrMakeTv.setOnClickListener(this::makePoster);
            if (isRemake) {
                //?????????????????????,???????????????
                mBuyOrMakeTv.performClick();
            }
        } else {
            //??????????????????
            mBuyOrMakeTv.setText(R.string.immediately_use);
            mBuyOrMakeTv.setOnClickListener(v -> showByDialog(tool));
        }
    }

    private void getPosterData() {
        Subscription subscription = mViewModel.getPoster()
                .subscribe(this::initView, e -> showErrorView());
        addSubscription(subscription);
    }

    private void initView(PosterPage page) {
        //?????????????????????
        mEditViews.clear();
        mImageViews.clear();

        //????????????????????????
        setBottomViewStatus();
        //1.????????????
        mPageTitle = mViewModel.getTool().getTitle();
        mToolbarLayout.setTitle(mPageTitle);
        mToolbarLayout.setRightIcon(R.drawable.icon_details);

        //2.????????????
        FrameLayout.LayoutParams baseLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        ViewGroup rootView = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        //????????????
        int baseWidth = ResHelper.getScreenWidth();
        int baseHeight = (int) (rootView.getHeight() - PixelUtil.dp2px(55));

        float mRatio = baseHeight * 1.0f / TARGET_SCREEN_HEIGHT;

        int leftMargin = (int) (page.getLeft() * mRatio);
        int rightMargin = (int) (page.getRight() * mRatio);
        int topMargin = (int) (page.getTop() * mRatio);
        int realWidth = baseWidth - leftMargin - rightMargin;
        int realHeight = (int) (realWidth * (page.getHeight() * 1.0f / page.getWidth()));

        baseLp.leftMargin = leftMargin;
        baseLp.rightMargin = rightMargin;
        baseLp.topMargin = topMargin;
        baseLp.height = realHeight;
        baseLp.width = realWidth;


        FrameLayout realLayout = new FrameLayout(this);
        //????????????
//        realLayout.setCardElevation(PixelUtil.dp2px(1.5f));

        mBaseLayout = realLayout;
        mContentLayout.addView(realLayout, baseLp);

        ZjbImageLoader.create(page.getImageUrl())
                .setDisplayType(ZjbImageLoader.DISPLAY_DEFAULT)
                .setDefaultDrawable(new ColorDrawable(0xffe0dedc))
                .into(realLayout);

        //3.?????????????????????,????????????
        List<Effect> effects = page.getEffects();
        if (ValidateUtil.isValidate(effects)) {

            for (Effect effect : effects) {
                int childLeftMargin = (int) (effect.getLeft() * realWidth);
                int childTopMargin = (int) (effect.getTop() * realHeight);
                int childRightMargin = (int) ((1 - effect.getRight()) * realWidth);
                int childBottom = (int) (effect.getBottom() * realHeight);

                int effectType = effect.getEffectType();

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = childTopMargin;
                lp.leftMargin = childLeftMargin;
                lp.rightMargin = childRightMargin;

                //?????????????????????????????????
                String colorStr = effect.getColor();
                int color = TextUtils.isEmpty(colorStr) ? 0xFF000000 : Color.parseColor("#" + colorStr);
                //??????????????????
                boolean canEdit = effect.isEnable();

                if (effectType == Effect.TYPE_TEXT) {
                    //??????
                    //??????????????????
                    int maxLength = effect.getMaxSize() > 0 ? effect.getMaxSize() : -1;
                    //??????????????????
                    float textSingleHeight = effect.getTextSize() * realHeight;
                    int textHeight = childBottom - childTopMargin;
                    //?????????????????????
                    int maxLines = Math.round(textHeight * 1.0f / textSingleHeight);

                    //?????????????????????
                    float realTextSize = textSingleHeight * 0.75f;
                    //????????????
                    boolean bold = effect.isBold();
                    //??????????????????
                    boolean italic = effect.isItalic();
                    //??????????????????
                    String defaultText = effect.getDefaultText();
                    String parseText = UrlParserManager.getInstance().parsePlaceholderUrl(defaultText);

                    //????????????,?????????????????????
                    if (defaultText.contains("{drivingSchool}") && TextUtils.isEmpty(parseText)) {
                        parseText = "xxx??????";
                    }

                    SpannableString sp = new SpannableString(parseText);

                    TextView mTextView = new TextView(this);
                    mTextView.setTextColor(color);
                    mTextView.setMaxLines(maxLines);
                    mTextView.setEllipsize(TextUtils.TruncateAt.END);
                    mTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    mTextView.setBackgroundDrawable(null);
                    //??????????????????
                    mTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, realTextSize);

                    //?????????????????????,?????????????????????
                    if (maxLength > 0) {
                        mTextView.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
                    }

                    //??????????????????
                    if (bold) {
                        TextPaint paint = mTextView.getPaint();
                        paint.setFakeBoldText(true);
                    }
                    //???????????????
                    if (italic) {
                        sp.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, parseText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    //??????layout??????
                    mTextView.setText(sp);
                    realLayout.addView(mTextView, lp);

                    if (canEdit) {
                        mTextView.setOnClickListener(v -> {
                            if (!isEdit) {
                                return;
                            }
                            EditInputDialog mDialog = new EditInputDialog(this);
                            if (maxLength > 0) {
                                mDialog.setMaxInputLength(maxLength);
                            }
                            mDialog.setDefaultText(mTextView.getText().toString());
                            mDialog.setOnSaveListener(text -> {
                                SpannableString str = new SpannableString(text);
                                if (italic) {
                                    str.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                mTextView.setText(str);
                            });
                            mDialog.show();
                        });
                    }
                } else if (effectType == Effect.TYPE_PIC) {
                    //?????????????????????
                    lp.height = childBottom - childTopMargin;
                    lp.width = realWidth - childLeftMargin - childRightMargin;
                    PhotoView mImageView = new PhotoView(this);
                    //??????????????????
                    mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                    String schema = UrlParserManager.getInstance().parsePlaceholderUrl(effect.getSchema());
                    String imageUrl = UrlParserManager.getInstance().parsePlaceholderUrl(effect.getImageUrl());
                    int imageType = effect.getImageType();

                    //????????????
                    ZjbImageLoader.create(imageUrl)
                            .setQiniu(lp.width, lp.height)
                            .setDisplayType(ZjbImageLoader.DISPLAY_DEFAULT)
                            .setDefaultDrawable(new ColorDrawable(0xffe0dedc))
                            .into(mImageView);
                    realLayout.addView(mImageView, lp);
                    mImageView.setEnabled(false);
                    mImageViews.add(mImageView);
                    if (canEdit) {
                        //????????????????????????
                        if (isEdit) {
                            mImageView.setEnabled(true);
                        }
                        mImageView.setOnPhotoTapListener(new PhotoViewAttacher.OnPhotoTapListener() {
                            @Override
                            public void onPhotoTap(View view, float x, float y) {
                                if (!isEdit) {
                                    return;
                                }
                                //????????????????????????  ?????????????????????????????????
                                if (imageType != Effect.IMAGE_TYPE_HTTP) {
                                    //?????????????????????ImageView,?????????????????????????????????
                                    mCurrentEditImageView = mImageView;
                                    //???????????????????????????
                                    boolean isOpenWebSite = mViewModel.isOpenWebSite();
                                    //????????????????????????,?????????????????????
                                    if (isOpenWebSite && imageType == Effect.IMAGE_TYPE_QR) {
                                        SelectPhotoDialog dialog = new SelectPhotoDialog(MakePosterActivity.this);
                                        dialog.changeCameraItem(getString(R.string.use_website_qr_image), View.VISIBLE);
                                        //??????dialog????????????
                                        dialog.setOnButtonClickListener(new SelectPhotoDialog.OnButtonClickListener() {
                                            @Override
                                            public void camera() {
                                                //????????????,??????.????????????.??????????????????????????????
                                                QRCodeUtil.createQrImage(schema, lp.width)
                                                        .observeOn(AndroidSchedulers.mainThread())
                                                        .subscribe(mImageView::setImageBitmap, Logger::e);
                                            }

                                            @Override
                                            public void gallery() {
                                                //??????????????????
                                                PictureUtil.FindPhoto(MakePosterActivity.this);
                                            }
                                        });
                                        dialog.show();
                                    } else {
                                        //????????????????????????
                                        PictureUtil.FindPhoto(MakePosterActivity.this);
                                    }
                                }
                            }

                            @Override
                            public void onOutsidePhotoTap() {

                            }
                        });
                    }
                } else if (effectType == Effect.TYPE_EDIT) {
                    //??????View?????????
                    lp.height = childBottom - childTopMargin;
                    lp.width = realWidth - childLeftMargin - childRightMargin;
                    //?????????
                    //???????????????
                    GradientDrawable drawable = (GradientDrawable) ContextCompat.getDrawable(this, R.drawable.dotted_line_bg);
                    drawable.setStroke((int) PixelUtil.dp2px(1), color, PixelUtil.dp2px(5), PixelUtil.dp2px(3));

                    View view = new View(this);
                    view.setBackgroundDrawable(drawable);
                    realLayout.addView(view, lp);
                    view.setVisibility(isEdit ? View.VISIBLE : View.GONE);
                    //???????????????
                    mEditViews.add(view);
                }
            }
            showContentView();
        } else {
            showEmptyView();
        }
    }

    /**
     * ????????????
     */
    private void makePoster(View v) {
        v.setEnabled(false);
        //?????????????????????
        boolean hasShowGuide = PreferenceUtil.getBoolean(SPConstant.KEY_HAS_SHOW_POSTER_MAKE_GUIDE);
        if (!hasShowGuide) {
            //??????alpha??????
            UIHelper.animFlicker(mBaseLayout, 800, 0);
            //??????????????????????????????
            mBaseLayout.postDelayed(this::showPosterMakeGuide, 1500);
        } else {
            if (null != mBaseLayout && !isRemake) {
                //??????alpha??????
                UIHelper.animFlicker(mBaseLayout, 800, 0);
                //????????????????????????
                mBaseLayout.postDelayed(() -> {
                    for (View view : mEditViews) {
                        view.setVisibility(View.VISIBLE);
                        UIHelper.animFlicker(view, 500, 5);
                    }
                }, 1000);
            }
        }
        //??????2???????????????
        v.postDelayed(() -> v.setEnabled(true), 2000);
        //??????imageView?????????
        for (ImageView imageView : mImageViews) {
            imageView.setEnabled(true);
        }
        //????????????
        isEdit = true;
        mBuyOrMakeTv.setText(R.string.make_complete);
        //????????????????????????
        mBuyOrMakeTv.setOnClickListener(this::savePoster);
    }

    /**
     * ????????????
     */
    private void savePoster(View v) {
        v.setEnabled(false);
        //????????????,????????????
        isEdit = false;
        //????????????????????????
        for (View view : mEditViews) {
            view.setVisibility(View.GONE);
        }
        //????????????????????????
        Bitmap bitmap = BitmapUtil.getBitmap(mBaseLayout);
        RxPermissions.getInstance(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE
                        , Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe(granted -> {
                    if (granted) {
                        BitmapUtil.saveBitmapAsync(bitmap, FileUtil.DIR_TYPE_SYS_IMAGE)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(this::onPictureSaveNext, Logger::e);

                    } else {
                        v.setEnabled(true);
                        UIHelper.shortToast(R.string.open_sdcard_permission);
                    }
                }, Logger::e);
    }

    private void onPictureSaveNext(String path) {
        SpreadTool tool = mViewModel.getTool();
        //???????????????sp
        PreferenceUtil.putString(String.format(Locale.US, SPConstant.KEY_SAVED_POSTER_PATH, tool.getId()), path);
        UIHelper.shortToast(R.string.pic_has_save_in_sys_album);
        PosterShareActivity.launch(this, path, mViewModel.getTool());
        finish();
    }

    /**
     * ???????????????????????????
     */
    private void showPosterMakeGuide() {
        ViewGroup rootView = (ViewGroup) mContentLayout.getRootView().findViewById(android.R.id.content);
        View mGuideView = LayoutInflater.from(this).inflate(R.layout.vw_poster_make_guide, null, false);
        mGuideView.getBackground().setAlpha(190);
        mGuideView.setOnTouchListener((__, ___) -> true);
        mGuideView.findViewById(R.id.btn_dismiss).setOnClickListener(v -> {
            rootView.removeView(mGuideView);
            PreferenceUtil.putBoolean(SPConstant.KEY_HAS_SHOW_POSTER_MAKE_GUIDE, true);
            for (View view : mEditViews) {
                view.setVisibility(View.VISIBLE);
                UIHelper.animFlicker(view, 500, 5);
            }
        });
        rootView.addView(mGuideView);
    }

    /**
     * ?????????????????????
     */
    private void showNotifyPopWindow() {
        MakePosterNotifyDialog mDialog = new MakePosterNotifyDialog(this);
        mDialog.show();
    }

    /**
     * ??????????????????????????????
     *
     * @param imagePath
     */
    private void showSelectImage(String imagePath) {
        if (null != mCurrentEditImageView) {
            ViewUtils.showImage(mCurrentEditImageView, "file:///" + imagePath);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_CANCELED) {
            //???????????????bug,????????????cancel,????????????
//            PictureUtil.deleteUri(this);
        } else if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PictureUtil.REQUEST_CODE_FROM_ALBUM) {
                if (data != null) {
                    PictureUtil.imgUri = data.getData();
                    String filePath = PictureUtil.getPath(MakePosterActivity.this, PictureUtil.imgUri);
                    showSelectImage(filePath);
                } else {
                    Logger.e("onActivityResult", "??????data????????????");
                }
            }
        }
    }

    @Override
    public WebParamBuilder getParams() {
        return null;
    }
}

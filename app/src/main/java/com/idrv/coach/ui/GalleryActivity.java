package com.idrv.coach.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;
import android.widget.PopupWindow;

import com.idrv.coach.R;
import com.idrv.coach.bean.Gallery;
import com.idrv.coach.data.manager.LocalPhotoManager;
import com.idrv.coach.ui.adapter.GalleryAdapter;
import com.idrv.coach.ui.adapter.GalleryNameAdapter;
import com.idrv.coach.ui.view.GalleryTitleBar;
import com.idrv.coach.ui.view.decoration.GridRecyclerDecoration;
import com.idrv.coach.utils.Logger;
import com.idrv.coach.utils.PictureUtil;
import com.idrv.coach.utils.PixelUtil;
import com.idrv.coach.utils.helper.ResHelper;
import com.zjb.loader.core.listener.RecyclerPauseOnScrollListener;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

/**
 * time:2016/4/18
 * description:
 *
 * @author sunjianfei
 */
public class GalleryActivity extends BaseActivity implements GalleryAdapter.OnRecyclerItemClickListener {
    public static final String DATA = "data";
    public static final String KEY_NEED_CAMERA = "need_camera";

    @InjectView(R.id.recycler_view)
    RecyclerView mRecyclerView;
    @InjectView(R.id.gallery_title)
    GalleryTitleBar mTitleBar;

    private PopupWindow mPopupWindow;
    private GalleryAdapter mGalleryAdapter;

    public static void launch(Activity activity, int requestCode) {
        launch(activity, requestCode, true);
    }

    public static void launch(Activity activity, int requestCode, boolean needCamera) {
        Intent intent = new Intent(activity, GalleryActivity.class);
        intent.putExtra(KEY_NEED_CAMERA, needCamera);
        activity.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_gallery);
        ButterKnife.inject(this);
        initView();
    }

    @Override
    protected boolean isToolbarEnable() {
        return false;
    }

    private void initView() {
        boolean needCamera = getIntent().getBooleanExtra(KEY_NEED_CAMERA, true);
        mGalleryAdapter = new GalleryAdapter(this,needCamera);
        //1.??????LayoutManager
        GridLayoutManager mLayoutManager = new GridLayoutManager(this, 4, GridLayoutManager.VERTICAL, false);
        //2.????????????
        mRecyclerView.addItemDecoration(new GridRecyclerDecoration((int) PixelUtil.dp2px(2.f), 0x242424));
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mGalleryAdapter);
        mRecyclerView.setItemViewCacheSize(0);
        mRecyclerView.setDrawingCacheEnabled(false);
        //3. ???recyclerView?????????????????????ScrollLinearLayout??????????????????
        /*?????????idle?????????ImageLoader???????????????????????????????????????*/
        mRecyclerView.addOnScrollListener(new RecyclerPauseOnScrollListener(true, true));
        //4.??????????????????
        mGalleryAdapter.setOnRecyclerItemClickListener(this);
        //???????????????
        mTitleBar.setOnBackClickListener(v -> finish());
        //?????????????????????????????????
        mTitleBar.setOnAheadClickListener(v -> {
            Intent intent = new Intent();
            intent.putStringArrayListExtra(DATA, mGalleryAdapter.getSelectPhotos());
            setResult(Activity.RESULT_OK, intent);
            finish();
        });
        //???????????????????????????????????????
        mTitleBar.setOnGalleryClickListener(v -> {
            showGalleryPopupWindow();
        });
    }

    @Override
    public void onItemClick(int position, String url, View view) {
        if (0 == position) {
            PictureUtil.TakePhoto(this);
        } else {
            //TODO
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (LocalPhotoManager.getInstance().isGalleryValidate()) {
                showGalleryPopupWindow();
            }
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mPopupWindow != null && mPopupWindow.isShowing()) {
                mPopupWindow.dismiss();
                mPopupWindow = null;
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * ?????????Menu?????????????????????????????????????????????????????????????????????????????????
     */
    public void showGalleryPopupWindow() {
        if (mPopupWindow != null && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
        } else {
            //1.???????????????
            List<Gallery> galleryList = LocalPhotoManager.getInstance().getLocalGallery();
            //2.
            RecyclerView recyclerView = (RecyclerView) View.inflate(this, R.layout.vw_gallery_pop, null);
            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
            recyclerView.setLayoutManager(layoutManager);
            GalleryNameAdapter adapter = new GalleryNameAdapter(this, galleryList);
            recyclerView.setAdapter(adapter);
            adapter.setOnRecyclerItemClickListener((position, __) -> {
                //1.??????????????????????????????
                String galleryName = galleryList.get(position).getGalleryName();
                mTitleBar.setTitle(galleryName);
                //2.???popWindow??????
                if (mPopupWindow != null && mPopupWindow.isShowing()) {
                    mPopupWindow.dismiss();
                }
                //3.??????????????????
                mGalleryAdapter.refresh(galleryList.get(position).getPictures());
                mGalleryAdapter.notifyDataSetChanged();
                mRecyclerView.scrollToPosition(0);
            });

            int width = (int) (this.getResources().getDisplayMetrics().widthPixels * 0.5f);
            //?????????????????????
            int height = (int) (ResHelper.getDimen(R.dimen.gallery_pop_item_height) * galleryList.size());
            int limitHeight = (int) (this.getResources().getDisplayMetrics().heightPixels * 0.6f);
            if (height > limitHeight) {
                height = limitHeight;
            }
            mPopupWindow = new PopupWindow(recyclerView, width, height);
            Drawable drawable = new ColorDrawable(0xf1131313);
            mPopupWindow.setBackgroundDrawable(drawable);
            mPopupWindow.setOutsideTouchable(true);
            mPopupWindow.showAsDropDown(mTitleBar, 0, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //??????????????????,??????uri
        if (resultCode == RESULT_CANCELED) {
            PictureUtil.deleteUri(this);
            Logger.e("result canceled!");
        } else if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case PictureUtil.REQUEST_CODE_FROM_CAMERA:
                    if (PictureUtil.imgUri != null) {
                        String filePath = "file:///" + PictureUtil.getPath(GalleryActivity.this, PictureUtil.imgUri);
                        ArrayList<String> list = new ArrayList<>();
                        list.add(filePath);
                        Intent intent = new Intent();
                        intent.putStringArrayListExtra(DATA, list);
                        setResult(Activity.RESULT_OK, intent);
                        finish();
                    } else {
                        Logger.e("take photo error!");
                    }
                    break;
            }
        }
    }
}

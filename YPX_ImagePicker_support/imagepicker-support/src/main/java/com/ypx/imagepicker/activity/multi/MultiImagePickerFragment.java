package com.ypx.imagepicker.activity.multi;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;


import com.ypx.imagepicker.ImagePicker;
import com.ypx.imagepicker.R;
import com.ypx.imagepicker.activity.PBaseLoaderFragment;
import com.ypx.imagepicker.adapter.multi.MultiGridAdapter;
import com.ypx.imagepicker.adapter.multi.MultiSetAdapter;
import com.ypx.imagepicker.bean.BaseSelectConfig;
import com.ypx.imagepicker.bean.ImageItem;
import com.ypx.imagepicker.bean.ImageSet;
import com.ypx.imagepicker.bean.MultiSelectConfig;
import com.ypx.imagepicker.bean.PickerUiConfig;
import com.ypx.imagepicker.bean.SelectMode;
import com.ypx.imagepicker.data.MultiPickerData;
import com.ypx.imagepicker.data.OnImagePickCompleteListener;
import com.ypx.imagepicker.presenter.IMultiPickerBindPresenter;
import com.ypx.imagepicker.utils.PTakePhotoUtil;
import com.ypx.imagepicker.utils.PViewSizeUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static android.app.Activity.RESULT_OK;
import static com.ypx.imagepicker.activity.multi.MultiImagePickerActivity.INTENT_KEY_SELECT_CONFIG;
import static com.ypx.imagepicker.activity.multi.MultiImagePickerActivity.INTENT_KEY_UI_CONFIG;
import static com.ypx.imagepicker.activity.multi.MultiImagePickerActivity.REQ_CAMERA;

/**
 * Description: 多选页
 * <p>
 * Author: peixing.yang
 * Date: 2019/2/21
 */
public class MultiImagePickerFragment extends PBaseLoaderFragment implements View.OnClickListener, MultiGridAdapter.OnActionResult {
    private List<ImageSet> imageSets;
    private ArrayList<ImageItem> imageItems;

    private RecyclerView mRecyclerView;
    private View v_masker;
    private Button btnDir;
    private TextView mTvTime;
    private MultiSetAdapter mImageSetAdapter;
    private RecyclerView mSetRecyclerView;
    private MultiGridAdapter mAdapter;
    private int currentSetIndex = 0;

    private TextView mTvPreview;
    private TextView mTvRight;
    private ImageView mSetArrowImg;
    private TextView mTvTitle;
    private ImageView mBckImg;
    private ViewGroup mTitleLayout;
    private RelativeLayout mBottomLayout;

    private MultiSelectConfig selectConfig;
    private IMultiPickerBindPresenter presenter;
    private PickerUiConfig uiConfig;
    private FragmentActivity mContext;
    private GridLayoutManager layoutManager;
    private View view;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.picker_activity_images_grid, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mContext = getActivity();
        Bundle bundle = getArguments();
        if (bundle != null) {
            selectConfig = (MultiSelectConfig) bundle.getSerializable(INTENT_KEY_SELECT_CONFIG);
            presenter = (IMultiPickerBindPresenter) bundle.getSerializable(INTENT_KEY_UI_CONFIG);
        }
        if (selectConfig == null || presenter == null) {
            if (mContext != null) {
                mContext.finish();
            }
            return;
        }

        ImagePicker.clearAllCache();
        if (selectConfig.getLastImageList() != null && selectConfig.getLastImageList().size() > 0) {
            MultiPickerData.instance.addAllImageItems(selectConfig.getLastImageList());
        }

        uiConfig = presenter.getUiConfig(mContext);
        if (selectConfig.getSelectMode() == SelectMode.MODE_TAKEPHOTO) {
            takePhoto();
        } else {
            findView();
            loadMediaSets();
        }
    }

    private OnImagePickCompleteListener onImagePickCompleteListener;

    public void setOnImagePickCompleteListener(OnImagePickCompleteListener onImagePickCompleteListener) {
        this.onImagePickCompleteListener = onImagePickCompleteListener;
    }

    /**
     * 初始化控件
     */
    private void findView() {
        v_masker = view.findViewById(R.id.v_masker);
        btnDir = view.findViewById(R.id.btn_dir);
        mRecyclerView = view.findViewById(R.id.mRecyclerView);
        mSetRecyclerView = view.findViewById(R.id.mSetRecyclerView);
        mTvTime = view.findViewById(R.id.tv_time);
        mTvTime.setVisibility(View.GONE);
        mSetArrowImg = view.findViewById(R.id.mSetArrowImg);
        mTvTitle = view.findViewById(R.id.tv_title);
        mTvRight = view.findViewById(R.id.tv_rightBtn);
        mTitleLayout = view.findViewById(R.id.top_bar);
        mBottomLayout = view.findViewById(R.id.footer_panel);
        mBckImg = view.findViewById(R.id.iv_back);
        mTvPreview = view.findViewById(R.id.tv_preview);
        initAdapters();
        initUI();
        setListener();
        refreshOKBtn();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (imageItems != null && mAdapter != null) {
            mAdapter.refreshData(imageItems);
            refreshOKBtn();
        }
    }

    private RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                if (mTvTime.getVisibility() == View.VISIBLE) {
                    mTvTime.setVisibility(View.GONE);
                    mTvTime.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.picker_fade_out));
                }
            } else {
                if (mTvTime.getVisibility() == View.GONE) {
                    mTvTime.setVisibility(View.VISIBLE);
                    mTvTime.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.picker_fade_in));
                }
            }
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (imageItems != null)
                try {
                    mTvTime.setText(imageItems.get(layoutManager.findFirstVisibleItemPosition()).getTimeFormat());
                } catch (Exception ignored) {

                }
        }
    };

    private void initUI() {
        mBckImg.setImageDrawable(getResources().getDrawable(uiConfig.getBackIconID()));
        mBckImg.setColorFilter(uiConfig.getBackIconColor());
        mTitleLayout.setBackgroundColor(uiConfig.getTitleBarBackgroundColor());
        mRecyclerView.setBackgroundColor(uiConfig.getPickerBackgroundColor());
        mBottomLayout.setBackgroundColor(uiConfig.getBottomBarBackgroundColor());
        mTvTitle.setTextColor(uiConfig.getTitleColor());
        if (uiConfig.getOkBtnSelectBackground() == null && uiConfig.getOkBtnUnSelectBackground() == null) {
            mTvRight.setPadding(0, 0, 0, 0);
        }

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mSetRecyclerView.getLayoutParams();
        int height = (int) (getResources().getDisplayMetrics().heightPixels / 4f);
        if (uiConfig.getPickStyle() == PickerUiConfig.PICK_STYLE_BOTTOM) {
            mBottomLayout.setVisibility(View.VISIBLE);
            v_masker.setPadding(0, 0, 0, PViewSizeUtils.dp(mContext, 51));
            mRecyclerView.setPadding(0, 0, 0, PViewSizeUtils.dp(mContext, 51));
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            params.bottomMargin = mRecyclerView.getPaddingBottom();
            params.topMargin = height;
        } else {
            mBottomLayout.setVisibility(View.GONE);
            v_masker.setPadding(0, 0, 0, 0);
            mRecyclerView.setPadding(0, 0, 0, 0);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            params.bottomMargin = height;
            params.topMargin = 0;
        }

        ((LinearLayout) view.findViewById(R.id.mTitleRoot)).setGravity(uiConfig.getTitleBarGravity());
        mSetArrowImg.setImageDrawable(uiConfig.getTitleDrawableRight());

        if (selectConfig.isShowVideo() && selectConfig.isShowImage()) {
            mTvTitle.setText(getResources().getString(R.string.str_image_video));
        } else if (selectConfig.isShowVideo()) {
            mTvTitle.setText(getResources().getString(R.string.str_video));
        } else {
            mTvTitle.setText(getResources().getString(R.string.str_image));
        }
    }

    /**
     * 初始化监听
     */
    private void setListener() {
        btnDir.setOnClickListener(this);
        v_masker.setOnClickListener(this);
        mTvRight.setOnClickListener(this);
        mTvTitle.setOnClickListener(this);
        mSetArrowImg.setOnClickListener(this);
        mTvPreview.setOnClickListener(this);
        mRecyclerView.addOnScrollListener(onScrollListener);
        mBckImg.setOnClickListener(this);
        mImageSetAdapter.setSetSelectCallBack(new MultiSetAdapter.SetSelectCallBack() {
            @Override
            public void selectImageSet(ImageSet set, int pos) {
                selectImageFromSet(pos, true);
            }
        });
    }


    /**
     * 初始化相关adapter
     */
    private void initAdapters() {
        mSetRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mImageSetAdapter = new MultiSetAdapter(mContext, presenter);
        mSetRecyclerView.setAdapter(mImageSetAdapter);
        mImageSetAdapter.refreshData(imageSets);

        mAdapter = new MultiGridAdapter(mContext, new ArrayList<ImageItem>(), selectConfig, presenter);
        mAdapter.setHasStableIds(true);
        mAdapter.setOnActionResult(this);
        layoutManager = new GridLayoutManager(mContext, selectConfig.getColumnCount());
        if (mRecyclerView.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
            mRecyclerView.getItemAnimator().setChangeDuration(0);// 通过设置动画执行时间为0来解决闪烁问题
        }
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);
    }

    /**
     * 选择图片文件夹
     *
     * @param position 位置
     */
    private void selectImageFromSet(final int position, boolean isTransit) {
        this.currentSetIndex = position;
        final ImageSet set = imageSets.get(currentSetIndex);
        MultiPickerData.instance.setCurrentImageSet(set);
        if (isTransit) {
            showOrHideImageSetList();
        }
        mImageSetAdapter.setSelectIndex(currentSetIndex);
        loadMediaItemsFromSet(set);
    }

    /**
     * 显示或隐藏图片文件夹选项列表
     */
    private void showOrHideImageSetList() {
        if (mSetRecyclerView.getVisibility() == View.GONE) {
            mSetArrowImg.setRotation(180);
            v_masker.setVisibility(View.VISIBLE);
            mSetRecyclerView.setVisibility(View.VISIBLE);
            mSetRecyclerView.setAnimation(AnimationUtils.loadAnimation(mContext,
                    uiConfig.isBottomStyle() ? R.anim.picker_show2bottom : R.anim.picker_anim_in));
        } else {
            mSetArrowImg.setRotation(0);
            v_masker.setVisibility(View.GONE);
            mSetRecyclerView.setVisibility(View.GONE);
            mSetRecyclerView.setAnimation(AnimationUtils.loadAnimation(mContext,
                    uiConfig.isBottomStyle() ? R.anim.picker_hide2bottom : R.anim.picker_anim_up));
        }
    }

    @Override
    public void onClick(View v) {
        if (v == btnDir || v == v_masker) {
            showOrHideImageSetList();
        } else if (v == mTvRight) {
            if (isEmpty() || onDoubleClick()) {
                return;
            }
            notifyOnImagePickComplete(new ArrayList<>(MultiPickerData.instance.getSelectImageList()));
        } else if (v == mBckImg) {
            mContext.onBackPressed();
        } else if (v == mTvTitle || v == mSetArrowImg) {
            if (!uiConfig.isBottomStyle()) {
                showOrHideImageSetList();
            }
        } else if (v == mTvPreview) {
            if (isEmpty()) {
                return;
            }
            intentPreview(0, MultiPickerData.instance.getSelectImageList());
        }
    }

    /**
     * 是否未选择
     *
     * @return true：未选择图片 false:选择了
     */
    private boolean isEmpty() {
        if (MultiPickerData.instance.isEmpty()) {
            presenter.tip(mContext, getResources()
                    .getString(R.string.str_emptytip));
            return true;
        }
        return false;
    }

    /**
     * 刷新选中图片列表，执行回调，退出页面
     *
     * @param list 选中图片列表
     */
    private void notifyOnImagePickComplete(ArrayList<ImageItem> list) {
        if (onImagePickCompleteListener != null) {
            onImagePickCompleteListener.onImagePickComplete(list);
        } else {
            Intent intent = new Intent();
            intent.putExtra(ImagePicker.INTENT_KEY_PICKER_RESULT, (Serializable) list);
            mContext.setResult(ImagePicker.REQ_PICKER_RESULT_CODE, intent);
            mContext.finish();
        }
    }

    @Override
    protected BaseSelectConfig getSelectConfig() {
        return selectConfig;
    }

    @Override
    protected void loadMediaSetsComplete(List<ImageSet> imageSetList) {
        if (imageSetList == null || imageSetList.size() == 0) {
            btnDir.setText("无媒体文件");
            return;
        }
        this.imageSets = imageSetList;
        mImageSetAdapter.refreshData(imageSets);
        selectImageFromSet(0, false);
    }

    @Override
    protected void loadMediaItemsComplete(ImageSet set) {
        this.imageItems = set.imageItems;
        btnDir.setText(imageSets.get(currentSetIndex).name);
        mTvTitle.setText(imageSets.get(currentSetIndex).name);
        mAdapter.refreshData(imageItems);
    }

    @Override
    protected void refreshAllVideoSet(ImageSet allVideoSet) {
        if (allVideoSet != null &&
                allVideoSet.imageItems != null
                && allVideoSet.imageItems.size() > 0
                && !imageSets.contains(allVideoSet)) {
            imageSets.add(1, allVideoSet);
            mImageSetAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 拍照回调
     *
     * @param requestCode 请求码
     * @param resultCode  返回码
     */
    @Override
    public void onTakePhotoResult(int requestCode, int resultCode) {
        if (resultCode == RESULT_OK && requestCode == REQ_CAMERA) {//拍照返回
            if (!TextUtils.isEmpty(PTakePhotoUtil.mCurrentPhotoPath)) {
                if (selectConfig.getSelectMode() == SelectMode.MODE_CROP) {
                    intentCrop(PTakePhotoUtil.mCurrentPhotoPath);
                    return;
                }
                PTakePhotoUtil.refreshGalleryAddPic(mContext);
                ImageItem item = new ImageItem();
                item.path = PTakePhotoUtil.mCurrentPhotoPath;
                ArrayList<ImageItem> list = new ArrayList<>();
                list.add(item);
                notifyOnImagePickComplete(list);
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (mSetRecyclerView != null && mSetRecyclerView.getVisibility() == View.VISIBLE) {
            showOrHideImageSetList();
            return true;
        }
        return false;
    }

    /**
     * 跳转剪裁页面
     *
     * @param path 图片路径
     */
    private void intentCrop(String path) {
        SingleCropActivity.intentCrop(mContext, presenter, selectConfig, path, new OnImagePickCompleteListener() {
            @Override
            public void onImagePickComplete(ArrayList<ImageItem> items) {
                notifyOnImagePickComplete(items);
            }
        });
    }

    /**
     * 跳转预览
     *
     * @param position 默认选中的index
     */
    private void intentPreview(int position, ArrayList<ImageItem> previewList) {
        MultiImagePreviewActivity.preview(mContext, selectConfig, presenter, true, previewList, position,
                new OnImagePickCompleteListener() {
                    @Override
                    public void onImagePickComplete(ArrayList<ImageItem> items) {
                        notifyOnImagePickComplete(items);
                    }
                });
    }

    @SuppressLint("DefaultLocale")
    private void refreshOKBtn() {
        if (selectConfig.getMaxCount() == 1 &&
                selectConfig.getSelectMode() != SelectMode.MODE_MULTI) {
            mTvRight.setVisibility(View.GONE);
            return;
        }
        int selectCount = MultiPickerData.instance.getSelectCount();
        if (MultiPickerData.instance.isEmpty()) {
            mTvRight.setEnabled(false);
            mTvRight.setText(uiConfig.getOkBtnText());
            mTvRight.setBackground(uiConfig.getOkBtnUnSelectBackground());
            mTvRight.setTextColor(uiConfig.getOkBtnUnSelectTextColor());
            mTvPreview.setVisibility(View.GONE);
        } else {
            mTvRight.setEnabled(true);
            mTvRight.setText(String.format("%s(%d/%d)", uiConfig.getOkBtnText(),
                    selectCount, selectConfig.getMaxCount()));
            mTvRight.setBackground(uiConfig.getOkBtnSelectBackground());
            mTvRight.setTextColor(uiConfig.getOkBtnSelectTextColor());
            mTvPreview.setText(String.format("预览(%d)", selectCount));
            //可以预览时才显示预览按钮
            if (selectConfig.isPreview()) {
                mTvPreview.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onClickItem(ImageItem item, int position) {
        //在屏蔽列表中
        if (selectConfig.isShieldItem(item)) {
            presenter.tip(getContext(), getResources().getString(R.string.str_shield));
            return;
        }

        mRecyclerView.setTag(item);

        //如果只能选择一个视频，且当前是视频的时候直接返回
        if (selectConfig.isVideoSinglePick() && item.isVideo()) {
            ArrayList<ImageItem> list = new ArrayList<>();
            list.add(item);
            notifyOnImagePickComplete(list);
            return;
        }

        //剪裁模式下，直接跳转剪裁
        if (selectConfig.getSelectMode() == SelectMode.MODE_CROP) {
            intentCrop(item.path);
            return;
        }

        //多选情况下，如果选择数量大于1个时，则要么执行预览，要么执行自定义点击操作
        if (selectConfig.getMaxCount() > 1 || selectConfig.getSelectMode() == SelectMode.MODE_MULTI) {
            //打开了预览，则跳转预览，否则执行自定义的点击操作
            if (selectConfig.isPreview()) {
                intentPreview(position, null);
            } else {
                presenter.imageItemClick(mContext, item, MultiPickerData.instance.getSelectImageList()
                        , imageItems, mAdapter);
            }
            return;
        }

        //单选模式下且选择数量只有一个时，直接回调出去
        if (selectConfig.getSelectMode() == SelectMode.MODE_SINGLE && selectConfig.getMaxCount() <= 1) {
            ArrayList<ImageItem> list2 = new ArrayList<>();
            list2.add(item);
            notifyOnImagePickComplete(list2);
        }
    }

    @Override
    public void onCheckItem(ImageItem imageItem) {
        if (!MultiPickerData.instance.hasItem(imageItem) &&
                MultiPickerData.instance.isOverLimit(selectConfig.getMaxCount())) {
            presenter.tip(getContext(), String.format(Objects.requireNonNull(getContext())
                            .getResources().getString(R.string.str_limit),
                    selectConfig.getMaxCount()));
            return;
        }
        if (MultiPickerData.instance.hasItem(imageItem)) {
            MultiPickerData.instance.removeImageItem(imageItem);
            if (selectConfig.isLastItem(imageItem)) {
                selectConfig.getLastImageList().remove(imageItem);
            }
        } else {
            MultiPickerData.instance.addImageItem(imageItem);
        }
        mAdapter.notifyDataSetChanged();
        refreshOKBtn();
    }
}

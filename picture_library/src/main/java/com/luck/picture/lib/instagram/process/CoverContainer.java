package com.luck.picture.lib.instagram.process;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.luck.picture.lib.R;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.tools.ScreenUtils;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;

/**
 * ================================================
 * Created by JessYan on 2020/7/8 16:32
 * <a href="mailto:jess.yan.effort@gmail.com">Contact me</a>
 * <a href="https://github.com/JessYanCoding">Follow me</a>
 * ================================================
 */
public class CoverContainer extends FrameLayout {
    private ImageView[] mImageViews = new ImageView[7];
    private final int mImageViewHeight;
    private int mImageViewWidth;
    private getAllFrameTask mFrameTask;
    private View mMaskView;
    private ZoomView mZoomView;
    private int startedTrackingX;
    private int startedTrackingY;
    int startClickX;
    int startClickY;
    private float scrollHorizontalPosition;
    private onSeekListener mOnSeekListener;

    public CoverContainer(@NonNull Context context, LocalMedia media) {
        super(context);
        mImageViewHeight = ScreenUtils.dip2px(getContext(), 60);

        for (int i = 0; i < mImageViews.length; i++) {
            mImageViews[i] = new ImageView(context);
            mImageViews[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            mImageViews[i].setImageResource((R.drawable.picture_image_placeholder));
            addView(mImageViews[i]);
        }

        mMaskView = new View(context);
        mMaskView.setBackgroundColor(0x77FFFFFF);
        addView(mMaskView);

        mZoomView = new ZoomView(context);
        addView(mZoomView);
    }

    public void getFrame(@NonNull Context context, LocalMedia media) {
        mFrameTask = new getAllFrameTask(context, media, mImageViews.length, 0, (int) media.getDuration(), new OnSingleBitmapListenerImpl(this));
        mFrameTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        mImageViewWidth = (width - ScreenUtils.dip2px(getContext(), 40)) / mImageViews.length;
        for (ImageView imageView : mImageViews) {
            imageView.measure(MeasureSpec.makeMeasureSpec(mImageViewWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(mImageViewHeight, MeasureSpec.EXACTLY));
        }

        mMaskView.measure(MeasureSpec.makeMeasureSpec(width - ScreenUtils.dip2px(getContext(), 40) + mImageViews.length - 1, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(mImageViewHeight, MeasureSpec.EXACTLY));
        mZoomView.measure(MeasureSpec.makeMeasureSpec(mImageViewHeight, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(mImageViewHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int viewTop = (getMeasuredHeight() - mImageViewHeight) / 2;
        int viewLeft;

        for (int i = 0; i < mImageViews.length; i++) {
            viewLeft = i * (mImageViewWidth + 1) + ScreenUtils.dip2px(getContext(), 20);
            mImageViews[i].layout(viewLeft, viewTop, viewLeft + mImageViews[i].getMeasuredWidth(), viewTop + mImageViews[i].getMeasuredHeight());
        }

        viewLeft = ScreenUtils.dip2px(getContext(), 20);
        mMaskView.layout(viewLeft, viewTop, viewLeft + mMaskView.getMeasuredWidth(), viewTop + mMaskView.getMeasuredHeight());

        mZoomView.layout(viewLeft, viewTop, viewLeft + mZoomView.getMeasuredWidth(), viewTop + mZoomView.getMeasuredHeight());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Rect rect = new Rect();
        mMaskView.getHitRect(rect);
        if (!rect.contains((int) (event.getX()), (int) (event.getY()))) {
            return super.onTouchEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startedTrackingX = (int) event.getX();
            startedTrackingY = (int) event.getY();

            startClickX = (int) event.getX();
            startClickY = (int) event.getY();

            setScrollHorizontalPosition(startClickX - ScreenUtils.dip2px(getContext(), 20) - mZoomView.getMeasuredWidth() / 2);

        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = (int) (event.getX() - startedTrackingX);
            float dy = (int) event.getY() - startedTrackingY;

            moveByX(dx);

            startedTrackingX = (int) event.getX();
            startedTrackingY = (int) event.getY();
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
            if (mOnSeekListener != null) {
                mOnSeekListener.onSeekEnd();
            }
        }
        return true;
    }

    public void moveByX(float dx) {
        setScrollHorizontalPosition(scrollHorizontalPosition + dx);
    }

    public void setScrollHorizontalPosition(float value) {
        float oldHorizontalPosition = scrollHorizontalPosition;
        scrollHorizontalPosition = Math.min(Math.max(0, value), mMaskView.getMeasuredWidth() - mZoomView.getMeasuredWidth());

        if (oldHorizontalPosition == scrollHorizontalPosition) {
            return;
        }

        mZoomView.setTranslationX(scrollHorizontalPosition);
        if (mOnSeekListener != null) {
            mOnSeekListener.onSeek(scrollHorizontalPosition / (mMaskView.getMeasuredWidth() - mZoomView.getMeasuredWidth()));
        }
    }

    public void onPause() {

    }

    public void onDestroy() {
        if (mFrameTask != null) {
            mFrameTask.setStop(true);
            mFrameTask.cancel(true);
            mFrameTask = null;
        }
    }

    public static class OnSingleBitmapListenerImpl implements getAllFrameTask.OnSingleBitmapListener {
        private WeakReference<CoverContainer> mContainerWeakReference;
        private int index;

        public OnSingleBitmapListenerImpl(CoverContainer coverContainer) {
            mContainerWeakReference = new WeakReference<>(coverContainer);
        }


        @Override
        public void onSingleBitmapComplete(Bitmap bitmap) {
            CoverContainer container = mContainerWeakReference.get();
            if (container != null) {
                container.post(new RunnableImpl(container.mImageViews[index], bitmap));
                index ++;
            }
        }

        public static class RunnableImpl implements Runnable {
            private WeakReference<ImageView> mViewWeakReference;
            private Bitmap mBitmap;

            public RunnableImpl(ImageView imageView, Bitmap bitmap) {
                mViewWeakReference = new WeakReference<>(imageView);
                mBitmap = bitmap;
            }

            @Override
            public void run() {
                ImageView imageView = mViewWeakReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(mBitmap);
                }
            }
        }
    }

    public void setOnSeekListener(onSeekListener onSeekListener) {
        mOnSeekListener = onSeekListener;
    }

    public interface onSeekListener {
        void onSeek(float percent);
        void onSeekEnd();
    }
}

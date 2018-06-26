package com.icool.ptrlib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;
import android.widget.TextView;

import java.util.Arrays;

/**
 * @author zhzy
 * @Description 下拉刷新
 * Created by zhzy on 2018/6/25.
 */
public class PtrLayout extends ViewGroup {


    //取消状态
    private static final int STATE_CANCEL = 0x01;
    //滚动状态
    private static final int STATE_SCROLL = 0x02;
    //松开
    private static final int STATE_RELEASE = 0x03;
    //阻尼系数
    private float mResistance;
    //产生滑动的距离
    private int mScaledTouchSlop;
    //滚动辅助
    private Scroller mScroller;
    //滚动监听
    private OnPtrListener mPtrListener;
    //起始触摸点
    private int mStartX, mStartY;
    //实时触摸点
    private int mTouchX, mTouchY;
    //滑动点
    private int mMoveX, mMoveY;
    //头部View
    private View mHeaderView;
    //内容View
    private View mContentView;
    //头部高度
    private int mHeaderHeight;
    //Y轴上的滑动距离
    private int mDistanceY;
    //是否正在滚动
    private boolean isScroll;
    //当前状态-STATE_CANCEL/STATE_RELEASE
    private int mCurPtmStatus;

    public PtrLayout(Context context) {
        this(context, null);
    }

    public PtrLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PtrLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mScroller = new Scroller(context);
        mScaledTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mHeaderView != null) {
            measureChildWithMargins(mHeaderView, widthMeasureSpec, 0, heightMeasureSpec, 0);
            mHeaderHeight = mHeaderView.getMeasuredHeight();
        }

        measureChildWithMargins(mContentView, widthMeasureSpec, 0, heightMeasureSpec, 0);
        MarginLayoutParams lp = (MarginLayoutParams) mContentView.getLayoutParams();
        int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin, lp.width);
        int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + lp.topMargin, lp.height);
        mContentView.measure(childWidthMeasureSpec, childHeightMeasureSpec);

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        if (mHeaderView != null) {
            MarginLayoutParams lp = (MarginLayoutParams) mHeaderView.getLayoutParams();
            int left;
            int right;
            int top;
            int bottom;
            LayoutParams headerParams = (LayoutParams) mHeaderView.getLayoutParams();
            if (mDistanceY <= mHeaderHeight) {
                left = paddingLeft + lp.leftMargin;
                right = left + mHeaderView.getMeasuredWidth();
                top = -mHeaderHeight + paddingTop + lp.topMargin + mDistanceY;
                bottom = top + mHeaderHeight;
                mHeaderView.layout(left, top, right, bottom);
                headerParams.height = mHeaderHeight;
            } else {
                left = 0;
                right = left + mHeaderView.getMeasuredWidth();
                top = 0;
                bottom = mDistanceY;
                headerParams.height = bottom;
            }
            mHeaderView.setLayoutParams(headerParams);
            mHeaderView.layout(left, top, right, bottom);
        }
        MarginLayoutParams lp = (MarginLayoutParams) mContentView.getLayoutParams();
        int left = paddingLeft + lp.leftMargin;
        int right = left + mContentView.getMeasuredWidth();
        int top = paddingTop + lp.topMargin + mDistanceY;
        int bottom = top + mContentView.getMeasuredHeight();
        mContentView.layout(left, top, right, bottom);
        if (mCurPtmStatus == STATE_SCROLL && null != mPtrListener) {
            int progress = mDistanceY * 100 / mHeaderHeight;
            mPtrListener.onPtrProgress(progress);
        }
    }


    @SuppressLint("SetTextI18n")
    @Override
    protected void onFinishInflate() {
        int childCount = getChildCount();
        if (childCount > 2) {
            throw new IllegalStateException("PtrLayout contains two children，and only two!");
        } else if (childCount == 2) {
            mHeaderView = getChildAt(0);
            mContentView = getChildAt(1);
        } else if (childCount == 1) {
            mContentView = getChildAt(0);
        } else if (childCount == 0) {
            TextView textView = new TextView(getContext());
            textView.setTextColor(Color.RED);
            textView.setText("EMPTY CHILD IN PTRLAYOUT");
            addView(textView);
            mContentView = textView;
        }
        super.onFinishInflate();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mCurPtmStatus == STATE_RELEASE) return true;
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        mTouchX = x;
        mTouchY = y;
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mStartX = x;
                mStartY = y;
                mMoveX = 0;
                mMoveY = 0;
                reset();
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaX = Math.abs(x - mStartX);
                float deltaY = Math.abs(y - mStartY);
                if (deltaX >= mScaledTouchSlop && deltaY >= mScaledTouchSlop) {
                    if (deltaX > deltaY) {//左右滑动  不拦截
                        return false;
                    } else {//上下滑动 并且是下拉操作
                        if (mPtrListener != null && mTouchY > mStartY) {
                            mPtrListener.onPtrStart();
                            return true;
                        }
                    }
                } else if (deltaX >= mScaledTouchSlop && deltaY < mScaledTouchSlop) {
                    return false;//左右滑动  不拦截
                } else if (deltaX < mScaledTouchSlop && deltaY >= mScaledTouchSlop) {
                    if (mPtrListener != null && mTouchY > mStartY) {
                        mPtrListener.onPtrStart();
                        return true;//上下滑动  拦截
                    }
                }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (canPtr() && mPtrListener != null && !mPtrListener.canPtr()) {
            return true;
        }
        if (isScroll || !isEnabled() || mContentView == null) {
            return false;
        }
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        mTouchX = x;
        mTouchY = y;
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mStartX = x;
                mStartY = y;
                mMoveX = 0;
                mMoveY = 0;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mTouchY <= mStartY) {
                    reset();
                    return false;
                }
                mMoveX = x;
                mMoveY = y;
                updatePos();
                return true;
            case MotionEvent.ACTION_UP:
                moveTop();
                return true;
        }
        return super.onTouchEvent(ev);
    }

    /**
     * TODO
     * 对Header和contentView进行重新布局
     */
    private void updatePos() {
        mDistanceY = (int) ((mMoveY - mStartY) / mResistance);
        requestLayout();
    }


    /**
     * 移动到顶部
     */
    private void moveTop() {
        if (mDistanceY == 0) return;
        isScroll = true;
        mScroller.startScroll(0, 0, 0, mDistanceY, 400);
        invalidate();

    }

    /**
     * 初始化
     */
    private void reset() {
        mDistanceY = 0;
        mCurPtmStatus = -1;
    }


    @Override
    public void computeScroll() {
        super.computeScroll();
        boolean isNotFinish = mScroller.computeScrollOffset();
        if (isNotFinish) {
            int currY = mScroller.getCurrY();
            //mDistanceY = mDistanceTemp - Math.abs(currY);
            if (isScroll && mScroller.getFinalY() == currY) {
                isScroll = false;
            }
            invalidate();
            requestLayout();
        }
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    public class LayoutParams extends MarginLayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    /**
     * 设置监听事件
     *
     * @param onPtrListener PTR Listener
     */
    public void setOnPtrListener(OnPtrListener onPtrListener) {
        this.mPtrListener = onPtrListener;
    }

    private boolean canPtr() {
        if (mContentView instanceof RecyclerView) {
            RecyclerView.LayoutManager layoutManager = ((RecyclerView) mContentView).getLayoutManager();
            if (layoutManager == null) return false;
            if (layoutManager instanceof LinearLayoutManager) {
                return ((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition() == 0;
            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                int[] info = new int[]{};
                ((StaggeredGridLayoutManager) layoutManager).findFirstCompletelyVisibleItemPositions(info);
                return Arrays.asList(info).contains(0);
            }
        }
        return false;
    }

    public interface OnPtrListener {

        boolean canPtr();

        void onPtrStart();

        void onPtrProgress(int progress);

        void onPtrEnd();

        void onPtrCancel();
    }


}

package com.tpkarras.mirror2rearultra;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View.MeasureSpec;

import androidx.core.widget.NestedScrollView;

/** A scroll view that wraps its content up to a ceiling and scrolls beyond it. */
public class MaxHeightScrollView extends NestedScrollView {
    private int maxHeight = Integer.MAX_VALUE;

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setMaxHeight(int pixels) {
        maxHeight = pixels;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            int offered = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST
                    ? MeasureSpec.getSize(heightMeasureSpec) : Integer.MAX_VALUE;
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                    Math.min(offered, maxHeight), MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}

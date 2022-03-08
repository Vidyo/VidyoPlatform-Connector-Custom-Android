package com.vidyo.vidyoconnector.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vidyo.vidyoconnector.utils.Logger;

public class VideoFrameLayout extends FrameLayout {

    private int xDelta;
    private int yDelta;

    private boolean dragEnabled = false;

    public VideoFrameLayout(@NonNull Context context) {
        super(context);
    }

    public VideoFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public VideoFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void enableDrag(boolean enabled) {
        this.dragEnabled = enabled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!dragEnabled) return false;

        final int x = (int) event.getRawX();
        final int y = (int) event.getRawY();

        RelativeLayout.LayoutParams lParams = (RelativeLayout.LayoutParams) getLayoutParams();
        View parent = (View) getParent();

        switch (event.getAction() & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:
                xDelta = x - lParams.leftMargin;
                yDelta = y - lParams.topMargin;
                break;

            case MotionEvent.ACTION_UP:
                Logger.i("View was Placed");
                break;

            case MotionEvent.ACTION_MOVE:
                int xPos = x - xDelta;
                int yPos = y - yDelta;

                lParams.leftMargin = xPos;
                lParams.topMargin = yPos;
                lParams.rightMargin = 0;
                lParams.bottomMargin = 0;
                setLayoutParams(lParams);
                break;
        }

        parent.invalidate();
        return false;
    }
}
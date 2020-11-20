package com.vidyo.vidyoconnector.tiles.gallery;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.vidyo.vidyoconnector.BuildConfig;
import com.vidyo.vidyoconnector.R;
import com.vidyo.vidyoconnector.tiles.model.ViewType;
import com.vidyo.vidyoconnector.utils.AppUtils;
import com.vidyo.vidyoconnector.utils.Logger;
import com.vidyo.vidyoconnector.view.VideoFrameLayout;

import java.util.LinkedList;
import java.util.Queue;

public class GalleryViewManager {

    private static final int MAX_TILES = 4;

    private final FrameLayout selfView;
    private final RelativeLayout remoteContainer;

    public GalleryViewManager(FrameLayout selfView, RelativeLayout remoteContainer) {
        this.selfView = selfView;
        this.remoteContainer = remoteContainer;
    }

    public View getLocal() {
        return selfView;
    }

    public void update(int participants) {
        int viewCount = remoteContainer.getChildCount();
        Logger.i("View count start: %d", viewCount);

        remoteContainer.removeAllViews();

        Logger.i("View count between: %d", remoteContainer.getChildCount());

        if (participants > MAX_TILES) {
            participants = MAX_TILES;
        }

        for (int participant = 0; participant < participants; participant++) {
            addFrame(participant, participants);
        }

        Logger.i("View count afterward: %d", remoteContainer.getChildCount());
    }

    public Queue<View> remoteViews() {
        Queue<View> views = new LinkedList<>();

        for (int index = 0; index < remoteContainer.getChildCount(); index++) {
            View view = remoteContainer.getChildAt(index);
            views.add(view);
        }

        return views;
    }

    public View loudest() {
        for (int index = 0; index < remoteContainer.getChildCount(); index++) {
            View view = remoteContainer.getChildAt(index);
            ViewType viewType = (ViewType) view.getTag();
            if (viewType == ViewType.REMOTE_LOUDEST) return view;
        }

        throw new IllegalStateException("No loudest!");
    }

    public View updateSelfViewPosition(int participants) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) selfView.getLayoutParams();
        selfView.setTag(ViewType.LOCAL);

        int width = remoteContainer.getMeasuredWidth();
        int height = remoteContainer.getMeasuredHeight();

        if (participants > 0) {
            boolean isLandscape = AppUtils.isLandscape(this.selfView.getResources());

            params.width = isLandscape ? width / 8 : width / 4;
            params.height = isLandscape ? height / 3 : height / 4;

            int margin = remoteContainer.getResources().getDimensionPixelSize(R.dimen.material_margin);
            params.setMargins(margin, margin, margin, margin);
            params.gravity = (Gravity.TOP | Gravity.END);
        } else {
            params.width = width;
            params.height = height;

            params.setMargins(0, 0, 0, 0);
            params.gravity = Gravity.TOP | Gravity.CENTER;
        }

        return selfView;
    }

    private void addFrame(int at, int max) {
        VideoFrameLayout remote = new VideoFrameLayout(remoteContainer.getContext());
        RelativeLayout.LayoutParams params = generateParams();
        remote.setLayoutParams(params);

        boolean isLoudest = at == 0;
        boolean twoTiles = max == 2;

        remote.setTag(isLoudest ? ViewType.REMOTE_LOUDEST : ViewType.REMOTE);
        Logger.i("Size at: %d. Max: %d", at, max);

        boolean isLandscape = AppUtils.isLandscape(this.selfView.getResources());

        int currentWidth = params.width;
        int currentHeight = params.height;
        Logger.i("Remote size: %dx%d", currentWidth, currentHeight);

        if (max > 1) {
            int cellWidth = isLandscape
                    ? (twoTiles ? currentWidth / 2 : currentHeight / (max - 1))
                    : currentWidth / (max - 1);
            int cellHeight = twoTiles
                    ? (isLandscape ? currentHeight : currentHeight / 2)
                    : cellWidth;

            Logger.i("Cell size: %dx%d", cellWidth, cellHeight);

            params.width = isLandscape
                    ? (isLoudest) ? (currentWidth - cellWidth) : cellWidth
                    : (isLoudest) ? currentWidth : cellWidth;

            params.height = isLandscape
                    ? isLoudest ? currentHeight : cellHeight
                    : isLoudest ? (currentHeight - cellHeight) : cellHeight;

            Logger.i("View size: %dx%d", params.width, params.height);

            /* X-axis */
            params.addRule(isLandscape
                    ? isLoudest ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT
                    : isLoudest || twoTiles ? RelativeLayout.CENTER_HORIZONTAL : defineHorizontalRule(at, max));

            /* Y-axis */
            params.addRule(isLandscape
                    ? isLoudest || twoTiles ? RelativeLayout.CENTER_VERTICAL : defineVerticalRule(at, max)
                    : isLoudest ? RelativeLayout.ALIGN_PARENT_TOP : RelativeLayout.ALIGN_PARENT_BOTTOM);
        } else {
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        }

        if (BuildConfig.DEBUG) {
            remote.setBackgroundColor(at == 0 ? Color.GRAY : Color.GREEN);
            if (at == 2)
                remote.setBackgroundColor(Color.RED);
            if (at == 3)
                remote.setBackgroundColor(Color.YELLOW);
        }

        remoteContainer.addView(remote);
    }

    private int defineVerticalRule(int at, int max) {
        switch (at) {
            case 1:
                return RelativeLayout.ALIGN_PARENT_TOP;
            case 2:
                if (max == 3) return RelativeLayout.ALIGN_PARENT_BOTTOM;
                if (max == 4) return RelativeLayout.CENTER_VERTICAL;
                break;
            case 3:
                if (max == 4) return RelativeLayout.ALIGN_PARENT_BOTTOM;
                break;
        }

        return RelativeLayout.CENTER_VERTICAL;
    }

    private int defineHorizontalRule(int at, int max) {
        if (at == 1) return RelativeLayout.ALIGN_PARENT_LEFT;
        if (at == 2 && max == 3) return RelativeLayout.ALIGN_PARENT_RIGHT;
        if (at == 2 && max == 4) return RelativeLayout.CENTER_HORIZONTAL;
        if (at == 3 && max == 4) return RelativeLayout.ALIGN_PARENT_RIGHT;

        return RelativeLayout.CENTER_HORIZONTAL;
    }

    private RelativeLayout.LayoutParams generateParams() {
        int containerWidth = remoteContainer.getMeasuredWidth();
        int containerHeight = remoteContainer.getMeasuredHeight();
        return new RelativeLayout.LayoutParams(containerWidth, containerHeight);
    }
}

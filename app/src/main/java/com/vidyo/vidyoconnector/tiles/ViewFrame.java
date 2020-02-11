package com.vidyo.vidyoconnector.tiles;

import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.vidyo.vidyoconnector.utils.Logger;

import java.util.UUID;

/**
 * Tile representation
 */
public class ViewFrame implements Comparable<ViewFrame> {

    private View view;
    private ViewType viewType;
    private String unique;

    private final int order;

    public ViewFrame(View view, ViewType viewType) {
        this.view = view;
        this.unique = UUID.randomUUID().toString();
        this.viewType = viewType;
        this.order = viewType.getOrder();
    }

    @NonNull
    public View getView() {
        return view;
    }

    public String getId() {
        return unique;
    }

    public ViewType getType() {
        return viewType;
    }

    @Override
    public int compareTo(@NonNull ViewFrame to) {
        return Integer.compare(order, to.order);
    }

    View invalidate(int width, int height, boolean anyRemote, boolean hasParticipants, boolean isLandscape) {
        Logger.i("Invalidate view frame: %s. Land: %s.", viewType, isLandscape);

        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();

        switch (viewType) {
            case LOCAL:
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

                int currentWidth = layoutParams.width;
                int currentHeight = layoutParams.height;

                int nextWidth = anyRemote ? (isLandscape ? width / 8 : width / 4) : width;
                int nextHeight = anyRemote ? (isLandscape ? height / 3 : height / 4) : height;

                this.view.bringToFront();

                if (currentWidth == nextWidth && currentHeight == nextHeight) {
                    return this.view;
                }

                layoutParams.width = nextWidth;
                layoutParams.height = nextHeight;
                break;
            case REMOTE:
                layoutParams.width = width;
                layoutParams.height = height;
                break;
            case SHARE:
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);

                this.view.bringToFront();

                int currentShareWidth = layoutParams.width;
                int currentShareHeight = layoutParams.height;

                int nextShareWidth = hasParticipants ? (isLandscape ? height / 3 : width / 2) : width;
                int nextShareHeight = hasParticipants ? (isLandscape ? height / 3 : width / 2) : height;

                if (currentShareWidth == nextShareWidth && currentShareHeight == nextShareHeight) {
                    return this.view;
                }

                layoutParams.width = nextShareWidth;
                layoutParams.height = nextShareHeight;
                break;
        }

        return this.view;
    }
}
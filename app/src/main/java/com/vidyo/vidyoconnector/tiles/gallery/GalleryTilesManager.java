package com.vidyo.vidyoconnector.tiles.gallery;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.UiThread;

import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Device.LocalCamera;
import com.vidyo.VidyoClient.Endpoint.Participant;
import com.vidyo.vidyoconnector.tiles.TilesApi;
import com.vidyo.vidyoconnector.tiles.model.Command;
import com.vidyo.vidyoconnector.tiles.model.RemoteHolder;
import com.vidyo.vidyoconnector.utils.Logger;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import static com.vidyo.vidyoconnector.tiles.model.Command.NONE;

public class GalleryTilesManager implements TilesApi, View.OnLayoutChangeListener {

    private final Connector connector;
    private final GalleryViewManager viewManager;

    /* Handle post layout updates */
    private Command currentCommand = NONE;

    /**
     * List wrapper of remote participants
     */
    private final Set<RemoteHolder> streamHolderList;

    /**
     * Actual local camera reference
     */
    private LocalCamera localCamera;

    /**
     * Loudest participant
     */
    private Participant considerLoudest;

    public GalleryTilesManager(Connector connector, FrameLayout localContainer, RelativeLayout remoteContainer) {
        this.connector = connector;
        this.viewManager = new GalleryViewManager(localContainer, remoteContainer);

        this.streamHolderList = new HashSet<>();
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        view.removeOnLayoutChangeListener(this);

        if (this.currentCommand != NONE) {
            switch (this.currentCommand) {
                case ATTACH_LOCAL:
                    attachLocal(this.localCamera);
                    break;
                case INVALIDATE_ALL:
                    View local = this.viewManager.getLocal();
                    if (local != null) {
                        local.addOnLayoutChangeListener(GalleryTilesManager.this);
                        local.requestLayout();
                    }

                    for (View remoteView : this.viewManager.remoteViews()) {
                        remoteView.addOnLayoutChangeListener(GalleryTilesManager.this);
                        remoteView.requestLayout();
                    }
                    break;
            }

            this.currentCommand = NONE;
            return;
        }

        int width = view.getWidth();
        int height = view.getHeight();

        this.connector.showViewAt(view, 0, 0, width, height);
        Logger.i("ShowViewAt: " + width + ", " + height);
    }

    /**
     * Start local camera rendering
     *
     * @param localCamera {@link LocalCamera}
     */
    @UiThread
    @Override
    public void attachLocal(LocalCamera localCamera) {
        if (localCamera == null) return;
        this.localCamera = localCamera;

        final View local = viewManager.getLocal();
        local.setVisibility(View.VISIBLE);

        /* Refresh container */
        if (local.getMeasuredHeight() == 0 || local.getMeasuredWidth() == 0) {
            this.currentCommand = Command.ATTACH_LOCAL;
            local.post(() -> {
                local.addOnLayoutChangeListener(this);
                local.requestLayout();
            });
            return;
        }

        View self = viewManager.updateSelfViewPosition(this.streamHolderList.size());
        connector.assignViewToLocalCamera(self, localCamera, true, false);

        self.post(() -> {
            self.addOnLayoutChangeListener(this);
            self.requestLayout();
        });
    }

    @Override
    public void detachLocal() {
        View view = this.viewManager.getLocal();
        connector.hideView(view);
        view.setVisibility(View.INVISIBLE);
    }

    @Override
    public void attachRemote(RemoteHolder remote) {
        if (streamHolderList.add(remote)) {
            invalidateTiles();
        }
    }

    @Override
    public void detachRemote(RemoteHolder remote) {
        if (this.streamHolderList.remove(remote)) {
            invalidateTiles();
        }
    }

    @Override
    public void updateLoudest(Participant participant) {
        this.considerLoudest = participant;
        invalidateTiles();
    }

    /**
     * @return {@link LocalCamera} cached.
     */
    public LocalCamera getLastSelectedLocalCamera() {
        return this.localCamera;
    }

    @Override
    public void requestInvalidate() {
        new Handler(Looper.getMainLooper()).postDelayed(this::invalidateTiles, 500);
    }

    @Override
    public void shutDown() {
        this.connector.hideView(this.viewManager.getLocal());
        dropRemoteRenderers();

        this.streamHolderList.clear();
        this.localCamera = null;
    }

    private void invalidateTiles() {
        int participants = this.streamHolderList.size();
        Logger.i("Invalidate with count: %d", participants);

        View local = this.viewManager.updateSelfViewPosition(participants);
        local.addOnLayoutChangeListener(this);
        local.requestLayout();

        dropRemoteRenderers();

        this.viewManager.update(participants);
        Queue<View> available = this.viewManager.remoteViews();

        /* Loudest */
        RemoteHolder loudest = findLoudest();
        Set<RemoteHolder> temp = new HashSet<>(this.streamHolderList);

        View loudestView = this.viewManager.loudest();
        if (loudest != null && loudestView != null) {
            available.remove(loudestView);
            temp.remove(loudest);

            Logger.i("Loudest consumed. Name: %s. Available views: %d.", loudest.getName(), available.size());
            render(loudest, loudestView);
        }

        /* Rest */
        for (RemoteHolder remoteHolder : temp) {
            if (available.isEmpty()) break;

            View view = available.poll();
            if (view == null) {
                Logger.e("Skip null view");
                continue;
            }

            render(remoteHolder, view);
        }
    }

    private void render(RemoteHolder holder, View view) {
        int width = view.getLayoutParams().width;
        int height = view.getLayoutParams().height;

        Logger.i("Assign remote video: %dx%d", width, height);
        connector.assignViewToRemoteCamera(view, holder.getCamera(), true, true);

        view.post(() -> {
            view.addOnLayoutChangeListener(GalleryTilesManager.this);
            view.requestLayout();
        });
    }

    private RemoteHolder findLoudest() {
        RemoteHolder first = null;
        for (RemoteHolder remoteHolder : this.streamHolderList) {
            if (first == null) first = remoteHolder;

            String id = remoteHolder.getId();
            if (this.considerLoudest != null && id.equalsIgnoreCase(this.considerLoudest.getId())) {
                return remoteHolder;
            }
        }

        return first;
    }

    private void dropRemoteRenderers() {
        for (View view : this.viewManager.remoteViews()) {
            this.connector.hideView(view);
        }
    }
}

package com.vidyo.vidyoconnector.tiles;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.UiThread;

import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Device.LocalCamera;
import com.vidyo.VidyoClient.Device.RemoteCamera;
import com.vidyo.VidyoClient.Device.RemoteWindowShare;
import com.vidyo.VidyoClient.Endpoint.Participant;
import com.vidyo.vidyoconnector.utils.AppUtils;
import com.vidyo.vidyoconnector.utils.Logger;
import com.vidyo.vidyoconnector.view.VideoFrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper class for Custom Tiles handling.
 */
public class CustomTilesHelper implements View.OnLayoutChangeListener {

    enum Command {
        INVALIDATE_ALL, ATTACH_LOCAL, NONE
    }

    /**
     * {@link Context} app context
     */
    private final Context context;

    /**
     * {@link Connector} main connector instance
     */
    private final Connector connector;

    /**
     * Custom views container
     */
    private final RelativeLayout container;

    /**
     * Command to be handled after container becomes available
     */
    private Command currentCommand = Command.NONE;

    /**
     * List of available views
     */
    private final List<ViewFrame> viewFrameList;

    /**
     * List wrapper of remote participants
     */
    private final List<RemoteHolder> streamHolderList;

    /**
     * Actual local camera reference
     */
    private LocalCamera localCamera;

    /**
     * Loudest participant
     */
    private Participant considerLoudest;

    public CustomTilesHelper(Context context, Connector connector, RelativeLayout container) {
        this.context = context;
        this.connector = connector;

        this.container = container;

        this.viewFrameList = new LinkedList<>();
        this.streamHolderList = new ArrayList<>();
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        v.removeOnLayoutChangeListener(this);

        int width = v.getWidth();
        int height = v.getHeight();

        if (v == this.container) {
            Logger.i(CustomTilesHelper.class, "Refresh container: " + width + ", " + height);

            switch (this.currentCommand) {
                case ATTACH_LOCAL:
                    attachLocal(this.localCamera);
                    break;
                case INVALIDATE_ALL:
                    invalidateViews();
                    break;
            }

            this.currentCommand = Command.NONE;
        } else {
            this.connector.showViewAt(v, 0, 0, width, height);
            Logger.i(CustomTilesHelper.class, "Show view at: " + width + ", " + height);
        }
    }

    /**
     * Start local camera rendering
     *
     * @param localCamera {@link LocalCamera}
     */
    @UiThread
    public void attachLocal(LocalCamera localCamera) {
        assertInstance();

        if (localCamera == null) return;
        this.localCamera = localCamera;

        /* Refresh container */
        if (this.container.getWidth() == 0 && this.container.getHeight() == 0) {
            this.currentCommand = Command.ATTACH_LOCAL;

            this.container.addOnLayoutChangeListener(this);
            this.container.requestLayout();
            return;
        }

        ViewFrame localFrame = findFrame(ViewType.LOCAL);
        View frame = localFrame.getView();

        connector.assignViewToLocalCamera(frame, localCamera, true, false);
        frame.setVisibility(View.VISIBLE);

        frame.addOnLayoutChangeListener(this);
        frame.requestLayout();
    }

    /**
     * Stop {@link LocalCamera} camera rendering
     */
    @UiThread
    public void detachLocal() {
        assertInstance();

        ViewFrame localFrame = findFrame(ViewType.LOCAL);
        View frame = localFrame.getView();
        connector.hideView(frame);
        frame.setVisibility(View.INVISIBLE);
    }

    /**
     * Add remote wrapped participants to cache
     *
     * @param remote {@link RemoteHolder} wrapper of participant and his camera.
     */
    @UiThread
    public void attachRemote(RemoteHolder remote) {
        assertInstance();

        if (!this.streamHolderList.contains(remote)) {
            this.streamHolderList.add(remote);
        }

        Logger.i(CustomTilesHelper.class, "RemoteHolder attach: " + streamHolderList.size());
        AppUtils.dump(this.streamHolderList);

        invalidateRemoteStreams();
        invalidateViews();
    }

    /**
     * Remove remote wrapped participants from cache.
     *
     * @param participant {@link Participant} to remove
     * @param isShare     whenever it's share [TODO]
     */
    @UiThread
    public void detachRemote(Participant participant, boolean isShare) {
        assertInstance();

        RemoteHolder remote = findRemote(participant, isShare);
        if (remote == null || !remote.isValid()) return;

        remote.release();
        this.streamHolderList.remove(remote);

        Logger.i(CustomTilesHelper.class, "RemoteHolder detach: " + streamHolderList.size());
        AppUtils.dump(this.streamHolderList);

        invalidateRemoteStreams();
        invalidateViews();
    }

    /**
     * Pass loudest participant to logic and invalidate streams.
     *
     * @param participant {@link Participant}
     */
    @UiThread
    public void updateLoudest(Participant participant) {
        this.considerLoudest = participant;

        invalidateRemoteStreams();
        invalidateViews();
    }

    /**
     * Refresh remote streams following loudest or any available.
     * Mark remote as rendering to avoid double assignment.
     */
    @UiThread
    private void invalidateRemoteStreams() {
        Logger.i(CustomTilesHelper.class, "Invalidate remote: %d", streamHolderList.size());

        if (!hasAnyRemote()) {
            unRenderRemote(ViewType.REMOTE);

            Logger.i("No items to render. Hide remote tiles.");
            return;
        }

        String loudestId = this.considerLoudest != null ? this.considerLoudest.getId() : "";

        boolean foundLoudest = false;

        for (RemoteHolder remoteHolder : this.streamHolderList) {
            if (remoteHolder.getId().equalsIgnoreCase(loudestId)) {
                foundLoudest = true;

                renderRemote(remoteHolder);
            } else {
                /* Clear rendering state for other participants */
                Logger.i("Stop rendering: %s", remoteHolder.getName());
                remoteHolder.setRendering(false);
            }
        }

        if (!foundLoudest) {
            Logger.i("Loudest participant not found. Picked 1st.");
            renderRemote(this.streamHolderList.get(0));
        }
    }

    /**
     * Render remote stream.
     *
     * @param remoteHolder {@link RemoteHolder}
     */
    @UiThread
    private void renderRemote(RemoteHolder remoteHolder) {
        if (remoteHolder == null || !remoteHolder.isValid()) return;

        if (remoteHolder.isRendering()) {
            Logger.i("Remote stream is already rendering.");
            return;
        }

        Logger.i("Start rendering. Name: %s", remoteHolder.getName());

        ViewFrame viewFrame = findFrame(ViewType.REMOTE);
        View frame = viewFrame.getView();

        frame.setVisibility(View.VISIBLE);
        connector.hideView(frame);

        /* Assign remote view */
        RemoteCamera remoteCamera = remoteHolder.getCamera();
        if (remoteCamera != null && !remoteHolder.isShare()) {
            connector.assignViewToRemoteCamera(frame, remoteCamera, true, false);
        }

        /* Assign remote share */
        RemoteWindowShare remoteWindowShare = remoteHolder.getShare();
        if (remoteHolder.getShare() != null && remoteHolder.isShare()) {
            connector.assignViewToRemoteWindowShare(frame, remoteWindowShare, true, false);
        }

        remoteHolder.setRendering(true);
    }

    /**
     * Shut down remote rendering.
     */
    @UiThread
    private void unRenderRemote(ViewType viewType) {
        ViewFrame remoteTile = findFrame(viewType);
        View frame = remoteTile.getView();
        frame.setVisibility(View.GONE);
        this.connector.hideView(frame);
    }

    /**
     * Update internal views size. Probably after rotate.
     */
    @UiThread
    private void invalidateViews() {
        Logger.i(CustomTilesHelper.class, "Invalidate. Views: " + viewFrameList.size());
        Collections.sort(viewFrameList);

        int containerWidth = container.getMeasuredWidth();
        int containerHeight = container.getMeasuredHeight();

        for (ViewFrame viewFrame : viewFrameList) {
            boolean isLandscape = AppUtils.isLandscape(context.getResources());

            View frame = viewFrame.invalidate(containerWidth, containerHeight, hasAnyRemote(), hasRemoteParticipants(), isLandscape);

            frame.addOnLayoutChangeListener(this);
            frame.requestLayout();
        }
    }

    /**
     * @return {@link LocalCamera} cached.
     */
    public LocalCamera getLastSelectedLocalCamera() {
        return this.localCamera;
    }

    /**
     * Invalidate tiles. Both remote and local.
     */
    @UiThread
    public void requestInvalidate() {
        if (this.container == null) return;

        this.currentCommand = Command.INVALIDATE_ALL;
        this.container.addOnLayoutChangeListener(this);
    }

    /**
     * Finish any custom tiles processing.
     */
    public void shutDown() {
        assertInstance();

        this.connector.hideView(findFrame(ViewType.LOCAL));
        this.connector.hideView(findFrame(ViewType.REMOTE));

        this.streamHolderList.clear();
        this.viewFrameList.clear();

        this.localCamera = null;
    }

    private boolean hasAnyRemote() {
        return !streamHolderList.isEmpty();
    }

    private boolean hasRemoteShare() {
        for (RemoteHolder remoteHolder : this.streamHolderList) {
            if (remoteHolder.isShare()) return true;
        }

        return false;
    }

    private boolean hasRemoteParticipants() {
        for (RemoteHolder remoteHolder : this.streamHolderList) {
            if (!remoteHolder.isShare()) return true;
        }

        return false;
    }

    /**
     * Find or generate view frame based on {@link ViewType}
     */
    private ViewFrame findFrame(ViewType viewType) {
        for (ViewFrame viewFrame : viewFrameList) {
            if (viewFrame.getType() == viewType) return viewFrame;
        }

        /* Was not able to find. Add dynamically. */
        return generateFrame(viewType);
    }

    /**
     * Look for remove holder {@link RemoteHolder}
     * <p>
     * Consider that SHARE and PARTICIPANT would have SAME ID!
     *
     * @param participant by {@link Participant} object
     * @param lookShare   if we are looking for share
     * @return {@link RemoteHolder}
     */
    private RemoteHolder findRemote(Participant participant, boolean lookShare) {
        for (RemoteHolder remote : streamHolderList) {
            boolean match = remote.getId().equals(participant.id);

            if (match && lookShare && remote.isShare()) {
                return remote;
            }

            if (match) return remote;
        }

        return null;
    }

    /**
     * Generate custom view frame based on type {@link ViewType}
     *
     * @param viewType {@link ViewType} of frame
     * @return {@link ViewFrame}
     */
    private ViewFrame generateFrame(ViewType viewType) {
        Logger.i("Generate frame: %s", viewType.name());

        int containerWidth = container.getMeasuredWidth();
        int containerHeight = container.getMeasuredHeight();

        Logger.i("Container width: %1d, height: %2d", containerWidth, containerHeight);

        VideoFrameLayout frame = new VideoFrameLayout(this.context);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(containerWidth, containerHeight);
        frame.setLayoutParams(layoutParams);

        frame.setTag(viewType.name());

        frame.setBackgroundColor(Color.GRAY);
        ViewFrame viewFrame = new ViewFrame(frame, viewType);
        container.addView(frame);
        viewFrameList.add(viewFrame);

        boolean isLandscape = AppUtils.isLandscape(context.getResources());
        viewFrame.invalidate(containerWidth, containerHeight, hasAnyRemote(), hasRemoteParticipants(), isLandscape);
        return viewFrame;
    }

    private void assertInstance() {
        if (context == null || connector == null || container == null)
            throw new RuntimeException("Object assertion!");
    }
}
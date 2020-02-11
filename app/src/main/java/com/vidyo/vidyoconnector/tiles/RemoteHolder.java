package com.vidyo.vidyoconnector.tiles;

import androidx.annotation.NonNull;

import com.vidyo.VidyoClient.Device.RemoteCamera;
import com.vidyo.VidyoClient.Device.RemoteWindowShare;
import com.vidyo.VidyoClient.Endpoint.Participant;

import java.util.Objects;

/**
 * Holder of remote stream whenever it's Share or Remote Camera.
 */
public class RemoteHolder {

    private Participant participant;

    private RemoteCamera camera;
    private RemoteWindowShare share;

    private boolean isShare;
    private boolean isRendering;

    private RemoteHolder(Participant participant) {
        this.participant = participant;
    }

    public RemoteHolder(Participant participant, RemoteCamera camera) {
        this(participant);
        this.camera = camera;
        this.isShare = false;
    }

    public RemoteHolder(Participant participant, RemoteWindowShare share) {
        this(participant);
        this.share = share;
        this.isShare = true;
    }

    public RemoteCamera getCamera() {
        return camera;
    }

    public RemoteWindowShare getShare() {
        return share;
    }

    public String getId() {
        return participant.id;
    }

    public boolean isShare() {
        return this.isShare;
    }

    public boolean isValid() {
        return this.participant != null;
    }

    public void setRendering(boolean rendering) {
        isRendering = rendering;
    }

    public boolean isRendering() {
        return isRendering;
    }

    public String getName() {
        return this.participant.getName();
    }

    public void release() {
        this.camera = null;
        this.participant = null;
        this.share = null;
    }

    @NonNull
    @Override
    public String toString() {
        return "Remote ID: " + this.getId().substring(0, 5) + ", Share: " + this.isShare;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteHolder remote = (RemoteHolder) o;
        return isShare == remote.isShare && Objects.equals(participant, remote.participant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(participant, isShare);
    }
}
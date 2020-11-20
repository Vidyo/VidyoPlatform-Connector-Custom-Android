package com.vidyo.vidyoconnector.tiles;

import com.vidyo.VidyoClient.Device.LocalCamera;
import com.vidyo.VidyoClient.Endpoint.Participant;
import com.vidyo.vidyoconnector.tiles.model.RemoteHolder;

public interface TilesApi {

    void attachLocal(LocalCamera localCamera);

    void detachLocal();

    void attachRemote(RemoteHolder remote);

    void detachRemote(RemoteHolder remote);

    void updateLoudest(Participant participant);

    LocalCamera getLastSelectedLocalCamera();

    void requestInvalidate();

    void shutDown();
}
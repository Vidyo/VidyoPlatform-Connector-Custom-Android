package com.vidyo.vidyoconnector;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Connector.ConnectorPkg;
import com.vidyo.VidyoClient.Device.Device;
import com.vidyo.VidyoClient.Device.LocalCamera;
import com.vidyo.VidyoClient.Device.LocalMicrophone;
import com.vidyo.VidyoClient.Device.LocalSpeaker;
import com.vidyo.VidyoClient.Device.RemoteCamera;
import com.vidyo.VidyoClient.Device.RemoteMicrophone;
import com.vidyo.VidyoClient.Device.RemoteWindowShare;
import com.vidyo.VidyoClient.Endpoint.Participant;
import com.vidyo.vidyoconnector.event.ControlEvent;
import com.vidyo.vidyoconnector.event.IControlLink;
import com.vidyo.vidyoconnector.tiles.CustomTilesHelper;
import com.vidyo.vidyoconnector.tiles.RemoteHolder;
import com.vidyo.vidyoconnector.utils.AppUtils;
import com.vidyo.vidyoconnector.utils.Logger;
import com.vidyo.vidyoconnector.view.ControlView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conference activity holding all connection and callbacks logic.
 */
public class VideoConferenceActivity extends FragmentActivity implements Connector.IConnect,
        Connector.IRegisterLocalCameraEventListener, Connector.IRegisterRemoteCameraEventListener,
        Connector.IRegisterLocalSpeakerEventListener, Connector.IRegisterRemoteMicrophoneEventListener,
        Connector.IRegisterLocalMicrophoneEventListener, Connector.IRegisterResourceManagerEventListener,
        Connector.IRegisterRemoteWindowShareEventListener, Connector.IRegisterParticipantEventListener, IControlLink {

    public static final String PORTAL_KEY = "portal.key";
    public static final String ROOM_KEY = "room.key";
    public static final String PIN_KEY = "pin.key";
    public static final String NAME_KEY = "name.key";

    private ControlView controlView;
    private View progressBar;

    private Connector connector;

    private AtomicBoolean isCameraDisabledForBackground = new AtomicBoolean(false);
    private AtomicBoolean isDisconnectAndQuit = new AtomicBoolean(false);

    private CustomTilesHelper customTilesHelper;

    @Override
    public void onStart() {
        super.onStart();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        if (connector != null) {
            ControlView.State state = controlView.getState();
            connector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Foreground);

            connector.setCameraPrivacy(state.isMuteCamera());
            connector.setMicrophonePrivacy(state.isMuteMic());
            connector.setSpeakerPrivacy(state.isMuteSpeaker());
        }

        LocalCamera localCamera = customTilesHelper.getLastSelectedLocalCamera();
        if (connector != null && localCamera != null && isCameraDisabledForBackground.getAndSet(false)) {
            connector.selectLocalCamera(localCamera);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (connector != null) {
            connector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Background);

            connector.setCameraPrivacy(true);
            connector.setMicrophonePrivacy(true);
            connector.setSpeakerPrivacy(true);
        }

        if (!isFinishing() && connector != null && !controlView.getState().isMuteCamera()
                && !isCameraDisabledForBackground.getAndSet(true)) {
            connector.selectLocalCamera(null);
            customTilesHelper.detachLocal();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_layouts);

        ConnectorPkg.initialize();
        ConnectorPkg.setApplicationUIContext(this);

        progressBar = findViewById(R.id.progress);
        progressBar.setVisibility(View.GONE);

        controlView = findViewById(R.id.control_view);
        controlView.registerListener(this);

        /*
         * Connector instance created with NULL passed as video frame. Local & RemoteHolder camera will be assigned later.
         */
        connector = new Connector(null, Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default,
                8, "*@VidyoClient info@VidyoConnector info warning",
                AppUtils.configLogFile(this), 0);
        Logger.i("Connector instance has been created.");

        controlView.showVersion(connector.getVersion());

        RelativeLayout container = findViewById(R.id.master_container);
        customTilesHelper = new CustomTilesHelper(this, connector, container);

        /*
         * Register all the  listeners required for custom implementation
         */
        connector.registerLocalCameraEventListener(this);
        connector.registerLocalSpeakerEventListener(this);
        connector.registerLocalMicrophoneEventListener(this);

        connector.registerRemoteCameraEventListener(this);
        connector.registerRemoteMicrophoneEventListener(this);

        connector.registerRemoteWindowShareEventListener(this);

        connector.registerParticipantEventListener(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (customTilesHelper != null) customTilesHelper.requestInvalidate();
    }

    @Override
    public void onSuccess() {
        if (!connector.registerResourceManagerEventListener(this)) {
            Logger.e("Failed to register resource manager event listener");
        } else {
            Logger.e("Resource manager event listener succeed.");
        }

        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, R.string.connected, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(true);
            controlView.updateConnectionState(ControlView.ConnectionState.CONNECTED);
            controlView.disable(false);
        });
    }

    @Override
    public void onFailure(final Connector.ConnectorFailReason connectorFailReason) {
        if (connector != null) connector.unregisterResourceManagerEventListener();

        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, connectorFailReason.name(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(false);
            controlView.updateConnectionState(ControlView.ConnectionState.FAILED);
            controlView.disable(false);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    @Override
    public void onDisconnected(Connector.ConnectorDisconnectReason connectorDisconnectReason) {
        if (connector != null) connector.unregisterResourceManagerEventListener();

        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, R.string.disconnected, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(false);
            controlView.updateConnectionState(ControlView.ConnectionState.DISCONNECTED);
            controlView.disable(false);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            /* Wrap up the conference */
            if (isDisconnectAndQuit.get()) {
                finish();
            }
        });
    }

    @Override
    public void onControlEvent(ControlEvent event) {
        if (connector == null) return;

        switch (event.getCall()) {
            case CONNECT_DISCONNECT:
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                progressBar.setVisibility(View.VISIBLE);
                controlView.disable(true);
                boolean state = (boolean) event.getValue();
                controlView.updateConnectionState(state ? ControlView.ConnectionState.CONNECTING : ControlView.ConnectionState.DISCONNECTING);

                if (state) {
                    Intent intent = getIntent();

                    String portal = intent.getStringExtra(PORTAL_KEY);
                    String room = intent.getStringExtra(ROOM_KEY);
                    String pin = intent.getStringExtra(PIN_KEY);
                    String name = intent.getStringExtra(NAME_KEY);

                    Logger.i("Start connection: %s, %s, %s, %s", portal, room, pin, name);
                    connector.connectToRoomAsGuest(portal, name, room, pin, this);
                } else {
                    if (connector != null) connector.disconnect();
                }
                break;
            case MUTE_CAMERA:
                boolean cameraPrivacy = (boolean) event.getValue();
                connector.setCameraPrivacy(cameraPrivacy);

                if (cameraPrivacy) {
                    connector.selectLocalCamera(null);
                    customTilesHelper.detachLocal();
                } else {
                    connector.selectLocalCamera(customTilesHelper.getLastSelectedLocalCamera());
                }
                break;
            case MUTE_MIC:
                connector.setMicrophonePrivacy((boolean) event.getValue());
                break;
            case MUTE_SPEAKER:
                connector.setSpeakerPrivacy((boolean) event.getValue());
                break;
            case CYCLE_CAMERA:
                connector.cycleCamera();
                break;
            case DEBUG_OPTION:
                boolean value = (boolean) event.getValue();
                if (value) {
                    connector.enableDebug(7776, "");
                } else {
                    connector.disableDebug();
                }

                Toast.makeText(VideoConferenceActivity.this, getString(R.string.debug_option) + value, Toast.LENGTH_SHORT).show();
                break;
            case SEND_LOGS:
                AppUtils.sendLogs(this);
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (connector == null) {
            Logger.e("Connector is null!");
            finish();
            return;
        }

        Connector.ConnectorState state = connector.getState();

        if (state == Connector.ConnectorState.VIDYO_CONNECTORSTATE_Idle || state == Connector.ConnectorState.VIDYO_CONNECTORSTATE_Ready) {
            super.onBackPressed();
        } else {
            /* You are still connecting or connected */
            Toast.makeText(this, "You have to disconnect or await connection first", Toast.LENGTH_SHORT).show();

            /* Start disconnection if connected. Quit afterward. */
            if (state == Connector.ConnectorState.VIDYO_CONNECTORSTATE_Connected && !isDisconnectAndQuit.get()) {
                isDisconnectAndQuit.set(true);
                onControlEvent(new ControlEvent<>(ControlEvent.Call.CONNECT_DISCONNECT, false));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controlView != null) controlView.unregisterListener();

        if (customTilesHelper != null) customTilesHelper.shutDown();

        if (connector != null) {
            connector.unregisterLocalCameraEventListener();
            connector.unregisterLocalSpeakerEventListener();
            connector.unregisterLocalMicrophoneEventListener();

            connector.unregisterRemoteCameraEventListener();
            connector.unregisterRemoteMicrophoneEventListener();

            connector.unregisterParticipantEventListener();

            connector.disable();
            connector = null;
        }

        ConnectorPkg.uninitialize();
        ConnectorPkg.setApplicationUIContext(null);

        Logger.i("Connector instance has been released.");
    }

    @Override
    public void onLocalCameraAdded(LocalCamera localCamera) {
        Logger.i("Local camera added.");
    }

    @Override
    public void onLocalCameraSelected(final LocalCamera localCamera) {
        Logger.i(VideoConferenceActivity.class, "Local camera selected");

        runOnUiThread(() -> customTilesHelper.attachLocal(localCamera));
    }

    @Override
    public void onLocalCameraRemoved(LocalCamera localCamera) {
        Logger.i("Local camera removed.");
    }

    @Override
    public void onRemoteCameraAdded(final RemoteCamera remoteCamera, final Participant participant) {
        Logger.i(VideoConferenceActivity.class, "RemoteHolder camera added");

        runOnUiThread(() -> customTilesHelper.attachRemote(new RemoteHolder(participant, remoteCamera)));
    }

    @Override
    public void onRemoteCameraRemoved(RemoteCamera remoteCamera, final Participant participant) {
        Logger.i(VideoConferenceActivity.class, "RemoteHolder camera removed");

        runOnUiThread(() -> customTilesHelper.detachRemote(participant, false));
    }

    @Override
    public void onRemoteWindowShareAdded(final RemoteWindowShare remoteWindowShare, final Participant participant) {
        Logger.i(VideoConferenceActivity.class, "RemoteHolder share added");

//        runOnUiThread(() -> customTilesHelper.attachRemote(new RemoteHolder(participant, remoteWindowShare)));
    }

    @Override
    public void onRemoteWindowShareRemoved(RemoteWindowShare remoteWindowShare, final Participant participant) {
        Logger.i(VideoConferenceActivity.class, "RemoteHolder share removed");

//        runOnUiThread(() -> customTilesHelper.detachRemote(participant, true));
    }

    @Override
    public void onLoudestParticipantChanged(Participant participant, boolean b) {
        Logger.i("Loudest participant arrived. Name: %s", participant.getName());

        runOnUiThread(() -> {
            if (customTilesHelper != null) customTilesHelper.updateLoudest(participant);
        });
    }

    @Override
    public void onLocalCameraStateUpdated(LocalCamera localCamera, Device.DeviceState deviceState) {
    }

    @Override
    public void onRemoteWindowShareStateUpdated(RemoteWindowShare remoteWindowShare, Participant participant, Device.DeviceState deviceState) {
    }

    @Override
    public void onRemoteCameraStateUpdated(RemoteCamera remoteCamera, Participant participant, Device.DeviceState deviceState) {
    }

    @Override
    public void onLocalSpeakerAdded(LocalSpeaker localSpeaker) {

    }

    @Override
    public void onLocalSpeakerRemoved(LocalSpeaker localSpeaker) {

    }

    @Override
    public void onLocalSpeakerSelected(LocalSpeaker localSpeaker) {

    }

    @Override
    public void onLocalSpeakerStateUpdated(LocalSpeaker localSpeaker, Device.DeviceState deviceState) {

    }

    @Override
    public void onRemoteMicrophoneAdded(RemoteMicrophone remoteMicrophone, Participant participant) {
    }

    @Override
    public void onRemoteMicrophoneRemoved(RemoteMicrophone remoteMicrophone, Participant participant) {
    }

    @Override
    public void onRemoteMicrophoneStateUpdated(RemoteMicrophone remoteMicrophone, Participant participant, Device.DeviceState deviceState) {
    }

    @Override
    public void onLocalMicrophoneAdded(LocalMicrophone localMicrophone) {

    }

    @Override
    public void onLocalMicrophoneRemoved(LocalMicrophone localMicrophone) {

    }

    @Override
    public void onLocalMicrophoneSelected(LocalMicrophone localMicrophone) {

    }

    @Override
    public void onLocalMicrophoneStateUpdated(LocalMicrophone localMicrophone, Device.DeviceState deviceState) {

    }

    @Override
    public void onAvailableResourcesChanged(int cpuEncode, int cpuDecode, int bandwidthSend, int bandwidthReceive) {
    }

    @Override
    public void onMaxRemoteSourcesChanged(int i) {
    }

    @Override
    public void onParticipantJoined(Participant participant) {
    }

    @Override
    public void onParticipantLeft(Participant participant) {
    }

    @Override
    public void onDynamicParticipantChanged(ArrayList<Participant> arrayList) {

    }
}
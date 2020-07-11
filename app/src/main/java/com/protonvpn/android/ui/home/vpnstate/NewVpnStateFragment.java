/*
 * Copyright (c) 2017 Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.ui.home.vpnstate;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.transition.TransitionManager;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.airbnb.lottie.value.ScaleXY;
import com.github.mikephil.charting.jobs.MoveViewJob;
import com.protonvpn.android.R;
import com.protonvpn.android.api.ApiResult;
import com.protonvpn.android.api.ProtonApiRetroFit;
import com.protonvpn.android.appconfig.AppConfig;
import com.protonvpn.android.bus.ConnectToProfile;
import com.protonvpn.android.bus.ConnectedToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.TrafficUpdate;
import com.protonvpn.android.components.BaseFragment;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.LoaderUI;
import com.protonvpn.android.components.NetworkFrameLayout;
import com.protonvpn.android.components.VPNException;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.ui.home.ServerListUpdater;
import com.protonvpn.android.utils.ConnectionTools;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.TimeUtils;
import com.protonvpn.android.utils.TrafficMonitor;
import com.protonvpn.android.vpn.RetryInfo;
import com.protonvpn.android.vpn.VpnState;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.protonvpn.android.vpn.VpnStateMonitor.ErrorType;

import java.util.Timer;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.OnClick;
import de.hdodenhof.circleimageview.CircleImageView;

@ContentLayout(R.layout.new_vpn_state_fragment)
public class NewVpnStateFragment extends BaseFragment implements LoaderUI {

    private static final String KEY_ERROR_CONNECTION_ID = "error_connection_id";
    private static final String KEY_DISMISSED_CONNECTION_ID = "dismissed_connection_id";

    @BindView(R.id.layoutConnected)
    View layoutConnected;
    @BindView(R.id.rootView)
    ConstraintLayout rootView;
    @BindView(R.id.textServerName)
    TextView textServerName;
    @BindView(R.id.textServerIp)
    TextView textServerIp;
    @BindView(R.id.textDownloadSpeed)
    TextView textDownloadSpeed;
    @BindView(R.id.textUploadSpeed)
    TextView textUploadSpeed;
    @BindView(R.id.textDownloadVolume)
    TextView textDownloadVolume;
    @BindView(R.id.textUploadVolume)
    TextView textUploadVolume;
    @BindView(R.id.textProtocol)
    TextView textProtocol;
    @BindView(R.id.textSessionTime)
    TextView textSessionTime;
    @BindView(R.id.textLoad)
    TextView textLoad;
    @BindView(R.id.imageLoad)
    CircleImageView imageLoad;
    @BindView(R.id.animation_view)
    LottieAnimationView animationView;
    @BindView(R.id.txtConnect)
    TextView txtConnect;

    @Inject
    ProtonApiRetroFit api;
    @Inject
    ServerManager manager;
    @Inject
    UserData userData;
    @Inject
    AppConfig appConfig;
    @Inject
    VpnStateMonitor stateMonitor;
    @Inject
    ServerListUpdater serverListUpdater;
    @Inject
    TrafficMonitor trafficMonitor;
    boolean isFirstTime = true;
    private long errorConnectionID;
    private long dismissedConnectionID;
    private Timer graphUpdateTimer;

    @Override
    public void onViewCreated() {
        registerForEvents();
        updateNotConnectedView();

        /*serverListUpdater.getIpAddress()
            .observe(getViewLifecycleOwner(), (ip) -> textCurrentIp.setText(textCurrentIp.getContext()
                .getString(R.string.notConnectedCurrentIp,
                    ip.isEmpty() ? getString(R.string.stateFragmentUnknownIp) : ip)));*/

        stateMonitor.getVpnStatus().observe(getViewLifecycleOwner(), state -> updateView(false, state));
        trafficMonitor
                .getTrafficStatus()
                .observe(getViewLifecycleOwner(), this::onTrafficUpdate);

        initAnimation2();
    }

    @OnClick(R.id.txtConnect)
    public void connect() {
        isFirstTime = true;
        Profile defaultProfile = manager.getDefaultConnection();
        EventBus.post(new ConnectToProfile(defaultProfile));
        Log.d("ClickOnConnect");
    }

    @OnClick(R.id.buttonDisconnect)
    public void buttonCancel() {
        stateMonitor.disconnect();
    }

    private void initAnimation1() {
        final int[] step = {0};
        animationView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (step[0]) {
                    case 0:
                        animationView.setAnimation(R.raw.connecting);
                        animationView.setMinFrame(0);
                        animationView.playAnimation();
                        animationView.addAnimatorUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                if (animationView.getFrame() == 38) {
                                    animationView.setMinAndMaxFrame(38, 39);
                                    animationView.setRepeatCount(LottieDrawable.INFINITE);
                                    animationView.setRepeatMode(LottieDrawable.REVERSE);
                                    animationView.setSpeed(0.1f);
                                    animationView.playAnimation();
                                    animationView.removeAllUpdateListeners();
                                }
                            }
                        });
                        step[0] = 1;
                        break;
                    case 1:
                        animationView.setSpeed(1f);
                        animationView.setMaxProgress(1);
                        animationView.setRepeatCount(0);
                        animationView.playAnimation();
                        animationView.addAnimatorUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                if (animationView.getProgress() == 1) {
                                    animationView.setAnimation(R.raw.connected);
                                    animationView.setMinFrame(0);
                                    animationView.playAnimation();
                                    animationView.addAnimatorUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                        @Override
                                        public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                            if (animationView.getFrame() == 278) {
                                                animationView.setMinFrame(278);
                                                animationView.setRepeatCount(LottieDrawable.INFINITE);
                                                animationView.setRepeatMode(LottieDrawable.REVERSE);
                                                animationView.playAnimation();
                                                animationView.removeAllUpdateListeners();
                                            }
                                        }
                                    });
                                }
                            }
                        });
                        break;
                }

            }
        });
    }

    private void initAnimation2() {
        Log.d("iniAnimation");
        animationView.setAnimation(R.raw.button);
        animationView.addValueCallback(new KeyPath("order"),
                LottieProperty.TEXT_SIZE,
                new LottieValueCallback<>(0.0f));
        animationView.addValueCallback(new KeyPath("ADD", "**"),
                LottieProperty.TRANSFORM_SCALE,
                new LottieValueCallback<>(new ScaleXY(0f, 0f)));
        animationView.addValueCallback(new KeyPath("squiggle", "**"),
                LottieProperty.TRANSFORM_SCALE,
                new LottieValueCallback<>(new ScaleXY(0f, 0f)));
        animationView.addValueCallback(new KeyPath("pink blob", "**"),
                LottieProperty.TRANSFORM_SCALE,
                new LottieValueCallback<>(new ScaleXY(0f, 0f)));
        animationView.addValueCallback(new KeyPath("u", "**"),
                LottieProperty.TRANSFORM_SCALE,
                new LottieValueCallback<>(new ScaleXY(0f, 0f)));
        animationView.addValueCallback(new KeyPath("u 2", "**"),
                LottieProperty.TRANSFORM_SCALE,
                new LottieValueCallback<>(new ScaleXY(0f, 0f)));
        animationView.addValueCallback(new KeyPath("yellow blob", "**"),
                LottieProperty.TRANSFORM_SCALE,
                new LottieValueCallback<>(new ScaleXY(0f, 0f)));
        txtConnect.setVisibility(View.VISIBLE);
        animationView.setRepeatCount(LottieDrawable.INFINITE);
        animationView.setMinFrame(0);
        animationView.playAnimation();
    }

    @Override
    public void onDestroyView() {
        // Workaround for charting library memory leak
        // https://github.com/PhilJay/MPAndroidChart/issues/2238
        MoveViewJob.getInstance(null, 0, 0, null, null);

        super.onDestroyView();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        errorConnectionID = 0;
        dismissedConnectionID = 0;
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_ERROR_CONNECTION_ID)) {
            errorConnectionID = (Long) savedInstanceState.getSerializable(KEY_ERROR_CONNECTION_ID);
            dismissedConnectionID = (Long) savedInstanceState.getSerializable(KEY_DISMISSED_CONNECTION_ID);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(KEY_ERROR_CONNECTION_ID, errorConnectionID);
        outState.putSerializable(KEY_DISMISSED_CONNECTION_ID, dismissedConnectionID);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (graphUpdateTimer != null) {
            graphUpdateTimer.cancel();
            graphUpdateTimer = null;
        }
    }

    private void initConnectingStateView(@Nullable Server profile, boolean fromSavedState) {
        TransitionManager.beginDelayedTransition(rootView);
        layoutConnected.setVisibility(View.GONE);
        if (isFirstTime) {
            Log.d("Connecting");
            isFirstTime = false;
            txtConnect.setVisibility(View.GONE);
            animationView.setAnimation(R.raw.shuttle);
            animationView.setMinFrame(0);
            animationView.playAnimation();
            animationView.addAnimatorUpdateListener(valueAnimator -> {
                if (animationView.getFrame() == 282) {
                    animationView.setMinAndMaxFrame(282, 500);
                    animationView.setRepeatCount(LottieDrawable.INFINITE);
                    animationView.setRepeatMode(LottieDrawable.RESTART);
                    animationView.playAnimation();
                    animationView.removeAllUpdateListeners();
                }
            });
        }
    }

    private void onTrafficUpdate(final @Nullable TrafficUpdate update) {
        if (getActivity() != null && update != null) {
            textSessionTime.setText(TimeUtils.getFormattedTimeFromSeconds(update.getSessionTimeSeconds()));
            textUploadSpeed.setText(update.getUploadSpeedString());
            textDownloadSpeed.setText(update.getDownloadSpeedString());
            textUploadVolume.setText(ConnectionTools.bytesToSize(update.getSessionUpload()));
            textDownloadVolume.setText(ConnectionTools.bytesToSize(update.getSessionDownload()));
        }
    }

    private void clearConnectedStatus() {
        if (graphUpdateTimer != null) {
            graphUpdateTimer.cancel();
            graphUpdateTimer = null;
        }
        initAnimation2();
        onTrafficUpdate(new TrafficUpdate(0, 0, 0, 0, 0));
    }

    private void updateNotConnectedView() {
        TransitionManager.beginDelayedTransition(rootView);
        layoutConnected.setVisibility(View.GONE);
    }

    private void initConnectedStateView(Server server) {
        Log.d("Connected");
        txtConnect.setVisibility(View.GONE);
        animationView.setAnimation(R.raw.shuttle);
        animationView.setMinAndMaxFrame(530, 640);
        animationView.setRepeatCount(0);
        animationView.playAnimation();
        animationView.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                animationView.setAnimation(R.raw.check);
                animationView.setMinFrame(0);
                animationView.setRepeatCount(0);
                animationView.playAnimation();
                animationView.removeAllAnimatorListeners();
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });

        TransitionManager.beginDelayedTransition(rootView);
        layoutConnected.setVisibility(View.VISIBLE);
        textServerName.setText(server.getServerName());
        textServerIp.setText(stateMonitor.getExitIP());
        textProtocol.setText(stateMonitor.getConnectionProtocol().toString());
        int load = (int) server.getLoad();
        textLoad.setText(textLoad.getContext().getString(R.string.serverLoad, String.valueOf(load)));
        imageLoad.setImageDrawable(
                new ColorDrawable(ContextCompat.getColor(imageLoad.getContext(), server.getLoadColor())));
    }

    public void updateView(boolean fromSavedState, @NonNull VpnStateMonitor.Status vpnState) {
        Profile profile = vpnState.getProfile();

        String serverName = "";
        Server connectedServer = null;
        if (profile != null) {
            serverName = (profile.isPreBakedProfile() || profile.getDisplayName(getContext()).isEmpty())
                    && stateMonitor.getConnectingToServer() != null ?
                    stateMonitor.getConnectingToServer().getDisplayName() : profile.getDisplayName(getContext());
            connectedServer = vpnState.getServer();
        }
        if (isAdded()) {
            VpnState state = vpnState.getState();
            //TODO: migrate to kotlin to use "when" here
            if (state instanceof VpnState.Error) {
                reportError(((VpnState.Error) vpnState.getState()).getType());
            } else if (VpnState.Disabled.INSTANCE.equals(state)) {
                checkDisconnectFromOutside();
                updateNotConnectedView();
            } else if (VpnState.CheckingAvailability.INSTANCE.equals(state)
                    || VpnState.ScanningPorts.INSTANCE.equals(state)) {
                initConnectingStateView(connectedServer, fromSavedState);
            } else if (VpnState.Connecting.INSTANCE.equals(state)) {
                initConnectingStateView(connectedServer, fromSavedState);
            } else if (VpnState.WaitingForNetwork.INSTANCE.equals(state)) {
                initConnectingStateView(connectedServer, fromSavedState);
            } else if (VpnState.Connected.INSTANCE.equals(state)) {
                initConnectedStateView(connectedServer);
            } else if (VpnState.Disconnecting.INSTANCE.equals(state)) {
                clearConnectedStatus();
            } else {
                updateNotConnectedView();
            }
        }
    }

    private void checkDisconnectFromOutside() {
        if (stateMonitor.isConnected()) {
            EventBus.getInstance().post(new ConnectedToServer(null));
        }
    }

    private boolean reportError(ErrorType error) {
        Log.e("report error: " + error.toString());
//        animationView.reverseAnimationSpeed();
//        animationView.playAnimation();
        switch (error) {
            case AUTH_FAILED:
                showAuthError(R.string.error_auth_failed);
                break;
            case PEER_AUTH_FAILED:
                showErrorDialog(R.string.error_peer_auth_failed);
                Log.exception(new VPNException("Peer Auth: Verifying gateway authentication failed"));
                break;
            case LOOKUP_FAILED:
                showErrorDialog(R.string.error_lookup_failed);
                Log.exception(new VPNException("Gateway address lookup failed"));
                break;
            case UNREACHABLE:
                showErrorDialog(R.string.error_unreachable);
                Log.exception(new VPNException("Gateway is unreachable"));
                break;
            case MAX_SESSIONS:
                showAuthError(R.string.errorMaxSessions);
                Log.exception(new VPNException("Maximum number of sessions used"));
                break;
            case UNPAID:
                showAuthError(R.string.errorUserDelinquent);
                Log.exception(new VPNException("Overdue payment"));
            default:
                showErrorDialog(R.string.error_generic);
                Log.exception(new VPNException("Unspecified failure while connecting"));
                break;
        }

        return true;
    }

    private void showAuthError(@StringRes int stringRes) {
        new MaterialDialog.Builder(getActivity()).theme(Theme.DARK)
                .title(R.string.dialogTitleAttention)
                .content(stringRes)
                .cancelable(false)
                .negativeText(R.string.close)
                .onNegative((dialog, which) -> stateMonitor.disconnect())
                .show();
    }

    private void showErrorDialog(@StringRes int textId) {
        TransitionManager.beginDelayedTransition(rootView);
        layoutConnected.setVisibility(View.GONE);
        RetryInfo retryInfo = stateMonitor.getRetryInfo();
    }

    @Override
    public void switchToRetry(ApiResult.Error error) {

    }

    @Override
    public void switchToEmpty() {

    }

    @Override
    public void switchToLoading() {

    }

    @Override
    public void setRetryListener(NetworkFrameLayout.OnRequestRetryListener listener) {

    }

    @Override
    public NetworkFrameLayout.State getState() {
        return null;
    }
}

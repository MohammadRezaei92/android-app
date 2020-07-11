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
package com.protonvpn.android.ui.home;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.R;
import com.protonvpn.android.api.ProtonApiRetroFit;
import com.protonvpn.android.bus.ConnectToProfile;
import com.protonvpn.android.bus.ConnectToServer;
import com.protonvpn.android.bus.ConnectedToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.ForcedLogout;
import com.protonvpn.android.bus.VpnStateChanged;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.LoaderUI;
import com.protonvpn.android.components.SecureCoreCallback;
import com.protonvpn.android.migration.NewAppMigrator;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.ui.drawer.AccountActivity;
import com.protonvpn.android.ui.drawer.ReportBugActivity;
import com.protonvpn.android.ui.drawer.SettingsActivity;
import com.protonvpn.android.ui.home.vpnstate.NewVpnStateFragment;
import com.protonvpn.android.ui.login.LoginActivity;
import com.protonvpn.android.utils.AndroidUtils;
import com.protonvpn.android.utils.HtmlTools;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.vpn.LogActivity;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.squareup.otto.Subscribe;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.OnClick;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import kotlin.Unit;

@ContentLayout(R.layout.activity_home)
public class HomeActivity extends PoolingActivity implements SecureCoreCallback {

    @BindView(R.id.coordinator) CoordinatorLayout coordinator;
    @BindView(R.id.textUser) TextView textUser;
    @BindView(R.id.textTier) TextView textTier;
    @BindView(R.id.textVersion) TextView textVersion;
    boolean doubleBackToExitPressedOnce = false;
    NewVpnStateFragment fragment;

    @Inject ProtonApiRetroFit api;
    @Inject ServerManager serverManager;
    @Inject UserData userData;
    @Inject VpnStateMonitor vpnStateMonitor;
    @Inject ServerListUpdater serverListUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerForEvents();
        super.onCreate(savedInstanceState);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(HtmlTools.fromHtml(getString(R.string.toolbar_app_title)));
        initDrawer();
        initDrawerView();
        fragment = (NewVpnStateFragment) getSupportFragmentManager().findFragmentById(R.id.vpnStatusFragment);

        Sentry.getContext().setUser(new UserBuilder().setUsername(userData.getUser()).build());
        checkForUpdate();
        if (serverManager.isDownloadedAtLeastOnce() || serverManager.isOutdated()) {
            initLayout();
        }

        serverManager.getUpdateEvent().observe(this, () -> {
            if (serverManager.isDownloadedAtLeastOnce()) {
                EventBus.post(new VpnStateChanged(userData.isSecureCoreEnabled()));
            }
            else {
                initLayout();
            }
            return Unit.INSTANCE;
        });

        serverListUpdater.onHomeActivityCreated(this);

        if (Storage.getBoolean(NewAppMigrator.PREFS_MIGRATED_FROM_OLD)) {
            if (AndroidUtils.INSTANCE.isPackageInstalled(this, NewAppMigrator.OLD_APP_ID)) {
                showMigrationDialog();
            }
            Storage.saveBoolean(NewAppMigrator.PREFS_MIGRATED_FROM_OLD, false);
        }
    }

    private void showMigrationDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setMessage(R.string.successful_migration_message);
        Intent oldAppIntent = AndroidUtils.INSTANCE.playMarketIntentFor(NewAppMigrator.OLD_APP_ID);
        dialog.setPositiveButton(R.string.successful_migration_uninstall,
            (dialogInterface, button) -> startActivity(oldAppIntent));
        dialog.setNegativeButton(R.string.ok, null);
        dialog.create().show();
    }

    private void checkForUpdate() {
        int versionCode = Storage.getInt("VERSION_CODE");
        Storage.saveInt("VERSION_CODE", BuildConfig.VERSION_CODE);
    }

    @SuppressLint("CheckResult")
    private void initLayout() {

    }

    private void initDrawerView() {
        textTier.setText(userData.getVpnInfoResponse().getUserTierName());
        textUser.setText(userData.getUser());
        textVersion.setText(getString(R.string.drawerAppVersion, BuildConfig.VERSION_NAME));
    }

    @OnClick(R.id.layoutUserInfo)
    public void onUserInfoClick() {
        navigateTo(AccountActivity.class);
        closeDrawer();
    }

    @OnClick(R.id.drawerButtonSettings)
    public void drawerButtonSettings() {
        closeDrawer();
        navigateTo(SettingsActivity.class);
    }

    @OnClick(R.id.drawerButtonShowLog)
    public void drawerButtonShowLog() {
        navigateTo(LogActivity.class);
    }

    @OnClick(R.id.drawerButtonReportBug)
    public void drawerButtonReportBug() {
        closeDrawer();
        navigateTo(ReportBugActivity.class);
    }

    @OnClick(R.id.drawerButtonAccount)
    public void drawerButtonAccount() {
        navigateTo(AccountActivity.class);
        closeDrawer();
    }

    @OnClick(R.id.drawerButtonHelp)
    public void drawerButtonHelp() {
        openUrl("https://protonvpn.com/support");
    }

    @OnClick(R.id.drawerButtonLogout)
    public void drawerButtonLogout() {
        if (vpnStateMonitor.isConnected()) {
            new MaterialDialog.Builder(this).theme(Theme.DARK)
                .title(R.string.warning)
                .content(R.string.logoutDescription)
                .positiveText(R.string.ok)
                .onPositive((dialog, which) -> logout())
                .negativeText(R.string.cancel)
                .show();
        }
        else {
            logout();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent.getBooleanExtra("OpenStatus", false)) {
//            fragment.openBottomSheet();
        }
        super.onNewIntent(intent);
    }

    @Subscribe
    public void onForcedLogout(ForcedLogout event) {
        logout();
    }

    @Subscribe
    public void onConnectToServer(ConnectToServer server) {
        if (server.getServer() == null) {
            vpnStateMonitor.disconnect();
        }
        else {
            onConnect(Profile.Companion.getTempProfile(server.getServer(), serverManager));
        }
    }

    @Subscribe
    public void onConnectToProfile(@NotNull ConnectToProfile profile) {
        onConnect(profile.getProfile());
    }

    public void logout() {
        userData.logout();
        serverManager.clearCache();
        api.logout(result ->
            Log.d(result.isSuccess() ? "Logout successful" : "Logout api call failed"));
        vpnStateMonitor.disconnect();
        finish();
        navigateTo(LoginActivity.class);
    }



    // FIXME: API needs to inform app of changes, not other way
    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public LoaderUI getNetworkFrameLayout() {
        if (serverManager.isDownloadedAtLeastOnce()) {
            return fragment;
        }
        else {
            return getLoadingContainer();
        }
    }

    @Subscribe
    public void onConnectedToServer(@NonNull ConnectedToServer server) {
        if (server.getServer() != null) {
            userData.setSecureCoreEnabled(server.getServer().isSecureCoreServer());
        }
        EventBus.post(new VpnStateChanged(userData.isSecureCoreEnabled()));
    }


    private boolean shouldCloseDrawer() {
        if (getDrawer().isDrawerOpen(GravityCompat.START)) {
            getDrawer().closeDrawer(GravityCompat.START, true);
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (!shouldCloseDrawer()) {
            if (doubleBackToExitPressedOnce) {
                this.moveTaskToBack(true);
                return;
            }

            this.doubleBackToExitPressedOnce = true;
            Toast.makeText(getContext(), R.string.clickBackAgainLogout, Toast.LENGTH_LONG).show();

            new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
        }
    }

    @Override
    public void onConnect(@NotNull Profile profile) {
        boolean secureCoreServer = profile.getServer() != null && profile.getServer().isSecureCoreServer();
        boolean secureCoreOn = userData.isSecureCoreEnabled();
        if ((secureCoreServer && !secureCoreOn) || (!secureCoreServer && secureCoreOn)) {
            showSecureCoreChangeDialog(profile);
        }
        else {
            super.onConnect(profile);
        }
    }

    private void showSecureCoreChangeDialog(Profile profileToConnect) {
        String disconnect =
            vpnStateMonitor.isConnected() ? getString(R.string.currentConnectionWillBeLost) : ".";
        boolean isSecureCoreServer = profileToConnect.getServer().isSecureCoreServer();
        new MaterialDialog.Builder(this).title(R.string.warning)
            .theme(Theme.DARK)
            .content(HtmlTools.fromHtml(
                getString(isSecureCoreServer ? R.string.secureCoreSwitchOn : R.string.secureCoreSwitchOff,
                    disconnect)))
            .cancelable(false)
            .positiveText(R.string.yes)
            .negativeText(R.string.no)
            .negativeColor(ContextCompat.getColor(this, R.color.white))
            .onPositive((dialog, which) -> super.onConnect(profileToConnect))
            .show();
    }
}
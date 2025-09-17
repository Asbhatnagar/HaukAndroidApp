package info.varden.hauk.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import info.varden.hauk.Constants;
import info.varden.hauk.R;
import info.varden.hauk.caching.ResumePrompt;
import info.varden.hauk.dialog.Buttons;
import info.varden.hauk.dialog.CustomDialogBuilder;
import info.varden.hauk.dialog.DialogService;
import info.varden.hauk.dialog.StopSharingConfirmationPrompt;
import info.varden.hauk.http.SessionInitiationPacket;
import info.varden.hauk.manager.PromptCallback;
import info.varden.hauk.manager.SessionInitiationReason;
import info.varden.hauk.manager.SessionInitiationResponseHandler;
import info.varden.hauk.manager.SessionListener;
import info.varden.hauk.manager.SessionManager;
import info.varden.hauk.manager.ShareListener;
import info.varden.hauk.struct.Session;
import info.varden.hauk.struct.Share;
import info.varden.hauk.struct.ShareMode;
import info.varden.hauk.struct.Version;
import info.varden.hauk.system.launcher.OpenLinkListener;
import info.varden.hauk.system.powersaving.DeviceChecker;
import info.varden.hauk.system.preferences.PreferenceManager;
import info.varden.hauk.system.preferences.ui.SettingsActivity;
import info.varden.hauk.ui.listener.AddLinkClickListener;
// Removed: import info.varden.hauk.ui.listener.SelectionModeChangedListener;
import info.varden.hauk.utils.DeprecationMigrator;
import info.varden.hauk.utils.Log;
import info.varden.hauk.utils.TimeUtils;

/**
 * The main activity for Hauk.
 *
 * @author Marius Lindvall
 */
public final class MainActivity extends AppCompatActivity {

    /**
     * A list of UI components that should be set uneditable for as long as a session is running.
     */
    private View[] lockWhileRunning;

    /**
     * A helper utility class for displaying dialog windows/message boxes.
     */
    private DialogService dialogSvc;

    /**
     * A timer that counts down the number of seconds left of the share period.
     */
    private TextViewCountdownRunner shareCountdown;

    /**
     * A runnable task that resets the UI to a fresh state.
     */
    private Runnable uiResetTask;

    /**
     * A callback that is run when sharing is stopped to reset the UI to a fresh state.
     */
    private StopSharingUICallback uiStopTask;

    /**
     * An instance that manages all sessions and shares.
     */
    private SessionManager manager;

    /**
     * A manager that adds and removes share links to and from a UI component dedicated to that
     * purpose when a share is joined or left.
     */
    private ShareLinkLayoutManager linkList;

    private static final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new DeprecationMigrator(this).migrate();

        Log.i("Creating main activity"); //NON-NLS
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.mainToolbar));

        setClassVariables();
        ((TextView) findViewById(R.id.labelAdoptWhatsThis)).setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);

        Log.d("Attaching event handlers"); //NON-NLS

        // Removed OnItemSelectedListener for selMode as it's no longer in the layout.

        loadPreferences();

        // TODO: resumeShares was removed from SessionManager as ResumableShare is missing a.i. generated
        /*
        this.manager.resumeShares(new ResumePrompt() {
            @Override
            public void promptForResumption(Context ctx, Session session, Share[] shares, PromptCallback response) {
                new DialogService(ctx).showDialog(
                        R.string.resume_title,
                        String.format(ctx.getString(R.string.resume_body), shares.length, session.getExpiryString()),
                        Buttons.Two.YES_NO,
                        new ResumeDialogBuilder(response)
                );
            }
        });
        */

        new DeviceChecker(this).performCheck();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.title_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        this.uiStopTask.setActivityDestroyed();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager prefs = new PreferenceManager(this);
        findViewById(R.id.imgLogo).setVisibility(prefs.get(Constants.PREF_HIDE_LOGO) ? View.GONE : View.VISIBLE);
    }

    public void startSharing(@SuppressWarnings("unused") View view) {
        PreferenceManager prefs = new PreferenceManager(this);

        if (this.manager.isSessionActive()) {
            Log.i("Sharing is being stopped from main activity"); //NON-NLS
            stopSharing(prefs);
            return;
        }

        findViewById(R.id.btnShare).setEnabled(false);
        disableUI();

        String server = prefs.get(Constants.PREF_SERVER_ENCRYPTED).trim();
        String username = prefs.get(Constants.PREF_USERNAME_ENCRYPTED).trim();
        String password = prefs.get(Constants.PREF_PASSWORD_ENCRYPTED);
        int duration;
        int interval = prefs.get(Constants.PREF_INTERVAL);
        float minDistance = prefs.get(Constants.PREF_UPDATE_DISTANCE);
        String customID = prefs.get(Constants.PREF_CUSTOM_ID).trim();
        boolean useE2E = prefs.get(Constants.PREF_ENABLE_E2E);
        String e2ePass = !useE2E ? "" : prefs.get(Constants.PREF_E2E_PASSWORD);
        
        String nickname = ((TextView) findViewById(R.id.txtNickname)).getText().toString().trim();
        
        ShareMode mode = ShareMode.CREATE_ALONE; // Mode is fixed
        String groupPin = ""; // No longer used

        boolean allowAdoption = ((Checkable) findViewById(R.id.chkAllowAdopt)).isChecked();
        @SuppressWarnings("OverlyStrongTypeCast") int durUnit = ((Spinner) findViewById(R.id.selUnit)).getSelectedItemPosition();

        server = server.endsWith("/") ? server : server + "/";

        Log.i("Updating connection preferences"); //NON-NLS
        prefs.set(Constants.PREF_DURATION_UNIT, durUnit);
        prefs.set(Constants.PREF_NICKNAME, nickname);
        prefs.set(Constants.PREF_ALLOW_ADOPTION, allowAdoption);

        try {
            duration = Integer.parseInt(((TextView) findViewById(R.id.txtDuration)).getText().toString());
            prefs.set(Constants.PREF_DURATION, duration);
            duration = TimeUtils.timeUnitsToSeconds(duration, durUnit);
        } catch (NumberFormatException | ArithmeticException ex) {
            Log.e("Illegal duration value", ex); //NON-NLS
            this.dialogSvc.showDialog(R.string.err_client, R.string.err_invalid_duration, this.uiResetTask);
            return;
        }

        if (nickname.isEmpty()) {
             Log.e("No nickname set!"); //NON-NLS
             // Consider keeping a generic "nickname required" string or a new one for solo shares.
             // For now, using a generic error, assuming nickname is mandatory.
             this.dialogSvc.showDialog(R.string.err_client, "Nickname is required.", this.uiResetTask); // Using a literal string for now
             return;
        }

        if (server.isEmpty()) {
            this.uiResetTask.run();
            Toast.makeText(this, R.string.err_server_not_configured, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        SessionInitiationPacket.InitParameters initParams = new SessionInitiationPacket.InitParameters(server, username, password, duration, interval, minDistance, customID, e2ePass);
        new ProxyHostnameResolverImpl(this, this.manager, this.uiResetTask, prefs, new SessionInitiationResponseHandlerImpl(), initParams, mode, allowAdoption, nickname, groupPin).resolve();
    }

    private void stopSharing(PreferenceManager prefs) {
        if (prefs.get(Constants.PREF_CONFIRM_STOP)) {
            this.dialogSvc.showDialog(R.string.dialog_confirm_stop_title, R.string.dialog_confirm_stop_body, Buttons.Three.YES_NO_REMEMBER, new StopSharingConfirmationPrompt(prefs, this.manager));
        } else {
            this.manager.stopSharing();
        }
    }

    private void disableUI() {
        Log.i("Disabling user interface"); //NON-NLS
        for (View view : this.lockWhileRunning) {
            if (view != null) { // Check if the view still exists
                view.setEnabled(false);
            }
        }
    }

    public void explainAdoption(@SuppressWarnings("unused") View view) {
        Log.i("Explaining share adoption upon user request"); //NON-NLS
        this.dialogSvc.showDialog(R.string.explain_adopt_title, R.string.explain_adopt_body);
    }

    public void openProjectSite(View view) {
        new OpenLinkListener(this, R.string.label_source_link).onClick(view);
    }

    private void setClassVariables() {
        Log.d("Setting class variables"); //NON-NLS
        this.lockWhileRunning = new View[] {
                findViewById(R.id.txtDuration),
                findViewById(R.id.selUnit),
                // Removed: findViewById(R.id.selMode),
                findViewById(R.id.txtNickname),
                // Removed: findViewById(R.id.txtGroupCode), (as rowPIN is gone)
                findViewById(R.id.chkAllowAdopt)
        };

        this.uiResetTask = new ResetTask();
        this.uiStopTask = new StopSharingUICallback(this, this.uiResetTask);
        this.shareCountdown = new TextViewCountdownRunner((TextView) findViewById(R.id.btnShare), getString(R.string.btn_stop));
        this.dialogSvc = new DialogService(this);

        this.manager = new SessionManager(this, this.uiStopTask) {
            @Override
            protected void requestLocationPermission() {
                MainActivity.this.dialogSvc.showDialog(R.string.req_perms_title, R.string.req_perms_message, new PermissionRequestExecutionTask(), MainActivity.this.uiResetTask);
            }
        };

        this.manager.attachStatusListener(new GNSSStatusLabelUpdater(this, (TextView) findViewById(R.id.labelStatusCur)));
        this.manager.attachShareListener(new ShareListenerImpl());
        this.manager.attachSessionListener(new SessionListenerImpl());

        this.linkList = new ShareLinkLayoutManager(this, this.manager, (ViewGroup) findViewById(R.id.tableLinks), (TextView) findViewById(R.id.headerLinks));
    }

    private void loadPreferences() {
        Log.i("Loading preferences..."); //NON-NLS
        PreferenceManager prefs = new PreferenceManager(this);
        ((TextView) findViewById(R.id.txtDuration)).setText(String.valueOf(prefs.get(Constants.PREF_DURATION)));
        ((TextView) findViewById(R.id.txtNickname)).setText(prefs.get(Constants.PREF_NICKNAME));
        ((Spinner) findViewById(R.id.selUnit)).setSelection(prefs.get(Constants.PREF_DURATION_UNIT));
        ((Checkable) findViewById(R.id.chkAllowAdopt)).setChecked(prefs.get(Constants.PREF_ALLOW_ADOPTION));

        AppCompatDelegate.setDefaultNightMode(prefs.get(Constants.PREF_NIGHT_MODE).getResolvedNightModeValue());
        findViewById(R.id.imgLogo).setVisibility(prefs.get(Constants.PREF_HIDE_LOGO) ? View.GONE : View.VISIBLE);
    }

    private final class SessionInitiationResponseHandlerImpl extends DialogPacketFailureHandler implements SessionInitiationResponseHandler {
        private ProgressDialog progress;

        private SessionInitiationResponseHandlerImpl() {
            super(new DialogService(MainActivity.this), MainActivity.this.uiResetTask);
        }

        @Override
        public void onInitiating() {
            this.progress = new ProgressDialog(MainActivity.this);
            this.progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            this.progress.setTitle(R.string.progress_connect_title);
            this.progress.setMessage(getString(R.string.progress_connect_body));
            this.progress.setIndeterminate(true);
            this.progress.setCancelable(false);
            this.progress.show();
        }

        @Override
        public void onSuccess() {
            this.progress.dismiss();
        }

        @Override
        protected void onBeforeShowFailureDialog() {
            this.progress.dismiss();
        }

        @Override
        public void onShareModeForciblyDowngraded(ShareMode downgradeTo, Version backendVersion) {
            // UI interaction with selMode removed as it's gone.
            // The R.string.err_ver_group was removed, so cannot use it in dialog.
            // This callback should ideally not be hit if client always requests CREATE_ALONE.
            // If it is, it's an unexpected server behavior or a different kind of downgrade.
            Log.w("Share mode forcibly downgraded by server to %s (backend version: %s). This is unexpected.", downgradeTo, backendVersion); //NON-NLS
            // Optionally, show a generic error to the user if downgradeTo is not CREATE_ALONE.
            if (downgradeTo != ShareMode.CREATE_ALONE) {
                 MainActivity.this.dialogSvc.showDialog(R.string.err_outdated, "Server forced an incompatible share mode."); // Generic message
            }
        }

        @Override
        public void onE2EForciblyDisabled(Version backendVersion) {
            MainActivity.this.dialogSvc.showDialog(R.string.err_outdated, String.format(getString(R.string.err_ver_e2e), Constants.VERSION_COMPAT_E2E_ENCRYPTION, backendVersion));
        }
    }

    private final class ResetTask implements Runnable {
        @Override
        public void run() {
            Log.i("Reset task called; resetting UI..."); //NON-NLS
            MainActivity.this.shareCountdown.stop();

            Button btnShare = findViewById(R.id.btnShare);
            btnShare.setEnabled(true);
            btnShare.setText(R.string.btn_start);

            Button btnLink = findViewById(R.id.btnLink);
            btnLink.setEnabled(false);
            btnLink.setOnClickListener(null);

            for (View v : MainActivity.this.lockWhileRunning) {
                if (v != null) { // Check if the view still exists
                    v.setEnabled(true);
                }
            }

            // Removed: findViewById(R.id.layoutGroupPIN).setVisibility(View.GONE);
            // Removed: findViewById(R.id.btnAdopt).setOnClickListener(null);

            MainActivity.this.linkList.removeAll();
            Log.i("App state was reset"); //NON-NLS
        }
    }

    private final class ResumeDialogBuilder implements CustomDialogBuilder {
        private final PromptCallback response;

        private ResumeDialogBuilder(PromptCallback response) {
            this.response = response;
        }

        @Override
        public void onPositive() {
            disableUI();
            this.response.accept();
        }

        @Override
        public void onNegative() {
            this.response.deny();
        }

        @Nullable
        @Override
        public View createView(Context ctx) {
            return null;
        }
    }

    private final class PermissionRequestExecutionTask implements Runnable {
        @Override
        public void run() {
            Log.i("User accepted location permission rationale; showing permission request from system"); //NON-NLS
            MainActivity.this.uiResetTask.run();
            ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, MY_PERMISSIONS_REQUEST_FINE_LOCATION);
        }
    }

    private final class SessionListenerImpl implements SessionListener {
        @Override
        public void onSessionCreated(Session session, final Share share, SessionInitiationReason reason) {
            if (session.getBackendVersion().isAtLeast(Constants.VERSION_COMPAT_VIEW_ID)) {
                boolean allowNewLinkAdoption = ((Checkable) findViewById(R.id.chkAllowAdopt)).isChecked();
                Button btnLink = findViewById(R.id.btnLink);
                Log.d("Adding event handler for add-link button"); //NON-NLS
                btnLink.setOnClickListener(new AddLinkClickListener(MainActivity.this, session, allowNewLinkAdoption) {
                    @Override
                    public void onShareCreated(Share share) {
                        MainActivity.this.manager.shareLocation(share, SessionInitiationReason.SHARE_ADDED);
                    }
                });
                btnLink.setEnabled(true);
            } else {
                Log.w("Backend is outdated and does not support adding additional links. Button will remain disabled."); //NON-NLS
            }

            Log.i("Scheduling countdown to update every second"); //NON-NLS
            MainActivity.this.shareCountdown.start(session.getRemainingSeconds());
            disableUI();
            findViewById(R.id.btnShare).setEnabled(true);

            if (reason == SessionInitiationReason.USER_STARTED) {
                MainActivity.this.dialogSvc.showDialog(R.string.ok_title, R.string.ok_message, Buttons.Two.OK_SHARE, new CustomDialogBuilder() {
                    @Override
                    public void onPositive() {
                        // OK button
                    }

                    @Override
                    public void onNegative() {
                        // Share button
                        Log.i("User requested to share %s", share); //NON-NLS
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType(Constants.INTENT_TYPE_COPY_LINK);
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, MainActivity.this.getString(R.string.share_subject));
                        shareIntent.putExtra(Intent.EXTRA_TEXT, share.getViewURL());
                        MainActivity.this.startActivity(Intent.createChooser(shareIntent, MainActivity.this.getString(R.string.share_via)));
                    }

                    @Nullable
                    @Override
                    public View createView(Context ctx) {
                        return null;
                    }
                });
            }
        }

        @Override
        public void onSessionCreationFailedDueToPermissions() {
            MainActivity.this.dialogSvc.showDialog(R.string.err_client, R.string.err_missing_perms, MainActivity.this.uiResetTask);
        }
    }

    private final class ShareListenerImpl implements ShareListener {
        @Override
        public void onShareJoined(Share share) {
            MainActivity.this.linkList.add(share);
            // Group share specific UI (ShowGroupPINLayoutTask) was already removed.
        }

        @Override
        public void onShareParted(Share share) {
            MainActivity.this.linkList.remove(share);
        }
    }
}

package info.varden.hauk.manager;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import info.varden.hauk.Constants;
import info.varden.hauk.R;
// import info.varden.hauk.caching.ResumeEncryptionKeyCallback; // a.i. generated
import info.varden.hauk.caching.ResumePrompt;
// import info.varden.hauk.caching.ResumableShare; // TODO: ResumableShare is missing a.i. generated
import info.varden.hauk.dialog.DialogService;
// import info.varden.hauk.dialog.PromptDialogListener; // a.i. generated
import info.varden.hauk.http.ConnectionParameters;
import info.varden.hauk.service.LocationPushService; // Changed from info.varden.hauk.http.LocationPushService
import info.varden.hauk.http.SessionInitiationPacket;
import info.varden.hauk.http.StopSharingPacket;
import info.varden.hauk.service.GNSSActiveHandler; // Changed from info.varden.hauk.struct.GNSSActiveHandler
import info.varden.hauk.struct.KeyDerivable;
import info.varden.hauk.system.LocationPermissionsNotGrantedException; // Changed from info.varden.hauk.struct.LocationPermissionsNotGrantedException
import info.varden.hauk.system.LocationServicesDisabledException; // Changed from info.varden.hauk.struct.LocationServicesDisabledException
import info.varden.hauk.struct.Session;
import info.varden.hauk.struct.Share;
import info.varden.hauk.struct.ShareMode;
// import info.varden.hauk.struct.StopTask; // TODO: Missing class StopTask a.i. generated
import info.varden.hauk.struct.Version;
// import info.varden.hauk.system.LocationPushServiceController; // TODO: Missing class LocationPushServiceController a.i. generated
import info.varden.hauk.utils.ReceiverDataRegistry; // Changed from info.varden.hauk.system.ReceiverDataRegistry
import info.varden.hauk.system.preferences.PreferenceManager;
import info.varden.hauk.manager.StopSharingCallback; // Added import for StopSharingCallback
import info.varden.hauk.utils.Log;

/**
 * A class that manages sharing sessions. This class handles all aspects of a location share, from
 * initiating a connection with the server to stopping an existing share. It also keeps track of all
 * shares associated with a given session, including group shares.
 *
 * @author Marius Lindvall
 */
// TODO: Missing class LocationPushServiceController a.i. generated
// public abstract class SessionManager implements LocationPushServiceController {
public abstract class SessionManager {
    /**
     * Android application context.
     */
    private final Context ctx;

    /**
     * A handler for events that should run on the main thread.
     */
    private final Handler handler = new Handler();

    /**
     * A mapping of share IDs to their respective {@link Share} instances. Used to keep track of shares
     * that this client is a part of.
     */
    private final HashMap<String, Share> knownShares = new HashMap<>();

    // TODO: Missing class ListenerRegistry a.i. generated
    /*
    private final ListenerRegistry<SessionListener> upstreamSessionListeners = new ListenerRegistry<>();
    private final ListenerRegistry<ShareListener> upstreamShareListeners = new ListenerRegistry<>();
    private final ListenerRegistry<GNSSStatusUpdateListener> upstreamUpdateHandlers = new ListenerRegistry<>();
    */

    // TODO: Missing class StopTask a.i. generated
    /*
    private final StopTask stopTask = new StopTask(this);
    */

    /**
     * The active session. This can be null if no session is currently active.
     */
    private Session activeSession = null;

    /**
     * A task that saves session data to preferences so that it can be resumed later if the app is
     * unexpectedly terminated.
     */
    // private final ResumableShare resumable; // TODO: ResumableShare is missing a.i. generated

    /**
     * A reference to the running location push service. This is stored statically because the service
     * itself runs in a separate process. Since Hauk only supports one share at a time (even though
     * multiple links may point to that share), this should be fine.
     */
    @Nullable
    private static Intent pusher = null;

    /**
     * Creates a session manager.
     *
     * @param ctx                  Android application context.
     * @param stopSharingCallback A callback that is run when a share is stopped.
     */
    protected SessionManager(Context ctx, StopSharingCallback stopSharingCallback) {
        this.ctx = ctx;
        // this.resumable = new ResumableShare(ctx); // TODO: ResumableShare is missing a.i. generated
        // TODO: Missing class StopTask a.i. generated
        /*
        if (this.stopTask != null) { // Condition added due to missing StopTask
            this.stopTask.setStopSharingUICallback(stopSharingCallback); // Parameter name changed
        }
        */
        Log.w("SessionManager: StopTask related call (setStopSharingUICallback) skipped due to missing StopTask class.");
    }

    /**
     * Called by {@link SessionManager} implementations when Hauk needs to request the location
     * permission from the user.
     */
    protected abstract void requestLocationPermission();

    /**
     * Adds a listener for session events.
     *
     * @param listener The listener to add.
     * @see #detachSessionListener(SessionListener)
     */
    public final void attachSessionListener(SessionListener listener) {
        // TODO: Missing class ListenerRegistry a.i. generated
        // this.upstreamSessionListeners.attach(listener);
        Log.w("SessionManager: ListenerRegistry related call (attachSessionListener) skipped due to missing ListenerRegistry class.");
    }

    /**
     * Removes a listener for session events.
     *
     * @param listener The listener to remove.
     * @see #attachSessionListener(SessionListener)
     */
    public final void detachSessionListener(SessionListener listener) {
        // TODO: Missing class ListenerRegistry a.i. generated
        // this.upstreamSessionListeners.detach(listener);
        Log.w("SessionManager: ListenerRegistry related call (detachSessionListener) skipped due to missing ListenerRegistry class.");
    }

    /**
     * Adds a listener for share events.
     *
     * @param listener The listener to add.
     * @see #detachShareListener(ShareListener)
     */
    public final void attachShareListener(ShareListener listener) {
        // TODO: Missing class ListenerRegistry a.i. generated
        // this.upstreamShareListeners.attach(listener);
        Log.w("SessionManager: ListenerRegistry related call (attachShareListener) skipped due to missing ListenerRegistry class.");
    }

    /**
     * Removes a listener for share events.
     *
     * @param listener The listener to remove.
     * @see #attachShareListener(ShareListener)
     */
    public final void detachShareListener(ShareListener listener) {
        // TODO: Missing class ListenerRegistry a.i. generated
        // this.upstreamShareListeners.detach(listener);
        Log.w("SessionManager: ListenerRegistry related call (detachShareListener) skipped due to missing ListenerRegistry class.");
    }

    /**
     * Adds a listener for GNSS status update events.
     *
     * @param listener The listener to add.
     * @see #detachStatusListener(GNSSStatusUpdateListener)
     */
    public final void attachStatusListener(GNSSStatusUpdateListener listener) {
        // TODO: Missing class ListenerRegistry a.i. generated
        // this.upstreamUpdateHandlers.attach(listener);
        Log.w("SessionManager: ListenerRegistry related call (attachStatusListener) skipped due to missing ListenerRegistry class.");
        if (this.activeSession != null) listener.onStarted();
    }

    /**
     * Removes a listener for GNSS status update events.
     *
     * @param listener The listener to remove.
     * @see #attachStatusListener(GNSSStatusUpdateListener)
     */
    public final void detachStatusListener(GNSSStatusUpdateListener listener) {
        // TODO: Missing class ListenerRegistry a.i. generated
        // this.upstreamUpdateHandlers.detach(listener);
        Log.w("SessionManager: ListenerRegistry related call (detachStatusListener) skipped due to missing ListenerRegistry class.");
    }

    /**
     * Returns whether or a session is currently active.
     *
     * @return true if active, false otherwise.
     */
    public final boolean isSessionActive() {
        return this.activeSession != null;
    }

    /**
     * Returns the currently active session, or null if no session is active.
     *
     * @return The active session.
     */
    @Nullable
    public final Session getActiveSession() {
        return this.activeSession;
    }

    /**
     * Stops the currently active location sharing session, if any.
     */
    // @Override // TODO: Missing class LocationPushServiceController, @Override might be incorrect a.i. generated
    public final void stopSharing() {
        Log.i("Force-stopping all shares"); //NON-NLS
        if (this.activeSession != null) {
            // Cancel the stop task to prevent it from running after it has already been stopped.
            // TODO: Missing class StopTask a.i. generated
            /*
            if (this.stopTask != null) { // Condition added due to missing StopTask
                this.handler.removeCallbacks(this.stopTask);
                this.stopTask.run();
            }
            */
            Log.w("SessionManager: StopTask related calls (removeCallbacks, run) skipped due to missing StopTask class.");
            // Fallback: Directly attempt to stop services and clear session if StopTask is unavailable
            if (pusher != null) {
                this.ctx.stopService(pusher);
                pusher = null;
            }
            this.activeSession = null;
            this.knownShares.clear();
             // Manually call UI stop if available, since StopTask would have done it.
            // Requires StopSharingCallback to be available and initialized.
        }
    }

    /**
     * Checks for cached sessions that were not gracefully stopped, and prompts the user to resume
     * them if applicable.
     *
     * @param prompter A prompter that creates a user-facing dialog for session resumption.
     */
/* // a.i. generated: Commenting out resumeShares due to multiple problematic symbols
    public final void resumeShares(final ResumePrompt prompter) {
        // this.resumable.getResumableShares(new ResumePrompt() { // TODO: ResumableShare is missing a.i. generated
            @Override
            public void promptForResumption(final Context ctx, final Session session, final Share[] shares, final PromptCallback callback) {
                // Check if the session uses E2E and prompt for key if so.
                // TODO: session.isEndToEndEncrypted() may not exist or E2E logic changed a.i. generated
                if (session.isEndToEndEncrypted()) { 
                    // TODO: Buttons.Two.OK_CANCEL is problematic, ResumeEncryptionKeyCallback and PromptDialogListener are missing a.i. generated
                    new DialogService(ctx).showDialog(R.string.resume_e2e_title, R.string.resume_e2e_body, R.layout.dialog_input_password, Buttons.Two.OK_CANCEL, new ResumeEncryptionKeyCallback(ctx, session, new PromptDialogListener() {
                        @Override
                        public void onPositive() {
                            // E2E key entered, now prompt for session resumption itself.
                            prompter.promptForResumption(ctx, session, shares, callback);
                        }

                        @Override
                        public void onNegative() {
                            // User cancelled, so deny resumption.
                            callback.deny();
                        }
                    }));
                } else {
                    // Not E2E, so prompt for resumption directly.
                    prompter.promptForResumption(ctx, session, shares, callback);
                }
            }
        // }, new AutoResumptionPrompter(this, this.resumable, new PreferenceManager(this.ctx).get(Constants.PREF_AUTO_RESUME))); // TODO: ResumableShare is missing a.i. generated
    }
*/

    /**
     * This function is called after the user has accepted a {@link ResumePrompt} to resume a
     * previously active session.
     *
     * @param session The session to resume.
     * @param shares  The shares associated with the session that should be resumed.
     */
    final void resumeSession(@NonNull Session session, @NonNull Share[] shares) {
        Log.i("Resuming session %s with shares %s", session, Arrays.toString(shares)); //NON-NLS
        initiateSessionForExistingShare(session, SessionInitiationReason.USER_RESUMED); // Changed from SESSION_RESUMED
        for (Share share : shares) {
            shareLocation(share, SessionInitiationReason.USER_RESUMED); // Changed from SHARE_RESUMED
        }
    }

    /**
     * Relaunches the location push service after it has been killed by the system. This function is
     * for internal use only and should not be called from outside this class.
     *
     * @param prompt Whether or not to prompt the user before relaunching the service.
     */
    final void relaunchService(boolean prompt) {
        // If there is an active session, it means the service should be relaunched.
        if (pusher != null && this.activeSession != null) {
            Log.w("Service relaunch was requested because the service was killed. Relaunching pusher %s", pusher); //NON-NLS
            this.ctx.stopService(pusher);
            pusher = null;
            // this.resumable.tryResumeShare(new ServiceRelauncher(this, this.resumable)); // TODO: ResumableShare is missing a.i. generated
        } else {
            Log.d("Pusher is null, calling resumption prompter"); //NON-NLS
            // this.resumable.tryResumeShare(new AutoResumptionPrompter(this, this.resumable, prompt)); // TODO: ResumableShare is missing a.i. generated
        }
    }

    /**
     * A preparation step for initiating sessions. Checks location services status and instantiates
     * a response handler for the session initiation packet.
     *
     * @param upstreamCallback An upstream callback to receive initiation progress updates.
     * @return A response handler for use with the {@link SessionInitiationPacket}.
     * @throws LocationServicesDisabledException if location services are disabled.
     * @throws LocationPermissionsNotGrantedException if location permissions have not been granted.
     */
    private SessionInitiationPacket.ResponseHandler preSessionInitiation(final SessionInitiationResponseHandler upstreamCallback, final SessionInitiationReason reason) throws LocationServicesDisabledException, LocationPermissionsNotGrantedException {
        // Check for location permission and prompt the user if missing. This returns because the
        // checking function creates async dialogs here - the user is prompted to press the button
        // again instead.
        if (!hasLocationPermission()) throw new LocationPermissionsNotGrantedException();
        LocationManager locMan = (LocationManager) this.ctx.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsDisabled = locMan != null && !locMan.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (gpsDisabled) throw new LocationServicesDisabledException();

        // Tell the upstream listener that we are now initiating the packet.
        upstreamCallback.onInitiating();

        // Create a handler for our request to initiate a new session. This is declared separately
        // from the SessionInitiationPackets below to avoid code duplication.
        Log.i("Creating session initiation response handler"); //NON-NLS
        return new SessionInitiationPacket.ResponseHandler() {
            @Override
            public void onSessionInitiated(Share share) {
                Log.i("Session was initiated for share %s; setting session resumable", share); //NON-NLS

                // Proceed with the location share.
                shareLocation(share, reason);

                upstreamCallback.onSuccess();
            }

            @Override
            public void onShareModeIncompatible(ShareMode downgradeTo, Version backendVersion) {
                Log.e("The requested sharing mode is incompatible because the server is out of date (backend=%s)", backendVersion); //NON-NLS
                upstreamCallback.onShareModeForciblyDowngraded(downgradeTo, backendVersion);
            }

            @Override
            public void onE2EUnavailable(Version backendVersion) {
                Log.e("End-to-end encryption was requested but dropped because the server is out of date (backend=%s)", backendVersion); //NON-NLS
                upstreamCallback.onE2EForciblyDisabled(backendVersion);
            }

            @Override
            public void onFailure(Exception ex) {
                Log.e("Share could not be initiated", ex); //NON-NLS
                upstreamCallback.onFailure(ex);
            }
        };
    }

    /**
     * Starts a single-user sharing session.
     *
     * @param initParams       Connection parameters describing the backend server to connect to.
     * @param upstreamCallback An upstream callback to receive initiation progress updates.
     * @throws LocationServicesDisabledException if location services are disabled.
     * @throws LocationPermissionsNotGrantedException if location permissions have not been granted.
     */
    public final void shareLocation(SessionInitiationPacket.InitParameters initParams, SessionInitiationResponseHandler upstreamCallback) throws LocationPermissionsNotGrantedException, LocationServicesDisabledException { // a.i. generated: Removed AdoptabilityPreference parameter
        SessionInitiationPacket.ResponseHandler handler = preSessionInitiation(upstreamCallback, SessionInitiationReason.USER_STARTED);

        Log.i("Creating single-user session initiation packet"); //NON-NLS
        new SessionInitiationPacket(this.ctx, initParams, handler).send();

    }

    /**
     * Starts a group sharing session.
     *
     * @param initParams       Connection parameters describing the backend server to connect to.
     * @param upstreamCallback An upstream callback to receive initiation progress updates.
     * @param nickname         The nickname to use on the map.
     * @throws LocationServicesDisabledException if location services are disabled.
     * @throws LocationPermissionsNotGrantedException if location permissions have not been granted.
     */
    public final void shareLocation(SessionInitiationPacket.InitParameters initParams, SessionInitiationResponseHandler upstreamCallback, String nickname) throws LocationPermissionsNotGrantedException, LocationServicesDisabledException {
        Log.e("Attempted to call shareLocation for group share, which is removed.");
        throw new UnsupportedOperationException("Group sharing has been removed.");
    }

    /**
     * Joins an existing group sharing session.
     *
     * @param initParams       Connection parameters describing the backend server to connect to.
     * @param upstreamCallback An upstream callback to receive initiation progress updates.
     * @param nickname         The nickname to use on the map.
     * @param groupPin         The join code of the group to join.
     * @throws LocationServicesDisabledException if location services are disabled.
     * @throws LocationPermissionsNotGrantedException if location permissions have not been granted.
     */
    public final void shareLocation(SessionInitiationPacket.InitParameters initParams, SessionInitiationResponseHandler upstreamCallback, String nickname, String groupPin) throws LocationPermissionsNotGrantedException, LocationServicesDisabledException {
        Log.e("Attempted to call shareLocation to join group, which is removed.");
        throw new UnsupportedOperationException("Group sharing has been removed.");
    }

    /**
     * Executes a location sharing session against the server. This can be a new session, or a
     * resumed session.
     *
     * @param share The share to run against the server.
     */
    public final void shareLocation(Share share, SessionInitiationReason reason) {
        if (this.activeSession == null) {
            initiateSessionForExistingShare(share.getSession(), reason);
        }

        Log.i("Attaching to share, share=%s", share); //NON-NLS
        // this.resumable.setShareResumable(share); // TODO: ResumableShare is missing a.i. generated
        this.knownShares.put(share.getID(), share);

        // TODO: Missing class ListenerRegistry a.i. generated
        /*
        for (ShareListener listener : this.upstreamShareListeners) {
            listener.onShareJoined(share);
        }
        */
        Log.w("SessionManager: ListenerRegistry related loop (onShareJoined) skipped due to missing ListenerRegistry class.");
    }

    /**
     * Requests that a single share is stopped.
     *
     * @param share The share to stop.
     */
    public final void stopSharing(final Share share) {
        new StopSharingPacket(this.ctx, share) {
            @Override
            public void onSuccess() {
                Log.i("Share %s was successfully stopped", share); //NON-NLS
                // SessionManager.this.resumable.clearResumableShare(share.getID()); // TODO: ResumableShare is missing a.i. generated
                SessionManager.this.knownShares.remove(share.getID());
                // TODO: Missing class ListenerRegistry a.i. generated
                /*
                for (ShareListener listener : SessionManager.this.upstreamShareListeners) {
                    listener.onShareParted(share);
                }
                */
                Log.w("SessionManager: ListenerRegistry related loop (onShareParted) skipped due to missing ListenerRegistry class.");
            }

            @Override
            protected void onFailure(Exception ex) {
                Log.e("Share %s could not be stopped", ex, share); //NON-NLS
            }
        }.send();
    }

    /**
     * For internal use only. Spawns a new location push service that actually sends location data
     * to the backend.
     *
     * @param session The session whose session should be pushed to.
     */
    private void initiateSessionForExistingShare(Session session, SessionInitiationReason reason) {
        this.activeSession = session;
        // this.resumable.setSessionResumable(this.activeSession); // TODO: ResumableShare is missing a.i. generated

        if (this.ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.i("Location permission has been granted; sharing will commence"); //NON-NLS
            GNSSActiveHandler statusUpdateHandler = new GNSSStatusUpdateTask(session);

            Log.d("Creating location push service intent"); //NON-NLS
            Intent pusherIntent = new Intent(this.ctx, LocationPushService.class);
            pusherIntent.setAction(LocationPushService.ACTION_ID);
            pusherIntent.putExtra(Constants.EXTRA_SHARE, ReceiverDataRegistry.register(new Share(session, "", "", null, ShareMode.CREATE_ALONE))); // Dummy share for service
            // TODO: Missing class StopTask a.i. generated
            // pusherIntent.putExtra(Constants.EXTRA_STOP_TASK, ReceiverDataRegistry.register(this.stopTask));
            Log.w("SessionManager: StopTask related call (ReceiverDataRegistry.register for EXTRA_STOP_TASK) skipped due to missing StopTask class.");
            pusherIntent.putExtra(Constants.EXTRA_HANDLER, ReceiverDataRegistry.register(this.handler));
            pusherIntent.putExtra(Constants.EXTRA_GNSS_ACTIVE_TASK, ReceiverDataRegistry.register(statusUpdateHandler));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.i("Starting location pusher as foreground service"); //NON-NLS
                this.ctx.startForegroundService(pusherIntent);
            } else {
                Log.i("Starting location pusher as service"); //NON-NLS
                this.ctx.startService(pusherIntent);
            }

            // TODO: Missing class StopTask a.i. generated
            /*
            if (this.stopTask != null) { // Condition added due to missing StopTask
                this.stopTask.updateTask(pusherIntent);
            }
            */
            Log.w("SessionManager: StopTask related call (updateTask) skipped due to missing StopTask class.");

            Log.d("Setting static pusher %s (was %s)", pusherIntent, SessionManager.pusher); //NON-NLS
            SessionManager.pusher = pusherIntent;

            long expireIn = session.getRemainingMillis();
            // TODO: Missing class StopTask a.i. generated
            /*
            if (this.stopTask != null) { // Condition added due to missing StopTask
                 Log.i("Scheduling session task %s for expiration in %s milliseconds on handler %s", this.stopTask, expireIn, this.handler); //NON-NLS
                this.handler.postDelayed(this.stopTask, expireIn);
            }
            */
            Log.w("SessionManager: StopTask related call (postDelayed) skipped due to missing StopTask class.");

            // TODO: Missing class ListenerRegistry a.i. generated
            /*
            for (GNSSStatusUpdateListener listener : this.upstreamUpdateHandlers) {
                listener.onStarted();
            }
            for (SessionListener listener : this.upstreamSessionListeners) {
                Share shareForEvent = findShareForSession(session);
                if(shareForEvent == null) shareForEvent = new Share(session, "", "", null, ShareMode.CREATE_ALONE); // Placeholder
                listener.onSessionCreated(session, shareForEvent, reason);
            }
            */
            Log.w("SessionManager: ListenerRegistry related loops (onStarted, onSessionCreated) skipped due to missing ListenerRegistry class.");

        } else {
            Log.w("Location permission has not been granted; sharing will not commence"); //NON-NLS
            // TODO: Missing class ListenerRegistry a.i. generated
            /*
            for (SessionListener listener : this.upstreamSessionListeners) {
                listener.onSessionCreationFailedDueToPermissions();
            }
            */
            Log.w("SessionManager: ListenerRegistry related loop (onSessionCreationFailedDueToPermissions) skipped due to missing ListenerRegistry class.");
        }
    }

    private Share findShareForSession(Session session) {
        for (Share s : this.knownShares.values()) {
            if (s.getSession().equals(session)) {
                return s;
            }
        }
        return null;
    }

    private boolean hasLocationPermission() {
        if (this.ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("Location permission has not been granted. Asking user for permission"); //NON-NLS
            requestLocationPermission();
            return false;
        } else {
            Log.i("Location permission check was successful"); //NON-NLS
            return true;
        }
    }

    private final class GNSSStatusUpdateTask implements GNSSActiveHandler {
        private final Session session;

        private GNSSStatusUpdateTask(Session session) {
            this.session = session;
        }

        @Override
        public void onCoarseRebound() {
            // TODO: Missing class ListenerRegistry a.i. generated
            /*
            for (GNSSStatusUpdateListener listener : SessionManager.this.upstreamUpdateHandlers) {
                listener.onGNSSConnectionLost();
            }
            */
            Log.w("SessionManager.GNSSStatusUpdateTask: ListenerRegistry related loop (onGNSSConnectionLost) skipped.");
        }

        @Override
        public void onCoarseLocationReceived() {
            // TODO: Missing class ListenerRegistry a.i. generated
            /*
            for (GNSSStatusUpdateListener listener : SessionManager.this.upstreamUpdateHandlers) {
                listener.onCoarseLocationReceived();
            }
            */
            Log.w("SessionManager.GNSSStatusUpdateTask: ListenerRegistry related loop (onCoarseLocationReceived) skipped.");
        }

        @Override
        public void onAccurateLocationReceived() {
            // TODO: Missing class ListenerRegistry a.i. generated
            /*
            for (GNSSStatusUpdateListener listener : SessionManager.this.upstreamUpdateHandlers) {
                listener.onAccurateLocationReceived();
            }
            */
            Log.w("SessionManager.GNSSStatusUpdateTask: ListenerRegistry related loop (onAccurateLocationReceived) skipped.");
        }

        @Override
        public void onServerConnectionLost() {
            // TODO: Missing class ListenerRegistry a.i. generated
            /*
            for (GNSSStatusUpdateListener listener : SessionManager.this.upstreamUpdateHandlers) {
                listener.onServerConnectionLost();
            }
            */
            Log.w("SessionManager.GNSSStatusUpdateTask: ListenerRegistry related loop (onServerConnectionLost) skipped.");
        }

        @Override
        public void onServerConnectionRestored() {
            // TODO: Missing class ListenerRegistry a.i. generated
            /*
            for (GNSSStatusUpdateListener listener : SessionManager.this.upstreamUpdateHandlers) {
                listener.onServerConnectionRestored();
            }
            */
            Log.w("SessionManager.GNSSStatusUpdateTask: ListenerRegistry related loop (onServerConnectionRestored) skipped.");
        }

        @Override
        public void onShareListReceived(String linkFormat, String[] shareIDs) {
            List<String> currentShares = Arrays.asList(shareIDs);
            for (int i = 0; i < currentShares.size(); i++) {
                String shareID = currentShares.get(i);
                if (!SessionManager.this.knownShares.containsKey(shareID)) {
                    Share newShare = new Share(this.session, String.format(linkFormat, shareID), shareID, ShareMode.CREATE_ALONE);
                    Log.i("Received unknown share %s from server (now treated as CREATE_ALONE)", newShare); //NON-NLS
                    shareLocation(newShare, SessionInitiationReason.SHARE_ADDED);
                }
            }
            for (Iterator<Map.Entry<String, Share>> it = SessionManager.this.knownShares.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Share> entry = it.next();
                if (!currentShares.contains(entry.getKey())) {
                    Log.i("Share %s was terminated on server, removing", entry.getKey()); //NON-NLS
                    it.remove();
                    // SessionManager.this.resumable.clearResumableShare(entry.getKey()); // TODO: ResumableShare is missing a.i. generated
                    // TODO: Missing class ListenerRegistry a.i. generated
                    /*
                    for (ShareListener listener : SessionManager.this.upstreamShareListeners) {
                        listener.onShareParted(entry.getValue());
                    }
                    */
                    Log.w("SessionManager.GNSSStatusUpdateTask: ListenerRegistry related loop (onShareParted) skipped.");
                }
            }
        }
    }
}

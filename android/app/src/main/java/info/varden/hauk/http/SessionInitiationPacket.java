package info.varden.hauk.http;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.security.SecureRandom;

import info.varden.hauk.Constants;
import info.varden.hauk.R;
// import info.varden.hauk.struct.AdoptabilityPreference; // a.i. generated
import info.varden.hauk.struct.KeyDerivable;
import info.varden.hauk.struct.Session;
import info.varden.hauk.struct.Share;
import info.varden.hauk.struct.ShareMode;
import info.varden.hauk.struct.Version;
import info.varden.hauk.utils.TimeUtils;

/**
 * Packet sent to initiate a sharing session on the server. Creates a share of a given type.
 *
 * @author Marius Lindvall
 */
public class SessionInitiationPacket extends Packet {
    private final InitParameters params;
    private final ResponseHandler handler;

    /**
     * The sharing mode for the initial share that the session is being created for.
     * Will always be CREATE_ALONE after modifications.
     */
    private ShareMode mode;

    /**
     * A salt used if the session is end-to-end encrypted.
     */
    private final byte[] salt;

    public SessionInitiationPacket(Context ctx, InitParameters params, ResponseHandler handler) { // Changed to public, will consolidate logic here
        super(ctx, params.getServerURL(), params.getConnectionParameters(), Constants.URL_PATH_CREATE_SHARE);
        this.params = params;
        this.handler = handler;
        if (params.getUsername() != null) {
            setParameter(Constants.PACKET_PARAM_USERNAME, params.getUsername());
        }
        if (params.getCustomID() != null) {
            setParameter(Constants.PACKET_PARAM_SHARE_ID, params.getCustomID());
        }
        if (params.getE2EPassword() != null) {
            SecureRandom rand = new SecureRandom();
            this.salt = new byte[Constants.E2E_AES_KEY_SIZE / 8];
            rand.nextBytes(this.salt);
            setParameter(Constants.PACKET_PARAM_SALT, Base64.encodeToString(this.salt, Base64.DEFAULT));
        } else {
            this.salt = null;
        }
        setParameter(Constants.PACKET_PARAM_PASSWORD, params.getPassword());
        setParameter(Constants.PACKET_PARAM_DURATION, String.valueOf(params.getDuration()));
        setParameter(Constants.PACKET_PARAM_INTERVAL, String.valueOf(params.getInterval()));
        setParameter(Constants.PACKET_PARAM_E2E_FLAG, params.getE2EPassword() != null ? "1" : "0");

        // Added lines from the old public constructor
        this.mode = ShareMode.CREATE_ALONE;
        setParameter(Constants.PACKET_PARAM_SHARE_MODE, String.valueOf(this.mode.getIndex()));
        setParameter(Constants.PACKET_PARAM_ADOPTABLE, "0"); // Defaulted to not adoptable
    }

    // REMOVED the conflicting public constructor that called this(ctx, params, handler)

    // REMOVED: Constructor for CREATE_GROUP
    // public SessionInitiationPacket(Context ctx, InitParameters params, ResponseHandler handler, String nickname) { ... }

    // REMOVED: Constructor for JOIN_GROUP
    // public SessionInitiationPacket(Context ctx, InitParameters params, ResponseHandler handler, String nickname, String groupPin) { ... }

    @Override
    protected final void onSuccess(String[] data, Version backendVersion) throws ServerException {
        // REMOVED: Check for group share compatibility, as mode is always CREATE_ALONE
        // if (this.mode.isGroupType()) { ... }

        KeyDerivable e2eParams = null;
        if (this.params.getE2EPassword() != null) {
            if (backendVersion.isAtLeast(Constants.VERSION_COMPAT_E2E_ENCRYPTION)) {
                e2eParams = new KeyDerivable(this.params.getE2EPassword(), this.salt);
            } else {
                this.handler.onE2EUnavailable(backendVersion);
            }
        }

        if (data.length < 1) {
            throw new ServerException(getContext(), R.string.err_empty);
        }

        if (data[0].equals(Constants.PACKET_RESPONSE_OK)) {
            String sessionID = data[1];
            String viewURL = data[2];
            String joinCode = null; // joinCode is no longer applicable as group shares are removed
            String viewID = viewURL; // Default to full URL

            // If the server sends it, get the internal share ID.
            // For CREATE_ALONE, this would be data[3] if compatible and available.
            if (backendVersion.isAtLeast(Constants.VERSION_COMPAT_VIEW_ID)) {
                if (data.length > 3) { // Check array bounds before accessing data[3]
                    viewID = data[3];
                }
            }

            Session session = new Session(
                    this.params.getServerURL(),
                    this.params.getConnectionParameters(),
                    backendVersion,
                    sessionID,
                    this.params.getDuration() * TimeUtils.MILLIS_PER_SECOND + System.currentTimeMillis(),
                    this.params.getInterval(),
                    this.params.getMinimumDistance(),
                    e2eParams
            );
            // Mode is always CREATE_ALONE now
            Share share = new Share(session, viewURL, viewID, joinCode, this.mode);

            this.handler.onSessionInitiated(share);
        } else {
            StringBuilder err = new StringBuilder();
            for (String line : data) {
                err.append(line);
                err.append(System.lineSeparator());
            }
            throw new ServerException(err.toString());
        }
    }

    @Override
    protected final void onFailure(Exception ex) {
        this.handler.onFailure(ex);
    }

    public interface ResponseHandler extends FailureHandler {
        void onSessionInitiated(Share share);
        void onShareModeIncompatible(ShareMode downgradeTo, Version backendVersion);
        void onE2EUnavailable(Version backendVersion);
    }

    public static final class InitParameters {
        private final String server;
        private final String username;
        private final String password;
        private final int duration;
        private final int interval;
        private final float minDistance;
        private final String customID;
        private final String e2ePass; // Field name
        private ConnectionParameters connParams;

        public InitParameters(String server, String username, String password, int duration, int interval, float minDistance, String customID, String e2ePass) {
            this.server = server;
            this.connParams = null;
            this.username = username == null || username.isEmpty() ? null : username;
            this.password = password;
            this.duration = duration;
            this.interval = interval;
            this.minDistance = minDistance;
            this.customID = customID == null || customID.isEmpty() ? null : customID;
            this.e2ePass = e2ePass == null || e2ePass.isEmpty() ? null : e2ePass;
        }

        String getServerURL() { return this.server; }
        public void setConnectionParameters(ConnectionParameters connParams) { this.connParams = connParams; }
        ConnectionParameters getConnectionParameters() { return this.connParams; }
        @Nullable String getUsername() { return this.username; }
        String getPassword() { return this.password; }
        int getDuration() { return this.duration; }
        int getInterval() { return this.interval; }
        float getMinimumDistance() { return this.minDistance; }
        @Nullable String getCustomID() { return this.customID; }
        @Nullable String getE2EPassword() { return this.e2ePass; } // Corrected: this.e2ePass
    }
}

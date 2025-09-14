package info.varden.hauk.system.preferences.indexresolver;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * An enum preference that maps night mode styles for the app.
 *
 * @author Marius Lindvall
 */
@SuppressWarnings("unused")
public final class NightModeStyle extends Resolver<NightModeStyle, Integer> {
    private static final long serialVersionUID = 1926796368584326815L;

    public static final NightModeStyle FOLLOW_SYSTEM = new NightModeStyle(0, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    public static final NightModeStyle AUTO_BATTERY = new NightModeStyle(1, AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
    public static final NightModeStyle ALWAYS_DARK = new NightModeStyle(2, AppCompatDelegate.MODE_NIGHT_YES);
    public static final NightModeStyle NEVER_DARK = new NightModeStyle(3, AppCompatDelegate.MODE_NIGHT_NO);

    private NightModeStyle(int index, Integer mapping) {
        super(index, mapping);
    }

    /**
     * Returns the underlying AppCompatDelegate.NightMode int value.
     * This method is annotated to satisfy Lint's WrongConstant check.
     * @return The AppCompatDelegate.NightMode int value.
     */
    @AppCompatDelegate.NightMode
    public int getResolvedNightModeValue() {
        // The 'mapping' field in the parent Resolver class holds the Integer.
        Integer value = resolve();
        if (value == null) {
            // This case should ideally not be reached given the constructor ensures 'mapping' is set.
            // Fallback to a default value if it somehow is null.
            return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        return value.intValue();
    }
}

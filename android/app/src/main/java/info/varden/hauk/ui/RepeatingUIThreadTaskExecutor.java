package info.varden.hauk.ui;

import android.os.Handler;

import androidx.annotation.UiThread;

import java.util.Timer;
import java.util.TimerTask;

/**
 * UI timer class that repeatedly runs a given task on the UI thread.
 *
 * @author Marius Lindvall
 */
abstract class RepeatingUIThreadTaskExecutor {
    /**
     * Android handler for task posting.
     */
    private final Handler handler;

    /**
     * Timer that repeatedly posts to the handler.
     */
    private Timer timer = null;
    private long currentInterval; // Store interval for rescheduling

    /**
     * Called from the UI thread on every tick of the timer.
     */
    protected abstract void onTick();

    @UiThread
    RepeatingUIThreadTaskExecutor() {
        this.handler = new Handler();
    }

    /**
     * Starts the timer with the given delay and interval.
     *
     * @param delay    The delay before first run, in milliseconds.
     * @param interval The interval between each tick, in milliseconds.
     */
    @SuppressWarnings("SameParameterValue") // to ensure future extensibility
    final void start(long delay, long interval) {
        if (this.timer != null) { // Stop any existing timer first
            this.timer.cancel();
            this.timer.purge();
        }
        this.timer = new Timer();
        this.currentInterval = interval; // Store the interval
        this.timer.schedule(new RepeatingTask(this.currentInterval), delay); // Schedule first run
    }

    /**
     * Stops the timer and prevents it from ticking further.
     */
    final void stop() {
        if (this.timer != null) {
            this.timer.cancel();
            this.timer.purge();
            this.timer = null;
        }
    }

    /**
     * The timer task that is executed by the timer.
     */
    private final class RepeatingTask extends TimerTask {
        private final long taskInterval;

        RepeatingTask(long interval) {
            this.taskInterval = interval;
        }

        @Override
        public void run() {
            // Post the task to be run on the UI thread
            RepeatingUIThreadTaskExecutor.this.handler.post(new Task());

            // Reschedule if the timer hasn't been stopped
            synchronized (RepeatingUIThreadTaskExecutor.this) {
                if (RepeatingUIThreadTaskExecutor.this.timer != null) {
                    try {
                        RepeatingUIThreadTaskExecutor.this.timer.schedule(new RepeatingTask(this.taskInterval), this.taskInterval);
                    } catch (IllegalStateException e) {
                        // Timer was cancelled or purged concurrently, which is fine.
                    }
                }
            }
        }
    }

    /**
     * The runnable that is executed by the {@link Handler}. Calls the tick function.
     */
    private final class Task implements Runnable {
        @Override
        public void run() {
            // Only run if the timer is still active (not stopped)
            // This check helps prevent onTick from running after stop() has been called,
            // especially if there was a pending post in the Handler queue.
            if (RepeatingUIThreadTaskExecutor.this.timer != null) {
                onTick();
            }
        }
    }
}

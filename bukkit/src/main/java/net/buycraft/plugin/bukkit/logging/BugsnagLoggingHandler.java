package net.buycraft.plugin.bukkit.logging;

import com.bugsnag.Bugsnag;
import com.bugsnag.Severity;
import com.google.common.base.Preconditions;
import net.buycraft.plugin.bukkit.BuycraftPlugin;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

public class BugsnagLoggingHandler extends Handler {
    private static final Pattern PLUGIN_ERROR = Pattern.compile("Could not dispatch command '(.*)' for player '(.*)'\\. " +
            "This is typically a plugin error, not an issue with BuycraftX\\.");
    private final Bugsnag client;
    private final BuycraftPlugin plugin;

    public BugsnagLoggingHandler(Bugsnag client, BuycraftPlugin plugin) {
        this.plugin = Preconditions.checkNotNull(plugin, "plugin");
        this.client = Preconditions.checkNotNull(client, "client");
    }

    @Override
    public void publish(final LogRecord record) {
        if (record.getThrown() == null) {
            return;
        }

        if (PLUGIN_ERROR.matcher(record.getMessage()).find()) {
            return;
        }

        boolean relevant = false;
        for (StackTraceElement element : record.getThrown().getStackTrace()) {
            if (element.getClassName().startsWith("net.buycraft.plugin")) {
                relevant = true;
                break;
            }
        }

        if (!relevant) {
            return;
        }

        if (record.getLevel() == Level.SEVERE) {
            client.notify(client.buildReport(record.getThrown())
                    .setSeverity(Severity.ERROR));
        } else if (record.getLevel() == Level.WARNING) {
            client.notify(client.buildReport(record.getThrown())
                    .setSeverity(Severity.WARNING));
        }
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() throws SecurityException {

    }
}

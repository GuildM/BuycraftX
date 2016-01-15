package net.buycraft.plugin.bukkit.tasks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import net.buycraft.plugin.bukkit.BuycraftPlugin;
import net.buycraft.plugin.bukkit.util.CommandExecutorResult;
import net.buycraft.plugin.client.ApiException;
import net.buycraft.plugin.data.QueuedCommand;
import net.buycraft.plugin.data.responses.QueueInformation;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;

@RequiredArgsConstructor
public class ImmediateExecutionRunner implements Runnable {
    private final BuycraftPlugin plugin;
    private final Set<Integer> executingLater = Sets.newConcurrentHashSet();
    private final Random random = new Random();

    @Override
    public void run() {
        if (plugin.getApiClient() == null) {
            return; // no API client
        }

        QueueInformation information;

        do {
            plugin.getLogger().info("Fetching commands to execute...");

            try {
                information = plugin.getApiClient().retrieveOfflineQueue();
            } catch (IOException | ApiException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not fetch command queue", e);
                return;
            }

            // Filter out commands we're going to execute at a later time.
            for (Iterator<QueuedCommand> it = information.getCommands().iterator(); it.hasNext(); ) {
                QueuedCommand command = it.next();
                if (executingLater.contains(command.getId()))
                    it.remove();
            }

            // Perform the actual command execution.
            CommandExecutorResult result;
            try {
                result = new ExecuteAndConfirmCommandExecutor(plugin, null, information.getCommands(), false, false).call();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unable to execute commands", e);
                return;
            }

            if (!result.getQueuedForDelay().isEmpty()) {
                for (QueuedCommand command : result.getQueuedForDelay().values()) {
                    executingLater.add(command.getId());
                }

                for (Map.Entry<Integer, Collection<QueuedCommand>> entry : result.getQueuedForDelay().asMap().entrySet()) {
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new ExecuteAndConfirmCommandExecutor(plugin,
                            null, ImmutableList.copyOf(entry.getValue()), false, true), entry.getKey() * 20);
                }
            }

            // Sleep for between 0.5-1.5 seconds to help spread load.
            try {
                Thread.sleep(500 + random.nextInt(1000));
            } catch (InterruptedException e) {
                // Shouldn't happen, but in that case just bail out.
                return;
            }
        } while (!information.getMeta().isLimited());
    }
}

package de.minetropolis.minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import de.minetropolis.util.InterpretedPattern;
import de.minetropolis.util.Notification;
import de.minetropolis.util.Statics;
import de.minetropolis.util.Corrections.Correction;

public class CommandblockCorrectCommand implements CommandExecutor {

    private final CommandCorrector plugin;
    private List<InterpretedPattern> defaultChangeRules = Collections.emptyList();

    public CommandblockCorrectCommand(CommandCorrector plugin) {
        this.plugin = plugin;
    }

    public void setDefaultChangeRules(List<InterpretedPattern> changes) {
        this.defaultChangeRules = new ArrayList<>(Objects.requireNonNull(changes));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.messenger.setReceiver(sender);
        boolean result = doCommand(sender, command, label, args);
        plugin.messenger.reset();
        return result;
    }

    public boolean doCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("commandcorrect.apply")) {
            sender.sendMessage("You don't have the required Permissions!");
            return true;
        }

        if (args == null || args.length == 0)
            return false;

        args = Statics.process(args);

        final List<InterpretedPattern> changeRules;
        Location min, max;
        min = plugin.getBound(-1, args[0], sender);
        max = plugin.getBound(1, args[0], sender);

        if (min == null || max == null)
            return false;

        switch (args.length) {
            case 1:
                changeRules = getChangeRule(null, null, null);
                break;
            case 3:
                args = Arrays.copyOf(args, 4);
                args[3] = "";
            case 4:
                changeRules = getChangeRule(args[1], args[2], args[3]);
                break;
            default:
                return false;
        }

        return true;
    }

    private List<InterpretedPattern> getChangeRule(String pattern, String target, String assertion) {
        if (pattern != null && target != null && assertion != null && !pattern.isEmpty()) {
            List<InterpretedPattern> changeRule = new ArrayList<>();
            changeRule.add(Statics.interpretPattern(pattern).fill(target, assertion));
            return changeRule;
        } else {
            return Collections.unmodifiableList(defaultChangeRules);
        }
    }

    private void correctCommandblocks(Location min, Location max, List<InterpretedPattern> changeRules) {
        int blocksFound = 0;
        int blocksChanged = 0;
        Map<String, Integer> changes = new TreeMap<>();
        Correction correction = plugin.corrections.makeNew();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {

                    Location blockLocation = new Location(min.getWorld(), x, y, z);
                    BlockState commandBlock = blockLocation.getBlock().getState();
                    if (commandBlock instanceof CommandBlock) {
                        blocksFound++;
                        Set<String> blockChanges = correctCommandblock((CommandBlock) commandBlock, changeRules, correction);
                        if (!blockChanges.isEmpty()) {
                            blocksChanged++;
                            for (String change : blockChanges) {
                                int amountChanged = changes.getOrDefault(change, 0);
                                changes.put(change, amountChanged + 1);
                            }
                        }
                    }
                }
            }
        }
        plugin.getLogger().log(Level.INFO, "{0} has modified {1} commands from {2} of {3} commandblocks!",
        	new Object[]{plugin.messenger.getReceiver().getName(), correction.getCorrections().size(), blocksChanged, blocksFound});
        plugin.messenger.message(blocksChanged + " / " + blocksFound + " commandblocks were modified with " + correction.getCorrections().size() + " modifications. Undo with /ccu");
    }

    private Set<String> correctCommandblock(CommandBlock commandBlock, List<InterpretedPattern> changeRules,
                                            Correction correction) {
        Set<String> changes = new HashSet<>();
        String command = commandBlock.getCommand();
        String changed = command;
        for (InterpretedPattern ip : changeRules) {
            String unchanged = changed;
            Notification notification = Statics.notify(Statics.changeCommand(ip, changed));
            notification.entries.forEach(entry -> plugin.messenger.message("CommandBlock at" + Statics.locationToString(commandBlock.getLocation()) + " notifies: " + entry.message,
                    entry.colorText, "/tp @p" + Statics.locationToString(commandBlock.getLocation())));

            changed = notification.command;

            if (!changed.equals(unchanged))
                changes.add(ip.pattern);
        }
        if (!changed.equals(command)) {
            commandBlock.setCommand(changed);
        }
        if (commandBlock.update(true, false)) {
            correction.add(commandBlock.getLocation(), plugin.getCBDataString(commandBlock), command, changed);
            return Collections.unmodifiableSet(changes);
        } else {
            plugin.messenger.message("Couldn't modify commandblock at:" + Statics.locationToString(commandBlock.getLocation()), null, "tp @p" + Statics.locationToString(commandBlock.getLocation()));
            plugin.getLogger().log(Level.WARNING, "Couldn't modify commandblock at {0}", commandBlock.getLocation());
            return Collections.emptySet();
        }
    }

}
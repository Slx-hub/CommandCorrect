package de.minetropolis.commandcorrectorutil;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.minetropolis.commandcorrector.CommandCorrector;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

import de.minetropolis.commandcorrectorutil.NotificationEntry;

public class Statics {

    public static List<InterpretedPattern> loadConfig() {
        File jar = null;
        try {
            jar = new File(Statics.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e1) {
            e1.printStackTrace();
        }
        File config = new File(new File(jar.getParentFile().toURI().getPath(), "CommandCorrector").toURI().getPath(), "config.yml");

        if (!config.exists() || config.isDirectory()) {
            config.getParentFile().mkdirs();
            new File(config.getParent(), "Dedicated").mkdir();
            try {
                Files.copy(Statics.class.getResourceAsStream("/config.yml"), Paths.get(config.toURI()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            StringBuilder sb = new StringBuilder();
            Files.readAllLines(config.toPath()).forEach(string -> sb.append(string).append("\n"));
            return processFile(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Collections.emptyList();
    }

    private static List<InterpretedPattern> processFile(String string) {
        List<InterpretedPattern> list = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?:^|\\n)[ \\t]*\\\"(.+)\\\"[ \\t]*\\n?:[ \\t]*\\n?[ \\t]*\\\"(.*)\\\"[ \\t]*\\n?[ \\t]*\\|[ \\t]*\\n?\\\"(.*)\\\"").matcher(string);
        while (matcher.find()) {
            list.add(interpretPattern(matcher.group(1)).fill(matcher.group(2), matcher.group(3)));
        }
        return list;
    }

    public static Location getLocation(CommandSender sender) {
        Location location = null;
        if (sender instanceof Entity) {
            location = ((Entity) sender).getLocation();
        } else if (sender instanceof BlockCommandSender) {
            location = ((BlockCommandSender) sender).getBlock().getLocation();
        } else {
            sender.sendMessage("This command is not supported for this sender.");
        }
        return location;
    }

    public static String locationToString(Location location) {
        return new StringBuilder(" ").append(location.getBlockX()).append(" ").append(location.getBlockY()).append(" ")
                .append(location.getBlockZ()).toString();
    }

    public static String[] process(String[] args) {
        ArrayList<String> processed = new ArrayList<>();

        StringBuilder stringBuilder = new StringBuilder();
        Arrays.asList(args).forEach(arg -> stringBuilder.append(arg).append(" "));

        String commandArgs = stringBuilder.toString().trim();

        String[] splitArgs = commandArgs.split(";/");
        processed.addAll(Arrays.asList(splitArgs[0].trim().split(" ")));
        for (int i = 1; i < splitArgs.length; i++)
            processed.add(splitArgs[i].trim());
        if (Objects.equals(processed.get(0), ""))
            processed.remove(0);

        return processed.toArray(new String[0]);
    }

    public static InterpretedPattern interpretPattern(String pattern) {
        InterpretedPattern ip = findGroupPositions(pattern);
        if (ip == null) {
            Bukkit.getLogger().log(Level.WARNING, "\"" + pattern + "\"" + " Has unbalanced brackets!");
            return new InterpretedPattern(pattern);
        }
        return escapeAll(ip);
    }

    public static InterpretedPattern findGroupPositions(String string) {
        InterpretedPattern ip = new InterpretedPattern(string);
        int i = 0;
        int offset = 0;
        while (i < string.length()) {
            int group = (string.indexOf(";?(", i) == -1) ? Integer.MAX_VALUE : string.indexOf(";?(", i);
            int array = (string.indexOf(";>(", i) == -1) ? Integer.MAX_VALUE : string.indexOf(";>(", i);
            if (Math.min(group, array) == Integer.MAX_VALUE)
                return ip;
            i = Math.min(group, array);
            if (group < array) {
                boolean capturing = ";?(?:".equals(string.substring(i, i + 4));
                Group pos = new Group(ip, i, 0, offset, offset -= 2, true, capturing);
                int braceCount = 1;
                for (i = i + 3; i < string.length() && braceCount > 0; i++) {
                    if (string.charAt(i) == '(' && string.charAt(i - 1) != '\\')
                        braceCount++;
                    if (string.charAt(i) == ')' && string.charAt(i - 1) != '\\')
                        braceCount--;
                }
                if (braceCount > 0) {
                    return null;
                }
                pos.end = i;
                ip.groups.add(pos);
            } else {
                Group pos = new Group(ip, i, string.indexOf(")<;", i) + 1,
                        offset, offset += -2 + StringUtils.countMatches(string.substring(i + 2, string.indexOf(")<;", i)), "(") * 2,
                        false, true);
                ip.groups.add(pos);
                i = string.indexOf(")<;", i);
            }
        }
        return ip;
    }

    public static InterpretedPattern escapeAll(InterpretedPattern ip) {
        StringBuilder sb = new StringBuilder();
        Group group = null;
        if (!ip.groups.isEmpty())
            group = ip.groups.get(0);
        for (int i = 0; i < ip.pattern.length(); i++) {
            if (!ip.groups.isEmpty() && i >= group.start && i <= group.end - 1) {
                if (i == group.start) {
                    i += 2;
                    if (!group.group) {
                        sb.append("(");
                    }
                }
                if (i < ip.pattern.length()) {
                    sb.append(ip.pattern.charAt(i));
                    if (!group.group && ip.pattern.charAt(i) == '(')
                        sb.append("?:");
                }
                if (i == group.end - 1) {
                    if (!group.group) {
                        i += 2;
                        sb.append(")");
                    }
                    if (ip.groups.indexOf(group) < ip.groups.size() - 1)
                        group = ip.groups.get(ip.groups.indexOf(group) + 1);
                }
            } else {
                String escaped = escape("" + ip.pattern.charAt(i));
                if (group != null && escaped.length() > 1)
                    group.addOffset(escaped.length() - 1, i);
                sb.append(escaped);
            }
        }
        ip.pattern = sb.toString();
        return ip;
    }

    private static String escape(String string) {
        String escapable = "\\/()[]{}?*+.$^|";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            if (escapable.contains("" + string.charAt(i)))
                sb.append("\\");
            sb.append(string.charAt(i));
        }
        return sb.toString();
    }

    public static String changeCommand(InterpretedPattern ip, String command) {
        //System.out.println(command + " || " + pattern + " || " + target);
        //Bukkit.getLogger().log(Level.WARNING, "Pattern: " + ip.pattern + "; Target: " + ip.target + "; Command: " + command);
        //System.out.println(command);

        if (!ip.assertion.equals("")) {
            Matcher asserter = Pattern.compile(ip.assertion).matcher(command);

            if (asserter.find()) {
                return command;
            }
        }

        Matcher matcher = Pattern.compile(ip.pattern).matcher(command);

        int offset = 0;
        while (matcher.find()) {
            command = command.substring(0, matcher.start() + offset) + ip.target + command.substring(matcher.group().length() + matcher.start() + offset);
            offset += ip.target.length() - matcher.group().length();
            Group group = null;
            if (!ip.groups.isEmpty())
                group = ip.groups.get(0);

            for (int i = 1; i <= matcher.groupCount(); i++) {
                String string = matcher.group(i);
                if (group != null && !group.group) {
                    String rawGroup = ip.pattern.substring(group.start + group.startOffset, group.end + group.endOffset + 1);
                    Matcher groupMatcher = Pattern.compile("\\(\\?:" + string + "\\||\\|" + string + "\\||\\|" + string + "\\)").matcher(rawGroup);
                    groupMatcher.find();
                    rawGroup = rawGroup.substring(groupMatcher.start(), rawGroup.length());
                    groupMatcher.reset();
                    groupMatcher = Pattern.compile("\\|([\\w ]*?)\\)").matcher(rawGroup);
                    groupMatcher.find();
                    string = groupMatcher.group(1);
                }
                command = command.replace(";:(" + i + ")", string);
                if (group != null && i < matcher.groupCount())
                    group = group.next();
            }

            for (int i = 1; i <= matcher.groupCount(); i++) {
                Matcher outputGroup = Pattern.compile(";\\:\\((.+?)\\)(?:\\:(!?)\\((.+?)\\))\\:;").matcher(command);
                while (outputGroup.find()) {
                    Matcher groupIndex = Pattern.compile("\\%(\\d+)").matcher(outputGroup.group(1));
                    if (groupIndex.find()) {
                        int index = Integer.parseInt(groupIndex.group(1));
                        if (matcher.groupCount() >= index) {
                            command = command.replace(outputGroup.group(), "");
                            if (matcher.group(index).equals(outputGroup.group(3)) ^ outputGroup.group(2).equals("!")) {
                                command = command.substring(0, outputGroup.start()) + outputGroup.group(1) + command.substring(outputGroup.start(), command.length());
                                command = command.replaceFirst("%" + index, matcher.group(index));
                            }
                        }
                    }
                }
            }
        }

        return command;
    }

    public static Notification notify(String pattern) {
        Matcher matcher = Pattern.compile(";!\\(([\\w ]*)\\)").matcher(pattern);
        List<Integer> positions = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        int offset = 0;

        while (matcher.find()) {
            positions.add(matcher.start() - offset);
            offset += matcher.end() - matcher.start();
            pattern = pattern.replace(matcher.group(), "");
            messages.add(matcher.group(1));
        }

        Notification notification = new Notification(pattern);

        for (int i = 0; i < positions.size(); i++) {
            String colorText = new StringBuilder(
                    pattern.substring(Math.max(positions.get(i) - 20, 0), positions.get(i))).append(ChatColor.GOLD)
                    .append(">!<").append(ChatColor.RESET).append(pattern.substring(positions.get(i),
                            Math.min(positions.get(i) + 20, pattern.length())))
                    .toString();
            String normalText = new StringBuilder(
                    pattern.substring(Math.max(positions.get(i) - 20, 0), positions.get(i)))
                    .append(">!<").append(pattern.substring(positions.get(i),
                            Math.min(positions.get(i) + 20, pattern.length())))
                    .toString();
            notification.add(new NotificationEntry(colorText, normalText, messages.get(i)));
        }
        return notification;
    }
}
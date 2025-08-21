package com.syntex.islamicstudio.commands;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syntex.islamicstudio.cli.Color;
import com.syntex.islamicstudio.cli.CommandCategory;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@CommandLine.Command(
        name = "help",
        description = "Show available commands or details for a specific command"
)
@CommandCategory("Information")
public class HelpCommand implements Runnable {

    @CommandLine.Spec
    CommandSpec spec;

    @CommandLine.Parameters(
            index = "0",
            arity = "0..1",
            paramLabel = "COMMAND",
            description = "Optional command name to show details for"
    )
    private String commandName;

    private static class TreeChars {

        final String branch;
        final String lastBranch;
        final String vertical;

        TreeChars(String branch, String lastBranch, String vertical) {
            this.branch = branch;
            this.lastBranch = lastBranch;
            this.vertical = vertical;
        }
    }

    private static TreeChars getTreeChars() {
        boolean utf8 = Charset.defaultCharset().name().equalsIgnoreCase("UTF-8");
        if (utf8) {
            return new TreeChars(" ├─ ", " └─ ", " │  ");
        } else {
            return new TreeChars(" |-- ", " \\-- ", " |   ");
        }
    }

    @Override
    public void run() {
        CommandLine root = spec.commandLine().getParent() == null
                ? spec.commandLine()
                : spec.commandLine().getParent();

        if (commandName == null) {
            printTree(root);
        } else {
            printCommandDetails(root, commandName);
        }
    }

    private void printTree(CommandLine root) {
        Map<String, CommandLine> subcommands = root.getSubcommands();
        Map<String, Map<String, String>> categorized = new HashMap<>();

        // collect commands
        for (Map.Entry<String, CommandLine> entry : subcommands.entrySet()) {
            CommandLine cmd = entry.getValue();
            String desc = String.join(" ", cmd.getCommandSpec().usageMessage().description());
            CommandCategory cat = cmd.getCommand().getClass().getAnnotation(CommandCategory.class);
            String category = (cat != null) ? cat.value() : "Other";

            categorized.computeIfAbsent(category, k -> new HashMap<>())
                    .put(entry.getKey(), desc);
        }

        // add exit as a system command
        categorized.computeIfAbsent("System", k -> new HashMap<>())
                .put("exit", "Exit the application");

        // sort categories alphabetically
        List<String> sortedCats = new ArrayList<>(categorized.keySet());
        Collections.sort(sortedCats);

        TreeChars tree = getTreeChars();

        System.out.println(Color.CYAN.wrap("\n Islamic Studio CLI"));
        System.out.println(Color.CYAN.wrap("══════════════════════════════════"));
        System.out.println(Color.WHITE.wrap("Available Commands:\n"));

        for (String cat : sortedCats) {
            System.out.println(Color.YELLOW.wrap(cat + ":"));

            Map<String, String> cmds = categorized.get(cat);
            List<String> sortedCmds = new ArrayList<>(cmds.keySet());
            Collections.sort(sortedCmds);

            int longest = sortedCmds.stream().mapToInt(String::length).max().orElse(10);

            for (int i = 0; i < sortedCmds.size(); i++) {
                String cmd = sortedCmds.get(i);
                String desc = cmds.get(cmd);
                boolean last = (i == sortedCmds.size() - 1);

                String branch = last ? tree.lastBranch : tree.branch;
                String padding = " ".repeat(longest - cmd.length());

                System.out.printf("%s%s%s%s  %s%s%n",
                        branch,
                        Color.GREEN.code(), cmd, Color.RESET.code(),
                        padding,
                        Color.WHITE.wrap(desc));
            }
            System.out.println();
        }

        System.out.println(Color.YELLOW.wrap("Tip: Use 'help <command>' for more details.\n"));
    }

    private void printCommandDetails(CommandLine root, String name) {
        if (name.equals("exit")) {
            System.out.println(Color.CYAN.wrap("\n Command: exit"));
            System.out.println(Color.CYAN.wrap("────────────────"));
            System.out.println(Color.WHITE.wrap("Exit the application\n"));
            System.out.println(Color.YELLOW.wrap("Usage: exit\n"));
            return;
        }

        CommandLine cmd = root.getSubcommands().get(name);
        if (cmd == null) {
            System.out.println(Color.RED.wrap("Unknown command: " + name));
            return;
        }

        CommandSpec cs = cmd.getCommandSpec();

        System.out.println(Color.CYAN.wrap("\n Command: " + name));
        System.out.println(Color.CYAN.wrap("──────────────────"));
        System.out.println(Color.WHITE.wrap(String.join(" ", cs.usageMessage().description())));
        System.out.println();

        // usage
        String usage = cmd.getUsageMessage(CommandLine.Help.Ansi.OFF).split("\n")[0];
        System.out.println(Color.YELLOW.wrap("Usage: " + usage + "\n"));

        // options
        if (!cs.options().isEmpty()) {
            System.out.println(Color.PURPLE.wrap("Options:"));
            cs.options().forEach(opt -> {
                String names = String.join(", ", opt.names());
                String desc = String.join(" ", opt.description());
                System.out.printf("  %s%s%s  %s%n",
                        Color.PURPLE.code(), names, Color.RESET.code(),
                        desc.isEmpty() ? "-" : desc);
            });
            System.out.println();
        }
    }
}

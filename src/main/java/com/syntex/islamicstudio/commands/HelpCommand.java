package com.syntex.islamicstudio.commands;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syntex.islamicstudio.cli.Color;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@CommandLine.Command(
    name = "help",
    description = "Show available commands or details for a specific command"
)
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
        Map<String, String> commands = new HashMap<>();
        for (Map.Entry<String, CommandLine> entry : root.getSubcommands().entrySet()) {
            String desc = String.join(" ", entry.getValue().getCommandSpec().usageMessage().description());
            commands.put(entry.getKey(), desc);
        }
        commands.put("exit", "Exit the application");

        List<String> sorted = new ArrayList<>(commands.keySet());
        Collections.sort(sorted);

        TreeChars tree = getTreeChars();

        System.out.println(Color.CYAN.wrap("\n Islamic Studio CLI"));
        System.out.println(Color.CYAN.wrap("──────────────────────"));
        System.out.println(Color.WHITE.wrap("Available Commands:\n"));

        int longest = sorted.stream().mapToInt(String::length).max().orElse(10);
        for (int i = 0; i < sorted.size(); i++) {
            String cmd = sorted.get(i);
            String desc = commands.get(cmd);
            boolean last = (i == sorted.size() - 1);

            String branch = last ? tree.lastBranch : tree.branch;
            String padding = " ".repeat(longest - cmd.length());

            System.out.printf("%s%s%s%s  %s%s%n",
                    branch,
                    Color.GREEN.code(), cmd, Color.RESET.code(),
                    padding,
                    Color.WHITE.wrap(desc));
        }
        System.out.println();
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
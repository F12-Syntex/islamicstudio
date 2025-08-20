package com.syntex.islamicstudio;

import com.syntex.islamicstudio.cli.CliInterface;
import com.syntex.islamicstudio.cli.CommandLoader;

import picocli.CommandLine;

@CommandLine.Command(
        name = "islamicstudio",
        description = "Islamic Studio CLI application"
)
public class Main implements Runnable {

    @Override
    public void run() {
        Config config = new Config();
        CommandLine cmd = buildCommandLine();
        CliInterface cli = new CliInterface(config, cmd);
        cli.start();
    }

    public static void main(String[] args) {
        CommandLine cmd = buildCommandLine();
        if (args.length > 0) {
            int exitCode = cmd.execute(args);
            System.exit(exitCode);
        } else {
            new Main().run();
        }
    }

    private static CommandLine buildCommandLine() {
        CommandLine root = new CommandLine(new Main());
        // Dynamically register commands from package
        CommandLoader.registerCommands(root, "com.syntex.islamicstudio.commands");
        return root;
    }
}
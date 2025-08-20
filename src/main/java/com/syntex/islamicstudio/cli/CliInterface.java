package com.syntex.islamicstudio.cli;

import java.util.Scanner;

import com.syntex.islamicstudio.Config;

import picocli.CommandLine;

/**
 * Interactive shell for Islamic Studio CLI. Reads config values for banner,
 * colors, prompt, etc.
 */
public class CliInterface {

    private final Config config;
    private final CommandLine cmd;
    private final Scanner scanner;

    public CliInterface(Config config, CommandLine cmd) {
        this.config = config;
        this.cmd = cmd;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        printBanner();
        loop();
    }

    private void printBanner() {
        String banner = config.get("cli.banner");
        Color bannerColor = Color.from(config.get("cli.color.banner"));
        System.out.println(bannerColor.wrap(banner.replace("\\n", "\n")));
    }

    private void loop() {
        String prompt = config.get("cli.prompt");
        Color promptColor = Color.from(config.get("cli.color.prompt"));
        String exitMessage = config.get("cli.exit");
        Color exitColor = Color.from(config.get("cli.color.exit"));
        String errorPrefix = config.get("cli.error.prefix");
        Color errorColor = Color.from(config.get("cli.color.error"));

        while (true) {
            System.out.print(promptColor.wrap(prompt));
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                System.out.println(exitColor.wrap(exitMessage));
                break;
            }

            try {
                cmd.execute(input.split("\\s+"));
            } catch (Exception e) {
                System.out.println(errorColor.wrap(errorPrefix + e.getMessage()));
            }
        }
    }
}

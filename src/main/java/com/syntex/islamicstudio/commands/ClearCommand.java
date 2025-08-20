package com.syntex.islamicstudio.commands;

import picocli.CommandLine;

@CommandLine.Command(
    name = "clear",
    description = "Clear the screen"
)
public class ClearCommand implements Runnable {
    @Override
    public void run() {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            System.out.println("Unable to clear screen: " + e.getMessage());
        }
    }
}
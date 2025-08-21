package com.syntex.islamicstudio.commands;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.syntex.islamicstudio.cli.Color;
import com.syntex.islamicstudio.cli.CommandCategory;

import picocli.CommandLine;

@CommandLine.Command(
    name = "credits",
    description = "Show credits and acknowledgements for resources used"
)
@CommandCategory("Information")
public class CreditsCommand implements Runnable {

    @Override
    public void run() {
        System.out.println(Color.CYAN.wrap("\n=== Credits & Acknowledgements ===\n"));

        try (InputStream in = getClass().getClassLoader().getResourceAsStream("credits.txt")) {
            if (in == null) {
                System.out.println(Color.RED.wrap("credits.txt not found in resources."));
                return;
            }
            String content = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            System.out.println(Color.WHITE.wrap(content));
        } catch (Exception e) {
            System.out.println(Color.RED.wrap("Error reading credits.txt: " + e.getMessage()));
        }
    }
}
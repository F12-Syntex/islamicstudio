package com.syntex.islamicstudio.cli;

import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import picocli.CommandLine;

public class CommandLoader {

    public static void registerCommands(CommandLine root, String basePackage) {
        Reflections reflections = new Reflections(basePackage, Scanners.TypesAnnotated);

        Set<Class<?>> commands = reflections.getTypesAnnotatedWith(CommandLine.Command.class);
        for (Class<?> cmdClass : commands) {
            try {
                Object instance = cmdClass.getDeclaredConstructor().newInstance();
                CommandLine.Command annotation = cmdClass.getAnnotation(CommandLine.Command.class);
                root.addSubcommand(annotation.name(), instance);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to load command: " + cmdClass.getName() + " -> " + e.getMessage());
            }
        }
    }
}
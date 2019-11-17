package com.github.sixddc.mirage;

import org.apache.commons.cli.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableScheduling
public class Application implements ApplicationRunner {

    private static long updateInterval;
    private static String fileExt;
    private static String[] scripts;
    private static String[] files;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Autowired
    FileWatcher fileWatcher;

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("c").longOpt("script").hasArg().argName("script")
                .desc("raw dsl script").build());
        options.addOption(Option.builder("p").longOpt("port").hasArg().argName("port")
                .desc("specify server port (default: 8080)").build());
        options.addOption(Option.builder("n").longOpt("interval").hasArg().argName("interval")
                .desc("specify update interval in seconds").build());
        options.addOption(Option.builder().longOpt("ext").hasArg().argName("ext")
                .desc("specify DSL file extension when the command arguments contains directory, (default: mir)").build());
        options.addOption(Option.builder("h").longOpt("help")
                .desc("print help message").build());

        DefaultParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Parsing failed: " + e.getMessage());
            System.exit(1);
        }

        if (cmd.hasOption('h')) {
            printUsage(options);
            System.exit(1);
        }

        System.setProperty("server.port", cmd.getOptionValue('p', "8080"));

        updateInterval = Long.parseLong(cmd.getOptionValue("n", "0"));
        fileExt = cmd.getOptionValue("ext", "mir");
        scripts = cmd.getOptionValues("c");
        files = cmd.getArgs();

        SpringApplication.run(Application.class, args);
    }

    private void watchWithFixedDelay(long delay) {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                fileWatcher.refreshPathSet();
            }
        }, delay, delay, TimeUnit.SECONDS);
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        fileWatcher.setDslFileExtension(fileExt);
        if (updateInterval > 0) {
            watchWithFixedDelay(updateInterval);
        }
        if (files != null) {
            for (String file : files) {
                Path path = Paths.get(file);
                fileWatcher.addFile(path);
            }
        }
        if (scripts != null) {
            for (String script : scripts) {
                fileWatcher.addScript(script);
            }
        }
    }

    private static void printUsage(Options options) {
        String header = "A web DSL to easily create a simple HTTP server\n\n";
        String footer = "\nFor more information, see https://github.com/six-ddc/mirage";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("mirage [-p <port>] [-c <script>] [file|dir]...", header, options, footer, false);
    }
}

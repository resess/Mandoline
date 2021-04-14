package mandoline.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import mandoline.exceptions.InvalidCommandsException;

public class CommandParser {
    public static final String CMD_LINE_SYNTAX = "java -jar Mandoline/target/mandoline-jar-with-dependencies.jar";
    static Options options;
    static {
        options = new Options();
        options.addOption("h", "help", false, "Display this help and exit");
        options.addOption("m", "mode", true, "Tool mode: i for instrument, g to produce ICDG, m to slice with mandoline, and md to slice with mandoline-dynamic");
        options.addOption("a", "apk", true, "apk path");
        options.addOption("p", "platform", true, "android platforms path");
        options.addOption("c", "callbacks", true, "FlowDroid's callbacks list");
        options.addOption("pk", "package-name", true, "Package name"); // packageName
        options.addOption("ml", "mandoline-logger", true, "mandoline logger jar"); // mandolineLoggerJar
        options.addOption("t", "trace", true, "Execution trace"); // fileToParse
        options.addOption("sp", "slice-position", true, "starting statement for the slice"); // posToSlice
        options.addOption("sv", "slice-variables", true, "dash-joined list of starting variables to slice from"); // variables
        options.addOption("fw", "forward-slice-position", true, "starting statement for the forward slice (for chopping)"); 
        options.addOption("sl", "static-log", true, "Static-log file path"); // static-log file
        options.addOption("o", "outDir", true, "Output directory"); // staticLogFile
        options.addOption("sd", "stub-droid", true, "Location of the StubDroid summaries"); // stubDroidPath
        options.addOption("tw", "taint-wrapper", true, "Location of the FlowDroid's taint-wrapper list"); // taintWrapperPath
        options.addOption("im", "instrumenter-mode", true, "Instrumentation mode, m for mandoline, md for for mandoline-dynamic, append j at the end of the option to print the jimple");
        options.addOption("scp", "soot-class-path", true, "Soot class path if soot compains about missing dependencies"); // scp
    }


    private CommandParser() {
        throw new IllegalStateException("Utility class");
    }

    public static Map<String, String> parse(String[] args){
        Map<String, String> parsed = new HashMap<>();
        try {
            CommandLine cmd = (new DefaultParser()).parse(options, args);
            if (cmd.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(CMD_LINE_SYNTAX, options);
                return parsed;
            }
            String[] optionTags = {"m", "a", "p", "c", "pk", "ml", "t", "sp", "sv", "o", "sd", "tw", "im", "sl", "fw", "scp"};
            for (String opt: Arrays.asList(optionTags)) {
                String value = cmd.getOptionValue(opt);
                if (value != null) {
                    parsed.put(opt, value);
                }
            }
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(CMD_LINE_SYNTAX, options);
            throw new InvalidCommandsException(e);
        }
        return parsed;
    }

    public static Options getOptions() {
        return options;
    }
}

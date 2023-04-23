package de.gematik.fhir.validator;

import ch.qos.logback.classic.Level;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

import static picocli.CommandLine.Spec.Target.MIXEE;

/**
 * This is a mixin that adds a {@code --verbose} option to a command.
 */
public class LoggingMixin {
    /**
     * This mixin is able to climb the command hierarchy because the
     * {@code @Spec(Target.MIXEE)}-annotated field gets a reference to the command where it is used.
     */
    private @Spec(MIXEE) CommandSpec mixee; // spec of the command where the @Mixin is used

    private boolean[] verbosity = new boolean[0];

    private static LoggingMixin getTopLevelCommandLoggingMixin(CommandSpec commandSpec) {
        return ((App) commandSpec.root().userObject()).loggingMixin;
    }

    /**
     * Sets the specified verbosity on the LoggingMixin of the top-level command.
     * @param verbosity the new verbosity value
     */
    @Option(names = {"-v", "--verbose"}, description = {
            "Specify multiple -v options to increase verbosity.",
            "For example, `-v -v -v` or `-vvv`"})
    public void setVerbose(boolean[] verbosity) {
        getTopLevelCommandLoggingMixin(mixee).verbosity = verbosity;
    }

    /**
     * Returns the verbosity from the LoggingMixin of the top-level command.
     * @return the verbosity value
     */
    public boolean[] getVerbosity() {
        return getTopLevelCommandLoggingMixin(mixee).verbosity;
    }

    /**
     * Configures Log4j2 based on the verbosity level of the top-level command's LoggingMixin,
     * before invoking the default execution strategy ({@link picocli.CommandLine.RunLast RunLast}) and returning the result.
     * <p>
     *   Example usage:
     * </p>
     * <pre>
     * public void main(String... args) {
     *     new CommandLine(new MyApp())
     *             .setExecutionStrategy(LoggingMixin::executionStrategy))
     *             .execute(args);
     * }
     * </pre>
     *
     * @param parseResult represents the result of parsing the command line
     * @return the exit code of executing the most specific subcommand
     */
    public static int executionStrategy(ParseResult parseResult) {
        getTopLevelCommandLoggingMixin(parseResult.commandSpec()).configureLoggers();
        return new CommandLine.RunLast().execute(parseResult);
    }

    /**
     * Configures the Log4j2 console appender(s), using the specified verbosity:
     * <ul>
     *   <li>{@code -vvv} : enable TRACE level</li>
     *   <li>{@code -vv} : enable DEBUG level</li>
     *   <li>{@code -v} : enable INFO level</li>
     *   <li>(not specified) : enable WARN level</li>
     * </ul>
     */
    public void configureLoggers() {
        Level level = getTopLevelCommandLoggingMixin(mixee).calculateLogLevel();
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(level);
    }

    private Level calculateLogLevel() {
        switch (getVerbosity().length) {
            case 0:  return Level.ERROR;
            case 1:  return Level.INFO;
            case 2:  return Level.DEBUG;
            default: return Level.TRACE;
        }
    }

}
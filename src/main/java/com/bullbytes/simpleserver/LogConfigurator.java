package com.bullbytes.simpleserver;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.logging.*;

/**
 * Configures Java's logging mechanism such as the log message's format and the location of the log files.
 * <p>
 * Person of contact: Matthias Braun
 */
public enum LogConfigurator {
    ;

    /**
     * Gets the root {@link Logger}.
     *
     * @return the parent of all loggers from which other loggers inherit log levels and handlers
     * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/logging/overview.html#a1.3">Logging overview</a>
     */
    public static Logger getRootLogger() {
        return Logger.getLogger("");
    }

    /**
     * Gets the {@link ConsoleHandler} of the root logger.
     * <p>
     * Configuring this handler will change how the children of the root logger log to console.
     *
     * @return the {@link ConsoleHandler} of the root logger wrapped in a {@link Optional} if the root logger doesn't
     * have a console handler
     */
    static Optional<Handler> getDefaultConsoleHandler() {
        var rootLogger = getRootLogger();
        return first(rootLogger.getHandlers());
    }

    private static Optional<Handler> first(Handler[] handlers) {
        return handlers.length == 0 ?
                Optional.empty() :
                Optional.ofNullable(handlers[0]);
    }

    /**
     * Configures the format used by the default {@link ConsoleHandler} and adds a {@link FileHandler} that logs to
     * a local file.
     *
     * @param logLevel the handlers will process the messages of this log level or above that the log creates
     */
    public static void configureLogHandlers(Level logLevel) {
        Formatter formatter = getCustomFormatter();
        getDefaultConsoleHandler().ifPresentOrElse(
                consoleHandler -> {
                    consoleHandler.setLevel(logLevel);
                    consoleHandler.setFormatter(formatter);
                },
                () -> System.err.println("Could not get default ConsoleHandler"));

    }

    private static Formatter getCustomFormatter() {
        return new Formatter() {

            @Override
            public String format(LogRecord record) {

                var dateTime = ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault());

                int threadId = record.getThreadID();
                String threadName = Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.getId() == (long) threadId)
                        .findFirst()
                        .map(Thread::getName)
                        .orElseGet(() -> "Thread with ID " + threadId);

                /*
                 * Formats a log message like this:
                 * <p>
                 * INFO    Server started [2019-05-09 18:08:16 +0200] [com.bullbytes.simpleserver.Start.lambda$main$1] [main]
                 * <p>
                 * See also: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/Formatter.html
                 */
                var FORMAT_STRING = "%2$-7s %6$s [%1$tF %1$tT %1$tz] [%4$s.%5$s] [%3$s]%n%7$s";
                return String.format(
                        FORMAT_STRING,
                        dateTime,
                        record.getLevel().getName(),
                        threadName,
                        record.getSourceClassName(),
                        record.getSourceMethodName(),
                        record.getMessage(),
                        stackTraceToString(record)
                );
            }
        };
    }

    private static String stackTraceToString(LogRecord record) {
        String throwableAsString;
        if (record.getThrown() != null) {
            var stringWriter = new StringWriter();
            var printWriter = new PrintWriter(stringWriter);
            printWriter.println();
            record.getThrown().printStackTrace(printWriter);
            printWriter.close();
            throwableAsString = stringWriter.toString();
        } else {
            throwableAsString = "";
        }
        return throwableAsString;
    }

}

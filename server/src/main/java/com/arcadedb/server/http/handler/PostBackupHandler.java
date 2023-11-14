package com.arcadedb.server.http.handler;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import com.arcadedb.database.Database;
import com.arcadedb.network.binary.ServerIsNotTheLeaderException;
import com.arcadedb.server.MinioRestClient;
import com.arcadedb.server.ha.HAServer;
import com.arcadedb.server.http.HttpServer;
import com.arcadedb.server.security.ServerSecurityUser;
import com.arcadedb.server.security.oidc.role.ServerAdminRole;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import lombok.extern.slf4j.Slf4j;

/**
 * Backup all databases (with backups enabled) to minio
 */
@Slf4j
public class PostBackupHandler extends AbstractHandler {

    public PostBackupHandler(final HttpServer httpServer) {
        super(httpServer);
    }

    private void sendEvent(final HttpServerExchange exchange, String message) {
        exchange.getResponseSender().send(message + "\n\n", new IoCallback() {
            @Override
            public void onComplete(HttpServerExchange exchange, Sender sender) {
                // NO-OP (keep the connection open)
            }

            @Override
            public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                log.error("Error sending event", exception.getMessage());
                log.debug("Exception", exception);
            }
        });
    }

    // TODO optionally recieve database name to backup, otherwise backup all
    // databases.
    /**
     * Expecting exchange to contain the root backup url in minio
     */

    public ExecutionResponse execute(final HttpServerExchange exchange, final ServerSecurityUser user) {

        var anyRequiredRoles = List.of(ServerAdminRole.BACKUP_DATABASE,
                ServerAdminRole.ALL);
        if (httpServer.getServer().getSecurity().checkUserHasAnyServerAdminRole(user,
                anyRequiredRoles)) {
            checkServerIsLeaderIfInHA();
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/event-stream");

        // Start a new thread to handle SSE
        new Thread(() -> {
            backupDatabases(exchange);
        }).start();

        return null;
    }

    private void backupDatabases(final HttpServerExchange exchange) {
        var databaseNames = httpServer.getServer().getDatabaseNames();
        int counter = 1;
        long ellapsedCounter = 0l;
        for (String databaseName : databaseNames) {
            var database = httpServer.getServer().getDatabase(databaseName);
            if (database.getSchema().getEmbedded().shouldBackup()) {
                try {
                    backupDatabase(database, counter, databaseNames.size(), exchange);

                } catch (Exception e) {
                    String event = String.format("ERROR - error backing up database %s, message: %s",
                            databaseName, e.getMessage());
                    sendEvent(exchange, event);
                }
            } else {
                String event = String.format("INFO - (%n/$n) Skipping disabled backup of: %s", counter,
                        databaseNames.size(), database.getName());
                sendEvent(exchange, event);
            }
        }

        String ellapsed = formatDuration(Duration.ofMillis(ellapsedCounter));
        String event = String.format("INFO - Arcade backup complete. %s databases backed up in %s",
                databaseNames.size(), ellapsed);
        sendEvent(exchange, event);
        exchange.getResponseSender().close();
    }

    private void backupDatabase(Database database, int counter, int numDatabases, final HttpServerExchange exchange) {

        Instant dbStart = Instant.now();

        String event = String.format("INFO - (%n/$n) Starting backup of: %s", counter,
                numDatabases, database.getName());
        sendEvent(exchange, event);

        final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmssSSS");
        String fileName = String.format("%s-backup-%s.zip", database.getName(),
                dateFormat.format(System.currentTimeMillis()));

        String command = String.format("backup database file://%s", fileName);
        database.command("sql", command, httpServer.getServer().getConfiguration(), new HashMap<>());

        MinioRestClient.uploadBackup(database.getName(), fileName);

        Instant dbStop = Instant.now();
        String ellapsed = formatDuration(Duration.ofMillis(dbStop.toEpochMilli() - dbStart.toEpochMilli()));
        event = String.format("INFO - (%n/$n) backup of %s completed in %s", counter,
                numDatabases, database.getName(), ellapsed);
        sendEvent(exchange, ellapsed);
    }

    private void checkServerIsLeaderIfInHA() {
        final HAServer ha = httpServer.getServer().getHA();
        if (ha != null && !ha.isLeader())
            // NOT THE LEADER
            throw new ServerIsNotTheLeaderException("Backup of database can be executed only on the leader server",
                    ha.getLeaderName());
    }

    /**
     * Format duration as HH:MM:SS for better readability compared to a million seconds
     * @param duration
     * @return
     */
    private String formatDuration(Duration duration) {
        long HH = duration.toHours();
        long MM = duration.toMinutesPart();
        long SS = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", HH, MM, SS);
    }
}

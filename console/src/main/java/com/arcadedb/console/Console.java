/*
 * Copyright © 2021-present Arcade Data Ltd (info@arcadedata.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-FileCopyrightText: 2021-present Arcade Data Ltd (info@arcadedata.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package com.arcadedb.console;

import com.arcadedb.Constants;
import com.arcadedb.GlobalConfiguration;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.DatabaseInternal;
import com.arcadedb.database.Document;
import com.arcadedb.database.TransactionContext;
import com.arcadedb.engine.PaginatedFile;
import com.arcadedb.exception.ArcadeDBException;
import com.arcadedb.exception.CommandSQLParsingException;
import com.arcadedb.graph.Edge;
import com.arcadedb.graph.Vertex;
import com.arcadedb.query.sql.executor.MultiValue;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultInternal;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.remote.RemoteDatabase;
import com.arcadedb.schema.DocumentType;
import com.arcadedb.utility.RecordTableFormatter;
import com.arcadedb.utility.TableFormatter;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.*;
import java.util.*;

public class Console {
  private static final String           PROMPT               = "%n%s> ";
  private static final String           REMOTE_PREFIX        = "remote:";
  private static final String           SQL_LANGUAGE         = "SQL";
  private final        boolean          system               = System.console() != null;
  private final        Terminal         terminal;
  private final        LineReader       lineReader;
  private final        TerminalParser   parser               = new TerminalParser();
  private              RemoteDatabase   remoteDatabase;
  private              ConsoleOutput    output;
  private              DatabaseFactory  databaseFactory;
  private              DatabaseInternal localDatabase;
  private              int              limit                = 20;
  private              int              maxMultiValueEntries = 10;
  private              int              maxWidth             = TableFormatter.DEFAULT_MAX_WIDTH;
  private              Boolean          expandResultSet;
  private              ResultSet        resultSet;
  private              String           databaseDirectory;
  private              int              verboseLevel         = 1;
  private              String           language             = SQL_LANGUAGE;

  public Console(final DatabaseInternal database) throws IOException {
    this(false);
    this.localDatabase = database;
  }

  public Console(final boolean interactive) throws IOException {
    setRootPath(".");

    GlobalConfiguration.PROFILE.setValue("low-cpu");

    terminal = TerminalBuilder.builder().system(system).streams(System.in, System.out).jansi(true).build();
    Completer completer = new StringsCompleter("align database", "begin", "rollback", "commit", "check database", "close", "connect", "create database",
        "create user", "drop database", "drop user", "export", "import", "help", "info types", "load", "exit", "quit", "set", "match", "select", "insert into",
        "update", "delete", "pwd");

    lineReader = LineReaderBuilder.builder().terminal(terminal).parser(parser).variable("history-file", ".history").history(new DefaultHistory())
        .completer(completer).build();

    output("%s Console v.%s - %s (%s)", Constants.PRODUCT, Constants.getRawVersion(), Constants.COPYRIGHT, Constants.URL);

    if (!interactive)
      return;

    lineReader.getHistory().load();

    try {
      while (true) {

        String line = null;
        try {
          line = lineReader.readLine(getPrompt());
          if (line == null)
            continue;

          lineReader.getHistory().save();

        } catch (UserInterruptException | EndOfFileException e) {
          return;
        }

        try {
          if (!parse(line, false))
            return;
        } catch (Exception e) {
          // IGNORE (ALREADY PRINTED)
        }
      }
    } finally {
      close();
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length > 0) {
      final Console console = new Console(false);
      console.parse(args[0], true);
      console.parse("exit",true);
    } else
      new Console(true);
  }

  public void close() {
    if (terminal != null)
      flushOutput();

    if (remoteDatabase != null) {
      remoteDatabase.close();
      remoteDatabase = null;
    }

    if (localDatabase != null) {
      localDatabase.close();
      localDatabase = null;
    }

    if (databaseFactory != null) {
      databaseFactory.close();
      databaseFactory = null;
    }
  }

  public Console setRootPath(final String rootDirectory) {
    String root = rootDirectory;
    if (root == null || root.isEmpty())
      root = ".";
    else if (root.endsWith(File.separator))
      root = root.substring(0, root.length() - 1);

    if (!new File(root + File.separator + "config").exists() && new File(root + File.separator + ".." + File.separator + "config").exists()) {
      databaseDirectory = new File(root).getAbsoluteFile().getParentFile().getPath() + File.separator + "databases" + File.separator;
    } else
      databaseDirectory = root + File.separator + "databases" + File.separator;

    return this;
  }

  public void setOutput(final ConsoleOutput output) {
    this.output = output;
  }

  private boolean execute(String line) throws IOException {
    try {

      line = line.trim();

      if (line != null && !line.isEmpty()) {

        final String lineLowerCase = line.toLowerCase();

        if (lineLowerCase.startsWith("begin"))
          executeBegin();
        else if (lineLowerCase.startsWith("close"))
          executeClose();
        else if (lineLowerCase.startsWith("commit"))
          executeCommit();
        else if (lineLowerCase.startsWith("connect"))
          executeConnect(line);
        else if (lineLowerCase.startsWith("create database"))
          executeCreateDatabase(line);
        else if (lineLowerCase.startsWith("create user"))
          executeCreateUser(line);
        else if (lineLowerCase.startsWith("drop database"))
          executeDropDatabase(line);
        else if (lineLowerCase.startsWith("drop user"))
          executeDropUser(line);
        else if (lineLowerCase.equals("help") || line.equals("?"))
          executeHelp();
        else if (lineLowerCase.startsWith("info"))
          executeInfo(line.substring("info".length()).trim());
        else if (lineLowerCase.startsWith("load"))
          executeLoad(line.substring("load".length()).trim());
        else if (lineLowerCase.equals("quit") || lineLowerCase.equals("exit")) {
          executeClose();
          return false;
        } else if (lineLowerCase.startsWith("pwd"))
          outputLine("Current directory: " + new File(".").getAbsolutePath());
        else if (lineLowerCase.startsWith("rollback"))
          executeRollback();
        else if (lineLowerCase.startsWith("set"))
          executeSet(line.substring("set".length()).trim());
        else if (lineLowerCase.startsWith("--"))
          return true;
        else {
          executeSQL(line);
        }
      }

      return true;
    } catch (IOException | RuntimeException e) {
      outputError(e);
      throw e;
    }
  }

  private void executeSet(final String line) {
    if (line == null || line.isEmpty())
      return;

    final String[] parts = line.split("=");
    if (parts.length != 2) {
      outputLine("ERROR: invalid syntax for SET. Use SET <name> = <value>");
      return;
    }

    final String key = parts[0].trim();
    final String value = parts[1].trim();

    if ("limit".equalsIgnoreCase(key)) {
      limit = Integer.parseInt(value);
      outputLine("Set new limit to %d", limit);
    } else if ("language".equalsIgnoreCase(key)) {
      language = value;
      outputLine("Set language to %s", language);
    } else if ("expandResultSet".equalsIgnoreCase(key)) {
      expandResultSet = value.equalsIgnoreCase("true");
      outputLine("Set expanded result set to %s", expandResultSet);
    } else if ("maxMultiValueEntries".equalsIgnoreCase(key)) {
      maxMultiValueEntries = Integer.parseInt(value);
      outputLine("Set maximum multi value entries to %d", maxMultiValueEntries);
    } else if ("verbose".equalsIgnoreCase(key)) {
      verboseLevel = Integer.parseInt(value);
      outputLine("Set verbose level to %d", verboseLevel);
    } else if ("maxWidth".equalsIgnoreCase(key)) {
      maxWidth = Integer.parseInt(value);
      outputLine("Set maximum width to %d", maxWidth);
    }
  }

  private void executeTransactionStatus() {
    checkDatabaseIsOpen();

    final TransactionContext tx = localDatabase.getTransaction();
    if (tx.isActive()) {
      final ResultInternal row = new ResultInternal();
      row.setPropertiesFromMap(tx.getStats());
      printRecord(row);

    } else
      outputLine("Transaction is not Active");
  }

  private void executeBegin() {
    checkDatabaseIsOpen();
    if (localDatabase != null)
      localDatabase.begin();
    else
      remoteDatabase.begin();
  }

  private void executeCommit() {
    checkDatabaseIsOpen();
    if (localDatabase != null)
      localDatabase.commit();
    else
      remoteDatabase.commit();
  }

  private void executeRollback() {
    checkDatabaseIsOpen();
    if (localDatabase != null)
      localDatabase.rollback();
    else
      remoteDatabase.rollback();
  }

  private void executeClose() {
    if (localDatabase != null) {
      if (localDatabase.isTransactionActive())
        localDatabase.commit();

      localDatabase.close();
      localDatabase = null;
    }

    if (remoteDatabase != null) {
      remoteDatabase.close();
      remoteDatabase = null;
    }
  }

  private void executeConnect(final String line) {
    final String url = line.substring("connect".length()).trim();

    final String[] urlParts = url.split(" ");

    if (localDatabase != null || remoteDatabase != null)
      outputLine("Database already connected, to connect to a different database close the current one first");
    else if (!urlParts[0].isEmpty()) {
      if (urlParts[0].startsWith(REMOTE_PREFIX)) {
        connectToRemoteServer(url);

        outputLine("Connected");
        flushOutput();

      } else {
        PaginatedFile.MODE mode = PaginatedFile.MODE.READ_WRITE;
        if (urlParts.length > 1)
          mode = PaginatedFile.MODE.valueOf(urlParts[1].toUpperCase());

        final String databaseUrl = databaseDirectory + urlParts[0];

        databaseFactory = new DatabaseFactory(databaseUrl);
        localDatabase = (DatabaseInternal) databaseFactory.setAutoTransaction(true).open(mode);
      }
    } else
      throw new ConsoleException("URL missing");
  }

  private void executeCreateDatabase(final String line) {
    String url = line.substring("create database".length()).trim();
    if (localDatabase != null || remoteDatabase != null)
      outputLine("Database already connected, to connect to a different database close the current one first");
    else if (!url.isEmpty()) {
      if (url.startsWith(REMOTE_PREFIX)) {
        connectToRemoteServer(url);
        remoteDatabase.create();

        outputLine("Database created");
        flushOutput();

      } else {
        if (url.startsWith("file://"))
          url = url.substring("file://".length());

        url = databaseDirectory + url;

        databaseFactory = new DatabaseFactory(url);
        localDatabase = (DatabaseInternal) databaseFactory.setAutoTransaction(true).create();
      }
    } else
      throw new ConsoleException("URL missing");
  }

  private void executeCreateUser(final String line) {
    if (localDatabase != null || remoteDatabase == null)
      throw new ArcadeDBException("Create a new user is allowed only on a server connected in remote");

    String params = line.substring("create user ".length()).trim();
    final String paramsUpperCase = params.toUpperCase();

    final int identifiedByPos = paramsUpperCase.indexOf("IDENTIFIED BY");
    if (identifiedByPos < 0)
      throw new CommandSQLParsingException("IDENTIFIED BY is missing");

    final int databasesByPos = paramsUpperCase.indexOf(" GRANT CONNECT TO ");

    final String userName = params.substring(0, identifiedByPos).trim();
    if (userName.isEmpty())
      throw new CommandSQLParsingException("User name is empty");

    final String password;
    final List<String> databases;
    if (databasesByPos > -1) {
      password = params.substring(identifiedByPos + "IDENTIFIED BY".length() + 1, databasesByPos).trim();
      final String databasesList = params.substring(databasesByPos + " GRANT CONNECT TO ".length()).trim();
      final String[] databasesArray = databasesList.split(",");
      databases = List.of(databasesArray);
    } else {
      password = params.substring(identifiedByPos + "IDENTIFIED BY".length() + 1).trim();
      databases = new ArrayList<>();
    }

    if (password.isEmpty())
      throw new CommandSQLParsingException("User password missing");

    if (password.indexOf(" ") > -1)
      throw new CommandSQLParsingException("User password cannot have spaces");

    remoteDatabase.createUser(userName, password, databases);

    outputLine("User '" + userName + "' created correctly on the server");
  }

  private void executeDropDatabase(final String line) {
    final String url = line.substring("drop database".length()).trim();
    if (localDatabase != null || remoteDatabase != null)
      outputLine("A database is open, close the database first");
    else if (!url.isEmpty()) {
      if (url.startsWith(REMOTE_PREFIX)) {
        connectToRemoteServer(url);
        remoteDatabase.drop();

        outputLine("Database dropped");
        flushOutput();

      } else {
        databaseFactory = new DatabaseFactory(url);
        localDatabase = (DatabaseInternal) databaseFactory.setAutoTransaction(true).open();
        localDatabase.drop();
      }
    } else
      throw new ConsoleException("URL missing");

    remoteDatabase = null;
    localDatabase = null;
  }

  private void executeDropUser(final String line) {
    if (localDatabase != null || remoteDatabase == null)
      throw new ArcadeDBException("Dropping a user is allowed only on a server connected in remote");

    final String userName = line.substring("drop user ".length()).trim();
    if (userName.isEmpty())
      throw new CommandSQLParsingException("User name is empty");

    remoteDatabase.dropUser(userName);

    outputLine("User '" + userName + "' correctly deleted on the server");
  }

  private void printRecord(final Result currentRecord) {
    if (currentRecord == null)
      return;

    final Document rec = currentRecord.getElement().orElse(null);

    if (rec instanceof Vertex)
      outputLine("VERTEX @type:%s @rid:%s", rec.getTypeName(), rec.getIdentity());
    else if (rec instanceof Edge)
      outputLine("EDGE @type:%s @rid:%s", rec.getTypeName(), rec.getIdentity());
    else if (rec != null)
      outputLine("DOCUMENT @type:%s @rid:%s", rec.getTypeName(), rec.getIdentity());

    final List<TableFormatter.TableRow> resultSet = new ArrayList<>();

    Object value;
    for (String fieldName : currentRecord.getPropertyNames()) {
      value = currentRecord.getProperty(fieldName);
      if (value instanceof byte[])
        value = "byte[" + ((byte[]) value).length + "]";
      else if (value instanceof Iterator<?>) {
        final List<Object> coll = new ArrayList<>();
        while (((Iterator<?>) value).hasNext())
          coll.add(((Iterator<?>) value).next());
        value = coll;
      } else if (MultiValue.isMultiValue(value)) {
        value = TableFormatter.getPrettyFieldMultiValue(MultiValue.getMultiValueIterator(value), maxMultiValueEntries);
      }

      final ResultInternal row = new ResultInternal();
      resultSet.add(new RecordTableFormatter.TableRecordRow(row));

      row.setProperty("NAME", fieldName);
      row.setProperty("VALUE", value);
    }

    final TableFormatter formatter = new TableFormatter(this::output);
    formatter.setMaxWidthSize(maxWidth);
    formatter.writeRows(resultSet, -1);
  }

  private void executeSQL(final String line) {
    checkDatabaseIsOpen();

    final long beginTime = System.currentTimeMillis();

    if (remoteDatabase != null)
      resultSet = remoteDatabase.command(language, line);
    else
      resultSet = localDatabase.command(language, line);

    final long elapsed;

    Boolean expandOnThisQuery = expandResultSet;

    Result first = null;
    if (resultSet.hasNext()) {
      first = resultSet.next();

      if (expandOnThisQuery == null && !resultSet.hasNext())
        // AUTO MODE, EXPAND THE ONLY RECORD FOUND
        expandOnThisQuery = true;
    }

    if (expandOnThisQuery == null)
      expandOnThisQuery = false;

    if (expandOnThisQuery) {
      // EXPAND THE RECORD
      if (first != null) {
        printRecord(first);

        for (int i = 0; resultSet.hasNext(); ++i) {
          printRecord(resultSet.next());
          if (limit > -1 && i > limit)
            break;
        }
      }

      elapsed = System.currentTimeMillis() - beginTime;

    } else {
      // TABLE FORMAT
      final TableFormatter table = new TableFormatter(this::output);
      table.setMaxWidthSize(maxWidth);
      table.setPrefixedColumns("#", "@RID", "@TYPE");

      final List<RecordTableFormatter.TableRecordRow> list = new ArrayList<>();

      if (first != null) {
        list.add(new RecordTableFormatter.TableRecordRow(first));

        while (resultSet.hasNext()) {
          list.add(new RecordTableFormatter.TableRecordRow(resultSet.next()));

          if (limit > -1 && list.size() > limit)
            break;
        }
      }

      elapsed = System.currentTimeMillis() - beginTime;

      table.writeRows(list, limit);
    }

    outputLine("Command executed in %dms", elapsed);
  }

  private void executeLoad(final String fileName) throws IOException {
    if (fileName.isEmpty())
      throw new ArcadeDBException("File name is empty");

    final File file = new File(fileName);
    if (!file.exists())
      throw new ArcadeDBException("File name '" + fileName + "' not found");

    try (final BufferedReader bufferedReader = new BufferedReader(new FileReader(file, DatabaseFactory.getDefaultCharset()))) {
      while (bufferedReader.ready())
        parse(bufferedReader.readLine(), true);
    }
  }

  public boolean parse(final String line, final boolean printCommand) throws IOException {
    final ParsedLine parsed = parser.parse(line, 0);

    for (String w : parsed.words()) {
      if (printCommand)
        output(getPrompt() + w);

      if (!execute(w))
        return false;
    }
    return true;
  }

  private void outputLine(final String text, final Object... args) {
    output("\n" + text, args);
  }

  private void output(final String text, final Object... args) {
    if (verboseLevel < 1)
      return;

    if (output != null)
      output.onOutput(String.format(text, args));
    else
      terminal.writer().printf(text, args);
  }

  private void executeInfo(final String subject) {
    if (subject == null || subject.isEmpty())
      return;

    checkDatabaseIsOpen();

    if (subject.equalsIgnoreCase("types")) {
      outputLine("AVAILABLE TYPES");

      final TableFormatter table = new TableFormatter(this::output);
      table.setMaxWidthSize(maxWidth);

      if (remoteDatabase != null) {
        executeSQL("select from schema:types");
        return;
      }

      final List<TableFormatter.TableMapRow> rows = new ArrayList<>();
      for (DocumentType type : localDatabase.getSchema().getTypes()) {
        final TableFormatter.TableMapRow row = new TableFormatter.TableMapRow();
        row.setField("NAME", type.getName());

        final byte kind = type.getType();
        if (kind == Document.RECORD_TYPE)
          row.setField("TYPE", "Document");
        else if (kind == Vertex.RECORD_TYPE)
          row.setField("TYPE", "Vertex");
        else if (kind == Edge.RECORD_TYPE)
          row.setField("TYPE", "Edge");

        row.setField("SUPER TYPES", type.getSuperTypes());
        row.setField("BUCKETS", type.getBuckets(false));
        row.setField("PROPERTIES", type.getPropertyNames());
        row.setField("SYNC STRATEGY", type.getBucketSelectionStrategy());

        rows.add(row);
      }

      table.writeRows(rows, -1);
    } else if (subject.equalsIgnoreCase("transaction"))
      executeTransactionStatus();
    else
      throw new ConsoleException("Information about '" + subject + "' is not available");
  }

  private void executeHelp() {
    outputLine("HELP:");
    outputLine("begin                                             -> begins a new transaction");
    outputLine("check database                                    -> check database integrity");
    outputLine("close |<path>|remote:<url> <user> <pw>            -> closes the database");
    outputLine("commit                                            -> commits current transaction");
    outputLine("connect <path>|remote:<url> <user> <pw>           -> connects to a database");
    outputLine("create database <path>|remote:<url> <user> <pw>   -> creates a new database");
    outputLine("create user <user> identified by <pw> [grant connect to <db>*] -> creates a user");
    outputLine("drop database <path>|remote:<url> <user> <pw>     -> deletes a database");
    outputLine("drop user <user>                                  -> deletes a user");
    outputLine("help|?                                            -> ask for this help");
    outputLine("info types                                        -> prints available types");
    outputLine("info transaction                                  -> prints current transaction");
    outputLine("load <path>                                       -> runs local script");
    outputLine("rollback                                          -> rolls back current transaction");
    outputLine("set language = sql|sqlscript|cypher|gremlin|mongo -> sets console query language");
    outputLine("-- <comment>                                      -> comment (no operation)");
    outputLine("quit|exit                                         -> exits from the console");
  }

  private void checkDatabaseIsOpen() {
    if (localDatabase == null && remoteDatabase == null)
      throw new ArcadeDBException("No active database. Open a database first");
  }

  private void connectToRemoteServer(final String url) {
    final String conn = url.startsWith(REMOTE_PREFIX + "//") ? url.substring((REMOTE_PREFIX + "//").length()) : url.substring(REMOTE_PREFIX.length());

    final String[] serverUserPassword = conn.split(" ");
    if (serverUserPassword.length != 3)
      throw new ConsoleException("URL username and password are missing");

    final String[] serverParts = serverUserPassword[0].split("/");
    if (serverParts.length != 2)
      throw new ConsoleException("Remote URL '" + url + "' not valid");

    String remoteServer;
    int remotePort;

    final int portPos = serverParts[0].indexOf(":");
    if (portPos < 0) {
      remoteServer = serverParts[0];
      remotePort = RemoteDatabase.DEFAULT_PORT;
    } else {
      remoteServer = serverParts[0].substring(0, portPos);
      remotePort = Integer.parseInt(serverParts[0].substring(portPos + 1));
    }

    remoteDatabase = new RemoteDatabase(remoteServer, remotePort, serverParts[1], serverUserPassword[1], serverUserPassword[2]);
  }

  private void flushOutput() {
    terminal.writer().flush();
  }

  private void outputError(final Exception e) throws IOException {
    if (verboseLevel > 1) {
      try (ByteArrayOutputStream out = new ByteArrayOutputStream(); PrintWriter writer = new PrintWriter(out)) {
        e.printStackTrace(writer);
        writer.flush();
        output("\nERROR:\n" + out + "\n");
      }
    } else
      output("\nERROR: " + e.getMessage() + "\n");
  }

  private String getPrompt() {
    return String.format(PROMPT, localDatabase != null ? "{" + localDatabase.getName() + "}" : "");
  }
}

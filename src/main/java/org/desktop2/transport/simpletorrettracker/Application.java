package org.desktop2.transport.simpletorrettracker;

import static java.lang.StringTemplate.STR;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

public class Application {

  private static final List<String> ROLES = List.of("TRACKER", "SEEDER");

  @SneakyThrows
  public static void main(String[] args) {
    if (args.length != 2 && args.length != 3) {
      System.out.println("Wrong numbers of arguments.");
      System.exit(0);
    }

    val role = args[0];

    if (!ROLES.contains(role)) {
      System.out.println(STR."First arg should be role. Possible values are: \{ROLES}.");
      System.exit(0);
    }

    val downloadsFolderPath = args[1];

    Path directoryToWatch = Paths.get(downloadsFolderPath);
    if (!Files.exists(directoryToWatch)) {
      System.out.println(STR."Torrent file with given name(\{downloadsFolderPath}) does not exist");
      System.exit(0);
    }

    String announceUrl = null;

    if (role.equalsIgnoreCase("SEEDER")) {
      if (args.length != 3) {
        System.out.println("Please provide link to torrent tracker announcement");
        System.exit(0);
      }

      if (isValidURL(args[2]) && isUrlReachable(args[2])) {
        announceUrl = args[2];
      } else {
        System.out.println("torrent tracker announcement is correct or reachable");
        System.exit(0);
      }
    }

    val portAsString = System.getenv("PORT");

    if (portAsString == null) {
      System.out.println("PORT is not defined in environment");
      System.exit(0);
    }

    if (!StringUtils.isNumeric(portAsString)) {
      System.out.println("PORT is defined improperly");
      System.exit(0);
    }

    int port = Integer.parseInt(portAsString);

    InetAddress inetAddress = InetAddress.getLocalHost();

    DirectoryAwareTracker tracker =
        new DirectoryAwareTracker(
            new InetSocketAddress(inetAddress.getHostAddress(), port), directoryToWatch);

    tracker.watch();

    // Add shutdown hook to stop watching when the application exits
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  tracker.stopWatching();
                  tracker.stop();
                }));
  }

  public static boolean isUrlReachable(String url) {
    try {
      URL urlObj = new URL(url);
      urlObj.openStream().close();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isValidURL(String url) {
    try {
      new URL(url);
      return true;
    } catch (MalformedURLException e) {
      return false;
    }
  }
}

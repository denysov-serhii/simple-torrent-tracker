package org.desktop2.transport.simpletorrettracker;

import static org.desktop2.transport.simpletorrettracker.SneakyThrowsFactory.sneakyThrows;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.val;

public class DirectoryAwareTracker extends Tracker {
  private final DirectoryWatcher watcher;
  private final Path pathForTorrentFiles;

  DirectoryAwareTracker(InetSocketAddress address, Path directoryToWatch) throws IOException {
    super(address);

    pathForTorrentFiles = Files.createTempDirectory("torrent-testing-");

    System.out.println(pathForTorrentFiles + " path is used to store .torrent files");

    val filesToSeed = new ArrayList<File>();

    File parent = new File(directoryToWatch.toString());
    for (File f : parent.listFiles()) {
      if (f.getName().equals(".DS_Store")) {
        continue;
      }
      startTrackingFile(address, f.toPath()).ifPresent(filesToSeed::add);
    }

    start();

    filesToSeed.forEach(
        sneakyThrows(
            f -> {
              SharedTorrent sharedTorrent = SharedTorrent.fromFile(f, directoryToWatch.toFile());
              Client seeder = new Client(InetAddress.getLocalHost(), sharedTorrent);
              System.out.println("====== Client starting sharing ...");
              seeder.share();
            }));

    watcher =
        DirectoryWatcher.builder()
            .path(directoryToWatch)
            .listener(
                event -> {
                  switch (event.eventType()) {
                    case DirectoryChangeEvent.EventType.CREATE:
                      startTrackingFile(address, event.path());
                      break;
                  }
                })
            .build();
  }

  private Optional<File> startTrackingFile(InetSocketAddress address, Path newFilePath) {
    System.out.println(STR."New file added to tracking \{newFilePath}");
    File newFile = new File(newFilePath.toString());
    try {
      String torrentPath =
          STR."\{pathForTorrentFiles.toString()}\{File.separator}\{newFile.getName()}.torrent";

      System.out.println(STR."FILE SHOULD BE SAVED into this path: \{torrentPath}");

      val torrentFile = new File(torrentPath);
      String announceUrl = STR."http://\{address.getHostName()}:\{address.getPort()}/announce";

      System.out.println(STR."====== Announce url: \{announceUrl}");
      val torrent = Torrent.create(newFile, new URI(announceUrl), "Simple Torrent Tracker");
      FileOutputStream fos = new FileOutputStream(torrentFile);
      torrent.save(fos);
      fos.close();

      announce(TrackedTorrent.load(torrentFile));

      TimeUnit.SECONDS.sleep(2);

      System.out.println(STR."====== Torrent file should be created: \{torrentFile.getPath()}");
      System.out.println(
          STR."====== Torrent file should be in folder: \{pathForTorrentFiles.toString()}");

      System.out.println("START SEEDING file  ...");

      return Optional.of(torrentFile);

    } catch (Exception e) {
      System.err.println(STR."ERROR: \{e.getMessage()}");
      // Handle exception
    }

    return Optional.empty();
  }

  public void stopWatching() {
    try {
      watcher.close();
      Files.walk(pathForTorrentFiles)
          .peek(path -> System.out.println(STR."Deleting \{path.toString()}"))
          .forEach(sneakyThrows(Files::delete));

      Files.delete(pathForTorrentFiles);
    } catch (Exception e) {
      // Handle exception
    }
  }

  public void watch() {
    watcher.watch();
  }
}

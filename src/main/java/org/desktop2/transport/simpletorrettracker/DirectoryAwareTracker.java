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
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;

import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectoryAwareTracker extends Tracker {
  private final DirectoryWatcher watcher;
  private final Path folderToSaveTorrentFiles;

  private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryAwareTracker.class);

  DirectoryAwareTracker(InetSocketAddress address, Path directoryToWatch, Path folderToSaveTorrentFiles) throws IOException {
    super(address);

    this.folderToSaveTorrentFiles = folderToSaveTorrentFiles;

    LOGGER.info(folderToSaveTorrentFiles + " path is used to store .torrent files");

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
              Client seeder = new Client(address.getAddress(), sharedTorrent);
              LOGGER.info("====== Client starting sharing ...");
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
    LOGGER.info(STR."New file added to tracking \{newFilePath}");
    File newFile = new File(newFilePath.toString());
    try {
      String torrentPath =
          STR."\{folderToSaveTorrentFiles.toString()}\{File.separator}\{newFile.getName()}.torrent";

      LOGGER.info(STR."FILE SHOULD BE SAVED into this path: \{torrentPath}");

      val torrentFile = new File(torrentPath);
      String announceUrl =
          STR."http://\{address.getAddress().getHostAddress()}:\{address.getPort()}/announce";

      LOGGER.info(STR."====== Announce url: \{announceUrl}");
      val torrent = Torrent.create(newFile, new URI(announceUrl), "Simple Torrent Tracker");
      FileOutputStream fos = new FileOutputStream(torrentFile);
      torrent.save(fos);
      fos.close();

      announce(TrackedTorrent.load(torrentFile));


      LOGGER.info(STR."====== Torrent file should be created: \{torrentFile.getPath()}");
      LOGGER.info(
          STR."====== Torrent file should be in folder: \{folderToSaveTorrentFiles.toString()}");

      return Optional.of(torrentFile);

    } catch (Exception e) {
      LOGGER.info(STR."ERROR: \{e.getMessage()}");
      // Handle exception
    }

    return Optional.empty();
  }

  public void stopWatching() {
    try {
      watcher.close();
      Files.walk(folderToSaveTorrentFiles)
          .peek(path -> LOGGER.info(STR."Deleting \{path.toString()}"))
          .forEach(sneakyThrows(Files::delete));

      Files.delete(folderToSaveTorrentFiles);
    } catch (Exception e) {
      // Handle exception
    }
  }

  public void watch() {
    watcher.watch();
  }
}

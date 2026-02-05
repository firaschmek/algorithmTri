package tn.facturation.ttn.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tn.facturation.ttn.config.AppProperties;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileWatcherService {

    private final AppProperties config;
    private final FactureProcessorService processor;

    private WatchService watchService;
    private volatile boolean running = false;
    private Thread watcherThread;

    @PostConstruct
    public void start() {
        try {
            createFoldersIfNeeded();
            processExistingFiles();
            startWatching();
            log.info("FileWatcher démarré - Surveillance: {}", config.getFolders().getInput());
        } catch (IOException e) {
            log.error("Erreur démarrage FileWatcher", e);
        }
    }

    private void createFoldersIfNeeded() throws IOException {
        Files.createDirectories(Paths.get(config.getFolders().getInput()));
        Files.createDirectories(Paths.get(config.getFolders().getOutput()));
        Files.createDirectories(Paths.get(config.getFolders().getTtnSigned()));
        Files.createDirectories(Paths.get(config.getFolders().getQrcode()));
        Files.createDirectories(Paths.get(config.getFolders().getErrors()));
        Files.createDirectories(Paths.get(config.getFolders().getArchive()));
    }

    private void processExistingFiles() {
        File inputDir = new File(config.getFolders().getInput());
        File[] files = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));

        if (files != null && files.length > 0) {
            log.info("Traitement de {} fichier(s) existant(s)", files.length);
            for (File file : files) {
                processor.processInvoice(file);
            }
        }
    }

    private void startWatching() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        Path inputPath = Paths.get(config.getFolders().getInput());

        inputPath.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );

        running = true;
        watcherThread = new Thread(this::watchLoop, "FileWatcher");
        watcherThread.setDaemon(false);
        watcherThread.start();
    }

    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.poll(
                        config.getWatcher().getIntervalSeconds(),
                        TimeUnit.SECONDS
                );

                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path filename = pathEvent.context();

                    if (filename.toString().toLowerCase().endsWith(".xml")) {
                        File file = new File(config.getFolders().getInput(), filename.toString());
                        waitForFileStability(file);
                        processor.processInvoice(file);
                    }
                }

                key.reset();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Erreur surveillance fichiers", e);
            }
        }
    }

    private void waitForFileStability(File file) {
        long previousSize = -1;
        long currentSize = file.length();
        long startTime = System.currentTimeMillis();
        long timeout = config.getWatcher().getFileStabilityTimeoutMs();

        while (currentSize != previousSize &&
                (System.currentTimeMillis() - startTime) < timeout) {
            try {
                Thread.sleep(500);
                previousSize = currentSize;
                currentSize = file.length();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.watcher.interval-seconds}000", initialDelay = 60000)
    public void checkPendingFiles() {
        File inputDir = new File(config.getFolders().getInput());
        File[] files = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));

        if (files != null && files.length > 0) {
            log.debug("Vérification: {} fichier(s) en attente", files.length);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
                log.info("FileWatcher arrêté");
            } catch (IOException e) {
                log.error("Erreur arrêt FileWatcher", e);
            }
        }
    }
}
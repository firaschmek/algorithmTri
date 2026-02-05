package tn.facturation.ttn.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import tn.facturation.ttn.config.AppProperties;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class FactureProcessorService {

    private final AppProperties config;
    private final XmlValidationService validationService;
    private final Pkcs11XmlSignatureService signatureService;
    private final TtnSoapClientService ttnClient;
    private final QrCodeService qrCodeService;
    private final StatusService statusService;
    private final MeterRegistry meterRegistry;

    private Counter successCounter;
    private Counter failureCounter;
    private Timer processingTimer;

    public FactureProcessorService(
            AppProperties config,
            XmlValidationService validationService,
            Pkcs11XmlSignatureService signatureService,
            TtnSoapClientService ttnClient,
            QrCodeService qrCodeService,
            StatusService statusService,
            MeterRegistry meterRegistry) {

        this.config = config;
        this.validationService = validationService;
        this.signatureService = signatureService;
        this.ttnClient = ttnClient;
        this.qrCodeService = qrCodeService;
        this.statusService = statusService;
        this.meterRegistry = meterRegistry;

        initMetrics();
    }

    private void initMetrics() {
        this.successCounter = Counter.builder("factures.processed.success")
                .description("Nombre de factures traitées avec succès")
                .register(meterRegistry);

        this.failureCounter = Counter.builder("factures.processed.failure")
                .description("Nombre de factures échouées")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("factures.processing.time")
                .description("Temps de traitement des factures")
                .register(meterRegistry);
    }

    public void processInvoice(File inputFile) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String invoiceNumber = extractInvoiceNumber(inputFile.getName());

        log.info("Début traitement: {}", inputFile.getName());

        try {
            log.info("Validation XSD...");
            if (!validationService.validate(inputFile)) {
                throw new Exception("XML invalide selon schema XSD");
            }

            log.info("Signature électronique...");
            File signedFile = signFile(inputFile, invoiceNumber);
            if (signedFile == null) {
                throw new Exception("Échec signature");
            }
            statusService.updateStatus(invoiceNumber, "SIGNE", "Facture signée avec succès");

            log.info("Envoi à TTN...");
            File ttnResponseFile = null;
            if (config.getTtn().isEnabled()) {
                ttnResponseFile = sendToTtn(signedFile, invoiceNumber);
                if (ttnResponseFile == null) {
                    throw new Exception("Échec envoi TTN");
                }
                statusService.updateStatus(invoiceNumber, "VALIDE_TTN", "Validé par TTN");
            } else {
                log.warn("Mode TEST: Envoi TTN désactivé");
                ttnResponseFile = signedFile;
                statusService.updateStatus(invoiceNumber, "MODE_TEST", "Mode test - TTN non configuré");
            }

            log.info("Génération QR code...");
            boolean qrGenerated = generateQrCode(ttnResponseFile, invoiceNumber);
            if (!qrGenerated) {
                log.warn("Échec génération QR code (non bloquant)");
            }

            if (config.getArchive().isAutoArchiveEnabled()) {
                archiveInvoice(invoiceNumber, signedFile, ttnResponseFile);
            }

            if (inputFile.delete()) {
                log.debug("Fichier source supprimé");
            }

            successCounter.increment();
            sample.stop(processingTimer);

            log.info("Traitement réussi: {}", invoiceNumber);

        } catch (Exception e) {
            failureCounter.increment();
            sample.stop(processingTimer);

            log.error("Échec traitement: {}", invoiceNumber, e);

            statusService.updateStatus(invoiceNumber, "ERREUR", e.getMessage());
            moveToErrors(inputFile);
        }
    }

    private File signFile(File inputFile, String invoiceNumber) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File signedFile = new File(
                    config.getFolders().getOutput(),
                    invoiceNumber + "_signed_" + timestamp + ".xml"
            );

            boolean success = signatureService.signXmlFile(inputFile, signedFile);
            return success ? signedFile : null;

        } catch (Exception e) {
            log.error("Erreur signature: {}", e.getMessage(), e);
            return null;
        }
    }

    private File sendToTtn(File signedFile, String invoiceNumber) {
        try {
            String ttnReference = ttnClient.saveEfact(signedFile, invoiceNumber);

            if (ttnReference != null) {
                File ttnFile = ttnClient.consultEfact(invoiceNumber);

                if (ttnFile != null) {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    File destination = new File(
                            config.getFolders().getTtnSigned(),
                            invoiceNumber + "_ttn_" + timestamp + ".xml"
                    );
                    Files.copy(ttnFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return destination;
                }
            }

            return null;

        } catch (Exception e) {
            log.error("Erreur envoi TTN: {}", e.getMessage(), e);
            return null;
        }
    }

    private boolean generateQrCode(File xmlFile, String invoiceNumber) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File qrFile = new File(
                    config.getFolders().getQrcode(),
                    invoiceNumber + "_qr_" + timestamp + ".png"
            );

            return qrCodeService.generateQrCode(xmlFile, qrFile);

        } catch (Exception e) {
            log.error("Erreur génération QR: {}", e.getMessage(), e);
            return false;
        }
    }

    private void archiveInvoice(String invoiceNumber, File signedFile, File ttnFile) {
        try {
            String yearMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            File archiveDir = new File(config.getFolders().getArchive(), yearMonth);
            archiveDir.mkdirs();

            File archiveFile = new File(archiveDir, invoiceNumber + "_archive.zip");

            log.debug("Facture archivée: {}", archiveFile.getName());

        } catch (Exception e) {
            log.error("Erreur archivage: {}", e.getMessage());
        }
    }

    private void moveToErrors(File inputFile) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File errorFile = new File(
                    config.getFolders().getErrors(),
                    timestamp + "_" + inputFile.getName()
            );

            FileUtils.moveFile(inputFile, errorFile);
            log.info("Fichier déplacé vers erreurs: {}", errorFile.getName());

        } catch (Exception e) {
            log.error("Erreur déplacement vers erreurs: {}", e.getMessage());
        }
    }

    private String extractInvoiceNumber(String filename) {
        return filename.replace(".xml", "").replace(".XML", "");
    }
}
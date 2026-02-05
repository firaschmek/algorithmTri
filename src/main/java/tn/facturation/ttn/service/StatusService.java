package tn.facturation.ttn.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.facturation.ttn.config.AppProperties;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusService {

    private final AppProperties config;

    public void updateStatus(String invoiceNumber, String status, String details) {
        File statusFile = new File(config.getFolders().getOutput(), invoiceNumber + ".status");

        try (PrintWriter writer = new PrintWriter(new FileWriter(statusFile))) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

            writer.println("NUMERO_FACTURE=" + invoiceNumber);
            writer.println("STATUT=" + status);
            writer.println("DESCRIPTION=" + getStatusDescription(status));
            writer.println("DATE=" + sdf.format(new Date()));

            if (details != null && !details.isEmpty()) {
                writer.println("DETAILS=" + details);
            }

            addFilePath(writer, "FICHIER_SIGNE", config.getFolders().getOutput(), invoiceNumber, ".xml");
            addFilePath(writer, "FICHIER_TTN", config.getFolders().getTtnSigned(), invoiceNumber, ".xml");
            addFilePath(writer, "FICHIER_QRCODE", config.getFolders().getQrcode(), invoiceNumber, ".png");

            log.debug("Statut mis à jour: {} - {}", invoiceNumber, status);

        } catch (IOException e) {
            log.error("Erreur écriture statut: {}", e.getMessage());
        }
    }

    public void updateStatusWithTtnRef(String invoiceNumber, String status, String ttnReference, String details) {
        File statusFile = new File(config.getFolders().getOutput(), invoiceNumber + ".status");

        try (PrintWriter writer = new PrintWriter(new FileWriter(statusFile))) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

            writer.println("NUMERO_FACTURE=" + invoiceNumber);
            writer.println("STATUT=" + status);
            writer.println("DESCRIPTION=" + getStatusDescription(status));
            writer.println("DATE=" + sdf.format(new Date()));
            writer.println("REFERENCE_TTN=" + ttnReference);

            if (details != null && !details.isEmpty()) {
                writer.println("DETAILS=" + details);
            }

            addFilePath(writer, "FICHIER_SIGNE", config.getFolders().getOutput(), invoiceNumber, ".xml");
            addFilePath(writer, "FICHIER_TTN", config.getFolders().getTtnSigned(), invoiceNumber, ".xml");
            addFilePath(writer, "FICHIER_QRCODE", config.getFolders().getQrcode(), invoiceNumber, ".png");

            log.info("Statut avec ref TTN: {} - {}", invoiceNumber, ttnReference);

        } catch (IOException e) {
            log.error("Erreur écriture statut: {}", e.getMessage());
        }
    }

    public void updateStatusJson(String invoiceNumber, String status, String ttnReference, String details) {
        File statusFile = new File(config.getFolders().getOutput(), invoiceNumber + ".json");

        try (FileWriter writer = new FileWriter(statusFile)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"numeroFacture\": \"").append(invoiceNumber).append("\",\n");
            json.append("  \"statut\": \"").append(status).append("\",\n");
            json.append("  \"description\": \"").append(escapeJson(getStatusDescription(status))).append("\",\n");
            json.append("  \"date\": \"").append(sdf.format(new Date())).append("\"");

            if (ttnReference != null && !ttnReference.isEmpty()) {
                json.append(",\n  \"referenceTTN\": \"").append(ttnReference).append("\"");
            }

            if (details != null && !details.isEmpty()) {
                json.append(",\n  \"details\": \"").append(escapeJson(details)).append("\"");
            }

            File qrFile = findFile(config.getFolders().getQrcode(), invoiceNumber, ".png");
            if (qrFile != null) {
                json.append(",\n  \"fichierQRCode\": \"")
                        .append(escapeJson(qrFile.getAbsolutePath())).append("\"");
            }

            json.append("\n}");
            writer.write(json.toString());

            log.debug("Statut JSON créé: {}", invoiceNumber);

        } catch (IOException e) {
            log.error("Erreur écriture statut JSON: {}", e.getMessage());
        }
    }

    private void addFilePath(PrintWriter writer, String key, String folder, String invoiceNumber, String extension) {
        File file = findFile(folder, invoiceNumber, extension);
        if (file != null) {
            writer.println(key + "=" + file.getAbsolutePath());
        }
    }

    private File findFile(String folder, String invoiceNumber, String extension) {
        File dir = new File(folder);
        if (!dir.exists()) {
            return null;
        }

        File[] files = dir.listFiles((d, name) ->
                name.contains(invoiceNumber) && name.endsWith(extension)
        );

        return (files != null && files.length > 0) ? files[0] : null;
    }

    private String getStatusDescription(String status) {
        return switch (status) {
            case "EN_ATTENTE" -> "Facture en attente de traitement";
            case "EN_COURS" -> "Traitement en cours";
            case "SIGNE" -> "Facture signée, en attente envoi TTN";
            case "ENVOYE_TTN" -> "Envoyé à TTN, en attente réponse";
            case "VALIDE_TTN" -> "Validé par TTN, QR code généré";
            case "REJETE_TTN" -> "Rejeté par TTN";
            case "MODE_TEST" -> "Mode test - TTN non configuré";
            case "ERREUR_SIGNATURE" -> "Erreur lors de la signature";
            case "ERREUR_XML" -> "XML invalide ou mal formé";
            case "ERREUR_CERTIFICAT" -> "Problème avec le certificat";
            case "ERREUR_RESEAU" -> "Impossible de contacter TTN";
            case "ERREUR" -> "Erreur durant le traitement";
            default -> "Statut inconnu";
        };
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
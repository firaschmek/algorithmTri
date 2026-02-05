package tn.facturation.ttn.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tn.facturation.ttn.config.AppProperties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeService {

    private final AppProperties config;

    public boolean generateQrCode(File xmlFile, File qrOutputFile) {
        try {
            if (extractCEV(xmlFile, qrOutputFile)) {
                log.info("QR code CEV extrait");
                return true;
            }

            String qrData = extractInvoiceData(xmlFile);
            if (qrData != null) {
                return generateQrImage(qrData, qrOutputFile);
            }

            log.warn("Impossible d'extraire les données pour le QR code");
            return false;

        } catch (Exception e) {
            log.error("Erreur génération QR code: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean extractCEV(File xmlFile, File qrOutputFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            NodeList cevNodes = doc.getElementsByTagName("ReferenceCEV");
            if (cevNodes.getLength() > 0) {
                String base64Image = cevNodes.item(0).getTextContent().trim();

                byte[] imageBytes = Base64.getDecoder().decode(base64Image);
                Files.write(qrOutputFile.toPath(), imageBytes);

                log.debug("CEV extrait et sauvegardé: {} bytes", imageBytes.length);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.debug("CEV non trouvé ou erreur: {}", e.getMessage());
            return false;
        }
    }

    private String extractInvoiceData(File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            StringBuilder qrData = new StringBuilder();

            String documentId = getElementText(doc, "DocumentIdentifier");
            if (documentId != null) {
                qrData.append("FACTURE:").append(documentId).append("\n");
            }

            String refTTN = getElementText(doc, "ReferenceTTN");
            if (refTTN != null) {
                qrData.append("REF_TTN:").append(refTTN).append("\n");
            }

            String date = getElementText(doc, "DateText");
            if (date != null) {
                qrData.append("DATE:").append(formatDate(date)).append("\n");
            }

            String montantTTC = extractMontant(doc, "I-180");
            if (montantTTC != null) {
                qrData.append("MONTANT:").append(montantTTC).append(" TND\n");
            }

            String emetteur = getElementText(doc, "MessageSenderIdentifier");
            if (emetteur != null) {
                qrData.append("EMETTEUR:").append(emetteur).append("\n");
            }

            if (qrData.length() == 0) {
                qrData.append("FACTURE_TEST\n");
                qrData.append("DATE:").append(new java.text.SimpleDateFormat("dd/MM/yyyy")
                        .format(new java.util.Date())).append("\n");
                qrData.append("STATUT:En attente validation TTN");
            }

            return qrData.toString();

        } catch (Exception e) {
            log.error("Erreur extraction données facture: {}", e.getMessage());
            return null;
        }
    }

    private boolean generateQrImage(String content, File outputFile) {
        try {
            int size = config.getQrcode().getSize();

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    size,
                    size,
                    hints
            );

            Path outputPath = outputFile.toPath();
            MatrixToImageWriter.writeToPath(
                    bitMatrix,
                    config.getQrcode().getFormat(),
                    outputPath
            );

            log.debug("QR code généré: {}x{}", size, size);
            return true;

        } catch (Exception e) {
            log.error("Erreur génération image QR: {}", e.getMessage(), e);
            return false;
        }
    }

    private String getElementText(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private String extractMontant(Document doc, String amountTypeCode) {
        NodeList moaNodes = doc.getElementsByTagName("Moa");
        for (int i = 0; i < moaNodes.getLength(); i++) {
            Element moaElement = (Element) moaNodes.item(i);
            String typeCode = moaElement.getAttribute("amountTypeCode");

            if (amountTypeCode.equals(typeCode)) {
                NodeList amountNodes = moaElement.getElementsByTagName("Amount");
                if (amountNodes.getLength() > 0) {
                    return amountNodes.item(0).getTextContent().trim();
                }
            }
        }
        return null;
    }

    private String formatDate(String dateStr) {
        try {
            if (dateStr.length() == 6) {
                String day = dateStr.substring(0, 2);
                String month = dateStr.substring(2, 4);
                String year = "20" + dateStr.substring(4, 6);
                return day + "/" + month + "/" + year;
            }
            return dateStr;
        } catch (Exception e) {
            return dateStr;
        }
    }
}
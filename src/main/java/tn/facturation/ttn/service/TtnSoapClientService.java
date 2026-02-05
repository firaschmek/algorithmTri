package tn.facturation.ttn.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.facturation.ttn.config.AppProperties;

import jakarta.xml.soap.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtnSoapClientService {

    private final AppProperties config;

    public String saveEfact(File signedFile, String invoiceNumber) {
        try {
            log.info("Envoi TTN: {}", invoiceNumber);

            byte[] xmlBytes = Files.readAllBytes(signedFile.toPath());
            String base64Xml = Base64.getEncoder().encodeToString(xmlBytes);

            SOAPMessage soapRequest = createSaveEfactRequest(
                    config.getTtn().getLogin(),
                    config.getTtn().getPassword(),
                    config.getTtn().getMatricule(),
                    base64Xml
            );

            SOAPMessage soapResponse = sendSoapMessage(soapRequest);
            String result = extractSaveEfactResponse(soapResponse);

            if (result != null && (result.contains("succès") || result.contains("success") || result.contains("enregistr"))) {
                log.info("Facture acceptée par TTN");
                return extractReference(result);
            } else {
                log.error("Facture rejetée: {}", result);
                return null;
            }

        } catch (Exception e) {
            log.error("Erreur envoi TTN: {}", e.getMessage(), e);
            return null;
        }
    }

    public File consultEfact(String invoiceNumber) {
        try {
            log.info("Consultation TTN: {}", invoiceNumber);

            SOAPMessage soapRequest = createConsultEfactRequest(
                    config.getTtn().getLogin(),
                    config.getTtn().getPassword(),
                    config.getTtn().getMatricule(),
                    invoiceNumber
            );

            SOAPMessage soapResponse = sendSoapMessage(soapRequest);
            byte[] xmlContent = extractXmlContent(soapResponse);

            if (xmlContent != null && xmlContent.length > 0) {
                File tempFile = File.createTempFile("ttn_", ".xml");
                Files.write(tempFile.toPath(), xmlContent);
                log.info("Facture récupérée de TTN");
                return tempFile;
            }

            return null;

        } catch (Exception e) {
            log.error("Erreur consultation TTN: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean testConnection() {
        try {
            log.info("Test connexion TTN...");
            URL url = new URL(config.getTtn().getWsdlUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            int responseCode = conn.getResponseCode();
            boolean success = responseCode == 200;
            log.info(success ? "Connexion TTN OK" : "Connexion TTN échouée");
            return success;
        } catch (Exception e) {
            log.error("Connexion TTN échouée: {}", e.getMessage());
            return false;
        }
    }

    private SOAPMessage createSaveEfactRequest(String login, String password, String matricule, String base64Xml) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();

        String namespace = "ser";
        String namespaceURI = "http://services.elfatoura.tradenet.com.tn/";

        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration(namespace, namespaceURI);

        SOAPBody soapBody = envelope.getBody();
        SOAPElement saveEfact = soapBody.addChildElement("saveEfact", namespace);

        SOAPElement loginElement = saveEfact.addChildElement("login");
        loginElement.addTextNode(login);

        SOAPElement passwordElement = saveEfact.addChildElement("password");
        passwordElement.addTextNode(password);

        SOAPElement matriculeElement = saveEfact.addChildElement("matricule");
        matriculeElement.addTextNode(matricule);

        SOAPElement documentElement = saveEfact.addChildElement("documentEfact");
        documentElement.addTextNode(base64Xml);

        soapMessage.saveChanges();
        return soapMessage;
    }

    private SOAPMessage createConsultEfactRequest(String login, String password, String matricule, String invoiceNumber) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();

        String namespace = "ser";
        String namespaceURI = "http://services.elfatoura.tradenet.com.tn/";

        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration(namespace, namespaceURI);

        SOAPBody soapBody = envelope.getBody();
        SOAPElement consultEfact = soapBody.addChildElement("consultEfact", namespace);

        SOAPElement loginElement = consultEfact.addChildElement("login");
        loginElement.addTextNode(login);

        SOAPElement passwordElement = consultEfact.addChildElement("password");
        passwordElement.addTextNode(password);

        SOAPElement matriculeElement = consultEfact.addChildElement("matricule");
        matriculeElement.addTextNode(matricule);

        SOAPElement criteriaElement = consultEfact.addChildElement("efactCriteria");
        SOAPElement docNumberElement = criteriaElement.addChildElement("documentNumber");
        docNumberElement.addTextNode(invoiceNumber);

        soapMessage.saveChanges();
        return soapMessage;
    }

    private SOAPMessage sendSoapMessage(SOAPMessage soapRequest) throws Exception {
        URL endpoint = new URL(config.getTtn().getEndpoint());
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        connection.setDoOutput(true);
        connection.setConnectTimeout(config.getTtn().getConnectionTimeoutMs());
        connection.setReadTimeout(config.getTtn().getReadTimeoutMs());

        try (OutputStream os = connection.getOutputStream()) {
            soapRequest.writeTo(os);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("HTTP " + responseCode);
        }

        MessageFactory messageFactory = MessageFactory.newInstance();
        try (InputStream is = connection.getInputStream()) {
            return messageFactory.createMessage(null, is);
        }
    }

    private String extractSaveEfactResponse(SOAPMessage soapResponse) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        soapResponse.writeTo(out);
        String response = out.toString("UTF-8");

        SOAPBody body = soapResponse.getSOAPBody();
        if (body.hasFault()) {
            SOAPFault fault = body.getFault();
            return "Erreur: " + fault.getFaultString();
        }

        return response;
    }

    private byte[] extractXmlContent(SOAPMessage soapResponse) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            soapResponse.writeTo(out);
            String response = out.toString("UTF-8");

            if (response.contains("<xmlContent>")) {
                int start = response.indexOf("<xmlContent>") + 12;
                int end = response.indexOf("</xmlContent>");
                String base64Content = response.substring(start, end).trim();
                return Base64.getDecoder().decode(base64Content);
            }

            return null;
        } catch (Exception e) {
            log.error("Erreur extraction XML: {}", e.getMessage());
            return null;
        }
    }

    private String extractReference(String response) {
        try {
            if (response.contains("idSaveEfact")) {
                int start = response.indexOf("<idSaveEfact>") + 13;
                int end = response.indexOf("</idSaveEfact>");
                if (start > 0 && end > start) {
                    return response.substring(start, end).trim();
                }
            }
            return "REF_" + System.currentTimeMillis();
        } catch (Exception e) {
            return "REF_" + System.currentTimeMillis();
        }
    }
}
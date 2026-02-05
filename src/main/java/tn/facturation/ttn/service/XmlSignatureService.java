package tn.facturation.ttn.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.Init;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.c14n.Canonicalizer;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import tn.facturation.ttn.config.AppProperties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.TimeZone;

@Slf4j
@Service
@RequiredArgsConstructor
public class XmlSignatureService {

    private final AppProperties config;

    private KeyStore keyStore;
    private PrivateKey privateKey;
    private X509Certificate certificate;
    private boolean initialized = false;

    public boolean signXmlFile(File inputFile, File outputFile) {
        if (!ensureInitialized()) {
            log.warn("Mode TEST: Signature MOCK utilisée (certificat non configuré)");
            return signMock(inputFile, outputFile);
        }

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(inputFile);

            Element root = doc.getDocumentElement();
            XMLSignature signature = new XMLSignature(
                    doc,
                    "",
                    XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                    Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            );
            signature.setId("SigFrs");

            root.appendChild(signature.getElement());

            Transforms transforms = new Transforms(doc);

            transforms.addTransform(Transforms.TRANSFORM_XPATH);
            Element xpathElement = doc.createElementNS(
                    "http://www.w3.org/2000/09/xmldsig#",
                    "ds:XPath"
            );
            xpathElement.setTextContent("not(ancestor-or-self::ds:Signature) and not(ancestor-or-self::RefTtnVal)");
            transforms.item(0).getElement().appendChild(xpathElement);

            transforms.addTransform(Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

            signature.addDocument("", transforms, "http://www.w3.org/2001/04/xmlenc#sha256");

            addXAdESElements(doc, signature);

            signature.addKeyInfo(certificate);

            signature.sign(privateKey);

            saveDocument(doc, outputFile);

            log.info("Fichier signé avec succès");
            return true;

        } catch (Exception e) {
            log.error("Erreur signature: {}", e.getMessage(), e);
            return false;
        }
    }

    private void addXAdESElements(Document doc, XMLSignature signature) {
        try {
            String xadesNS = "http://uri.etsi.org/01903/v1.3.2#";

            Element qualifyingProps = doc.createElementNS(xadesNS, "xades:QualifyingProperties");
            qualifyingProps.setAttribute("Target", "#SigFrs");

            Element signedProps = doc.createElementNS(xadesNS, "xades:SignedProperties");
            signedProps.setAttribute("Id", "xades-SigFrs");

            Element signedSigProps = doc.createElementNS(xadesNS, "xades:SignedSignatureProperties");

            Element signingTime = doc.createElementNS(xadesNS, "xades:SigningTime");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            signingTime.setTextContent(sdf.format(new Date()));
            signedSigProps.appendChild(signingTime);

            Element signingCert = doc.createElementNS(xadesNS, "xades:SigningCertificateV2");
            Element cert = doc.createElementNS(xadesNS, "xades:Cert");

            Element certDigest = doc.createElementNS(xadesNS, "xades:CertDigest");
            Element digestMethod = doc.createElementNS("http://www.w3.org/2000/09/xmldsig#", "ds:DigestMethod");
            digestMethod.setAttribute("Algorithm", "http://www.w3.org/2000/09/xmldsig#sha1");
            certDigest.appendChild(digestMethod);

            Element digestValue = doc.createElementNS("http://www.w3.org/2000/09/xmldsig#", "ds:DigestValue");
            digestValue.setTextContent(calculateCertDigest(certificate));
            certDigest.appendChild(digestValue);
            cert.appendChild(certDigest);

            signingCert.appendChild(cert);
            signedSigProps.appendChild(signingCert);

            Element sigPolicy = doc.createElementNS(xadesNS, "xades:SignaturePolicyIdentifier");
            Element sigPolicyId = doc.createElementNS(xadesNS, "xades:SignaturePolicyId");
            Element sigPolId = doc.createElementNS(xadesNS, "xades:SigPolicyId");

            Element identifier = doc.createElementNS(xadesNS, "xades:Identifier");
            identifier.setAttribute("Qualifier", "OIDasURN");
            identifier.setTextContent("urn:2.16.788.1.2.1");
            sigPolId.appendChild(identifier);

            Element description = doc.createElementNS(xadesNS, "xades:Description");
            description.setTextContent("Politique de signature de la facture electronique");
            sigPolId.appendChild(description);

            sigPolicyId.appendChild(sigPolId);
            sigPolicy.appendChild(sigPolicyId);
            signedSigProps.appendChild(sigPolicy);

            Element signerRole = doc.createElementNS(xadesNS, "xades:SignerRoleV2");
            Element claimedRoles = doc.createElementNS(xadesNS, "xades:ClaimedRoles");
            Element claimedRole = doc.createElementNS(xadesNS, "xades:ClaimedRole");
            claimedRole.setTextContent("CEO");
            claimedRoles.appendChild(claimedRole);
            signerRole.appendChild(claimedRoles);
            signedSigProps.appendChild(signerRole);

            signedProps.appendChild(signedSigProps);
            qualifyingProps.appendChild(signedProps);

            Element objectElement = doc.createElementNS(
                    "http://www.w3.org/2000/09/xmldsig#",
                    "ds:Object"
            );
            objectElement.appendChild(qualifyingProps);
            signature.getElement().appendChild(objectElement);

        } catch (Exception e) {
            log.error("Erreur ajout XAdES: {}", e.getMessage(), e);
        }
    }

    private boolean ensureInitialized() {
        if (initialized) {
            return true;
        }

        Init.init();

        String certPath = config.getCertificate().getPath();
        String password = config.getCertificate().getPassword();

        if (certPath == null || certPath.isEmpty()) {
            log.warn("Certificat non configuré");
            return false;
        }

        try {
            File certFile = new File(certPath);
            if (!certFile.exists()) {
                log.error("Certificat introuvable: {}", certPath);
                return false;
            }

            keyStore = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(certFile)) {
                keyStore.load(fis, password.toCharArray());
            }

            String alias = config.getCertificate().getAlias();
            if (alias == null || alias.isEmpty()) {
                Enumeration<String> aliases = keyStore.aliases();
                if (aliases.hasMoreElements()) {
                    alias = aliases.nextElement();
                }
            }

            if (alias != null) {
                privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
                certificate = (X509Certificate) keyStore.getCertificate(alias);

                if (privateKey != null && certificate != null) {
                    log.info("Certificat chargé");
                    log.info("  Sujet: {}", certificate.getSubjectX500Principal());
                    log.info("  Valide jusqu'au: {}", certificate.getNotAfter());

                    certificate.checkValidity();

                    initialized = true;
                    return true;
                }
            }

            log.error("Impossible de charger le certificat");
            return false;

        } catch (Exception e) {
            log.error("Erreur chargement certificat: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean signMock(File inputFile, File outputFile) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(inputFile);

            Element root = doc.getDocumentElement();
            Element signature = createMockSignature(doc);
            root.appendChild(signature);

            saveDocument(doc, outputFile);

            log.warn("Signature MOCK ajoutée (TESTS UNIQUEMENT)");
            return true;

        } catch (Exception e) {
            log.error("Erreur signature MOCK: {}", e.getMessage(), e);
            return false;
        }
    }

    private Element createMockSignature(Document doc) {
        String dsNS = "http://www.w3.org/2000/09/xmldsig#";

        Element signature = doc.createElementNS(dsNS, "ds:Signature");
        signature.setAttribute("Id", "SigFrs-MOCK");

        Element signedInfo = doc.createElementNS(dsNS, "ds:SignedInfo");
        Element signatureValue = doc.createElementNS(dsNS, "ds:SignatureValue");
        signatureValue.setTextContent("MOCK_SIGNATURE_FOR_TESTING_ONLY");

        signature.appendChild(signedInfo);
        signature.appendChild(signatureValue);

        return signature;
    }

    private void saveDocument(Document doc, File outputFile) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer trans = tf.newTransformer();
        trans.setOutputProperty("encoding", "UTF-8");
        trans.transform(new DOMSource(doc), new StreamResult(outputFile));
    }

    private String calculateCertDigest(X509Certificate cert) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(cert.getEncoded());
            return java.util.Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            log.error("Erreur calcul digest: {}", e.getMessage());
            return "MOCK_DIGEST";
        }
    }
}
package tn.facturation.ttn.service;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs11SignatureToken;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.facturation.ttn.config.AppProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.List;


@Slf4j
@Service
public class Pkcs11XmlSignatureService {

    private final AppProperties config;

    private Pkcs11SignatureToken token;
    private DSSPrivateKeyEntry privateKey;
    private XAdESService xadesService;
    private boolean initialized = false;

    public Pkcs11XmlSignatureService(AppProperties config) {
        this.config = config;
    }


    @PostConstruct
    public void init() {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("  Initialisation du service de signature PKCS#11 (Token SafeNet)");
        log.info("═══════════════════════════════════════════════════════════");
        ensureInitialized();
    }


    public boolean signXmlFile(File inputFile, File outputFile) {
        // Vérifier que le token est initialisé
        if (!initialized) {
            log.error("═══════════════════════════════════════════════════════════");
            log.error("  ❌ ERREUR: Token SafeNet non initialisé");
            log.error("═══════════════════════════════════════════════════════════");
            log.error("Le service de signature n'est pas prêt.");
            log.error("Vérifiez les logs au démarrage pour identifier le problème.");
            log.error("");
            log.error("Problèmes possibles:");
            log.error("  • Token SafeNet ST3 non branché sur le serveur");
            log.error("  • Pilote SafeNet non installé (eTPKCS11.dll manquant)");
            log.error("  • PIN incorrect");
            log.error("  • Certificat expiré ou non présent sur le token");
            log.error("═══════════════════════════════════════════════════════════");
            return false;
        }

        try {
            log.info("┌─────────────────────────────────────────────────────────");
            log.info("│ Signature du fichier: {}", inputFile.getName());
            log.info("└─────────────────────────────────────────────────────────");

            DSSDocument documentToSign = new FileDocument(inputFile);
            log.debug("  1. Document chargé: {} bytes", inputFile.length());

            XAdESSignatureParameters parameters = createTTNSignatureParameters();
            log.debug("  2. Paramètres XAdES configurés (Level: {}, Digest: {})",
                    parameters.getSignatureLevel(), parameters.getDigestAlgorithm());

            ToBeSigned dataToSign = xadesService.getDataToSign(documentToSign, parameters);
            log.debug("  3. Données à signer préparées");

            SignatureValue signatureValue = token.sign(
                    dataToSign,
                    parameters.getDigestAlgorithm(),
                    privateKey
            );
            log.debug("  4. Signature effectuée avec le token SafeNet");


            DSSDocument signedDocument = xadesService.signDocument(
                    documentToSign,
                    parameters,
                    signatureValue
            );
            log.debug("  5. Document XML signé créé");


            signedDocument.save(outputFile.getAbsolutePath());
            log.debug("  6. Document sauvegardé: {} bytes", outputFile.length());

            log.info("┌─────────────────────────────────────────────────────────");
            log.info("│ ✓ SIGNATURE RÉUSSIE");
            log.info("│ Fichier: {}", outputFile.getName());
            log.info("│ Taille: {} bytes", outputFile.length());
            log.info("└─────────────────────────────────────────────────────────");

            return true;

        } catch (Exception e) {
            log.error("═══════════════════════════════════════════════════════════");
            log.error("  ❌ ERREUR DE SIGNATURE");
            log.error("═══════════════════════════════════════════════════════════");
            log.error("Fichier: {}", inputFile.getName());
            log.error("Erreur: {}", e.getMessage(), e);
            log.error("");
            log.error("Causes possibles:");
            log.error("  • Token déconnecté pendant la signature");
            log.error("  • PIN expiré ou session fermée");
            log.error("  • Fichier XML mal formé");
            log.error("  • Certificat révoqué");
            log.error("═══════════════════════════════════════════════════════════");

            return false;
        }
    }


    private XAdESSignatureParameters createTTNSignatureParameters() {
        XAdESSignatureParameters parameters = new XAdESSignatureParameters();


        parameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B);

        parameters.setSignaturePackaging(SignaturePackaging.ENVELOPED);

        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);

        parameters.setSigningCertificate(privateKey.getCertificate());
        parameters.setCertificateChain(privateKey.getCertificateChain());

        return parameters;
    }


    private synchronized boolean ensureInitialized() {
        if (initialized) {
            return true;
        }

        try {
            log.info("");
            log.info("Étape 1/6: Lecture de la configuration...");
            String library = config.getCertificate().getPkcs11().getLibrary();
            String pin = config.getCertificate().getPkcs11().getPin();
            int slotIndex = config.getCertificate().getPkcs11().getSlotIndex();

            log.info("  • Bibliothèque PKCS#11: {}", library);
            log.info("  • Slot index: {}", slotIndex);
            log.info("  ✓ Configuration lue");

            log.info("");
            log.info("Étape 2/6: Vérification du pilote SafeNet...");
            File dllFile = new File(library);
            if (!dllFile.exists()) {
                log.error("");
                log.error("═══════════════════════════════════════════════════════════");
                log.error("  ❌ ERREUR: Pilote SafeNet non trouvé");
                log.error("═══════════════════════════════════════════════════════════");
                log.error("Fichier manquant: {}", library);
                log.error("");
                log.error("SOLUTION:");
                log.error("  1. Télécharger le pilote SafeNet depuis:");
                log.error("     → https://www.tuntrust.tn/fr/documents-utiles");
                log.error("  2. Chercher: 'Pilote Token SafeNet 64 Bits sur Windows 10 et Windows 11'");
                log.error("  3. Installer le pilote");
                log.error("  4. Redémarrer Windows");
                log.error("  5. Vérifier que le fichier existe:");
                log.error("     → {}", library);
                log.error("═══════════════════════════════════════════════════════════");
                return false;
            }
            log.info("  ✓ DLL PKCS#11 trouvée: {}", library);

            log.info("");
            log.info("Étape 3/6: Vérification du PIN...");
            if (pin == null || pin.trim().isEmpty()) {
                log.error("");
                log.error("═══════════════════════════════════════════════════════════");
                log.error("  ❌ ERREUR: PIN non configuré");
                log.error("═══════════════════════════════════════════════════════════");
                log.error("");
                log.error("SOLUTION:");
                log.error("  Option 1 (Recommandée): Variable d'environnement système");
                log.error("    Windows CMD:");
                log.error("      setx SAFENET_PIN \"votre_pin\" /M");
                log.error("    Puis redémarrer l'application");
                log.error("");
                log.error("  Option 2: Fichier application.properties");
                log.error("    Ajouter la ligne:");
                log.error("      app.certificate.pkcs11.pin=votre_pin");
                log.error("");
                log.error("  Le PIN est le code à 6-8 chiffres fourni avec votre token SafeNet");
                log.error("═══════════════════════════════════════════════════════════");
                return false;
            }
            log.info("  ✓ PIN configuré ({} caractères)", pin.length());

            log.info("");
            log.info("Étape 4/6: Connexion au token SafeNet...");
            token = new Pkcs11SignatureToken(
                    library,
                    () -> pin.toCharArray(),
                    slotIndex
            );
            log.info("  ✓ Token PKCS#11 connecté");

            log.info("");
            log.info("Étape 5/6: Recherche du certificat...");
            List<DSSPrivateKeyEntry> keys = token.getKeys();
            if (keys == null || keys.isEmpty()) {
                log.error("");
                log.error("═══════════════════════════════════════════════════════════");
                log.error("  ❌ ERREUR: Aucun certificat trouvé sur le token");
                log.error("═══════════════════════════════════════════════════════════");
                log.error("");
                log.error("VÉRIFICATIONS:");
                log.error("  1. Le token SafeNet ST3 (clé USB) est-il branché?");
                log.error("     → Vérifier qu'il est bien inséré dans un port USB");
                log.error("");
                log.error("  2. Le certificat TunTrust est-il installé sur le token?");
                log.error("     → Vérifier avec l'application TunSign");
                log.error("");
                log.error("  3. Le PIN est-il correct?");
                log.error("     → Tester la signature avec TunSign pour valider le PIN");
                log.error("");
                log.error("  4. Test avec TunSign:");
                log.error("     → Lancer l'application TunSign");
                log.error("     → Essayer de signer un fichier XML de test");
                log.error("     → Si TunSign fonctionne, le problème vient de la config Java");
                log.error("═══════════════════════════════════════════════════════════");
                return false;
            }

            log.info("  ✓ {} clé(s) trouvée(s) sur le token", keys.size());


            privateKey = keys.get(0);


            log.info("");
            log.info("┌─────────────────────────────────────────────────────────");
            log.info("│ Certificat TunTrust détecté:");
            log.info("│ ");
            log.info("│ Sujet: {}", privateKey.getCertificate().getSubject().getPrettyPrintRFC2253());
            log.info("│ Émetteur: {}", privateKey.getCertificate().getIssuer().getPrettyPrintRFC2253());
            log.info("│ Valide du: {}", privateKey.getCertificate().getNotBefore());
            log.info("│ Valide jusqu'au: {}", privateKey.getCertificate().getNotAfter());
            log.info("└─────────────────────────────────────────────────────────");


            log.info("");
            log.info("Étape 6/6: Initialisation du service XAdES...");
            CommonCertificateVerifier verifier = new CommonCertificateVerifier();
            xadesService = new XAdESService(verifier);
            log.info("  ✓ Service XAdES prêt");

            initialized = true;

            log.info("");
            log.info("═══════════════════════════════════════════════════════════");
            log.info("  ✓✓✓ SERVICE DE SIGNATURE OPÉRATIONNEL ✓✓✓");
            log.info("═══════════════════════════════════════════════════════════");
            log.info("");

            return true;

        } catch (Exception e) {
            log.error("");
            log.error("═══════════════════════════════════════════════════════════");
            log.error("  ❌ ERREUR D'INITIALISATION");
            log.error("═══════════════════════════════════════════════════════════");
            log.error("Erreur: {}", e.getMessage(), e);
            log.error("");
            log.error("CAUSES POSSIBLES:");
            log.error("  1. Token SafeNet ST3 non branché");
            log.error("  2. Pilote SafeNet non installé ou corrompu");
            log.error("  3. PIN incorrect (3 erreurs bloquent le token!)");
            log.error("  4. Certificat expiré ou révoqué");
            log.error("  5. Antivirus bloquant l'accès à la DLL");
            log.error("  6. Permissions insuffisantes sur le système");
            log.error("");
            log.error("ACTION IMMÉDIATE:");
            log.error("  → Testez d'abord avec TunSign (application desktop)");
            log.error("  → Si TunSign fonctionne = problème de config Java");
            log.error("  → Si TunSign échoue = problème hardware/pilote");
            log.error("═══════════════════════════════════════════════════════════");

            return false;
        }
    }


    @PreDestroy
    public void cleanup() {
        if (token != null) {
            token.close();
            log.info("═══════════════════════════════════════════════════════════");
            log.info("  ✓ Token PKCS#11 fermé proprement");
            log.info("═══════════════════════════════════════════════════════════");
        }
        initialized = false;
    }


    public boolean isReady() {
        return initialized && token != null && privateKey != null && xadesService != null;
    }


    public String getCertificateInfo() {
        if (!initialized || privateKey == null) {
            return "Service PKCS#11 non initialisé - Token SafeNet non disponible";
        }

        return String.format("Certificat: %s | Valide jusqu'au: %s",
                privateKey.getCertificate().getSubject().getPrettyPrintRFC2253(),
                privateKey.getCertificate().getNotAfter()
        );
    }


    public boolean testConnection() {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("  Test de connexion au token SafeNet");
        log.info("═══════════════════════════════════════════════════════════");

        if (!isReady()) {
            log.error("  ❌ Service non initialisé");
            return false;
        }

        log.info("  ✓ Service opérationnel");
        log.info("  {}", getCertificateInfo());
        log.info("═══════════════════════════════════════════════════════════");

        return true;
    }
}
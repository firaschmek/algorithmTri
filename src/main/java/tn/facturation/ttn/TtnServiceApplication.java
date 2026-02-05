package tn.facturation.ttn;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application principale - Service de signature et envoi des factures électroniques à TTN
 * 
 * Architecture:
 * 1. FileWatcher surveille le dossier d'entrée
 * 2. Signe les factures XML (XMLDSig/XAdES)
 * 3. Envoie à TTN via SOAP Web Service
 * 4. Génère le QR code depuis la réponse TTN
 * 5. Archive et notifie le logiciel client
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class TtnServiceApplication {

    public static void main(String[] args) {
        log.info("========================================");
        log.info("  TTN El Fatoora Service - Démarrage");
        log.info("========================================");
        
        SpringApplication.run(TtnServiceApplication.class, args);
        
        log.info("========================================");
        log.info("  Service démarré avec succès");
        log.info("  Monitoring: http://localhost:8080/actuator");
        log.info("========================================");
    }
}

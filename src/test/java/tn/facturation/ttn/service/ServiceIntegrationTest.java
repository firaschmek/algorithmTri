package tn.facturation.ttn.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import tn.facturation.ttn.config.AppProperties;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour les services principaux
 */
/*@SpringBootTest
@TestPropertySource(properties = {
    "app.ttn.enabled=false",  // Mode test
    "logging.level.tn.facturation.ttn=DEBUG"
})*/
class ServiceIntegrationTest {

   /* @Autowired
    private XmlSignatureService signatureService;

    @Autowired
    private QrCodeService qrCodeService;

    @Autowired
    private StatusService statusService;

    @Autowired
    private AppProperties config;

    @TempDir
    Path tempDir;

    private File testXmlFile;

    @BeforeEach
    void setUp() throws Exception {
        // Créer un fichier XML de test
        testXmlFile = tempDir.resolve("test_invoice.xml").toFile();
        
        String testXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TEIF controlingAgency="TTN" version="1.8.8">
              <InvoiceHeader>
                <MessageSenderIdentifier type="I-01">1234567ABC000</MessageSenderIdentifier>
                <MessageRecieverIdentifier type="I-01">7654321XYZ000</MessageRecieverIdentifier>
              </InvoiceHeader>
              <InvoiceBody>
                <Bgm>
                  <DocumentIdentifier>TEST_001</DocumentIdentifier>
                  <DocumentType code="I-11">Facture</DocumentType>
                </Bgm>
                <Dtm>
                  <DateText format="ddMMyy" functionCode="I-31">170126</DateText>
                </Dtm>
                <PartnerSection>
                  <PartnerDetails functionCode="I-62">
                    <Nad>
                      <PartnerIdentifier type="I-01">1234567ABC000</PartnerIdentifier>
                      <PartnerName nameType="Qualification">Test Supplier</PartnerName>
                    </Nad>
                  </PartnerDetails>
                </PartnerSection>
                <LinSection>
                  <Lin>
                    <ItemIdentifier>1</ItemIdentifier>
                    <LinImd lang="fr">
                      <ItemCode>PROD001</ItemCode>
                      <ItemDescription>Produit test</ItemDescription>
                    </LinImd>
                    <LinQty>
                      <Quantity measurementUnit="UNIT">1.0</Quantity>
                    </LinQty>
                    <LinTax>
                      <TaxTypeName code="I-1602">TVA</TaxTypeName>
                      <TaxDetails>
                        <TaxRate>19</TaxRate>
                      </TaxDetails>
                    </LinTax>
                    <LinMoa>
                      <MoaDetails>
                        <Moa amountTypeCode="I-171" currencyCodeList="ISO_4217">
                          <Amount currencyIdentifier="TND">100.000</Amount>
                        </Moa>
                      </MoaDetails>
                    </LinMoa>
                  </Lin>
                </LinSection>
                <InvoiceMoa>
                  <AmountDetails>
                    <Moa amountTypeCode="I-180" currencyCodeList="ISO_4217">
                      <Amount currencyIdentifier="TND">119.000</Amount>
                    </Moa>
                  </AmountDetails>
                </InvoiceMoa>
                <InvoiceTax>
                  <InvoiceTaxDetails>
                    <Tax>
                      <TaxTypeName code="I-1602">TVA</TaxTypeName>
                      <TaxDetails>
                        <TaxRate>19</TaxRate>
                      </TaxDetails>
                    </Tax>
                    <AmountDetails>
                      <Moa amountTypeCode="I-178" currencyCodeList="ISO_4217">
                        <Amount currencyIdentifier="TND">19.000</Amount>
                      </Moa>
                    </AmountDetails>
                  </InvoiceTaxDetails>
                </InvoiceTax>
              </InvoiceBody>
            </TEIF>
            """;
        
        Files.writeString(testXmlFile.toPath(), testXml);
    }

    @Test
    void testXmlSignature_MockMode() {
        // Given
        File outputFile = tempDir.resolve("signed_invoice.xml").toFile();

        // When
        boolean result = signatureService.signXmlFile(testXmlFile, outputFile);

        // Then
        assertTrue(result, "La signature devrait réussir");
        assertTrue(outputFile.exists(), "Le fichier signé devrait exister");
        assertTrue(outputFile.length() > testXmlFile.length(), "Le fichier signé devrait être plus gros");
    }

    @Test
    void testQrCodeGeneration() {
        // Given
        File qrFile = tempDir.resolve("test_qr.png").toFile();

        // When
        boolean result = qrCodeService.generateQrCode(testXmlFile, qrFile);

        // Then
        assertTrue(result, "La génération du QR code devrait réussir");
        assertTrue(qrFile.exists(), "Le fichier QR code devrait exister");
        assertTrue(qrFile.length() > 0, "Le fichier QR code ne devrait pas être vide");
    }

    @Test
    void testStatusService() {
        // Given
        String invoiceNumber = "TEST_001";

        // When
        statusService.updateStatus(invoiceNumber, "TEST", "Test status");

        // Then
        File statusFile = new File(config.getFolders().getOutput(), invoiceNumber + ".status");
        // Note: Le fichier sera créé dans le dossier configuré
        // Dans un test, on vérifie juste que la méthode ne lance pas d'exception
    }

    @Test
    void testXmlFileIsValid() {
        // Given & When
        boolean exists = testXmlFile.exists();
        long size = testXmlFile.length();

        // Then
        assertTrue(exists, "Le fichier XML de test devrait exister");
        assertTrue(size > 0, "Le fichier XML ne devrait pas être vide");
    }

    @Test
    void testSignedXmlContainsSignature() throws Exception {
        // Given
        File outputFile = tempDir.resolve("signed_invoice.xml").toFile();
        signatureService.signXmlFile(testXmlFile, outputFile);

        // When
        String content = Files.readString(outputFile.toPath());

        // Then
        assertTrue(content.contains("<ds:Signature"), "Le XML devrait contenir une signature");
        assertTrue(content.contains("SigFrs"), "La signature devrait avoir l'ID SigFrs");
    }

    @Test
    void testQrCodeIsPng() {
        // Given
        File qrFile = tempDir.resolve("test_qr.png").toFile();
        qrCodeService.generateQrCode(testXmlFile, qrFile);

        // When & Then
        assertTrue(qrFile.getName().endsWith(".png"), "Le QR code devrait être en PNG");
        assertTrue(qrFile.length() > 100, "Le QR code devrait avoir une taille raisonnable");
    }*/
}

package tn.facturation.ttn.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;

@Slf4j
@Service
public class XmlValidationService {

    private Schema schema;

    public XmlValidationService() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            ClassPathResource xsdResource = new ClassPathResource("xsd/facture_INVOIC_V1_8_8_withSig.xsd");
            this.schema = factory.newSchema(xsdResource.getURL());
            log.info("Schema XSD chargé");
        } catch (Exception e) {
            log.warn("Schema XSD non disponible: {}", e.getMessage());
        }
    }

    public boolean validate(File xmlFile) {
        if (schema == null) {
            log.debug("Validation XSD désactivée");
            return true;
        }

        try {
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xmlFile));
            log.debug("Validation XSD OK: {}", xmlFile.getName());
            return true;
        } catch (SAXException e) {
            log.error("Validation XSD échouée: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Erreur validation: {}", e.getMessage());
            return false;
        }
    }
}

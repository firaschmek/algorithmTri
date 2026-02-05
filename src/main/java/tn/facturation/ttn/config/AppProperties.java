package tn.facturation.ttn.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;


@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Folders folders = new Folders();
    private Watcher watcher = new Watcher();
    private Certificate certificate = new Certificate();
    private Ttn ttn = new Ttn();
    private Qrcode qrcode = new Qrcode();
    private Retry retry = new Retry();
    private Archive archive = new Archive();
    private MultiCompany multiCompany = new MultiCompany();

    @Data
    public static class Folders {
        @NotBlank
        private String input;
        @NotBlank
        private String output;
        @NotBlank
        private String ttnSigned;
        @NotBlank
        private String qrcode;
        @NotBlank
        private String errors;
        @NotBlank
        private String archive;
    }

    @Data
    public static class Watcher {
        private int intervalSeconds = 5;
        private long fileStabilityTimeoutMs = 2000;
    }

    @Data
    public static class Certificate {
        private String path;
        private String password;
        private String alias;

        // NOUVELLE config PKCS11 pour token SafeNet
        private Pkcs11 pkcs11 = new Pkcs11();
    }

    @Data
    public static class Pkcs11 {
        private boolean enabled = false;
        private String library = "C:\\Windows\\System32\\eTPKCS11.dll";
        private String pin;
        private int slotIndex = 0;
    }

    @Data
    public static class Ttn {
        @NotBlank
        private String wsdlUrl;
        @NotBlank
        private String endpoint;
        private String login;
        private String password;
        private String matricule;
        private int connectionTimeoutMs = 30000;
        private int readTimeoutMs = 30000;
        private boolean enabled = false;
    }

    @Data
    public static class Qrcode {
        private int size = 300;
        private String format = "PNG";
    }

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private long delayMs = 1000;
    }

    @Data
    public static class Archive {
        private boolean autoArchiveEnabled = true;
        private int archiveAfterDays = 90;
    }

    @Data
    public static class MultiCompany {
        private boolean enabled = false;
    }
}

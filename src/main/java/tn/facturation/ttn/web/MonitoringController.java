package tn.facturation.ttn.web;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.facturation.ttn.config.AppProperties;
import tn.facturation.ttn.service.TtnSoapClientService;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Contrôleur REST pour le monitoring de l'application
 * Interface web simple pour superviser le service
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MonitoringController {

    private final AppProperties config;
    private final TtnSoapClientService ttnClient;
    private final MeterRegistry meterRegistry;
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * Page d'accueil HTML du monitoring
     */
    @GetMapping(value = "/", produces = "text/html")
    public String home() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>TTN Service - Monitoring</title>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        padding: 20px;
                    }
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                    }
                    .header {
                        background: white;
                        padding: 30px;
                        border-radius: 10px;
                        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                        margin-bottom: 20px;
                        text-align: center;
                    }
                    .header h1 {
                        color: #667eea;
                        margin-bottom: 10px;
                    }
                    .header p {
                        color: #666;
                    }
                    .grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                        gap: 20px;
                        margin-bottom: 20px;
                    }
                    .card {
                        background: white;
                        padding: 25px;
                        border-radius: 10px;
                        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                    }
                    .card h2 {
                        color: #333;
                        margin-bottom: 15px;
                        font-size: 18px;
                        border-bottom: 2px solid #667eea;
                        padding-bottom: 10px;
                    }
                    .stat {
                        display: flex;
                        justify-content: space-between;
                        padding: 10px 0;
                        border-bottom: 1px solid #eee;
                    }
                    .stat:last-child {
                        border-bottom: none;
                    }
                    .stat-label {
                        color: #666;
                    }
                    .stat-value {
                        font-weight: bold;
                        color: #333;
                    }
                    .status {
                        display: inline-block;
                        padding: 5px 15px;
                        border-radius: 20px;
                        font-size: 14px;
                        font-weight: bold;
                    }
                    .status.ok {
                        background: #d4edda;
                        color: #155724;
                    }
                    .status.warning {
                        background: #fff3cd;
                        color: #856404;
                    }
                    .status.error {
                        background: #f8d7da;
                        color: #721c24;
                    }
                    .refresh-btn {
                        background: #667eea;
                        color: white;
                        border: none;
                        padding: 12px 30px;
                        border-radius: 5px;
                        cursor: pointer;
                        font-size: 16px;
                        margin-top: 20px;
                        width: 100%;
                    }
                    .refresh-btn:hover {
                        background: #5568d3;
                    }
                    .endpoint {
                        background: #f8f9fa;
                        padding: 10px;
                        border-radius: 5px;
                        margin: 10px 0;
                        font-family: monospace;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🚀 TTN El Fatoora Service</h1>
                        <p>Service de signature et envoi des factures électroniques</p>
                    </div>
                    
                    <div class="grid">
                        <div class="card">
                            <h2>📊 Vue d'ensemble</h2>
                            <div id="overview">Chargement...</div>
                        </div>
                        
                        <div class="card">
                            <h2>📁 Dossiers</h2>
                            <div id="folders">Chargement...</div>
                        </div>
                        
                        <div class="card">
                            <h2>🔗 API TTN</h2>
                            <div id="ttn">Chargement...</div>
                        </div>
                    </div>
                    
                    <div class="card">
                        <h2>📈 Métriques</h2>
                        <div id="metrics">Chargement...</div>
                    </div>
                    
                    <div class="card">
                        <h2>🔌 Endpoints disponibles</h2>
                        <div class="endpoint">GET /api/status - Statut du service</div>
                        <div class="endpoint">GET /api/metrics - Métriques détaillées</div>
                        <div class="endpoint">GET /api/health - Health check</div>
                        <div class="endpoint">GET /actuator/health - Spring Actuator</div>
                    </div>
                    
                    <button class="refresh-btn" onclick="location.reload()">🔄 Actualiser</button>
                </div>
                
                <script>
                    async function loadData() {
                        try {
                            const response = await fetch('/api/status');
                            const data = await response.json();
                            
                            // Overview
                            document.getElementById('overview').innerHTML = `
                                <div class="stat">
                                    <span class="stat-label">Statut</span>
                                    <span class="status ${data.status === 'UP' ? 'ok' : 'error'}">${data.status}</span>
                                </div>
                                <div class="stat">
                                    <span class="stat-label">Uptime</span>
                                    <span class="stat-value">${data.uptime}</span>
                                </div>
                            `;
                            
                            // Folders
                            document.getElementById('folders').innerHTML = `
                                <div class="stat">
                                    <span class="stat-label">Entrée</span>
                                    <span class="stat-value">${data.folders.input.count} fichier(s)</span>
                                </div>
                                <div class="stat">
                                    <span class="stat-label">Sortie</span>
                                    <span class="stat-value">${data.folders.output.count} fichier(s)</span>
                                </div>
                                <div class="stat">
                                    <span class="stat-label">QR Codes</span>
                                    <span class="stat-value">${data.folders.qrcode.count} fichier(s)</span>
                                </div>
                            `;
                            
                            // TTN
                            document.getElementById('ttn').innerHTML = `
                                <div class="stat">
                                    <span class="stat-label">Connexion</span>
                                    <span class="status ${data.ttn.enabled ? (data.ttn.connected ? 'ok' : 'error') : 'warning'}">
                                        ${data.ttn.enabled ? (data.ttn.connected ? 'Connecté' : 'Erreur') : 'Mode Test'}
                                    </span>
                                </div>
                                <div class="stat">
                                    <span class="stat-label">Endpoint</span>
                                    <span class="stat-value" style="font-size: 12px;">${data.ttn.endpoint || 'N/A'}</span>
                                </div>
                            `;
                            
                            // Metrics
                            const metricsResponse = await fetch('/api/metrics');
                            const metrics = await metricsResponse.json();
                            
                            document.getElementById('metrics').innerHTML = `
                                <div class="stat">
                                    <span class="stat-label">✅ Factures réussies</span>
                                    <span class="stat-value">${metrics.success || 0}</span>
                                </div>
                                <div class="stat">
                                    <span class="stat-label">❌ Factures échouées</span>
                                    <span class="stat-value">${metrics.failure || 0}</span>
                                </div>
                                <div class="stat">
                                    <span class="stat-label">⏱️ Temps moyen</span>
                                    <span class="stat-value">${metrics.avgTime || '0'}ms</span>
                                </div>
                            `;
                            
                        } catch (error) {
                            console.error('Erreur chargement données:', error);
                        }
                    }
                    
                    loadData();
                    setInterval(loadData, 10000); // Rafraîchir toutes les 10s
                </script>
            </body>
            </html>
            """;
    }

    /**
     * Status complet du service
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("status", "UP");
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        status.put("uptime", calculateUptime());
        
        // Folders
        Map<String, Object> folders = new HashMap<>();
        folders.put("input", getFolderInfo(config.getFolders().getInput()));
        folders.put("output", getFolderInfo(config.getFolders().getOutput()));
        folders.put("qrcode", getFolderInfo(config.getFolders().getQrcode()));
        folders.put("errors", getFolderInfo(config.getFolders().getErrors()));
        status.put("folders", folders);
        
        // TTN
        Map<String, Object> ttn = new HashMap<>();
        ttn.put("enabled", config.getTtn().isEnabled());
        ttn.put("endpoint", config.getTtn().getEndpoint());
        
        if (config.getTtn().isEnabled()) {
            try {
                boolean connected = ttnClient.testConnection();
                ttn.put("connected", connected);
            } catch (Exception e) {
                ttn.put("connected", false);
                ttn.put("error", e.getMessage());
            }
        }
        status.put("ttn", ttn);
        
        return ResponseEntity.ok(status);
    }

    /**
     * Métriques détaillées
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Compteurs
        metrics.put("success", meterRegistry.counter("factures.processed.success").count());
        metrics.put("failure", meterRegistry.counter("factures.processed.failure").count());
        
        // Timer
        var timer = meterRegistry.timer("factures.processing.time");
        metrics.put("count", timer.count());
        metrics.put("avgTime", timer.count() > 0 ? (long)timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0);
        metrics.put("maxTime", (long)timer.max(java.util.concurrent.TimeUnit.MILLISECONDS));
        
        return ResponseEntity.ok(metrics);
    }

    /**
     * Health check simple
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "ttn-elfatoora-service");
        health.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(health);
    }

    private Map<String, Object> getFolderInfo(String path) {
        Map<String, Object> info = new HashMap<>();
        File folder = new File(path);
        
        info.put("path", path);
        info.put("exists", folder.exists());
        
        if (folder.exists()) {
            File[] files = folder.listFiles();
            info.put("count", files != null ? files.length : 0);
        } else {
            info.put("count", 0);
        }
        
        return info;
    }

    private String calculateUptime() {
        long seconds = java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        return String.format("%dh %dm %ds", hours, minutes, secs);
    }
}

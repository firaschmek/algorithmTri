@echo off
echo Demarrage du service TTN...
echo.
echo Build du projet en cours...

call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo ERREUR: Le build a echoue!
    pause
    exit /b 1
)

echo.
echo Build termine avec succes!
echo Demarrage du service...
echo.

set JAVA_OPTS=-Xms512m -Xmx1024m -Dfile.encoding=UTF-8

java %JAVA_OPTS% -jar target\ttn-elfatoora-service-1.0.0.jar

pause
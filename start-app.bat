@echo off
set DB_PORT=3307
set DB_USERNAME=root
set DB_PASSWORD=root
cd /d "%~dp0"
mvnw.cmd spring-boot:run

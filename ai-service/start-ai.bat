@echo off
cd /d "%~dp0"
rem Must match ai.service.api-key / AI_SERVICE_API_KEY in the Spring Boot app,
rem otherwise every /predict call gets rejected with 401.
if not defined AI_SERVICE_API_KEY set AI_SERVICE_API_KEY=dev-ai-key-change-me
echo [MediScan AI] Starting AI service on 127.0.0.1:8000...
rem Bound to localhost only: this service is meant to be called by the
rem Spring Boot app on the same machine, not reachable from the network.
python -m uvicorn app:app --host 127.0.0.1 --port 8000 --reload
pause

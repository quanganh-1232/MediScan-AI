@echo off
cd /d "%~dp0"
echo [MediScan AI] Starting AI service on port 8000...
python -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload
pause

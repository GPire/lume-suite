@echo off
cd /d "%~dp0"
py -3 tvdoctor_app.py --auto || python tvdoctor_app.py --auto

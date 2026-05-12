@echo off
cd /d e:\javaprogram\AndroidApp\docs
python getpdf.py > output.txt 2>&1
type output.txt
pause

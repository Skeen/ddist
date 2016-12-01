import subprocess

proc = subprocess.Popen("pdflatex -shell-escape -synctex=-1 report.tex", shell=True)
proc.wait()

# proc = subprocess.Popen("mkdir output", shell=True)
# proc.wait()

# proc = subprocess.Popen("move report.pdf RB.pdf", shell=True)
# proc.wait()

proc = subprocess.Popen("del *.out *.log *.aux *.pyg", shell=True)
proc.wait()

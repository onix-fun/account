import sys
import os
import signal

def write_stdout(s):
    sys.stdout.write(s)
    sys.stdout.flush()

def write_stderr(s):
    sys.stderr.write(s)
    sys.stderr.flush()

def main():
    while True:
        write_stdout("READY\n")
        line = sys.stdin.readline()
        write_stderr("Event: " + line)
        
        # Kill supervisord itself
        os.kill(os.getppid(), signal.SIGTERM)
        sys.exit(0)

if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Run the real protocol client against an isolated loopback mock, and clean up."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import time

root = Path(__file__).resolve().parents[1]
os.chdir(root)
classpath = os.pathsep.join([str(root / 'out/tests'), str(root / '.cache/json-20240303.jar')])
with tempfile.TemporaryDirectory(prefix='tvremote-test-') as directory:
    work = Path(directory)
    shutil.copy2(root / 'tools/mock_tv.py', work / 'mock_tv.py')
    with (work / 'server.log').open('w+') as output:
        mock = subprocess.Popen([sys.executable, str(work / 'mock_tv.py'), '127.0.0.1'],
                                stdout=output, stderr=subprocess.STDOUT)
        try:
            for _ in range(100):
                if mock.poll() is not None:
                    raise RuntimeError('Mock exited before listening (ports 1900, 1990, 3001 must be free)')
                if 'listening' in (work / 'server.log').read_text():
                    break
                time.sleep(0.1)
            else:
                raise TimeoutError('Mock did not start within 10 seconds')
            for test, args in [('ProtocolTest', ['127.0.0.1', str(work / 'mock_tv.log')]),
                               ('WolTest', ['127.0.0.1'])]:
                subprocess.run(['java', '-cp', classpath, 'lean.tvremote.' + test, *args],
                               check=True, timeout=60)
        except BaseException:
            print((work / 'server.log').read_text(), file=sys.stderr)
            raise
        finally:
            mock.terminate()
            try:
                mock.wait(timeout=5)
            except subprocess.TimeoutExpired:
                mock.kill()
                mock.wait()

"""Integration proofs: real tools must reject real counterexamples, not broken invocations."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from quality_gates import duplication_metrics, require_duplication, QualityError

ROOT = Path(__file__).resolve().parents[2]


class ToolProofTests(unittest.TestCase):
    def test_real_ruff_and_complexipy_reject_excessive_complexity(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'.gradle-user-home') as directory:
            source = Path(directory)/'complex.py'
            source.write_text('def excessive(x):\n'+''.join(f'    if x == {n}:\n        print(x)\n' for n in range(16)))
            ruff = subprocess.run([sys.executable,'-m','ruff','check','--select','C901','--config','lint.mccabe.max-complexity=10','--output-format','json',str(source)],capture_output=True,text=True,encoding='utf-8',errors='replace')
            self.assertEqual(ruff.returncode,1,ruff.stderr)
            self.assertEqual(json.loads(ruff.stdout)[0]['code'],'C901')
            tool = Path(sys.executable).parent/('complexipy.exe' if sys.platform=='win32' else 'complexipy')
            output = Path(directory)/'complexity.json'
            result = subprocess.run([str(tool),str(source),'--max-complexity-allowed','15','--output-format','json','--output',str(output),'--cache-dir',str(ROOT/'.tools/complexipy-cache')],capture_output=True,text=True,encoding='utf-8',errors='replace')
            self.assertNotEqual(result.returncode,0)
            self.assertGreater(json.loads(output.read_text())[0]['complexity'],15)

    def test_real_python_clones_are_rejected_by_the_new_code_gate(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'.gradle-user-home') as directory:
            root = Path(directory)
            source = root/'pc/src'
            source.mkdir(parents=True)
            code = 'def total(values):\n    result = 0\n    for value in values:\n        if value > 0:\n            result += value * 2\n        else:\n            result -= value * 3\n    return result\n'
            for name in ('first.py','second.py'):
                (source/name).write_text(code)
            output = root/'clones'
            result = subprocess.run(['node',str(ROOT/'node_modules/jscpd/bin/jscpd'),source.relative_to(ROOT).as_posix(),'--format','python','--min-lines','5','--min-tokens','30','--reporters','json','--output',str(output),'--absolute'],capture_output=True,text=True,encoding='utf-8',errors='replace',cwd=ROOT)
            self.assertEqual(result.returncode,0,result.stderr)
            changed = {f'pc/src/{n}':set(range(1,9)) for n in ('first.py','second.py')}
            metrics = duplication_metrics(output/'jscpd-report.json',changed,root,'pc/src','.py')
            with self.assertRaises(QualityError):
                require_duplication(metrics['new_duplicated_lines'],metrics['new_lines'])

    def test_real_mypy_rejects_wrong_return_type_and_ruff_rejects_bad_format(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'.gradle-user-home') as directory:
            source = Path(directory)/'wrong.py'
            source.write_text('def count() -> int:\n    return "wrong"\n')
            result = subprocess.run([sys.executable,'-m','mypy','--strict','--cache-dir',str(ROOT/'.tools/mypy-cache'),str(source)],capture_output=True,text=True,encoding='utf-8',errors='replace')
            self.assertNotEqual(result.returncode,0)
            self.assertIn('[return-value]',result.stdout)
            source.write_text('def total( ):\n return  1\n')
            result = subprocess.run([sys.executable,'-m','ruff','format','--check',str(source)],capture_output=True,text=True,encoding='utf-8',errors='replace')
            self.assertEqual(result.returncode,1,result.stderr)
            self.assertIn('reformatted',result.stdout)

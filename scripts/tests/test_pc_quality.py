from email.message import Message
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from quality_gates import QualityError, duplication_metrics
from quality_pc import runtime_inventory
import quality_pc

TEMP = Path(__file__).resolve().parents[2] / '.gradle-user-home'


class RuntimeInventoryTests(unittest.TestCase):
    def metadata(self, version='1.0', license='MIT'):
        message = Message()
        message['Name'] = 'example'
        message['Version'] = version
        message['License-Expression'] = license
        return message

    def test_exact_runtime_versions_are_read_from_installed_metadata(self):
        modules, licenses = runtime_inventory('example==1.0\n# comment\n', lambda n: self.metadata())
        self.assertEqual(modules, [{'ecosystem': 'PyPI', 'name': 'example', 'version': '1.0'}])
        self.assertEqual(licenses[0]['moduleLicense'], 'MIT')

    def test_wrong_installed_version_is_rejected(self):
        with self.assertRaises(QualityError):
            runtime_inventory('example==1.1', lambda n: self.metadata())

    def test_missing_license_is_rejected(self):
        metadata = self.metadata()
        del metadata['License-Expression']
        with self.assertRaises(QualityError):
            runtime_inventory('example==1.0', lambda n: metadata)

    def test_unpinned_requirement_is_rejected(self):
        with self.assertRaises(QualityError):
            runtime_inventory('example>=1.0', lambda n: self.metadata())

    def test_empty_inventory_is_rejected(self):
        with self.assertRaises(QualityError):
            runtime_inventory('# nothing')

    def test_false_environment_marker_is_not_scanned_as_installed(self):
        modules, _ = runtime_inventory('missing==1.0; python_version < "2"\nexample==1.0', lambda n: self.metadata())
        self.assertEqual(len(modules), 1)
        self.assertEqual(modules[0]['name'], 'example')

    def test_reviewed_legacy_metadata_requires_exact_fingerprint_and_version(self):
        import hashlib, json
        metadata = self.metadata()
        del metadata['License-Expression']
        fingerprint = hashlib.sha256(json.dumps({'expression': None, 'license': None, 'classifiers': []}, sort_keys=True).encode()).hexdigest()
        overrides = {'example': {'version': '1.0', 'metadata_sha256': fingerprint, 'license': 'BSD-3-Clause'}}
        modules, licenses = runtime_inventory('example==1.0', lambda _: metadata, overrides)
        self.assertEqual(licenses[0]['moduleLicense'], 'BSD-3-Clause')
        overrides['example']['metadata_sha256'] = 'wrong'
        with self.assertRaises(QualityError):
            runtime_inventory('example==1.0', lambda _: metadata, overrides)


class PcRunnerTests(unittest.TestCase):
    def test_missing_autonomous_environment_is_reported(self):
        with tempfile.TemporaryDirectory(dir=TEMP) as directory:
            with self.assertRaisesRegex(QualityError,'environment absent'):
                quality_pc.run_checks(Path(directory),'HEAD')

    def test_failed_real_child_process_preserves_its_output(self):
        with tempfile.TemporaryDirectory(dir=TEMP) as directory:
            root=Path(directory)
            with self.assertRaisesRegex(QualityError,'exited 7'):
                quality_pc.execute(root,'failure',[sys.executable,'-c','print("evidence");raise SystemExit(7)'])
            self.assertIn('evidence',(root/'build/reports/quality/pc/failure.log').read_text())

    def test_runtime_scan_keeps_success_findings_and_network_failures(self):
        import json
        from unittest.mock import patch
        with tempfile.TemporaryDirectory(dir=TEMP) as directory:
            root=Path(directory)
            (root/'pc/config').mkdir(parents=True)
            (root/'pc/config/licenses.json').write_text(json.dumps({'allowed':['MIT'],'overrides':{}}))
            report=root/'reports';report.mkdir()
            (report/'runtime.txt').write_text('example==1.0')
            inventory=([{'ecosystem':'PyPI','name':'example','version':'1.0'}],[{'moduleName':'example','moduleLicense':'MIT'}])
            with patch.object(quality_pc,'runtime_inventory',return_value=inventory),patch.object(quality_pc,'scan_dependencies',return_value={'status':'complete','findings':[]}):
                self.assertEqual(quality_pc.check_runtime(root,report)['runtime_packages'],1)
                self.assertIn('PC runtime',json.loads((report/'osv.json').read_text())['scope'])
            with patch.object(quality_pc,'runtime_inventory',return_value=inventory),patch.object(quality_pc,'scan_dependencies',return_value={'status':'complete','findings':[{'id':'GHSA-example'}]}):
                with self.assertRaisesRegex(QualityError,'vulnerabilities'):
                    quality_pc.check_runtime(root,report)
            with patch.object(quality_pc,'runtime_inventory',return_value=inventory),patch.object(quality_pc,'scan_dependencies',side_effect=OSError('offline')):
                with self.assertRaises(OSError):
                    quality_pc.check_runtime(root,report)
                self.assertEqual(json.loads((report/'osv.json').read_text())['status'],'incomplete')

    def test_missing_or_failed_pc_summary_cannot_pass_global_quality(self):
        import json
        with tempfile.TemporaryDirectory(dir=TEMP) as directory:
            path = Path(directory)/'summary.json'
            for content in (None, {'status':'failed','metrics':{}}, {'status':'passed'}):
                if content is not None:
                    path.write_text(json.dumps(content))
                with self.assertRaises(QualityError):
                    quality_pc.read_pc_summary(path)
            path.write_text(json.dumps({'status':'passed','metrics':{'coverage': {'line':100}}}))
            self.assertEqual(quality_pc.read_pc_summary(path)['coverage']['line'],100)

    def test_tool_failure_returns_failure_and_a_summary_not_false_success(self):
        from unittest.mock import patch
        import json
        import subprocess
        with tempfile.TemporaryDirectory(dir=TEMP) as directory:
            root = Path(directory)
            with patch.object(quality_pc, 'run_checks', side_effect=subprocess.CalledProcessError(1,['ruff']), create=True):
                self.assertEqual(quality_pc.main(root, []), 1)
            report = json.loads((root/'build/reports/quality/pc/summary.json').read_text())
            self.assertEqual(report['status'], 'failed')
            self.assertIn('ruff', report['error'])


class PcDuplicationTests(unittest.TestCase):
    def test_script_scope_can_measure_top_level_production_without_test_files(self):
        import json
        with tempfile.TemporaryDirectory(dir=TEMP) as directory:
            root = Path(directory)
            (root/'scripts/tests').mkdir(parents=True)
            (root/'scripts/gate.py').write_text('print("gate")\n')
            (root/'scripts/tests/test_gate.py').write_text('print("test")\n')
            report=root/'clones.json'
            report.write_text(json.dumps({'statistics':{'total':{'sources':1,'lines':1}},'duplicates':[]}))
            try:
                metrics=duplication_metrics(report,{'scripts/gate.py':{1}},root,'scripts','.py',recursive=False)
            except TypeError:
                self.fail('Le scope des scripts doit omettre leur dossier de tests')
            self.assertEqual(metrics['analyzed_files'],1)

    def test_python_sources_are_checked_in_their_own_component(self):
        import json
        with tempfile.TemporaryDirectory(dir=TEMP) as directory:
            root = Path(directory)
            (root/'pc/src').mkdir(parents=True)
            (root/'pc/src/app.py').write_text('print("local")\n', encoding='utf-8')
            path = root/'report.json'
            path.write_text(json.dumps({'statistics': {'total': {'sources': 1, 'lines': 1}}, 'duplicates': []}))
            try:
                result = duplication_metrics(path, {'pc/src/app.py': {1}}, root, 'pc/src', '.py')
            except TypeError:
                self.fail('Le gate doit accepter un perimetre PC independant')
            self.assertEqual(result['new_lines'], 1)

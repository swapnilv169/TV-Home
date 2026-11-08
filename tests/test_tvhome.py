import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('tvhome', Path(__file__).parents[1] / 'tools/tvhome.py')
tvhome = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tvhome)


class RecoveryTests(unittest.TestCase):
    def test_preflight_rejects_old_os_but_accepts_armv7_fire_tv(self):
        for sdk, supported in [('22', False), ('24', False), ('25', True), ('28', True)]:
            tv = tvhome.TV('adb', 'fixture')
            responses = ['package:com.amazon.tv.launcher', 'AFTSS', sdk]
            with patch.object(tv, 'run', return_value='device'), patch.object(tv, 'shell', side_effect=responses):
                if supported:
                    tv.preflight()
                else:
                    with self.assertRaisesRegex(RuntimeError, 'API 25'):
                        tv.preflight()

    def test_rejects_different_device_and_core_packages(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / 'backup.json'
            tvhome.write_backup(p, {'schema': 1, 'identity': 'first', 'packages': {}})
            with self.assertRaises(ValueError):
                tvhome.read_backup(p, 'second')
            tvhome.write_backup(p, {'schema': 1, 'identity': 'first',
                                   'packages': {'com.amazon.tv.launcher': 3}})
            with self.assertRaises(ValueError):
                tvhome.read_backup(p, 'first')

    def test_all_original_states_survive_roundtrip(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / 'backup.json'
            for state in range(5):
                record = {'schema': 1, 'identity': 'test',
                          'packages': {'com.amazon.sneakpeek': state}}
                tvhome.write_backup(p, record)
                self.assertEqual(tvhome.read_backup(p, 'test'), record)

    def test_adb_always_targets_serial_and_quotes_remote_arguments(self):
        with patch.object(tvhome.subprocess, 'check_output', return_value='ok\n__TVHOME_RC:0') as proc:
            tv = tvhome.TV('custom-adb', '192.0.2.10:5555')
            self.assertEqual(tv.shell('echo', "a; touch /tmp/nope '"), 'ok')
            args = proc.call_args.args[0]
            self.assertEqual(args[:4], ['custom-adb', '-s', '192.0.2.10:5555', 'shell'])
            self.assertIn(tvhome.shlex.quote("a; touch /tmp/nope '"), args[4])

    def test_nonzero_shell_result_is_not_success(self):
        tv = tvhome.TV('adb', 'test')
        with patch.object(tv, 'run', return_value='denied\n__TVHOME_RC:1'):
            with self.assertRaisesRegex(RuntimeError, 'denied'):
                tv.shell('pm', 'disable-user', 'com.amazon.sneakpeek')

    def test_original_saved_before_failure_and_not_overwritten_on_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            backup = Path(directory) / 'tvhome-restore.json'
            state = {'value': 2, 'fail': True}

            class FakeTV:
                def __init__(self, adb, serial):
                    self.model = 'Fixture'
                    self.packages = {'com.amazon.sneakpeek'}

                def preflight(self):
                    pass

                def identity(self):
                    return 'same-device'

                def package_state(self, package):
                    return state['value']

                def set_package(self, package, value):
                    # Every attempted mutation must already have its backup on disk.
                    saved = json.loads(backup.read_text())
                    assert saved['packages'][package] == 2
                    if state['fail']:
                        raise RuntimeError('Protected package')
                    state['value'] = value

            cleanup = ['--serial', 'fixture', 'cleanup', '--packages',
                       'com.amazon.sneakpeek', '--backup', str(backup)]
            with patch.object(tvhome, 'TV', FakeTV):
                with self.assertRaisesRegex(RuntimeError, 'Protected'):
                    tvhome.main(cleanup)
                state.update(value=1, fail=False)
                tvhome.main(cleanup)
                self.assertEqual(state['value'], 3)
                tvhome.main(['--serial', 'fixture', 'restore-cleanup', '--backup', str(backup)])
                self.assertEqual(state['value'], 2)


if __name__ == '__main__':
    unittest.main()

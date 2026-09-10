"""Regression checks for report integrity and deterministic dependency selection; no SUT calls."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from analyze_run import analyze, write
from compile_tests import resolve_libraries

class HarnessTests(unittest.TestCase):
    def setUp(self):
        self.run = Path(tempfile.mkdtemp(prefix='explab2974-harness-'))
        self.addCleanup(self.clean)
        (self.run/'http-evidence').mkdir()
        self.result = {'expectedCases':1,'cases':[{'uniqueId':'case-1','name':'case one','status':'FAILED'}]}
        write(self.run/'results.json',self.result)
        self.evidence = {'uniqueId':'case-1','httpStatus':500,'contentType':'text/html','responseBodyText':'<html>error</html>'}
        write(self.run/'http-evidence/one.json',self.evidence)
        self.state = {'health':{'status':'UP'},'caches':{c:0 for c in ['splitting_object_cache','splitting_field_cache','actualization_cache']},'errors':[]}
        write(self.run/'pre-run-state.json',self.state)
        write(self.run/'post-run-state.json',self.state)

    def clean(self):
        # Each test creates only known individual files, no recursive deletion.
        for child in (self.run/'http-evidence').iterdir():
            child.unlink()
        (self.run/'http-evidence').rmdir()
        for child in self.run.iterdir():
            child.unlink()
        self.run.rmdir()

    def test_offline_analysis_handles_non_json_response_without_console_log(self):
        summary = analyze(self.run)
        self.assertEqual([],summary['infrastructureIssues'])
        self.assertEqual({500:1},summary['by_http'])
        self.assertIsNone(json.loads((self.run/'results-with-http.json').read_text())['cases'][0]['responseBody'])

    def test_missing_evidence_is_infrastructure_failure(self):
        (self.run/'http-evidence/one.json').unlink()
        summary = analyze(self.run)
        self.assertTrue(summary['infrastructureIssues'])
        self.assertEqual({'NO_RESPONSE':1},summary['by_http'])

    def test_duplicate_evidence_is_not_silently_overwritten(self):
        write(self.run/'http-evidence/two.json',self.evidence)
        self.assertTrue(any('Duplicate HTTP' in s for s in analyze(self.run)['infrastructureIssues']))

    def test_catalog_growth_is_not_fixed_to_103(self):
        old = self.run/'old.json'
        write(old,{'cases':[]})
        summary = analyze(self.run,old)
        self.assertEqual(['case one'],summary['comparison']['added'])
        self.assertEqual([],summary['infrastructureIssues'])

    def test_residual_data_is_reported_without_deleting_it(self):
        self.state['caches']['splitting_object_cache'] = 1
        write(self.run/'post-run-state.json',self.state)
        self.assertTrue(any('test data' in s for s in analyze(self.run)['infrastructureIssues']))
        self.assertEqual(1,json.loads((self.run/'post-run-state.json').read_text())['caches']['splitting_object_cache'])

    def test_incomplete_junit_execution_is_detected(self):
        self.result['expectedCases'] = 2
        write(self.run/'results.json',self.result)
        self.assertTrue(any('Incomplete' in s for s in analyze(self.run)['infrastructureIssues']))

    def test_stopped_stand_container_is_reported_even_when_http_responds(self):
        self.result['environment'] = {'images':[{'component':'kafka','status':'exited'}]}
        write(self.run/'results.json',self.result)
        self.assertTrue(any('kafka (exited)' in issue for issue in analyze(self.run)['infrastructureIssues']))

    def test_junit_reordering_preserves_comparison_identity(self):
        old = self.run/'old.json'
        write(old,{'cases':[{'uniqueId':'different-invocation','name':'case one','status':'FAILED','httpStatus':500}]})
        comparison = analyze(self.run,old)['comparison']
        self.assertEqual([],comparison['changed'])
        self.assertEqual([],comparison['added'])
        self.assertEqual([],comparison['removed'])

    def test_library_checksum_mismatch_is_rejected(self):
        (self.run/'sample.jar').write_bytes(b'changed')
        entry={'group':'g','artifact':'a','source':'bundle','path':'sample.jar','sha256':hashlib.sha256(b'original').hexdigest()}
        with self.assertRaisesRegex(ValueError,'Checksum mismatch'):
            resolve_libraries({'libraries':[entry]},{'bundle':self.run})

    def test_fixture_cleanup_requires_all_caches_and_released_lease(self):
        self.result['environment'] = {'suite':'fixture'}
        write(self.run/'results.json',self.result)
        write(self.run/'fixture-cleanup.json',{'remaining':{}})
        self.assertTrue(analyze(self.run)['infrastructureIssues'])
        remaining = {key:{'found':0} for key in [*self.state['caches'],'param_cache','data_source_cache']}
        write(self.run/'fixture-cleanup.json',{'remaining':remaining})
        self.assertTrue(analyze(self.run)['infrastructureIssues'])
        write(self.run/'fixture-manifest.json',{'status':'cleaned'})
        self.assertEqual([],analyze(self.run)['infrastructureIssues'])
        remaining['param_cache']['found'] = 1
        write(self.run/'fixture-cleanup.json',{'remaining':remaining})
        self.assertTrue(analyze(self.run)['infrastructureIssues'])

    def test_duplicate_library_versions_are_rejected(self):
        (self.run/'sample.jar').write_bytes(b'original')
        entry={'group':'g','artifact':'a','source':'bundle','path':'sample.jar','sha256':hashlib.sha256(b'original').hexdigest()}
        with self.assertRaisesRegex(ValueError,'Duplicate artifact'):
            resolve_libraries({'libraries':[entry,entry]},{'bundle':self.run})

    def test_functional_report_requires_every_fixture_to_be_cleaned(self):
        self.result['environment'] = {'suite':'functional-full', 'functionalCases':1}
        write(self.run/'results.json',self.result)
        fixtures = self.run/'fixtures'
        case = fixtures/'case-one'
        case.mkdir(parents=True)
        try:
            write(case/'fixture-manifest.json', {'status':'ready'})
            self.assertTrue(any('not cleaned' in issue for issue in analyze(self.run)['infrastructureIssues']))
            write(case/'fixture-manifest.json', {'status':'cleaned'})
            write(case/'fixture-cleanup.json', {'remaining':{key:0 for key in [*self.state['caches'],'param_cache','data_source_cache']}})
            self.assertEqual([],analyze(self.run)['infrastructureIssues'])
            self.result['environment']['functionalCases']=2
            write(self.run/'results.json',self.result)
            self.assertTrue(any('Incomplete functional' in issue for issue in analyze(self.run)['infrastructureIssues']))
        finally:
            for file in case.iterdir(): file.unlink()
            case.rmdir()
            fixtures.rmdir()

if __name__ == '__main__':
    unittest.main()

"""Offline result analysis: consumes structured evidence, never calls the running service."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def write(path, value):
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')

def analyze(run, baseline=None):
    result = read(run/'results.json')
    issues = []
    evidence = {}
    for file in sorted((run/'http-evidence').glob('*.json')):
        record = read(file)
        key = record['uniqueId']
        if key in evidence:
            issues.append('Duplicate HTTP evidence: '+key)
        evidence[key] = record
    ids = [c['uniqueId'] for c in result['cases']]
    if len(ids) != len(set(ids)):
        issues.append('Duplicate JUnit case identities')
    if len({c['name'] for c in result['cases']}) != len(ids):
        issues.append('Duplicate scenario/variant names')
    if len(ids) != result['expectedCases']:
        issues.append('Incomplete execution: expected '+str(result['expectedCases'])+', recorded '+str(len(ids)))
    if result.get('containerFailures'):
        issues.append('JUnit container failure: inspect results.json')
    for container in result.get('environment', {}).get('images', []):
        if container.get('status') != 'running':
            issues.append('Stand container not running at test start: ' + str(container.get('component'))
                          + ' (' + str(container.get('status')) + ')')
    for case in result['cases']:
        http = evidence.get(case['uniqueId'])
        if http is None:
            issues.append('No HTTP response evidence: '+case['name'])
            continue
        case.update({k:http[k] for k in ['httpStatus','contentType','responseBodyText']})
        try:
            case['responseBody'] = json.loads(http['responseBodyText'])
        except json.JSONDecodeError:
            case['responseBody'] = None
    if set(evidence)-set(ids):
        issues.append('HTTP evidence without a JUnit result')
    write(run/'results-with-http.json',result)
    summary = {'total':len(ids),'expected':result['expectedCases'],
               'by_result':dict(Counter(c['status'] for c in result['cases'])),
               'by_http':dict(Counter(c.get('httpStatus','NO_RESPONSE') for c in result['cases']))}
    if any(c['status'] in ['ABORTED','SKIPPED'] for c in result['cases']):
        issues.append('Skipped or aborted scenarios')
    if baseline:
        # Scenario + variant names remain stable if JUnit method/invocation order changes.
        previous = {c['name']:c for c in read(baseline)['cases']}
        current = {c['name']:c for c in result['cases']}
        def outcome(c):
            return [c['status'],c.get('httpStatus',c.get('http_status'))]
        summary['comparison'] = {'baseline':str(baseline),'added':sorted(current.keys()-previous.keys()),
            'removed':sorted(previous.keys()-current.keys()),'changed':[
                {'name':current[k]['name'],'before':outcome(previous[k]),'after':outcome(current[k])}
                for k in sorted(previous.keys() & current.keys()) if outcome(previous[k]) != outcome(current[k])]}
    interval_file = run/'service-test-interval.log'
    if interval_file.exists():
        entries = re.split(r'(?=^\d{4}-\d\d-\d\dT)',interval_file.read_text(encoding='utf-8-sig'),flags=re.M)
        errors = [e for e in entries if re.search(r'^\S+\s+ERROR\s',e)]
        exceptions = Counter()
        for entry in errors:
            match = re.search(r'^([\w.]+(?:Exception|Error))(?=:|\s*$)',entry,re.M)
            exceptions[match.group(1) if match else 'unclassified'] += 1
        summary['service_log'] = {'error_count':len(errors),'by_exception':dict(exceptions)}
        (run/'service-errors.log').write_text(''.join(errors),encoding='utf-8')
    before = read(run/'pre-run-state.json')
    after = read(run/'post-run-state.json')
    for name,state in [('before',before),('after',after)]:
        if state.get('errors') or (state.get('health') or {}).get('status') != 'UP':
            issues.append('Unavailable infrastructure '+name+' run')
    data_caches = ['splitting_object_cache','splitting_field_cache','actualization_cache']
    fixture_suite = result.get('environment', {}).get('suite') == 'fixture'
    if fixture_suite:
        cleanup_file = run/'fixture-cleanup.json'
        required = set(data_caches + ['param_cache', 'data_source_cache'])
        remaining = read(cleanup_file).get('remaining', {}) if cleanup_file.exists() else {}
        fixture_file = run/'fixture-manifest.json'
        released = fixture_file.exists() and read(fixture_file).get('status') == 'cleaned'
        if set(remaining) != required or any(row.get('found') != 0 for row in remaining.values()) or not released:
            issues.append('Fixture cleanup missing or owned rows remain')
    if result.get('environment', {}).get('suite') == 'functional-full':
        manifests = list((run/'fixtures').glob('case-*/fixture-manifest.json'))
        expected_fixtures = result['environment']['functionalCases']
        if len(manifests) != expected_fixtures:
            issues.append('Incomplete functional fixture lifecycle: expected '+str(expected_fixtures)+', found '+str(len(manifests)))
        required = set(data_caches + ['param_cache', 'data_source_cache'])
        for manifest in manifests:
            cleanup_file = manifest.with_name('fixture-cleanup.json')
            remaining = read(cleanup_file).get('remaining', {}) if cleanup_file.exists() else {}
            if read(manifest).get('status') != 'cleaned' or set(remaining) != required or any(value != 0 for value in remaining.values()):
                issues.append('Functional fixture not cleaned: '+str(manifest.parent.name))
    if not fixture_suite and any(before['caches'].get(c) != 0 for c in data_caches):
        issues.append('Non-empty input caches for the request-only suite')
    if any(after['caches'].get(c) != before['caches'].get(c) for c in data_caches):
        issues.append('Unexpected change in test data caches')
    summary['infrastructureIssues'] = issues
    write(run/'analysis.json',summary)
    return summary

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--run',type=Path,required=True)
    parser.add_argument('--baseline',type=Path)
    args = parser.parse_args()
    summary = analyze(args.run,args.baseline)
    print(json.dumps({k:v for k,v in summary.items() if k not in ['comparison','infrastructureIssues']},ensure_ascii=True))
    print('Infrastructure issues:',len(summary['infrastructureIssues']))
    if 'comparison' in summary:
        print('Changed/added/removed:',*[len(summary['comparison'][k]) for k in ['changed','added','removed']])
    raise SystemExit(2 if summary['infrastructureIssues'] else 0)

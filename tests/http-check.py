"""Exercise the Java HTTP adapter against a temporary loopback-only server."""
import json
import pathlib
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

root = pathlib.Path(__file__).resolve().parents[1]
base = 'http://127.0.0.1:18088'
passed = 0

def request(path, data=None, method='GET', key=None):
    suffix = ('?' + urllib.parse.urlencode(data)) if data is not None else ''
    headers = {'Idempotency-Key': key} if key else {}
    req = urllib.request.Request(base + path + suffix, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=3) as r:
            body = r.read()
            return r.status, json.loads(body) if 'application/json' in r.headers.get('Content-Type', '') else body
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)

def check(condition, name):
    global passed
    assert condition, name
    passed += 1
    print('PASS HTTP ' + name)

with tempfile.TemporaryDirectory(prefix='freshness-http-') as classes:
    subprocess.run(['javac', '--release', '21', '--add-modules', 'jdk.httpserver', '-d', classes,
                    *map(str, (root / 'java/src/demo').glob('*.java'))], check=True)
    server = subprocess.Popen(['java', '--add-modules', 'jdk.httpserver', '-cp', classes, 'demo.Server',
                               str(root / 'dist'), '18088'], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    try:
        for _ in range(100):
            if server.poll() is not None:
                raise RuntimeError(server.stderr.read())
            try:
                status, html = request('/')
                break
            except urllib.error.URLError:
                time.sleep(.05)
        else:
            raise RuntimeError('Server did not start')
        check(status == 200 and b'content="java"' in html, 'UI selects the Java API runtime')
        check(request('/api/reset', {}, 'POST') == (200, {'reset': True}), 'reset accepts an empty query')
        args = {'cart': 'http-cart-001', 'cook': 'tomorrow', 'slot': 'express'}
        status, q = request('/api/quote', args)
        check(status == 200 and q['batch'] is None and q['alternatives'][0]['slotKey'] == 'evening', 'cooking time changes feasible delivery')
        args['slot'] = 'evening'
        status, q = request('/api/quote', args)
        check(status == 200 and q['batch'] == 'B', 'a later delivery can promise batch B')
        request('/api/quality', {'held': 'true'}, 'POST')
        status, error = request('/api/reserve', {'cart': args['cart'], 'quote': q['quoteId']}, 'POST', 'http-key-0001')
        check(status == 409 and error['error'] == 'QUALITY_HOLD', 'quality change blocks old quote')
        status, revised = request('/api/quote', args)
        check(revised['batch'] is None and revised['alternatives'][0]['batch'] == 'C', 'refresh offers next-day batch C')
        request('/api/reset', {}, 'POST')
        args.update(cook='tonight', slot='express')
        _, q = request('/api/quote', args)
        payload = {'cart': args['cart'], 'quote': q['quoteId']}
        status, receipt = request('/api/reserve', payload, 'POST', 'http-key-0002')
        check(status == 200 and receipt['batch'] == 'A' and receipt['status'] == 'ACTIVE', 'reservation succeeds')
        check(request('/api/reserve', payload, 'POST', 'http-key-0002') == (200, receipt), 'retry returns the same reservation')
        _, after = request('/api/quote', args)
        check(after['diagnostics'][0]['available'] == 2, 'retry decrements inventory only once')
        status, error = request('/api/reserve', {'cart': 'http-cart-002', 'quote': q['quoteId']}, 'POST', 'http-key-0003')
        check(status == 409 and error['error'] == 'QUOTE_CART_MISMATCH', 'quote is bound to the requesting cart')
        print(f'All {passed} HTTP checks passed.')
    finally:
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait()

const assert = require('node:assert/strict')
const test = require('node:test')
const {
  manifestHash,
  planResourceChanges,
} = require('../lib/circle-tooling.cjs')

function asset(path, hash, contentType = 'text/plain; charset=utf-8') {
  return { path, plaintextHash: hash, contentType }
}

test('unchanged resources are skipped', () => {
  const local = [asset('/index.html', 'same', 'text/html; charset=utf-8')]
  const plan = planResourceChanges(local, structuredClone(local))
  assert.equal(plan.changed.length, 0)
  assert.equal(plan.unchanged.length, 1)
  assert.equal(plan.removed.length, 0)
})

test('hash and content type changes require upload', () => {
  const local = [
    asset('/script.js', 'new', 'application/javascript; charset=utf-8'),
    asset('/data', 'same', 'application/json; charset=utf-8'),
  ]
  const deployed = [
    asset('/script.js', 'old', 'application/javascript; charset=utf-8'),
    asset('/data', 'same', 'text/plain; charset=utf-8'),
  ]
  const plan = planResourceChanges(local, deployed)
  assert.deepEqual(plan.changed.map((item) => item.path), ['/script.js', '/data'])
  assert.equal(plan.unchanged.length, 0)
})

test('removed resources are tracked without re-uploading local files', () => {
  const local = [asset('/index.html', 'same')]
  const deployed = [asset('/index.html', 'same'), asset('/old.png', 'old', 'image/png')]
  const plan = planResourceChanges(local, deployed)
  assert.equal(plan.changed.length, 0)
  assert.deepEqual(plan.removed.map((item) => item.path), ['/old.png'])
})

test('force all selects every local resource', () => {
  const local = [asset('/a', '1'), asset('/b', '2')]
  const plan = planResourceChanges(local, structuredClone(local), true)
  assert.equal(plan.changed.length, 2)
  assert.equal(plan.unchanged.length, 0)
})

test('manifest hash is independent of asset order', () => {
  const first = [asset('/b', '2'), asset('/a', '1')]
  const second = [asset('/a', '1'), asset('/b', '2')]
  assert.equal(manifestHash(first), manifestHash(second))
})

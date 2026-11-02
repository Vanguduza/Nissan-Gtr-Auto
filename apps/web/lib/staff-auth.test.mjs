import test from 'node:test';
import assert from 'node:assert/strict';
import {canAccessPath, filterNavTreeForModuleAccess, moduleIdForPath, postLoginPath} from './staff-auth.ts';

const actor = (roles, moduleAccess = null, isApprover = true) => ({isStaff: true, roles, moduleAccess, isApprover});
test('master stock has its specific sales/finance role gate; warehouse mutations stay restricted', () => {
  for (const role of ['sales','finance','warehouse']) assert.equal(canAccessPath(actor([role]), '/staff/warehouse/master-stock'), true);
  for (const role of ['sales','finance','driver']) assert.equal(canAccessPath(actor([role]), '/staff/warehouse/transfers'), false);
  assert.equal(canAccessPath(actor(['driver']), '/staff/warehouse/master-stock'), false);
});
test('a module restriction is enforced on direct paths as well as navigation', () => {
  const limited = actor(['sales','warehouse'], ['warehouse']);
  assert.equal(canAccessPath(limited, '/staff/warehouse/master-stock'), true);
  assert.equal(canAccessPath(limited, '/staff/crm/product-pages'), false);
  const modules = filterNavTreeForModuleAccess(limited.roles, limited.moduleAccess).filter(x => x.kind === 'module');
  assert.deepEqual(modules.map(x => x.id), ['warehouse']);
  assert.equal(moduleIdForPath('/staff/warehouse/master-stock?overview_q=filter'), 'warehouse');
});
test('empty module assignments preserve role defaults; admins bypass only module restrictions', () => {
  assert.equal(canAccessPath(actor(['finance'], []), '/staff/finance'), true);
  assert.equal(canAccessPath(actor(['sales'], []), '/staff/finance'), false);
  assert.equal(canAccessPath(actor(['admin'], ['warehouse']), '/staff/finance'), true);
  assert.equal(canAccessPath({...actor(['admin']), isStaff: false}, '/staff/finance'), false);
});
test('later POS-manager approval gates remain composed with module access', () => {
  assert.equal(canAccessPath(actor(['sales'], ['pos'], false), '/staff/dashboard'), false);
  assert.equal(canAccessPath(actor(['sales'], ['pos'], true), '/staff/dashboard'), true);
  assert.equal(canAccessPath(actor(['finance'], ['finance'], false), '/staff/dashboard'), true);
  assert.equal(filterNavTreeForModuleAccess(['sales'], ['pos'], false).some(x => x.kind === 'link' && x.href === '/staff/dashboard'), false);
});
test('finance can open the funding desk without broadening general HR access', () => {
  assert.equal(canAccessPath(actor(['finance']), '/staff/hr/payroll'), true);
  assert.equal(canAccessPath(actor(['finance']), '/staff/hr'), false);
  const hr = filterNavTreeForModuleAccess(['finance'], null).find(x => x.kind === 'module' && x.id === 'hr');
  assert.equal(hr?.href, '/staff/hr/payroll');
  assert.deepEqual(hr?.children.map(x => x.href), ['/staff/hr/payroll']);
});
test('module links land on an accessible child and self service remains available', () => {
  const warehouse = filterNavTreeForModuleAccess(['sales'], ['warehouse']).find(x => x.kind === 'module' && x.id === 'warehouse');
  assert.equal(warehouse?.href, '/staff/warehouse/master-stock');
  assert.equal(canAccessPath(actor(['driver'], ['logistics']), '/staff/account'), true);
});
test('login honors the approved web POS and rejects URL-parser redirect escapes', () => {
  assert.equal(postLoginPath(true, '/staff/pos', ['sales']), '/staff/pos');
  assert.equal(postLoginPath(true, '/staff/pos?cart=1', ['sales']), '/staff/pos?cart=1');
  assert.equal(postLoginPath(true, '/staff/warehouse/master-stock', ['sales'], true), '/staff/change-password');
  for (const next of ['//evil.example', '/\\evil.example', '/%2f%2fevil.example', '/\nevil.example']) {
    assert.equal(postLoginPath(false, next), '/account');
    assert.equal(postLoginPath(true, next), '/staff');
  }
});

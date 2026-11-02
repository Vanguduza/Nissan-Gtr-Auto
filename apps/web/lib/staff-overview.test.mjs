import test from 'node:test';
import assert from 'node:assert/strict';
import {overviewDateBounds, overviewDefaultDates, percentChange, fetchStaffOverview, searchStaffOverview} from './staff-overview.ts';

test('overview ranges use whole Harare civil days regardless of browser DST', () => {
  for (const day of ['2026-03-08','2026-11-01','2026-03-29']) {
    const result = overviewDateBounds(day, day);
    assert.equal(result.ok, true);
    assert.equal(Date.parse(result.data.to) - Date.parse(result.data.from), 86_400_000);
    assert.equal(result.data.from.endsWith('T22:00:00.000Z'), true);
  }
});
test('invalid civil dates, backwards and excessive ranges fail before RPC', () => {
  for (const [from,to] of [['2026-02-31','2026-03-01'], ['2026-02-29','2026-03-01'], ['2026-04-31','2026-05-01'], ['2026-02-03','2026-02-01'], ['2024-01-01','2026-01-01'], ['bad','2026-01-01']]) assert.equal(overviewDateBounds(from,to).ok, false);
  assert.equal(overviewDateBounds('2024-02-29','2024-02-29').ok, true);
});
test('default period follows the business date at a UTC date boundary', () => {
  assert.deepEqual(overviewDefaultDates(new Date('2026-10-31T23:30:00Z')), {from:'2026-10-03', to:'2026-11-01'});
});
test('changes distinguish zero baselines and missing data from measured growth', () => {
  assert.equal(percentChange(0,0), 0);
  assert.equal(percentChange(1,0), null);
  assert.equal(percentChange(null,100), null);
  assert.equal(percentChange(Infinity,100), null);
  assert.equal(percentChange(90,100), -10);
});
test('overview validates a server payload instead of inventing metrics', async () => {
  const invalid = {rpc: async () => ({data: {revenue: 1000}, error: null})};
  assert.equal((await fetchStaffOverview(invalid, {from:'a',to:'b'})).ok, false);
  const failure = {rpc: async () => ({data: null, error: {message:'not allowed'}})};
  assert.deepEqual(await fetchStaffOverview(failure, {from:'a',to:'b'}), {ok:false,error:'not allowed'});
});
test('short global queries do not make a backend request', async () => {
  const client = {rpc: async () => {throw new Error('must not be called');}};
  assert.deepEqual(await searchStaffOverview(client, ' x '), {ok:true,data:[]});
});

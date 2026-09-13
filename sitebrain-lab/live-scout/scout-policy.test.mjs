import test from 'node:test';
import assert from 'node:assert/strict';
import {
  classifyPageBoundary,
  classifyRouteBoundary,
  scoreDiscoveryLink,
  calculatePublicCoverage
} from './scout-policy.mjs';

test('access-denied and challenge pages are protected boundaries', () => {
  assert.equal(classifyPageBoundary('Access to this page has been denied.', ''), 'ACCESS_BLOCKED');
  assert.equal(classifyPageBoundary('Attention Required! | Cloudflare', 'Verify you are human'), 'HUMAN_VERIFICATION_REQUIRED');
  assert.equal(classifyPageBoundary('Error Page | eBay', 'Something went wrong'), 'SITE_ERROR');
});

test('login-only marketplace page is auth required', () => {
  assert.equal(
    classifyPageBoundary('Facebook Marketplace', 'Log in to Facebook to continue to Marketplace'),
    'AUTH_REQUIRED'
  );
  assert.equal(classifyRouteBoundary('https://www.cargurus.com/Cars/myAccount/saved-listings'), 'AUTH_REQUIRED');
});

test('marketplace discovery favors search structure over legal and editorial links', () => {
  const search = scoreDiscoveryLink('Cars for Sale', 'https://example.com/cars');
  const filter = scoreDiscoveryLink('Filter results', 'https://example.com/cars?make=ford');
  const privacy = scoreDiscoveryLink('Privacy Policy', 'https://example.com/privacy');
  const login = scoreDiscoveryLink('Log in', 'https://example.com/login');
  const prohibited = scoreDiscoveryLink('prohibited items', 'https://example.com/about/prohibited');
  const research = scoreDiscoveryLink('Research', 'https://example.com/research');
  assert.ok(search > privacy);
  assert.ok(filter > privacy);
  assert.ok(privacy <= 0);
  assert.ok(login <= 0);
  assert.ok(prohibited <= 0);
  assert.ok(research <= 0);
});

test('blocked or empty sites cannot report one hundred percent learned', () => {
  assert.equal(calculatePublicCoverage({ nodes: 0, verifiedTransitions: 0, boundaries: 1, capabilities: [] }), 0);
  assert.equal(calculatePublicCoverage({ nodes: 1, verifiedTransitions: 0, boundaries: 0, capabilities: [] }), 0);
});

test('one hundred percent requires a minimum viable search structure', () => {
  const incomplete = calculatePublicCoverage({
    nodes: 4,
    verifiedTransitions: 3,
    boundaries: 0,
    capabilities: ['CATEGORY', 'RESULTS']
  });
  assert.ok(incomplete < 100);

  const complete = calculatePublicCoverage({
    nodes: 5,
    verifiedTransitions: 5,
    boundaries: 0,
    capabilities: ['SEARCH', 'CATEGORY', 'RESULTS', 'DETAIL']
  });
  assert.equal(complete, 100);
});

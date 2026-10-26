import test from 'node:test';
import assert from 'node:assert/strict';
import {FEATURES} from '../src/features.js';
test('original twelve enhancements have stable unique native routes and concrete descriptions',()=>{assert.equal(FEATURES.length,12);assert.equal(new Set(FEATURES.map(f=>f.id)).size,12);for(const f of FEATURES){assert.ok(f.title);assert.ok(f.detail.length>15);}assert.deepEqual(FEATURES.map(f=>f.id),['discovery','dual','downloads','library','gestures','diagnostics','cast','nas','subtitles','tools','vault','handoff']);});

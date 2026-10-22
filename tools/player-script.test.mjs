import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';

const script = readFileSync(new URL('../app/src/main/assets/player.js', import.meta.url), 'utf8');

function harness({missing = false, frameApi = true, paused = false} = {}) {
    const attributes = new Map([['poster', 'website-poster'], ['controls', '']]);
    const frames = [], animationFrames = [], observers = [], styles = new Map();
    const html = {style: {}, parentElement: null};
    const body = {style: {}, parentElement: html, children: []};
    const video = {style: {}, parentElement: body, controls: true, paused,
        readyState: 4, videoWidth: 1280, videoHeight: 720,
        addEventListener() {},
        setAttribute: (key, value) => attributes.set(key, value),
        hasAttribute: key => attributes.has(key),
        removeAttribute: key => attributes.delete(key),
        play() { this.paused = false; return Promise.resolve(); }
    };
    if (frameApi) video.requestVideoFrameCallback = callback => frames.push(callback);
    body.children = [video, {style: {}}];
    let current = missing ? null : video;
    const context = {window: {}, document: {
        querySelector: () => current,
        getElementById: id => styles.get(id),
        createElement: () => ({}),
        head: {appendChild: element => styles.set(element.id, element)},
        documentElement: html, body
    }, MutationObserver: class {
        constructor(callback) { observers.push(callback); }
        observe() {}
    }, requestAnimationFrame: callback => animationFrames.push(callback)};
    return {video, context, attributes, frames, animationFrames, observers,
        replace: value => {current = value;},
        run: (force = false) => vm.runInNewContext(script + '(' + force + ')', context)};
}

test('a missing video stays in the loading state', () => {
    assert.equal(harness({missing: true}).run(), 'waiting');
});

test('unpaused media with data does not uncover the player before a video frame', () => {
    const h = harness();
    assert.equal(h.run(), 'pending');
    assert.equal(h.run(), 'pending');
    assert.equal(h.frames.length, 1);
    h.frames.shift()();
    assert.equal(h.run(), 'playing');
});

test('website posters and native controls stay removed if the site restores them', () => {
    const h = harness();h.run();
    assert.equal(h.video.controls, false);
    assert.equal(h.attributes.has('poster'), false);
    h.video.controls = true;h.attributes.set('controls', '');h.attributes.set('poster', 'restored');
    h.observers[0]();
    assert.equal(h.video.controls, false);
    assert.equal(h.attributes.has('controls'), false);
    assert.equal(h.attributes.has('poster'), false);
});

test('a replacement video has to produce its own first frame', () => {
    const h = harness();h.run();h.frames.shift()();assert.equal(h.run(), 'playing');
    const replacement = {...h.video, style: {}, requestVideoFrameCallback: cb => h.frames.push(cb)};
    h.replace(replacement);
    assert.equal(h.run(), 'pending');
    h.frames.shift()();assert.equal(h.run(), 'playing');
});

test('older WebViews wait for decoded data and two rendering opportunities', () => {
    const h = harness({frameApi: false});
    h.video.readyState = 1;assert.equal(h.run(), 'pending');
    assert.equal(h.animationFrames.length, 0);
    h.video.readyState = 3;assert.equal(h.run(), 'pending');
    h.animationFrames.shift()();assert.equal(h.run(), 'pending');
    h.animationFrames.shift()();assert.equal(h.run(), 'playing');
});

test('autoplay rejection can be retried by an explicit OK action', async () => {
    const h = harness({paused: true});
    h.video.play = () => Promise.reject({name: 'NotAllowedError'});
    assert.equal(h.run(), 'pending');
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(h.run(), 'blocked');
    h.video.play = () => {h.video.paused = false;return Promise.resolve();};
    assert.equal(h.run(true), 'pending');
    h.frames.shift()();assert.equal(h.run(), 'playing');
});

test('a media failure takes precedence over an earlier ready frame', () => {
    const h = harness();h.run();h.frames.shift()();h.video.error = {code: 2};
    assert.equal(h.run(), 'error');
});

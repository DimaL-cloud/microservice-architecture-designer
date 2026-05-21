import { JSDOM } from 'jsdom';

const dom = new JSDOM('<!DOCTYPE html><html><body></body></html>', {
  url: 'http://localhost',
  pretendToBeVisual: true,
});

const { window } = dom;

const domGlobals = [
  'window',
  'document',
  'navigator',
  'DOMParser',
  'XMLSerializer',
  'Element',
  'HTMLElement',
  'SVGElement',
  'Node',
  'NodeList',
  'Text',
  'Image',
  'HTMLCanvasElement',
  'getComputedStyle',
];

for (const key of domGlobals) {
  const value = (window as unknown as Record<string, unknown>)[key];
  if (value !== undefined) {
    Object.defineProperty(globalThis, key, {
      value: typeof value === 'function' ? (value as Function).bind(window) : value,
      configurable: true,
      writable: true,
    });
  }
}

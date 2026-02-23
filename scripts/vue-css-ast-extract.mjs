#!/usr/bin/env node

function readStdin() {
  return new Promise((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => { data += chunk; });
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });
}

function toColorPoint(hex) {
  const raw = hex.slice(1);
  const r = parseInt(raw.slice(0, 2), 16);
  const g = parseInt(raw.slice(2, 4), 16);
  const b = parseInt(raw.slice(4, 6), 16);
  const l = (r + g + b) / 7.65;
  const a = (r - b) / 2.55;
  const bb = (g - (r + b) / 2) / 2.55;
  return { l, a, b: bb };
}

function extract(cssCode) {
  const tokens = {};
  const add = (name) => { tokens[name] = (tokens[name] || 0) + 1; };

  if (/display\s*:\s*flex/i.test(cssCode)) add('layout:flex');
  if (/flex-direction\s*:\s*column/i.test(cssCode)) add('flex:col');
  if (/flex-direction\s*:\s*row/i.test(cssCode)) add('flex:row');
  if (/align-items\s*:\s*center/i.test(cssCode)) add('align:center');
  if (/justify-content\s*:\s*space-between/i.test(cssCode)) add('justify:space-between');
  if (/margin/i.test(cssCode)) add('margin');
  if (/padding/i.test(cssCode)) add('padding');
  if (/box-shadow/i.test(cssCode)) add('shadow');
  if (/border-radius/i.test(cssCode)) add('radius');
  if (/cursor\s*:\s*pointer/i.test(cssCode)) add('cursor:pointer');
  if (/:hover/i.test(cssCode)) add('hover');
  if (/font-weight\s*:\s*(bold|700)/i.test(cssCode)) add('fw:bold');
  if (/font-weight\s*:\s*600/i.test(cssCode)) add('fw:semibold');
  if (/font-size/i.test(cssCode)) add('font-size');

  const colorMatches = cssCode.match(/#[0-9a-fA-F]{6}/g) || [];
  const palette = colorMatches.map(toColorPoint);

  const spacingVals = [];
  const spacingRegex = /(margin|padding)[^;]*?([0-9]+)px/gi;
  let sm;
  while ((sm = spacingRegex.exec(cssCode)) !== null) {
    spacingVals.push(Number(sm[2]));
  }
  const spacingMean = spacingVals.length ? spacingVals.reduce((a, b) => a + b, 0) / spacingVals.length : 0;
  let spacingStd = 0;
  if (spacingVals.length > 1) {
    const variance = spacingVals.map((v) => (v - spacingMean) ** 2).reduce((a, b) => a + b, 0) / spacingVals.length;
    spacingStd = Math.sqrt(variance);
  }

  const fontSizeBuckets = {};
  const fontSizeRegex = /font-size\s*:\s*([0-9]+)px/gi;
  let fm;
  while ((fm = fontSizeRegex.exec(cssCode)) !== null) {
    const px = Number(fm[1]);
    const bucket = px <= 12 ? 'xs' : px <= 14 ? 'sm' : px <= 16 ? 'md' : px <= 20 ? 'lg' : 'xl';
    fontSizeBuckets[bucket] = (fontSizeBuckets[bucket] || 0) + 1;
  }

  return {
    status: 'ok',
    styleTokens: tokens,
    palette,
    spacingMean,
    spacingStd,
    fontFamilies: [],
    fontSizeBuckets,
  };
}

async function main() {
  try {
    const raw = await readStdin();
    const req = JSON.parse(raw || '{}');
    const cssCode = typeof req.cssCode === 'string' ? req.cssCode : '';
    process.stdout.write(JSON.stringify(extract(cssCode)));
  } catch (error) {
    process.stdout.write(JSON.stringify({
      status: 'error',
      error: error instanceof Error ? error.message : String(error),
    }));
    process.exit(1);
  }
}

main();

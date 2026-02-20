#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

function readStdin() {
  return new Promise((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => { data += chunk; });
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });
}

function walkFiles(root, out = []) {
  const entries = fs.readdirSync(root, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name === 'dist' || entry.name === 'build') continue;
    const full = path.join(root, entry.name);
    if (entry.isDirectory()) {
      walkFiles(full, out);
      continue;
    }
    if (entry.isFile() && full.endsWith('.component.ts')) out.push(full);
  }
  return out;
}

function toRelativePosix(root, file) {
  return path.relative(root, file).split(path.sep).join('/');
}

function parseClassNames(text) {
  const names = [];
  const regex = /(?:export\s+)?class\s+([A-Za-z_][A-Za-z0-9_]*)/g;
  let m;
  while ((m = regex.exec(text)) !== null) names.push(m[1]);
  return Array.from(new Set(names));
}

function parseTemplateUrl(text) {
  const m = text.match(/templateUrl\s*:\s*['\"]([^'\"]+)['\"]/);
  return m ? m[1] : null;
}

function parseInlineTemplate(text) {
  const m1 = text.match(/template\s*:\s*`([\s\S]*?)`/);
  if (m1) return m1[1];
  const m2 = text.match(/template\s*:\s*'([^']*)'/);
  if (m2) return m2[1];
  const m3 = text.match(/template\s*:\s*\"([^\"]*)\"/);
  if (m3) return m3[1];
  return null;
}

function parseStyleUrls(text) {
  const m = text.match(/styleUrls\s*:\s*\[([\s\S]*?)\]/);
  if (!m) return [];
  const arr = m[1];
  const quoteRegex = /['\"]([^'\"]+)['\"]/g;
  const out = [];
  let qm;
  while ((qm = quoteRegex.exec(arr)) !== null) out.push(qm[1]);
  return out;
}

function parseInlineStyles(text) {
  const m = text.match(/styles\s*:\s*\[([\s\S]*?)\]/);
  if (!m) return [];
  const arr = m[1];
  const itemRegex = /`([\s\S]*?)`|'([^']*)'|\"([^\"]*)\"/g;
  const out = [];
  let im;
  while ((im = itemRegex.exec(arr)) !== null) {
    const value = im[1] || im[2] || im[3] || '';
    if (value.length > 0) out.push(value);
  }
  return out;
}

function resolveRelative(baseFileRel, spec) {
  const dir = path.posix.dirname(baseFileRel);
  return path.posix.normalize(path.posix.join(dir, spec));
}

function defaultStyleCandidates(repoRoot, relativePath) {
  const dir = path.posix.dirname(relativePath);
  const file = path.posix.basename(relativePath);
  const stem = file.replace(/\.ts$/, '');
  const candidates = [
    path.posix.join(dir, `${stem}.css`),
    path.posix.join(dir, `${stem}.scss`)
  ];
  return candidates.filter((rel) => fs.existsSync(path.join(repoRoot, rel)));
}

function scanAngularRepo(repoRoot) {
  const files = walkFiles(repoRoot);
  const components = [];

  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8');
    const relativePath = toRelativePosix(repoRoot, file);
    const classNames = parseClassNames(text);
    if (classNames.length === 0) continue;

    const templateUrl = parseTemplateUrl(text);
    const inlineTemplate = parseInlineTemplate(text);
    const styleUrls = parseStyleUrls(text);
    const inlineStyles = parseInlineStyles(text);

    const templatePath = templateUrl ? resolveRelative(relativePath, templateUrl) : relativePath;
    const resolvedStylePaths = styleUrls.length > 0
      ? styleUrls.map((s) => resolveRelative(relativePath, s))
      : (inlineStyles.length === 0 ? defaultStyleCandidates(repoRoot, relativePath) : []);

    for (const className of classNames) {
      components.push({
        relativePath,
        exportName: className,
        templatePath,
        logicPath: relativePath,
        stylePaths: resolvedStylePaths,
        inlineTemplateCode: inlineTemplate,
        inlineStyleCodes: inlineStyles
      });
    }
  }

  return components;
}

async function main() {
  try {
    const raw = await readStdin();
    const request = JSON.parse(raw || '{}');
    if (!request.repoRoot || typeof request.repoRoot !== 'string') {
      console.log(JSON.stringify({ status: 'error', components: [], error: 'Missing repoRoot' }));
      process.exit(1);
      return;
    }
    const components = scanAngularRepo(request.repoRoot);
    console.log(JSON.stringify({ status: 'ok', components }));
  } catch (error) {
    console.log(JSON.stringify({
      status: 'error',
      components: [],
      error: error instanceof Error ? error.message : String(error)
    }));
    process.exit(1);
  }
}

main();

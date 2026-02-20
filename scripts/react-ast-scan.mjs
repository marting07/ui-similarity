#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

function readStdin() {
  return new Promise((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => {
      data += chunk;
    });
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
    if (entry.isFile() && (full.endsWith('.tsx') || full.endsWith('.jsx'))) {
      out.push(full);
    }
  }
  return out;
}

function toRelativePosix(root, file) {
  return path.relative(root, file).split(path.sep).join('/');
}

function stripComments(code) {
  return code
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ');
}

function tokenize(code) {
  const tokens = [];
  let i = 0;
  while (i < code.length) {
    const ch = code[i];
    if (/\s/.test(ch)) {
      i++;
      continue;
    }
    if (/[A-Za-z_$]/.test(ch)) {
      const start = i;
      i++;
      while (i < code.length && /[A-Za-z0-9_$]/.test(code[i])) i++;
      tokens.push({ type: 'ident', value: code.slice(start, i) });
      continue;
    }
    if (ch === '{' || ch === '}' || ch === ',' || ch === ';') {
      tokens.push({ type: 'punc', value: ch });
      i++;
      continue;
    }
    i++;
  }
  return tokens;
}

function parseNamedExportList(tokens, start) {
  const names = [];
  let i = start;
  while (i < tokens.length) {
    const t = tokens[i];
    if (!t) break;
    if (t.type === 'punc' && t.value === '}') return { names, next: i + 1 };
    if (t.type === 'ident') {
      const local = t.value;
      const next = tokens[i + 1];
      if (next && next.type === 'ident' && next.value === 'as') {
        const alias = tokens[i + 2];
        if (alias && alias.type === 'ident') {
          names.push(alias.value);
          i += 3;
          continue;
        }
      }
      names.push(local);
      i++;
      continue;
    }
    i++;
  }
  return { names, next: i };
}

function extractExportNames(code) {
  const tokens = tokenize(stripComments(code));
  const names = new Set();
  let i = 0;

  while (i < tokens.length) {
    const t = tokens[i];
    if (!t || t.type !== 'ident' || t.value !== 'export') {
      i++;
      continue;
    }

    const t1 = tokens[i + 1];
    const t2 = tokens[i + 2];
    const t3 = tokens[i + 3];

    if (t1?.type === 'ident' && t1.value === 'default') {
      if (t2?.type === 'ident' && (t2.value === 'function' || t2.value === 'class')) {
        if (t3?.type === 'ident') names.add(t3.value);
        i += 4;
        continue;
      }
      if (t2?.type === 'ident') {
        names.add(t2.value);
        i += 3;
        continue;
      }
      i += 2;
      continue;
    }

    if (t1?.type === 'ident' && (t1.value === 'const' || t1.value === 'function' || t1.value === 'class')) {
      if (t2?.type === 'ident') names.add(t2.value);
      i += 3;
      continue;
    }

    if (t1?.type === 'punc' && t1.value === '{') {
      const parsed = parseNamedExportList(tokens, i + 2);
      for (const name of parsed.names) names.add(name);
      i = parsed.next;
      continue;
    }

    i++;
  }

  return Array.from(names);
}

function extractStyleImports(code, relativePath) {
  const styles = [];
  const importRegex = /import\s+(?:[^'\"]+from\s+)?['\"]([^'\"]+)['\"]/g;
  let m;
  while ((m = importRegex.exec(code)) !== null) {
    const spec = m[1];
    if (!spec.endsWith('.css') && !spec.endsWith('.scss')) continue;
    if (spec.startsWith('.')) {
      const baseDir = path.posix.dirname(relativePath);
      const rel = path.posix.normalize(path.posix.join(baseDir, spec));
      styles.push(rel);
    } else {
      styles.push(spec);
    }
  }
  return Array.from(new Set(styles));
}

function scanReactRepo(repoRoot) {
  const files = walkFiles(repoRoot);
  const components = [];
  for (const file of files) {
    const code = fs.readFileSync(file, 'utf8');
    const relativePath = toRelativePosix(repoRoot, file);
    const exportNames = extractExportNames(code);
    if (exportNames.length === 0) continue;
    const stylePaths = extractStyleImports(code, relativePath);
    for (const exportName of exportNames) {
      components.push({
        relativePath,
        exportName,
        templatePath: relativePath,
        logicPath: relativePath,
        stylePaths,
        inlineTemplateCode: null,
        inlineStyleCodes: []
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

    const components = scanReactRepo(request.repoRoot);
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

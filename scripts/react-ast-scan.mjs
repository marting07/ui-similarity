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

function extractExportNamesTokenizer(code) {
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
      if (t2?.type === 'ident' && t2.value === 'function') {
        if (t3?.type === 'ident') names.add(t3.value);
        i += 4;
        continue;
      }
      // Keep parity with legacy scanner: ignore class default exports.
      if (t2?.type === 'ident' && t2.value !== 'class' && t2.value !== 'function') {
        names.add(t2.value);
        i += 3;
        continue;
      }
      i += 2;
      continue;
    }

    // Keep parity with legacy scanner: only const/function direct exports.
    if (t1?.type === 'ident' && (t1.value === 'const' || t1.value === 'function')) {
      if (t2?.type === 'ident') names.add(t2.value);
      i += 3;
      continue;
    }

    // Keep parity with legacy scanner: ignore `export { A as B }` re-export lists.
    if (t1?.type === 'punc' && t1.value === '{') {
      const parsed = parseNamedExportList(tokens, i + 2);
      i = parsed.next;
      continue;
    }

    i++;
  }

  return Array.from(names);
}

function extractStyleImportsTokenizer(code, relativePath) {
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

async function loadTypeScript() {
  try {
    const mod = await import('typescript');
    return mod.default || mod;
  } catch {
    return null;
  }
}

function extractExportNamesWithTs(ts, code, filePath) {
  const sourceFile = ts.createSourceFile(
    filePath,
    code,
    ts.ScriptTarget.Latest,
    true,
    filePath.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.JSX
  );

  const names = new Set();

  for (const stmt of sourceFile.statements) {
    if (ts.isExportAssignment(stmt) && !stmt.isExportEquals) {
      // Keep parity with legacy scanner:
      // - include `export default Name`
      // - include identifier param for `export default props => ...`
      if (ts.isIdentifier(stmt.expression)) {
        names.add(stmt.expression.text);
      } else if (ts.isArrowFunction(stmt.expression) && stmt.expression.parameters.length > 0) {
        const first = stmt.expression.parameters[0]?.name;
        if (first && ts.isIdentifier(first)) names.add(first.text);
      }
      continue;
    }

    if (!stmt.modifiers || !stmt.modifiers.some((m) => m.kind === ts.SyntaxKind.ExportKeyword)) {
      continue;
    }

    if (ts.isFunctionDeclaration(stmt)) {
      if (!stmt.name) continue;
      names.add(stmt.name.text);
      continue;
    }

    if (ts.isVariableStatement(stmt)) {
      for (const d of stmt.declarationList.declarations) {
        if (ts.isIdentifier(d.name)) names.add(d.name.text);
      }
      continue;
    }

    // Keep parity with legacy scanner:
    // - ignore class exports
    // - ignore export declaration lists (`export { A as B }`)
  }

  return Array.from(names);
}

function extractStyleImportsWithTs(ts, code, relativePath, filePath) {
  const sourceFile = ts.createSourceFile(
    filePath,
    code,
    ts.ScriptTarget.Latest,
    true,
    filePath.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.JSX
  );

  const styles = new Set();
  for (const stmt of sourceFile.statements) {
    if (!ts.isImportDeclaration(stmt)) continue;
    if (!ts.isStringLiteral(stmt.moduleSpecifier)) continue;
    const spec = stmt.moduleSpecifier.text;
    if (!spec.endsWith('.css') && !spec.endsWith('.scss')) continue;
    if (spec.startsWith('.')) {
      const baseDir = path.posix.dirname(relativePath);
      styles.add(path.posix.normalize(path.posix.join(baseDir, spec)));
    } else {
      styles.add(spec);
    }
  }

  return Array.from(styles);
}

function buildComponentDescriptors(relativePath, exportNames, stylePaths) {
  return exportNames.map((exportName) => ({
    relativePath,
    exportName,
    templatePath: relativePath,
    logicPath: relativePath,
    stylePaths,
    inlineTemplateCode: null,
    inlineStyleCodes: []
  }));
}

async function scanReactRepo(repoRoot) {
  const ts = await loadTypeScript();
  const files = walkFiles(repoRoot);
  const components = [];

  for (const file of files) {
    const code = fs.readFileSync(file, 'utf8');
    const relativePath = toRelativePosix(repoRoot, file);

    const exportNames = ts
      ? extractExportNamesWithTs(ts, code, file)
      : extractExportNamesTokenizer(code);

    if (exportNames.length === 0) continue;

    const stylePaths = ts
      ? extractStyleImportsWithTs(ts, code, relativePath, file)
      : extractStyleImportsTokenizer(code, relativePath);

    components.push(...buildComponentDescriptors(relativePath, exportNames, stylePaths));
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

    const components = await scanReactRepo(request.repoRoot);
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

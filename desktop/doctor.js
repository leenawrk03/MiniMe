#!/usr/bin/env node
/* MiniMe desktop doctor — answers "why is there no window?" */
const fs = require('fs');
const path = require('path');
const http = require('http');

const ok = (m) => console.log('  OK   ' + m);
const bad = (m) => console.log('  FAIL ' + m);
const warn = (m) => console.log('  WARN ' + m);

console.log('\nMiniMe desktop doctor\n---------------------');
console.log(`node ${process.version} on ${process.platform}/${process.arch}\n`);

let fatal = false;

// 1. electron package + downloaded binary
const electronDir = path.join(__dirname, 'node_modules', 'electron');
if (!fs.existsSync(electronDir)) {
  bad('electron is not installed. Run: npm install');
  fatal = true;
} else {
  const pkg = JSON.parse(fs.readFileSync(path.join(electronDir, 'package.json'), 'utf8'));
  ok(`electron package ${pkg.version} installed`);
  const pathTxt = path.join(electronDir, 'path.txt');
  if (!fs.existsSync(pathTxt)) {
    bad('electron binary was never downloaded (no path.txt).');
    console.log('       The npm install finished but the binary download was blocked.');
    console.log('       Retry with:  npm install electron --force');
    console.log('       Behind a proxy/offline: set ELECTRON_MIRROR or ELECTRON_OVERRIDE_DIST_PATH.');
    fatal = true;
  } else {
    const bin = path.join(electronDir, 'dist', fs.readFileSync(pathTxt, 'utf8').trim());
    if (fs.existsSync(bin)) ok(`electron binary present: ${bin}`);
    else {
      bad(`electron binary missing at ${bin}. Run: npm install electron --force`);
      fatal = true;
    }
  }
}

// 2. built UI
const dist = path.join(__dirname, '..', 'ui', 'dist', 'index.html');
if (fs.existsSync(dist)) ok(`UI build found: ${dist}`);
else {
  bad(`UI build missing: ${dist}`);
  console.log('       Run:  cd ../ui && npm install && npm run build');
  console.log('       (or start with MINIME_DEV_URL=http://localhost:4200 npm start)');
  fatal = true;
}

// 3. backend
const req = http.get({ host: 'localhost', port: 842, path: '/api/health', timeout: 1500 }, (res) => {
  if (res.statusCode === 200) ok('backend answering on http://localhost:842/api/health');
  else warn(`backend responded with HTTP ${res.statusCode}`);
  res.resume();
  finish();
});
req.on('timeout', () => { req.destroy(new Error('timeout')); });
req.on('error', () => {
  warn('backend not reachable on port 842 (chat will fail; the orb still shows).');
  console.log('       Run:  cd ../backend && mvn spring-boot:run');
  finish();
});

function finish() {
  console.log('');
  if (fatal) {
    console.log('Result: fix the FAIL items above, then run `npm start` again.\n');
    process.exitCode = 1;
  } else {
    console.log('Result: everything needed for a visible window is in place.');
    console.log('If you still see nothing, run:  npm run start:opaque\n');
  }
}

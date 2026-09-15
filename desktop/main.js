const {
  app,
  BrowserWindow,
  ipcMain,
  screen,
  systemPreferences,
  session,
  desktopCapturer,
} = require('electron');

const path = require('path');
const { execFile } = require('child_process');

const ALLOWED_APPS = new Set([
  'Google Chrome',
  'Safari',
  'Visual Studio Code',
  'Terminal',
  'Finder',
  'Notes',
  'Calendar',
  'Mail',
  'Messages',
  'Slack',
  'WhatsApp',
]);

let win = null;

const COLLAPSED = {
  width: 180,
  height: 180,
};

const EXPANDED = {
  width: 520,
  height: 820,
};

function getBottomRightPosition(width, height) {
  const display = screen.getPrimaryDisplay();
  const { workArea } = display;

  const x = Math.max(
    workArea.x,
    workArea.x + workArea.width - width - 24
  );

  const y = Math.max(
    workArea.y,
    workArea.y + workArea.height - height - 24
  );

  return { x, y };
}

function createWindow() {
  const position = getBottomRightPosition(
    COLLAPSED.width,
    COLLAPSED.height
  );

  win = new BrowserWindow({
    width: COLLAPSED.width,
    height: COLLAPSED.height,

    x: position.x,
    y: position.y,

    frame: false,
    transparent: true,

    resizable: false,
    movable: true,

    alwaysOnTop: true,
    type: 'panel',
    hasShadow: false,

    show: false,

    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  /*
   * MiniMe currently runs from Angular's development server.
   */
  win.loadURL('http://localhost:4200/');
  win.webContents.openDevTools();

  /*
   * Show the window as soon as Electron has something to display.
   */
  win.once('ready-to-show', () => {
    if (!win) return;

    win.show();
    win.focus();

    console.log('🟢 MiniMe window shown');
    console.log('📐 Bounds:', win.getBounds());
  });

  /*
   * Useful diagnostics if Angular fails to load.
   */
  win.webContents.on('did-finish-load', () => {
    console.log('✅ MiniMe Angular page loaded');
  });

  win.webContents.on(
    'did-fail-load',
    (_event, errorCode, errorDescription, validatedURL) => {
      console.error(
        '❌ MiniMe failed to load:',
        errorCode,
        errorDescription,
        validatedURL
      );
    }
  );

  win.webContents.on('render-process-gone', (_event, details) => {
    console.error('❌ MiniMe renderer stopped:', details);
  });

  win.setAlwaysOnTop(true, 'floating');

  win.setSkipTaskbar(false);

  win.on('closed', () => {
    win = null;
  });
}


/* =====================================================
   EXPAND / COLLAPSE
   ===================================================== */

ipcMain.on('minime:resize', (_event, expanded) => {
  console.log('🔥 RESIZE MESSAGE RECEIVED:', expanded);

  if (!win) return;

  const size = expanded ? EXPANDED : COLLAPSED;

  // console.log(`📐 MiniMe resize: ${size.width}x${size.height}`);

  win.setSize(size.width, size.height);

  const position = getBottomRightPosition(
    size.width,
    size.height
  );

  win.setPosition(position.x, position.y);

  win.setAlwaysOnTop(true, 'floating');

  console.log('📐 Actual Electron bounds:', win.getBounds());
});


/* =====================================================
   COMPUTER CONTROL — OPEN APP
   ===================================================== */

ipcMain.handle('minime:open-app', async (_event, appName) => {
  if (process.platform !== 'darwin') {
    throw new Error(
      'Opening apps through MiniMe is currently implemented for macOS.'
    );
  }

  const name = String(appName || '').trim();

  if (!name) {
    throw new Error('No application name was provided.');
  }

  if (!ALLOWED_APPS.has(name)) {
    throw new Error(
      `App "${name}" is not allowed.`
    );
  }

  console.log(`🖥️ MiniMe opening app: ${name}`);

  return new Promise((resolve, reject) => {
    execFile('open', ['-a', name], (error, _stdout, stderr) => {
      if (error) {
        console.error(
          `❌ Could not open ${name}:`,
          stderr || error.message
        );

        reject(new Error(stderr || error.message));
        return;
      }

      console.log(`✅ Opened app: ${name}`);

      resolve({
        ok: true,
        action: 'OPEN_APP',
        app: name,
      });
    });
  });
});


/* =====================================================
   SCREEN CAPTURE
   ===================================================== */

ipcMain.handle('minime:screen-capture', async () => {
  const primaryDisplay = screen.getPrimaryDisplay();

  const sources = await desktopCapturer.getSources({
    types: ['screen'],
    thumbnailSize: {
      width: 1920,
      height: 1080,
    },
  });

  const source =
    sources.find(
      (s) => String(s.display_id) === String(primaryDisplay.id)
    ) || sources[0];

  if (!source) {
    throw new Error('No screen source found');
  }

  return {
    imageBase64: source.thumbnail.toPNG().toString('base64'),
    mimeType: 'image/png',
  };
});


/* =====================================================
   ELECTRON START
   ===================================================== */

app.whenReady().then(async () => {
  /*
   * macOS microphone permission
   */
  if (process.platform === 'darwin') {
    const status =
      systemPreferences.getMediaAccessStatus('microphone');

    console.log('🎤 MIC STATUS:', status);

    if (status !== 'granted') {
      const granted =
        await systemPreferences.askForMediaAccess('microphone');

      console.log('🎤 MIC REQUEST RESULT:', granted);
    }
  }

  /*
   * Chromium media permission
   */
  session.defaultSession.setPermissionRequestHandler(
    (_webContents, permission, callback) => {
      if (
        permission === 'media' ||
        permission === 'audioCapture'
      ) {
        callback(true);
        return;
      }

      callback(false);
    }
  );

  /*
   * Chromium permission check
   */
  session.defaultSession.setPermissionCheckHandler(
    (_webContents, permission) => {
      return (
        permission === 'media' ||
        permission === 'audioCapture'
      );
    }
  );

  /*
   * Start MiniMe.
   */
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});


/* =====================================================
   QUIT
   ===================================================== */

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
const {
  app,
  BrowserWindow,
  ipcMain,
  screen,
  session,
} = require('electron');

const path = require('path');

let win;

const COLLAPSED = {
  width: 180,
  height: 180,
};

const EXPANDED = {
  width: 460,
  height: 720,
};

function getBottomRightPosition(width, height) {
  const { workArea } = screen.getPrimaryDisplay();

  return {
    x: workArea.x + workArea.width - width - 24,
    y: workArea.y + workArea.height - height - 24,
  };
}
app.whenReady().then(() => {
  session.defaultSession.setPermissionRequestHandler(
    (webContents, permission, callback) => {
      if (permission === 'media') {
        callback(true);
      } else {
        callback(true);
      }
    }
  );

  createWindow();
});
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
   * Load Angular.
   *
   * Angular still runs on localhost,
   * but the user sees only the Electron window.
   */
  win.loadURL('http://localhost:4200/');

  /*
   * Show only after Angular has loaded.
   */
  win.once('ready-to-show', () => {
    win.show();
  });

  /*
   * Keep MiniMe above normal windows.
   */
  win.setAlwaysOnTop(true, 'floating');
  win.setVisibleOnAllWorkspaces(true, {
    visibleOnFullScreen: true,
  });

  /*
   * Optional: don't show the app in the taskbar/dock
   * as a normal browser-style window.
   */
  win.setSkipTaskbar(false);

  win.on('closed', () => {
    win = null;
  });
}


/* =====================================================
   EXPAND / COLLAPSE
   ===================================================== */

ipcMain.on('minime:resize', (_event, expanded) => {
  if (!win) return;

  const size = expanded ? EXPANDED : COLLAPSED;

  const position = getBottomRightPosition(
    size.width,
    size.height
  );

  win.setBounds({
    x: position.x,
    y: position.y,

    width: size.width,
    height: size.height,
  });

  win.setAlwaysOnTop(true, 'floating');
  win.setVisibleOnAllWorkspaces(true, {
    visibleOnFullScreen: true,
  });
});


/* =====================================================
   ELECTRON START
   ===================================================== */

app.whenReady().then(() => {
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

app.on('window-all-closed', (event) => {
  /*
   * Don't quit automatically on macOS.
   */
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
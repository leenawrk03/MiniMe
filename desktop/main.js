const {
  app,
  BrowserWindow,
  ipcMain,
  screen,
  systemPreferences,
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

  win.loadURL('http://localhost:4200/');
  win.webContents.openDevTools();

  win.once('ready-to-show', () => {
    win.show();
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
      if (permission === 'media') {
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
      return permission === 'media';
    }
  );


  /*
   * Start Orb
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
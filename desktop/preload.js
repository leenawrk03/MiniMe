const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  /* Existing Orb controls */
  resize: (expanded) => {
    ipcRenderer.send('minime:resize', expanded);
  },

  /* Existing screen agent */
  captureScreen: () => {
    return ipcRenderer.invoke('minime:screen-capture');
  },

  /* Computer control */
  openApp: (appName) => {
    return ipcRenderer.invoke('minime:open-app', appName);
  },
});
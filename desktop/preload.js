const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('minime', {
  resize: (expanded) => {
    ipcRenderer.send('minime:resize', expanded);
  },
});
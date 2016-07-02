var m = require('mithril');
var xhr = require('./xhr');
var hookRepo = require('./hookRepo');

module.exports = function(send, ctrl) {

  this.send = send;

  var handlers = {
    had: function(hook) {
      hookRepo.add(ctrl, hook);
      if (hook.action === 'cancel') ctrl.flushHooks(true);
      if (ctrl.vm.tab === 'real_time') m.redraw();
    },
    hrm: function(id) {
      hookRepo.remove(ctrl, id);
      if (ctrl.vm.tab === 'real_time') m.redraw();
    },
    hli: function(ids) {
      hookRepo.syncIds(ctrl, ids.split(','));
      if (ctrl.vm.tab === 'real_time') m.redraw();
    },
    reload_seeks: function() {
      if (ctrl.vm.tab === 'seeks') xhr.seeks().then(ctrl.setSeeks);
    }
  };

  this.receive = function(type, data) {
    if (this.music) this.music.receive(type, data);
    if (handlers[type]) {
      handlers[type](data);
      return true;
    }
    return false;
  }.bind(this);

  this.music = null;
  $('body').on('lichess.sound_set', function(e, set) {
    if (!this.music && set === 'music')
      lichess.loadScript('/assets/javascripts/music/lobby.js').then(function() {
        this.music = lichessLobbyMusic();
        ctrl.setMode('chart');
      }.bind(this));
    if (this.music && set !== 'music') this.music = null;
  }.bind(this));
};

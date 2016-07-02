var m = require('mithril');
var socket = require('./socket');
var xhr = require('./xhr');
var util = require('chessground').util;
var simul = require('./simul');

module.exports = function(env) {

  this.data = env.data;

  this.userId = env.userId;

  this.socket = new socket(env.socketSend, this);

  this.reload = function(data) {
    this.data = data;
    startWatching();
  }.bind(this);

  var alreadyWatching = [];
  var startWatching = function() {
    var newIds = this.data.pairings.map(function(p) {
      return p.game.id;
    }).filter(function(id) {
      return alreadyWatching.indexOf(id) === -1;
    });
    if (newIds.length) {
      setTimeout(function() {
        this.socket.send("startWatching", newIds.join(' '));
      }.bind(this), 1000);
      newIds.forEach(alreadyWatching.push.bind(alreadyWatching));
    }
  }.bind(this);
  startWatching();

  if (simul.createdByMe(this) && this.data.isCreated)
    lichess.storage.set('lichess.move_on', '1'); // hideous hack :D

  this.trans = lichess.trans(env.i18n);
};

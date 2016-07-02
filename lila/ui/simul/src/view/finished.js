var m = require('mithril');
var partial = require('chessground').util.partial;
var util = require('./util');
var pairings = require('./pairings');
var results = require('./results');

module.exports = function(ctrl) {
  return [
    m('div.title_tag', ctrl.trans('finished')),
    util.title(ctrl),
    results(ctrl),
    pairings(ctrl)
  ];
};

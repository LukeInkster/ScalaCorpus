var m = require('mithril');
var util = require('./util');

function ratingAt(title, opt, color) {
  return util.fMap(opt, function(r) {
    return [
      m('h2', [title + ': ', m('strong', color(r.int))]),
      util.gameLink(r.gameId, ['reached ', util.date(r.at)])
    ];
  }, [
    m('h2', title + ': '),
    m('span.na', util.noData)
  ]);
}

module.exports = function(d) {
  return [
    m('div.half', ratingAt('Highest rating', d.stat.highest, util.green)),
    m('div.half', ratingAt('Lowest rating', d.stat.lowest, util.red))
  ];
};

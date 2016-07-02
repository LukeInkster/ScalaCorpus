var m = require('mithril');
var util = require('../util');
var arrow = util.arrow;

module.exports = {
  key: 'queen',
  title: 'The queen',
  subtitle: 'Queen = rook + bishop.',
  image: util.assetUrl + 'images/learn/pieces/Q.svg',
  intro: 'Enters the most powerful chess piece. Her majesty the queen!',
  illustration: m('div.is2d.no-square',
    m('piece.queen.white')
  ),
  levels: [{
    goal: 'Grab all the stars!',
    fen: '8/8/8/8/8/8/4Q3/8 w - - 0 1',
    apples: 'e5 b8',
    nbMoves: 2,
    shapes: [arrow('e2e5'), arrow('e5b8')]
  }, {
    goal: 'Grab all the stars!',
    fen: '8/8/8/8/3Q4/8/8/8 w - - 0 1',
    apples: 'a3 f2 f8 h3',
    nbMoves: 4
  }, {
    goal: 'Grab all the stars!',
    fen: '8/8/8/8/2Q5/8/8/8 w - - 0 1',
    apples: 'a3 d6 f1 f8 g3 h6',
    nbMoves: 6
  }, {
    goal: 'Grab all the stars!',
    fen: '8/6q1/8/8/8/8/8/8 b - - 0 1',
    apples: 'a2 b5 d3 g1 g8 h2 h5',
    nbMoves: 7
  }, {
    goal: 'Grab all the stars!',
    fen: '8/8/8/8/8/8/8/4q3 b - - 0 1',
    apples: 'a6 d1 f2 f6 g6 g8 h1 h4',
    nbMoves: 9
  }].map(util.toLevel),
  complete: 'Congratulations! Queens have no secrets for you.'
};

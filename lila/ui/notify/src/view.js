var m = require('mithril');

function userFullName(u) {
  if (!u) return 'Anonymous';
  return u.title ? u.title + ' ' + u.name : u.name;
}

function genericNotification(notification, url, icon, content) {
  return m('a.site_notification', {
    class: notification.type + (notification.read ? '' : ' new'),
    href: url
  }, [
    m('i', {
      'data-icon': icon
    }),
    m('span.content', content)
  ]);
}

function drawTime(notification) {
  return m('time', {
    class: "moment-from-now",
    datetime: new Date(notification.date).toISOString()
  });
};

var handlers = {
  mentioned: {
    html: function(notification) {
      var content = notification.content;
      var url = "/forum/redirect/post/" + content.postId

      return genericNotification(notification, url, 'd', [
        m('span', [
          m('strong', userFullName(content.mentionedBy)),
          drawTime(notification)
        ]),
        m('span', ' mentioned you in « ' + content.topic + ' ».')
      ]);
    },
    text: function(n) {
      return userFullName(n.content.mentionedBy) +
        ' mentioned you in « ' + n.content.topic + ' ».';
    }
  },
  invitedStudy: {
    html: function(notification) {
      var content = notification.content;
      var url = "/study/" + content.studyId;

      return genericNotification(notification, url, '', [
        m('span', [
          m('strong', userFullName(content.invitedBy)),
          drawTime(notification)
        ]),
        m('span', ' invited you to « ' + content.studyName + ' ».')
      ]);
    },
    text: function(n) {
      return userFullName(n.content.invitedBy) +
        ' invited you to « ' + n.content.studyName + ' ».';
    }
  },
  privateMessage: {
    html: function(notification) {
      var content = notification.content;
      var url = "/inbox/" + content.thread.id + '#bottom';

      return genericNotification(notification, url, 'c', [
        m('span', [
          m('strong', userFullName(content.sender)),
          drawTime(notification)
        ]),
        m('span', content.text)
      ]);
    },
    text: function(n) {
      return userFullName(n.content.sender) + ': ' + n.content.text;
    }
  },
  qaAnswer: {
    html: function(notification) {
      var content = notification.content
      var q = content.question;
      var url = "/qa/" + q.id + "/" + q.slug + "#" + "answer-" + content.answerId;

      return genericNotification(notification, url, '&', [
        m('span', [
          m('strong', userFullName(content.answerer)),
          drawTime(notification)
        ]),
        m('span', " answered « " + q.title + "  ».")
      ]);
    },
    text: function(n) {
      return userFullName(n.content.answerer) + " answered « " + n.content.question.title + "  »."
    }
  },
  teamJoined: {
    html: function(notification) {
      var content = notification.content
      var url = "/team/" + content.id;

      return genericNotification(notification, url, 'f', [
        m('span', [
          m('strong', content.name),
          drawTime(notification)
        ]),
        m('span', "You are now part of the team.")
      ]);
    },
    text: function(n) {
      return "You have joined  « " + n.content.name + "  »."
    }
  },
  newBlogPost: {
    html: function(notification) {
      var content = notification.content
      var url = "/blog/" + content.id + "/" + content.slug;

      return genericNotification(notification, url, '*', [
        m('span', [
          m('strong', 'New blog post'),
          drawTime(notification)
        ]),
        m('span', content.title)
      ]);
    },
    text: function(n) {
      return n.content.title;
    }
  },
  limitedTournamentInvitation: {
    html: function(notification) {
      var url = '/tournament/limited-invitation';

      return genericNotification(notification, url, 'g', [
        m('span', [
          m('strong', 'Low rated tournament'),
          drawTime(notification)
        ]),
        m('span', 'A tournament you can win!')
      ]);
    },
    text: function(n) {
      return 'Game with ' + n.content.opponentName + '.';
    }
  },
  gameEnd: {
    html: function(notification) {
      var content = notification.content
      var url = "/" + content.id;
      var result;
      switch (content.win) {
        case true:
          result = 'Congratulations, you won!';
          break;
        case false:
          result = 'You lost!';
          break;
        default:
          result = "It's a draw.";
      }

      return genericNotification(notification, url, ';', [
        m('span', [
          m('strong', 'Game vs ' + userFullName(content.opponent)),
          drawTime(notification)
        ]),
        m('span', result)
      ]);
    },
    text: function(n) {
      var result;
      switch (n.content.win) {
        case true:
          result = 'Victory';
          break;
        case false:
          result = 'Defeat';
          break;
        default:
          result = 'Draw';
      }
      return result + ' vs ' + userFullName(n.content.opponent);
    }
  }
};

function drawNotification(notification) {
  var handler = handlers[notification.type];
  if (handler) return handler.html(notification);
}

function recentNotifications(ctrl) {
  return m('div.notifications', {
    class: ctrl.vm.scrolling ? 'scrolling' : '',
    config: function() {
      $('body').trigger('lichess.content_loaded');
    }
  }, ctrl.data.pager.currentPageResults.map(drawNotification));
}

function empty() {
  return m('div', {
    class: 'empty text',
    'data-icon': '',
  }, 'No notifications.');
}

module.exports = {
  html: function(ctrl) {

    if (ctrl.vm.initiating) return m('div.initiating', m.trust(lichess.spinnerHtml));

    var pager = ctrl.data.pager;
    var nb = pager.currentPageResults.length;

    return [
      pager.previousPage ? m('div.pager.prev', {
        'data-icon': 'S',
        onclick: ctrl.previousPage
      }) : (pager.nextPage ? m('div.pager.prev.disabled', {
        'data-icon': 'S',
      }) : null),
      nb ? recentNotifications(ctrl) : empty(),
      pager.nextPage ? m('div.pager.next', {
        'data-icon': 'R',
        onclick: ctrl.nextPage
      }) : null
    ];
  },
  text: function(notification) {
    var handler = handlers[notification.type];
    if (handler) return handler.text(notification);
  }
};

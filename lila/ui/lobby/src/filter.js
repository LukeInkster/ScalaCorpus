module.exports = function(ctrl, hooks) {
  var f = ctrl.data.filter;
  var seen = [], hidden = 0, visible = [];
  hooks.forEach(function(hook) {
    var variant = hook.variant;
    if (hook.action === 'cancel') visible.push(hook);
    else {
      if (!$.fp.contains(f.variant, variant) || !$.fp.contains(f.mode, hook.ra || 0) || !$.fp.contains(f.speed, hook.s) ||
        (f.rating && (!hook.rating || (hook.rating < f.rating[0] || hook.rating > f.rating[1])))) {
        hidden++;
      } else {
        var hash = hook.ra + variant + hook.t + hook.rating;
        if (!$.fp.contains(seen, hash)) visible.push(hook);
        seen.push(hash);
      }
    }
  });
  return {
    visible: visible,
    hidden: hidden
  };
}

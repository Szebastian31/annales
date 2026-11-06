'use strict';
// ui/AnnalesApp.kt: the five tabs, and what's open over them.

const U = {
  tab: 'today', back: 0, openDay: null, dayColl: null, dayEdit: false, openColl: null, month: null, pages: false,
  collFilter: null, range: 'MONTH', insTab: 0, openLink: null, moreFeels: false, sel: {}, filled: {},
};
const TABS = [['today', tr('Today')], ['journal', tr('Journal')], ['collections', tr('Collections')], ['insights', tr('Insights')], ['profile', tr('Profile')]];

const App = {
  shown: null,
  render() {
    const main = document.getElementById('main');
    const coll = U.openColl != null ? M.collections.find(c => c.id === U.openColl) : null;
    if (U.openColl != null && !coll) U.openColl = null;
    const key = U.openDay && U.dayEdit ? 'day:' + U.openDay : coll ? 'coll:' + coll.id : U.tab + (U.tab === 'today' ? U.back : '');
    const top = main.scrollTop;
    let screen;
    if (U.openDay && U.dayEdit) screen = openDayScreen();
    else if (coll) screen = collectionScreen(coll);
    else screen = { today: () => h('div', null, daySwitch(), dayPage(D.plus(D.today(), -U.back))), journal: journalScreen,
      collections: collectionsScreen, insights: insightsScreen, profile: profileScreen }[U.tab]();
    main.replaceChildren(screen);
    main.scrollTop = key === this.shown ? top : 0;
    this.shown = key;

    document.getElementById('tabs').replaceChildren(...TABS.map(([k, label]) => h('button.tab' + (k === U.tab ? '.on' : ''), {
      onclick: () => { U.tab = k; U.openDay = null; U.openColl = null; App.render(); },
    }, h('span', { html: TAB_ICONS[k] }), label, h('i'))));
    Layers.refreshAll();
  },
};

(async () => {
  try {
    await M.load();
  } catch (e) {
    showError(tr("Couldn't open the journal storage ({error}). Private Browsing keeps nothing; open Annales in a normal tab.", { error: e.message || e }));
    return;
  }
  M.onChange = () => App.render();
  App.render();
  // a new day while the app sat open
  let today = D.today();
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && D.today() !== today) { today = D.today(); U.filled = {}; App.render(); }
  });
  if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('sw.js').catch(() => {});
})();

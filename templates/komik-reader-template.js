// ==UserScript==
// @name         [Nama Sumber] Komik Reader
// @version      1.0.0
// @description  Template kompatibel Rhino untuk katalog, detail, chapter, dan reader komik.
// @category     Komik
// @settings     readingMode: select: options=single,scroll,webtoon, default=single, label=Mode Baca
// @settings     autoSaveProgress: boolean: default=true, label=Simpan Progress Otomatis
// ==/UserScript==

var SOURCE = { name: "Nama Sumber", baseUrl: "https://website.com", selectors: {
    comicGrid: ".comic-card", comicTitle: ".title a", comicCover: "img", comicStatus: ".status",
    detailContainer: ".comic-detail", chapterList: ".chapter-item", readerImages: ".page"
} };
var State = { currentComic: null, currentChapter: null, pages: [], readingProgress: {}, bookmarks: [] };
function normalizeUrl(url) { if (!url) return ""; if (/^https?:/.test(url)) return url; if (/^\\/\\//.test(url)) return "https:" + url; return SOURCE.baseUrl + url; }
function storageKey(key) { return "comic:" + key; }
function loadState() { try { State.bookmarks = JSON.parse(RoCat.storage.get(storageKey("bookmarks")) || "[]"); } catch (e) { State.bookmarks = []; } }
function saveState() { RoCat.storage.set(storageKey("bookmarks"), JSON.stringify(State.bookmarks)); }
function toggleBookmark() { if (!State.currentComic) return; var i = State.bookmarks.indexOf(State.currentComic.id); if (i < 0) State.bookmarks.push(State.currentComic.id); else State.bookmarks.splice(i, 1); saveState(); renderDetail(State.currentComic); }
function saveProgress(comicId, chapter) { State.readingProgress[comicId] = chapter; if (RoCat.settings.autoSaveProgress !== false) RoCat.storage.set(storageKey("progress:" + comicId), String(chapter)); }
function renderMain() { RoCat.render([{type:"clear"},{type:"text",content:"📚 " + SOURCE.name,style:"title"},{type:"layout",layout:"row",padding:8,margin:16,spacing:8,align:"center",children:[{type:"autocomplete",id:"search",hint:"Cari komik...",flex:3},{type:"button",label:"Cari",fn:"doSearch",flex:1}]},{type:"layout",layout:"row",margin:16,spacing:8,children:[{type:"button",label:"Populer",fn:"loadPopular"},{type:"button",label:"Terbaru",fn:"loadLatest"},{type:"button",label:"Favorit",fn:"loadFavorites"}]}]); loadPopular(); }
function renderDetail(comic) { RoCatUI.clear(); RoCatUI.addText(comic.title, "heading"); RoCatUI.addImage(comic.cover, comic.title, true); RoCatUI.addBadgeGroup(JSON.stringify([comic.status, comic.type].concat(comic.genres || []))); RoCatUI.addHtmlPreview(comic.synopsis || "", "Sinopsis"); RoCatUI.addButton("Bookmark", "toggleBookmark"); RoCatUI.addGrid(3, JSON.stringify(comic.chapters || []), "openChapter"); }
function renderReader(pages) { RoCatUI.clear(); var mode = RoCat.settings.readingMode || "single"; if (mode === "single") { RoCatUI.addImage(pages[0], "1/" + pages.length, true); } else { for (var i=0;i<pages.length;i++) RoCatUI.addImage(pages[i], "", mode !== "webtoon"); } }
function onLaunch() { loadState(); renderMain(); }
try { onLaunch(); } catch (e) { RoCatUI.log("Init error: " + e.message); }

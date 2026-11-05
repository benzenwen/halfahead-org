goog.provide('halfahead.lab.draft');
halfahead.lab.draft.ghost_life_ms = (1500);
halfahead.lab.draft.between_QMARK_ = (function halfahead$lab$draft$between_QMARK_(lo,hi,u){
return (((cljs.core.compare(lo,u) <= (0))) && ((cljs.core.compare(u,hi) <= (0))));
});
halfahead.lab.draft.high_surrogate_QMARK_ = (function halfahead$lab$draft$high_surrogate_QMARK_(u){
return halfahead.lab.draft.between_QMARK_("\uD800","\uDBFF",u);
});
halfahead.lab.draft.low_surrogate_QMARK_ = (function halfahead$lab$draft$low_surrogate_QMARK_(u){
return halfahead.lab.draft.between_QMARK_("\uDC00","\uDFFF",u);
});
/**
 * s split into glyph chars: single UTF-16 units, except that a high
 *   surrogate followed by a low one stays whole. A lone surrogate is its own
 *   char, so the chars always join back to exactly s.
 */
halfahead.lab.draft.glyph_chars = (function halfahead$lab$draft$glyph_chars(s){
var n = cljs.core.count(s);
var i = (0);
var out = cljs.core.transient$(cljs.core.PersistentVector.EMPTY);
while(true){
if((i < n)){
var w = ((((((i + (1)) < n)) && (((halfahead.lab.draft.high_surrogate_QMARK_(cljs.core.subs.cljs$core$IFn$_invoke$arity$3(s,i,(i + (1))))) && (halfahead.lab.draft.low_surrogate_QMARK_(cljs.core.subs.cljs$core$IFn$_invoke$arity$3(s,(i + (1)),(i + (2)))))))))?(2):(1));
var G__15476 = (i + w);
var G__15477 = cljs.core.conj_BANG_.cljs$core$IFn$_invoke$arity$2(out,cljs.core.subs.cljs$core$IFn$_invoke$arity$3(s,i,(i + w)));
i = G__15476;
out = G__15477;
continue;
} else {
return cljs.core.persistent_BANG_(out);
}
break;
}
});
halfahead.lab.draft.letter_or_digit = (new RegExp("^[\\p{L}\\p{Nd}]$","u"));
/**
 * Letters, decimal digits, apostrophes and hyphens make words.
 */
halfahead.lab.draft.word_char_QMARK_ = (function halfahead$lab$draft$word_char_QMARK_(c){
var or__5162__auto__ = cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(c,"'");
if(or__5162__auto__){
return or__5162__auto__;
} else {
var or__5162__auto____$1 = cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(c,"\u2019");
if(or__5162__auto____$1){
return or__5162__auto____$1;
} else {
var or__5162__auto____$2 = cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(c,"-");
if(or__5162__auto____$2){
return or__5162__auto____$2;
} else {
return halfahead.lab.draft.letter_or_digit.test(c);
}
}
}
});
halfahead.lab.draft.common_prefix_count = (function halfahead$lab$draft$common_prefix_count(as,bs){
var n = cljs.core.min.cljs$core$IFn$_invoke$arity$2(cljs.core.count(as),cljs.core.count(bs));
var i = (0);
while(true){
if((((i < n)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(as,i),cljs.core.nth.cljs$core$IFn$_invoke$arity$2(bs,i))))){
var G__15478 = (i + (1));
i = G__15478;
continue;
} else {
return i;
}
break;
}
});
/**
 * Matching chars at the ends of as and bs, never reaching into the first
 *   `taken` of either (the common prefix), so the two never overlap.
 */
halfahead.lab.draft.common_suffix_count = (function halfahead$lab$draft$common_suffix_count(as,bs,taken){
var na = cljs.core.count(as);
var nb = cljs.core.count(bs);
var n = (cljs.core.min.cljs$core$IFn$_invoke$arity$2(na,nb) - taken);
var k = (0);
while(true){
if((((k < n)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(as,((na - k) - (1))),cljs.core.nth.cljs$core$IFn$_invoke$arity$2(bs,((nb - k) - (1))))))){
var G__15481 = (k + (1));
k = G__15481;
continue;
} else {
return k;
}
break;
}
});
halfahead.lab.draft.prune_ghosts = (function halfahead$lab$draft$prune_ghosts(ghosts,now_ms){
return cljs.core.filterv((function (p1__15422_SHARP_){
return ((now_ms - new cljs.core.Keyword(null,"died-ms","died-ms",1637752567).cljs$core$IFn$_invoke$arity$1(p1__15422_SHARP_)) <= halfahead.lab.draft.ghost_life_ms);
}),ghosts);
});
halfahead.lab.draft.keep_live_cues = (function halfahead$lab$draft$keep_live_cues(cues,glyphs){
var live = cljs.core.into.cljs$core$IFn$_invoke$arity$3(cljs.core.PersistentHashSet.EMPTY,cljs.core.map.cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"id","id",-1388402092)),glyphs);
return cljs.core.into.cljs$core$IFn$_invoke$arity$3(cljs.core.PersistentVector.EMPTY,cljs.core.keep.cljs$core$IFn$_invoke$arity$1((function (cue){
var ids = cljs.core.filterv(live,new cljs.core.Keyword(null,"glyph-ids","glyph-ids",1144320035).cljs$core$IFn$_invoke$arity$1(cue));
if(cljs.core.seq(ids)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(cue,new cljs.core.Keyword(null,"glyph-ids","glyph-ids",1144320035),ids);
} else {
return null;
}
})),cues);
});
/**
 * Every maximal run of word glyphs followed by a non-word glyph, in text
 *   order, as {:ids :word :following}. A run at the very end is unfinished.
 */
halfahead.lab.draft.finished_words = (function halfahead$lab$draft$finished_words(glyphs){
var wordy = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(cljs.core.comp.cljs$core$IFn$_invoke$arity$2(halfahead.lab.draft.word_char_QMARK_,new cljs.core.Keyword(null,"char","char",-641587586)),glyphs);
var n = cljs.core.count(glyphs);
var i = (0);
var start = null;
var out = cljs.core.PersistentVector.EMPTY;
while(true){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(i,n)){
return out;
} else {
if(cljs.core.truth_(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(wordy,i))){
var G__15484 = (i + (1));
var G__15485 = (function (){var or__5162__auto__ = start;
if(cljs.core.truth_(or__5162__auto__)){
return or__5162__auto__;
} else {
return i;
}
})();
var G__15486 = out;
i = G__15484;
start = G__15485;
out = G__15486;
continue;
} else {
if(cljs.core.truth_(start)){
var run = cljs.core.subvec.cljs$core$IFn$_invoke$arity$3(glyphs,start,i);
var G__15491 = (i + (1));
var G__15492 = null;
var G__15493 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(out,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"ids","ids",-998535796),cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"id","id",-1388402092),run),new cljs.core.Keyword(null,"word","word",-420123725),cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"char","char",-641587586),run)),new cljs.core.Keyword(null,"following","following",-2049193617),new cljs.core.Keyword(null,"char","char",-641587586).cljs$core$IFn$_invoke$arity$1(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(glyphs,i))], null));
i = G__15491;
start = G__15492;
out = G__15493;
continue;
} else {
var G__15494 = (i + (1));
var G__15495 = null;
var G__15496 = out;
i = G__15494;
start = G__15495;
out = G__15496;
continue;

}
}
}
break;
}
});
halfahead.lab.draft.checked_cue = (function halfahead$lab$draft$checked_cue(cue,word){
var temp__5823__auto___15497 = halfahead.schema.explain(new cljs.core.Keyword("ha","word-cue","ha/word-cue",1120462584),cue);
if(cljs.core.truth_(temp__5823__auto___15497)){
var explain_15498 = temp__5823__auto___15497;
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2("cue-fn returned an invalid :ha/word-cue",new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword("ha.lab","invalid-cue","ha.lab/invalid-cue",1542856229),new cljs.core.Keyword(null,"word","word",-420123725),word,new cljs.core.Keyword(null,"cue","cue",1555624311),cue,new cljs.core.Keyword(null,"explain","explain",484226146),explain_15498], null));
} else {
}

return cue;
});
halfahead.lab.draft.cue_finished_words = (function halfahead$lab$draft$cue_finished_words(cues,glyphs,now_ms,cue_fn){
var cued = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(cljs.core.comp.cljs$core$IFn$_invoke$arity$2(cljs.core.set,new cljs.core.Keyword(null,"glyph-ids","glyph-ids",1144320035)),cues);
var already_QMARK_ = (function (ids){
return cljs.core.some((function (s){
return cljs.core.every_QMARK_(s,ids);
}),cued);
});
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (cues__$1,p__15423){
var map__15425 = p__15423;
var map__15425__$1 = cljs.core.__destructure_map(map__15425);
var ids = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15425__$1,new cljs.core.Keyword(null,"ids","ids",-998535796));
var word = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15425__$1,new cljs.core.Keyword(null,"word","word",-420123725));
var following = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15425__$1,new cljs.core.Keyword(null,"following","following",-2049193617));
if(cljs.core.truth_(already_QMARK_(ids))){
return cues__$1;
} else {
return cljs.core.conj.cljs$core$IFn$_invoke$arity$2(cues__$1,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"glyph-ids","glyph-ids",1144320035),ids,new cljs.core.Keyword(null,"cue","cue",1555624311),halfahead.lab.draft.checked_cue((cue_fn.cljs$core$IFn$_invoke$arity$2 ? cue_fn.cljs$core$IFn$_invoke$arity$2(word,following) : cue_fn.call(null,word,following)),word),new cljs.core.Keyword(null,"at-ms","at-ms",-377491401),now_ms], null));
}
}),cues,halfahead.lab.draft.finished_words(glyphs));
});
/**
 * A draft with nothing typed yet.
 */
halfahead.lab.draft.empty_draft = (function halfahead$lab$draft$empty_draft(){
return new cljs.core.PersistentArrayMap(null, 5, [new cljs.core.Keyword(null,"text","text",-1790561697),"",new cljs.core.Keyword(null,"glyphs","glyphs",-533936706),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"ghosts","ghosts",665819293),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"cues","cues",1596514991),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"next-id","next-id",-224240762),(0)], null);
});
/**
 * draft without the ghosts that have lingered past their time at now-ms;
 *   for frames between edits.
 */
halfahead.lab.draft.prune = (function halfahead$lab$draft$prune(draft,now_ms){
return cljs.core.update.cljs$core$IFn$_invoke$arity$4(draft,new cljs.core.Keyword(null,"ghosts","ghosts",665819293),halfahead.lab.draft.prune_ghosts,now_ms);
});
/**
 * draft after its text became new-text at now-ms. Glyphs in the unchanged
 *   start and end keep their ids and birth times; removed glyphs become
 *   ghosts; inserted chars become glyphs with fresh ids. Every finished word
 *   not already under one cue gets (cue-fn word following), which must
 *   return a :ha/word-cue, placed on that word's glyphs.
 */
halfahead.lab.draft.edit = (function halfahead$lab$draft$edit(p__15432,new_text,now_ms,cue_fn){
var map__15434 = p__15432;
var map__15434__$1 = cljs.core.__destructure_map(map__15434);
var draft = map__15434__$1;
var text = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15434__$1,new cljs.core.Keyword(null,"text","text",-1790561697));
var glyphs = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15434__$1,new cljs.core.Keyword(null,"glyphs","glyphs",-533936706));
var ghosts = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15434__$1,new cljs.core.Keyword(null,"ghosts","ghosts",665819293));
var cues = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15434__$1,new cljs.core.Keyword(null,"cues","cues",1596514991));
var next_id = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15434__$1,new cljs.core.Keyword(null,"next-id","next-id",-224240762));
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(text,new_text)){
return halfahead.lab.draft.prune(draft,now_ms);
} else {
var old_chars = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"char","char",-641587586),glyphs);
var new_chars = halfahead.lab.draft.glyph_chars(new_text);
var pre = halfahead.lab.draft.common_prefix_count(old_chars,new_chars);
var suf = halfahead.lab.draft.common_suffix_count(old_chars,new_chars,pre);
var removed_to = (cljs.core.count(glyphs) - suf);
var inserted = cljs.core.subvec.cljs$core$IFn$_invoke$arity$3(new_chars,pre,(cljs.core.count(new_chars) - suf));
var born = cljs.core.map_indexed.cljs$core$IFn$_invoke$arity$2((function (k,c){
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"id","id",-1388402092),(next_id + k),new cljs.core.Keyword(null,"char","char",-641587586),c,new cljs.core.Keyword(null,"born-ms","born-ms",-2053429117),now_ms], null);
}),inserted);
var glyphs_SINGLEQUOTE_ = cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.subvec.cljs$core$IFn$_invoke$arity$3(glyphs,(0),pre),born),cljs.core.subvec.cljs$core$IFn$_invoke$arity$2(glyphs,removed_to));
var died = cljs.core.map.cljs$core$IFn$_invoke$arity$3((function (i,p__15438){
var map__15465 = p__15438;
var map__15465__$1 = cljs.core.__destructure_map(map__15465);
var id = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15465__$1,new cljs.core.Keyword(null,"id","id",-1388402092));
var char$ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15465__$1,new cljs.core.Keyword(null,"char","char",-641587586));
return new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"id","id",-1388402092),id,new cljs.core.Keyword(null,"char","char",-641587586),char$,new cljs.core.Keyword(null,"died-ms","died-ms",1637752567),now_ms,new cljs.core.Keyword(null,"index","index",-1531685915),i], null);
}),cljs.core.range.cljs$core$IFn$_invoke$arity$2(pre,removed_to),cljs.core.subvec.cljs$core$IFn$_invoke$arity$3(glyphs,pre,removed_to));
return new cljs.core.PersistentArrayMap(null, 5, [new cljs.core.Keyword(null,"text","text",-1790561697),new_text,new cljs.core.Keyword(null,"glyphs","glyphs",-533936706),glyphs_SINGLEQUOTE_,new cljs.core.Keyword(null,"ghosts","ghosts",665819293),cljs.core.into.cljs$core$IFn$_invoke$arity$2(halfahead.lab.draft.prune_ghosts(ghosts,now_ms),died),new cljs.core.Keyword(null,"cues","cues",1596514991),halfahead.lab.draft.cue_finished_words(halfahead.lab.draft.keep_live_cues(cues,glyphs_SINGLEQUOTE_),glyphs_SINGLEQUOTE_,now_ms,cue_fn),new cljs.core.Keyword(null,"next-id","next-id",-224240762),(next_id + cljs.core.count(inserted))], null);
}
});

//# sourceMappingURL=halfahead.lab.draft.js.map

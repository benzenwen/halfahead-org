goog.provide('halfahead.lab.cues');
halfahead.lab.cues.max_word_length = (64);
halfahead.lab.cues.glossary_hit = (function halfahead$lab$cues$glossary_hit(word){
var w = clojure.string.lower_case(word);
var or__5162__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(halfahead.lab.glossary.entries,w);
if(cljs.core.truth_(or__5162__auto__)){
return or__5162__auto__;
} else {
if(clojure.string.ends_with_QMARK_(w,"s")){
return cljs.core.get.cljs$core$IFn$_invoke$arity$2(halfahead.lab.glossary.entries,cljs.core.subs.cljs$core$IFn$_invoke$arity$3(w,(0),(((w).length) - (1))));
} else {
return null;
}
}
});
halfahead.lab.cues.punctuated = (function halfahead$lab$cues$punctuated(following){
var G__7788 = following;
switch (G__7788) {
case "!":
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"effect","effect",347343289),new cljs.core.Keyword(null,"quake","quake",-586699081),new cljs.core.Keyword(null,"intensity","intensity",1142770863),0.7], null);

break;
case "?":
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"effect","effect",347343289),new cljs.core.Keyword(null,"wobble","wobble",-1490235021),new cljs.core.Keyword(null,"intensity","intensity",1142770863),0.5], null);

break;
default:
return null;

}
});
halfahead.lab.cues.cased_letter_QMARK_ = (function halfahead$lab$cues$cased_letter_QMARK_(c){
var s = (""+cljs.core.str.cljs$core$IFn$_invoke$arity$1(c));
return cljs.core.not_EQ_.cljs$core$IFn$_invoke$arity$2(clojure.string.upper_case(s),clojure.string.lower_case(s));
});
halfahead.lab.cues.all_capitals_QMARK_ = (function halfahead$lab$cues$all_capitals_QMARK_(word){
return ((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(word,clojure.string.upper_case(word))) && (((2) <= cljs.core.count(cljs.core.filter.cljs$core$IFn$_invoke$arity$2(halfahead.lab.cues.cased_letter_QMARK_,word)))));
});
halfahead.lab.cues.shaped = (function halfahead$lab$cues$shaped(word){
if(halfahead.lab.cues.all_capitals_QMARK_(word)){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"effect","effect",347343289),new cljs.core.Keyword(null,"launch","launch",1165376825),new cljs.core.Keyword(null,"intensity","intensity",1142770863),0.8], null);
} else {
if(((12) <= cljs.core.count(word))){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"effect","effect",347343289),new cljs.core.Keyword(null,"shimmer","shimmer",2123148747),new cljs.core.Keyword(null,"intensity","intensity",1142770863),0.5], null);
} else {
if(cljs.core.truth_(cljs.core.re_matches(/[0-9]+/,word))){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"effect","effect",347343289),new cljs.core.Keyword(null,"pop","pop",-1734778776),new cljs.core.Keyword(null,"intensity","intensity",1142770863),0.4], null);
} else {
return null;
}
}
}
});
/**
 * String comparison is by UTF-16 unit on both hosts, so no interop is needed.
 */
halfahead.lab.cues.high_surrogate_QMARK_ = (function halfahead$lab$cues$high_surrogate_QMARK_(c){
var s = (""+cljs.core.str.cljs$core$IFn$_invoke$arity$1(c));
return ((((0) <= cljs.core.compare(s,"\uD800"))) && ((cljs.core.compare(s,"\uDBFF") <= (0))));
});
/**
 * Leading whitespace goes first, so a long run of it cannot truncate a
 *   non-blank word down to a blank one; the cut never splits a surrogate pair.
 */
halfahead.lab.cues.cue_word = (function halfahead$lab$cues$cue_word(word){
var w = clojure.string.triml(word);
if((((w).length) <= halfahead.lab.cues.max_word_length)){
return w;
} else {
var end = ((halfahead.lab.cues.high_surrogate_QMARK_(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(w,(halfahead.lab.cues.max_word_length - (1)))))?(halfahead.lab.cues.max_word_length - (1)):halfahead.lab.cues.max_word_length);
return cljs.core.subs.cljs$core$IFn$_invoke$arity$3(w,(0),end);
}
});
/**
 * The cue for a just-finished word, as typed, and the one-character string
 *   that followed it. Rules, first match wins: Half Ahead's glossary (a
 *   following "!" adds 0.2, capped at 1), then the following punctuation,
 *   then the word's shape, then a quiet default. Pure and local; a model, if
 *   ever added, belongs behind this function as a fallback before the default.
 *   Never throws for a non-blank word.
 */
halfahead.lab.cues.word_cue = (function halfahead$lab$cues$word_cue(word,following){
var vec__7802 = (function (){var temp__5821__auto__ = halfahead.lab.cues.glossary_hit(word);
if(cljs.core.truth_(temp__5821__auto__)){
var map__7805 = temp__5821__auto__;
var map__7805__$1 = cljs.core.__destructure_map(map__7805);
var effect = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7805__$1,new cljs.core.Keyword(null,"effect","effect",347343289));
var intensity = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7805__$1,new cljs.core.Keyword(null,"intensity","intensity",1142770863));
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"effect","effect",347343289),effect,new cljs.core.Keyword(null,"intensity","intensity",1142770863),((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("!",following))?cljs.core.min.cljs$core$IFn$_invoke$arity$2(1.0,(intensity + 0.2)):intensity)], null),new cljs.core.Keyword(null,"glossary","glossary",1104312830)], null);
} else {
var temp__5821__auto____$1 = halfahead.lab.cues.punctuated(following);
if(cljs.core.truth_(temp__5821__auto____$1)){
var cue = temp__5821__auto____$1;
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [cue,new cljs.core.Keyword(null,"punctuation","punctuation",-1466717709)], null);
} else {
var temp__5821__auto____$2 = halfahead.lab.cues.shaped(word);
if(cljs.core.truth_(temp__5821__auto____$2)){
var cue = temp__5821__auto____$2;
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [cue,new cljs.core.Keyword(null,"shape","shape",1190694006)], null);
} else {
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"effect","effect",347343289),new cljs.core.Keyword(null,"pop","pop",-1734778776),new cljs.core.Keyword(null,"intensity","intensity",1142770863),0.3], null),new cljs.core.Keyword(null,"default","default",-1987822328)], null);
}
}
}
})();
var cue = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7802,(0),null);
var source = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7802,(1),null);
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$variadic(cue,new cljs.core.Keyword(null,"word","word",-420123725),halfahead.lab.cues.cue_word(word),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"source","source",-433931539),source], 0));
});

//# sourceMappingURL=halfahead.lab.cues.js.map

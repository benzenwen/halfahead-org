goog.provide('halfahead.lab.pose');
halfahead.lab.pose.tau = (2.0 * Math.PI);
halfahead.lab.pose.sin = (function halfahead$lab$pose$sin(x){
return Math.sin(x);
});
halfahead.lab.pose.cos = (function halfahead$lab$pose$cos(x){
return Math.cos(x);
});
halfahead.lab.pose.exp = (function halfahead$lab$pose$exp(x){
return Math.exp(x);
});
halfahead.lab.pose.clamp = (function halfahead$lab$pose$clamp(lo,hi,x){
return cljs.core.max.cljs$core$IFn$_invoke$arity$2(lo,cljs.core.min.cljs$core$IFn$_invoke$arity$2(hi,x));
});
halfahead.lab.pose.smoothstep = (function halfahead$lab$pose$smoothstep(p){
return ((p * p) * (3.0 - (2.0 * p)));
});
/**
 * A fixed angle per glyph id and salt, so jitter is deterministic and the
 *   same on both hosts. Small integers only: JavaScript has no 64-bit ints.
 */
halfahead.lab.pose.phase = (function halfahead$lab$pose$phase(id,salt){
var h = cljs.core.mod(((cljs.core.mod(id,(65521)) * (7919)) + (salt * (104729))),(65521));
return (halfahead.lab.pose.tau * (h / 65521.0));
});
halfahead.lab.pose.line_height = 1.35;
/**
 * Every character 0.6 em wide: the fallback before a font is measured.
 */
halfahead.lab.pose.default_advance = (function halfahead$lab$pose$default_advance(_c){
return 0.6;
});
halfahead.lab.pose.line_y = (function halfahead$lab$pose$line_y(n){
if((n === (0))){
return 0.0;
} else {
return ((-1.0 * halfahead.lab.pose.line_height) * n);
}
});
/**
 * Indexes of chars grouped into words (runs of non-whitespace) and single
 *   whitespace chars, in order.
 */
halfahead.lab.pose.tokens = (function halfahead$lab$pose$tokens(chars){
return cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__15424_SHARP_){
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(cljs.core.first,p1__15424_SHARP_);
}),cljs.core.partition_by.cljs$core$IFn$_invoke$arity$2((function (p__15426){
var vec__15427 = p__15426;
var i = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15427,(0),null);
var c = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15427,(1),null);
if(clojure.string.blank_QMARK_(c)){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"space","space",348133475),i], null);
} else {
return new cljs.core.Keyword(null,"word","word",-420123725);
}
}),cljs.core.map_indexed.cljs$core$IFn$_invoke$arity$2(cljs.core.vector,chars)));
});
/**
 * One {:x :y} per char, left-aligned from x 0. A newline starts the next
 *   line; a word that would cross width-em moves whole to the next line, and
 *   a word wider than width-em breaks wherever it must. Line n has
 *   y = -1.35 n. advance is a fn char -> em width.
 */
halfahead.lab.pose.layout = (function halfahead$lab$pose$layout(chars,advance,width_em){
var chars__$1 = cljs.core.vec(chars);
var width = width_em;
var adv = (function (p1__15430_SHARP_){
var G__15433 = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(chars__$1,p1__15430_SHARP_);
return (advance.cljs$core$IFn$_invoke$arity$1 ? advance.cljs$core$IFn$_invoke$arity$1(G__15433) : advance.call(null,G__15433));
});
var toks = halfahead.lab.pose.tokens(chars__$1);
var x = 0.0;
var n = (0);
var out = cljs.core.transient$(cljs.core.vec(cljs.core.repeat.cljs$core$IFn$_invoke$arity$2(cljs.core.count(chars__$1),null)));
while(true){
var temp__5821__auto__ = cljs.core.first(toks);
if(cljs.core.truth_(temp__5821__auto__)){
var vec__15461 = temp__5821__auto__;
var i0 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15461,(0),null);
var tok = vec__15461;
var c = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(chars__$1,i0);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("\n",c)){
var G__15611 = cljs.core.rest(toks);
var G__15612 = 0.0;
var G__15613 = (n + (1));
var G__15614 = cljs.core.assoc_BANG_.cljs$core$IFn$_invoke$arity$3(out,i0,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"x","x",2099068185),x,new cljs.core.Keyword(null,"y","y",-1757859776),halfahead.lab.pose.line_y(n)], null));
toks = G__15611;
x = G__15612;
n = G__15613;
out = G__15614;
continue;
} else {
if(clojure.string.blank_QMARK_(c)){
var x_SINGLEQUOTE_ = (x + adv(i0));
var G__15615 = cljs.core.rest(toks);
var G__15616 = x_SINGLEQUOTE_;
var G__15617 = n;
var G__15618 = cljs.core.assoc_BANG_.cljs$core$IFn$_invoke$arity$3(out,i0,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"x","x",2099068185),x,new cljs.core.Keyword(null,"y","y",-1757859776),halfahead.lab.pose.line_y(n)], null));
toks = G__15615;
x = G__15616;
n = G__15617;
out = G__15618;
continue;
} else {
var w = cljs.core.reduce.cljs$core$IFn$_invoke$arity$2(cljs.core._PLUS_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(adv,tok));
var vec__15468 = (((((x > (0))) && ((((w <= width)) && (((x + w) > width))))))?new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [0.0,(n + (1))], null):new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [x,n], null));
var x__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15468,(0),null);
var n__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15468,(1),null);
var vec__15471 = cljs.core.reduce.cljs$core$IFn$_invoke$arity$3(((function (toks,x,n,out,w,vec__15468,x__$1,n__$1,c,vec__15461,i0,tok,temp__5821__auto__,chars__$1,width,adv){
return (function (p__15475,i){
var vec__15499 = p__15475;
var x__$2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15499,(0),null);
var n__$2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15499,(1),null);
var out__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15499,(2),null);
var a = adv(i);
var vec__15503 = (((((x__$2 > (0))) && (((x__$2 + a) > width))))?new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [0.0,(n__$2 + (1))], null):new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [x__$2,n__$2], null));
var x__$3 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15503,(0),null);
var n__$3 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15503,(1),null);
return new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [(x__$3 + a),n__$3,cljs.core.assoc_BANG_.cljs$core$IFn$_invoke$arity$3(out__$1,i,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"x","x",2099068185),x__$3,new cljs.core.Keyword(null,"y","y",-1757859776),halfahead.lab.pose.line_y(n__$3)], null))], null);
});})(toks,x,n,out,w,vec__15468,x__$1,n__$1,c,vec__15461,i0,tok,temp__5821__auto__,chars__$1,width,adv))
,new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [x__$1,n__$1,out], null),tok);
var x__$2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15471,(0),null);
var n__$2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15471,(1),null);
var out__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15471,(2),null);
var G__15619 = cljs.core.rest(toks);
var G__15620 = x__$2;
var G__15621 = n__$2;
var G__15622 = out__$1;
toks = G__15619;
x = G__15620;
n = G__15621;
out = G__15622;
continue;

}
}
} else {
return cljs.core.persistent_BANG_(out);
}
break;
}
});
/**
 * Where the next character would go: after the last char, or at the start
 *   of the next line when the text ends with a newline.
 */
halfahead.lab.pose.end_position = (function halfahead$lab$pose$end_position(chars,positions,advance){
var temp__5821__auto__ = cljs.core.peek(positions);
if(cljs.core.truth_(temp__5821__auto__)){
var map__15510 = temp__5821__auto__;
var map__15510__$1 = cljs.core.__destructure_map(map__15510);
var x = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15510__$1,new cljs.core.Keyword(null,"x","x",2099068185));
var y = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15510__$1,new cljs.core.Keyword(null,"y","y",-1757859776));
var c = cljs.core.peek(chars);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("\n",c)){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"x","x",2099068185),0.0,new cljs.core.Keyword(null,"y","y",-1757859776),(y - halfahead.lab.pose.line_height)], null);
} else {
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"x","x",2099068185),(x + (advance.cljs$core$IFn$_invoke$arity$1 ? advance.cljs$core$IFn$_invoke$arity$1(c) : advance.call(null,c))),new cljs.core.Keyword(null,"y","y",-1757859776),y], null);
}
} else {
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"x","x",2099068185),0.0,new cljs.core.Keyword(null,"y","y",-1757859776),0.0], null);
}
});
halfahead.lab.pose.birth_ms = (400);
halfahead.lab.pose.spring = (function (){var zeta = 0.517;
var omega = (4.6 / (zeta * halfahead.lab.pose.birth_ms));
return new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"zeta","zeta",257464678),zeta,new cljs.core.Keyword(null,"decay","decay",1036712184),(zeta * omega),new cljs.core.Keyword(null,"omega-d","omega-d",1649460506),(omega * Math.sqrt((1.0 - (zeta * zeta)))),new cljs.core.Keyword(null,"ratio","ratio",-926560044),(zeta / Math.sqrt((1.0 - (zeta * zeta))))], null);
})();
halfahead.lab.pose.birth = (function halfahead$lab$pose$birth(age){
if((age < halfahead.lab.pose.birth_ms)){
var map__15514 = halfahead.lab.pose.spring;
var map__15514__$1 = cljs.core.__destructure_map(map__15514);
var decay = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15514__$1,new cljs.core.Keyword(null,"decay","decay",1036712184));
var omega_d = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15514__$1,new cljs.core.Keyword(null,"omega-d","omega-d",1649460506));
var ratio = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15514__$1,new cljs.core.Keyword(null,"ratio","ratio",-926560044));
var s = (((age < (0)))?0.0:(1.0 - (halfahead.lab.pose.exp((- (decay * age))) * (halfahead.lab.pose.cos((omega_d * age)) + (ratio * halfahead.lab.pose.sin((omega_d * age)))))));
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"scale","scale",-230427353),(s - 1.0),new cljs.core.Keyword(null,"z","z",-789527183),(0.6 * (1.0 - s)),new cljs.core.Keyword(null,"opacity","opacity",397153780),(halfahead.lab.pose.clamp(0.0,1.0,(age / 90.0)) - 1.0)], null);
} else {
return null;
}
});
/**
 * How long each effect moves a glyph; none exceeds 1400 ms.
 */
halfahead.lab.pose.effect_ms = cljs.core.PersistentHashMap.fromArrays([new cljs.core.Keyword(null,"skitter","skitter",-308039072),new cljs.core.Keyword(null,"jitter","jitter",1262097060),new cljs.core.Keyword(null,"pop","pop",-1734778776),new cljs.core.Keyword(null,"shimmer","shimmer",2123148747),new cljs.core.Keyword(null,"rewind","rewind",-669264915),new cljs.core.Keyword(null,"fuse","fuse",-1648682513),new cljs.core.Keyword(null,"wobble","wobble",-1490235021),new cljs.core.Keyword(null,"quake","quake",-586699081),new cljs.core.Keyword(null,"melt","melt",137852440),new cljs.core.Keyword(null,"launch","launch",1165376825),new cljs.core.Keyword(null,"glow","glow",216329469)],[(900),(1400),(400),(1400),(700),(900),(1400),(1000),(1400),(1400),(1000)]);
/**
 * The delta for one effect at local time u (0 <= u < its duration), at
 *   intensity k, for the glyph at pos of n in the cue.
 */
halfahead.lab.pose.effect_delta = (function halfahead$lab$pose$effect_delta(effect,u,k,id,pos,n){
var d = (halfahead.lab.pose.effect_ms.cljs$core$IFn$_invoke$arity$1 ? halfahead.lab.pose.effect_ms.cljs$core$IFn$_invoke$arity$1(effect) : halfahead.lab.pose.effect_ms.call(null,effect));
var p = (u / d);
var fade = (1.0 - p);
var G__15531 = effect;
var G__15531__$1 = (((G__15531 instanceof cljs.core.Keyword))?G__15531.fqn:null);
switch (G__15531__$1) {
case "pop":
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"scale","scale",-230427353),((0.35 * k) * halfahead.lab.pose.sin((Math.PI * p)))], null);

break;
case "launch":
if((u < (700))){
var q = (u / 700.0);
var arc = ((4.0 * q) * (1.0 - q));
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"y","y",-1757859776),((1.2 * k) * arc),new cljs.core.Keyword(null,"z","z",-789527183),((1.5 * k) * arc)], null);
} else {
var e = (1.0 - ((u - 700.0) / 700.0));
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"wobble","wobble",-1490235021),((k * e) * e)], null);
}

break;
case "shimmer":
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"glow","glow",216329469),((((0.8 * k) * fade) * fade) * Math.abs(halfahead.lab.pose.sin(((halfahead.lab.pose.tau * u) / 350.0)))),new cljs.core.Keyword(null,"y","y",-1757859776),(((0.05 * k) * fade) * halfahead.lab.pose.sin(((halfahead.lab.pose.tau * u) / 700.0)))], null);

break;
case "skitter":
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"x","x",2099068185),((((0.12 * k) * fade) * fade) * halfahead.lab.pose.sin((((halfahead.lab.pose.tau * u) / 90.0) + halfahead.lab.pose.phase(id,(1)))))], null);

break;
case "quake":
var a = (((halfahead.lab.pose.tau * u) / 70.0) + halfahead.lab.pose.phase(id,(2)));
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"x","x",2099068185),(((0.15 * k) * fade) * halfahead.lab.pose.sin(a)),new cljs.core.Keyword(null,"y","y",-1757859776),(((0.15 * k) * fade) * halfahead.lab.pose.cos(a)),new cljs.core.Keyword(null,"glow","glow",216329469),(k * cljs.core.max.cljs$core$IFn$_invoke$arity$2(0.0,(1.0 - (u / 600.0))))], null);

break;
case "melt":
var droop = (((u < (600)))?halfahead.lab.pose.sin(((0.5 * Math.PI) * (u / 600.0))):(function (){var q = ((u - 600.0) / 800.0);
return (((1.0 - q) * (1.0 - q)) * halfahead.lab.pose.cos(((3.0 * Math.PI) * q)));
})());
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"y","y",-1757859776),((-0.4 * k) * droop),new cljs.core.Keyword(null,"rx","rx",1627208482),((0.6 * k) * droop)], null);

break;
case "rewind":
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"rz","rz",386461181),(((- halfahead.lab.pose.tau) * k) * (1.0 - halfahead.lab.pose.smoothstep(p)))], null);

break;
case "fuse":
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"x","x",2099068185),(((0.5 * k) * (pos - ((n - (1)) / 2.0))) * (1.0 - halfahead.lab.pose.smoothstep(p)))], null);

break;
case "jitter":
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"x","x",2099068185),(((0.04 * k) * fade) * halfahead.lab.pose.sin((((halfahead.lab.pose.tau * u) / 60.0) + halfahead.lab.pose.phase(id,(3))))),new cljs.core.Keyword(null,"y","y",-1757859776),(((0.04 * k) * fade) * halfahead.lab.pose.sin((((halfahead.lab.pose.tau * u) / 75.0) + halfahead.lab.pose.phase(id,(4)))))], null);

break;
case "glow":
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"glow","glow",216329469),((0.9 * k) * halfahead.lab.pose.sin((Math.PI * p)))], null);

break;
case "wobble":
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"wobble","wobble",-1490235021),((k * fade) * fade)], null);

break;
default:
throw (new Error((""+"No matching clause: "+cljs.core.str.cljs$core$IFn$_invoke$arity$1(G__15531__$1))));

}
});
/**
 * 25 ms between neighbours, compressed so the last glyph of any cue starts
 *   within 100 ms: 100 + the longest effect (1400) is the 1500 ms settle.
 */
halfahead.lab.pose.stagger_step = (function halfahead$lab$pose$stagger_step(n){
if((n > (5))){
return (100.0 / (n - (1)));
} else {
return 25.0;
}
});
/**
 * id -> [delta ...] for every glyph a cue is moving at time t.
 */
halfahead.lab.pose.cue_deltas = (function halfahead$lab$pose$cue_deltas(cues,t){
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (acc,p__15559){
var map__15560 = p__15559;
var map__15560__$1 = cljs.core.__destructure_map(map__15560);
var glyph_ids = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15560__$1,new cljs.core.Keyword(null,"glyph-ids","glyph-ids",1144320035));
var cue = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15560__$1,new cljs.core.Keyword(null,"cue","cue",1555624311));
var at_ms = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15560__$1,new cljs.core.Keyword(null,"at-ms","at-ms",-377491401));
var map__15561 = cue;
var map__15561__$1 = cljs.core.__destructure_map(map__15561);
var effect = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15561__$1,new cljs.core.Keyword(null,"effect","effect",347343289));
var intensity = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15561__$1,new cljs.core.Keyword(null,"intensity","intensity",1142770863));
var n = cljs.core.count(glyph_ids);
var step = halfahead.lab.pose.stagger_step(n);
var d = (halfahead.lab.pose.effect_ms.cljs$core$IFn$_invoke$arity$1 ? halfahead.lab.pose.effect_ms.cljs$core$IFn$_invoke$arity$1(effect) : halfahead.lab.pose.effect_ms.call(null,effect));
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (acc__$1,p__15566){
var vec__15568 = p__15566;
var i = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15568,(0),null);
var id = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15568,(1),null);
var pos = ((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"rewind","rewind",-669264915),effect))?((n - (1)) - i):i);
var u = ((t - at_ms) - (step * pos));
if((((u >= (0))) && ((u < d)))){
return cljs.core.update.cljs$core$IFn$_invoke$arity$4(acc__$1,id,cljs.core.fnil.cljs$core$IFn$_invoke$arity$2(cljs.core.conj,cljs.core.PersistentVector.EMPTY),halfahead.lab.pose.effect_delta(effect,u,intensity,id,i,n));
} else {
return acc__$1;
}
}),acc,cljs.core.vals(cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (m,p__15575){
var vec__15576 = p__15575;
var i = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15576,(0),null);
var id = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__15576,(1),null);
if(cljs.core.truth_((m.cljs$core$IFn$_invoke$arity$1 ? m.cljs$core$IFn$_invoke$arity$1(id) : m.call(null,id)))){
return m;
} else {
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(m,id,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [i,id], null));
}
}),cljs.core.PersistentArrayMap.EMPTY,cljs.core.map_indexed.cljs$core$IFn$_invoke$arity$2(cljs.core.vector,glyph_ids))));
}),cljs.core.PersistentArrayMap.EMPTY,cues);
});
halfahead.lab.pose.base_pose = (function halfahead$lab$pose$base_pose(p__15583,p__15584){
var map__15587 = p__15583;
var map__15587__$1 = cljs.core.__destructure_map(map__15587);
var id = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15587__$1,new cljs.core.Keyword(null,"id","id",-1388402092));
var char$ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15587__$1,new cljs.core.Keyword(null,"char","char",-641587586));
var map__15588 = p__15584;
var map__15588__$1 = cljs.core.__destructure_map(map__15588);
var x = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15588__$1,new cljs.core.Keyword(null,"x","x",2099068185));
var y = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15588__$1,new cljs.core.Keyword(null,"y","y",-1757859776));
return cljs.core.PersistentHashMap.fromArrays([new cljs.core.Keyword(null,"y","y",-1757859776),new cljs.core.Keyword(null,"rx","rx",1627208482),new cljs.core.Keyword(null,"ghost?","ghost?",864936484),new cljs.core.Keyword(null,"scale","scale",-230427353),new cljs.core.Keyword(null,"z","z",-789527183),new cljs.core.Keyword(null,"wobble","wobble",-1490235021),new cljs.core.Keyword(null,"opacity","opacity",397153780),new cljs.core.Keyword(null,"id","id",-1388402092),new cljs.core.Keyword(null,"x","x",2099068185),new cljs.core.Keyword(null,"glow","glow",216329469),new cljs.core.Keyword(null,"rz","rz",386461181),new cljs.core.Keyword(null,"ry","ry",-334598563),new cljs.core.Keyword(null,"char","char",-641587586)],[y,0.0,false,1.0,0.0,0.0,1.0,id,x,0.0,0.0,0.0,char$]);
});
halfahead.lab.pose.apply_deltas = (function halfahead$lab$pose$apply_deltas(pose,deltas){
if(cljs.core.empty_QMARK_(deltas)){
return pose;
} else {
return cljs.core.update.cljs$core$IFn$_invoke$arity$3(cljs.core.update.cljs$core$IFn$_invoke$arity$3(cljs.core.update.cljs$core$IFn$_invoke$arity$3(cljs.core.update.cljs$core$IFn$_invoke$arity$3(cljs.core.apply.cljs$core$IFn$_invoke$arity$4(cljs.core.merge_with,cljs.core._PLUS_,pose,deltas),new cljs.core.Keyword(null,"scale","scale",-230427353),(function (p1__15589_SHARP_){
return cljs.core.max.cljs$core$IFn$_invoke$arity$2(0.0,p1__15589_SHARP_);
})),new cljs.core.Keyword(null,"opacity","opacity",397153780),(function (p1__15590_SHARP_){
return halfahead.lab.pose.clamp(0.0,1.0,p1__15590_SHARP_);
})),new cljs.core.Keyword(null,"glow","glow",216329469),(function (p1__15591_SHARP_){
return halfahead.lab.pose.clamp(0.0,1.0,p1__15591_SHARP_);
})),new cljs.core.Keyword(null,"wobble","wobble",-1490235021),(function (p1__15592_SHARP_){
return halfahead.lab.pose.clamp(0.0,1.0,p1__15592_SHARP_);
}));
}
});
halfahead.lab.pose.ember_ms = (600);
halfahead.lab.pose.ember = (function halfahead$lab$pose$ember(p__15593,t,positions,end){
var map__15594 = p__15593;
var map__15594__$1 = cljs.core.__destructure_map(map__15594);
var id = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15594__$1,new cljs.core.Keyword(null,"id","id",-1388402092));
var char$ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15594__$1,new cljs.core.Keyword(null,"char","char",-641587586));
var died_ms = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15594__$1,new cljs.core.Keyword(null,"died-ms","died-ms",1637752567));
var index = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15594__$1,new cljs.core.Keyword(null,"index","index",-1531685915));
var age = (t - died_ms);
if((((age >= (0))) && ((age < halfahead.lab.pose.ember_ms)))){
var p = (age / halfahead.lab.pose.ember_ms);
var map__15595 = cljs.core.get.cljs$core$IFn$_invoke$arity$3(positions,index,end);
var map__15595__$1 = cljs.core.__destructure_map(map__15595);
var x = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15595__$1,new cljs.core.Keyword(null,"x","x",2099068185));
var y = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15595__$1,new cljs.core.Keyword(null,"y","y",-1757859776));
return cljs.core.PersistentHashMap.fromArrays([new cljs.core.Keyword(null,"y","y",-1757859776),new cljs.core.Keyword(null,"rx","rx",1627208482),new cljs.core.Keyword(null,"ghost?","ghost?",864936484),new cljs.core.Keyword(null,"scale","scale",-230427353),new cljs.core.Keyword(null,"z","z",-789527183),new cljs.core.Keyword(null,"wobble","wobble",-1490235021),new cljs.core.Keyword(null,"opacity","opacity",397153780),new cljs.core.Keyword(null,"id","id",-1388402092),new cljs.core.Keyword(null,"x","x",2099068185),new cljs.core.Keyword(null,"glow","glow",216329469),new cljs.core.Keyword(null,"rz","rz",386461181),new cljs.core.Keyword(null,"ry","ry",-334598563),new cljs.core.Keyword(null,"char","char",-641587586)],[(y + (1.5 * p)),0.0,true,(1.0 - (0.4 * p)),(0.3 * p),0.0,(1.0 - p),id,x,(0.5 * (1.0 - p)),((((cljs.core.even_QMARK_(id))?1.0:-1.0) * halfahead.lab.pose.tau) * p),0.0,char$]);
} else {
return null;
}
});
/**
 * The :ha/glyph-frame for draft at t-ms: one live pose per glyph, in
 *   order, then one ember per ghost younger than 600 ms, and the caret.
 *   opts: {:advance fn :width-em n :reduced-motion? b}; with reduced motion
 *   every live pose is its resting place and there are no embers.
 */
halfahead.lab.pose.frame = (function halfahead$lab$pose$frame(draft,t_ms,p__15598){
var map__15599 = p__15598;
var map__15599__$1 = cljs.core.__destructure_map(map__15599);
var advance = cljs.core.get.cljs$core$IFn$_invoke$arity$3(map__15599__$1,new cljs.core.Keyword(null,"advance","advance",1621005601),halfahead.lab.pose.default_advance);
var width_em = cljs.core.get.cljs$core$IFn$_invoke$arity$3(map__15599__$1,new cljs.core.Keyword(null,"width-em","width-em",1586622069),(40));
var reduced_motion_QMARK_ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15599__$1,new cljs.core.Keyword(null,"reduced-motion?","reduced-motion?",-2046278380));
var map__15600 = draft;
var map__15600__$1 = cljs.core.__destructure_map(map__15600);
var glyphs = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15600__$1,new cljs.core.Keyword(null,"glyphs","glyphs",-533936706));
var ghosts = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15600__$1,new cljs.core.Keyword(null,"ghosts","ghosts",665819293));
var cues = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15600__$1,new cljs.core.Keyword(null,"cues","cues",1596514991));
var chars = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"char","char",-641587586),glyphs);
var positions = halfahead.lab.pose.layout(chars,advance,width_em);
var end = halfahead.lab.pose.end_position(chars,positions,advance);
var bases = cljs.core.mapv.cljs$core$IFn$_invoke$arity$3(halfahead.lab.pose.base_pose,glyphs,positions);
var t = t_ms;
var live = (cljs.core.truth_(reduced_motion_QMARK_)?bases:(function (){var moving = halfahead.lab.pose.cue_deltas(cues,t);
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$3((function (p__15605,base){
var map__15606 = p__15605;
var map__15606__$1 = cljs.core.__destructure_map(map__15606);
var id = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15606__$1,new cljs.core.Keyword(null,"id","id",-1388402092));
var born_ms = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__15606__$1,new cljs.core.Keyword(null,"born-ms","born-ms",-2053429117));
return halfahead.lab.pose.apply_deltas(base,cljs.core.into.cljs$core$IFn$_invoke$arity$3(cljs.core.get.cljs$core$IFn$_invoke$arity$3(moving,id,cljs.core.PersistentVector.EMPTY),cljs.core.keep.cljs$core$IFn$_invoke$arity$1(cljs.core.identity),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [halfahead.lab.pose.birth((t - born_ms))], null)));
}),glyphs,bases);
})());
var embers = (cljs.core.truth_(reduced_motion_QMARK_)?null:cljs.core.keep.cljs$core$IFn$_invoke$arity$2((function (p1__15597_SHARP_){
return halfahead.lab.pose.ember(p1__15597_SHARP_,t,positions,end);
}),ghosts));
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"t-ms","t-ms",940365408),t_ms,new cljs.core.Keyword(null,"poses","poses",-120129129),cljs.core.into.cljs$core$IFn$_invoke$arity$2(live,embers),new cljs.core.Keyword(null,"caret","caret",-1275001854),end], null);
});

//# sourceMappingURL=halfahead.lab.pose.js.map

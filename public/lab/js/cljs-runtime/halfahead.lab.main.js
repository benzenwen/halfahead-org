goog.provide('halfahead.lab.main');
if((typeof halfahead !== 'undefined') && (typeof halfahead.lab !== 'undefined') && (typeof halfahead.lab.main !== 'undefined') && (typeof halfahead.lab.main.state !== 'undefined')){
} else {
halfahead.lab.main.state = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"draft","draft",1421831058),halfahead.lab.draft.empty_draft(),new cljs.core.Keyword(null,"last-frame","last-frame",1255377175),null], null));
}
halfahead.lab.main.now_ms = (function halfahead$lab$main$now_ms(){
return Math.round(performance.now());
});
halfahead.lab.main.by_id = (function halfahead$lab$main$by_id(id){
return document.getElementById(id);
});
/**
 * The layout width that fits the viewport, less a margin of 1 em a side.
 */
halfahead.lab.main.width_em = (function halfahead$lab$main$width_em(){
var w = window.innerWidth;
return cljs.core.max.cljs$core$IFn$_invoke$arity$2((4),((w / halfahead.lab.render.em_px(w)) - (2)));
});
halfahead.lab.main.frame = (function halfahead$lab$main$frame(r,reduced_motion_QMARK_){
var t = halfahead.lab.main.now_ms();
return halfahead.lab.pose.frame(halfahead.lab.draft.prune(new cljs.core.Keyword(null,"draft","draft",1421831058).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(halfahead.lab.main.state)),t),t,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"advance","advance",1621005601),(function (p1__10519_SHARP_){
return halfahead.lab.render.advance(r,p1__10519_SHARP_);
}),new cljs.core.Keyword(null,"width-em","width-em",1586622069),halfahead.lab.main.width_em(),new cljs.core.Keyword(null,"reduced-motion?","reduced-motion?",-2046278380),reduced_motion_QMARK_], null));
});
halfahead.lab.main.loop_BANG_ = (function halfahead$lab$main$loop_BANG_(r,motion_query){
var f = halfahead.lab.main.frame(r,motion_query.matches);
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(halfahead.lab.main.state,cljs.core.assoc,new cljs.core.Keyword(null,"last-frame","last-frame",1255377175),f);

halfahead.lab.render.draw_BANG_(r,f);

return requestAnimationFrame((function (){
return (halfahead.lab.main.loop_BANG_.cljs$core$IFn$_invoke$arity$2 ? halfahead.lab.main.loop_BANG_.cljs$core$IFn$_invoke$arity$2(r,motion_query) : halfahead.lab.main.loop_BANG_.call(null,r,motion_query));
}));
});
halfahead.lab.main.expose_BANG_ = (function halfahead$lab$main$expose_BANG_(r){
return (window.__lab = ({"backend": halfahead.lab.render.backend(r), "liveText": (function (){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"char","char",-641587586),cljs.core.remove.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"ghost?","ghost?",864936484),new cljs.core.Keyword(null,"poses","poses",-120129129).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"last-frame","last-frame",1255377175).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(halfahead.lab.main.state))))));
}), "lastFrame": (function (){
return cljs.core.clj__GT_js(new cljs.core.Keyword(null,"last-frame","last-frame",1255377175).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(halfahead.lab.main.state)));
}), "drawn": (function (){
return halfahead.lab.render.drawn(r);
})}));
});
halfahead.lab.main.on_input = (function halfahead$lab$main$on_input(e){
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(halfahead.lab.main.state,cljs.core.update,new cljs.core.Keyword(null,"draft","draft",1421831058),halfahead.lab.draft.edit,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([e.target.value,halfahead.lab.main.now_ms(),halfahead.lab.cues.word_cue], 0));
});
halfahead.lab.main.init_BANG_ = (function halfahead$lab$main$init_BANG_(){
var input = halfahead.lab.main.by_id("type");
var status = halfahead.lab.main.by_id("status");
var motion_query = window.matchMedia("(prefers-reduced-motion: reduce)");
input.focus();

input.addEventListener("input",halfahead.lab.main.on_input);

return halfahead.lab.render.start_BANG_(halfahead.lab.main.by_id("stage")).then((function (r){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(halfahead.lab.main.state,cljs.core.update,new cljs.core.Keyword(null,"draft","draft",1421831058),halfahead.lab.draft.edit,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([input.value,halfahead.lab.main.now_ms(),halfahead.lab.cues.word_cue], 0));

halfahead.lab.main.loop_BANG_(r,motion_query);

halfahead.lab.main.expose_BANG_(r);

return (status.textContent = (""+"Rendering with "+cljs.core.str.cljs$core$IFn$_invoke$arity$1(halfahead.lab.render.backend(r))));
})).catch((function (e){
(status.textContent = (""+"Couldn't start the 3D renderer: "+cljs.core.str.cljs$core$IFn$_invoke$arity$1(e.message)));

return console.error(e);
}));
});

//# sourceMappingURL=halfahead.lab.main.js.map

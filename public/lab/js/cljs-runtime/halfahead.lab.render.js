goog.provide('halfahead.lab.render');
var module$node_modules$$mapbox$tiny_sdf$index=shadow.js.require("module$node_modules$$mapbox$tiny_sdf$index", {});
var module$node_modules$three$build$three_tsl=shadow.js.require("module$node_modules$three$build$three_tsl", {});
var module$node_modules$three$build$three_webgpu=shadow.js.require("module$node_modules$three$build$three_webgpu", {});
halfahead.lab.render.font_px = (48);
halfahead.lab.render.sdf_buffer = (8);
halfahead.lab.render.sdf_radius = (12);
halfahead.lab.render.atlas_width = (1024);
halfahead.lab.render.font_family = "\"Arial Narrow\", \"Helvetica Neue\", \"Roboto Condensed\", system-ui, sans-serif";
halfahead.lab.render.data_texture = (function halfahead$lab$render$data_texture(pixels,height){
var G__10444 = (new module$node_modules$three$build$three_webgpu.DataTexture(pixels,halfahead.lab.render.atlas_width,height,module$node_modules$three$build$three_webgpu.RedFormat,module$node_modules$three$build$three_webgpu.UnsignedByteType));
(G__10444["minFilter"] = module$node_modules$three$build$three_webgpu.LinearFilter);

(G__10444["magFilter"] = module$node_modules$three$build$three_webgpu.LinearFilter);

(G__10444["needsUpdate"] = true);

return G__10444;
});
halfahead.lab.render.new_atlas = (function halfahead$lab$render$new_atlas(){
var height = (512);
var pixels = (new Uint8Array((halfahead.lab.render.atlas_width * height)));
return new cljs.core.PersistentArrayMap(null, 6, [new cljs.core.Keyword(null,"sdf","sdf",-844168232),(new module$node_modules$$mapbox$tiny_sdf$index.default(({"fontSize": halfahead.lab.render.font_px, "buffer": halfahead.lab.render.sdf_buffer, "radius": halfahead.lab.render.sdf_radius, "cutoff": 0.25, "fontFamily": halfahead.lab.render.font_family, "fontWeight": "600"}))),new cljs.core.Keyword(null,"pixels","pixels",-40523077),pixels,new cljs.core.Keyword(null,"height","height",1025178622),height,new cljs.core.Keyword(null,"texture","texture",-266291651),halfahead.lab.render.data_texture(pixels,height),new cljs.core.Keyword(null,"shelf","shelf",-1470183451),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"x","x",2099068185),(0),new cljs.core.Keyword(null,"y","y",-1757859776),(0),new cljs.core.Keyword(null,"row","row",-570139521),(0)], null),new cljs.core.Keyword(null,"glyphs","glyphs",-533936706),cljs.core.PersistentArrayMap.EMPTY], null);
});
halfahead.lab.render.grow = (function halfahead$lab$render$grow(p__10445){
var map__10446 = p__10445;
var map__10446__$1 = cljs.core.__destructure_map(map__10446);
var atlas = map__10446__$1;
var pixels = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10446__$1,new cljs.core.Keyword(null,"pixels","pixels",-40523077));
var height = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10446__$1,new cljs.core.Keyword(null,"height","height",1025178622));
var texture = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10446__$1,new cljs.core.Keyword(null,"texture","texture",-266291651));
var height_SINGLEQUOTE_ = ((2) * height);
var pixels_SINGLEQUOTE_ = (function (){var G__10447 = (new Uint8Array((halfahead.lab.render.atlas_width * height_SINGLEQUOTE_)));
G__10447.set(pixels);

return G__10447;
})();
texture.dispose();

return cljs.core.assoc.cljs$core$IFn$_invoke$arity$variadic(atlas,new cljs.core.Keyword(null,"pixels","pixels",-40523077),pixels_SINGLEQUOTE_,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"height","height",1025178622),height_SINGLEQUOTE_,new cljs.core.Keyword(null,"texture","texture",-266291651),halfahead.lab.render.data_texture(pixels_SINGLEQUOTE_,height_SINGLEQUOTE_)], 0));
});
/**
 * The shelf position for a w x h bitmap, growing the atlas as needed.
 */
halfahead.lab.render.place = (function halfahead$lab$render$place(atlas,w,h){
var G__10449 = atlas;
var map__10450 = G__10449;
var map__10450__$1 = cljs.core.__destructure_map(map__10450);
var atlas__$1 = map__10450__$1;
var shelf = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10450__$1,new cljs.core.Keyword(null,"shelf","shelf",-1470183451));
var height = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10450__$1,new cljs.core.Keyword(null,"height","height",1025178622));
var G__10449__$1 = G__10449;
while(true){
var map__10451 = G__10449__$1;
var map__10451__$1 = cljs.core.__destructure_map(map__10451);
var atlas__$2 = map__10451__$1;
var shelf__$1 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10451__$1,new cljs.core.Keyword(null,"shelf","shelf",-1470183451));
var height__$1 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10451__$1,new cljs.core.Keyword(null,"height","height",1025178622));
var map__10452 = shelf__$1;
var map__10452__$1 = cljs.core.__destructure_map(map__10452);
var x = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10452__$1,new cljs.core.Keyword(null,"x","x",2099068185));
var y = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10452__$1,new cljs.core.Keyword(null,"y","y",-1757859776));
var row = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10452__$1,new cljs.core.Keyword(null,"row","row",-570139521));
var vec__10453 = ((((x + w) > halfahead.lab.render.atlas_width))?new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [(0),((y + row) + (1)),(0)], null):new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [x,y,row], null));
var x__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10453,(0),null);
var y__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10453,(1),null);
var row__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10453,(2),null);
if(((y__$1 + h) > height__$1)){
var G__10515 = halfahead.lab.render.grow(atlas__$2);
G__10449__$1 = G__10515;
continue;
} else {
return new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(atlas__$2,new cljs.core.Keyword(null,"shelf","shelf",-1470183451),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"x","x",2099068185),((x__$1 + w) + (1)),new cljs.core.Keyword(null,"y","y",-1757859776),y__$1,new cljs.core.Keyword(null,"row","row",-570139521),cljs.core.max.cljs$core$IFn$_invoke$arity$2(row__$1,h)], null)),x__$1,y__$1], null);
}
break;
}
});
halfahead.lab.render.rasterise = (function halfahead$lab$render$rasterise(p__10456,c){
var map__10457 = p__10456;
var map__10457__$1 = cljs.core.__destructure_map(map__10457);
var atlas = map__10457__$1;
var sdf = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10457__$1,new cljs.core.Keyword(null,"sdf","sdf",-844168232));
var g = sdf.draw(c);
var w = g.width;
var h = g.height;
var vec__10458 = halfahead.lab.render.place(atlas,w,h);
var map__10461 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10458,(0),null);
var map__10461__$1 = cljs.core.__destructure_map(map__10461);
var atlas__$1 = map__10461__$1;
var pixels = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10461__$1,new cljs.core.Keyword(null,"pixels","pixels",-40523077));
var texture = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10461__$1,new cljs.core.Keyword(null,"texture","texture",-266291651));
var x = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10458,(1),null);
var y = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10458,(2),null);
var data = g.data;
var em = (function (px){
return (px / halfahead.lab.render.font_px);
});
var n__5762__auto___10516 = h;
var row_10517 = (0);
while(true){
if((row_10517 < n__5762__auto___10516)){
pixels.set(data.subarray((row_10517 * w),((row_10517 + (1)) * w)),(x + ((y + row_10517) * halfahead.lab.render.atlas_width)));

var G__10518 = (row_10517 + (1));
row_10517 = G__10518;
continue;
} else {
}
break;
}

(texture["needsUpdate"] = true);

return cljs.core.assoc_in(atlas__$1,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"glyphs","glyphs",-533936706),c], null),new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"rect","rect",-108902628),new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [x,y,w,h], null),new cljs.core.Keyword(null,"advance","advance",1621005601),em(g.glyphAdvance),new cljs.core.Keyword(null,"size","size",1098693007),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [em(w),em(h)], null),new cljs.core.Keyword(null,"centre","centre",-948091970),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [em(((g.glyphLeft - halfahead.lab.render.sdf_buffer) + (w / (2)))),em(((g.glyphTop + halfahead.lab.render.sdf_buffer) - (h / (2))))], null)], null));
});
/**
 * The atlas entry for c, rasterising it on first use.
 */
halfahead.lab.render.glyph = (function halfahead$lab$render$glyph(p__10462,c){
var map__10463 = p__10462;
var map__10463__$1 = cljs.core.__destructure_map(map__10463);
var atlas = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10463__$1,new cljs.core.Keyword(null,"atlas","atlas",1594808522));
var atlas_node = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10463__$1,new cljs.core.Keyword(null,"atlas-node","atlas-node",-1446814981));
var or__5162__auto__ = cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(atlas),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"glyphs","glyphs",-533936706),c], null));
if(cljs.core.truth_(or__5162__auto__)){
return or__5162__auto__;
} else {
var before = new cljs.core.Keyword(null,"texture","texture",-266291651).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(atlas));
var map__10464 = cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(atlas,halfahead.lab.render.rasterise,c);
var map__10464__$1 = cljs.core.__destructure_map(map__10464);
var after = map__10464__$1;
var texture = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10464__$1,new cljs.core.Keyword(null,"texture","texture",-266291651));
if((before === texture)){
} else {
(atlas_node["value"] = texture);
}

return cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(after,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"glyphs","glyphs",-533936706),c], null));
}
});
halfahead.lab.render.ink = "#efe6d6";
halfahead.lab.render.vermilion = "#ff7a55";
halfahead.lab.render.ember = "#ff8a3d";
/**
 * Alpha from the distance field with a smooth edge; glow as a wider, softer
 *   halo from the same field, tinted vermilion; ghosts in ember orange; wobble
 *   as a jelly sway of the quad's corners.
 */
halfahead.lab.render.glyph_material = (function halfahead$lab$render$glyph_material(atlas_texture,atlas_size){
var rect = module$node_modules$three$build$three_tsl.attribute("glyphRect","vec4");
var opacity = module$node_modules$three$build$three_tsl.attribute("glyphOpacity","float");
var glow = module$node_modules$three$build$three_tsl.attribute("glyphGlow","float");
var wobble = module$node_modules$three$build$three_tsl.attribute("glyphWobble","float");
var ghost = module$node_modules$three$build$three_tsl.attribute("glyphGhost","float");
var uv = module$node_modules$three$build$three_tsl.uv();
var texel = rect.xy.add(module$node_modules$three$build$three_tsl.vec2(uv.x,uv.y.oneMinus()).mul(rect.zw)).div(atlas_size);
var atlas_node = module$node_modules$three$build$three_tsl.texture(atlas_texture,texel);
var d = atlas_node.r;
var edge = 0.75;
var fill = module$node_modules$three$build$three_tsl.smoothstep((edge - 0.06),(edge + 0.06),d);
var halo = module$node_modules$three$build$three_tsl.smoothstep(0.15,edge,d).mul(glow).mul(0.85);
var base = module$node_modules$three$build$three_tsl.mix(module$node_modules$three$build$three_tsl.color(halfahead.lab.render.ink),module$node_modules$three$build$three_tsl.color(halfahead.lab.render.ember),ghost);
var rgb = module$node_modules$three$build$three_tsl.mix(module$node_modules$three$build$three_tsl.color(halfahead.lab.render.vermilion),base,fill);
var g = module$node_modules$three$build$three_tsl.positionGeometry;
var sway = module$node_modules$three$build$three_tsl.vec3(module$node_modules$three$build$three_tsl.sin(module$node_modules$three$build$three_tsl.time.mul(11.0).add(g.y.mul(5.0))).mul(0.18),module$node_modules$three$build$three_tsl.sin(module$node_modules$three$build$three_tsl.time.mul(9.0).add(g.x.mul(6.0))).mul(0.14),0.0);
var m = (new module$node_modules$three$build$three_webgpu.MeshBasicNodeMaterial());
(m.transparent = true);

(m.depthWrite = false);

(m.side = module$node_modules$three$build$three_webgpu.DoubleSide);

(m.colorNode = module$node_modules$three$build$three_tsl.vec4(rgb,1.0));

(m.opacityNode = module$node_modules$three$build$three_tsl.max(fill,halo).mul(opacity));

(m.positionNode = module$node_modules$three$build$three_tsl.positionLocal.add(sway.mul(wobble).mul(g.xy.length())));

return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"material","material",460118677),m,new cljs.core.Keyword(null,"atlas-node","atlas-node",-1446814981),atlas_node], null);
});
halfahead.lab.render.per_instance = new cljs.core.PersistentArrayMap(null, 5, ["glyphRect",(4),"glyphOpacity",(1),"glyphGlow",(1),"glyphWobble",(1),"glyphGhost",(1)], null);
halfahead.lab.render.glyph_mesh = (function halfahead$lab$render$glyph_mesh(capacity,material){
var geometry = (new module$node_modules$three$build$three_webgpu.PlaneGeometry((1),(1),(6),(6)));
var seq__10465_10520 = cljs.core.seq(halfahead.lab.render.per_instance);
var chunk__10466_10521 = null;
var count__10467_10522 = (0);
var i__10468_10523 = (0);
while(true){
if((i__10468_10523 < count__10467_10522)){
var vec__10477_10524 = chunk__10466_10521.cljs$core$IIndexed$_nth$arity$2(null,i__10468_10523);
var attr_10525 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10477_10524,(0),null);
var width_10526 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10477_10524,(1),null);
geometry.setAttribute(attr_10525,(function (){var G__10480 = (new module$node_modules$three$build$three_webgpu.InstancedBufferAttribute((new Float32Array((capacity * width_10526))),width_10526));
G__10480.setUsage(module$node_modules$three$build$three_webgpu.DynamicDrawUsage);

return G__10480;
})());


var G__10527 = seq__10465_10520;
var G__10528 = chunk__10466_10521;
var G__10529 = count__10467_10522;
var G__10530 = (i__10468_10523 + (1));
seq__10465_10520 = G__10527;
chunk__10466_10521 = G__10528;
count__10467_10522 = G__10529;
i__10468_10523 = G__10530;
continue;
} else {
var temp__5823__auto___10531 = cljs.core.seq(seq__10465_10520);
if(temp__5823__auto___10531){
var seq__10465_10532__$1 = temp__5823__auto___10531;
if(cljs.core.chunked_seq_QMARK_(seq__10465_10532__$1)){
var c__5694__auto___10533 = cljs.core.chunk_first(seq__10465_10532__$1);
var G__10534 = cljs.core.chunk_rest(seq__10465_10532__$1);
var G__10535 = c__5694__auto___10533;
var G__10536 = cljs.core.count(c__5694__auto___10533);
var G__10537 = (0);
seq__10465_10520 = G__10534;
chunk__10466_10521 = G__10535;
count__10467_10522 = G__10536;
i__10468_10523 = G__10537;
continue;
} else {
var vec__10481_10538 = cljs.core.first(seq__10465_10532__$1);
var attr_10539 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10481_10538,(0),null);
var width_10540 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10481_10538,(1),null);
geometry.setAttribute(attr_10539,(function (){var G__10484 = (new module$node_modules$three$build$three_webgpu.InstancedBufferAttribute((new Float32Array((capacity * width_10540))),width_10540));
G__10484.setUsage(module$node_modules$three$build$three_webgpu.DynamicDrawUsage);

return G__10484;
})());


var G__10541 = cljs.core.next(seq__10465_10532__$1);
var G__10542 = null;
var G__10543 = (0);
var G__10544 = (0);
seq__10465_10520 = G__10541;
chunk__10466_10521 = G__10542;
count__10467_10522 = G__10543;
i__10468_10523 = G__10544;
continue;
}
} else {
}
}
break;
}

var G__10485 = (new module$node_modules$three$build$three_webgpu.InstancedMesh(geometry,material,capacity));
(G__10485["frustumCulled"] = false);

G__10485.instanceMatrix.setUsage(module$node_modules$three$build$three_webgpu.DynamicDrawUsage);

return G__10485;
});
/**
 * The glyph mesh, replaced by one twice as large when n poses overflow it.
 */
halfahead.lab.render.ensure_capacity_BANG_ = (function halfahead$lab$render$ensure_capacity_BANG_(p__10486,n){
var map__10487 = p__10486;
var map__10487__$1 = cljs.core.__destructure_map(map__10487);
var mesh = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10487__$1,new cljs.core.Keyword(null,"mesh","mesh",456320595));
var scene = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10487__$1,new cljs.core.Keyword(null,"scene","scene",1523800415));
var material = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10487__$1,new cljs.core.Keyword(null,"material","material",460118677));
var current = cljs.core.deref(mesh);
var capacity = current.instanceMatrix.array.length;
if((n <= (capacity / (16)))){
return current;
} else {
var bigger = halfahead.lab.render.glyph_mesh(cljs.core.max.cljs$core$IFn$_invoke$arity$2(n,(capacity / (8))),material);
scene.remove(current);

current.geometry.dispose();

scene.add(bigger);

return cljs.core.reset_BANG_(mesh,bigger);
}
});
/**
 * Matches the drawing buffer and the camera's aspect to the canvas's CSS
 *   size, only when that size changed.
 */
halfahead.lab.render.fit_viewport_BANG_ = (function halfahead$lab$render$fit_viewport_BANG_(p__10488,canvas){
var map__10489 = p__10488;
var map__10489__$1 = cljs.core.__destructure_map(map__10489);
var renderer = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10489__$1,new cljs.core.Keyword(null,"renderer","renderer",336841071));
var camera = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10489__$1,new cljs.core.Keyword(null,"camera","camera",-1190348585));
var viewport = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10489__$1,new cljs.core.Keyword(null,"viewport","viewport",443342715));
var w = cljs.core.max.cljs$core$IFn$_invoke$arity$2((1),canvas.clientWidth);
var h = cljs.core.max.cljs$core$IFn$_invoke$arity$2((1),canvas.clientHeight);
var dpr = cljs.core.min.cljs$core$IFn$_invoke$arity$2((2),(function (){var or__5162__auto__ = window.devicePixelRatio;
if(cljs.core.truth_(or__5162__auto__)){
return or__5162__auto__;
} else {
return (1);
}
})());
if(cljs.core.not_EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(viewport),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [w,h,dpr], null))){
cljs.core.reset_BANG_(viewport,new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [w,h,dpr], null));

renderer.setPixelRatio(dpr);

renderer.setSize(w,h,false);

(camera.aspect = (w / h));

return camera.updateProjectionMatrix();
} else {
return null;
}
});
/**
 * On-screen size of 1 em at the text plane, in CSS pixels, at most.
 */
halfahead.lab.render.text_px = (40);
/**
 * How many CSS pixels one em of text occupies in a viewport w px wide; the
 *   shell divides by this to give the layout a width that fits.
 */
halfahead.lab.render.em_px = (function halfahead$lab$render$em_px(w){
return cljs.core.min.cljs$core$IFn$_invoke$arity$2(halfahead.lab.render.text_px,(w / (12)));
});
/**
 * A camera slightly below and in front of the text plane (z = 0, text
 *   starting at the origin and running down), aimed up into it, so motion in
 *   z reads as depth; the first line sits near the top of the canvas.
 */
halfahead.lab.render.view = (function halfahead$lab$render$view(camera,canvas){
var w = cljs.core.max.cljs$core$IFn$_invoke$arity$2((1),canvas.clientWidth);
var h = cljs.core.max.cljs$core$IFn$_invoke$arity$2((1),canvas.clientHeight);
var px = halfahead.lab.render.em_px(w);
var fov_rad = (camera.fov * (Math.PI / (180)));
var dist = ((h / px) / ((2) * Math.tan((fov_rad / (2)))));
var cx = (((w / px) / (2)) - (1));
var cy = (2.5 - ((h / px) / (2)));
camera.position.set(cx,(cy - (0.18 * dist)),dist);

return camera.lookAt(cx,(cy - 0.4),(0));
});
/**
 * "WebGPU" or "WebGL2", whichever this renderer is using.
 */
halfahead.lab.render.backend = (function halfahead$lab$render$backend(r){
return new cljs.core.Keyword(null,"backend","backend",-847489124).cljs$core$IFn$_invoke$arity$1(r);
});
/**
 * The advance of char c in em, from the font's metrics.
 */
halfahead.lab.render.advance = (function halfahead$lab$render$advance(r,c){
return new cljs.core.Keyword(null,"advance","advance",1621005601).cljs$core$IFn$_invoke$arity$1(halfahead.lab.render.glyph(r,c));
});
/**
 * Glyph instances drawn in the last frame.
 */
halfahead.lab.render.drawn = (function halfahead$lab$render$drawn(r){
return cljs.core.deref(new cljs.core.Keyword(null,"drawn","drawn",2034398063).cljs$core$IFn$_invoke$arity$1(r));
});
halfahead.lab.render.scratch = (new module$node_modules$three$build$three_webgpu.Object3D());
/**
 * Draws one :ha/glyph-frame.
 */
halfahead.lab.render.draw_BANG_ = (function halfahead$lab$render$draw_BANG_(p__10490,p__10491){
var map__10492 = p__10490;
var map__10492__$1 = cljs.core.__destructure_map(map__10492);
var r = map__10492__$1;
var renderer = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10492__$1,new cljs.core.Keyword(null,"renderer","renderer",336841071));
var scene = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10492__$1,new cljs.core.Keyword(null,"scene","scene",1523800415));
var camera = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10492__$1,new cljs.core.Keyword(null,"camera","camera",-1190348585));
var canvas = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10492__$1,new cljs.core.Keyword(null,"canvas","canvas",-1798817489));
var atlas = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10492__$1,new cljs.core.Keyword(null,"atlas","atlas",1594808522));
var atlas_size = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10492__$1,new cljs.core.Keyword(null,"atlas-size","atlas-size",1242272045));
var caret = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10492__$1,new cljs.core.Keyword(null,"caret","caret",-1275001854));
var map__10493 = p__10491;
var map__10493__$1 = cljs.core.__destructure_map(map__10493);
var frame = map__10493__$1;
var poses = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10493__$1,new cljs.core.Keyword(null,"poses","poses",-120129129));
var n = cljs.core.count(poses);
var mesh = halfahead.lab.render.ensure_capacity_BANG_(r,n);
var geometry = mesh.geometry;
var attr = (function (k){
return geometry.getAttribute(k).array;
});
var rects = attr("glyphRect");
var opacity = attr("glyphOpacity");
var glow = attr("glyphGlow");
var wobble = attr("glyphWobble");
var ghost = attr("glyphGhost");
var o = halfahead.lab.render.scratch;
halfahead.lab.render.fit_viewport_BANG_(r,canvas);

halfahead.lab.render.view(camera,canvas);

var n__5762__auto___10545 = n;
var i_10546 = (0);
while(true){
if((i_10546 < n__5762__auto___10545)){
var p_10547 = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(poses,i_10546);
var map__10494_10548 = halfahead.lab.render.glyph(r,new cljs.core.Keyword(null,"char","char",-641587586).cljs$core$IFn$_invoke$arity$1(p_10547));
var map__10494_10549__$1 = cljs.core.__destructure_map(map__10494_10548);
var rect_10550 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10494_10549__$1,new cljs.core.Keyword(null,"rect","rect",-108902628));
var size_10551 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10494_10549__$1,new cljs.core.Keyword(null,"size","size",1098693007));
var centre_10552 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10494_10549__$1,new cljs.core.Keyword(null,"centre","centre",-948091970));
var vec__10495_10553 = rect_10550;
var rx_10554 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10495_10553,(0),null);
var ry_10555 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10495_10553,(1),null);
var rw_10556 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10495_10553,(2),null);
var rh_10557 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10495_10553,(3),null);
var vec__10498_10558 = size_10551;
var w_10559 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10498_10558,(0),null);
var h_10560 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10498_10558,(1),null);
var vec__10501_10561 = centre_10552;
var cx_10562 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10501_10561,(0),null);
var cy_10563 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10501_10561,(1),null);
var s_10564 = new cljs.core.Keyword(null,"scale","scale",-230427353).cljs$core$IFn$_invoke$arity$1(p_10547);
o.position.set((new cljs.core.Keyword(null,"x","x",2099068185).cljs$core$IFn$_invoke$arity$1(p_10547) + cx_10562),(new cljs.core.Keyword(null,"y","y",-1757859776).cljs$core$IFn$_invoke$arity$1(p_10547) + cy_10563),new cljs.core.Keyword(null,"z","z",-789527183).cljs$core$IFn$_invoke$arity$1(p_10547));

o.rotation.set(new cljs.core.Keyword(null,"rx","rx",1627208482).cljs$core$IFn$_invoke$arity$1(p_10547),new cljs.core.Keyword(null,"ry","ry",-334598563).cljs$core$IFn$_invoke$arity$1(p_10547),new cljs.core.Keyword(null,"rz","rz",386461181).cljs$core$IFn$_invoke$arity$1(p_10547));

o.scale.set((s_10564 * w_10559),(s_10564 * h_10560),(1));

o.updateMatrix();

mesh.setMatrixAt(i_10546,o.matrix);

rects.set([rx_10554,ry_10555,rw_10556,rh_10557],((4) * i_10546));

(opacity[i_10546] = new cljs.core.Keyword(null,"opacity","opacity",397153780).cljs$core$IFn$_invoke$arity$1(p_10547));

(glow[i_10546] = new cljs.core.Keyword(null,"glow","glow",216329469).cljs$core$IFn$_invoke$arity$1(p_10547));

(wobble[i_10546] = new cljs.core.Keyword(null,"wobble","wobble",-1490235021).cljs$core$IFn$_invoke$arity$1(p_10547));

(ghost[i_10546] = (cljs.core.truth_(new cljs.core.Keyword(null,"ghost?","ghost?",864936484).cljs$core$IFn$_invoke$arity$1(p_10547))?(1):(0)));

var G__10565 = (i_10546 + (1));
i_10546 = G__10565;
continue;
} else {
}
break;
}

atlas_size.value.set(halfahead.lab.render.atlas_width,new cljs.core.Keyword(null,"height","height",1025178622).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(atlas)));

var seq__10504_10566 = cljs.core.seq(cljs.core.keys(halfahead.lab.render.per_instance));
var chunk__10505_10567 = null;
var count__10506_10568 = (0);
var i__10507_10569 = (0);
while(true){
if((i__10507_10569 < count__10506_10568)){
var k_10570 = chunk__10505_10567.cljs$core$IIndexed$_nth$arity$2(null,i__10507_10569);
(geometry.getAttribute(k_10570)["needsUpdate"] = true);


var G__10571 = seq__10504_10566;
var G__10572 = chunk__10505_10567;
var G__10573 = count__10506_10568;
var G__10574 = (i__10507_10569 + (1));
seq__10504_10566 = G__10571;
chunk__10505_10567 = G__10572;
count__10506_10568 = G__10573;
i__10507_10569 = G__10574;
continue;
} else {
var temp__5823__auto___10575 = cljs.core.seq(seq__10504_10566);
if(temp__5823__auto___10575){
var seq__10504_10576__$1 = temp__5823__auto___10575;
if(cljs.core.chunked_seq_QMARK_(seq__10504_10576__$1)){
var c__5694__auto___10577 = cljs.core.chunk_first(seq__10504_10576__$1);
var G__10578 = cljs.core.chunk_rest(seq__10504_10576__$1);
var G__10579 = c__5694__auto___10577;
var G__10580 = cljs.core.count(c__5694__auto___10577);
var G__10581 = (0);
seq__10504_10566 = G__10578;
chunk__10505_10567 = G__10579;
count__10506_10568 = G__10580;
i__10507_10569 = G__10581;
continue;
} else {
var k_10582 = cljs.core.first(seq__10504_10576__$1);
(geometry.getAttribute(k_10582)["needsUpdate"] = true);


var G__10583 = cljs.core.next(seq__10504_10576__$1);
var G__10584 = null;
var G__10585 = (0);
var G__10586 = (0);
seq__10504_10566 = G__10583;
chunk__10505_10567 = G__10584;
count__10506_10568 = G__10585;
i__10507_10569 = G__10586;
continue;
}
} else {
}
}
break;
}

(mesh.instanceMatrix["needsUpdate"] = true);

(mesh.count = n);

var map__10508_10587 = new cljs.core.Keyword(null,"caret","caret",-1275001854).cljs$core$IFn$_invoke$arity$1(frame);
var map__10508_10588__$1 = cljs.core.__destructure_map(map__10508_10587);
var x_10589 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10508_10588__$1,new cljs.core.Keyword(null,"x","x",2099068185));
var y_10590 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10508_10588__$1,new cljs.core.Keyword(null,"y","y",-1757859776));
caret.position.set((x_10589 + 0.04),(y_10590 + 0.3),(0));

renderer.render(scene,camera);

return cljs.core.reset_BANG_(new cljs.core.Keyword(null,"drawn","drawn",2034398063).cljs$core$IFn$_invoke$arity$1(r),n);
});
/**
 * Everything drawn, around a renderer that has finished init.
 */
halfahead.lab.render.build = (function halfahead$lab$render$build(renderer,canvas){
var atlas = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(halfahead.lab.render.new_atlas());
var atlas_size = module$node_modules$three$build$three_tsl.uniform((new module$node_modules$three$build$three_webgpu.Vector2(halfahead.lab.render.atlas_width,new cljs.core.Keyword(null,"height","height",1025178622).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(atlas)))));
var map__10509 = halfahead.lab.render.glyph_material(new cljs.core.Keyword(null,"texture","texture",-266291651).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(atlas)),atlas_size);
var map__10509__$1 = cljs.core.__destructure_map(map__10509);
var material = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10509__$1,new cljs.core.Keyword(null,"material","material",460118677));
var atlas_node = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10509__$1,new cljs.core.Keyword(null,"atlas-node","atlas-node",-1446814981));
var scene = (new module$node_modules$three$build$three_webgpu.Scene());
var camera = (new module$node_modules$three$build$three_webgpu.PerspectiveCamera((35),(1),0.1,(500)));
var caret = (new module$node_modules$three$build$three_webgpu.Mesh((new module$node_modules$three$build$three_webgpu.PlaneGeometry(0.06,1.1)),(new module$node_modules$three$build$three_webgpu.MeshBasicNodeMaterial(({"color": halfahead.lab.render.vermilion})))));
var r = cljs.core.PersistentHashMap.fromArrays([new cljs.core.Keyword(null,"caret","caret",-1275001854),new cljs.core.Keyword(null,"atlas","atlas",1594808522),new cljs.core.Keyword(null,"atlas-size","atlas-size",1242272045),new cljs.core.Keyword(null,"canvas","canvas",-1798817489),new cljs.core.Keyword(null,"renderer","renderer",336841071),new cljs.core.Keyword(null,"drawn","drawn",2034398063),new cljs.core.Keyword(null,"mesh","mesh",456320595),new cljs.core.Keyword(null,"material","material",460118677),new cljs.core.Keyword(null,"camera","camera",-1190348585),new cljs.core.Keyword(null,"atlas-node","atlas-node",-1446814981),new cljs.core.Keyword(null,"viewport","viewport",443342715),new cljs.core.Keyword(null,"backend","backend",-847489124),new cljs.core.Keyword(null,"scene","scene",1523800415)],[caret,atlas,atlas_size,canvas,renderer,cljs.core.atom.cljs$core$IFn$_invoke$arity$1((0)),cljs.core.atom.cljs$core$IFn$_invoke$arity$1(halfahead.lab.render.glyph_mesh((256),material)),material,camera,atlas_node,cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null),(cljs.core.truth_(renderer.backend.isWebGPUBackend)?"WebGPU":"WebGL2"),scene]);
(scene.background = (new module$node_modules$three$build$three_webgpu.Color("#141210")));

scene.add(cljs.core.deref(new cljs.core.Keyword(null,"mesh","mesh",456320595).cljs$core$IFn$_invoke$arity$1(r)));

scene.add(caret);

var seq__10510_10591 = cljs.core.seq(cljs.core.map.cljs$core$IFn$_invoke$arity$2(cljs.core.char$,cljs.core.range.cljs$core$IFn$_invoke$arity$2((32),(127))));
var chunk__10511_10592 = null;
var count__10512_10593 = (0);
var i__10513_10594 = (0);
while(true){
if((i__10513_10594 < count__10512_10593)){
var c_10595 = chunk__10511_10592.cljs$core$IIndexed$_nth$arity$2(null,i__10513_10594);
halfahead.lab.render.glyph(r,(""+cljs.core.str.cljs$core$IFn$_invoke$arity$1(c_10595)));


var G__10596 = seq__10510_10591;
var G__10597 = chunk__10511_10592;
var G__10598 = count__10512_10593;
var G__10599 = (i__10513_10594 + (1));
seq__10510_10591 = G__10596;
chunk__10511_10592 = G__10597;
count__10512_10593 = G__10598;
i__10513_10594 = G__10599;
continue;
} else {
var temp__5823__auto___10600 = cljs.core.seq(seq__10510_10591);
if(temp__5823__auto___10600){
var seq__10510_10601__$1 = temp__5823__auto___10600;
if(cljs.core.chunked_seq_QMARK_(seq__10510_10601__$1)){
var c__5694__auto___10602 = cljs.core.chunk_first(seq__10510_10601__$1);
var G__10603 = cljs.core.chunk_rest(seq__10510_10601__$1);
var G__10604 = c__5694__auto___10602;
var G__10605 = cljs.core.count(c__5694__auto___10602);
var G__10606 = (0);
seq__10510_10591 = G__10603;
chunk__10511_10592 = G__10604;
count__10512_10593 = G__10605;
i__10513_10594 = G__10606;
continue;
} else {
var c_10607 = cljs.core.first(seq__10510_10601__$1);
halfahead.lab.render.glyph(r,(""+cljs.core.str.cljs$core$IFn$_invoke$arity$1(c_10607)));


var G__10608 = cljs.core.next(seq__10510_10601__$1);
var G__10609 = null;
var G__10610 = (0);
var G__10611 = (0);
seq__10510_10591 = G__10608;
chunk__10511_10592 = G__10609;
count__10512_10593 = G__10610;
i__10513_10594 = G__10611;
continue;
}
} else {
}
}
break;
}

return r;
});
halfahead.lab.render.init = (function halfahead$lab$render$init(canvas,webgl_QMARK_){
var renderer = (new module$node_modules$three$build$three_webgpu.WebGPURenderer(({"canvas": canvas, "antialias": true, "forceWebGL": webgl_QMARK_})));
return renderer.init().then((function (){
return halfahead.lab.render.build(renderer,canvas);
}));
});
/**
 * A copy of canvas in its place: a canvas that has handed out a WebGPU
 *   context cannot give a WebGL2 one.
 */
halfahead.lab.render.fresh_canvas = (function halfahead$lab$render$fresh_canvas(canvas){
var copy = canvas.cloneNode(false);
canvas.replaceWith(copy);

return copy;
});
/**
 * A promise of a renderer drawing into canvas (or a fresh copy of it).
 *   WebGPU when the browser has it and it can draw a first frame, WebGL2
 *   otherwise; `backend` says which. Some browsers expose a WebGPU whose
 *   interface is older than this three.js expects, and only the first frame
 *   shows it, hence the probe.
 */
halfahead.lab.render.start_BANG_ = (function halfahead$lab$render$start_BANG_(canvas){
return halfahead.lab.render.init(canvas,false).then((function (r){
try{halfahead.lab.render.draw_BANG_(r,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"t-ms","t-ms",940365408),(0),new cljs.core.Keyword(null,"poses","poses",-120129129),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"caret","caret",-1275001854),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"x","x",2099068185),0.0,new cljs.core.Keyword(null,"y","y",-1757859776),0.0], null)], null));

return r;
}catch (e10514){var e = e10514;
console.warn("WebGPU could not draw; falling back to WebGL2:",e.message);

new cljs.core.Keyword(null,"renderer","renderer",336841071).cljs$core$IFn$_invoke$arity$1(r).dispose();

return halfahead.lab.render.init(halfahead.lab.render.fresh_canvas(canvas),true);
}}));
});

//# sourceMappingURL=halfahead.lab.render.js.map

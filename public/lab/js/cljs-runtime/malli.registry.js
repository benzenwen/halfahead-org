goog.provide('malli.registry');
/**
 * @define {string}
 * @type {string}
 */
malli.registry.mode = goog.define("malli.registry.mode","default");
/**
 * @define {string}
 * @type {string}
 */
malli.registry.type = goog.define("malli.registry.type","default");

/**
 * @interface
 */
malli.registry.Registry = function(){};

var malli$registry$Registry$_schema$dyn_8030 = (function (this$,type){
var x__5519__auto__ = (((this$ == null))?null:this$);
var m__5520__auto__ = (malli.registry._schema[goog.typeOf(x__5519__auto__)]);
if((!((m__5520__auto__ == null)))){
return (m__5520__auto__.cljs$core$IFn$_invoke$arity$2 ? m__5520__auto__.cljs$core$IFn$_invoke$arity$2(this$,type) : m__5520__auto__.call(null,this$,type));
} else {
var m__5518__auto__ = (malli.registry._schema["_"]);
if((!((m__5518__auto__ == null)))){
return (m__5518__auto__.cljs$core$IFn$_invoke$arity$2 ? m__5518__auto__.cljs$core$IFn$_invoke$arity$2(this$,type) : m__5518__auto__.call(null,this$,type));
} else {
throw cljs.core.missing_protocol("Registry.-schema",this$);
}
}
});
/**
 * returns the schema from a registry
 */
malli.registry._schema = (function malli$registry$_schema(this$,type){
if((((!((this$ == null)))) && ((!((this$.malli$registry$Registry$_schema$arity$2 == null)))))){
return this$.malli$registry$Registry$_schema$arity$2(this$,type);
} else {
return malli$registry$Registry$_schema$dyn_8030(this$,type);
}
});

var malli$registry$Registry$_schemas$dyn_8031 = (function (this$){
var x__5519__auto__ = (((this$ == null))?null:this$);
var m__5520__auto__ = (malli.registry._schemas[goog.typeOf(x__5519__auto__)]);
if((!((m__5520__auto__ == null)))){
return (m__5520__auto__.cljs$core$IFn$_invoke$arity$1 ? m__5520__auto__.cljs$core$IFn$_invoke$arity$1(this$) : m__5520__auto__.call(null,this$));
} else {
var m__5518__auto__ = (malli.registry._schemas["_"]);
if((!((m__5518__auto__ == null)))){
return (m__5518__auto__.cljs$core$IFn$_invoke$arity$1 ? m__5518__auto__.cljs$core$IFn$_invoke$arity$1(this$) : m__5518__auto__.call(null,this$));
} else {
throw cljs.core.missing_protocol("Registry.-schemas",this$);
}
}
});
/**
 * returns all schemas from a registry
 */
malli.registry._schemas = (function malli$registry$_schemas(this$){
if((((!((this$ == null)))) && ((!((this$.malli$registry$Registry$_schemas$arity$1 == null)))))){
return this$.malli$registry$Registry$_schemas$arity$1(this$);
} else {
return malli$registry$Registry$_schemas$dyn_8031(this$);
}
});

malli.registry.registry_QMARK_ = (function malli$registry$registry_QMARK_(x){
if((!((x == null)))){
if(((false) || ((cljs.core.PROTOCOL_SENTINEL === x.malli$registry$Registry$)))){
return true;
} else {
return false;
}
} else {
return false;
}
});

/**
* @constructor
 * @implements {malli.registry.Registry}
 * @implements {cljs.core.IMeta}
 * @implements {cljs.core.IWithMeta}
*/
malli.registry.t_malli$registry7896 = (function (m,fm,meta7897){
this.m = m;
this.fm = fm;
this.meta7897 = meta7897;
this.cljs$lang$protocol_mask$partition0$ = 393216;
this.cljs$lang$protocol_mask$partition1$ = 0;
});
(malli.registry.t_malli$registry7896.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (_7898,meta7897__$1){
var self__ = this;
var _7898__$1 = this;
return (new malli.registry.t_malli$registry7896(self__.m,self__.fm,meta7897__$1));
}));

(malli.registry.t_malli$registry7896.prototype.cljs$core$IMeta$_meta$arity$1 = (function (_7898){
var self__ = this;
var _7898__$1 = this;
return self__.meta7897;
}));

(malli.registry.t_malli$registry7896.prototype.malli$registry$Registry$ = cljs.core.PROTOCOL_SENTINEL);

(malli.registry.t_malli$registry7896.prototype.malli$registry$Registry$_schema$arity$2 = (function (_,type){
var self__ = this;
var ___$1 = this;
return self__.fm.get(type);
}));

(malli.registry.t_malli$registry7896.prototype.malli$registry$Registry$_schemas$arity$1 = (function (_){
var self__ = this;
var ___$1 = this;
return self__.m;
}));

(malli.registry.t_malli$registry7896.getBasis = (function (){
return new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"m","m",-1021758608,null),new cljs.core.Symbol(null,"fm","fm",-1190690268,null),new cljs.core.Symbol(null,"meta7897","meta7897",1934246128,null)], null);
}));

(malli.registry.t_malli$registry7896.cljs$lang$type = true);

(malli.registry.t_malli$registry7896.cljs$lang$ctorStr = "malli.registry/t_malli$registry7896");

(malli.registry.t_malli$registry7896.cljs$lang$ctorPrWriter = (function (this__5455__auto__,writer__5456__auto__,opt__5457__auto__){
return cljs.core._write(writer__5456__auto__,"malli.registry/t_malli$registry7896");
}));

/**
 * Positional factory function for malli.registry/t_malli$registry7896.
 */
malli.registry.__GT_t_malli$registry7896 = (function malli$registry$__GT_t_malli$registry7896(m,fm,meta7897){
return (new malli.registry.t_malli$registry7896(m,fm,meta7897));
});


malli.registry.fast_registry = (function malli$registry$fast_registry(m){
var fm = m;
return (new malli.registry.t_malli$registry7896(m,fm,cljs.core.PersistentArrayMap.EMPTY));
});

/**
* @constructor
 * @implements {malli.registry.Registry}
 * @implements {cljs.core.IMeta}
 * @implements {cljs.core.IWithMeta}
*/
malli.registry.t_malli$registry7903 = (function (m,meta7904){
this.m = m;
this.meta7904 = meta7904;
this.cljs$lang$protocol_mask$partition0$ = 393216;
this.cljs$lang$protocol_mask$partition1$ = 0;
});
(malli.registry.t_malli$registry7903.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (_7905,meta7904__$1){
var self__ = this;
var _7905__$1 = this;
return (new malli.registry.t_malli$registry7903(self__.m,meta7904__$1));
}));

(malli.registry.t_malli$registry7903.prototype.cljs$core$IMeta$_meta$arity$1 = (function (_7905){
var self__ = this;
var _7905__$1 = this;
return self__.meta7904;
}));

(malli.registry.t_malli$registry7903.prototype.malli$registry$Registry$ = cljs.core.PROTOCOL_SENTINEL);

(malli.registry.t_malli$registry7903.prototype.malli$registry$Registry$_schema$arity$2 = (function (_,type){
var self__ = this;
var ___$1 = this;
return (self__.m.cljs$core$IFn$_invoke$arity$1 ? self__.m.cljs$core$IFn$_invoke$arity$1(type) : self__.m.call(null,type));
}));

(malli.registry.t_malli$registry7903.prototype.malli$registry$Registry$_schemas$arity$1 = (function (_){
var self__ = this;
var ___$1 = this;
return self__.m;
}));

(malli.registry.t_malli$registry7903.getBasis = (function (){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"m","m",-1021758608,null),new cljs.core.Symbol(null,"meta7904","meta7904",1768548384,null)], null);
}));

(malli.registry.t_malli$registry7903.cljs$lang$type = true);

(malli.registry.t_malli$registry7903.cljs$lang$ctorStr = "malli.registry/t_malli$registry7903");

(malli.registry.t_malli$registry7903.cljs$lang$ctorPrWriter = (function (this__5455__auto__,writer__5456__auto__,opt__5457__auto__){
return cljs.core._write(writer__5456__auto__,"malli.registry/t_malli$registry7903");
}));

/**
 * Positional factory function for malli.registry/t_malli$registry7903.
 */
malli.registry.__GT_t_malli$registry7903 = (function malli$registry$__GT_t_malli$registry7903(m,meta7904){
return (new malli.registry.t_malli$registry7903(m,meta7904));
});


malli.registry.simple_registry = (function malli$registry$simple_registry(m){
return (new malli.registry.t_malli$registry7903(m,cljs.core.PersistentArrayMap.EMPTY));
});
malli.registry.registry = (function malli$registry$registry(_QMARK_registry){
if((_QMARK_registry == null)){
return null;
} else {
if(malli.registry.registry_QMARK_(_QMARK_registry)){
return _QMARK_registry;
} else {
if(cljs.core.map_QMARK_(_QMARK_registry)){
return malli.registry.simple_registry(_QMARK_registry);
} else {
if((((!((_QMARK_registry == null))))?((((false) || ((cljs.core.PROTOCOL_SENTINEL === _QMARK_registry.malli$registry$Registry$))))?true:(((!_QMARK_registry.cljs$lang$protocol_mask$partition$))?cljs.core.native_satisfies_QMARK_(malli.registry.Registry,_QMARK_registry):false)):cljs.core.native_satisfies_QMARK_(malli.registry.Registry,_QMARK_registry))){
return _QMARK_registry;
} else {
return null;
}
}
}
}
});
malli.registry.registry_STAR_ = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(malli.registry.simple_registry(cljs.core.PersistentArrayMap.EMPTY));
malli.registry.set_default_registry_BANG_ = (function malli$registry$set_default_registry_BANG_(_QMARK_registry){
if((!((malli.registry.mode === "strict")))){
return cljs.core.reset_BANG_(malli.registry.registry_STAR_,malli.registry.registry(_QMARK_registry));
} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2("can't set default registry, invalid mode",new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"mode","mode",654403691),malli.registry.mode,new cljs.core.Keyword(null,"type","type",1174270348),malli.registry.type], null));
}
});

/**
* @constructor
 * @implements {malli.registry.Registry}
 * @implements {cljs.core.IMeta}
 * @implements {cljs.core.IWithMeta}
*/
malli.registry.t_malli$registry7907 = (function (meta7908){
this.meta7908 = meta7908;
this.cljs$lang$protocol_mask$partition0$ = 393216;
this.cljs$lang$protocol_mask$partition1$ = 0;
});
(malli.registry.t_malli$registry7907.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (_7909,meta7908__$1){
var self__ = this;
var _7909__$1 = this;
return (new malli.registry.t_malli$registry7907(meta7908__$1));
}));

(malli.registry.t_malli$registry7907.prototype.cljs$core$IMeta$_meta$arity$1 = (function (_7909){
var self__ = this;
var _7909__$1 = this;
return self__.meta7908;
}));

(malli.registry.t_malli$registry7907.prototype.malli$registry$Registry$ = cljs.core.PROTOCOL_SENTINEL);

(malli.registry.t_malli$registry7907.prototype.malli$registry$Registry$_schema$arity$2 = (function (_,type){
var self__ = this;
var ___$1 = this;
return malli.registry._schema(cljs.core.deref(malli.registry.registry_STAR_),type);
}));

(malli.registry.t_malli$registry7907.prototype.malli$registry$Registry$_schemas$arity$1 = (function (_){
var self__ = this;
var ___$1 = this;
return malli.registry._schemas(cljs.core.deref(malli.registry.registry_STAR_));
}));

(malli.registry.t_malli$registry7907.getBasis = (function (){
return new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"meta7908","meta7908",-295800777,null)], null);
}));

(malli.registry.t_malli$registry7907.cljs$lang$type = true);

(malli.registry.t_malli$registry7907.cljs$lang$ctorStr = "malli.registry/t_malli$registry7907");

(malli.registry.t_malli$registry7907.cljs$lang$ctorPrWriter = (function (this__5455__auto__,writer__5456__auto__,opt__5457__auto__){
return cljs.core._write(writer__5456__auto__,"malli.registry/t_malli$registry7907");
}));

/**
 * Positional factory function for malli.registry/t_malli$registry7907.
 */
malli.registry.__GT_t_malli$registry7907 = (function malli$registry$__GT_t_malli$registry7907(meta7908){
return (new malli.registry.t_malli$registry7907(meta7908));
});


malli.registry.custom_default_registry = (function malli$registry$custom_default_registry(){
return (new malli.registry.t_malli$registry7907(cljs.core.PersistentArrayMap.EMPTY));
});

/**
* @constructor
 * @implements {malli.registry.Registry}
 * @implements {cljs.core.IMeta}
 * @implements {cljs.core.IWithMeta}
*/
malli.registry.t_malli$registry7931 = (function (_QMARK_registries,registries,meta7932){
this._QMARK_registries = _QMARK_registries;
this.registries = registries;
this.meta7932 = meta7932;
this.cljs$lang$protocol_mask$partition0$ = 393216;
this.cljs$lang$protocol_mask$partition1$ = 0;
});
(malli.registry.t_malli$registry7931.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (_7933,meta7932__$1){
var self__ = this;
var _7933__$1 = this;
return (new malli.registry.t_malli$registry7931(self__._QMARK_registries,self__.registries,meta7932__$1));
}));

(malli.registry.t_malli$registry7931.prototype.cljs$core$IMeta$_meta$arity$1 = (function (_7933){
var self__ = this;
var _7933__$1 = this;
return self__.meta7932;
}));

(malli.registry.t_malli$registry7931.prototype.malli$registry$Registry$ = cljs.core.PROTOCOL_SENTINEL);

(malli.registry.t_malli$registry7931.prototype.malli$registry$Registry$_schema$arity$2 = (function (_,type){
var self__ = this;
var ___$1 = this;
return cljs.core.some((function (p1__7919_SHARP_){
return malli.registry._schema(p1__7919_SHARP_,type);
}),self__.registries);
}));

(malli.registry.t_malli$registry7931.prototype.malli$registry$Registry$_schemas$arity$1 = (function (_){
var self__ = this;
var ___$1 = this;
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$2(cljs.core.merge,cljs.core.map.cljs$core$IFn$_invoke$arity$2(malli.registry._schemas,cljs.core.reverse(self__.registries)));
}));

(malli.registry.t_malli$registry7931.getBasis = (function (){
return new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"?registries","?registries",2135368100,null),new cljs.core.Symbol(null,"registries","registries",-1366064418,null),new cljs.core.Symbol(null,"meta7932","meta7932",350350750,null)], null);
}));

(malli.registry.t_malli$registry7931.cljs$lang$type = true);

(malli.registry.t_malli$registry7931.cljs$lang$ctorStr = "malli.registry/t_malli$registry7931");

(malli.registry.t_malli$registry7931.cljs$lang$ctorPrWriter = (function (this__5455__auto__,writer__5456__auto__,opt__5457__auto__){
return cljs.core._write(writer__5456__auto__,"malli.registry/t_malli$registry7931");
}));

/**
 * Positional factory function for malli.registry/t_malli$registry7931.
 */
malli.registry.__GT_t_malli$registry7931 = (function malli$registry$__GT_t_malli$registry7931(_QMARK_registries,registries,meta7932){
return (new malli.registry.t_malli$registry7931(_QMARK_registries,registries,meta7932));
});


malli.registry.composite_registry = (function malli$registry$composite_registry(var_args){
var args__5903__auto__ = [];
var len__5897__auto___8050 = arguments.length;
var i__5898__auto___8051 = (0);
while(true){
if((i__5898__auto___8051 < len__5897__auto___8050)){
args__5903__auto__.push((arguments[i__5898__auto___8051]));

var G__8053 = (i__5898__auto___8051 + (1));
i__5898__auto___8051 = G__8053;
continue;
} else {
}
break;
}

var argseq__5904__auto__ = ((((0) < args__5903__auto__.length))?(new cljs.core.IndexedSeq(args__5903__auto__.slice((0)),(0),null)):null);
return malli.registry.composite_registry.cljs$core$IFn$_invoke$arity$variadic(argseq__5904__auto__);
});

(malli.registry.composite_registry.cljs$core$IFn$_invoke$arity$variadic = (function (_QMARK_registries){
var registries = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(malli.registry.registry,_QMARK_registries);
return (new malli.registry.t_malli$registry7931(_QMARK_registries,registries,cljs.core.PersistentArrayMap.EMPTY));
}));

(malli.registry.composite_registry.cljs$lang$maxFixedArity = (0));

/** @this {Function} */
(malli.registry.composite_registry.cljs$lang$applyTo = (function (seq7921){
var self__5883__auto__ = this;
return self__5883__auto__.cljs$core$IFn$_invoke$arity$variadic(cljs.core.seq(seq7921));
}));


/**
* @constructor
 * @implements {malli.registry.Registry}
 * @implements {cljs.core.IMeta}
 * @implements {cljs.core.IWithMeta}
*/
malli.registry.t_malli$registry7958 = (function (db,meta7959){
this.db = db;
this.meta7959 = meta7959;
this.cljs$lang$protocol_mask$partition0$ = 393216;
this.cljs$lang$protocol_mask$partition1$ = 0;
});
(malli.registry.t_malli$registry7958.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (_7960,meta7959__$1){
var self__ = this;
var _7960__$1 = this;
return (new malli.registry.t_malli$registry7958(self__.db,meta7959__$1));
}));

(malli.registry.t_malli$registry7958.prototype.cljs$core$IMeta$_meta$arity$1 = (function (_7960){
var self__ = this;
var _7960__$1 = this;
return self__.meta7959;
}));

(malli.registry.t_malli$registry7958.prototype.malli$registry$Registry$ = cljs.core.PROTOCOL_SENTINEL);

(malli.registry.t_malli$registry7958.prototype.malli$registry$Registry$_schema$arity$2 = (function (_,type){
var self__ = this;
var ___$1 = this;
return malli.registry._schema(malli.registry.registry(cljs.core.deref(self__.db)),type);
}));

(malli.registry.t_malli$registry7958.prototype.malli$registry$Registry$_schemas$arity$1 = (function (_){
var self__ = this;
var ___$1 = this;
return malli.registry._schemas(malli.registry.registry(cljs.core.deref(self__.db)));
}));

(malli.registry.t_malli$registry7958.getBasis = (function (){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"db","db",-1661185010,null),new cljs.core.Symbol(null,"meta7959","meta7959",-957163539,null)], null);
}));

(malli.registry.t_malli$registry7958.cljs$lang$type = true);

(malli.registry.t_malli$registry7958.cljs$lang$ctorStr = "malli.registry/t_malli$registry7958");

(malli.registry.t_malli$registry7958.cljs$lang$ctorPrWriter = (function (this__5455__auto__,writer__5456__auto__,opt__5457__auto__){
return cljs.core._write(writer__5456__auto__,"malli.registry/t_malli$registry7958");
}));

/**
 * Positional factory function for malli.registry/t_malli$registry7958.
 */
malli.registry.__GT_t_malli$registry7958 = (function malli$registry$__GT_t_malli$registry7958(db,meta7959){
return (new malli.registry.t_malli$registry7958(db,meta7959));
});


malli.registry.mutable_registry = (function malli$registry$mutable_registry(db){
return (new malli.registry.t_malli$registry7958(db,cljs.core.PersistentArrayMap.EMPTY));
});

/**
* @constructor
 * @implements {malli.registry.Registry}
 * @implements {cljs.core.IMeta}
 * @implements {cljs.core.IWithMeta}
*/
malli.registry.t_malli$registry7971 = (function (meta7972){
this.meta7972 = meta7972;
this.cljs$lang$protocol_mask$partition0$ = 393216;
this.cljs$lang$protocol_mask$partition1$ = 0;
});
(malli.registry.t_malli$registry7971.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (_7973,meta7972__$1){
var self__ = this;
var _7973__$1 = this;
return (new malli.registry.t_malli$registry7971(meta7972__$1));
}));

(malli.registry.t_malli$registry7971.prototype.cljs$core$IMeta$_meta$arity$1 = (function (_7973){
var self__ = this;
var _7973__$1 = this;
return self__.meta7972;
}));

(malli.registry.t_malli$registry7971.prototype.malli$registry$Registry$ = cljs.core.PROTOCOL_SENTINEL);

(malli.registry.t_malli$registry7971.prototype.malli$registry$Registry$_schema$arity$2 = (function (_,type){
var self__ = this;
var ___$1 = this;
if(cljs.core.var_QMARK_(type)){
return cljs.core.deref(type);
} else {
return null;
}
}));

(malli.registry.t_malli$registry7971.prototype.malli$registry$Registry$_schemas$arity$1 = (function (_){
var self__ = this;
var ___$1 = this;
return null;
}));

(malli.registry.t_malli$registry7971.getBasis = (function (){
return new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"meta7972","meta7972",1638664231,null)], null);
}));

(malli.registry.t_malli$registry7971.cljs$lang$type = true);

(malli.registry.t_malli$registry7971.cljs$lang$ctorStr = "malli.registry/t_malli$registry7971");

(malli.registry.t_malli$registry7971.cljs$lang$ctorPrWriter = (function (this__5455__auto__,writer__5456__auto__,opt__5457__auto__){
return cljs.core._write(writer__5456__auto__,"malli.registry/t_malli$registry7971");
}));

/**
 * Positional factory function for malli.registry/t_malli$registry7971.
 */
malli.registry.__GT_t_malli$registry7971 = (function malli$registry$__GT_t_malli$registry7971(meta7972){
return (new malli.registry.t_malli$registry7971(meta7972));
});


malli.registry.var_registry = (function malli$registry$var_registry(){
return (new malli.registry.t_malli$registry7971(cljs.core.PersistentArrayMap.EMPTY));
});
malli.registry._STAR_registry_STAR_ = cljs.core.PersistentArrayMap.EMPTY;

/**
* @constructor
 * @implements {malli.registry.Registry}
 * @implements {cljs.core.IMeta}
 * @implements {cljs.core.IWithMeta}
*/
malli.registry.t_malli$registry7994 = (function (meta7995){
this.meta7995 = meta7995;
this.cljs$lang$protocol_mask$partition0$ = 393216;
this.cljs$lang$protocol_mask$partition1$ = 0;
});
(malli.registry.t_malli$registry7994.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (_7996,meta7995__$1){
var self__ = this;
var _7996__$1 = this;
return (new malli.registry.t_malli$registry7994(meta7995__$1));
}));

(malli.registry.t_malli$registry7994.prototype.cljs$core$IMeta$_meta$arity$1 = (function (_7996){
var self__ = this;
var _7996__$1 = this;
return self__.meta7995;
}));

(malli.registry.t_malli$registry7994.prototype.malli$registry$Registry$ = cljs.core.PROTOCOL_SENTINEL);

(malli.registry.t_malli$registry7994.prototype.malli$registry$Registry$_schema$arity$2 = (function (_,type){
var self__ = this;
var ___$1 = this;
return malli.registry._schema(malli.registry.registry(malli.registry._STAR_registry_STAR_),type);
}));

(malli.registry.t_malli$registry7994.prototype.malli$registry$Registry$_schemas$arity$1 = (function (_){
var self__ = this;
var ___$1 = this;
return malli.registry._schemas(malli.registry.registry(malli.registry._STAR_registry_STAR_));
}));

(malli.registry.t_malli$registry7994.getBasis = (function (){
return new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"meta7995","meta7995",-69416600,null)], null);
}));

(malli.registry.t_malli$registry7994.cljs$lang$type = true);

(malli.registry.t_malli$registry7994.cljs$lang$ctorStr = "malli.registry/t_malli$registry7994");

(malli.registry.t_malli$registry7994.cljs$lang$ctorPrWriter = (function (this__5455__auto__,writer__5456__auto__,opt__5457__auto__){
return cljs.core._write(writer__5456__auto__,"malli.registry/t_malli$registry7994");
}));

/**
 * Positional factory function for malli.registry/t_malli$registry7994.
 */
malli.registry.__GT_t_malli$registry7994 = (function malli$registry$__GT_t_malli$registry7994(meta7995){
return (new malli.registry.t_malli$registry7994(meta7995));
});


malli.registry.dynamic_registry = (function malli$registry$dynamic_registry(){
return (new malli.registry.t_malli$registry7994(cljs.core.PersistentArrayMap.EMPTY));
});

/**
* @constructor
 * @implements {malli.registry.Registry}
 * @implements {cljs.core.IMeta}
 * @implements {cljs.core.IWithMeta}
*/
malli.registry.t_malli$registry8010 = (function (default_registry,provider,cache_STAR_,registry_STAR_,meta8011){
this.default_registry = default_registry;
this.provider = provider;
this.cache_STAR_ = cache_STAR_;
this.registry_STAR_ = registry_STAR_;
this.meta8011 = meta8011;
this.cljs$lang$protocol_mask$partition0$ = 393216;
this.cljs$lang$protocol_mask$partition1$ = 0;
});
(malli.registry.t_malli$registry8010.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (_8012,meta8011__$1){
var self__ = this;
var _8012__$1 = this;
return (new malli.registry.t_malli$registry8010(self__.default_registry,self__.provider,self__.cache_STAR_,self__.registry_STAR_,meta8011__$1));
}));

(malli.registry.t_malli$registry8010.prototype.cljs$core$IMeta$_meta$arity$1 = (function (_8012){
var self__ = this;
var _8012__$1 = this;
return self__.meta8011;
}));

(malli.registry.t_malli$registry8010.prototype.malli$registry$Registry$ = cljs.core.PROTOCOL_SENTINEL);

(malli.registry.t_malli$registry8010.prototype.malli$registry$Registry$_schema$arity$2 = (function (_,name){
var self__ = this;
var ___$1 = this;
var or__5162__auto__ = (function (){var fexpr__8019 = cljs.core.deref(self__.cache_STAR_);
return (fexpr__8019.cljs$core$IFn$_invoke$arity$1 ? fexpr__8019.cljs$core$IFn$_invoke$arity$1(name) : fexpr__8019.call(null,name));
})();
if(cljs.core.truth_(or__5162__auto__)){
return or__5162__auto__;
} else {
var temp__5823__auto__ = (function (){var G__8020 = name;
var G__8021 = cljs.core.deref(self__.registry_STAR_);
return (self__.provider.cljs$core$IFn$_invoke$arity$2 ? self__.provider.cljs$core$IFn$_invoke$arity$2(G__8020,G__8021) : self__.provider.call(null,G__8020,G__8021));
})();
if(cljs.core.truth_(temp__5823__auto__)){
var schema = temp__5823__auto__;
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(self__.cache_STAR_,cljs.core.assoc,name,schema);

return schema;
} else {
return null;
}
}
}));

(malli.registry.t_malli$registry8010.prototype.malli$registry$Registry$_schemas$arity$1 = (function (_){
var self__ = this;
var ___$1 = this;
return cljs.core.deref(self__.cache_STAR_);
}));

(malli.registry.t_malli$registry8010.getBasis = (function (){
return new cljs.core.PersistentVector(null, 5, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"default-registry","default-registry",732204441,null),new cljs.core.Symbol(null,"provider","provider",1338474627,null),new cljs.core.Symbol(null,"cache*","cache*",-548597526,null),new cljs.core.Symbol(null,"registry*","registry*",-268031273,null),new cljs.core.Symbol(null,"meta8011","meta8011",-105660234,null)], null);
}));

(malli.registry.t_malli$registry8010.cljs$lang$type = true);

(malli.registry.t_malli$registry8010.cljs$lang$ctorStr = "malli.registry/t_malli$registry8010");

(malli.registry.t_malli$registry8010.cljs$lang$ctorPrWriter = (function (this__5455__auto__,writer__5456__auto__,opt__5457__auto__){
return cljs.core._write(writer__5456__auto__,"malli.registry/t_malli$registry8010");
}));

/**
 * Positional factory function for malli.registry/t_malli$registry8010.
 */
malli.registry.__GT_t_malli$registry8010 = (function malli$registry$__GT_t_malli$registry8010(default_registry,provider,cache_STAR_,registry_STAR_,meta8011){
return (new malli.registry.t_malli$registry8010(default_registry,provider,cache_STAR_,registry_STAR_,meta8011));
});


malli.registry.lazy_registry = (function malli$registry$lazy_registry(default_registry,provider){
var cache_STAR_ = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
var registry_STAR_ = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(default_registry);
return cljs.core.reset_BANG_(registry_STAR_,malli.registry.composite_registry.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([default_registry,(new malli.registry.t_malli$registry8010(default_registry,provider,cache_STAR_,registry_STAR_,cljs.core.PersistentArrayMap.EMPTY))], 0)));
});
/**
 * finds a schema from a registry
 */
malli.registry.schema = (function malli$registry$schema(registry,type){
return malli.registry._schema(registry,type);
});
/**
 * finds all schemas from a registry
 */
malli.registry.schemas = (function malli$registry$schemas(registry){
return malli.registry._schemas(registry);
});

//# sourceMappingURL=malli.registry.js.map

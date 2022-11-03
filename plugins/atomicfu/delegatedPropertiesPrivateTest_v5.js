var main = function (_) {
  'use strict';
  //region block: pre-declaration
  setMetadataFor(Unit, 'Unit', objectMeta, VOID, VOID, VOID, VOID, []);
  setMetadataFor(DelegatedPropertiesPrivate, 'DelegatedPropertiesPrivate', classMeta, VOID, VOID, VOID, VOID, []);
  //endregion
  //region file: jsMainSources/core/builtins/src/kotlin/Unit.kt
  function Unit() {
    Unit_instance = this;
  }
  var Unit_instance;
  function Unit_getInstance() {
    if (Unit_instance == null)
      new Unit();
    return Unit_instance;
  }
  //endregion
  //region file: jsMainSources/libraries/stdlib/js-ir/runtime/arrays.kt
  //endregion
  //region file: jsMainSources/libraries/stdlib/js-ir/runtime/coreRuntime.kt
  function protoOf(constructor) {
    return constructor.prototype;
  }
  function defineProp(obj, name, getter, setter) {
    return Object.defineProperty(obj, name, {configurable: true, get: getter, set: setter});
  }
  function objectCreate(proto) {
    return Object.create(proto);
  }
  //endregion
  //region file: jsMainSources/libraries/stdlib/js-ir/runtime/noPackageHacks.kt
  //endregion
  //region file: jsMainSources/libraries/stdlib/js-ir/runtime/numberConversion.kt
  //endregion
  //region file: jsMainSources/libraries/stdlib/js-ir/runtime/typeCheckUtils.kt
  function classMeta(name, associatedObjectKey, associatedObjects, suspendArity) {
    return createMetadata('class', name, associatedObjectKey, associatedObjects, suspendArity, null);
  }
  function createMetadata(kind, name, associatedObjectKey, associatedObjects, suspendArity, iid) {
    var undef = get_VOID();
    return {kind: kind, simpleName: name, associatedObjectKey: associatedObjectKey, associatedObjects: associatedObjects, suspendArity: suspendArity, $kClass$: undef, iid: iid};
  }
  function setMetadataFor(ctor, name, metadataConstructor, parent, interfaces, associatedObjectKey, associatedObjects, suspendArity) {
    if (!(parent == null)) {
      ctor.prototype = Object.create(parent.prototype);
      ctor.prototype.constructor = ctor;
    }
    var metadata = metadataConstructor(name, associatedObjectKey, associatedObjects, suspendArity);
    ctor.$metadata$ = metadata;
    if (!(interfaces == null)) {
      var receiver = !(metadata.iid == null) ? ctor : ctor.prototype;
      receiver.$imask$ = implement(interfaces.slice());
    }
  }
  function objectMeta(name, associatedObjectKey, associatedObjects, suspendArity) {
    return createMetadata('object', name, associatedObjectKey, associatedObjects, suspendArity, null);
  }
  //endregion
  //region file: jsMainSources/libraries/stdlib/js-ir/runtime/void.kt
  function get_VOID() {
    init_properties_void_kt_71liqu();
    return VOID;
  }
  var VOID;
  var properties_initialized_void_kt_e4ret2;
  function init_properties_void_kt_71liqu() {
    if (properties_initialized_void_kt_e4ret2) {
    } else {
      properties_initialized_void_kt_e4ret2 = true;
      VOID = void 0;
    }
  }
  //endregion
  //region file: /DelegatedPropertiesPrivate.kt
  function _set_a__db556s($this, _set____db54di) {
    // Inline function 'DelegatedPropertiesPrivate.<set-a>.<set-_a>' call
    this.a_1 = _set____db54di;
    return Unit_getInstance();
  }
  function DelegatedPropertiesPrivate() {
    this.a_1 = 42;
    this.b_1 = this.a_1;
    this.c_1 = this.a_1;
  }
  protoOf(DelegatedPropertiesPrivate).d = function (_set____db54di) {
    // Inline function 'DelegatedPropertiesPrivate.<set-b>.<set-_a>' call
    this.a_1 = _set____db54di;
    return Unit_getInstance();
  };
  protoOf(DelegatedPropertiesPrivate).e = function () {
    _set_a__db556s(this, 5);
    this.d(7);
  };
  function box() {
    var testClass = new DelegatedPropertiesPrivate();
    testClass.e();
    return 'OK';
  }
  //endregion
  //region block: exports
  function $jsExportAll$(_) {
    _.box = box;
  }
  $jsExportAll$(_);
  //endregion
  return _;
}(typeof main === 'undefined' ? {} : main);

//# sourceMappingURL=delegatedPropertiesPrivateTest_v5.js.map

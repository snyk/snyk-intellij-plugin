/******/ (function(modules) { // webpackBootstrap
/******/ 	// The module cache
/******/ 	var installedModules = {};
/******/
/******/ 	// The require function
/******/ 	function __webpack_require__(moduleId) {
/******/
/******/ 		// Check if module is in cache
/******/ 		if(installedModules[moduleId]) {
/******/ 			return installedModules[moduleId].exports;
/******/ 		}
/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = installedModules[moduleId] = {
/******/ 			i: moduleId,
/******/ 			l: false,
/******/ 			exports: {}
/******/ 		};
/******/
/******/ 		// Execute the module function
/******/ 		modules[moduleId].call(module.exports, module, module.exports, __webpack_require__);
/******/
/******/ 		// Flag the module as loaded
/******/ 		module.l = true;
/******/
/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}
/******/
/******/
/******/ 	// expose the modules object (__webpack_modules__)
/******/ 	__webpack_require__.m = modules;
/******/
/******/ 	// expose the module cache
/******/ 	__webpack_require__.c = installedModules;
/******/
/******/ 	// define getter function for harmony exports
/******/ 	__webpack_require__.d = function(exports, name, getter) {
/******/ 		if(!__webpack_require__.o(exports, name)) {
/******/ 			Object.defineProperty(exports, name, { enumerable: true, get: getter });
/******/ 		}
/******/ 	};
/******/
/******/ 	// define __esModule on exports
/******/ 	__webpack_require__.r = function(exports) {
/******/ 		if(typeof Symbol !== 'undefined' && Symbol.toStringTag) {
/******/ 			Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
/******/ 		}
/******/ 		Object.defineProperty(exports, '__esModule', { value: true });
/******/ 	};
/******/
/******/ 	// create a fake namespace object
/******/ 	// mode & 1: value is a module id, require it
/******/ 	// mode & 2: merge all properties of value into the ns
/******/ 	// mode & 4: return value when already ns object
/******/ 	// mode & 8|1: behave like require
/******/ 	__webpack_require__.t = function(value, mode) {
/******/ 		if(mode & 1) value = __webpack_require__(value);
/******/ 		if(mode & 8) return value;
/******/ 		if((mode & 4) && typeof value === 'object' && value && value.__esModule) return value;
/******/ 		var ns = Object.create(null);
/******/ 		__webpack_require__.r(ns);
/******/ 		Object.defineProperty(ns, 'default', { enumerable: true, value: value });
/******/ 		if(mode & 2 && typeof value != 'string') for(var key in value) __webpack_require__.d(ns, key, function(key) { return value[key]; }.bind(null, key));
/******/ 		return ns;
/******/ 	};
/******/
/******/ 	// getDefaultExport function for compatibility with non-harmony modules
/******/ 	__webpack_require__.n = function(module) {
/******/ 		var getter = module && module.__esModule ?
/******/ 			function getDefault() { return module['default']; } :
/******/ 			function getModuleExports() { return module; };
/******/ 		__webpack_require__.d(getter, 'a', getter);
/******/ 		return getter;
/******/ 	};
/******/
/******/ 	// Object.prototype.hasOwnProperty.call
/******/ 	__webpack_require__.o = function(object, property) { return Object.prototype.hasOwnProperty.call(object, property); };
/******/
/******/ 	// __webpack_public_path__
/******/ 	__webpack_require__.p = "";
/******/
/******/
/******/ 	// Load entry module and return exports
/******/ 	return __webpack_require__(__webpack_require__.s = "./components/vue/search-filter/search-filter.vue");
/******/ })
/************************************************************************/
/******/ ({

/***/ "./components/vue/search-filter/search-filter.html?vue&type=template&id=4ad1a377":
/*!***************************************************************************************!*\
  !*** ./components/vue/search-filter/search-filter.html?vue&type=template&id=4ad1a377 ***!
  \***************************************************************************************/
/*! exports provided: render, staticRenderFns */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _node_modules_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_lib_loaders_compile_handlebars_loader_js_search_filter_html_vue_type_template_id_4ad1a377__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!../../../lib/loaders/compile-handlebars-loader.js!./search-filter.html?vue&type=template&id=4ad1a377 */ \"./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js!./components/vue/search-filter/search-filter.html?vue&type=template&id=4ad1a377\");\n/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, \"render\", function() { return _node_modules_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_lib_loaders_compile_handlebars_loader_js_search_filter_html_vue_type_template_id_4ad1a377__WEBPACK_IMPORTED_MODULE_0__[\"render\"]; });\n\n/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, \"staticRenderFns\", function() { return _node_modules_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_lib_loaders_compile_handlebars_loader_js_search_filter_html_vue_type_template_id_4ad1a377__WEBPACK_IMPORTED_MODULE_0__[\"staticRenderFns\"]; });\n\n\n\n//# sourceURL=webpack:///./components/vue/search-filter/search-filter.html?");

/***/ }),

/***/ "./components/vue/search-filter/search-filter.vue":
/*!********************************************************!*\
  !*** ./components/vue/search-filter/search-filter.vue ***!
  \********************************************************/
/*! exports provided: default */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _search_filter_html_vue_type_template_id_4ad1a377__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./search-filter.html?vue&type=template&id=4ad1a377 */ \"./components/vue/search-filter/search-filter.html?vue&type=template&id=4ad1a377\");\n/* harmony import */ var _search_filter_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./search-filter.vue?vue&type=script&lang=js */ \"./components/vue/search-filter/search-filter.vue?vue&type=script&lang=js\");\n/* empty/unused harmony star reexport *//* harmony import */ var _node_modules_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ../../../node_modules/vue-loader/lib/runtime/componentNormalizer.js */ \"./node_modules/vue-loader/lib/runtime/componentNormalizer.js\");\n/* harmony import */ var vue__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! vue */ \"vue\");\n/* harmony import */ var vue__WEBPACK_IMPORTED_MODULE_3___default = /*#__PURE__*/__webpack_require__.n(vue__WEBPACK_IMPORTED_MODULE_3__);\n/* harmony import */ var _Users_kevin_projects_snyk_snyk_ui_components_vue_search_filter_search_filter_vue__WEBPACK_IMPORTED_MODULE_4__ = __webpack_require__(/*! ./components/vue/search-filter/search-filter.vue */ \"./components/vue/search-filter/search-filter.vue\");\n\n\n\n\n\n/* normalize component */\n\nvar component = Object(_node_modules_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_2__[\"default\"])(\n  _search_filter_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_1__[\"default\"],\n  _search_filter_html_vue_type_template_id_4ad1a377__WEBPACK_IMPORTED_MODULE_0__[\"render\"],\n  _search_filter_html_vue_type_template_id_4ad1a377__WEBPACK_IMPORTED_MODULE_0__[\"staticRenderFns\"],\n  false,\n  null,\n  null,\n  null\n  \n)\n\n/* hot reload */\nif (false) { var api; }\ncomponent.options.__file = \"components/vue/search-filter/search-filter.vue\"\n/* harmony default export */ __webpack_exports__[\"default\"] = (component.exports);\n    \n    \n\n    if (!document.querySelector('[data-vue]')) {\n      const el = document.createElement('div');\n\n      el.appendChild(document.createElement('snyk-component'));\n      document.body.appendChild(el);\n\n      new vue__WEBPACK_IMPORTED_MODULE_3___default.a({\n        el,\n        components: {SnykComponent: _Users_kevin_projects_snyk_snyk_ui_components_vue_search_filter_search_filter_vue__WEBPACK_IMPORTED_MODULE_4__[\"default\"]},\n        mounted() {\n\n          let comp = this.$children[0];\n\n          Object.keys(comp._props || {}).forEach((key, i) => {\n            if (window.SnykUIFixture[key]) {\n              comp._props[key] = window.SnykUIFixture[key];\n              console.log('☝️ No worries');\n            }\n          });\n        }\n      });\n    }\n\n\n//# sourceURL=webpack:///./components/vue/search-filter/search-filter.vue?");

/***/ }),

/***/ "./components/vue/search-filter/search-filter.vue?vue&type=script&lang=js":
/*!********************************************************************************!*\
  !*** ./components/vue/search-filter/search-filter.vue?vue&type=script&lang=js ***!
  \********************************************************************************/
/*! exports provided: default */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _node_modules_babel_loader_lib_index_js_lib_loaders_snyk_ui_fractal_wrapper_js_node_modules_vue_loader_lib_index_js_vue_loader_options_search_filter_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../node_modules/babel-loader/lib!../../../lib/loaders/snyk-ui-fractal-wrapper.js!../../../node_modules/vue-loader/lib??vue-loader-options!./search-filter.vue?vue&type=script&lang=js */ \"./node_modules/babel-loader/lib/index.js!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib/index.js??vue-loader-options!./components/vue/search-filter/search-filter.vue?vue&type=script&lang=js\");\n/* empty/unused harmony star reexport */ /* harmony default export */ __webpack_exports__[\"default\"] = (_node_modules_babel_loader_lib_index_js_lib_loaders_snyk_ui_fractal_wrapper_js_node_modules_vue_loader_lib_index_js_vue_loader_options_search_filter_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_0__[\"default\"]); \n\n//# sourceURL=webpack:///./components/vue/search-filter/search-filter.vue?");

/***/ }),

/***/ "./node_modules/babel-loader/lib/index.js!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib/index.js??vue-loader-options!./components/vue/search-filter/search-filter.vue?vue&type=script&lang=js":
/*!***********************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/babel-loader/lib!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib??vue-loader-options!./components/vue/search-filter/search-filter.vue?vue&type=script&lang=js ***!
  \***********************************************************************************************************************************************************************************************************/
/*! exports provided: default */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var vue_clickaway__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! vue-clickaway */ \"vue-clickaway\");\n/* harmony import */ var vue_clickaway__WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(vue_clickaway__WEBPACK_IMPORTED_MODULE_0__);\n//\n\n/* harmony default export */ __webpack_exports__[\"default\"] = ({\n  mixins: [vue_clickaway__WEBPACK_IMPORTED_MODULE_0__[\"mixin\"]],\n  name: 'SearchFilter',\n  props: {\n    'label': {\n      default: 'Label'\n    },\n    'name': {\n      default: 'label'\n    },\n    'items': {\n      type: Array,\n\n      default() {\n        return [];\n      }\n\n    }\n  },\n\n  data() {\n    return {\n      hasFocus: false,\n      selectedItems: [],\n      searchQuery: null,\n      maximumNumberOfItems: 50\n    };\n  },\n\n  computed: {\n    searchPlaceholder() {\n      return `Search for ${this.label}`;\n    },\n\n    matchingQuery() {\n      if (!this.searchQuery) {\n        return this.items.slice(0, this.maximumNumberOfItems);\n      }\n\n      let pattern = new RegExp(`^${this.searchQuery}`, 'ig');\n      return this.items.filter(item => pattern.test(item.title)).slice(0, this.maximumNumberOfItems);\n    }\n\n  },\n  watch: {\n    selectedItems(newVal) {\n      this.$emit('selected-items-updated', newVal, this.name, this.items.length);\n    }\n\n  },\n  methods: {\n    setFocus() {\n      this.hasFocus = true;\n    },\n\n    releaseFocus() {\n      this.hasFocus = false;\n    },\n\n    selectAll() {\n      this.selectedItems = this.items.map(item => {\n        return item.value;\n      });\n    },\n\n    deselectAll() {\n      this.selectedItems = [];\n    }\n\n  }\n});\n\n//# sourceURL=webpack:///./components/vue/search-filter/search-filter.vue?./node_modules/babel-loader/lib!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib??vue-loader-options");

/***/ }),

/***/ "./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js!./components/vue/search-filter/search-filter.html?vue&type=template&id=4ad1a377":
/*!**************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js!./components/vue/search-filter/search-filter.html?vue&type=template&id=4ad1a377 ***!
  \**************************************************************************************************************************************************************************************************************/
/*! exports provided: render, staticRenderFns */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, \"render\", function() { return render; });\n/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, \"staticRenderFns\", function() { return staticRenderFns; });\nvar render = function() {\n  var _vm = this\n  var _h = _vm.$createElement\n  var _c = _vm._self._c || _h\n  return _c(\n    \"div\",\n    {\n      directives: [\n        {\n          name: \"on-clickaway\",\n          rawName: \"v-on-clickaway\",\n          value: _vm.releaseFocus,\n          expression: \"releaseFocus\"\n        }\n      ]\n    },\n    [\n      _c(\"div\", { staticClass: \"search-filter\" }, [\n        _c(\n          \"div\",\n          {\n            staticClass: \"search\",\n            attrs: { role: \"search\", \"data-snyk-test\": \"search\" }\n          },\n          [\n            _c(\"input\", {\n              directives: [\n                {\n                  name: \"model\",\n                  rawName: \"v-model\",\n                  value: _vm.searchQuery,\n                  expression: \"searchQuery\"\n                }\n              ],\n              staticClass: \"input--xs\",\n              attrs: {\n                type: \"search\",\n                placeholder: _vm.searchPlaceholder,\n                \"data-snyk-search-filter\": \"input\",\n                \"data-snyk-test\": \"search input\"\n              },\n              domProps: { value: _vm.searchQuery },\n              on: {\n                focus: _vm.setFocus,\n                input: function($event) {\n                  if ($event.target.composing) {\n                    return\n                  }\n                  _vm.searchQuery = $event.target.value\n                }\n              }\n            })\n          ]\n        ),\n        _vm._v(\" \"),\n        _vm.hasFocus\n          ? _c(\"div\", { staticClass: \"search-filter__dropdown\" }, [\n              _c(\n                \"div\",\n                {\n                  staticClass: \"dropdown__options\",\n                  staticStyle: { \"overflow-x\": \"hidden\" }\n                },\n                [\n                  _c(\"div\", { staticClass: \"l-repel l-push-btm--xs t--xs\" }, [\n                    _c(\"h3\", [\n                      _vm._v(\n                        _vm._s(_vm.label) +\n                          \" (\" +\n                          _vm._s(_vm.selectedItems.length) +\n                          \"/\" +\n                          _vm._s(_vm.items.length) +\n                          \")\"\n                      )\n                    ]),\n                    _vm._v(\" \"),\n                    _vm.selectedItems.length < 3\n                      ? _c(\"span\", [\n                          _c(\n                            \"button\",\n                            {\n                              staticClass: \"btn   btn--link link--quiet\",\n                              on: { click: _vm.selectAll }\n                            },\n                            [_vm._v(\"\\n            Select All\\n            \")]\n                          )\n                        ])\n                      : _c(\"span\", [\n                          _c(\n                            \"button\",\n                            {\n                              staticClass: \"btn   btn--link link--quiet\",\n                              on: { click: _vm.deselectAll }\n                            },\n                            [_vm._v(\"\\n            Deselect All\\n            \")]\n                          )\n                        ])\n                  ]),\n                  _vm._v(\" \"),\n                  _vm.matchingQuery.length\n                    ? _c(\n                        \"ul\",\n                        _vm._l(_vm.matchingQuery, function(item) {\n                          return _c(\"li\", [\n                            _c(\n                              \"label\",\n                              {\n                                staticClass: \"checkbox checkbox--sm\",\n                                attrs: { for: item.title }\n                              },\n                              [\n                                _vm._v(\n                                  \" \" +\n                                    _vm._s(item.title) +\n                                    \"\\n            \\n              \"\n                                ),\n                                _c(\"input\", {\n                                  directives: [\n                                    {\n                                      name: \"model\",\n                                      rawName: \"v-model\",\n                                      value: _vm.selectedItems,\n                                      expression: \"selectedItems\"\n                                    }\n                                  ],\n                                  staticClass: \"checkbox__input\",\n                                  attrs: {\n                                    type: \"checkbox\",\n                                    name: \"\",\n                                    id: item.title,\n                                    name: _vm.name\n                                  },\n                                  domProps: {\n                                    value: item.value,\n                                    checked: Array.isArray(_vm.selectedItems)\n                                      ? _vm._i(_vm.selectedItems, item.value) >\n                                        -1\n                                      : _vm.selectedItems\n                                  },\n                                  on: {\n                                    change: function($event) {\n                                      var $$a = _vm.selectedItems,\n                                        $$el = $event.target,\n                                        $$c = $$el.checked ? true : false\n                                      if (Array.isArray($$a)) {\n                                        var $$v = item.value,\n                                          $$i = _vm._i($$a, $$v)\n                                        if ($$el.checked) {\n                                          $$i < 0 &&\n                                            (_vm.selectedItems = $$a.concat([\n                                              $$v\n                                            ]))\n                                        } else {\n                                          $$i > -1 &&\n                                            (_vm.selectedItems = $$a\n                                              .slice(0, $$i)\n                                              .concat($$a.slice($$i + 1)))\n                                        }\n                                      } else {\n                                        _vm.selectedItems = $$c\n                                      }\n                                    }\n                                  }\n                                }),\n                                _vm._v(\" \"),\n                                _c(\"span\", { staticClass: \"checkbox__toggle\" })\n                              ]\n                            )\n                          ])\n                        })\n                      )\n                    : _c(\"div\", [\n                        _vm._v(\"\\n          No results found.\\n        \")\n                      ])\n                ]\n              )\n            ])\n          : _vm._e()\n      ])\n    ]\n  )\n}\nvar staticRenderFns = []\nrender._withStripped = true\n\n\n\n//# sourceURL=webpack:///./components/vue/search-filter/search-filter.html?./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js");

/***/ }),

/***/ "./node_modules/vue-loader/lib/runtime/componentNormalizer.js":
/*!********************************************************************!*\
  !*** ./node_modules/vue-loader/lib/runtime/componentNormalizer.js ***!
  \********************************************************************/
/*! exports provided: default */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, \"default\", function() { return normalizeComponent; });\n/* globals __VUE_SSR_CONTEXT__ */\n\n// IMPORTANT: Do NOT use ES2015 features in this file (except for modules).\n// This module is a runtime utility for cleaner component module output and will\n// be included in the final webpack user bundle.\n\nfunction normalizeComponent (\n  scriptExports,\n  render,\n  staticRenderFns,\n  functionalTemplate,\n  injectStyles,\n  scopeId,\n  moduleIdentifier, /* server only */\n  shadowMode /* vue-cli only */\n) {\n  // Vue.extend constructor export interop\n  var options = typeof scriptExports === 'function'\n    ? scriptExports.options\n    : scriptExports\n\n  // render functions\n  if (render) {\n    options.render = render\n    options.staticRenderFns = staticRenderFns\n    options._compiled = true\n  }\n\n  // functional template\n  if (functionalTemplate) {\n    options.functional = true\n  }\n\n  // scopedId\n  if (scopeId) {\n    options._scopeId = 'data-v-' + scopeId\n  }\n\n  var hook\n  if (moduleIdentifier) { // server build\n    hook = function (context) {\n      // 2.3 injection\n      context =\n        context || // cached call\n        (this.$vnode && this.$vnode.ssrContext) || // stateful\n        (this.parent && this.parent.$vnode && this.parent.$vnode.ssrContext) // functional\n      // 2.2 with runInNewContext: true\n      if (!context && typeof __VUE_SSR_CONTEXT__ !== 'undefined') {\n        context = __VUE_SSR_CONTEXT__\n      }\n      // inject component styles\n      if (injectStyles) {\n        injectStyles.call(this, context)\n      }\n      // register component module identifier for async chunk inferrence\n      if (context && context._registeredComponents) {\n        context._registeredComponents.add(moduleIdentifier)\n      }\n    }\n    // used by ssr in case component is cached and beforeCreate\n    // never gets called\n    options._ssrRegister = hook\n  } else if (injectStyles) {\n    hook = shadowMode\n      ? function () { injectStyles.call(this, this.$root.$options.shadowRoot) }\n      : injectStyles\n  }\n\n  if (hook) {\n    if (options.functional) {\n      // for template-only hot-reload because in that case the render fn doesn't\n      // go through the normalizer\n      options._injectStyles = hook\n      // register for functioal component in vue file\n      var originalRender = options.render\n      options.render = function renderWithStyleInjection (h, context) {\n        hook.call(context)\n        return originalRender(h, context)\n      }\n    } else {\n      // inject component registration as beforeCreate hook\n      var existing = options.beforeCreate\n      options.beforeCreate = existing\n        ? [].concat(existing, hook)\n        : [hook]\n    }\n  }\n\n  return {\n    exports: scriptExports,\n    options: options\n  }\n}\n\n\n//# sourceURL=webpack:///./node_modules/vue-loader/lib/runtime/componentNormalizer.js?");

/***/ }),

/***/ "vue":
/*!**********************!*\
  !*** external "vue" ***!
  \**********************/
/*! no static exports found */
/***/ (function(module, exports) {

eval("module.exports = require(\"vue\");\n\n//# sourceURL=webpack:///external_%22vue%22?");

/***/ }),

/***/ "vue-clickaway":
/*!********************************!*\
  !*** external "vue-clickaway" ***!
  \********************************/
/*! no static exports found */
/***/ (function(module, exports) {

eval("module.exports = require(\"vue-clickaway\");\n\n//# sourceURL=webpack:///external_%22vue-clickaway%22?");

/***/ })

/******/ });
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
/******/ 	return __webpack_require__(__webpack_require__.s = "./components/vue/patterns/modal-interaction/modal-interaction.vue");
/******/ })
/************************************************************************/
/******/ ({

/***/ "./components/vue/modal/modal.html?vue&type=template&id=0e98f752":
/*!***********************************************************************!*\
  !*** ./components/vue/modal/modal.html?vue&type=template&id=0e98f752 ***!
  \***********************************************************************/
/*! exports provided: render, staticRenderFns */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _node_modules_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_lib_loaders_compile_handlebars_loader_js_modal_html_vue_type_template_id_0e98f752__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!../../../lib/loaders/compile-handlebars-loader.js!./modal.html?vue&type=template&id=0e98f752 */ \"./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js!./components/vue/modal/modal.html?vue&type=template&id=0e98f752\");\n/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, \"render\", function() { return _node_modules_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_lib_loaders_compile_handlebars_loader_js_modal_html_vue_type_template_id_0e98f752__WEBPACK_IMPORTED_MODULE_0__[\"render\"]; });\n\n/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, \"staticRenderFns\", function() { return _node_modules_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_lib_loaders_compile_handlebars_loader_js_modal_html_vue_type_template_id_0e98f752__WEBPACK_IMPORTED_MODULE_0__[\"staticRenderFns\"]; });\n\n\n\n//# sourceURL=webpack:///./components/vue/modal/modal.html?");

/***/ }),

/***/ "./components/vue/modal/modal.vue":
/*!****************************************!*\
  !*** ./components/vue/modal/modal.vue ***!
  \****************************************/
/*! exports provided: default */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _modal_html_vue_type_template_id_0e98f752__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./modal.html?vue&type=template&id=0e98f752 */ \"./components/vue/modal/modal.html?vue&type=template&id=0e98f752\");\n/* harmony import */ var _modal_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./modal.vue?vue&type=script&lang=js */ \"./components/vue/modal/modal.vue?vue&type=script&lang=js\");\n/* empty/unused harmony star reexport *//* harmony import */ var _node_modules_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ../../../node_modules/vue-loader/lib/runtime/componentNormalizer.js */ \"./node_modules/vue-loader/lib/runtime/componentNormalizer.js\");\n/* harmony import */ var vue__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! vue */ \"vue\");\n/* harmony import */ var vue__WEBPACK_IMPORTED_MODULE_3___default = /*#__PURE__*/__webpack_require__.n(vue__WEBPACK_IMPORTED_MODULE_3__);\n/* harmony import */ var _Users_kevin_projects_snyk_snyk_ui_components_vue_modal_modal_vue__WEBPACK_IMPORTED_MODULE_4__ = __webpack_require__(/*! ./components/vue/modal/modal.vue */ \"./components/vue/modal/modal.vue\");\n\n\n\n\n\n/* normalize component */\n\nvar component = Object(_node_modules_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_2__[\"default\"])(\n  _modal_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_1__[\"default\"],\n  _modal_html_vue_type_template_id_0e98f752__WEBPACK_IMPORTED_MODULE_0__[\"render\"],\n  _modal_html_vue_type_template_id_0e98f752__WEBPACK_IMPORTED_MODULE_0__[\"staticRenderFns\"],\n  false,\n  null,\n  null,\n  null\n  \n)\n\n/* hot reload */\nif (false) { var api; }\ncomponent.options.__file = \"components/vue/modal/modal.vue\"\n/* harmony default export */ __webpack_exports__[\"default\"] = (component.exports);\n    \n    \n\n    if (!document.querySelector('[data-vue]')) {\n      const el = document.createElement('div');\n\n      el.appendChild(document.createElement('snyk-component'));\n      document.body.appendChild(el);\n\n      new vue__WEBPACK_IMPORTED_MODULE_3___default.a({\n        el,\n        components: {SnykComponent: _Users_kevin_projects_snyk_snyk_ui_components_vue_modal_modal_vue__WEBPACK_IMPORTED_MODULE_4__[\"default\"]},\n        mounted() {\n\n          let comp = this.$children[0];\n\n          Object.keys(comp._props || {}).forEach((key, i) => {\n            if (window.SnykUIFixture[key]) {\n              comp._props[key] = window.SnykUIFixture[key];\n              console.log('☝️ No worries');\n            }\n          });\n        }\n      });\n    }\n\n\n//# sourceURL=webpack:///./components/vue/modal/modal.vue?");

/***/ }),

/***/ "./components/vue/modal/modal.vue?vue&type=script&lang=js":
/*!****************************************************************!*\
  !*** ./components/vue/modal/modal.vue?vue&type=script&lang=js ***!
  \****************************************************************/
/*! exports provided: default */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _node_modules_babel_loader_lib_index_js_lib_loaders_snyk_ui_fractal_wrapper_js_node_modules_vue_loader_lib_index_js_vue_loader_options_modal_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../node_modules/babel-loader/lib!../../../lib/loaders/snyk-ui-fractal-wrapper.js!../../../node_modules/vue-loader/lib??vue-loader-options!./modal.vue?vue&type=script&lang=js */ \"./node_modules/babel-loader/lib/index.js!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib/index.js??vue-loader-options!./components/vue/modal/modal.vue?vue&type=script&lang=js\");\n/* empty/unused harmony star reexport */ /* harmony default export */ __webpack_exports__[\"default\"] = (_node_modules_babel_loader_lib_index_js_lib_loaders_snyk_ui_fractal_wrapper_js_node_modules_vue_loader_lib_index_js_vue_loader_options_modal_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_0__[\"default\"]); \n\n//# sourceURL=webpack:///./components/vue/modal/modal.vue?");

/***/ }),

/***/ "./components/vue/patterns/modal-interaction/modal-interaction.html?vue&type=template&id=51d41e0d":
/*!********************************************************************************************************!*\
  !*** ./components/vue/patterns/modal-interaction/modal-interaction.html?vue&type=template&id=51d41e0d ***!
  \********************************************************************************************************/
/*! exports provided: render, staticRenderFns */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _node_modules_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_lib_loaders_compile_handlebars_loader_js_modal_interaction_html_vue_type_template_id_51d41e0d__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../../node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!../../../../lib/loaders/compile-handlebars-loader.js!./modal-interaction.html?vue&type=template&id=51d41e0d */ \"./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js!./components/vue/patterns/modal-interaction/modal-interaction.html?vue&type=template&id=51d41e0d\");\n/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, \"render\", function() { return _node_modules_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_lib_loaders_compile_handlebars_loader_js_modal_interaction_html_vue_type_template_id_51d41e0d__WEBPACK_IMPORTED_MODULE_0__[\"render\"]; });\n\n/* harmony reexport (safe) */ __webpack_require__.d(__webpack_exports__, \"staticRenderFns\", function() { return _node_modules_vue_loader_lib_loaders_templateLoader_js_vue_loader_options_lib_loaders_compile_handlebars_loader_js_modal_interaction_html_vue_type_template_id_51d41e0d__WEBPACK_IMPORTED_MODULE_0__[\"staticRenderFns\"]; });\n\n\n\n//# sourceURL=webpack:///./components/vue/patterns/modal-interaction/modal-interaction.html?");

/***/ }),

/***/ "./components/vue/patterns/modal-interaction/modal-interaction.vue":
/*!*************************************************************************!*\
  !*** ./components/vue/patterns/modal-interaction/modal-interaction.vue ***!
  \*************************************************************************/
/*! exports provided: default */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _modal_interaction_html_vue_type_template_id_51d41e0d__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./modal-interaction.html?vue&type=template&id=51d41e0d */ \"./components/vue/patterns/modal-interaction/modal-interaction.html?vue&type=template&id=51d41e0d\");\n/* harmony import */ var _modal_interaction_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./modal-interaction.vue?vue&type=script&lang=js */ \"./components/vue/patterns/modal-interaction/modal-interaction.vue?vue&type=script&lang=js\");\n/* empty/unused harmony star reexport *//* harmony import */ var _node_modules_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ../../../../node_modules/vue-loader/lib/runtime/componentNormalizer.js */ \"./node_modules/vue-loader/lib/runtime/componentNormalizer.js\");\n/* harmony import */ var vue__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! vue */ \"vue\");\n/* harmony import */ var vue__WEBPACK_IMPORTED_MODULE_3___default = /*#__PURE__*/__webpack_require__.n(vue__WEBPACK_IMPORTED_MODULE_3__);\n/* harmony import */ var _Users_kevin_projects_snyk_snyk_ui_components_vue_patterns_modal_interaction_modal_interaction_vue__WEBPACK_IMPORTED_MODULE_4__ = __webpack_require__(/*! ./components/vue/patterns/modal-interaction/modal-interaction.vue */ \"./components/vue/patterns/modal-interaction/modal-interaction.vue\");\n\n\n\n\n\n/* normalize component */\n\nvar component = Object(_node_modules_vue_loader_lib_runtime_componentNormalizer_js__WEBPACK_IMPORTED_MODULE_2__[\"default\"])(\n  _modal_interaction_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_1__[\"default\"],\n  _modal_interaction_html_vue_type_template_id_51d41e0d__WEBPACK_IMPORTED_MODULE_0__[\"render\"],\n  _modal_interaction_html_vue_type_template_id_51d41e0d__WEBPACK_IMPORTED_MODULE_0__[\"staticRenderFns\"],\n  false,\n  null,\n  null,\n  null\n  \n)\n\n/* hot reload */\nif (false) { var api; }\ncomponent.options.__file = \"components/vue/patterns/modal-interaction/modal-interaction.vue\"\n/* harmony default export */ __webpack_exports__[\"default\"] = (component.exports);\n    \n    \n\n    if (!document.querySelector('[data-vue]')) {\n      const el = document.createElement('div');\n\n      el.appendChild(document.createElement('snyk-component'));\n      document.body.appendChild(el);\n\n      new vue__WEBPACK_IMPORTED_MODULE_3___default.a({\n        el,\n        components: {SnykComponent: _Users_kevin_projects_snyk_snyk_ui_components_vue_patterns_modal_interaction_modal_interaction_vue__WEBPACK_IMPORTED_MODULE_4__[\"default\"]},\n        mounted() {\n\n          let comp = this.$children[0];\n\n          Object.keys(comp._props || {}).forEach((key, i) => {\n            if (window.SnykUIFixture[key]) {\n              comp._props[key] = window.SnykUIFixture[key];\n              console.log('☝️ No worries');\n            }\n          });\n        }\n      });\n    }\n\n\n//# sourceURL=webpack:///./components/vue/patterns/modal-interaction/modal-interaction.vue?");

/***/ }),

/***/ "./components/vue/patterns/modal-interaction/modal-interaction.vue?vue&type=script&lang=js":
/*!*************************************************************************************************!*\
  !*** ./components/vue/patterns/modal-interaction/modal-interaction.vue?vue&type=script&lang=js ***!
  \*************************************************************************************************/
/*! exports provided: default */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _node_modules_babel_loader_lib_index_js_lib_loaders_snyk_ui_fractal_wrapper_js_node_modules_vue_loader_lib_index_js_vue_loader_options_modal_interaction_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! -!../../../../node_modules/babel-loader/lib!../../../../lib/loaders/snyk-ui-fractal-wrapper.js!../../../../node_modules/vue-loader/lib??vue-loader-options!./modal-interaction.vue?vue&type=script&lang=js */ \"./node_modules/babel-loader/lib/index.js!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib/index.js??vue-loader-options!./components/vue/patterns/modal-interaction/modal-interaction.vue?vue&type=script&lang=js\");\n/* empty/unused harmony star reexport */ /* harmony default export */ __webpack_exports__[\"default\"] = (_node_modules_babel_loader_lib_index_js_lib_loaders_snyk_ui_fractal_wrapper_js_node_modules_vue_loader_lib_index_js_vue_loader_options_modal_interaction_vue_vue_type_script_lang_js__WEBPACK_IMPORTED_MODULE_0__[\"default\"]); \n\n//# sourceURL=webpack:///./components/vue/patterns/modal-interaction/modal-interaction.vue?");

/***/ }),

/***/ "./node_modules/babel-loader/lib/index.js!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib/index.js??vue-loader-options!./components/vue/modal/modal.vue?vue&type=script&lang=js":
/*!*******************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/babel-loader/lib!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib??vue-loader-options!./components/vue/modal/modal.vue?vue&type=script&lang=js ***!
  \*******************************************************************************************************************************************************************************************/
/*! exports provided: default */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n//\n/* harmony default export */ __webpack_exports__[\"default\"] = ({\n  name: 'modal',\n  props: {\n    'title': {\n      type: String\n    },\n    'cancelbutton': {\n      type: String,\n      default: 'Cancel'\n    },\n    'proceedbutton': {\n      type: String,\n      default: 'Submit'\n    },\n    'format': {\n      type: String\n    }\n  },\n  computed: {\n    isNarrow: function () {\n      return this.format === 'narrow';\n    }\n  },\n  methods: {\n    close() {\n      this.$emit('close');\n    },\n\n    submit() {\n      this.$emit('submit');\n    }\n\n  },\n  mounted: function () {\n    document.body.classList.add('modal--active');\n  },\n  beforeDestroy: function () {\n    document.body.classList.remove('modal--active');\n  }\n});\n\n//# sourceURL=webpack:///./components/vue/modal/modal.vue?./node_modules/babel-loader/lib!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib??vue-loader-options");

/***/ }),

/***/ "./node_modules/babel-loader/lib/index.js!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib/index.js??vue-loader-options!./components/vue/patterns/modal-interaction/modal-interaction.vue?vue&type=script&lang=js":
/*!****************************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/babel-loader/lib!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib??vue-loader-options!./components/vue/patterns/modal-interaction/modal-interaction.vue?vue&type=script&lang=js ***!
  \****************************************************************************************************************************************************************************************************************************/
/*! no exports provided */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var vue__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! vue */ \"vue\");\n/* harmony import */ var vue__WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(vue__WEBPACK_IMPORTED_MODULE_0__);\n/* harmony import */ var SnykUI_vue_modal_modal_vue__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! SnykUI/vue/modal/modal.vue */ \"./components/vue/modal/modal.vue\");\n//\n\n\nvue__WEBPACK_IMPORTED_MODULE_0___default.a.component('modal', SnykUI_vue_modal_modal_vue__WEBPACK_IMPORTED_MODULE_1__[\"default\"]);\nnew vue__WEBPACK_IMPORTED_MODULE_0___default.a({\n  el: document.querySelector(`[data-vue]`),\n\n  data() {\n    return {\n      isModalVisible: false\n    };\n  },\n\n  methods: {\n    closeModal(evt) {\n      this.isModalVisible = false;\n    },\n\n    showModal(evt) {\n      evt.preventDefault();\n      this.isModalVisible = true;\n    }\n\n  }\n});\n\n//# sourceURL=webpack:///./components/vue/patterns/modal-interaction/modal-interaction.vue?./node_modules/babel-loader/lib!./lib/loaders/snyk-ui-fractal-wrapper.js!./node_modules/vue-loader/lib??vue-loader-options");

/***/ }),

/***/ "./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js!./components/vue/modal/modal.html?vue&type=template&id=0e98f752":
/*!**********************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js!./components/vue/modal/modal.html?vue&type=template&id=0e98f752 ***!
  \**********************************************************************************************************************************************************************************************/
/*! exports provided: render, staticRenderFns */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, \"render\", function() { return render; });\n/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, \"staticRenderFns\", function() { return staticRenderFns; });\nvar render = function() {\n  var _vm = this\n  var _h = _vm.$createElement\n  var _c = _vm._self._c || _h\n  return _c(\"transition\", { attrs: { name: \"modal-fade\" } }, [\n    _c(\n      \"div\",\n      {\n        staticClass: \"modal-backdrop\",\n        attrs: { role: \"dialog\" },\n        on: {\n          click: function($event) {\n            if ($event.target !== $event.currentTarget) {\n              return null\n            }\n            return _vm.close($event)\n          }\n        }\n      },\n      [\n        _c(\n          \"div\",\n          {\n            staticClass: \"modal-wrap\",\n            on: {\n              click: function($event) {\n                if ($event.target !== $event.currentTarget) {\n                  return null\n                }\n                return _vm.close($event)\n              }\n            }\n          },\n          [\n            _c(\"div\", { staticClass: \"modal-header\" }, [\n              _c(\"div\", { staticClass: \"modal-header__container\" }, [\n                _c(\n                  \"div\",\n                  {\n                    staticClass: \"u--d__if\",\n                    staticStyle: { \"align-items\": \"center\" }\n                  },\n                  [\n                    _vm._t(\"icon\"),\n                    _vm._v(\" \"),\n                    _c(\"h2\", { staticClass: \"heading__title\" }, [\n                      _vm._v(\n                        \"\\n              \" +\n                          _vm._s(_vm.title) +\n                          \"\\n            \"\n                      )\n                    ])\n                  ],\n                  2\n                ),\n                _vm._v(\" \"),\n                _c(\"div\", [\n                  _vm.cancelbutton\n                    ? _c(\n                        \"span\",\n                        {\n                          staticClass: \"l-pad-right--md\",\n                          staticStyle: { display: \"inline-block\" }\n                        },\n                        [\n                          _c(\n                            \"button\",\n                            {\n                              staticClass: \"btn   btn--outlined \",\n                              on: { click: _vm.close }\n                            },\n                            [\n                              _vm._v(\n                                \"\\n              \" +\n                                  _vm._s(_vm.cancelbutton) +\n                                  \"\\n              \"\n                              )\n                            ]\n                          )\n                        ]\n                      )\n                    : _vm._e(),\n                  _vm._v(\" \"),\n                  _c(\n                    \"button\",\n                    { staticClass: \"btn   \", on: { click: _vm.submit } },\n                    [\n                      _vm._v(\n                        \"\\n            \" +\n                          _vm._s(_vm.proceedbutton) +\n                          \"\\n            \"\n                      )\n                    ]\n                  )\n                ])\n              ])\n            ]),\n            _vm._v(\" \"),\n            _c(\n              \"div\",\n              {\n                ref: \"modal\",\n                staticClass: \"modal\",\n                class: { \"modal--sm\": _vm.isNarrow }\n              },\n              [\n                _c(\n                  \"section\",\n                  { staticClass: \"modal-body\" },\n                  [\n                    _vm._t(\"body\", [\n                      _c(\n                        \"div\",\n                        { staticClass: \"loading-spinner loading-spinner--xl\" },\n                        [\n                          _c(\"div\", { staticClass: \"loading-spinner__image\" }, [\n                            _c(\n                              \"svg\",\n                              {\n                                attrs: {\n                                  width: \"48\",\n                                  height: \"48\",\n                                  viewBox: \"0 0 24 24\",\n                                  role: \"img\",\n                                  \"aria-labelledby\": \"svg-spinner\"\n                                }\n                              },\n                              [\n                                _c(\"title\", { attrs: { id: \"svg-spinner\" } }, [\n                                  _vm._v(\"Loading data\")\n                                ]),\n                                _vm._v(\" \"),\n                                _c(\"path\", {\n                                  attrs: {\n                                    d:\n                                      \"M20 12h4c0-6.6-5.4-12-12-12v4c4.4 0 8 3.6 8 8z\"\n                                  }\n                                }),\n                                _vm._v(\" \"),\n                                _c(\"path\", {\n                                  attrs: {\n                                    d:\n                                      \"M12 0c6.6 0 12 5.4 12 12s-5.4 12-12 12S0 18.6 0 12 5.4 0 12 0zm0 20c4.4 0 8-3.6 8-8s-3.6-8-8-8-8 3.6-8 8 3.6 8 8 8z\",\n                                    opacity: \".2\"\n                                  }\n                                })\n                              ]\n                            )\n                          ])\n                        ]\n                      )\n                    ])\n                  ],\n                  2\n                )\n              ]\n            )\n          ]\n        )\n      ]\n    )\n  ])\n}\nvar staticRenderFns = []\nrender._withStripped = true\n\n\n\n//# sourceURL=webpack:///./components/vue/modal/modal.html?./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js");

/***/ }),

/***/ "./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js!./components/vue/patterns/modal-interaction/modal-interaction.html?vue&type=template&id=51d41e0d":
/*!*******************************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js!./components/vue/patterns/modal-interaction/modal-interaction.html?vue&type=template&id=51d41e0d ***!
  \*******************************************************************************************************************************************************************************************************************************/
/*! exports provided: render, staticRenderFns */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, \"render\", function() { return render; });\n/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, \"staticRenderFns\", function() { return staticRenderFns; });\nvar render = function() {\n  var _vm = this\n  var _h = _vm.$createElement\n  var _c = _vm._self._c || _h\n  return _c(\"div\")\n}\nvar staticRenderFns = []\nrender._withStripped = true\n\n\n\n//# sourceURL=webpack:///./components/vue/patterns/modal-interaction/modal-interaction.html?./node_modules/vue-loader/lib/loaders/templateLoader.js??vue-loader-options!./lib/loaders/compile-handlebars-loader.js");

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

/***/ })

/******/ });
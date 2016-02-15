/**
 * @fileoverview Utilities to serialize objects
 *
 */

goog.provide('util.obj');

/**
 * Checks if the object is of type function
 * @param {Object} obj - The object to check
 * @return {boolean}
 */
util.obj.isFn = function(obj) {
    return typeof obj === 'function';
};

/**
 * Checks if the object is of type object
 * @param {Object} obj - The object to check
 * @return {boolean}
 */
util.obj.isObj = function(obj) {
    return typeof obj === 'object';
};

/**
 * Checks if the object contains the keys
 * @param {Array} keys
 * @param {string} k
 * @return {boolean}
 **/
util.obj.contains = function(keys, k) {
    var i = keys.length;
    while (i--) {
       if (keys[i] === k) {
           return true;
       }
    }
    return false;
};

/**
 * Returns a copy of the object with the selected keys
 * @param {Array} keys
 * @param {Object} obj
 * @return {Object}
 **/
util.obj.selectKeys = function(keys, obj) {
    var result = {};
    for(var k in obj) {
        if (util.obj.contains(keys, k)) {
            result[k] = obj[k];
        };
    };
    return result;
};

/**
 * Return s a copy of the object without functions or objects
 * @param {Object} obj
 * @return {Object}
 */
util.obj.simpleKeys = function (obj) {
    var keys = Object.getOwnPropertyNames(obj).filter(function(p) {
        return !((util.obj.isFn(obj[p])) || (util.obj.isObj(obj[p])));
    });
    return util.obj.selectKeys(keys, obj);
};

/**
 * Serializes an object by removing closures and nested objects
 * @param {Object} obj - The object to serialize
 * @return {string} - The serialized object
 */
util.obj.serialize = function(obj) {
    return JSON.stringify(util.obj.simpleKeys(obj));
};

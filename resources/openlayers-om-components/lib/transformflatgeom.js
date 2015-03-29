goog.provide('ol.geom.flat.transform.scale');

goog.require('ol.geom.flat.transform');

/**
 * @param {Array.<number>} flatCoordinates Flat coordinates.
 * @param {number} offset Offset.
 * @param {number} end End.
 * @param {number} stride Stride.
 * @param {number} scaleX X scaling factor.
 * @param {number} scaleY Y scaling factor.
 * @param {number=} opt_originX Origin X coordinate.
 * @param {number=} opt_originY Origin Y coordinate.
 * @param {Array.<number>=} opt_dest Destination.
 * @return {Array.<number>} Transformed coordinates.
 */
ol.geom.flat.transform.scale =
    function(flatCoordinates, offset, end, stride, scaleX, scaleY, opt_originX,
             opt_originY, opt_dest) {
  var dest = goog.isDef(opt_dest) ? opt_dest : [];
  var originX = goog.isDef(opt_originX) ? opt_originX : 0;
  var originY = goog.isDef(opt_originY) ? opt_originY : 0;
  var i = 0;
  var j, k;
  for (j = offset; j < end; j += stride) {
    dest[i++] = (flatCoordinates[j] - originX) * scaleX + originX;
    dest[i++] = (flatCoordinates[j + 1] - originY) * scaleY + originY;
    for (k = j + 2; k < j + stride; ++k) {
      dest[i++] = flatCoordinates[k];
    }
  }
  if (goog.isDef(opt_dest) && dest.length != i) {
    dest.length = i;
  }
  return dest;
};

goog.provide('ol.geom.SimpleGeometry.scale');

goog.require('goog.asserts');
goog.require('goog.functions');
goog.require('goog.object');
goog.require('ol.extent');
goog.require('ol.geom.Geometry');
goog.require('ol.geom.flat.transform.scale');
goog.require('ol.geom.SimpleGeometry');


/**
 * Scale the geometry. The scaling factors are absolute, i.e. a scale factor
 * of 1 is no change, .5 is shrinking by 50%, 1.5 is growing by 50%.
 * @param {number} scaleX Scale in the X axis.
 * @param {number} scaleY Scale in the Y axis.
 * @param {ol.Coordinate=} opt_originPoint Point to scale about. (Defaults to
 * the center point of the geometry's extent.)
 * @api
 */
ol.geom.SimpleGeometry.prototype.scale = function(scaleX, scaleY,
                                                  opt_originPoint) {
  var originPoint = opt_originPoint;
  if (!goog.isDef(originPoint)) {
    var extent = this.getExtent();
    var centerX = (extent[2] + extent[0]) / 2;
    var centerY = (extent[3] + extent[1]) / 2;
    originPoint = [centerX, centerY];
  }

  var flatCoordinates = this.getFlatCoordinates();
  if (!goog.isNull(flatCoordinates)) {
    var stride = this.getStride();
    ol.geom.flat.transform.scale(
        flatCoordinates, 0, flatCoordinates.length, stride,
        scaleX, scaleY, originPoint[0], originPoint[1], flatCoordinates);
    this.changed();
  }
};

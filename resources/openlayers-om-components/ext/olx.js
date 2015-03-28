/**
 * @typedef {{ features: ol.Collection.<ol.Feature>,
 *     condition: (ol.events.ConditionType|undefined)}}
 * @api
 */
olx.interaction.ScaleOptions;


/**
 * The features the interaction works on.
 * @type {ol.Collection.<ol.Feature>}
 * @api
 */
olx.interaction.ScaleOptions.prototype.features;


/**
 * Should the feature's aspect ratio be locked while scaling.
 * @type {ol.events.ConditionType|undefined}
 * @api
 */
olx.interaction.ScaleOptions.prototype.condition;


/**
 * @typedef {{features: (ol.Collection.<ol.Feature>|undefined)}}
 * @api
 */
olx.interaction.TranslateOptions;


/**
 * The features the interaction works on.
 * @type {ol.Collection.<ol.Feature>|undefined}
 * @api
 */
olx.interaction.TranslateOptions.prototype.features;


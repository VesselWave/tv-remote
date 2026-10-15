// VERSION and VERSION_CODE are the single source for the displayed version and Android's update counter.
/* global __dirname */
const fs = require('node:fs');
const path = require('node:path');

const read = (name) => fs.readFileSync(path.join(__dirname, name), 'utf8').trim();

module.exports = ({ config }) => ({
  ...config,
  version: read('VERSION'),
  android: { ...config.android, versionCode: Number(read('VERSION_CODE')) },
});

const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');
module.exports = defineConfig([expoConfig, { ignores: ['android/**', 'build/**', 'dist/**', 'out/**', 'site/**', 'modules/**/android/**'] }]);

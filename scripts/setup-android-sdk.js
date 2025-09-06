#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const projectRoot = path.join(__dirname, '..');
const androidDir = path.join(projectRoot, 'android');
const localProps = path.join(androidDir, 'local.properties');

const sdkDefault = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME || path.join(process.env.HOME || process.env.USERPROFILE, 'Library/Android/sdk');

if (!fs.existsSync(androidDir)) {
  console.error('Android directory not found at', androidDir);
  process.exit(1);
}

let content = `sdk.dir=${sdkDefault.replace(/\\/g, '/')}`;
if (process.env.JAVA_HOME) content += `\norg.gradle.java.home=${process.env.JAVA_HOME.replace(/\\/g, '/')}`;

fs.writeFileSync(localProps, content + '\n');
console.log('Written', localProps);
console.log(content);

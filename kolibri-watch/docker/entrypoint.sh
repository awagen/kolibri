#!/bin/sh

# hack around the fact that all env variables are baked
# in at build-time. Therefore if placeholder values are used
# in the .env files, we can replace those with the runtime env
# variables. See https://stackoverflow.com/questions/53010064/pass-environment-variable-into-a-vue-app-at-runtime.

ROOT_DIR=/app

# Replace env vars in JavaScript files
echo "Replacing env constants in JS"
for file in $ROOT_DIR/assets/*.js $ROOT_DIR/index.html;
do
  echo "Processing $file ...";
  sed -i 's|##VITE_KOLIBRI_BASE_URL|'${KOLIBRI_BASE_URL}'|g' $file
done

echo "Starting Nginx"
nginx -g 'daemon off;'

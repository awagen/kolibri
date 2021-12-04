#!/bin/sh

OVERLAY_DEST_PATH=specific_env1

# values used for replacement
GCP_GS_BUCKET=testbucket
GCP_GS_PATH=testpath
GCP_GS_PROJECT_ID=testprojectid
KOLIBRI_BASE_URL=http:\\/\\/localhost:8000
GCP_CLOUD_KEY_SECRET_NAME=testsecret
GCP_SERVICE_ACCOUNT_NAME=testserviceaccount
INGRESS_HOST=my.host.com
UI_INGRESS_HOST=my-ui.host.com
NAMESPACE=mynamespace
SERVICE_ACCOUNT_NAME=myaccount

mkdir -p ./template_copy/overlays/$OVERLAY_DEST_PATH
cp -r ./base ./template_copy/
cp -r ./overlays/specific_env/* ./template_copy/overlays/$OVERLAY_DEST_PATH/


for file in ./template_copy/base/* ./template_copy/overlays/$OVERLAY_DEST_PATH/*; do
    if [ -f "$file" ]; then
        echo "replacing vars for file $file"
        # now on new files run the replacement inplace
        sed -i "s/##GCP_GS_BUCKET/$GCP_GS_BUCKET/g" "$file"
        sed -i "s/##GCP_GS_PATH/$GCP_GS_PATH/g" "$file"
        sed -i "s/##GCP_GS_PROJECT_ID/$GCP_GS_PROJECT_ID/g" "$file"
        sed -i "s/##KOLIBRI_BASE_URL/$KOLIBRI_BASE_URL/g" "$file"
        sed -i "s/##GCP_CLOUD_KEY_SECRET_NAME/$GCP_CLOUD_KEY_SECRET_NAME/g" "$file"
        sed -i "s/##GCP_SERVICE_ACCOUNT_NAME/$GCP_SERVICE_ACCOUNT_NAME/g" "$file"
        sed -i "s/##INGRESS_HOST/$INGRESS_HOST/g" "$file"
        sed -i "s/##UI_INGRESS_HOST/$UI_INGRESS_HOST/g" "$file"
        sed -i "s/##NAMESPACE/$NAMESPACE/g" "$file"
        sed -i "s/##SERVICE_ACCOUNT_NAME/$SERVICE_ACCOUNT_NAME/g" "$file"
    fi
done

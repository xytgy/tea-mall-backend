#!/bin/bash
set -e

IK_VERSION="8.17.0"

if /usr/share/elasticsearch/bin/elasticsearch-plugin list 2>/dev/null | grep -q "analysis-ik"; then
    echo "IK plugin already installed, skipping..."
else
    echo "Installing IK plugin version ${IK_VERSION} from infini.cloud mirror..."
    /usr/share/elasticsearch/bin/elasticsearch-plugin install --batch "https://get.infini.cloud/elasticsearch/analysis-ik/${IK_VERSION}"
    echo "IK plugin installed successfully."
fi
